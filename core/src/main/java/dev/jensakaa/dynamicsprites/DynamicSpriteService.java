package dev.jensakaa.dynamicsprites;

import dev.jensakaa.dynamicsprites.internal.Hashes;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;

public final class DynamicSpriteService implements AutoCloseable {
  private final TextureUploadProvider uploadProvider;
  private final SpriteCacheStore cacheStore;
  private final PlayerSkinResolver playerSkinResolver;
  private final SpriteLimits limits;
  private final ExecutorService executor;
  private final boolean ownsExecutor;
  private final SourceReader sourceReader;
  private final SpriteDecoders decoders = new SpriteDecoders();
  private final SkinTileEncoder tileEncoder = new SkinTileEncoder();
  private final ConcurrentHashMap<String, CompletableFuture<TextureProperty>> inFlight = new ConcurrentHashMap<>();
  private final Map<String, TextureProperty> memoryCache;

  public DynamicSpriteService(
    TextureUploadProvider uploadProvider,
    SpriteCacheStore cacheStore,
    PlayerSkinResolver playerSkinResolver,
    SpriteLimits limits
  ) {
    this(
      uploadProvider,
      cacheStore,
      playerSkinResolver,
      limits,
      Executors.newVirtualThreadPerTaskExecutor(),
      true,
      HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()
    );
  }

  public DynamicSpriteService(
    TextureUploadProvider uploadProvider,
    SpriteCacheStore cacheStore,
    PlayerSkinResolver playerSkinResolver,
    SpriteLimits limits,
    ExecutorService executor,
    HttpClient httpClient
  ) {
    this(uploadProvider, cacheStore, playerSkinResolver, limits, executor, false, httpClient);
  }

  private DynamicSpriteService(
    TextureUploadProvider uploadProvider,
    SpriteCacheStore cacheStore,
    PlayerSkinResolver playerSkinResolver,
    SpriteLimits limits,
    ExecutorService executor,
    boolean ownsExecutor,
    HttpClient httpClient
  ) {
    this.uploadProvider = Objects.requireNonNull(uploadProvider, "uploadProvider");
    this.cacheStore = Objects.requireNonNull(cacheStore, "cacheStore");
    this.playerSkinResolver = playerSkinResolver;
    this.limits = Objects.requireNonNull(limits, "limits");
    this.executor = Objects.requireNonNull(executor, "executor");
    this.ownsExecutor = ownsExecutor;
    this.sourceReader = new SourceReader(Objects.requireNonNull(httpClient, "httpClient"), limits);
    this.memoryCache = Collections.synchronizedMap(new LinkedHashMap<>(128, 0.75f, true) {
      @Override
      protected boolean removeEldestEntry(Map.Entry<String, TextureProperty> eldest) {
        return size() > DynamicSpriteService.this.limits.memoryEntries();
      }
    });
  }

  public SpritePreparation<DynamicSpriteAsset> prepare(SpriteRequest request) {
    AtomicBoolean cancelled = new AtomicBoolean();
    AtomicReference<SpriteProgress> progress = progress();
    CompletableFuture<DynamicSpriteAsset> completion = CompletableFuture.supplyAsync(
      () -> prepareAsset(request, cancelled, progress),
      executor
    );
    return new SpritePreparation<>(completion, cancelled, progress);
  }

  public SpritePreparation<DynamicSpriteAnimation> prepareAnimation(AnimationRequest request) {
    AtomicBoolean cancelled = new AtomicBoolean();
    AtomicReference<SpriteProgress> progress = progress();
    CompletableFuture<DynamicSpriteAnimation> completion = CompletableFuture.supplyAsync(
      () -> prepareAnimation(request, cancelled, progress),
      executor
    );
    return new SpritePreparation<>(completion, cancelled, progress);
  }

  private DynamicSpriteAsset prepareAsset(
    SpriteRequest request,
    AtomicBoolean cancelled,
    AtomicReference<SpriteProgress> progress
  ) {
    CancellationToken token = cancelled::get;
    try {
      if (request.source() instanceof SpriteSource.Texture texture) {
        return propertyAsset(request, texture.property());
      }
      if (request.source() instanceof SpriteSource.PlayerSkin playerSkin) {
        if (playerSkinResolver == null) {
          throw new SpriteException(request.assetId(), "no PlayerSkinResolver is configured", false);
        }
        TextureProperty property = playerSkinResolver.resolve(playerSkin.playerId()).toCompletableFuture().join();
        return propertyAsset(request, property);
      }
      progress.set(new SpriteProgress(SpriteProgress.Stage.READING, 0, 1, "Reading source"));
      BufferedImage image = readImage(request.source(), request.assetId(), token);
      progress.set(new SpriteProgress(SpriteProgress.Stage.DECODING, 1, 1, "Decoded image"));
      if (request.renderMode() == SpriteRenderMode.FLAT_HEAD ||
        request.renderMode() == SpriteRenderMode.ISOMETRIC_HEAD) {
        validatePixels(request.assetId(), image.getWidth(), image.getHeight(), 1);
        return prepareSkinAsset(request, image, token, progress);
      }
      image = ImageTransforms.resize(image, request.targetWidth(), request.targetHeight(), request.resizeMode());
      validatePixels(request.assetId(), image.getWidth(), image.getHeight(), 1);
      String assetHash = imageHash(image, request.resizeMode());
      List<EncodedSpriteTile> encoded = tileEncoder.encode(image);
      validateVisibleTiles(request.assetId(), encoded.size());
      List<DynamicSpriteTile> tiles = uploadTiles(request.assetId(), encoded, token, progress);
      progress.set(new SpriteProgress(SpriteProgress.Stage.COMPLETE, tiles.size(), tiles.size(), "Ready"));
      return new DynamicSpriteAsset(request.assetId(), assetHash, image.getWidth(), image.getHeight(), tiles);
    } catch (CompletionException exception) {
      throw unwrap(exception);
    } catch (IOException exception) {
      throw new SpriteException(request.assetId(), exception.getMessage(), false, null, exception);
    }
  }

  private DynamicSpriteAnimation prepareAnimation(
    AnimationRequest request,
    AtomicBoolean cancelled,
    AtomicReference<SpriteProgress> progress
  ) {
    CancellationToken token = cancelled::get;
    try {
      if (request.source() instanceof SpriteSource.Texture ||
        request.source() instanceof SpriteSource.PlayerSkin) {
        throw new SpriteException(request.assetId(), "texture properties cannot be decoded as an animation", false);
      }
      progress.set(new SpriteProgress(SpriteProgress.Stage.READING, 0, 1, "Reading animation"));
      DecodedAnimation decoded;
      if (request.source() instanceof SpriteSource.Image image) {
        decoded = new DecodedAnimation(
          List.of(new DecodedAnimation.DecodedFrame(ImageTransforms.argbCopy(image.image()), 1)),
          0
        );
      } else {
        SourceReader.LoadedSource source = sourceReader.read(request.source(), request.assetId(), token);
        decoded = decoders.decodeAnimation(source.bytes(), source.mediaType(), request.assetId(), limits);
      }
      validateDecodedAnimation(request.assetId(), decoded);
      List<PreparedFrame> preparedFrames = new ArrayList<>();
      Set<String> uniqueTiles = new LinkedHashSet<>();
      int targetWidth = 0;
      int targetHeight = 0;
      progress.set(new SpriteProgress(
        SpriteProgress.Stage.TILING,
        0,
        decoded.frames().size(),
        "Encoding animation frames"
      ));
      for (int index = 0; index < decoded.frames().size(); index++) {
        token.throwIfCancelled(request.assetId());
        DecodedAnimation.DecodedFrame frame = decoded.frames().get(index);
        BufferedImage image = ImageTransforms.resize(
          frame.image(),
          request.targetWidth(),
          request.targetHeight(),
          request.resizeMode()
        );
        targetWidth = image.getWidth();
        targetHeight = image.getHeight();
        List<EncodedSpriteTile> encoded = tileEncoder.encode(image);
        validateVisibleTiles(request.assetId(), encoded.size());
        encoded.forEach(tile -> uniqueTiles.add(tile.hash()));
        if (uniqueTiles.size() > limits.maximumAnimationUniqueTiles()) {
          throw new SpriteException(
            request.assetId(),
            "animation requires " + uniqueTiles.size() + " unique tiles, limit is " +
              limits.maximumAnimationUniqueTiles(),
            false
          );
        }
        preparedFrames.add(new PreparedFrame(frame.durationTicks(), encoded));
        progress.set(new SpriteProgress(
          SpriteProgress.Stage.TILING,
          index + 1,
          decoded.frames().size(),
          "Encoded frame " + (index + 1)
        ));
      }
      Map<String, TextureProperty> uploaded = uploadUniqueTiles(
        request.assetId(),
        preparedFrames,
        token,
        progress
      );
      List<DynamicSpriteFrame> frames = coalesceFrames(preparedFrames, uploaded);
      String animationHash = animationHash(preparedFrames, request.resizeMode());
      progress.set(new SpriteProgress(SpriteProgress.Stage.COMPLETE, frames.size(), frames.size(), "Ready"));
      return new DynamicSpriteAnimation(
        request.assetId(),
        animationHash,
        targetWidth,
        targetHeight,
        decoded.loopCount(),
        frames
      );
    } catch (CompletionException exception) {
      throw unwrap(exception);
    } catch (IOException exception) {
      throw new SpriteException(request.assetId(), exception.getMessage(), false, null, exception);
    }
  }

  private DynamicSpriteAsset prepareSkinAsset(
    SpriteRequest request,
    BufferedImage source,
    CancellationToken token,
    AtomicReference<SpriteProgress> progress
  ) {
    if (source.getWidth() != 64 || source.getHeight() != 64) {
      throw new SpriteException(
        request.assetId(),
        request.renderMode() + " requires a 64x64 Minecraft skin, got " +
          source.getWidth() + "x" + source.getHeight(),
        false
      );
    }
    validateHeadSize(request);
    byte[] png = png(source);
    String hash = Hashes.sha256(png);
    TextureProperty property = resolveTile(request.assetId(), hash, png, token).join();
    int width = request.targetWidth() == 0 ? 32 : request.targetWidth();
    int height = request.targetHeight() == 0 ? 32 : request.targetHeight();
    progress.set(new SpriteProgress(SpriteProgress.Stage.COMPLETE, 1, 1, "Ready"));
    return new DynamicSpriteAsset(
      request.assetId(),
      hash,
      width,
      height,
      List.of(new DynamicSpriteTile(hash, property, 0, 0, width, height, request.renderMode()))
    );
  }

  private DynamicSpriteAsset propertyAsset(SpriteRequest request, TextureProperty property) {
    int maximumWidth = switch (request.renderMode()) {
      case OPAQUE_TILE, LOSSLESS_RGBA_TILE -> 64;
      case FLAT_HEAD, ISOMETRIC_HEAD -> 32;
    };
    int maximumHeight = switch (request.renderMode()) {
      case OPAQUE_TILE -> 64;
      case LOSSLESS_RGBA_TILE, FLAT_HEAD, ISOMETRIC_HEAD -> 32;
    };
    if (request.renderMode() == SpriteRenderMode.FLAT_HEAD ||
      request.renderMode() == SpriteRenderMode.ISOMETRIC_HEAD) {
      validateHeadSize(request);
    } else if (request.targetWidth() > maximumWidth || request.targetHeight() > maximumHeight) {
      throw new SpriteException(
        request.assetId(),
        "ready " + request.renderMode() + " texture is one tile and cannot exceed " +
          maximumWidth + "x" + maximumHeight + "; requested " +
          request.targetWidth() + "x" + request.targetHeight(),
        false
      );
    }
    int width = request.targetWidth() == 0 ? maximumWidth : request.targetWidth();
    int height = request.targetHeight() == 0 ? maximumHeight : request.targetHeight();
    String hash = property.textureHash() == null
      ? Hashes.sha256(Hashes.utf8(property.value()))
      : property.textureHash();
    DynamicSpriteTile tile = new DynamicSpriteTile(
      hash,
      property,
      0,
      0,
      width,
      height,
      request.renderMode()
    );
    return new DynamicSpriteAsset(request.assetId(), hash, width, height, List.of(tile));
  }

  private BufferedImage readImage(SpriteSource source, String assetId, CancellationToken token)
    throws IOException {
    if (source instanceof SpriteSource.Image image) return ImageTransforms.argbCopy(image.image());
    SourceReader.LoadedSource loaded = sourceReader.read(source, assetId, token);
    return decoders.decodeStatic(loaded.bytes(), assetId);
  }

  private List<DynamicSpriteTile> uploadTiles(
    String assetId,
    List<EncodedSpriteTile> encoded,
    CancellationToken token,
    AtomicReference<SpriteProgress> progress
  ) {
    List<CompletableFuture<DynamicSpriteTile>> futures = new ArrayList<>();
    for (EncodedSpriteTile tile : encoded) {
      futures.add(resolveTile(assetId, tile.hash(), tile.skinPng(), token).thenApply(property ->
        new DynamicSpriteTile(
          tile.hash(),
          property,
          tile.x(),
          tile.y(),
          tile.width(),
          tile.height(),
          tile.renderMode()
        )
      ));
    }
    progress.set(new SpriteProgress(SpriteProgress.Stage.UPLOADING, 0, futures.size(), "Resolving textures"));
    CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
    List<DynamicSpriteTile> result = futures.stream().map(CompletableFuture::join).toList();
    progress.set(new SpriteProgress(SpriteProgress.Stage.UPLOADING, result.size(), result.size(), "Textures ready"));
    return result;
  }

  private Map<String, TextureProperty> uploadUniqueTiles(
    String assetId,
    List<PreparedFrame> frames,
    CancellationToken token,
    AtomicReference<SpriteProgress> progress
  ) {
    Map<String, EncodedSpriteTile> unique = new LinkedHashMap<>();
    for (PreparedFrame frame : frames) {
      for (EncodedSpriteTile tile : frame.tiles()) unique.putIfAbsent(tile.hash(), tile);
    }
    Map<String, CompletableFuture<TextureProperty>> futures = new LinkedHashMap<>();
    unique.forEach((hash, tile) -> futures.put(hash, resolveTile(assetId, hash, tile.skinPng(), token)));
    progress.set(new SpriteProgress(SpriteProgress.Stage.UPLOADING, 0, futures.size(), "Resolving textures"));
    CompletableFuture.allOf(futures.values().toArray(CompletableFuture[]::new)).join();
    Map<String, TextureProperty> result = new LinkedHashMap<>();
    futures.forEach((hash, future) -> result.put(hash, future.join()));
    return Map.copyOf(result);
  }

  private CompletableFuture<TextureProperty> resolveTile(
    String assetId,
    String tileHash,
    byte[] png,
    CancellationToken token
  ) {
    TextureProperty memory = memoryCache.get(tileHash);
    if (memory != null) return CompletableFuture.completedFuture(memory);
    try {
      var disk = cacheStore.find(tileHash);
      if (disk.isPresent()) {
        memoryCache.put(tileHash, disk.get());
        return CompletableFuture.completedFuture(disk.get());
      }
    } catch (IOException exception) {
      throw new SpriteException(assetId, "cache read failed for tile " + tileHash, true, null, exception);
    }
    CompletableFuture<TextureProperty> pending = new CompletableFuture<>();
    CompletableFuture<TextureProperty> existing = inFlight.putIfAbsent(tileHash, pending);
    if (existing != null) return existing;

    try {
      uploadProvider.upload(assetId, tileHash, png, token)
        .thenApply(property -> {
          try {
            cacheStore.put(tileHash, png, property);
          } catch (IOException exception) {
            throw new CompletionException(new SpriteException(
              assetId,
              "cache write failed for tile " + tileHash,
              true,
              null,
              exception
            ));
          }
          memoryCache.put(tileHash, property);
          return property;
        })
        .whenComplete((result, error) -> {
          inFlight.remove(tileHash, pending);
          if (error == null) {
            pending.complete(result);
          } else {
            pending.completeExceptionally(error);
          }
        });
    } catch (RuntimeException exception) {
      inFlight.remove(tileHash, pending);
      pending.completeExceptionally(exception);
    }
    return pending;
  }

  private List<DynamicSpriteFrame> coalesceFrames(
    List<PreparedFrame> prepared,
    Map<String, TextureProperty> properties
  ) {
    List<DynamicSpriteFrame> result = new ArrayList<>();
    List<String> previousSignature = null;
    for (PreparedFrame frame : prepared) {
      List<DynamicSpriteTile> tiles = frame.tiles().stream().map(tile -> new DynamicSpriteTile(
        tile.hash(),
        properties.get(tile.hash()),
        tile.x(),
        tile.y(),
        tile.width(),
        tile.height(),
        tile.renderMode()
      )).toList();
      List<String> signature = frame.tiles().stream().map(tile ->
        tile.hash() + ":" + tile.x() + ":" + tile.y()
      ).toList();
      if (previousSignature != null && previousSignature.equals(signature)) {
        DynamicSpriteFrame previous = result.removeLast();
        result.add(new DynamicSpriteFrame(previous.durationTicks() + frame.durationTicks(), previous.tiles()));
      } else {
        result.add(new DynamicSpriteFrame(frame.durationTicks(), tiles));
        previousSignature = signature;
      }
    }
    return List.copyOf(result);
  }

  private void validateDecodedAnimation(String assetId, DecodedAnimation animation) {
    if (animation.frames().size() > limits.maximumAnimationFrames()) {
      throw new SpriteException(
        assetId,
        "animation contains " + animation.frames().size() + " frames, limit is " +
          limits.maximumAnimationFrames(),
        false
      );
    }
    long pixels = 0;
    for (DecodedAnimation.DecodedFrame frame : animation.frames()) {
      pixels += (long) frame.image().getWidth() * frame.image().getHeight();
    }
    if (pixels > limits.maximumDecodedPixels()) {
      throw new SpriteException(
        assetId,
        "animation decodes to " + pixels + " pixels, limit is " + limits.maximumDecodedPixels(),
        false
      );
    }
  }

  private void validatePixels(String assetId, int width, int height, int frames) {
    long pixels = (long) width * height * frames;
    if (pixels > limits.maximumDecodedPixels()) {
      throw new SpriteException(
        assetId,
        "decoded size is " + width + "x" + height + " (" + pixels +
          " pixels), limit is " + limits.maximumDecodedPixels(),
        false
      );
    }
  }

  private void validateVisibleTiles(String assetId, int count) {
    if (count > limits.maximumVisibleTiles()) {
      throw new SpriteException(
        assetId,
        "frame requires " + count + " visible tiles, limit is " + limits.maximumVisibleTiles(),
        false
      );
    }
  }

  private static String imageHash(BufferedImage image, ResizeMode resizeMode) {
    return Hashes.sha256(
      Hashes.utf8(SkinTileEncoder.ENCODER_VERSION),
      Hashes.utf8(image.getWidth() + "x" + image.getHeight() + ":" + resizeMode),
      pixels(image)
    );
  }

  private static String animationHash(List<PreparedFrame> frames, ResizeMode resizeMode) {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    frames.forEach(frame -> {
      output.writeBytes(Hashes.utf8(Integer.toString(frame.durationTicks())));
      frame.tiles().forEach(tile -> output.writeBytes(Hashes.utf8(tile.hash())));
    });
    return Hashes.sha256(
      Hashes.utf8(SkinTileEncoder.ENCODER_VERSION + ":" + resizeMode),
      output.toByteArray()
    );
  }

  private static byte[] pixels(BufferedImage image) {
    byte[] bytes = new byte[image.getWidth() * image.getHeight() * 4];
    int offset = 0;
    for (int y = 0; y < image.getHeight(); y++) {
      for (int x = 0; x < image.getWidth(); x++) {
        int color = image.getRGB(x, y);
        bytes[offset++] = (byte) (color >>> 16);
        bytes[offset++] = (byte) (color >>> 8);
        bytes[offset++] = (byte) color;
        bytes[offset++] = (byte) (color >>> 24);
      }
    }
    return bytes;
  }

  private static byte[] png(BufferedImage image) {
    try {
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      if (!ImageIO.write(image, "PNG", output)) throw new IOException("PNG writer is unavailable");
      return output.toByteArray();
    } catch (IOException exception) {
      throw new IllegalStateException("Failed to encode PNG", exception);
    }
  }

  private static AtomicReference<SpriteProgress> progress() {
    return new AtomicReference<>(new SpriteProgress(SpriteProgress.Stage.READING, 0, 1, "Queued"));
  }

  private static RuntimeException unwrap(CompletionException exception) {
    Throwable cause = exception.getCause();
    if (cause instanceof RuntimeException runtime) return runtime;
    return new RuntimeException(cause);
  }

  private static void validateHeadSize(SpriteRequest request) {
    if (request.targetWidth() != 0 && (request.targetWidth() != 32 || request.targetHeight() != 32)) {
      throw new SpriteException(
        request.assetId(),
        request.renderMode() + " currently renders at 32x32; requested " +
          request.targetWidth() + "x" + request.targetHeight(),
        false
      );
    }
  }

  @Override
  public void close() throws Exception {
    Exception failure = null;
    if (ownsExecutor) executor.close();
    if (uploadProvider instanceof AutoCloseable closeable) {
      try {
        closeable.close();
      } catch (Exception exception) {
        failure = exception;
      }
    }
    try {
      cacheStore.close();
    } catch (Exception exception) {
      if (failure == null) {
        failure = exception;
      } else {
        failure.addSuppressed(exception);
      }
    }
    if (failure != null) throw failure;
  }

  private record PreparedFrame(int durationTicks, List<EncodedSpriteTile> tiles) {
    PreparedFrame {
      tiles = List.copyOf(tiles);
    }
  }
}
