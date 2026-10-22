/*
 * Copyright 2026 Atos Secure Digital GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package solutions.dirx.identity.connector.sdk.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import solutions.dirx.identity.connector.sdk.api.ConnectorException;
import solutions.dirx.identity.connector.sdk.api.ErrorCode;
import solutions.dirx.identity.connector.sdk.api.RetryableConnectorException;
import solutions.dirx.identity.connector.sdk.api.config.ConnectionSettings;
import solutions.dirx.identity.connector.sdk.api.config.MapConnectorConfiguration;
import solutions.dirx.identity.connector.sdk.api.model.SecretValue;
import solutions.dirx.identity.connector.sdk.rest.TestHttpServer.Reply;
import tools.jackson.databind.JsonNode;

class RestClientTest {

    private TestHttpServer server;

    @BeforeEach
    void start() throws Exception {
        server = new TestHttpServer();
    }

    @AfterEach
    void stop() {
        server.close();
    }

    private RestClient.Builder client() {
        return RestClient.builder(server.uri("/api/")).retryBackoff(Duration.ZERO);
    }

    @Test
    void getJsonWithBasicAuthAndQuery() throws Exception {
        server.on("/api/users", Reply.of(200, "{\"items\":[{\"id\":\"1\"}]}"));
        RestClient c = client().authentication(HttpAuthentication.basic("joe", SecretValue.of("s3cr:t")))
                .defaultHeader("X-Tenant", "t1").build();

        RestResponse r = c.send(RestRequest.get("/users").query("filter", "name eq \"a b\"").query("skip", null).build());
        JsonNode json = RestClient.json(r);

        assertEquals("1", json.path("items").get(0).path("id").asString());
        TestHttpServer.Recorded req = server.requests().getFirst();
        assertEquals("/api/users?filter=name%20eq%20%22a%20b%22", req.uri());
        String expected = "Basic " + Base64.getEncoder().encodeToString("joe:s3cr:t".getBytes(StandardCharsets.UTF_8));
        assertEquals(expected, req.header("Authorization"));
        assertEquals("t1", req.header("X-Tenant"));
    }

    @Test
    void postJsonSendsBody() throws Exception {
        server.on("/api/users", Reply.of(201, "{\"id\":\"42\"}"));
        RestClient c = client().build();
        JsonNode created = c.postJson("users", Json.object().put("name", "x"));
        assertEquals("42", created.path("id").asString());
        TestHttpServer.Recorded req = server.requests().getFirst();
        assertEquals("POST", req.method());
        assertEquals("{\"name\":\"x\"}", req.body());
        assertEquals("application/json", req.header("Content-Type"));
    }

    @Test
    void errorStatusIsMapped() {
        server.on("/api/users/9", Reply.of(404, "{\"error\":\"gone\"}"));
        server.on("/api/users", Reply.of(409, ""));
        RestClient c = client().build();

        ConnectorException e = assertThrows(ConnectorException.class, () -> c.getJson("users/9"));
        assertEquals(ErrorCode.NOT_FOUND, e.getErrorCode());
        assertTrue(e.getMessage().contains("HTTP 404"), e.getMessage());
        assertTrue(e.getMessage().contains("gone"), e.getMessage());

        e = assertThrows(ConnectorException.class, () -> c.postJson("users", Json.object()));
        assertEquals(ErrorCode.ALREADY_EXISTS, e.getErrorCode());
    }

    @Test
    void acceptedStatusIsReturned() throws Exception {
        server.on("/api/users/9", Reply.of(404, ""));
        RestResponse r = client().build().send(RestRequest.get("users/9").accept(404).build());
        assertEquals(404, r.statusCode());
        assertFalse(r.isSuccess());
        assertTrue(r.json().isMissingNode());
    }

    @Test
    void throttledRequestIsRetried() throws Exception {
        server.on("/api/users", Reply.of(429, "").withHeader("Retry-After", "0"), Reply.of(201, "{}"));
        client().build().postJson("users", Json.object());
        assertEquals(2, server.requests().size());
    }

    @Test
    void retriesAreLimited() {
        server.on("/api/users", Reply.of(503, ""));
        ConnectorException e = assertThrows(ConnectorException.class,
                () -> client().maxRetries(2).build().getJson("users"));
        assertInstanceOf(RetryableConnectorException.class, e);
        assertEquals(ErrorCode.CONNECTION_FAILED, e.getErrorCode());
        assertEquals(3, server.requests().size());
    }

    @Test
    void badGatewayIsNotRetriedForPost() {
        server.on("/api/users", Reply.of(502, ""));
        assertThrows(RetryableConnectorException.class, () -> client().build().postJson("users", Json.object()));
        assertEquals(1, server.requests().size());
    }

    @Test
    void unauthorizedTriggersOneRefresh() {
        server.on("/api/users", Reply.of(401, ""));
        int[] refreshes = {0};
        HttpAuthentication auth = new HttpAuthentication() {
            @Override
            public void apply(java.net.http.HttpRequest.Builder request) {
            }

            @Override
            public boolean refresh() {
                refreshes[0]++;
                return true;
            }
        };
        ConnectorException e = assertThrows(ConnectorException.class,
                () -> client().authentication(auth).build().getJson("users"));
        assertEquals(ErrorCode.INVALID_CREDENTIALS, e.getErrorCode());
        assertEquals(1, refreshes[0]);
        assertEquals(2, server.requests().size());
    }

    @Test
    void connectionRefusedIsRetryable() throws Exception {
        int port;
        try (ServerSocket s = new ServerSocket(0)) {
            port = s.getLocalPort();
        }
        RestClient c = RestClient.builder(URI.create("http://127.0.0.1:" + port)).maxRetries(0).build();
        RetryableConnectorException e = assertThrows(RetryableConnectorException.class, () -> c.getJson("x"));
        assertEquals(ErrorCode.CONNECTION_FAILED, e.getErrorCode());
    }

    @Test
    void resolvesPaths() {
        RestClient a = RestClient.builder(URI.create("https://h/api")).build();
        RestClient b = RestClient.builder(URI.create("https://h/api/")).build();
        assertEquals("https://h/api/users", a.resolve("users").toString());
        assertEquals("https://h/api/users", a.resolve("/users").toString());
        assertEquals("https://h/api/users", b.resolve("users").toString());
        assertEquals("https://h/api/users", b.resolve("/users").toString());
        assertEquals("https://other/next?p=2", b.resolve("https://other/next?p=2").toString());
    }

    @Test
    void fromConfigurationUsesApiKey() throws Exception {
        server.on("/api/me", Reply.of(200, "{}"));
        MapConnectorConfiguration cfg = MapConnectorConfiguration.builder()
                .connection(ConnectionSettings.builder().url(server.uri("/api").toString()).build())
                .property(RestClient.PROP_AUTH_TYPE, "apikey")
                .property(RestClient.PROP_API_KEY_HEADER, "X-Key")
                .secret("apiKey", "k1")
                .build();
        RestClient.fromConfiguration(cfg).build().getJson("me");
        assertEquals("k1", server.requests().getFirst().header("X-Key"));
    }

    @Test
    void fromConfigurationDefaultsToBasicWhenUserIsSet() throws Exception {
        server.on("/api/me", Reply.of(200, "{}"));
        MapConnectorConfiguration cfg = MapConnectorConfiguration.builder()
                .connection(ConnectionSettings.builder().url(server.uri("/api").toString())
                        .user("u").password(SecretValue.of("p")).build())
                .build();
        RestClient.fromConfiguration(cfg).build().getJson("me");
        assertTrue(server.requests().getFirst().header("Authorization").startsWith("Basic "));
    }

    @Test
    void fromConfigurationRejectsInvalidSettings() {
        ConnectorException e = assertThrows(ConnectorException.class,
                () -> RestClient.fromConfiguration(MapConnectorConfiguration.builder().build()));
        assertEquals(ErrorCode.CONFIGURATION, e.getErrorCode());

        e = assertThrows(ConnectorException.class, () -> RestClient.fromConfiguration(
                MapConnectorConfiguration.builder()
                        .connection(ConnectionSettings.builder().url("https://h").build())
                        .property(RestClient.PROP_AUTH_TYPE, "kerberos").build()));
        assertEquals(ErrorCode.CONFIGURATION, e.getErrorCode());
        assertTrue(e.getMessage().contains("kerberos"));
    }
}
