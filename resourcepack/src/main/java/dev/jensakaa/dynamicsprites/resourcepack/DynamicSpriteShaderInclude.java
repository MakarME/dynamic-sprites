package dev.jensakaa.dynamicsprites.resourcepack;

import dev.jensakaa.dynamicsprites.DynamicSpriteEncoding;
import java.util.Set;

public final class DynamicSpriteShaderInclude {
  private DynamicSpriteShaderInclude() {
  }

  public static void validateMarkerConflicts(Set<Integer> reservedMarkers) {
    if (reservedMarkers.contains(DynamicSpriteEncoding.MARKER_RED)) {
      throw new IllegalArgumentException(
        "Dynamic sprite marker R=" + DynamicSpriteEncoding.MARKER_RED +
          " conflicts with an existing HUD marker"
      );
    }
  }

  public static String glslDefines() {
    return "#define DYNAMIC_SPRITE_MARKER_RED " + DynamicSpriteEncoding.MARKER_RED + "\n" +
      "#define DYNAMIC_SPRITE_Y_BITS " + DynamicSpriteEncoding.Y_BITS + "\n" +
      "#define DYNAMIC_SPRITE_Y_BIAS " + DynamicSpriteEncoding.Y_BIAS + "\n" +
      "#define DYNAMIC_SPRITE_Y_MASK " + DynamicSpriteEncoding.Y_MASK + "\n" +
      "#define DYNAMIC_SPRITE_MODE_SHIFT " + DynamicSpriteEncoding.MODE_SHIFT + "\n" +
      "#define DYNAMIC_SPRITE_MODE_MASK " + DynamicSpriteEncoding.MODE_MASK + "\n" +
      "#define DYNAMIC_SPRITE_PIXEL_SPACE_SHIFT " +
        DynamicSpriteEncoding.PIXEL_SPACE_SHIFT + "\n" +
      "#define DYNAMIC_SPRITE_SIGNATURE_SHIFT " + DynamicSpriteEncoding.SIGNATURE_SHIFT + "\n" +
      "#define DYNAMIC_SPRITE_SIGNATURE " + DynamicSpriteEncoding.SIGNATURE + "\n" +
      "#define DYNAMIC_SPRITE_SIGNATURE_MASK " + DynamicSpriteEncoding.SIGNATURE_MASK + "\n";
  }
}
