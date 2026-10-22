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
