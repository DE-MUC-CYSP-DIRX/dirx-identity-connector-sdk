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

import java.util.Objects;

/** Context handed to {@link solutions.dirx.identity.connector.sdk.api.Connector#init}. */
public interface ConnectorContext {

    /**
     * Returns the name of the connector instance, as configured in the host.
     *
     * @return the instance name
     */
    String instanceName();

    /**
     * Returns the configuration of the instance. Secrets are already decrypted.
     *
     * @return the configuration
     */
    ConnectorConfiguration configuration();

    /**
     * Returns a logger. The host routes it to its own logging system.
     *
     * @param type the class that logs
     * @return the logger
     */
    default System.Logger logger(Class<?> type) {
        return System.getLogger(type.getName());
    }

    /**
     * Creates a simple context, for tests and standalone use. Loggers go to
     * {@link System#getLogger(String)}.
     *
     * @param instanceName  the instance name
     * @param configuration the configuration
     * @return the context
     */
    static ConnectorContext of(String instanceName, ConnectorConfiguration configuration) {
        Objects.requireNonNull(instanceName, "instanceName");
        Objects.requireNonNull(configuration, "configuration");
        return new ConnectorContext() {
            @Override
            public String instanceName() {
                return instanceName;
            }

            @Override
            public ConnectorConfiguration configuration() {
                return configuration;
            }
        };
    }
}
