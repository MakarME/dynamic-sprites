package dev.jensakaa.dynamicsprites.resourcepack;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DynamicSpriteShaderIncludeTest {
  @Test
  void generatedDefinesUseLibraryNamespace() {
    String defines = DynamicSpriteShaderInclude.glslDefines();

    assertTrue(defines.contains("#define DYNAMIC_SPRITE_MARKER_RED "));
    assertTrue(defines.contains("#define DYNAMIC_SPRITE_SIGNATURE_MASK "));
  }
}
