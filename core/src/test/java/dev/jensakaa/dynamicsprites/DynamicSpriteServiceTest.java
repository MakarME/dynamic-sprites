package dev.jensakaa.dynamicsprites;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DynamicSpriteServiceTest {
  @TempDir
  Path temporaryDirectory;

  @Test
  void concurrentEqualTilesShareOneUpload() throws Exception {
    AtomicInteger uploads = new AtomicInteger();
    TextureUploadProvider provider = (assetId, hash, png, token) -> {
      uploads.incrementAndGet();
      return CompletableFuture.supplyAsync(() -> new TextureProperty("value-" + hash, "signature"));
    };
    BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
    for (int y = 0; y < 64; y++) {
      for (int x = 0; x < 64; x++) image.setRGB(x, y, 0xFF336699);
    }
    try (DynamicSpriteService service = new DynamicSpriteService(
      provider,
      new ContentAddressedDiskSpriteCacheStore(temporaryDirectory, 1024 * 1024),
      null,
      SpriteLimits.DEFAULTS
    )) {
      SpriteRequest first = SpriteRequest.original("first", new SpriteSource.Image(image));
      SpriteRequest second = SpriteRequest.original("second", new SpriteSource.Image(image));
      CompletableFuture.allOf(
        service.prepare(first).completion().toCompletableFuture(),
        service.prepare(second).completion().toCompletableFuture()
      ).join();
    }
    assertEquals(1, uploads.get());
  }

  @Test
  void readyTexturePropertyCanBeUsedAsEncodedRgbaTile() throws Exception {
    AtomicInteger uploads = new AtomicInteger();
    TextureUploadProvider provider = (assetId, hash, png, token) -> {
      uploads.incrementAndGet();
      return CompletableFuture.failedFuture(new AssertionError("Ready property must not be uploaded"));
    };
    TextureProperty property = new TextureProperty("ready-value", "ready-signature");
    try (DynamicSpriteService service = new DynamicSpriteService(
      provider,
      new ContentAddressedDiskSpriteCacheStore(temporaryDirectory, 1024 * 1024),
      null,
      SpriteLimits.DEFAULTS
    )) {
      DynamicSpriteAsset asset = service.prepare(new SpriteRequest(
        "ready-rgba",
        new SpriteSource.Texture(property),
        40,
        24,
        ResizeMode.NONE,
        SpriteRenderMode.LOSSLESS_RGBA_TILE
      )).completion().toCompletableFuture().join();

      assertEquals(40, asset.width());
      assertEquals(24, asset.height());
      assertEquals(property, asset.tiles().getFirst().texture());
      assertEquals(SpriteRenderMode.LOSSLESS_RGBA_TILE, asset.tiles().getFirst().renderMode());
    }
    assertEquals(0, uploads.get());
  }

  @Test
  void readyTexturePropertyRejectsMultipleTiles() throws Exception {
    TextureUploadProvider provider = (assetId, hash, png, token) ->
      CompletableFuture.failedFuture(new AssertionError("Ready property must not be uploaded"));
    try (DynamicSpriteService service = new DynamicSpriteService(
      provider,
      new ContentAddressedDiskSpriteCacheStore(temporaryDirectory, 1024 * 1024),
      null,
      SpriteLimits.DEFAULTS
    )) {
      SpritePreparation<DynamicSpriteAsset> preparation = service.prepare(new SpriteRequest(
        "oversized-ready-tile",
        new SpriteSource.Texture(new TextureProperty("ready-value", null)),
        65,
        32,
        ResizeMode.NONE,
        SpriteRenderMode.LOSSLESS_RGBA_TILE
      ));
      CompletionException exception = assertThrows(
        CompletionException.class,
        () -> preparation.completion().toCompletableFuture().join()
      );
      assertInstanceOf(SpriteException.class, exception.getCause());
    }
  }
}
