package dev.jensakaa.dynamicsprites;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class SkinTileEncoderTest {
  private final SkinTileEncoder encoder = new SkinTileEncoder();

  @Test
  void losslessRgbaSurvivesClientAlphaProcessing() throws Exception {
    BufferedImage source = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
    Random random = new Random(71);
    for (int y = 0; y < source.getHeight(); y++) {
      for (int x = 0; x < source.getWidth(); x++) source.setRGB(x, y, random.nextInt());
    }

    EncodedSpriteTile encoded = encoder.encode(source).getFirst();
    BufferedImage processedSkin = javax.imageio.ImageIO.read(
      new java.io.ByteArrayInputStream(encoded.skinPng())
    );
    applyMinecraftLegacyAlphaProcessing(processedSkin);
    EncodedSpriteTile processed = new EncodedSpriteTile(
      encoded.hash(),
      png(processedSkin),
      encoded.x(),
      encoded.y(),
      encoded.width(),
      encoded.height(),
      encoded.renderMode()
    );
    BufferedImage decoded = encoder.decode(processed);

    for (int y = 0; y < source.getHeight(); y++) {
      for (int x = 0; x < source.getWidth(); x++) {
        assertEquals(source.getRGB(x, y), decoded.getRGB(x, y), "pixel " + x + "," + y);
      }
    }
  }

  @Test
  void transparentAndDuplicateTilesAreDetectable() {
    BufferedImage source = new BufferedImage(192, 64, BufferedImage.TYPE_INT_ARGB);
    for (int y = 0; y < 64; y++) {
      for (int x = 0; x < 64; x++) {
        int color = 0xFF0088FF;
        source.setRGB(x, y, color);
        source.setRGB(128 + x, y, color);
      }
    }

    List<EncodedSpriteTile> tiles = encoder.encode(source);

    assertEquals(2, tiles.size());
    assertEquals(tiles.get(0).hash(), tiles.get(1).hash());
    assertEquals(0, tiles.get(0).x());
    assertEquals(128, tiles.get(1).x());
  }

  @Test
  void alphaSafeMappingCoversExactlyHalfTheAtlas() {
    boolean[] used = new boolean[4096];
    for (int logical = 0; logical < 2048; logical++) {
      int physical = SkinTileEncoder.alphaSafeAtlasIndex(logical);
      assertTrue(!used[physical], "duplicate physical pixel " + physical);
      used[physical] = true;
      int x = physical % 64;
      int y = physical / 64;
      assertTrue(!minecraftForcesOpaque(x, y), "mapped into forced-alpha region " + x + "," + y);
    }
    assertEquals(2048, java.util.stream.IntStream.range(0, used.length).filter(index -> used[index]).count());
  }

  private static void applyMinecraftLegacyAlphaProcessing(BufferedImage skin) {
    for (int y = 0; y < 64; y++) {
      for (int x = 0; x < 64; x++) {
        if (minecraftForcesOpaque(x, y)) skin.setRGB(x, y, skin.getRGB(x, y) | 0xFF000000);
      }
    }
  }

  private static boolean minecraftForcesOpaque(int x, int y) {
    return y < 16 && x < 32 ||
      y >= 16 && y < 32 ||
      y >= 48 && x >= 16 && x < 48;
  }

  private static byte[] png(BufferedImage image) throws Exception {
    java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
    javax.imageio.ImageIO.write(image, "PNG", output);
    return output.toByteArray();
  }
}
