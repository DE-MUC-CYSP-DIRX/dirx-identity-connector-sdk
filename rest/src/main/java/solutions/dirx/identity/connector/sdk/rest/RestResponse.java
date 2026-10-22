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

import java.net.http.HttpHeaders;
import java.util.Optional;
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
    public RestResponse {
        headers = headers != null ? headers : HttpHeaders.of(java.util.Map.of(), (a, b) -> true);
        body = body != null ? body : "";
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
