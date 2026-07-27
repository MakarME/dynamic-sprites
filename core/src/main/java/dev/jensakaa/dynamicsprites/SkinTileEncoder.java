package dev.jensakaa.dynamicsprites;

import dev.jensakaa.dynamicsprites.internal.Hashes;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

public final class SkinTileEncoder {
  public static final int SKIN_SIZE = 64;
  public static final int OPAQUE_TILE_HEIGHT = 64;
  public static final int RGBA_TILE_HEIGHT = 32;
  public static final String ENCODER_VERSION = "skin-atlas-v1";

  public List<EncodedSpriteTile> encode(BufferedImage image) {
    List<EncodedSpriteTile> result = new ArrayList<>();
    for (int tileY = 0; tileY < image.getHeight(); tileY += OPAQUE_TILE_HEIGHT) {
      for (int tileX = 0; tileX < image.getWidth(); tileX += SKIN_SIZE) {
        int width = Math.min(SKIN_SIZE, image.getWidth() - tileX);
        int height = Math.min(OPAQUE_TILE_HEIGHT, image.getHeight() - tileY);
        if (width == SKIN_SIZE && height == OPAQUE_TILE_HEIGHT && isOpaque(image, tileX, tileY, width, height)) {
          result.add(encodeOpaque(image, tileX, tileY));
          continue;
        }
        int upperHeight = Math.min(RGBA_TILE_HEIGHT, height);
        if (!isTransparent(image, tileX, tileY, width, upperHeight)) {
          result.add(encodeRgba(image, tileX, tileY, width, upperHeight));
        }
        int lowerHeight = height - RGBA_TILE_HEIGHT;
        if (lowerHeight > 0 && !isTransparent(image, tileX, tileY + RGBA_TILE_HEIGHT, width, lowerHeight)) {
          result.add(encodeRgba(
            image,
            tileX,
            tileY + RGBA_TILE_HEIGHT,
            width,
            lowerHeight
          ));
        }
      }
    }
    return List.copyOf(result);
  }

  public BufferedImage decode(EncodedSpriteTile tile) throws IOException {
    BufferedImage skin = ImageIO.read(new java.io.ByteArrayInputStream(tile.skinPng()));
    if (skin == null || skin.getWidth() != SKIN_SIZE || skin.getHeight() != SKIN_SIZE) {
      throw new IOException("Encoded tile is not a 64x64 PNG");
    }
    BufferedImage result = new BufferedImage(tile.width(), tile.height(), BufferedImage.TYPE_INT_ARGB);
    if (tile.renderMode() == SpriteRenderMode.OPAQUE_TILE) {
      for (int y = 0; y < tile.height(); y++) {
        for (int x = 0; x < tile.width(); x++) result.setRGB(x, y, skin.getRGB(x, y));
      }
      return result;
    }
    for (int y = 0; y < tile.height(); y++) {
      for (int x = 0; x < tile.width(); x++) {
        int source = alphaSafeAtlasIndex(y * SKIN_SIZE + x);
        result.setRGB(x, y, skin.getRGB(source % 64, source / 64));
      }
    }
    return result;
  }

  public static int alphaSafeAtlasIndex(int logicalIndex) {
    if (logicalIndex < 0 || logicalIndex >= SKIN_SIZE * RGBA_TILE_HEIGHT) {
      throw new IllegalArgumentException("RGBA logical pixel index must be in 0..2047");
    }
    if (logicalIndex < 512) {
      return (logicalIndex / 32) * 64 + 32 + logicalIndex % 32;
    }
    if (logicalIndex < 1536) {
      int index = logicalIndex - 512;
      return (32 + index / 64) * 64 + index % 64;
    }
    int index = logicalIndex - 1536;
    int localX = index % 32;
    int physicalX = localX < 16 ? localX : localX + 32;
    return (48 + index / 32) * 64 + physicalX;
  }

  private EncodedSpriteTile encodeOpaque(BufferedImage image, int tileX, int tileY) {
    BufferedImage skin = new BufferedImage(SKIN_SIZE, SKIN_SIZE, BufferedImage.TYPE_INT_ARGB);
    for (int y = 0; y < SKIN_SIZE; y++) {
      for (int x = 0; x < SKIN_SIZE; x++) skin.setRGB(x, y, image.getRGB(tileX + x, tileY + y));
    }
    byte[] png = png(skin);
    return new EncodedSpriteTile(
      Hashes.sha256(png),
      png,
      tileX,
      tileY,
      SKIN_SIZE,
      SKIN_SIZE,
      SpriteRenderMode.OPAQUE_TILE
    );
  }

  private EncodedSpriteTile encodeRgba(
    BufferedImage image,
    int tileX,
    int tileY,
    int width,
    int height
  ) {
    BufferedImage skin = new BufferedImage(SKIN_SIZE, SKIN_SIZE, BufferedImage.TYPE_INT_ARGB);
    for (int y = 0; y < SKIN_SIZE; y++) {
      for (int x = 0; x < SKIN_SIZE; x++) skin.setRGB(x, y, 0xFF000000);
    }
    for (int index = 0; index < SKIN_SIZE * RGBA_TILE_HEIGHT; index++) {
      int logicalX = index % SKIN_SIZE;
      int logicalY = index / SKIN_SIZE;
      int color = logicalX < width && logicalY < height
        ? image.getRGB(tileX + logicalX, tileY + logicalY)
        : 0;
      int target = alphaSafeAtlasIndex(index);
      skin.setRGB(target % SKIN_SIZE, target / SKIN_SIZE, color);
    }
    byte[] png = png(skin);
    return new EncodedSpriteTile(
      Hashes.sha256(png),
      png,
      tileX,
      tileY,
      width,
      height,
      SpriteRenderMode.LOSSLESS_RGBA_TILE
    );
  }

  private static boolean isOpaque(
    BufferedImage image,
    int startX,
    int startY,
    int width,
    int height
  ) {
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        if ((image.getRGB(startX + x, startY + y) >>> 24) != 255) return false;
      }
    }
    return true;
  }

  private static boolean isTransparent(
    BufferedImage image,
    int startX,
    int startY,
    int width,
    int height
  ) {
    if (width <= 0 || height <= 0) return true;
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        if ((image.getRGB(startX + x, startY + y) >>> 24) != 0) return false;
      }
    }
    return true;
  }

  private static byte[] png(BufferedImage image) {
    try {
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      if (!ImageIO.write(image, "PNG", output)) throw new IOException("PNG writer is unavailable");
      return output.toByteArray();
    } catch (IOException exception) {
      throw new IllegalStateException("Failed to encode skin PNG", exception);
    }
  }
}
