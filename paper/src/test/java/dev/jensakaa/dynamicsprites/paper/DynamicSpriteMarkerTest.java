package dev.jensakaa.dynamicsprites.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jensakaa.dynamicsprites.SpritePixelSpace;
import dev.jensakaa.dynamicsprites.SpriteRenderMode;
import org.junit.jupiter.api.Test;

class DynamicSpriteMarkerTest {
  @Test
  void allModesSpacesAndYBoundariesRoundTrip() {
    for (SpriteRenderMode mode : SpriteRenderMode.values()) {
      for (SpritePixelSpace space : SpritePixelSpace.values()) {
        for (int y : new int[]{-256, -1, 0, 255}) {
          int rgb = DynamicSpriteMarker.rgb(y, mode, space);
          assertTrue(DynamicSpriteMarker.isDynamicMarker(rgb));
          assertEquals(y, DynamicSpriteMarker.decodeY(rgb));
          assertEquals(mode, DynamicSpriteMarker.decodeMode(rgb));
          assertEquals(space, DynamicSpriteMarker.decodePixelSpace(rgb));
        }
      }
    }
  }
}
