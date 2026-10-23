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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * An HTTP request relative to the base URI of a {@link RestClient}.
 *
 * <p>Create instances with {@link #get(String)}, {@link #post(String)}, etc.
 */
public final class RestRequest {

    /** Content type of JSON bodies. */
    public static final String APPLICATION_JSON = "application/json";

    private final String method;
    private final String path;
    private final Map<String, List<String>> query;
    private final Map<String, String> headers;
    private final @Nullable byte[] body;
    private final @Nullable String contentType;
    private final List<Integer> acceptedStatus;

    private RestRequest(Builder b) {
        this.method = b.method;
        this.path = b.path;
        this.query = copy(b.query);
        this.headers = Map.copyOf(b.headers);
        this.body = b.body;
        this.contentType = b.contentType;
        this.acceptedStatus = List.copyOf(b.acceptedStatus);
    }

    private static Map<String, List<String>> copy(Map<String, List<String>> m) {
        Map<String, List<String>> c = new LinkedHashMap<>();
        m.forEach((k, v) -> c.put(k, List.copyOf(v)));
        return java.util.Collections.unmodifiableMap(c);
    }

    /**
     * Starts a GET request.
     *
     * @param path the path relative to the base URI, may contain a query string
     * @return the builder
     */
    public static Builder get(String path) {
        return new Builder("GET", path);
    }

    /**
     * Starts a POST request.
     *
     * @param path the path relative to the base URI
     * @return the builder
     */
    public static Builder post(String path) {
        return new Builder("POST", path);
    }

    /**
     * Starts a PUT request.
     *
     * @param path the path relative to the base URI
     * @return the builder
     */
    public static Builder put(String path) {
        return new Builder("PUT", path);
    }

    /**
     * Starts a PATCH request.
     *
     * @param path the path relative to the base URI
     * @return the builder
     */
    public static Builder patch(String path) {
        return new Builder("PATCH", path);
    }

    /**
     * Starts a DELETE request.
     *
     * @param path the path relative to the base URI
     * @return the builder
     */
    public static Builder delete(String path) {
        return new Builder("DELETE", path);
    }

    /**
     * Starts a request with an arbitrary method.
     *
     * @param method the HTTP method
     * @param path   the path relative to the base URI
     * @return the builder
     */
    public static Builder method(String method, String path) {
        return new Builder(method, path);
    }

    /**
     * Returns the HTTP method.
     *
     * @return the method
     */
    public String method() {
        return method;
    }

    /**
     * Returns the path relative to the base URI.
     *
     * @return the path
     */
    public String path() {
        return path;
    }

    /**
     * Returns the query parameters (not encoded).
     *
     * @return the parameters
     */
    public Map<String, List<String>> query() {
        return query;
    }

    /**
     * Returns the request specific headers.
     *
     * @return the headers
     */
    public Map<String, String> headers() {
        return headers;
    }

    /**
     * Returns the body.
     *
     * @return the body, or empty
     */
    public Optional<byte[]> body() {
        return Optional.ofNullable(body).map(byte[]::clone);
    }

    /**
     * Returns the content type of the body.
     *
     * @return the content type, or empty
     */
    public Optional<String> contentType() {
        return Optional.ofNullable(contentType);
    }

    /**
     * Returns the non-2xx status codes that are returned to the caller instead of being mapped
     * to an exception.
     *
     * @return the status codes
     */
    public List<Integer> acceptedStatus() {
        return acceptedStatus;
    }

    /**
     * Returns the encoded query string, without leading {@code ?}.
     *
     * @return the query string, empty if there are no parameters
     */
    public String encodedQuery() {
        List<String> parts = new ArrayList<>();
        query.forEach((k, values) -> values.forEach(v -> parts.add(encode(k) + "=" + encode(v))));
        return String.join("&", parts);
    }

    static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    @Override
    public String toString() {
        String q = encodedQuery();
        return method + " " + path + (q.isEmpty() ? "" : (path.contains("?") ? "&" : "?") + q);
    }

    /** Builder for {@link RestRequest}. */
    public static final class Builder {
        private final String method;
        private final String path;
        private final Map<String, List<String>> query = new LinkedHashMap<>();
        private final Map<String, String> headers = new LinkedHashMap<>();
        private @Nullable byte[] body;
        private @Nullable String contentType;
        private final List<Integer> acceptedStatus = new ArrayList<>();

        private Builder(String method, String path) {
            this.method = Objects.requireNonNull(method, "method");
            this.path = Objects.requireNonNull(path, "path");
        }

        /**
         * Adds a query parameter. {@code null} values are ignored.
         *
         * @param name  the name
         * @param value the value
         * @return this builder
         */
        public Builder query(String name, @Nullable Object value) {
            if (value != null) {
                query.computeIfAbsent(name, k -> new ArrayList<>()).add(String.valueOf(value));
            }
            return this;
        }

        /**
         * Sets a request header.
         *
         * @param name  the name
         * @param value the value
         * @return this builder
         */
        public Builder header(String name, String value) {
            headers.put(name, value);
            return this;
        }

        /**
         * Sets a JSON body.
         *
         * @param json the body
         * @return this builder
         */
        public Builder json(JsonNode json) {
            return body(Json.mapper().writeValueAsString(json), APPLICATION_JSON);
        }

        /**
         * Sets a text body.
         *
         * @param text        the body
         * @param contentType the content type
         * @return this builder
         */
        public Builder body(String text, String contentType) {
            return body(text.getBytes(StandardCharsets.UTF_8), contentType);
        }

        /**
         * Sets a binary body.
         *
         * @param bytes       the body
         * @param contentType the content type
         * @return this builder
         */
        public Builder body(byte[] bytes, String contentType) {
            this.body = bytes.clone();
            this.contentType = contentType;
            return this;
        }

        /**
         * Accepts the given non-2xx status codes: they are returned to the caller instead of
         * being converted into an exception, e.g. {@code 404} for existence checks.
         *
         * @param status the status codes
         * @return this builder
         */
        public Builder accept(int... status) {
            for (int s : status) {
                acceptedStatus.add(s);
            }
            return this;
        }

        /**
         * Builds the request.
         *
         * @return the request
         */
        public RestRequest build() {
            return new RestRequest(this);
        }
    }
}
