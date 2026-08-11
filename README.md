# Dynamic Sprites

`dynamic-sprites` подготавливает изображения во время работы Minecraft-сервера и показывает их через обычные Adventure `player`-components. Для нового изображения не требуется перезагрузка ресурспака: клиент получает подписанную текстуру `textures.minecraft.net` и кеширует её как скин.

Библиотека подходит для HUD, текста, портретов, карточек и небольших анимаций. Большие статические интерфейсы выгоднее хранить в ресурспаке: каждый фрагмент динамического изображения остаётся отдельным текстовым компонентом.

## Модули

- `core` — чтение PNG/GIF/APNG, resize, разбиение на фрагменты, кеш и абстракции загрузки текстур;
- `mineskin` — реализация `TextureUploadProvider` через MineSkin V2 queue;
- `paper` — Adventure renderer и интеграция со SkinsRestorer;
- `resourcepack` — Java/GLSL-константы shader marker.

## Подключение как зависимости

Релизные теги доступны через JitPack, поэтому потребителю не нужно клонировать репозиторий или добавлять composite build.

```kotlin
repositories {
  maven("https://jitpack.io")
}

dependencies {
  implementation("com.github.MakarME.dynamic-sprites:core:<version>")
  implementation("com.github.MakarME.dynamic-sprites:mineskin:<version>")
  implementation("com.github.MakarME.dynamic-sprites:paper:<version>")
  implementation("com.github.MakarME.dynamic-sprites:resourcepack:<version>")
}
```

Вместо `<version>` укажите Git tag, например `v1.0.0`. Подключайте только нужные модули; `mineskin`, `paper` и `resourcepack` уже экспортируют зависимость на `core`.

Для локальной разработки все модули публикуются в Maven Local вместе с source и Javadoc JAR:

```shell
./gradlew publishToMavenLocal -Pversion=1.0.0-local
```

Локальные координаты имеют вид `dev.jensakaa.dynamic-sprites:<module>:<version>`.

## Создание сервиса

Библиотека не читает конфигурацию конкретного плагина и не содержит глобального singleton. Владелец приложения создаёт сервис явно и закрывает его при выключении:

```java
String apiKey = System.getenv("DYNAMIC_SPRITES_MINESKIN_API_KEY");

MineSkinV2Provider uploader = new MineSkinV2Provider(
  MineSkinV2Settings.defaults(apiKey, "my-plugin/1.0")
);
ContentAddressedDiskSpriteCacheStore cache =
  new ContentAddressedDiskSpriteCacheStore(
    Path.of("plugins/my-plugin/dynamic-sprites-cache"),
    SpriteLimits.DEFAULTS.maximumDiskPayloadBytes()
  );

DynamicSpriteService sprites = new DynamicSpriteService(
  uploader,
  cache,
  null, // Передайте PlayerSkinResolver, если нужны SpriteSource.PlayerSkin.
  SpriteLimits.DEFAULTS
);
```

При выключении вызовите `sprites.close()` и `uploader.close()`. API key не следует хранить в Git.

## Подготовка и показ PNG

Подготовка всегда асинхронная. Не ожидайте результат через `join()` на серверном тике.

```java
SpriteRequest request = new SpriteRequest(
  "wanted_poster",
  new SpriteSource.File(Path.of("images/wanted.png")),
  256,
  128,
  ResizeMode.FIT,
  SpriteRenderMode.LOSSLESS_RGBA_TILE
);

SpritePreparation<DynamicSpriteAsset> preparation = sprites.prepare(request);

preparation.completion().thenAccept(asset -> {
  player.getScheduler().run(plugin, task -> {
    DynamicSpriteRenderer renderer = new DynamicSpriteRenderer(spacingProvider);
    Component component = renderer.inline(asset, 0, SpritePixelSpace.GUI);
    player.sendMessage(component);
  }, null);
});
```

`spacingProvider` — реализация `PixelSpacingProvider` из ресурспака потребителя. `SpriteSource` также принимает bytes, `BufferedImage`, URL, готовое texture property и скин игрока. URL предназначен только для доверенного серверного кода; локальные и private-адреса блокируются.

Для HUD без сохранения text advance используйте `DynamicSpriteRenderer.hud(...)` или получите независимые `DynamicSpriteNode` через `nodes(...)`.

## GIF и APNG

```java
AnimationRequest request = new AnimationRequest(
  "quest_alert",
  new SpriteSource.File(Path.of("images/alert.apng")),
  128,
  64,
  ResizeMode.FIT
);

sprites.prepareAnimation(request).completion().thenAccept(animation -> {
  Component frame = renderer.hud(
    animation,
    elapsedTicks,
    x,
    y,
    SpritePixelSpace.PHYSICAL
  );
});
```

Анимация становится доступна только после подготовки всех уникальных фрагментов. До завершения можно показывать обычный placeholder.

## Ограничения и кеш

По умолчанию:

- непрозрачный фрагмент вмещает `64x64` пикселей;
- RGBA-фрагмент вмещает `64x32` пикселей без потери прозрачности;
- полностью прозрачные и одинаковые фрагменты переиспользуются;
- один кадр ограничен 64 видимыми фрагментами;
- анимация ограничена 60 кадрами и 128 уникальными фрагментами.

Ключ кеша зависит от нормализованных RGBA-пикселей, размера, resize mode и версии encoder. Перед MineSkin проверяются memory cache и content-addressed disk cache, а одновременные запросы одного фрагмента объединяются.

## Shader hook

`R=4` зарезервирован библиотекой для dynamic sprite. В `G+B` кодируются signed Y, render mode, GUI/physical pixel space и signature. GLSL-константы генерируются вызовом `DynamicSpriteShaderInclude.glslDefines()` и используют нейтральный префикс `DYNAMIC_SPRITE_*`.

Режимы рендера: `OPAQUE_TILE`, `LOSSLESS_RGBA_TILE`, `FLAT_HEAD` и `ISOMETRIC_HEAD`.

## Лицензия

Проект распространяется на условиях [GNU General Public License v3.0 only](LICENSE).
