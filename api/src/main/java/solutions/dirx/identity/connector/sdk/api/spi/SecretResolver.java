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

package solutions.dirx.identity.connector.sdk.api.spi;

import java.util.Optional;
import solutions.dirx.identity.connector.sdk.api.ConnectorException;
import solutions.dirx.identity.connector.sdk.api.model.SecretValue;

/**
 * Host-side hook that turns a stored (typically encrypted) property value into a
 * {@link SecretValue}. Hosts use it when implementing
 * {@link solutions.dirx.identity.connector.sdk.api.config.ConnectorConfiguration#getSecret(String)};
 * connectors never call it directly.
 */
@FunctionalInterface
public interface SecretResolver {

    /** Resolver that treats stored values as clear text. */
    SecretResolver PLAIN = (name, stored) -> Optional.ofNullable(stored).map(SecretValue::of);

    /**
     * Resolves a stored value.
     *
     * @param propertyName the property name
     * @param storedValue  the stored value, or {@code null}
     * @return the secret, or empty if there is no value
     * @throws ConnectorException if the value cannot be decrypted
     */
    Optional<SecretValue> resolve(String propertyName, String storedValue) throws ConnectorException;
}
