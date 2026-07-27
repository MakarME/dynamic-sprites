# Dynamic Sprites

`dynamic-sprites` подготавливает новые изображения во время работы сервера и показывает их через
обычные Minecraft `player`-components. Перезагрузка ресурспака для нового изображения не нужна:
клиент получает подписанную ссылку `textures.minecraft.net` и кеширует текстуру как скин.

Система подходит для HUD, текста, портретов, карточек и небольших анимаций. Она не заменяет
ресурспак для больших статичных интерфейсов: каждый фрагмент изображения остаётся отдельным
текстовым компонентом.

## Подключение

При размещении библиотеки рядом с проектом подключить её как Gradle composite:

```kotlin
includeBuild("../dynamic-sprites")
```

Внутри находятся четыре модуля:

- `core` — PNG, resize, разбиение на фрагменты, GIF/APNG и кеш;
- `mineskin` — загрузка фрагментов через MineSkin V2 queue;
- `paper` — Adventure components, inline renderer и поддержка SkinsRestorer;
- `resourcepack` — единые Java/GLSL-константы для shader marker `R=4`.

Для другого проекта достаточно подключить необходимые зависимости
`dev.jensakaa.dynamic-sprites:core`, `mineskin` и `paper` через тот же composite build.

## Настройка MineSkin

Создать API key в MineSkin и передать его серверу:

```text
DYNAMIC_SPRITES_MINESKIN_API_KEY=msk_...
```

Ключ не хранится в Git и не попадает в HUD Editor. Без ключа уже закешированные изображения
продолжают работать, но подготовка новых фрагментов завершается понятной ошибкой.

Основные настройки находятся в `config.yml`:

```yaml
dynamic-sprites:
  cache-directory: dynamic-sprites-cache
  mineskin:
    concurrency: 2
    maximum-retries: 5
  limits:
    visible-tiles: 64
    animation-frames: 60
    animation-unique-tiles: 128
    source-mib: 20
    decoded-megapixels: 16
    disk-payload-mib: 256
```

## Подготовка PNG

Подготовка всегда асинхронная. Не следует ожидать результат через `join()` на тике сервера.

```java
SpriteRequest request = new SpriteRequest(
  "wanted_poster",
  new SpriteSource.File(Path.of("images/wanted.png")),
  256,
  128,
  ResizeMode.FIT,
  SpriteRenderMode.LOSSLESS_RGBA_TILE
);

SpritePreparation<DynamicSpriteAsset> preparation =
  DynamicSprites.service().prepare(request);

preparation.completion().thenAccept(asset -> {
  player.getScheduler().run(plugin, task -> {
    Component component = HudDynamicSpriteRenderer.inlineRenderer()
      .inline(asset, 0, SpritePixelSpace.GUI);
    player.sendMessage(component);
  }, null);
});
```

`SpriteSource` также принимает bytes, `BufferedImage`, URL, готовое texture property и skin
игрока. URL предназначен только для доверенного серверного кода; локальные и private-адреса
блокируются.

## Показ В HUD

Для Actionbar/Bossbar используется zero-advance узел. Он не двигает соседние элементы:

```java
HudNode node = HudDynamicSpriteRenderer.node(
  hudElement,
  asset,
  x,
  y,
  SpritePixelSpace.PHYSICAL
);
```

`x`, `y`, видимость, кадр и порядок можно менять отдельно для каждого игрока. Сам asset
неизменяемый и может безопасно переиспользоваться между игроками.

Для GIF/APNG:

```java
AnimationRequest request = new AnimationRequest(
  "quest_alert",
  new SpriteSource.File(Path.of("images/alert.apng")),
  128,
  64,
  ResizeMode.FIT
);

DynamicSprites.service().prepareAnimation(request).completion().thenAccept(animation -> {
  HudNode node = HudDynamicSpriteRenderer.node(
    hudElement,
    animation,
    elapsedTicks,
    x,
    y,
    SpritePixelSpace.PHYSICAL
  );
});
```

Анимация появляется только после подготовки всех уникальных фрагментов. До этого следует
показывать обычный placeholder.

## Как Считается Стоимость

- Непрозрачный фрагмент вмещает `64x64` пикселей.
- Фрагмент с любой прозрачностью вмещает `64x32` пикселей без потери RGBA.
- Полностью прозрачные фрагменты пропускаются.
- Одинаковые фрагменты и одинаковые кадры переиспользуются.
- Один кадр по умолчанию ограничен 64 видимыми фрагментами.
- Анимация ограничена 60 кадрами и 128 уникальными фрагментами.

Например, полностью непрозрачная картинка `1024x1024` требует 256 компонентов и не проходит
стандартный лимит. Такой фон дешевле запечь в ресурспак. Динамические спрайты лучше использовать
для небольших изображений, портретов и меняющегося содержимого.

## Кеш

Ключ строится из нормализованных RGBA-пикселей, размера, resize mode и версии encoder. Перед
MineSkin проверяются:

1. ограниченный memory cache;
2. content-addressed disk cache;
3. MineSkin V2 queue.

Одновременные запросы одного фрагмента объединяются в один `CompletableFuture`. Texture property
не протухает автоматически. При заполнении disk budget удаляются только старые encoded PNG;
маленькие property manifests сохраняются, поэтому повторная загрузка через MineSkin не нужна.

Minecraft кеширует `textures.minecraft.net` между сессиями в клиентском skin cache. Протокол не
сообщает серверу, успел ли клиент скачать текстуру, поэтому preloader остаётся best-effort.

## HUD Editor

В Asset Browser выбрать `Runtime`, затем импортировать PNG, GIF или APNG. Файл сохраняется в
`resourcepack/hud/dynamic-samples/` только как preview-образец.

Анализ выполняется в Web Worker и не блокирует canvas. В свойствах элемента показываются:

- число кадров;
- максимум видимых фрагментов на кадр;
- число уникальных фрагментов;
- длительность и предупреждения о лимитах.

После визуальной подгонки сохранить layout. Код реализации решает, какой реальный asset показать,
а editor хранит только геометрию и preview.

## Shader Hook

`R=4` зарезервирован для dynamic sprite. В `G+B` находятся signed Y, render mode,
GUI/physical pixel space и signature. Константы Java и GLSL генерируются из одного
`DynamicSpriteEncoding`; при конфликте с `R=1..3` сборка останавливается.

Режимы:

- `OPAQUE_TILE`;
- `LOSSLESS_RGBA_TILE`;
- `FLAT_HEAD`;
- `ISOMETRIC_HEAD`.

Изометрическая голова собирается шейдером из обычной `64x64` skin texture. Отдельный PNG для неё
MineSkin не создаёт.

Официальная документация: [MineSkin queue](https://docs.mineskin.org/docs/mineskin-api/queue-skin-generation/)
и [rate limits](https://docs.mineskin.org/docs/guides/rate-limits/).
