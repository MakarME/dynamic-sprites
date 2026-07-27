package dev.jensakaa.dynamicsprites;

import java.util.Objects;

public record AnimationRequest(
  String assetId,
  SpriteSource source,
  int targetWidth,
  int targetHeight,
  ResizeMode resizeMode
) {
  public AnimationRequest {
    Objects.requireNonNull(assetId, "assetId");
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(resizeMode, "resizeMode");
    if (assetId.isBlank()) throw new IllegalArgumentException("assetId must not be blank");
    if (targetWidth < 0 || targetHeight < 0) {
      throw new IllegalArgumentException("Target dimensions must be zero or positive");
    }
    if ((targetWidth == 0) != (targetHeight == 0)) {
      throw new IllegalArgumentException("Target width and height must both be zero or both be positive");
    }
  }
}
