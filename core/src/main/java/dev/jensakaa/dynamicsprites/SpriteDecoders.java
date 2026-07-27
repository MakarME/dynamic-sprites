package dev.jensakaa.dynamicsprites;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import javax.imageio.ImageIO;

final class SpriteDecoders {
  private final GifDecoder gifDecoder = new GifDecoder();
  private final ApngDecoder apngDecoder = new ApngDecoder();

  BufferedImage decodeStatic(byte[] bytes, String assetId) throws IOException {
    BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
    if (image == null) throw new SpriteException(assetId, "source is not a supported image", false);
    return ImageTransforms.argbCopy(image);
  }

  DecodedAnimation decodeAnimation(byte[] bytes, String mediaType, String assetId, SpriteLimits limits)
    throws IOException {
    if (isGif(bytes, mediaType)) return gifDecoder.decode(bytes, assetId, limits);
    if (apngDecoder.isApng(bytes)) return apngDecoder.decode(bytes, assetId, limits);
    BufferedImage image = decodeStatic(bytes, assetId);
    return new DecodedAnimation(
      List.of(new DecodedAnimation.DecodedFrame(image, 1)),
      0
    );
  }

  private static boolean isGif(byte[] bytes, String mediaType) {
    if (mediaType != null && mediaType.toLowerCase().contains("gif")) return true;
    return bytes.length >= 6 &&
      bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F' &&
      bytes[3] == '8' && (bytes[4] == '7' || bytes[4] == '9') && bytes[5] == 'a';
  }
}
