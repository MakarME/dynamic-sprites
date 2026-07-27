package dev.jensakaa.dynamicsprites.paper;

import net.kyori.adventure.text.Component;

@FunctionalInterface
public interface PixelSpacingProvider {
  Component spacing(int pixels);
}
