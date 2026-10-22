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

import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import solutions.dirx.identity.connector.sdk.api.ConnectorException;
import solutions.dirx.identity.connector.sdk.api.ErrorCode;
import solutions.dirx.identity.connector.sdk.api.model.SecretValue;

/**
 * Read-only configuration of a connector instance.
 *
 * <p>Property names are case-insensitive. Hosts implement {@link #get(String)},
 * {@link #getList(String)}, {@link #getSecret(String)}, {@link #names()} and
 * {@link #connection()}; the typed accessors are derived from these.
 */
public interface ConnectorConfiguration {

    /**
     * Returns the (first) value of a property.
     *
     * @param name the property name
     * @return the value, or empty if not set or blank
     */
    Optional<String> get(String name);

    /**
     * Returns all values of a multi-value property.
     *
     * @param name the property name
     * @return the values, possibly empty
     */
    List<String> getList(String name);

    /**
     * Returns a secret property, already decrypted by the host.
     *
     * @param name the property name
     * @return the secret, or empty
     */
    Optional<SecretValue> getSecret(String name);

    /**
     * Returns the names of all set properties.
     *
     * @return the names
     */
    Set<String> names();

    /**
     * Returns the standard endpoint settings.
     *
     * @return the connection settings, never {@code null}
     */
    ConnectionSettings connection();

    /**
     * Returns a mandatory property.
     *
     * @param name the property name
     * @return the value
     * @throws ConnectorException with {@link ErrorCode#CONFIGURATION} if the property is missing
     */
    default String require(String name) throws ConnectorException {
        return get(name).orElseThrow(() -> new ConnectorException(ErrorCode.CONFIGURATION,
                "mandatory configuration property '" + name + "' is missing"));
    }

    /**
     * Returns an integer property.
     *
     * @param name         the property name
     * @param defaultValue returned if the property is not set
     * @return the value
     * @throws ConnectorException with {@link ErrorCode#CONFIGURATION} if the value is not a number
     */
    default int getInt(String name, int defaultValue) throws ConnectorException {
        Optional<String> v = get(name);
        if (v.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(v.get().trim());
        } catch (NumberFormatException e) {
            throw new ConnectorException(ErrorCode.CONFIGURATION,
                    "configuration property '" + name + "' is not an integer: " + v.get(), e);
        }
    }

    /**
     * Returns a boolean property. {@code true}, {@code yes}, {@code on} and {@code 1} are
     * true; {@code false}, {@code no}, {@code off} and {@code 0} are false (case-insensitive).
     *
     * @param name         the property name
     * @param defaultValue returned if the property is not set
     * @return the value
     * @throws ConnectorException with {@link ErrorCode#CONFIGURATION} if the value is not a boolean
     */
    default boolean getBoolean(String name, boolean defaultValue) throws ConnectorException {
        Optional<String> v = get(name);
        if (v.isEmpty()) {
            return defaultValue;
        }
        return switch (v.get().trim().toLowerCase(java.util.Locale.ROOT)) {
            case "true", "yes", "on", "1" -> true;
            case "false", "no", "off", "0" -> false;
            default -> throw new ConnectorException(ErrorCode.CONFIGURATION,
                    "configuration property '" + name + "' is not a boolean: " + v.get());
        };
    }

    /**
     * Returns a duration property. The value is either an ISO-8601 duration such as
     * {@code PT30S} or a plain number of milliseconds.
     *
     * @param name         the property name
     * @param defaultValue returned if the property is not set
     * @return the value
     * @throws ConnectorException with {@link ErrorCode#CONFIGURATION} if the value is not a duration
     */
    default Duration getDuration(String name, Duration defaultValue) throws ConnectorException {
        Optional<String> v = get(name);
        if (v.isEmpty()) {
            return defaultValue;
        }
        String s = v.get().trim();
        try {
            return s.chars().allMatch(Character::isDigit) ? Duration.ofMillis(Long.parseLong(s)) : Duration.parse(s);
        } catch (NumberFormatException | DateTimeParseException e) {
            throw new ConnectorException(ErrorCode.CONFIGURATION,
                    "configuration property '" + name + "' is not a duration: " + s, e);
        }
    }
}
