package dev.jensakaa.dynamicsprites;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;

final class ApngDecoder {
  private static final byte[] PNG_SIGNATURE = {
    (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
  };

  boolean isApng(byte[] bytes) {
    return indexOf(bytes, "acTL".getBytes(StandardCharsets.US_ASCII)) >= 0;
  }

  DecodedAnimation decode(byte[] bytes, String assetId, SpriteLimits limits) throws IOException {
    ParsedApng parsed = parse(bytes);
    if (parsed.frames().size() > limits.maximumAnimationFrames()) {
      throw new SpriteException(
        assetId,
        "APNG contains " + parsed.frames().size() + " frames, limit is " + limits.maximumAnimationFrames(),
        false
      );
    }
    BufferedImage canvas = new BufferedImage(parsed.width(), parsed.height(), BufferedImage.TYPE_INT_ARGB);
    List<DecodedAnimation.DecodedFrame> frames = new ArrayList<>();
    for (Frame frame : parsed.frames()) {
      BufferedImage before = ImageTransforms.argbCopy(canvas);
      BufferedImage image = ImageIO.read(new ByteArrayInputStream(framePng(parsed, frame)));
      if (image == null) throw new IOException("ImageIO could not decode an APNG frame");
      Graphics2D graphics = canvas.createGraphics();
      graphics.setComposite(frame.blendOp() == 0 ? AlphaComposite.Src : AlphaComposite.SrcOver);
      graphics.drawImage(image, frame.x(), frame.y(), null);
      graphics.dispose();
      frames.add(new DecodedAnimation.DecodedFrame(
        ImageTransforms.argbCopy(canvas),
        delayTicks(frame.delayNumerator(), frame.delayDenominator())
      ));
      if (frame.disposeOp() == 1) {
        graphics = canvas.createGraphics();
        graphics.setComposite(AlphaComposite.Clear);
        graphics.fillRect(frame.x(), frame.y(), frame.width(), frame.height());
        graphics.dispose();
      } else if (frame.disposeOp() == 2) {
        canvas = before;
      }
    }
    return new DecodedAnimation(frames, parsed.loopCount());
  }

  private static ParsedApng parse(byte[] bytes) throws IOException {
    if (bytes.length < PNG_SIGNATURE.length ||
      !Arrays.equals(PNG_SIGNATURE, Arrays.copyOf(bytes, PNG_SIGNATURE.length))) {
      throw new IOException("Invalid PNG signature");
    }
    List<Chunk> shared = new ArrayList<>();
    List<Frame> frames = new ArrayList<>();
    FrameBuilder current = null;
    int width = 0;
    int height = 0;
    int loopCount = 0;
    boolean seenImageData = false;
    try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(
      bytes,
      PNG_SIGNATURE.length,
      bytes.length - PNG_SIGNATURE.length
    ))) {
      while (input.available() > 0) {
        int length = input.readInt();
        if (length < 0 || length > input.available() - 8) throw new IOException("Invalid PNG chunk length");
        byte[] typeBytes = input.readNBytes(4);
        String type = new String(typeBytes, StandardCharsets.US_ASCII);
        byte[] data = input.readNBytes(length);
        input.readInt();
        switch (type) {
          case "IHDR" -> {
            width = intAt(data, 0);
            height = intAt(data, 4);
            shared.add(new Chunk(type, data));
          }
          case "acTL" -> loopCount = intAt(data, 4);
          case "fcTL" -> {
            if (current != null) frames.add(current.build());
            current = new FrameBuilder(
              intAt(data, 4),
              intAt(data, 8),
              intAt(data, 12),
              intAt(data, 16),
              ushortAt(data, 20),
              ushortAt(data, 22),
              data[24] & 0xFF,
              data[25] & 0xFF
            );
          }
          case "IDAT" -> {
            seenImageData = true;
            if (current == null) {
              current = new FrameBuilder(width, height, 0, 0, 1, 10, 0, 0);
            }
            current.data().add(data);
          }
          case "fdAT" -> {
            seenImageData = true;
            if (current == null) throw new IOException("APNG fdAT appeared before fcTL");
            current.data().add(Arrays.copyOfRange(data, 4, data.length));
          }
          case "IEND" -> {
          }
          default -> {
            if (!seenImageData && !"acTL".equals(type)) shared.add(new Chunk(type, data));
          }
        }
      }
    }
    if (current != null) frames.add(current.build());
    if (width <= 0 || height <= 0 || frames.isEmpty()) throw new IOException("Incomplete APNG");
    return new ParsedApng(width, height, loopCount, shared, frames);
  }

  private static byte[] framePng(ParsedApng parsed, Frame frame) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (DataOutputStream output = new DataOutputStream(bytes)) {
      output.write(PNG_SIGNATURE);
      for (Chunk chunk : parsed.shared()) {
        if ("IHDR".equals(chunk.type())) {
          byte[] ihdr = Arrays.copyOf(chunk.data(), chunk.data().length);
          putInt(ihdr, 0, frame.width());
          putInt(ihdr, 4, frame.height());
          writeChunk(output, "IHDR", ihdr);
        } else if (!"IEND".equals(chunk.type())) {
          writeChunk(output, chunk.type(), chunk.data());
        }
      }
      for (byte[] data : frame.data()) writeChunk(output, "IDAT", data);
      writeChunk(output, "IEND", new byte[0]);
    }
    return bytes.toByteArray();
  }

  private static void writeChunk(DataOutputStream output, String type, byte[] data) throws IOException {
    byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
    output.writeInt(data.length);
    output.write(typeBytes);
    output.write(data);
    CRC32 crc = new CRC32();
    crc.update(typeBytes);
    crc.update(data);
    output.writeInt((int) crc.getValue());
  }

  private static int delayTicks(int numerator, int denominator) {
    int divisor = denominator == 0 ? 100 : denominator;
    return Math.max(1, (int) Math.round(numerator * 20.0 / divisor));
  }

  private static int intAt(byte[] data, int offset) {
    return (data[offset] & 0xFF) << 24 |
      (data[offset + 1] & 0xFF) << 16 |
      (data[offset + 2] & 0xFF) << 8 |
      data[offset + 3] & 0xFF;
  }

  private static int ushortAt(byte[] data, int offset) {
    return (data[offset] & 0xFF) << 8 | data[offset + 1] & 0xFF;
  }

  private static void putInt(byte[] data, int offset, int value) {
    data[offset] = (byte) (value >>> 24);
    data[offset + 1] = (byte) (value >>> 16);
    data[offset + 2] = (byte) (value >>> 8);
    data[offset + 3] = (byte) value;
  }

  private static int indexOf(byte[] haystack, byte[] needle) {
    outer:
    for (int index = 0; index <= haystack.length - needle.length; index++) {
      for (int part = 0; part < needle.length; part++) {
        if (haystack[index + part] != needle[part]) continue outer;
      }
      return index;
    }
    return -1;
  }

  private record Chunk(String type, byte[] data) {
  }

  private record Frame(
    int width,
    int height,
    int x,
    int y,
    int delayNumerator,
    int delayDenominator,
    int disposeOp,
    int blendOp,
    List<byte[]> data
  ) {
  }

  private record ParsedApng(
    int width,
    int height,
    int loopCount,
    List<Chunk> shared,
    List<Frame> frames
  ) {
  }

  private record FrameBuilder(
    int width,
    int height,
    int x,
    int y,
    int delayNumerator,
    int delayDenominator,
    int disposeOp,
    int blendOp,
    List<byte[]> data
  ) {
    FrameBuilder(
      int width,
      int height,
      int x,
      int y,
      int delayNumerator,
      int delayDenominator,
      int disposeOp,
      int blendOp
    ) {
      this(width, height, x, y, delayNumerator, delayDenominator, disposeOp, blendOp, new ArrayList<>());
    }

    Frame build() {
      return new Frame(
        width,
        height,
        x,
        y,
        delayNumerator,
        delayDenominator,
        disposeOp,
        blendOp,
        List.copyOf(data)
      );
    }
  }
}
