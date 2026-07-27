package dev.jensakaa.dynamicsprites;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Locale;

final class SourceReader {
  private static final int MAXIMUM_REDIRECTS = 3;
  private final HttpClient httpClient;
  private final SpriteLimits limits;

  SourceReader(HttpClient httpClient, SpriteLimits limits) {
    this.httpClient = httpClient;
    this.limits = limits;
  }

  LoadedSource read(SpriteSource source, String assetId, CancellationToken token) throws IOException {
    token.throwIfCancelled(assetId);
    return switch (source) {
      case SpriteSource.Bytes bytes -> {
        validateLength(assetId, bytes.data().length);
        yield new LoadedSource(bytes.data(), bytes.mediaType());
      }
      case SpriteSource.File file -> {
        long size = Files.size(file.path());
        validateLength(assetId, size);
        yield new LoadedSource(Files.readAllBytes(file.path()), Files.probeContentType(file.path()));
      }
      case SpriteSource.Url url -> readUrl(url.uri(), assetId, token, 0);
      case SpriteSource.Image ignored -> throw new IllegalArgumentException("BufferedImage sources are read directly");
      case SpriteSource.Texture ignored -> throw new IllegalArgumentException("Texture properties do not contain bytes");
      case SpriteSource.PlayerSkin ignored -> throw new IllegalArgumentException("Player skins use PlayerSkinResolver");
    };
  }

  private LoadedSource readUrl(
    URI uri,
    String assetId,
    CancellationToken token,
    int redirectCount
  ) throws IOException {
    validateUri(uri, assetId);
    if (redirectCount > MAXIMUM_REDIRECTS) {
      throw new SpriteException(assetId, "URL exceeded " + MAXIMUM_REDIRECTS + " redirects", false);
    }
    HttpRequest request = HttpRequest.newBuilder(uri)
      .timeout(Duration.ofSeconds(15))
      .header("Accept", "image/png,image/gif,image/apng")
      .GET()
      .build();
    HttpResponse<InputStream> response;
    try {
      response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted while downloading sprite source", exception);
    }
    int status = response.statusCode();
    if (status >= 300 && status < 400) {
      response.body().close();
      String location = response.headers().firstValue("location")
        .orElseThrow(() -> new SpriteException(assetId, "redirect has no Location header", false));
      return readUrl(uri.resolve(location), assetId, token, redirectCount + 1);
    }
    if (status < 200 || status >= 300) {
      response.body().close();
      throw new SpriteException(assetId, "URL returned HTTP " + status, status >= 500, status, null);
    }
    String contentType = response.headers().firstValue("content-type").orElse("");
    String normalizedType = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    if (!normalizedType.isEmpty() && !normalizedType.startsWith("image/")) {
      response.body().close();
      throw new SpriteException(assetId, "URL MIME type is not an image: " + normalizedType, false);
    }
    long declaredLength = response.headers().firstValueAsLong("content-length").orElse(-1);
    if (declaredLength >= 0) validateLength(assetId, declaredLength);
    try (InputStream input = response.body()) {
      return new LoadedSource(readBounded(input, assetId, token), normalizedType);
    }
  }

  private byte[] readBounded(InputStream input, String assetId, CancellationToken token) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    byte[] buffer = new byte[8192];
    int read;
    long total = 0;
    while ((read = input.read(buffer)) >= 0) {
      token.throwIfCancelled(assetId);
      total += read;
      validateLength(assetId, total);
      output.write(buffer, 0, read);
    }
    return output.toByteArray();
  }

  private void validateLength(String assetId, long length) {
    if (length > limits.maximumSourceBytes()) {
      throw new SpriteException(
        assetId,
        "source is " + length + " bytes, limit is " + limits.maximumSourceBytes(),
        false
      );
    }
  }

  private static void validateUri(URI uri, String assetId) throws IOException {
    String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
    if (!scheme.equals("http") && !scheme.equals("https")) {
      throw new SpriteException(assetId, "URL scheme must be http or https", false);
    }
    if (uri.getUserInfo() != null || uri.getHost() == null) {
      throw new SpriteException(assetId, "URL must have a public host and no user info", false);
    }
    for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
      if (address.isAnyLocalAddress() ||
        address.isLoopbackAddress() ||
        address.isLinkLocalAddress() ||
        address.isSiteLocalAddress() ||
        address.isMulticastAddress()) {
        throw new SpriteException(assetId, "URL resolves to a private or local address", false);
      }
    }
  }

  record LoadedSource(byte[] bytes, String mediaType) {
  }
}
