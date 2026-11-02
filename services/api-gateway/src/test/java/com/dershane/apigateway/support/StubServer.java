package com.dershane.apigateway.support;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** A tiny HTTP server standing in for an upstream service (or for Keycloak's JWKS endpoint). */
public final class StubServer implements AutoCloseable {

  private final HttpServer server;
  private final Map<String, List<String>> lastRequestHeaders = new ConcurrentHashMap<>();

  public StubServer(String contentType, String body) {
    try {
      server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    server.createContext(
        "/",
        exchange -> {
          lastRequestHeaders.clear();
          exchange
              .getRequestHeaders()
              .forEach(
                  (name, values) -> lastRequestHeaders.put(name.toLowerCase(Locale.ROOT), values));
          byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", contentType);
          exchange.sendResponseHeaders(200, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
  }

  public String url() {
    return "http://localhost:" + server.getAddress().getPort();
  }

  /** Header names are lower-cased. */
  public Map<String, List<String>> lastRequestHeaders() {
    return Map.copyOf(lastRequestHeaders);
  }

  @Override
  public void close() {
    server.stop(0);
  }
}
