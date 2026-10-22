/*
 * Copyright 2026 Atos Secure Digital GmbH
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package solutions.dirx.identity.connector.sdk.rest;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/** A minimal scripted HTTP server for tests. */
final class TestHttpServer implements AutoCloseable {

    record Recorded(String method, String uri, Map<String, List<String>> headers, String body) {
        String header(String name) {
            return headers.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(name))
                    .findFirst().map(e -> e.getValue().getFirst()).orElse(null);
        }
    }

    record Reply(int status, String body, Map<String, String> headers) {
        static Reply of(int status, String body) {
            return new Reply(status, body, Map.of("Content-Type", "application/json"));
        }

        Reply withHeader(String name, String value) {
            Map<String, String> h = new java.util.LinkedHashMap<>(headers);
            h.put(name, value);
            return new Reply(status, body, h);
        }
    }

    private final HttpServer server;
    private final Map<String, Deque<Reply>> replies = Collections.synchronizedMap(new java.util.HashMap<>());
    private final List<Recorded> requests = Collections.synchronizedList(new ArrayList<>());

    TestHttpServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    URI uri(String path) {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
    }

    /** Queues replies for a path (without query); the last reply is repeated. */
    TestHttpServer on(String path, Reply... r) {
        replies.computeIfAbsent(path, k -> new ArrayDeque<>()).addAll(List.of(r));
        return this;
    }

    List<Recorded> requests() {
        return List.copyOf(requests);
    }

    List<Recorded> requests(String path) {
        return requests().stream().filter(r -> r.uri().startsWith(path)).toList();
    }

    private void handle(HttpExchange ex) throws IOException {
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        requests.add(new Recorded(ex.getRequestMethod(), ex.getRequestURI().toString(),
                Map.copyOf(ex.getRequestHeaders()), body));
        Deque<Reply> q = replies.get(ex.getRequestURI().getPath());
        Reply reply;
        if (q == null || q.isEmpty()) {
            reply = Reply.of(404, "{\"error\":\"no reply\"}");
        } else {
            reply = q.size() > 1 ? q.poll() : q.peek();
        }
        reply.headers().forEach((k, v) -> ex.getResponseHeaders().add(k, v));
        byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(reply.status(), bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            ex.getResponseBody().write(bytes);
        }
        ex.close();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
