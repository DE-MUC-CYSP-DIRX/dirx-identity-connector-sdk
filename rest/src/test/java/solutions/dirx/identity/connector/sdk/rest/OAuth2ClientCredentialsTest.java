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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import solutions.dirx.identity.connector.sdk.api.ConnectorException;
import solutions.dirx.identity.connector.sdk.api.ErrorCode;
import solutions.dirx.identity.connector.sdk.api.model.SecretValue;
import solutions.dirx.identity.connector.sdk.rest.TestHttpServer.Reply;

class OAuth2ClientCredentialsTest {

    private TestHttpServer server;

    @BeforeEach
    void start() throws Exception {
        server = new TestHttpServer();
    }

    @AfterEach
    void stop() {
        server.close();
    }

    private static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    void tokenIsFetchedOnceAndRenewedAfterExpiry() throws Exception {
        server.on("/token",
                Reply.of(200, "{\"access_token\":\"t1\",\"expires_in\":3600}"),
                Reply.of(200, "{\"access_token\":\"t2\",\"expires_in\":3600}"));
        server.on("/api/me", Reply.of(200, "{}"));
        MutableClock clock = new MutableClock();
        RestClient c = RestClient.builder(server.uri("/api"))
                .oauth2(OAuth2ClientCredentials.builder(server.uri("/token"), "client", SecretValue.of("secret"))
                        .scope("read write").clock(clock))
                .build();

        c.getJson("me");
        c.getJson("me");
        assertEquals(1, server.requests("/token").size());
        assertEquals("Bearer t1", server.requests("/api/me").get(1).header("Authorization"));

        TestHttpServer.Recorded tokenRequest = server.requests("/token").getFirst();
        assertEquals("grant_type=client_credentials&scope=read+write", tokenRequest.body());
        assertTrue(tokenRequest.header("Authorization").startsWith("Basic "));

        clock.now = clock.now.plus(Duration.ofMinutes(59).plusSeconds(31));
        c.getJson("me");
        assertEquals(2, server.requests("/token").size());
        assertEquals("Bearer t2", server.requests("/api/me").get(2).header("Authorization"));
    }

    @Test
    void unauthorizedResponseRenewsToken() throws Exception {
        server.on("/token",
                Reply.of(200, "{\"access_token\":\"old\"}"),
                Reply.of(200, "{\"access_token\":\"new\"}"));
        server.on("/api/me", Reply.of(401, ""), Reply.of(200, "{}"));
        RestClient c = RestClient.builder(server.uri("/api"))
                .oauth2(OAuth2ClientCredentials.builder(server.uri("/token"), "client", SecretValue.of("secret"))
                        .credentialsInBody(true))
                .build();

        c.getJson("me");
        assertEquals(2, server.requests("/token").size());
        assertEquals("Bearer new", server.requests("/api/me").get(1).header("Authorization"));
        assertTrue(server.requests("/token").getFirst().body().contains("client_secret=secret"));
    }

    @Test
    void rejectedClientCredentialsAreReported() {
        server.on("/token", Reply.of(401, "{\"error\":\"invalid_client\"}"));
        RestClient c = RestClient.builder(server.uri("/api"))
                .oauth2(OAuth2ClientCredentials.builder(server.uri("/token"), "client", SecretValue.of("bad")))
                .build();
        ConnectorException e = assertThrows(ConnectorException.class, () -> c.getJson("me"));
        assertEquals(ErrorCode.INVALID_CREDENTIALS, e.getErrorCode());
        assertTrue(e.getMessage().contains("invalid_client"));
    }
}
