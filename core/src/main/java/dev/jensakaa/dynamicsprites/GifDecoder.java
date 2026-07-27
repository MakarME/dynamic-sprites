package dev.jensakaa.dynamicsprites;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

final class GifDecoder {
  DecodedAnimation decode(byte[] bytes, String assetId, SpriteLimits limits) throws IOException {
    Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
    if (!readers.hasNext()) throw new IOException("GIF ImageIO reader is unavailable");
    ImageReader reader = readers.next();
    try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
      reader.setInput(input, false, false);
      int frameCount = reader.getNumImages(true);
      if (frameCount > limits.maximumAnimationFrames()) {
        throw new SpriteException(
          assetId,
          "GIF contains " + frameCount + " frames, limit is " + limits.maximumAnimationFrames(),
          false
        );
      }
      int width = reader.getWidth(0);
      int height = reader.getHeight(0);
      BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
      List<DecodedAnimation.DecodedFrame> frames = new ArrayList<>();
      int loopCount = loopCount(reader.getStreamMetadata());
      for (int index = 0; index < frameCount; index++) {
        IIOMetadata metadata = reader.getImageMetadata(index);
        GifFrameMetadata frameMetadata = frameMetadata(metadata);
        BufferedImage before = ImageTransforms.argbCopy(canvas);
        BufferedImage raw = reader.read(index);
        Graphics2D graphics = canvas.createGraphics();
        graphics.setComposite(AlphaComposite.SrcOver);
        graphics.drawImage(raw, frameMetadata.left(), frameMetadata.top(), null);
        graphics.dispose();
        frames.add(new DecodedAnimation.DecodedFrame(
          ImageTransforms.argbCopy(canvas),
          Math.max(1, (int) Math.round(frameMetadata.delayCentiseconds() / 5.0))
        ));
        switch (frameMetadata.disposal()) {
          case "restoreToBackgroundColor" -> clear(canvas, new Rectangle(
            frameMetadata.left(),
            frameMetadata.top(),
            raw.getWidth(),
            raw.getHeight()
          ));
          case "restoreToPrevious" -> canvas = before;
          default -> {
          }
        }
      }
      return new DecodedAnimation(frames, loopCount);
    } finally {
      reader.dispose();
    }
  }

  private static void clear(BufferedImage image, Rectangle rectangle) {
    Graphics2D graphics = image.createGraphics();
    graphics.setComposite(AlphaComposite.Clear);
    graphics.fill(rectangle);
    graphics.dispose();
  }

  private static GifFrameMetadata frameMetadata(IIOMetadata metadata) {
    Node root = metadata.getAsTree("javax_imageio_gif_image_1.0");
    Node descriptor = child(root, "ImageDescriptor");
    Node control = child(root, "GraphicControlExtension");
    return new GifFrameMetadata(
      integer(attribute(descriptor, "imageLeftPosition"), 0),
      integer(attribute(descriptor, "imageTopPosition"), 0),
      integer(attribute(control, "delayTime"), 1),
      attribute(control, "disposalMethod") == null ? "none" : attribute(control, "disposalMethod")
    );
  }

  private static int loopCount(IIOMetadata metadata) {
    if (metadata == null) return 0;
    Node root = metadata.getAsTree("javax_imageio_gif_stream_1.0");
    Node extensions = child(root, "ApplicationExtensions");
    if (extensions == null) return 0;
    for (Node node = extensions.getFirstChild(); node != null; node = node.getNextSibling()) {
      if (!"ApplicationExtension".equals(node.getNodeName())) continue;
      if (!(node instanceof IIOMetadataNode metadataNode) ||
        !(metadataNode.getUserObject() instanceof byte[] bytes) ||
        bytes.length < 3 ||
        bytes[0] != 1) {
        continue;
      }
      return (bytes[2] & 0xFF) << 8 | bytes[1] & 0xFF;
    }
    return 0;
  }

  private static Node child(Node parent, String name) {
    if (parent == null) return null;
    for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (name.equals(child.getNodeName())) return child;
    }
    return null;
  }

  private static String attribute(Node node, String name) {
    if (node == null) return null;
    NamedNodeMap attributes = node.getAttributes();
    Node attribute = attributes == null ? null : attributes.getNamedItem(name);
    return attribute == null ? null : attribute.getNodeValue();
  }

  private static int integer(String value, int fallback) {
    try {
      return value == null ? fallback : Integer.parseInt(value);
    } catch (NumberFormatException exception) {
      return fallback;
    }
  }

  private record GifFrameMetadata(int left, int top, int delayCentiseconds, String disposal) {
  }
}
