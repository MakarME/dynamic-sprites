package dev.jensakaa.dynamicsprites.mineskin;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

public record MineSkinV2Settings(
  URI baseUri,
  String apiKey,
  String userAgent,
  int concurrency,
  int maximumRetries,
  Duration requestTimeout,
  Duration pollInterval,
  Duration jobTimeout
) {
  public static MineSkinV2Settings defaults(String apiKey, String userAgent) {
    return new MineSkinV2Settings(
      URI.create("https://api.mineskin.org"),
      apiKey,
      userAgent,
      2,
      5,
      Duration.ofSeconds(20),
      Duration.ofSeconds(2),
      Duration.ofMinutes(3)
    );
  }

  public MineSkinV2Settings {
    Objects.requireNonNull(baseUri, "baseUri");
    Objects.requireNonNull(apiKey, "apiKey");
    Objects.requireNonNull(userAgent, "userAgent");
    Objects.requireNonNull(requestTimeout, "requestTimeout");
    Objects.requireNonNull(pollInterval, "pollInterval");
    Objects.requireNonNull(jobTimeout, "jobTimeout");
    if (apiKey.isBlank()) throw new IllegalArgumentException("MineSkin API key must not be blank");
    if (userAgent.isBlank()) throw new IllegalArgumentException("MineSkin User-Agent must not be blank");
    if (concurrency <= 0) throw new IllegalArgumentException("MineSkin concurrency must be positive");
    if (maximumRetries < 0) throw new IllegalArgumentException("MineSkin maximumRetries must not be negative");
  }
}
