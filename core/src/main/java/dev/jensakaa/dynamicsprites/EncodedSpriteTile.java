package dev.jensakaa.dynamicsprites;

import java.util.Arrays;

public record EncodedSpriteTile(
  String hash,
  byte[] skinPng,
  int x,
  int y,
  int width,
  int height,
  SpriteRenderMode renderMode
) {
  public EncodedSpriteTile {
    skinPng = Arrays.copyOf(skinPng, skinPng.length);
  }

  @Override
  public byte[] skinPng() {
    return Arrays.copyOf(skinPng, skinPng.length);
  }
}
