package dev.jensakaa.dynamicsprites.paper;

import dev.jensakaa.dynamicsprites.DynamicSpriteAnimation;
import dev.jensakaa.dynamicsprites.DynamicSpriteAsset;
import dev.jensakaa.dynamicsprites.DynamicSpriteFrame;
import dev.jensakaa.dynamicsprites.DynamicSpriteTile;
import dev.jensakaa.dynamicsprites.SpritePixelSpace;
import dev.jensakaa.dynamicsprites.TextureProperty;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

public final class DynamicSpriteRenderer {
  public static final int PLAYER_COMPONENT_ADVANCE = 8;
  private final PixelSpacingProvider spacingProvider;

  public DynamicSpriteRenderer(PixelSpacingProvider spacingProvider) {
    this.spacingProvider = Objects.requireNonNull(spacingProvider, "spacingProvider");
  }

  public List<DynamicSpriteNode> nodes(
    DynamicSpriteAsset asset,
    int x,
    int y,
    SpritePixelSpace pixelSpace
  ) {
    return nodes(asset.tiles(), x, y, pixelSpace);
  }

  public List<DynamicSpriteNode> nodes(
    DynamicSpriteFrame frame,
    int x,
    int y,
    SpritePixelSpace pixelSpace
  ) {
    return nodes(frame.tiles(), x, y, pixelSpace);
  }

  public Component hud(
    DynamicSpriteAsset asset,
    int x,
    int y,
    SpritePixelSpace pixelSpace
  ) {
    return renderNodes(nodes(asset, x, y, pixelSpace), false, asset.width());
  }

  public Component hud(
    DynamicSpriteAnimation animation,
    long elapsedTicks,
    int x,
    int y,
    SpritePixelSpace pixelSpace
  ) {
    return renderNodes(nodes(animation.frameAt(elapsedTicks), x, y, pixelSpace), false, animation.width());
  }

  public Component inline(
    DynamicSpriteAsset asset,
    int y,
    SpritePixelSpace pixelSpace
  ) {
    return renderNodes(nodes(asset, 0, y, pixelSpace), true, asset.width());
  }

  public Component inline(
    DynamicSpriteAnimation animation,
    long elapsedTicks,
    int y,
    SpritePixelSpace pixelSpace
  ) {
    return renderNodes(
      nodes(animation.frameAt(elapsedTicks), 0, y, pixelSpace),
      true,
      animation.width()
    );
  }

  public List<Component> preloadBatches(DynamicSpriteAsset asset, int batchSize) {
    if (batchSize <= 0) throw new IllegalArgumentException("batchSize must be positive");
    Set<TextureProperty> textures = new LinkedHashSet<>();
    asset.tiles().forEach(tile -> textures.add(tile.texture()));
    List<Component> batches = new ArrayList<>();
    TextComponent.Builder current = Component.text();
    int count = 0;
    for (TextureProperty texture : textures) {
      Component marked = mark(playerComponent(texture), -256, asset.tiles().getFirst().renderMode(), SpritePixelSpace.GUI);
      current.append(marked).append(spacingProvider.spacing(-PLAYER_COMPONENT_ADVANCE));
      count++;
      if (count == batchSize) {
        batches.add(current.build().shadowColor(ShadowColor.none()));
        current = Component.text();
        count = 0;
      }
    }
    if (count > 0) batches.add(current.build().shadowColor(ShadowColor.none()));
    return List.copyOf(batches);
  }

  private List<DynamicSpriteNode> nodes(
    List<DynamicSpriteTile> tiles,
    int baseX,
    int baseY,
    SpritePixelSpace pixelSpace
  ) {
    return tiles.stream().map(tile -> new DynamicSpriteNode(
      tile.texture(),
      baseX + tile.x(),
      baseY + tile.y(),
      visualWidth(tile),
      visualHeight(tile),
      tile.renderMode(),
      pixelSpace
    )).toList();
  }

  private Component renderNodes(List<DynamicSpriteNode> nodes, boolean preserveAdvance, int width) {
    TextComponent.Builder builder = Component.text();
    for (DynamicSpriteNode node : nodes) {
      builder
        .append(spacingProvider.spacing(node.x()))
        .append(mark(playerComponent(node.texture()), node.y(), node.renderMode(), node.pixelSpace()))
        .append(spacingProvider.spacing(-(node.x() + PLAYER_COMPONENT_ADVANCE)));
    }
    if (preserveAdvance) builder.append(spacingProvider.spacing(width));
    return builder.build().shadowColor(ShadowColor.none());
  }

  private static Component mark(
    Component component,
    int y,
    dev.jensakaa.dynamicsprites.SpriteRenderMode mode,
    SpritePixelSpace pixelSpace
  ) {
    return component
      .color(TextColor.color(DynamicSpriteMarker.rgb(y, mode, pixelSpace)))
      .shadowColor(ShadowColor.none());
  }

  public static Component playerComponent(TextureProperty property) {
    String signature = property.signature() == null
      ? ""
      : ",\"signature\":\"" + json(property.signature()) + "\"";
    String json = "{\"player\":{\"properties\":[{\"name\":\"textures\",\"value\":\"" +
      json(property.value()) + "\"" + signature + "}]}}";
    return GsonComponentSerializer.gson().deserialize(json);
  }

  private static int visualWidth(DynamicSpriteTile tile) {
    return switch (tile.renderMode()) {
      case OPAQUE_TILE, LOSSLESS_RGBA_TILE -> 64;
      case FLAT_HEAD, ISOMETRIC_HEAD -> 32;
    };
  }

  private static int visualHeight(DynamicSpriteTile tile) {
    return switch (tile.renderMode()) {
      case OPAQUE_TILE -> 64;
      case LOSSLESS_RGBA_TILE, FLAT_HEAD, ISOMETRIC_HEAD -> 32;
    };
  }

  private static String json(String value) {
    StringBuilder result = new StringBuilder(value.length() + 16);
    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      switch (character) {
        case '\\' -> result.append("\\\\");
        case '"' -> result.append("\\\"");
        case '\n' -> result.append("\\n");
        case '\r' -> result.append("\\r");
        case '\t' -> result.append("\\t");
        default -> result.append(character);
      }
    }
    return result.toString();
  }
}
