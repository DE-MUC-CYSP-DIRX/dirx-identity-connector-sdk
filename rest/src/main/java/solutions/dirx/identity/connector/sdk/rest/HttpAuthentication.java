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

import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import solutions.dirx.identity.connector.sdk.api.ConnectorException;
import solutions.dirx.identity.connector.sdk.api.model.SecretValue;

/**
 * Adds credentials to outgoing HTTP requests.
 *
 * <p>Implementations must be safe for use by a single connector instance; they are not
 * required to be thread-safe.
 */
public interface HttpAuthentication {

    /**
     * Adds the credentials to a request.
     *
     * @param request the request builder
     * @throws ConnectorException if the credentials cannot be obtained
     */
    void apply(HttpRequest.Builder request) throws ConnectorException;

    /**
     * Called once when the target answered {@code 401 Unauthorized}. Implementations holding
     * cached tokens drop them so that the next {@link #apply} fetches fresh ones.
     *
     * @return {@code true} if the request should be repeated with refreshed credentials
     */
    default boolean refresh() {
        return false;
    }

    /**
     * Returns an authentication that adds nothing.
     *
     * @return the authentication
     */
    static HttpAuthentication none() {
        return request -> { };
    }

    /**
     * Returns HTTP Basic authentication.
     *
     * @param user     the user name
     * @param password the password
     * @return the authentication
     */
    static HttpAuthentication basic(String user, SecretValue password) {
        Objects.requireNonNull(user, "user");
        Objects.requireNonNull(password, "password");
        return request -> {
            String token = Base64.getEncoder()
                    .encodeToString((user + ":" + password.reveal()).getBytes(StandardCharsets.UTF_8));
            request.setHeader("Authorization", "Basic " + token);
        };
    }

    /**
     * Returns bearer token authentication with a static token.
     *
     * @param token the token
     * @return the authentication
     */
    static HttpAuthentication bearer(SecretValue token) {
        Objects.requireNonNull(token, "token");
        return request -> request.setHeader("Authorization", "Bearer " + token.reveal());
    }

    /**
     * Returns API key authentication that sends the key in a header.
     *
     * @param header the header name, e.g. {@code X-API-Key}
     * @param key    the key
     * @return the authentication
     */
    static HttpAuthentication apiKey(String header, SecretValue key) {
        Objects.requireNonNull(header, "header");
        Objects.requireNonNull(key, "key");
        return request -> request.setHeader(header, key.reveal());
    }
}
