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

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Optional;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import solutions.dirx.identity.connector.sdk.api.ConnectorException;
import solutions.dirx.identity.connector.sdk.api.ErrorCode;
import solutions.dirx.identity.connector.sdk.api.config.ConnectionSettings;
import solutions.dirx.identity.connector.sdk.api.model.SecretValue;

/**
 * Builds an {@link SSLContext} from the key and trust stores of {@link ConnectionSettings}.
 * Store types (PKCS#12, JKS) are detected automatically.
 */
public final class TlsSupport {

    private TlsSupport() {
    }

    /**
     * Creates an SSL context if the settings define a key store or trust store.
     *
     * @param settings the connection settings
     * @return the SSL context, or empty if neither store is configured (use the JDK defaults)
     * @throws ConnectorException with {@link ErrorCode#CONFIGURATION} if a store cannot be loaded
     */
    public static Optional<SSLContext> sslContext(ConnectionSettings settings) throws ConnectorException {
        boolean hasKeyStore = notBlank(settings.keyStore());
        boolean hasTrustStore = notBlank(settings.trustStore());
        if (!hasKeyStore && !hasTrustStore) {
            return Optional.empty();
        }
        try {
            KeyManager[] keyManagers = null;
            if (hasKeyStore) {
                char[] pw = chars(settings.keyStorePassword());
                KeyStore ks = load(settings.keyStore(), pw);
                if (notBlank(settings.keyStoreAlias())) {
                    ks = singleAlias(ks, settings.keyStoreAlias(), pw);
                }
                KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                kmf.init(ks, pw);
                keyManagers = kmf.getKeyManagers();
            }
            TrustManager[] trustManagers = null;
            if (hasTrustStore) {
                KeyStore ts = load(settings.trustStore(), chars(settings.trustStorePassword()));
                TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(ts);
                trustManagers = tmf.getTrustManagers();
            }
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(keyManagers, trustManagers, null);
            return Optional.of(ctx);
        } catch (IOException | GeneralSecurityException e) {
            throw new ConnectorException(ErrorCode.CONFIGURATION, "cannot initialize TLS: " + e.getMessage(), e);
        }
    }

    private static KeyStore load(String path, char[] password) throws IOException, GeneralSecurityException {
        return KeyStore.getInstance(new File(path), password);
    }

    private static KeyStore singleAlias(KeyStore source, String alias, char[] password)
            throws IOException, GeneralSecurityException {
        if (!source.isKeyEntry(alias)) {
            throw new GeneralSecurityException("key store contains no key with alias '" + alias + "'");
        }
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        ks.setKeyEntry(alias, source.getKey(alias, password), password, source.getCertificateChain(alias));
        return ks;
    }

    private static char[] chars(SecretValue secret) {
        return secret != null ? secret.revealChars() : null;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
