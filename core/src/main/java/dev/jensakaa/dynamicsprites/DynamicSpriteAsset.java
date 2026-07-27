package dev.jensakaa.dynamicsprites;

import java.util.List;
import java.util.Objects;

public record DynamicSpriteAsset(
  String assetId,
  String hash,
  int width,
  int height,
  List<DynamicSpriteTile> tiles
) {
  public DynamicSpriteAsset {
    Objects.requireNonNull(assetId, "assetId");
    Objects.requireNonNull(hash, "hash");
    tiles = List.copyOf(tiles);
  }
}
