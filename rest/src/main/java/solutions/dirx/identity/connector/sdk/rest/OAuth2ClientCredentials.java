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

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import solutions.dirx.identity.connector.sdk.api.ConnectorException;
import solutions.dirx.identity.connector.sdk.api.ErrorCode;
import solutions.dirx.identity.connector.sdk.api.model.SecretValue;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;

/**
 * OAuth 2.0 client credentials grant (RFC 6749, section 4.4). The access token is cached and
 * renewed shortly before it expires or after the target rejected it.
 */
public final class OAuth2ClientCredentials implements HttpAuthentication {

    private static final Duration EXPIRY_SKEW = Duration.ofSeconds(30);
    private static final Duration DEFAULT_LIFETIME = Duration.ofMinutes(5);

    private final URI tokenUri;
    private final String clientId;
    private final SecretValue clientSecret;
    private final String scope;
    private final boolean credentialsInBody;
    private final Map<String, String> extraParameters;
    private final HttpClient httpClient;
    private final Duration timeout;
    private final Clock clock;

    private SecretValue accessToken;
    private Instant expiresAt = Instant.MIN;

    private OAuth2ClientCredentials(Builder b) {
        this.tokenUri = Objects.requireNonNull(b.tokenUri, "tokenUri");
        this.clientId = Objects.requireNonNull(b.clientId, "clientId");
        this.clientSecret = Objects.requireNonNull(b.clientSecret, "clientSecret");
        this.scope = b.scope;
        this.credentialsInBody = b.credentialsInBody;
        this.extraParameters = Map.copyOf(b.extraParameters);
        this.httpClient = b.httpClient != null ? b.httpClient : HttpClient.newHttpClient();
        this.timeout = b.timeout;
        this.clock = b.clock;
    }

    /**
     * Creates a builder.
     *
     * @param tokenUri     the token endpoint
     * @param clientId     the client id
     * @param clientSecret the client secret
     * @return the builder
     */
    public static Builder builder(URI tokenUri, String clientId, SecretValue clientSecret) {
        return new Builder(tokenUri, clientId, clientSecret);
    }

    @Override
    public void apply(HttpRequest.Builder request) throws ConnectorException {
        request.setHeader("Authorization", "Bearer " + token().reveal());
    }

    @Override
    public boolean refresh() {
        invalidate();
        return true;
    }

    /** Drops the cached access token. */
    public void invalidate() {
        if (accessToken != null) {
            accessToken.clear();
            accessToken = null;
        }
        expiresAt = Instant.MIN;
    }

    private SecretValue token() throws ConnectorException {
        if (accessToken == null || !clock.instant().isBefore(expiresAt)) {
            fetchToken();
        }
        return accessToken;
    }

    private void fetchToken() throws ConnectorException {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "client_credentials");
        if (scope != null && !scope.isBlank()) {
            form.put("scope", scope);
        }
        form.putAll(extraParameters);
        HttpRequest.Builder req = HttpRequest.newBuilder(tokenUri)
                .timeout(timeout)
                .header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded");
        if (credentialsInBody) {
            form.put("client_id", clientId);
            form.put("client_secret", clientSecret.reveal());
        } else {
            HttpAuthentication.basic(formEncode(clientId), SecretValue.of(formEncode(clientSecret.reveal())))
                    .apply(req);
        }
        String body = form.entrySet().stream()
                .map(e -> formEncode(e.getKey()) + "=" + formEncode(e.getValue()))
                .collect(Collectors.joining("&"));
        req.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));

        HttpResponse<String> response;
        try {
            response = httpClient.send(req.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw RestClient.ioFailure(tokenUri, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConnectorException(ErrorCode.OTHER, "interrupted while fetching OAuth2 token", e);
        }
        RestResponse rr = new RestResponse(response.statusCode(), response.headers(), response.body());
        if (!rr.isSuccess()) {
            ConnectorException mapped = HttpErrorMapper.DEFAULT.map("POST", tokenUri, rr);
            if (rr.statusCode() == 400 || rr.statusCode() == 401) {
                throw new ConnectorException(ErrorCode.INVALID_CREDENTIALS,
                        "OAuth2 token request rejected: " + mapped.getMessage(), mapped);
            }
            throw mapped;
        }
        try {
            JsonNode json = rr.json();
            String token = json.path("access_token").asString(null);
            if (token == null || token.isEmpty()) {
                throw new ConnectorException(ErrorCode.INVALID_CREDENTIALS,
                        "OAuth2 token response of " + tokenUri + " contains no access_token");
            }
            long seconds = json.path("expires_in").asLong(-1);
            Duration lifetime = seconds > 0 ? Duration.ofSeconds(seconds) : DEFAULT_LIFETIME;
            Duration usable = lifetime.compareTo(EXPIRY_SKEW.multipliedBy(2)) > 0
                    ? lifetime.minus(EXPIRY_SKEW) : lifetime.dividedBy(2);
            invalidate();
            accessToken = SecretValue.of(token);
            expiresAt = clock.instant().plus(usable);
        } catch (JacksonException e) {
            throw new ConnectorException(ErrorCode.OTHER, "invalid OAuth2 token response of " + tokenUri, e);
        }
    }

    private static String formEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    /** Builder for {@link OAuth2ClientCredentials}. */
    public static final class Builder {
        private final URI tokenUri;
        private final String clientId;
        private final SecretValue clientSecret;
        private String scope;
        private boolean credentialsInBody;
        private final Map<String, String> extraParameters = new LinkedHashMap<>();
        private HttpClient httpClient;
        private Duration timeout = Duration.ofSeconds(30);
        private Clock clock = Clock.systemUTC();

        private Builder(URI tokenUri, String clientId, SecretValue clientSecret) {
            this.tokenUri = tokenUri;
            this.clientId = clientId;
            this.clientSecret = clientSecret;
        }

        /**
         * Sets the requested scope.
         *
         * @param value space separated scopes
         * @return this builder
         */
        public Builder scope(String value) {
            this.scope = value;
            return this;
        }

        /**
         * Sends client id and secret as form parameters instead of an HTTP Basic header.
         *
         * @param value {@code true} to send the credentials in the body
         * @return this builder
         */
        public Builder credentialsInBody(boolean value) {
            this.credentialsInBody = value;
            return this;
        }

        /**
         * Adds a form parameter to the token request, e.g. {@code resource} or {@code audience}.
         *
         * @param name  the parameter name
         * @param value the value
         * @return this builder
         */
        public Builder parameter(String name, String value) {
            extraParameters.put(name, value);
            return this;
        }

        /**
         * Sets the HTTP client used for token requests, e.g. to share TLS settings.
         *
         * @param value the client
         * @return this builder
         */
        public Builder httpClient(HttpClient value) {
            this.httpClient = value;
            return this;
        }

        /**
         * Sets the request timeout of token requests.
         *
         * @param value the timeout
         * @return this builder
         */
        public Builder timeout(Duration value) {
            this.timeout = Objects.requireNonNull(value, "timeout");
            return this;
        }

        /**
         * Sets the clock, for tests.
         *
         * @param value the clock
         * @return this builder
         */
        public Builder clock(Clock value) {
            this.clock = Objects.requireNonNull(value, "clock");
            return this;
        }

        /**
         * Builds the authentication.
         *
         * @return the authentication
         */
        public OAuth2ClientCredentials build() {
            return new OAuth2ClientCredentials(this);
        }
    }
}
