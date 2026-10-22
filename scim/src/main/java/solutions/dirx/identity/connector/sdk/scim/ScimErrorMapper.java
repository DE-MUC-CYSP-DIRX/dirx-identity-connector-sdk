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

package solutions.dirx.identity.connector.sdk.scim;

import java.net.URI;
import solutions.dirx.identity.connector.sdk.api.ConnectorException;
import solutions.dirx.identity.connector.sdk.api.ErrorCode;
import solutions.dirx.identity.connector.sdk.rest.HttpErrorMapper;
import solutions.dirx.identity.connector.sdk.rest.RestResponse;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;

/**
 * Maps SCIM error responses (RFC 7644, section 3.12) to connector exceptions.
 *
 * <p>The {@code scimType} of the error refines the mapping of {@link HttpErrorMapper#DEFAULT}:
 * {@code uniqueness} becomes {@link ErrorCode#ALREADY_EXISTS}, {@code mutability}
 * becomes {@link ErrorCode#SCHEMA_VIOLATION}, {@code noTarget} becomes
 * {@link ErrorCode#NO_SUCH_ATTRIBUTE}, and the other types become
 * {@link ErrorCode#INVALID_REQUEST}.
 */
public final class ScimErrorMapper implements HttpErrorMapper {

    /** The shared instance. */
    public static final ScimErrorMapper INSTANCE = new ScimErrorMapper();

    private ScimErrorMapper() {
    }

    @Override
    public ConnectorException map(String method, URI uri, RestResponse response) {
        ConnectorException fallback = DEFAULT.map(method, uri, response);
        if (fallback.isRetryable()) {
            return fallback;
        }
        String scimType = scimType(response);
        if (scimType == null) {
            return fallback;
        }
        ErrorCode code = switch (scimType) {
            case "uniqueness" -> ErrorCode.ALREADY_EXISTS;
            case "mutability" -> ErrorCode.SCHEMA_VIOLATION;
            case "noTarget" -> ErrorCode.NO_SUCH_ATTRIBUTE;
            case "invalidFilter", "tooMany", "invalidSyntax", "invalidPath", "invalidValue", "invalidVers",
                 "sensitive" -> ErrorCode.INVALID_REQUEST;
            default -> fallback.getErrorCode();
        };
        return new ConnectorException(code, fallback.getMessage());
    }

    private static String scimType(RestResponse response) {
        try {
            JsonNode type = response.json().path("scimType");
            return type.isString() ? type.asString() : null;
        } catch (JacksonException e) {
            return null;
        }
    }
}
