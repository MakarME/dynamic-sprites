package dev.jensakaa.dynamicsprites;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public sealed interface SpriteSource permits
  SpriteSource.Bytes,
  SpriteSource.File,
  SpriteSource.Url,
  SpriteSource.Image,
  SpriteSource.Texture,
  SpriteSource.PlayerSkin {

  record Bytes(byte[] data, String mediaType) implements SpriteSource {
    public Bytes {
      Objects.requireNonNull(data, "data");
      data = Arrays.copyOf(data, data.length);
    }

    @Override
    public byte[] data() {
      return Arrays.copyOf(data, data.length);
    }
  }

  record File(Path path) implements SpriteSource {
    public File {
      Objects.requireNonNull(path, "path");
    }
  }

  record Url(URI uri) implements SpriteSource {
    public Url {
      Objects.requireNonNull(uri, "uri");
    }
  }

  record Image(BufferedImage image) implements SpriteSource {
    public Image {
      Objects.requireNonNull(image, "image");
      BufferedImage copy = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
      Graphics2D graphics = copy.createGraphics();
      graphics.drawImage(image, 0, 0, null);
      graphics.dispose();
      image = copy;
    }
  }

  record Texture(TextureProperty property) implements SpriteSource {
    public Texture {
      Objects.requireNonNull(property, "property");
    }
  }

  record PlayerSkin(UUID playerId) implements SpriteSource {
    public PlayerSkin {
      Objects.requireNonNull(playerId, "playerId");
    }
  }
}
