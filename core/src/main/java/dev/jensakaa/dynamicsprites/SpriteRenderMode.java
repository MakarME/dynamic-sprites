package dev.jensakaa.dynamicsprites;

public enum SpriteRenderMode {
  OPAQUE_TILE(0),
  LOSSLESS_RGBA_TILE(1),
  FLAT_HEAD(2),
  ISOMETRIC_HEAD(3);

  private final int shaderCode;

  SpriteRenderMode(int shaderCode) {
    this.shaderCode = shaderCode;
  }

  public int shaderCode() {
    return shaderCode;
  }
}
