package dev.jensakaa.dynamicsprites;

public record SpriteLimits(
  int maximumVisibleTiles,
  int maximumAnimationFrames,
  int maximumAnimationUniqueTiles,
  long maximumSourceBytes,
  long maximumDecodedPixels,
  long maximumDiskPayloadBytes,
  int memoryEntries
) {
  public static final SpriteLimits DEFAULTS = new SpriteLimits(
    64,
    60,
    128,
    20L * 1024L * 1024L,
    16_000_000L,
    256L * 1024L * 1024L,
    2048
  );

  public SpriteLimits {
    if (maximumVisibleTiles <= 0) throw new IllegalArgumentException("maximumVisibleTiles must be positive");
    if (maximumAnimationFrames <= 0) throw new IllegalArgumentException("maximumAnimationFrames must be positive");
    if (maximumAnimationUniqueTiles <= 0) {
      throw new IllegalArgumentException("maximumAnimationUniqueTiles must be positive");
    }
    if (maximumSourceBytes <= 0 || maximumDecodedPixels <= 0 || maximumDiskPayloadBytes <= 0) {
      throw new IllegalArgumentException("Sprite byte and pixel budgets must be positive");
    }
    if (memoryEntries <= 0) throw new IllegalArgumentException("memoryEntries must be positive");
  }
}
