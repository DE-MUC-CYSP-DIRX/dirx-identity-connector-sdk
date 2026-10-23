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

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.net.ssl.SSLContext;
import org.jspecify.annotations.Nullable;
import solutions.dirx.identity.connector.sdk.api.ConnectorException;
import solutions.dirx.identity.connector.sdk.api.ErrorCode;
import solutions.dirx.identity.connector.sdk.api.RetryableConnectorException;
import solutions.dirx.identity.connector.sdk.api.config.ConnectionSettings;
import solutions.dirx.identity.connector.sdk.api.config.ConnectorConfiguration;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;

/**
 * A small REST client on top of {@link HttpClient}.
 *
 * <p>Features: base URI resolution, pluggable {@link HttpAuthentication} with one automatic
 * re-authentication after {@code 401}, mapping of error responses to {@link ConnectorException}
 * via {@link HttpErrorMapper}, retries of throttled or unavailable requests (honoring
 * {@code Retry-After}), and JSON convenience methods.
 *
 * <p>A client is used by a single connector instance; it is not designed for concurrent use.
 */
public final class RestClient {

    /** Configuration property selecting the authentication: none, basic, bearer, apikey, oauth2. */
    public static final String PROP_AUTH_TYPE = "authType";
    /** Configuration property: token endpoint for {@code oauth2}. */
    public static final String PROP_TOKEN_URL = "tokenUrl";
    /** Configuration property: requested scope for {@code oauth2}. */
    public static final String PROP_SCOPE = "scope";
    /** Configuration property: send OAuth2 client credentials in the body instead of a Basic header. */
    public static final String PROP_CLIENT_CREDENTIALS_IN_BODY = "clientCredentialsInBody";
    /** Configuration property: header name for {@code apikey}, default {@code X-API-Key}. */
    public static final String PROP_API_KEY_HEADER = "apiKeyHeader";
    /** Configuration property: connect timeout, ISO-8601 duration or milliseconds. */
    public static final String PROP_CONNECT_TIMEOUT = "connectTimeout";
    /** Configuration property: request timeout, ISO-8601 duration or milliseconds. */
    public static final String PROP_REQUEST_TIMEOUT = "requestTimeout";
    /** Configuration property: maximum number of retries of retryable failures. */
    public static final String PROP_MAX_RETRIES = "maxRetries";

    private static final Set<String> IDEMPOTENT = Set.of("GET", "HEAD", "PUT", "DELETE", "OPTIONS");
    private static final Set<Integer> RETRY_STATUS = Set.of(429, 502, 503, 504);

    private final URI baseUri;
    private final HttpClient httpClient;
    private final HttpAuthentication authentication;
    private final HttpErrorMapper errorMapper;
    private final Duration requestTimeout;
    private final Map<String, String> defaultHeaders;
    private final int maxRetries;
    private final Duration retryBackoff;
    private final Duration maxRetryDelay;

    private RestClient(Builder b) {
        this.baseUri = b.baseUri;
        this.authentication = b.authentication;
        this.errorMapper = b.errorMapper;
        this.requestTimeout = b.requestTimeout;
        this.defaultHeaders = Map.copyOf(b.defaultHeaders);
        this.maxRetries = b.maxRetries;
        this.retryBackoff = b.retryBackoff;
        this.maxRetryDelay = b.maxRetryDelay;
        if (b.httpClient != null) {
            this.httpClient = b.httpClient;
        } else {
            HttpClient.Builder hb = HttpClient.newBuilder()
                    .connectTimeout(b.connectTimeout)
                    .followRedirects(HttpClient.Redirect.NORMAL);
            if (b.sslContext != null) {
                hb.sslContext(b.sslContext);
            }
            this.httpClient = hb.build();
        }
    }

    /**
     * Creates a builder.
     *
     * @param baseUri the base URI; relative request paths are resolved against it
     * @return the builder
     */
    public static Builder builder(URI baseUri) {
        return new Builder(baseUri);
    }

    /**
     * Creates a builder preconfigured from a connector configuration.
     *
     * <p>The base URI is {@link ConnectionSettings#url()}. User and password of the connection
     * are used as Basic credentials, or as client id and secret for OAuth2. The secret
     * properties {@code bearerToken} and {@code apiKey} hold the token and key. TLS stores are
     * taken from the connection settings. See the {@code PROP_*} constants for further
     * properties.
     *
     * @param config the configuration
     * @return the builder
     * @throws ConnectorException with {@link ErrorCode#CONFIGURATION} if the configuration is invalid
     */
    public static Builder fromConfiguration(ConnectorConfiguration config) throws ConnectorException {
        ConnectionSettings conn = config.connection();
        String url = conn.urlValue().orElseThrow(() ->
                new ConnectorException(ErrorCode.CONFIGURATION, "the connection URL is missing"));
        URI base;
        try {
            base = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new ConnectorException(ErrorCode.CONFIGURATION, "invalid connection URL: " + url, e);
        }
        Builder b = builder(base)
                .connectTimeout(config.getDuration(PROP_CONNECT_TIMEOUT, Duration.ofSeconds(30)))
                .requestTimeout(config.getDuration(PROP_REQUEST_TIMEOUT, Duration.ofSeconds(60)))
                .maxRetries(config.getInt(PROP_MAX_RETRIES, 2));
        TlsSupport.sslContext(conn).ifPresent(b::sslContext);

        String type = config.get(PROP_AUTH_TYPE)
                .orElse(conn.userValue().isPresent() ? "basic" : "none")
                .trim().toLowerCase(Locale.ROOT);
        switch (type) {
            case "none" -> b.authentication(HttpAuthentication.none());
            case "basic" -> b.authentication(HttpAuthentication.basic(
                    conn.userValue().orElseThrow(() -> missing("connection user")),
                    conn.passwordValue().orElseThrow(() -> missing("connection password"))));
            case "bearer" -> b.authentication(HttpAuthentication.bearer(
                    config.getSecret("bearerToken").orElseThrow(() -> missing("bearerToken"))));
            case "apikey" -> b.authentication(HttpAuthentication.apiKey(
                    config.get(PROP_API_KEY_HEADER).orElse("X-API-Key"),
                    config.getSecret("apiKey").orElseThrow(() -> missing("apiKey"))));
            case "oauth2" -> {
                String tokenUrl = config.require(PROP_TOKEN_URL);
                OAuth2ClientCredentials.Builder ob = OAuth2ClientCredentials.builder(URI.create(tokenUrl),
                                conn.userValue().orElseThrow(() -> missing("connection user (client id)")),
                                conn.passwordValue().orElseThrow(() -> missing("connection password (client secret)")))
                        .credentialsInBody(config.getBoolean(PROP_CLIENT_CREDENTIALS_IN_BODY, false));
                config.get(PROP_SCOPE).ifPresent(ob::scope);
                b.oauth2(ob);
            }
            default -> throw new ConnectorException(ErrorCode.CONFIGURATION,
                    "unknown " + PROP_AUTH_TYPE + " '" + type + "'; expected none, basic, bearer, apikey or oauth2");
        }
        return b;
    }

    private static ConnectorException missing(String what) {
        return new ConnectorException(ErrorCode.CONFIGURATION, "the configuration lacks the " + what);
    }

    /**
     * Returns the base URI.
     *
     * @return the base URI
     */
    public URI baseUri() {
        return baseUri;
    }

    /**
     * Resolves a path against the base URI. Absolute URIs (e.g. "next" links) are returned as is.
     *
     * @param path the path, may contain a query string
     * @return the URI
     */
    public URI resolve(String path) {
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return URI.create(path);
        }
        String base = baseUri.toString();
        if (path.isEmpty()) {
            return baseUri;
        }
        boolean baseSlash = base.endsWith("/");
        boolean pathSlash = path.startsWith("/");
        if (baseSlash && pathSlash) {
            return URI.create(base + path.substring(1));
        }
        return URI.create(baseSlash || pathSlash ? base + path : base + "/" + path);
    }

    /**
     * Sends a request. Non-2xx responses are converted into exceptions, unless accepted via
     * {@link RestRequest.Builder#accept(int...)}.
     *
     * @param request the request
     * @return the response
     * @throws ConnectorException if the request failed
     */
    public RestResponse send(RestRequest request) throws ConnectorException {
        URI uri = uriOf(request);
        boolean reauthenticated = false;
        int attempt = 0;
        while (true) {
            RestResponse response;
            try {
                response = execute(request, uri);
            } catch (RetryableConnectorException e) {
                if (attempt < maxRetries && IDEMPOTENT.contains(request.method())) {
                    sleep(backoff(attempt++, Optional.empty()));
                    continue;
                }
                throw e;
            }
            int status = response.statusCode();
            if (response.isSuccess() || request.acceptedStatus().contains(status)) {
                return response;
            }
            if (status == 401 && !reauthenticated && authentication.refresh()) {
                reauthenticated = true;
                continue;
            }
            boolean retryable = status == 429 || status == 503
                    || (RETRY_STATUS.contains(status) && IDEMPOTENT.contains(request.method()));
            if (retryable && attempt < maxRetries) {
                sleep(backoff(attempt++, response.header("Retry-After")));
                continue;
            }
            throw errorMapper.map(request.method(), uri, response);
        }
    }

    /**
     * Sends a GET request and parses the JSON response.
     *
     * @param path the path
     * @return the JSON body
     * @throws ConnectorException if the request failed or the body is not JSON
     */
    public JsonNode getJson(String path) throws ConnectorException {
        return json(send(RestRequest.get(path).header("Accept", RestRequest.APPLICATION_JSON).build()));
    }

    /**
     * Sends a POST request with a JSON body and parses the JSON response.
     *
     * @param path the path
     * @param body the body
     * @return the JSON body of the response, a missing node if empty
     * @throws ConnectorException if the request failed or the body is not JSON
     */
    public JsonNode postJson(String path, JsonNode body) throws ConnectorException {
        return json(send(RestRequest.post(path).header("Accept", RestRequest.APPLICATION_JSON).json(body).build()));
    }

    /**
     * Sends a PUT request with a JSON body and parses the JSON response.
     *
     * @param path the path
     * @param body the body
     * @return the JSON body of the response, a missing node if empty
     * @throws ConnectorException if the request failed or the body is not JSON
     */
    public JsonNode putJson(String path, JsonNode body) throws ConnectorException {
        return json(send(RestRequest.put(path).header("Accept", RestRequest.APPLICATION_JSON).json(body).build()));
    }

    /**
     * Sends a PATCH request with a JSON body and parses the JSON response.
     *
     * @param path the path
     * @param body the body
     * @return the JSON body of the response, a missing node if empty
     * @throws ConnectorException if the request failed or the body is not JSON
     */
    public JsonNode patchJson(String path, JsonNode body) throws ConnectorException {
        return json(send(RestRequest.patch(path).header("Accept", RestRequest.APPLICATION_JSON).json(body).build()));
    }

    /**
     * Sends a DELETE request.
     *
     * @param path the path
     * @throws ConnectorException if the request failed
     */
    public void delete(String path) throws ConnectorException {
        send(RestRequest.delete(path).build());
    }

    /**
     * Parses the body of a response as JSON.
     *
     * @param response the response
     * @return the JSON tree, a missing node if the body is empty
     * @throws ConnectorException with {@link ErrorCode#OTHER} if the body is not JSON
     */
    public static JsonNode json(RestResponse response) throws ConnectorException {
        try {
            return response.json();
        } catch (JacksonException e) {
            throw new ConnectorException(ErrorCode.OTHER, "invalid JSON response: " + e.getOriginalMessage(), e);
        }
    }

    private URI uriOf(RestRequest request) {
        String q = request.encodedQuery();
        String path = request.path();
        return resolve(q.isEmpty() ? path : path + (path.contains("?") ? "&" : "?") + q);
    }

    private RestResponse execute(RestRequest request, URI uri) throws ConnectorException {
        HttpRequest.Builder hb = HttpRequest.newBuilder(uri).timeout(requestTimeout);
        defaultHeaders.forEach(hb::setHeader);
        request.headers().forEach(hb::setHeader);
        Optional<byte[]> body = request.body();
        request.contentType().ifPresent(ct -> hb.setHeader("Content-Type", ct));
        hb.method(request.method(), body.map(HttpRequest.BodyPublishers::ofByteArray)
                .orElse(HttpRequest.BodyPublishers.noBody()));
        authentication.apply(hb);
        try {
            HttpResponse<String> r = httpClient.send(hb.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new RestResponse(r.statusCode(), r.headers(), r.body());
        } catch (IOException e) {
            throw ioFailure(uri, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConnectorException(ErrorCode.OTHER, "interrupted while calling " + uri, e);
        }
    }

    static RetryableConnectorException ioFailure(URI uri, IOException e) {
        String target = uri.getScheme() + "://" + uri.getRawAuthority() + uri.getRawPath();
        if (e instanceof HttpTimeoutException && !(e instanceof HttpConnectTimeoutException)) {
            return new RetryableConnectorException(ErrorCode.TIMEOUT, "request to " + target + " timed out", e);
        }
        String reason = e instanceof ConnectException || e instanceof HttpConnectTimeoutException
                ? "cannot connect to " : "communication with ";
        return new RetryableConnectorException(ErrorCode.CONNECTION_FAILED,
                reason + target + " failed: " + e, e);
    }

    private Duration backoff(int attempt, Optional<String> retryAfter) {
        Duration d = retryAfter.flatMap(RestClient::parseRetryAfter)
                .orElse(retryBackoff.multipliedBy(1L << Math.min(attempt, 16)));
        return d.compareTo(maxRetryDelay) > 0 ? maxRetryDelay : d;
    }

    private static Optional<Duration> parseRetryAfter(String value) {
        try {
            return Optional.of(Duration.ofSeconds(Math.max(0, Long.parseLong(value.trim()))));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static void sleep(Duration d) throws ConnectorException {
        if (d.isZero() || d.isNegative()) {
            return;
        }
        try {
            Thread.sleep(d.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConnectorException(ErrorCode.OTHER, "interrupted while waiting for retry", e);
        }
    }

    /** Builder for {@link RestClient}. */
    public static final class Builder {
        private final URI baseUri;
        private @Nullable HttpClient httpClient;
        private HttpAuthentication authentication = HttpAuthentication.none();
        private OAuth2ClientCredentials.@Nullable Builder oauth2;
        private HttpErrorMapper errorMapper = HttpErrorMapper.DEFAULT;
        private Duration connectTimeout = Duration.ofSeconds(30);
        private Duration requestTimeout = Duration.ofSeconds(60);
        private @Nullable SSLContext sslContext;
        private final Map<String, String> defaultHeaders = new LinkedHashMap<>();
        private int maxRetries = 2;
        private Duration retryBackoff = Duration.ofSeconds(1);
        private Duration maxRetryDelay = Duration.ofSeconds(30);

        private Builder(URI baseUri) {
            this.baseUri = Objects.requireNonNull(baseUri, "baseUri");
        }

        /**
         * Sets the authentication.
         *
         * @param value the authentication
         * @return this builder
         */
        public Builder authentication(HttpAuthentication value) {
            this.authentication = Objects.requireNonNull(value, "authentication");
            this.oauth2 = null;
            return this;
        }

        /**
         * Uses OAuth2 client credentials. Token requests share the TLS settings of this client.
         *
         * @param value the OAuth2 builder
         * @return this builder
         */
        public Builder oauth2(OAuth2ClientCredentials.Builder value) {
            this.oauth2 = Objects.requireNonNull(value, "oauth2");
            return this;
        }

        /**
         * Sets the error mapper.
         *
         * @param value the mapper
         * @return this builder
         */
        public Builder errorMapper(HttpErrorMapper value) {
            this.errorMapper = Objects.requireNonNull(value, "errorMapper");
            return this;
        }

        /**
         * Uses an externally created HTTP client. Connect timeout and SSL context are then ignored.
         *
         * @param value the client
         * @return this builder
         */
        public Builder httpClient(HttpClient value) {
            this.httpClient = value;
            return this;
        }

        /**
         * Sets the connect timeout.
         *
         * @param value the timeout
         * @return this builder
         */
        public Builder connectTimeout(Duration value) {
            this.connectTimeout = Objects.requireNonNull(value, "connectTimeout");
            return this;
        }

        /**
         * Sets the request timeout.
         *
         * @param value the timeout
         * @return this builder
         */
        public Builder requestTimeout(Duration value) {
            this.requestTimeout = Objects.requireNonNull(value, "requestTimeout");
            return this;
        }

        /**
         * Sets the SSL context.
         *
         * @param value the context
         * @return this builder
         */
        public Builder sslContext(SSLContext value) {
            this.sslContext = value;
            return this;
        }

        /**
         * Adds a header sent with every request.
         *
         * @param name  the name
         * @param value the value
         * @return this builder
         */
        public Builder defaultHeader(String name, String value) {
            defaultHeaders.put(name, value);
            return this;
        }

        /**
         * Sets the maximum number of retries of throttled ({@code 429}), unavailable
         * ({@code 503}) or, for idempotent methods, failed requests. Default 2.
         *
         * @param value the number of retries, 0 to disable
         * @return this builder
         */
        public Builder maxRetries(int value) {
            if (value < 0) {
                throw new IllegalArgumentException("maxRetries must not be negative");
            }
            this.maxRetries = value;
            return this;
        }

        /**
         * Sets the initial backoff, doubled for every retry, used when the target sends no
         * {@code Retry-After} header. Default 1 second.
         *
         * @param value the backoff
         * @return this builder
         */
        public Builder retryBackoff(Duration value) {
            this.retryBackoff = Objects.requireNonNull(value, "retryBackoff");
            return this;
        }

        /**
         * Caps the delay between retries. Default 30 seconds.
         *
         * @param value the maximum delay
         * @return this builder
         */
        public Builder maxRetryDelay(Duration value) {
            this.maxRetryDelay = Objects.requireNonNull(value, "maxRetryDelay");
            return this;
        }

        /**
         * Builds the client.
         *
         * @return the client
         */
        public RestClient build() {
            if (oauth2 != null) {
                if (httpClient == null) {
                    HttpClient.Builder hb = HttpClient.newBuilder().connectTimeout(connectTimeout)
                            .followRedirects(HttpClient.Redirect.NORMAL);
                    if (sslContext != null) {
                        hb.sslContext(sslContext);
                    }
                    httpClient = hb.build();
                }
                authentication = oauth2.httpClient(httpClient).timeout(requestTimeout).build();
                oauth2 = null;
            }
            return new RestClient(this);
        }
    }
}
