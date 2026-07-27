package dev.jensakaa.dynamicsprites;

import java.io.IOException;
import java.util.Optional;

public interface SpriteCacheStore extends AutoCloseable {
  Optional<TextureProperty> find(String tileHash) throws IOException;

  void put(String tileHash, byte[] encodedSkinPng, TextureProperty property) throws IOException;

  default void touch(String tileHash) throws IOException {
  }

  @Override
  default void close() throws IOException {
  }
}
