package dev.jensakaa.dynamicsprites;

import java.util.Objects;

public record DynamicSpriteTile(
  String hash,
  TextureProperty texture,
  int x,
  int y,
  int width,
  int height,
  SpriteRenderMode renderMode
) {
  public DynamicSpriteTile {
    Objects.requireNonNull(hash, "hash");
    Objects.requireNonNull(texture, "texture");
    Objects.requireNonNull(renderMode, "renderMode");
    if (x < 0 || y < 0 || width <= 0 || height <= 0) {
      throw new IllegalArgumentException("Tile position must be non-negative and dimensions positive");
    }
  }
}
