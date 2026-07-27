package dev.jensakaa.dynamicsprites;

import java.util.concurrent.CompletionStage;

public interface TextureUploadProvider {
  CompletionStage<TextureProperty> upload(
    String assetId,
    String tileHash,
    byte[] skinPng,
    CancellationToken cancellationToken
  );
}
