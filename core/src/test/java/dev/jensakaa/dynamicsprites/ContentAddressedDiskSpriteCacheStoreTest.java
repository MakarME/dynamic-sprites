package dev.jensakaa.dynamicsprites;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContentAddressedDiskSpriteCacheStoreTest {
  @TempDir
  Path temporaryDirectory;

  @Test
  void propertiesSurviveRestartAndPayloadUsesContentAddress() throws Exception {
    TextureProperty property = new TextureProperty(
      "value",
      "signature",
      "texture-hash",
      URI.create("https://textures.minecraft.net/texture/texture-hash")
    );
    byte[] payload = {1, 2, 3, 4};
    try (ContentAddressedDiskSpriteCacheStore cache =
           new ContentAddressedDiskSpriteCacheStore(temporaryDirectory, 1024)) {
      cache.put("tile", payload, property);
      assertArrayEquals(payload, Files.readAllBytes(cache.payloadPath("tile")));
    }

    try (ContentAddressedDiskSpriteCacheStore restarted =
           new ContentAddressedDiskSpriteCacheStore(temporaryDirectory, 1024)) {
      assertEquals(property, restarted.find("tile").orElseThrow());
    }
  }

  @Test
  void corruptManifestIsQuarantined() throws Exception {
    Path properties = temporaryDirectory.resolve("properties");
    Files.createDirectories(properties);
    Files.writeString(properties.resolve("broken.properties"), "\\uZZZZ");
    try (ContentAddressedDiskSpriteCacheStore cache =
           new ContentAddressedDiskSpriteCacheStore(temporaryDirectory, 1024)) {
      assertFalse(cache.find("broken").isPresent());
    }
    try (var files = Files.list(properties)) {
      assertTrue(files.anyMatch(path -> path.getFileName().toString().contains(".corrupt-")));
    }
  }
}
