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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Describes a connector implementation. Read by {@link ConnectorRegistry} and by hosts. */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ConnectorInfo {

    /**
     * Returns the unique, stable name of the connector, for example {@code "acme-hr"}.
     * Hosts use it to select the connector in their configuration.
     *
     * @return the name
     */
    String name();

    /**
     * Returns the version of the connector.
     *
     * @return the version
     */
    String version() default "";

    /**
     * Returns a human-readable name.
     *
     * @return the display name
     */
    String displayName() default "";

    /**
     * Returns a short description.
     *
     * @return the description
     */
    String description() default "";

    /**
     * Returns the object types the connector supports, for example {@code {"user", "group"}}.
     *
     * @return the object types
     */
    String[] objectTypes() default {};
}
