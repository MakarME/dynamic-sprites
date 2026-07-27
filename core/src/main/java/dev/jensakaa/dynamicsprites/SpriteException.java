package dev.jensakaa.dynamicsprites;

public class SpriteException extends RuntimeException {
  private final String assetId;
  private final boolean retryable;
  private final Integer providerStatus;

  public SpriteException(String assetId, String message, boolean retryable) {
    this(assetId, message, retryable, null, null);
  }

  public SpriteException(
    String assetId,
    String message,
    boolean retryable,
    Integer providerStatus,
    Throwable cause
  ) {
    super("Dynamic sprite '" + assetId + "': " + message, cause);
    this.assetId = assetId;
    this.retryable = retryable;
    this.providerStatus = providerStatus;
  }

  public String assetId() {
    return assetId;
  }

  public boolean retryable() {
    return retryable;
  }

  public Integer providerStatus() {
    return providerStatus;
  }
}
