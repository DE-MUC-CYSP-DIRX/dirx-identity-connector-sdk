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

package solutions.dirx.identity.connector.sdk.api;

/**
 * Classifies a {@link ConnectorException}. Hosts map these codes to their own error model;
 * DirX Identity, for example, maps them to SPML result codes.
 */
public enum ErrorCode {
    /** The object to create already exists. */
    ALREADY_EXISTS,
    /** The addressed object does not exist. */
    NOT_FOUND,
    /** A value to add is already present. */
    ATTRIBUTE_OR_VALUE_EXISTS,
    /** An attribute or value to delete does not exist. */
    NO_SUCH_ATTRIBUTE,
    /** The request violates the schema of the target. */
    SCHEMA_VIOLATION,
    /** The request is malformed or uses an unsupported feature. */
    INVALID_REQUEST,
    /** The connector does not support the requested operation. */
    UNSUPPORTED_OPERATION,
    /** The target rejected the credentials of the connector. */
    INVALID_CREDENTIALS,
    /** The connector is not allowed to perform the operation. */
    PERMISSION_DENIED,
    /** The target could not be reached. Usually raised as {@link RetryableConnectorException}. */
    CONNECTION_FAILED,
    /** The target did not answer in time. Usually raised as {@link RetryableConnectorException}. */
    TIMEOUT,
    /** The connector configuration is invalid. */
    CONFIGURATION,
    /** Any other error. */
    OTHER
}
