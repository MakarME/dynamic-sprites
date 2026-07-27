package dev.jensakaa.dynamicsprites;

import java.awt.image.BufferedImage;
import java.util.List;

record DecodedAnimation(List<DecodedFrame> frames, int loopCount) {
  DecodedAnimation {
    frames = List.copyOf(frames);
  }

  record DecodedFrame(BufferedImage image, int durationTicks) {
  }
}
