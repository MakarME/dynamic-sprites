package dev.jensakaa.dynamicsprites;

import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class SpritePreparation<T> {
  private final CompletionStage<T> completion;
  private final AtomicBoolean cancelled;
  private final AtomicReference<SpriteProgress> progress;

  SpritePreparation(
    CompletionStage<T> completion,
    AtomicBoolean cancelled,
    AtomicReference<SpriteProgress> progress
  ) {
    this.completion = Objects.requireNonNull(completion, "completion");
    this.cancelled = Objects.requireNonNull(cancelled, "cancelled");
    this.progress = Objects.requireNonNull(progress, "progress");
  }

  public CompletionStage<T> completion() {
    return completion;
  }

  public SpriteProgress progress() {
    return progress.get();
  }

  public boolean cancel() {
    return cancelled.compareAndSet(false, true);
  }

  public boolean isCancelled() {
    return cancelled.get();
  }
}
