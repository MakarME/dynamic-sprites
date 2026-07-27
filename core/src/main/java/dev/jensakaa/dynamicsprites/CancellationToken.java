package dev.jensakaa.dynamicsprites;

@FunctionalInterface
public interface CancellationToken {
  boolean isCancelled();

  default void throwIfCancelled(String assetId) {
    if (isCancelled()) throw new SpriteException(assetId, "preparation was cancelled", false);
  }
}
