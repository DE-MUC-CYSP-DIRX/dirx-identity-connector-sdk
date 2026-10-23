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

package solutions.dirx.identity.connector.sdk.api.config;

import java.util.Optional;
import org.jspecify.annotations.Nullable;
import solutions.dirx.identity.connector.sdk.api.model.SecretValue;

/**
 * The standard endpoint settings of a connector instance. Every component may be
 * {@code null} if not configured; use the {@code Optional} accessors for convenience.
 *
 * @param url                the endpoint URL
 * @param server             the host name
 * @param port               the port, or {@code null}
 * @param user               the user (bind DN, client id, ...)
 * @param password           the password or client secret
 * @param ssl                whether TLS is required
 * @param keyStore           path of the key store (client certificate)
 * @param keyStorePassword   password of the key store
 * @param keyStoreAlias      alias of the client key in the key store
 * @param trustStore         path of the trust store
 * @param trustStorePassword password of the trust store
 */
public record ConnectionSettings(@Nullable String url, @Nullable String server, @Nullable Integer port,
                                 @Nullable String user, @Nullable SecretValue password, boolean ssl,
                                 @Nullable String keyStore, @Nullable SecretValue keyStorePassword,
                                 @Nullable String keyStoreAlias, @Nullable String trustStore,
                                 @Nullable SecretValue trustStorePassword) {

    private static final ConnectionSettings EMPTY = builder().build();

    /**
     * Returns settings with all components unset.
     *
     * @return the empty settings
     */
    public static ConnectionSettings empty() {
        return EMPTY;
    }

    /**
     * Returns the URL.
     *
     * @return the URL, or empty
     */
    public Optional<String> urlValue() {
        return Optional.ofNullable(url).filter(s -> !s.isBlank());
    }

    /**
     * Returns the user.
     *
     * @return the user, or empty
     */
    public Optional<String> userValue() {
        return Optional.ofNullable(user).filter(s -> !s.isBlank());
    }

    /**
     * Returns the password.
     *
     * @return the password, or empty
     */
    public Optional<SecretValue> passwordValue() {
        return Optional.ofNullable(password);
    }

    /**
     * Creates a builder.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /** Builder for {@link ConnectionSettings}. */
    public static final class Builder {
        private @Nullable String url;
        private @Nullable String server;
        private @Nullable Integer port;
        private @Nullable String user;
        private @Nullable SecretValue password;
        private boolean ssl;
        private @Nullable String keyStore;
        private @Nullable SecretValue keyStorePassword;
        private @Nullable String keyStoreAlias;
        private @Nullable String trustStore;
        private @Nullable SecretValue trustStorePassword;

        private Builder() {
        }

        /**
         * Sets the URL.
         *
         * @param value the URL
         * @return this builder
         */
        public Builder url(@Nullable String value) {
            this.url = value;
            return this;
        }

        /**
         * Sets the server.
         *
         * @param value the host name
         * @return this builder
         */
        public Builder server(@Nullable String value) {
            this.server = value;
            return this;
        }

        /**
         * Sets the port.
         *
         * @param value the port
         * @return this builder
         */
        public Builder port(@Nullable Integer value) {
            this.port = value;
            return this;
        }

        /**
         * Sets the user.
         *
         * @param value the user
         * @return this builder
         */
        public Builder user(@Nullable String value) {
            this.user = value;
            return this;
        }

        /**
         * Sets the password.
         *
         * @param value the password
         * @return this builder
         */
        public Builder password(@Nullable SecretValue value) {
            this.password = value;
            return this;
        }

        /**
         * Sets whether TLS is required.
         *
         * @param value {@code true} for TLS
         * @return this builder
         */
        public Builder ssl(boolean value) {
            this.ssl = value;
            return this;
        }

        /**
         * Sets the key store.
         *
         * @param path     the key store path
         * @param password the key store password
         * @param alias    the key alias
         * @return this builder
         */
        public Builder keyStore(@Nullable String path, @Nullable SecretValue password, @Nullable String alias) {
            this.keyStore = path;
            this.keyStorePassword = password;
            this.keyStoreAlias = alias;
            return this;
        }

        /**
         * Sets the trust store.
         *
         * @param path     the trust store path
         * @param password the trust store password
         * @return this builder
         */
        public Builder trustStore(@Nullable String path, @Nullable SecretValue password) {
            this.trustStore = path;
            this.trustStorePassword = password;
            return this;
        }

        /**
         * Builds the settings.
         *
         * @return the settings
         */
        public ConnectionSettings build() {
            return new ConnectionSettings(url, server, port, user, password, ssl, keyStore, keyStorePassword,
                    keyStoreAlias, trustStore, trustStorePassword);
        }
    }
}
