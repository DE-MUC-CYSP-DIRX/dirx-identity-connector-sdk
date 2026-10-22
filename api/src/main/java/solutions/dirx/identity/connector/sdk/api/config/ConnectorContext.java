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
