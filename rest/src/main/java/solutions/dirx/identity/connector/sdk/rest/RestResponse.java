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

import java.net.http.HttpHeaders;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.MissingNode;

/**
 * An HTTP response with a fully read text body.
 *
 * @param statusCode the HTTP status code
 * @param headers    the response headers
 * @param body       the body, empty if there is none
 */
public record RestResponse(int statusCode, HttpHeaders headers, String body) {

    /**
     * Creates a response.
     *
     * @param statusCode the HTTP status code
     * @param headers    the response headers, {@code null} for none
     * @param body       the body, {@code null} for none
     */
    public RestResponse(int statusCode, @Nullable HttpHeaders headers, @Nullable String body) {
        this.statusCode = statusCode;
        this.headers = headers != null ? headers : HttpHeaders.of(java.util.Map.of(), (a, b) -> true);
        this.body = body != null ? body : "";
    }

    /**
     * Tells whether the status code is in the 2xx range.
     *
     * @return {@code true} for success
     */
    public boolean isSuccess() {
        return statusCode >= 200 && statusCode < 300;
    }

    /**
     * Returns the first value of a header.
     *
     * @param name the header name (case-insensitive)
     * @return the value, or empty
     */
    public Optional<String> header(String name) {
        return headers.firstValue(name);
    }

    /**
     * Parses the body as JSON.
     *
     * @return the JSON tree; a missing node if the body is empty
     * @throws JacksonException if the body is not valid JSON
     */
    public JsonNode json() {
        return body.isBlank() ? MissingNode.getInstance() : Json.mapper().readTree(body);
    }
}
