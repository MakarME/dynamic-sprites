package dev.jensakaa.dynamicsprites;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface PlayerSkinResolver {
  CompletionStage<TextureProperty> resolve(UUID playerId);
}
