package dev.jensakaa.dynamicsprites;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

final class ImageTransforms {
  private ImageTransforms() {
  }

  static BufferedImage resize(
    BufferedImage source,
    int targetWidth,
    int targetHeight,
    ResizeMode mode
  ) {
    if (targetWidth == 0 || targetHeight == 0 || mode == ResizeMode.NONE) return argbCopy(source);
    BufferedImage result = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
    Graphics2D graphics = result.createGraphics();
    graphics.setComposite(AlphaComposite.Src);
    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
    double scaleX = (double) targetWidth / source.getWidth();
    double scaleY = (double) targetHeight / source.getHeight();
    double scale = switch (mode) {
      case FIT -> Math.min(scaleX, scaleY);
      case FILL -> Math.max(scaleX, scaleY);
      case STRETCH -> 1.0;
      case NONE -> throw new IllegalStateException("NONE resize should have returned the original size");
    };
    int width = mode == ResizeMode.STRETCH ? targetWidth : Math.max(1, (int) Math.round(source.getWidth() * scale));
    int height = mode == ResizeMode.STRETCH ? targetHeight : Math.max(1, (int) Math.round(source.getHeight() * scale));
    int x = (targetWidth - width) / 2;
    int y = (targetHeight - height) / 2;
    graphics.drawImage(source, x, y, width, height, null);
    graphics.dispose();
    return result;
  }

  static BufferedImage argbCopy(BufferedImage source) {
    BufferedImage result = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
    Graphics2D graphics = result.createGraphics();
    graphics.setComposite(AlphaComposite.Src);
    graphics.drawImage(source, 0, 0, null);
    graphics.dispose();
    return result;
  }
}
