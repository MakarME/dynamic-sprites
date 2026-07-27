package dev.jensakaa.dynamicsprites;

import java.util.List;
import java.util.Objects;

public record DynamicSpriteAnimation(
  String assetId,
  String hash,
  int width,
  int height,
  int loopCount,
  List<DynamicSpriteFrame> frames
) {
  public DynamicSpriteAnimation {
    Objects.requireNonNull(assetId, "assetId");
    Objects.requireNonNull(hash, "hash");
    frames = List.copyOf(frames);
    if (frames.isEmpty()) throw new IllegalArgumentException("Animation must contain at least one frame");
    if (loopCount < 0) throw new IllegalArgumentException("loopCount must be zero or positive");
  }

  public DynamicSpriteFrame frameAt(long elapsedTicks) {
    long total = frames.stream().mapToLong(DynamicSpriteFrame::durationTicks).sum();
    if (total <= 0) return frames.getFirst();
    long tick = loopCount == 0
      ? Math.floorMod(elapsedTicks, total)
      : Math.min(Math.max(0, elapsedTicks), total * loopCount - 1);
    long cursor = 0;
    for (DynamicSpriteFrame frame : frames) {
      cursor += frame.durationTicks();
      if (tick < cursor) return frame;
    }
    return frames.getLast();
  }
}
