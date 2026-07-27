package dev.jensakaa.dynamicsprites.paper;

import dev.jensakaa.dynamicsprites.SpritePixelSpace;
import dev.jensakaa.dynamicsprites.SpriteRenderMode;
import dev.jensakaa.dynamicsprites.TextureProperty;

public record DynamicSpriteNode(
  TextureProperty texture,
  int x,
  int y,
  int visualWidth,
  int visualHeight,
  SpriteRenderMode renderMode,
  SpritePixelSpace pixelSpace
) {
}
