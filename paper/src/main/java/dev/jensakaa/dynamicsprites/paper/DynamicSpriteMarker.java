package dev.jensakaa.dynamicsprites.paper;

import dev.jensakaa.dynamicsprites.DynamicSpriteEncoding;
import dev.jensakaa.dynamicsprites.SpritePixelSpace;
import dev.jensakaa.dynamicsprites.SpriteRenderMode;

public final class DynamicSpriteMarker {
  public static final int MARKER_RED = DynamicSpriteEncoding.MARKER_RED;
  public static final int Y_BITS = DynamicSpriteEncoding.Y_BITS;
  public static final int Y_BIAS = DynamicSpriteEncoding.Y_BIAS;
  public static final int Y_MASK = DynamicSpriteEncoding.Y_MASK;
  public static final int MODE_SHIFT = DynamicSpriteEncoding.MODE_SHIFT;
  public static final int MODE_BITS = DynamicSpriteEncoding.MODE_BITS;
  public static final int MODE_MASK = DynamicSpriteEncoding.MODE_MASK;
  public static final int PIXEL_SPACE_SHIFT = DynamicSpriteEncoding.PIXEL_SPACE_SHIFT;
  public static final int SIGNATURE_SHIFT = DynamicSpriteEncoding.SIGNATURE_SHIFT;
  public static final int SIGNATURE = DynamicSpriteEncoding.SIGNATURE;
  public static final int SIGNATURE_MASK = DynamicSpriteEncoding.SIGNATURE_MASK;

  private DynamicSpriteMarker() {
  }

  public static int rgb(int y, SpriteRenderMode mode, SpritePixelSpace pixelSpace) {
    return DynamicSpriteEncoding.rgb(y, mode, pixelSpace);
  }

  public static boolean isDynamicMarker(int rgb) {
    return DynamicSpriteEncoding.isDynamicMarker(rgb);
  }

  public static int decodeY(int rgb) {
    return DynamicSpriteEncoding.decodeY(rgb);
  }

  public static SpriteRenderMode decodeMode(int rgb) {
    return DynamicSpriteEncoding.decodeMode(rgb);
  }

  public static SpritePixelSpace decodePixelSpace(int rgb) {
    return DynamicSpriteEncoding.decodePixelSpace(rgb);
  }
}
