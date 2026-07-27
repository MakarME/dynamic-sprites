package dev.jensakaa.dynamicsprites.mineskin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jensakaa.dynamicsprites.TextureProperty;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MineSkinV2ProviderTest {
  private HttpServer server;

  @BeforeEach
  void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.start();
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  @Test
  void queuedJobHonorsRetryAndReturnsTextureProperty() {
    AtomicInteger submissions = new AtomicInteger();
    server.createContext("/v2/queue", exchange -> {
      assertTrue(exchange.getRequestHeaders().getFirst("Authorization").startsWith("Bearer "));
      assertTrue(exchange.getRequestHeaders().getFirst("Content-Type").startsWith("multipart/form-data"));
      if (submissions.getAndIncrement() == 0) {
        respond(exchange, 429, "{\"errors\":[{\"message\":\"slow down\"}]}");
      } else {
        respond(exchange, 202, "{\"job\":{\"id\":\"job-1\",\"status\":\"waiting\"}}");
      }
    });
    server.createContext("/v2/queue/job-1", exchange -> respond(
      exchange,
      200,
      "{\"job\":{\"id\":\"job-1\",\"status\":\"completed\"},\"skin\":{\"texture\":{" +
        "\"data\":{\"value\":\"value\",\"signature\":\"signature\"}," +
        "\"url\":\"https://textures.minecraft.net/texture/hash\"}}}"
    ));
    var executor = Executors.newVirtualThreadPerTaskExecutor();
    MineSkinV2Settings settings = new MineSkinV2Settings(
      URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
      "api-key",
      "DynamicSpritesTest/1",
      1,
      2,
      Duration.ofSeconds(2),
      Duration.ofMillis(5),
      Duration.ofSeconds(2)
    );
    try (MineSkinV2Provider provider = new MineSkinV2Provider(
      settings,
      HttpClient.newHttpClient(),
      executor
    )) {
      TextureProperty property = provider.upload(
        "asset",
        "tile",
        new byte[]{1, 2, 3},
        () -> false
      ).toCompletableFuture().join();
      assertEquals("value", property.value());
      assertEquals("signature", property.signature());
      assertEquals("hash", property.textureHash());
      assertEquals(2, submissions.get());
    } finally {
      executor.close();
    }
  }

  private static void respond(HttpExchange exchange, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }
}
