package dev.jensakaa.dynamicsprites;

public record SpriteProgress(Stage stage, int completed, int total, String message) {
  public enum Stage {
    READING,
    DECODING,
    TILING,
    CACHE,
    UPLOADING,
    COMPLETE
  }

  public double ratio() {
    return total <= 0 ? 0.0 : Math.clamp((double) completed / total, 0.0, 1.0);
  }
}
