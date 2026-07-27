package dev.jensakaa.dynamicsprites;

import java.net.URI;
import java.util.Objects;

public record TextureProperty(
  String value,
  String signature,
  String textureHash,
  URI textureUrl
) {
  public TextureProperty {
    Objects.requireNonNull(value, "value");
    if (value.isBlank()) throw new IllegalArgumentException("Texture property value must not be blank");
  }

  public TextureProperty(String value, String signature) {
    this(value, signature, null, null);
  }
}
