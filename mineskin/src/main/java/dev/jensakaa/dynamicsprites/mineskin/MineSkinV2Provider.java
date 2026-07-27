package dev.jensakaa.dynamicsprites.mineskin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.jensakaa.dynamicsprites.CancellationToken;
import dev.jensakaa.dynamicsprites.SpriteException;
import dev.jensakaa.dynamicsprites.TextureProperty;
import dev.jensakaa.dynamicsprites.TextureUploadProvider;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

public final class MineSkinV2Provider implements TextureUploadProvider, AutoCloseable {
  private final MineSkinV2Settings settings;
  private final HttpClient httpClient;
  private final ExecutorService executor;
  private final boolean ownsExecutor;
  private final Semaphore concurrency;

  public MineSkinV2Provider(MineSkinV2Settings settings) {
    this(
      settings,
      HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(),
      Executors.newVirtualThreadPerTaskExecutor(),
      true
    );
  }

  public MineSkinV2Provider(
    MineSkinV2Settings settings,
    HttpClient httpClient,
    ExecutorService executor
  ) {
    this(settings, httpClient, executor, false);
  }

  private MineSkinV2Provider(
    MineSkinV2Settings settings,
    HttpClient httpClient,
    ExecutorService executor,
    boolean ownsExecutor
  ) {
    this.settings = settings;
    this.httpClient = httpClient;
    this.executor = executor;
    this.ownsExecutor = ownsExecutor;
    this.concurrency = new Semaphore(settings.concurrency(), true);
  }

  @Override
  public CompletionStage<TextureProperty> upload(
    String assetId,
    String tileHash,
    byte[] skinPng,
    CancellationToken cancellationToken
  ) {
    return CompletableFuture.supplyAsync(() -> {
      cancellationToken.throwIfCancelled(assetId);
      acquire(assetId);
      try {
        return queueAndAwait(assetId, tileHash, skinPng, cancellationToken);
      } finally {
        concurrency.release();
      }
    }, executor);
  }

  private TextureProperty queueAndAwait(
    String assetId,
    String tileHash,
    byte[] skinPng,
    CancellationToken token
  ) {
    HttpResponse<String> queued = executeWithRetry(
      assetId,
      () -> queueRequest(tileHash, skinPng),
      token
    );
    JsonObject response = parse(assetId, queued);
    Optional<TextureProperty> immediate = textureProperty(response, assetId);
    if (immediate.isPresent()) return immediate.get();
    String jobId = jobId(response);
    if (jobId == null) {
      throw providerError(assetId, queued.statusCode(), "queue response has neither skin nor job id", false, null);
    }
    Instant deadline = Instant.now().plus(settings.jobTimeout());
    while (Instant.now().isBefore(deadline)) {
      token.throwIfCancelled(assetId);
      sleep(settings.pollInterval(), assetId);
      HttpResponse<String> polled = executeWithRetry(
        assetId,
        () -> getRequest("/v2/queue/" + jobId),
        token
      );
      JsonObject job = parse(assetId, polled);
      Optional<TextureProperty> property = textureProperty(job, assetId);
      if (property.isPresent()) return property.get();
      String status = stringAt(job, "job", "status");
      if (status == null) status = string(job, "status");
      if ("failed".equalsIgnoreCase(status) || "cancelled".equalsIgnoreCase(status)) {
        throw providerError(assetId, polled.statusCode(), "MineSkin job " + jobId + " " + status, true, null);
      }
    }
    throw providerError(assetId, null, "MineSkin job timed out after " + settings.jobTimeout(), true, null);
  }

  private HttpResponse<String> executeWithRetry(
    String assetId,
    RequestFactory requestFactory,
    CancellationToken token
  ) {
    int attempt = 0;
    while (true) {
      token.throwIfCancelled(assetId);
      HttpResponse<String> response;
      try {
        response = httpClient.send(requestFactory.create(), HttpResponse.BodyHandlers.ofString());
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw providerError(assetId, null, "MineSkin request was interrupted", true, exception);
      } catch (IOException exception) {
        if (attempt++ >= settings.maximumRetries()) {
          throw providerError(assetId, null, "MineSkin request failed: " + exception.getMessage(), true, exception);
        }
        sleep(backoff(attempt), assetId);
        continue;
      }
      int status = response.statusCode();
      if (status >= 200 && status < 300) return response;
      boolean retryable = status == 429 || status >= 500;
      if (!retryable || attempt++ >= settings.maximumRetries()) {
        throw providerError(
          assetId,
          status,
          responseMessage(response.body(), "MineSkin returned HTTP " + status),
          retryable,
          null
        );
      }
      sleep(retryAfter(response, attempt), assetId);
    }
  }

  private HttpRequest queueRequest(String tileHash, byte[] skinPng) {
    String boundary = "dynamic-sprites-" + UUID.randomUUID();
    byte[] body = multipart(boundary, tileHash, skinPng);
    return baseRequest("/v2/queue")
      .header("Content-Type", "multipart/form-data; boundary=" + boundary)
      .POST(HttpRequest.BodyPublishers.ofByteArray(body))
      .build();
  }

  private HttpRequest getRequest(String path) {
    return baseRequest(path).GET().build();
  }

  private HttpRequest.Builder baseRequest(String path) {
    return HttpRequest.newBuilder(settings.baseUri().resolve(path))
      .timeout(settings.requestTimeout())
      .header("Accept", "application/json")
      .header("Authorization", "Bearer " + settings.apiKey())
      .header("User-Agent", settings.userAgent())
      .header("MineSkin-User-Agent", settings.userAgent());
  }

  private static byte[] multipart(String boundary, String tileHash, byte[] png) {
    try {
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      String header = "--" + boundary + "\r\n" +
        "Content-Disposition: form-data; name=\"file\"; filename=\"" + tileHash + ".png\"\r\n" +
        "Content-Type: image/png\r\n\r\n";
      output.write(header.getBytes(StandardCharsets.UTF_8));
      output.write(png);
      output.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
      return output.toByteArray();
    } catch (IOException exception) {
      throw new IllegalStateException("Failed to build MineSkin multipart body", exception);
    }
  }

  private static JsonObject parse(String assetId, HttpResponse<String> response) {
    try {
      JsonElement json = JsonParser.parseString(response.body());
      if (!json.isJsonObject()) {
        throw providerError(assetId, response.statusCode(), "MineSkin returned non-object JSON", true, null);
      }
      return json.getAsJsonObject();
    } catch (RuntimeException exception) {
      if (exception instanceof SpriteException spriteException) throw spriteException;
      throw providerError(assetId, response.statusCode(), "MineSkin returned invalid JSON", true, exception);
    }
  }

  private static Optional<TextureProperty> textureProperty(JsonObject root, String assetId) {
    JsonObject texture = objectAt(root, "skin", "texture");
    JsonObject data = texture == null ? null : object(texture, "data");
    if (data == null) return Optional.empty();
    String value = string(data, "value");
    if (value == null || value.isBlank()) return Optional.empty();
    String signature = string(data, "signature");
    String url = string(texture, "url");
    if (url == null) {
      JsonObject urls = object(texture, "urls");
      url = urls == null ? null : string(urls, "skin");
    }
    URI textureUrl = null;
    String textureHash = null;
    if (url != null && !url.isBlank()) {
      textureUrl = URI.create(url);
      if (!"textures.minecraft.net".equalsIgnoreCase(textureUrl.getHost())) {
        throw providerError(
          assetId,
          null,
          "MineSkin returned unsupported texture host " + textureUrl.getHost(),
          false,
          null
        );
      }
      String path = textureUrl.getPath();
      textureHash = path.substring(path.lastIndexOf('/') + 1);
    }
    return Optional.of(new TextureProperty(value, signature, textureHash, textureUrl));
  }

  private static String jobId(JsonObject root) {
    String id = stringAt(root, "job", "id");
    if (id != null) return id;
    JsonObject job = object(root, "job");
    if (job != null) {
      JsonElement idElement = job.get("id");
      if (idElement != null && idElement.isJsonPrimitive()) return idElement.getAsString();
    }
    return null;
  }

  private static JsonObject objectAt(JsonObject root, String... path) {
    JsonObject current = root;
    for (String segment : path) {
      current = object(current, segment);
      if (current == null) return null;
    }
    return current;
  }

  private static String stringAt(JsonObject root, String... path) {
    if (path.length == 0) return null;
    JsonObject parent = root;
    for (int index = 0; index < path.length - 1; index++) {
      parent = object(parent, path[index]);
      if (parent == null) return null;
    }
    return string(parent, path[path.length - 1]);
  }

  private static JsonObject object(JsonObject parent, String name) {
    if (parent == null) return null;
    JsonElement value = parent.get(name);
    return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
  }

  private static String string(JsonObject parent, String name) {
    if (parent == null) return null;
    JsonElement value = parent.get(name);
    return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
  }

  private static String responseMessage(String body, String fallback) {
    try {
      JsonObject root = JsonParser.parseString(body).getAsJsonObject();
      JsonElement errors = root.get("errors");
      if (errors != null && errors.isJsonArray() && !errors.getAsJsonArray().isEmpty()) {
        JsonObject first = errors.getAsJsonArray().get(0).getAsJsonObject();
        String message = string(first, "message");
        if (message != null) return message;
      }
    } catch (RuntimeException ignored) {
    }
    return fallback;
  }

  private static Duration retryAfter(HttpResponse<?> response, int attempt) {
    String value = response.headers().firstValue("Retry-After").orElse(null);
    if (value == null) return backoff(attempt);
    try {
      return Duration.ofSeconds(Math.max(1, Long.parseLong(value)));
    } catch (NumberFormatException ignored) {
      try {
        Duration duration = Duration.between(
          Instant.now(),
          ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
        );
        return duration.isNegative() ? Duration.ofSeconds(1) : duration;
      } catch (RuntimeException ignoredDate) {
        return backoff(attempt);
      }
    }
  }

  private static Duration backoff(int attempt) {
    return Duration.ofMillis(Math.min(10_000L, 250L << Math.min(attempt, 5)));
  }

  private static void sleep(Duration duration, String assetId) {
    try {
      Thread.sleep(duration);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw providerError(assetId, null, "MineSkin wait was interrupted", true, exception);
    }
  }

  private void acquire(String assetId) {
    try {
      concurrency.acquire();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw providerError(assetId, null, "MineSkin queue wait was interrupted", true, exception);
    }
  }

  private static SpriteException providerError(
    String assetId,
    Integer status,
    String message,
    boolean retryable,
    Throwable cause
  ) {
    return new SpriteException(assetId, message, retryable, status, cause);
  }

  @Override
  public void close() {
    if (ownsExecutor) executor.close();
  }

  @FunctionalInterface
  private interface RequestFactory {
    HttpRequest create();
  }
}
