package dev.jensakaa.dynamicsprites;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

public final class ContentAddressedDiskSpriteCacheStore implements SpriteCacheStore {
  private final Path manifests;
  private final Path payloads;
  private final long maximumPayloadBytes;
  private final Object lock = new Object();

  public ContentAddressedDiskSpriteCacheStore(Path root, long maximumPayloadBytes) throws IOException {
    this.manifests = root.resolve("properties");
    this.payloads = root.resolve("payloads");
    this.maximumPayloadBytes = maximumPayloadBytes;
    Files.createDirectories(manifests);
    Files.createDirectories(payloads);
  }

  @Override
  public Optional<TextureProperty> find(String tileHash) throws IOException {
    synchronized (lock) {
      Path manifest = manifest(tileHash);
      if (!Files.isRegularFile(manifest)) return Optional.empty();
      Properties properties;
      try {
        properties = load(manifest);
      } catch (IOException exception) {
        quarantine(manifest);
        return Optional.empty();
      }
      String value = properties.getProperty("value");
      if (value == null || value.isBlank()) {
        quarantine(manifest);
        return Optional.empty();
      }
      touch(tileHash);
      String signature = blankToNull(properties.getProperty("signature"));
      String textureHash = blankToNull(properties.getProperty("textureHash"));
      String url = blankToNull(properties.getProperty("textureUrl"));
      URI textureUrl;
      try {
        textureUrl = url == null ? null : URI.create(url);
      } catch (IllegalArgumentException exception) {
        quarantine(manifest);
        return Optional.empty();
      }
      return Optional.of(new TextureProperty(value, signature, textureHash, textureUrl));
    }
  }

  @Override
  public void put(String tileHash, byte[] encodedSkinPng, TextureProperty property) throws IOException {
    synchronized (lock) {
      Properties manifest = new Properties();
      manifest.setProperty("version", "1");
      manifest.setProperty("tileHash", tileHash);
      manifest.setProperty("value", property.value());
      manifest.setProperty("signature", nullToBlank(property.signature()));
      manifest.setProperty("textureHash", nullToBlank(property.textureHash()));
      manifest.setProperty("textureUrl", property.textureUrl() == null ? "" : property.textureUrl().toString());
      manifest.setProperty("lastAccessEpochMillis", Long.toString(System.currentTimeMillis()));
      atomicWrite(payload(tileHash), encodedSkinPng);
      atomicWrite(manifest(tileHash), manifest);
      prunePayloads();
    }
  }

  @Override
  public void touch(String tileHash) throws IOException {
    synchronized (lock) {
      FileTime now = FileTime.from(Instant.now());
      Path manifest = manifest(tileHash);
      if (Files.exists(manifest)) Files.setLastModifiedTime(manifest, now);
      Path payload = payload(tileHash);
      if (Files.exists(payload)) Files.setLastModifiedTime(payload, now);
    }
  }

  public Path payloadPath(String tileHash) {
    return payload(tileHash);
  }

  private void prunePayloads() throws IOException {
    List<Path> files = new ArrayList<>();
    long total = 0;
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(payloads, "*.png")) {
      for (Path file : stream) {
        if (!Files.isRegularFile(file)) continue;
        files.add(file);
        total += Files.size(file);
      }
    }
    if (total <= maximumPayloadBytes) return;
    files.sort(Comparator.comparing(this::lastModified));
    for (Path file : files) {
      if (total <= maximumPayloadBytes) break;
      long size = Files.size(file);
      Files.deleteIfExists(file);
      total -= size;
    }
  }

  private FileTime lastModified(Path path) {
    try {
      return Files.getLastModifiedTime(path);
    } catch (IOException exception) {
      return FileTime.fromMillis(0);
    }
  }

  private Properties load(Path path) throws IOException {
    Properties properties = new Properties();
    try (InputStream input = Files.newInputStream(path)) {
      properties.load(input);
    } catch (IllegalArgumentException exception) {
      throw new IOException("Corrupt dynamic sprite cache manifest " + path, exception);
    }
    return properties;
  }

  private void atomicWrite(Path target, byte[] bytes) throws IOException {
    Path temporary = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
    try {
      Files.write(temporary, bytes);
      atomicMove(temporary, target);
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  private void atomicWrite(Path target, Properties properties) throws IOException {
    Path temporary = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
    try {
      try (OutputStream output = Files.newOutputStream(temporary)) {
        properties.store(output, "Dynamic sprite texture property");
      }
      atomicMove(temporary, target);
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  private static void atomicMove(Path source, Path target) throws IOException {
    try {
      Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (AtomicMoveNotSupportedException exception) {
      Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private void quarantine(Path path) {
    try {
      Files.move(
        path,
        path.resolveSibling(path.getFileName() + ".corrupt-" + System.currentTimeMillis()),
        StandardCopyOption.REPLACE_EXISTING
      );
    } catch (IOException ignored) {
    }
  }

  private Path manifest(String hash) {
    return manifests.resolve(hash + ".properties");
  }

  private Path payload(String hash) {
    return payloads.resolve(hash + ".png");
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  private static String nullToBlank(String value) {
    return value == null ? "" : value;
  }
}
