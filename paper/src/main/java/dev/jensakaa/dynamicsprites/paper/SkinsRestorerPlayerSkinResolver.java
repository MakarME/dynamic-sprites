package dev.jensakaa.dynamicsprites.paper;

import dev.jensakaa.dynamicsprites.PlayerSkinResolver;
import dev.jensakaa.dynamicsprites.SpriteException;
import dev.jensakaa.dynamicsprites.TextureProperty;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import net.skinsrestorer.api.SkinsRestorerProvider;

public final class SkinsRestorerPlayerSkinResolver implements PlayerSkinResolver {
  @Override
  public CompletionStage<TextureProperty> resolve(UUID playerId) {
    try {
      return SkinsRestorerProvider.get()
        .getPlayerStorage()
        .getSkinOfPlayer(playerId)
        .<CompletionStage<TextureProperty>>map(property -> CompletableFuture.completedFuture(
          new TextureProperty(property.getValue(), property.getSignature())
        ))
        .orElseGet(() -> CompletableFuture.failedFuture(
          new SpriteException(playerId.toString(), "SkinsRestorer has no texture property for player", true)
        ));
    } catch (RuntimeException exception) {
      return CompletableFuture.failedFuture(new SpriteException(
        playerId.toString(),
        "SkinsRestorer player skin lookup failed",
        true,
        null,
        exception
      ));
    }
  }
}
