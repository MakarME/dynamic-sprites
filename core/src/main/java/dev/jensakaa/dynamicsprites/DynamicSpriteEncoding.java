package dev.jensakaa.dynamicsprites;

public final class DynamicSpriteEncoding {
  public static final int MARKER_RED = 4;
  public static final int Y_BITS = 9;
  public static final int Y_BIAS = 1 << (Y_BITS - 1);
  public static final int Y_MASK = (1 << Y_BITS) - 1;
  public static final int MODE_SHIFT = Y_BITS;
  public static final int MODE_BITS = 2;
  public static final int MODE_MASK = (1 << MODE_BITS) - 1;
  public static final int PIXEL_SPACE_SHIFT = MODE_SHIFT + MODE_BITS;
  public static final int SIGNATURE_SHIFT = PIXEL_SPACE_SHIFT + 1;
  public static final int SIGNATURE_BITS = 4;
  public static final int SIGNATURE = 0xA;
  public static final int SIGNATURE_MASK = (1 << SIGNATURE_BITS) - 1;

  private DynamicSpriteEncoding() {
  }

  public static int rgb(int y, SpriteRenderMode mode, SpritePixelSpace pixelSpace) {
    if (y < -Y_BIAS || y >= Y_BIAS) {
      throw new IllegalArgumentException(
        "Dynamic sprite Y " + y + " is outside supported range " + -Y_BIAS + ".." + (Y_BIAS - 1)
      );
    }
    int payload = SIGNATURE << SIGNATURE_SHIFT |
      (pixelSpace == SpritePixelSpace.PHYSICAL ? 1 : 0) << PIXEL_SPACE_SHIFT |
      mode.shaderCode() << MODE_SHIFT |
      y + Y_BIAS;
    return MARKER_RED << 16 | payload;
  }

  public static boolean isDynamicMarker(int rgb) {
    return rgb >> 16 == MARKER_RED &&
      ((rgb & 0xFFFF) >> SIGNATURE_SHIFT & SIGNATURE_MASK) == SIGNATURE;
  }

  public static int decodeY(int rgb) {
    return (rgb & Y_MASK) - Y_BIAS;
  }

  public static SpriteRenderMode decodeMode(int rgb) {
    int mode = rgb >> MODE_SHIFT & MODE_MASK;
    for (SpriteRenderMode value : SpriteRenderMode.values()) {
      if (value.shaderCode() == mode) return value;
    }
    throw new IllegalArgumentException("Unknown dynamic sprite render mode " + mode);
  }

  public static SpritePixelSpace decodePixelSpace(int rgb) {
    return (rgb >> PIXEL_SPACE_SHIFT & 1) == 1
      ? SpritePixelSpace.PHYSICAL
      : SpritePixelSpace.GUI;
  }
}
