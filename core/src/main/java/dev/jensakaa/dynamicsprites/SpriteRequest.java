package dev.jensakaa.dynamicsprites;

import java.util.Objects;

public record SpriteRequest(
  String assetId,
  SpriteSource source,
  int targetWidth,
  int targetHeight,
  ResizeMode resizeMode,
  SpriteRenderMode renderMode
) {
  public SpriteRequest {
    Objects.requireNonNull(assetId, "assetId");
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(resizeMode, "resizeMode");
    Objects.requireNonNull(renderMode, "renderMode");
    if (assetId.isBlank()) throw new IllegalArgumentException("assetId must not be blank");
    if (targetWidth < 0 || targetHeight < 0) {
      throw new IllegalArgumentException("Target dimensions must be zero or positive");
    }
    if ((targetWidth == 0) != (targetHeight == 0)) {
      throw new IllegalArgumentException("Target width and height must both be zero or both be positive");
    }
  }

  public static SpriteRequest original(String assetId, SpriteSource source) {
    return new SpriteRequest(
      assetId,
      source,
      0,
      0,
      ResizeMode.NONE,
      SpriteRenderMode.LOSSLESS_RGBA_TILE
    );
  }
}
