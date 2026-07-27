package dev.jensakaa.dynamicsprites;

import java.util.List;

public record DynamicSpriteFrame(int durationTicks, List<DynamicSpriteTile> tiles) {
  public DynamicSpriteFrame {
    if (durationTicks <= 0) throw new IllegalArgumentException("Frame duration must be positive");
    tiles = List.copyOf(tiles);
  }
}
