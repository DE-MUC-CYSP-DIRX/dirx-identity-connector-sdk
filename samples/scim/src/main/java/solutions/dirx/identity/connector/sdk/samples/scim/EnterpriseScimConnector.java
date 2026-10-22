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

package solutions.dirx.identity.connector.sdk.samples.scim;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import solutions.dirx.identity.connector.sdk.api.ConnectorException;
import solutions.dirx.identity.connector.sdk.api.CreateRequest;
import solutions.dirx.identity.connector.sdk.api.SearchRequest;
import solutions.dirx.identity.connector.sdk.api.UpdateRequest;
import solutions.dirx.identity.connector.sdk.api.UpdateResult;
import solutions.dirx.identity.connector.sdk.api.model.ConnectorObject;
import solutions.dirx.identity.connector.sdk.api.model.Modification;
import solutions.dirx.identity.connector.sdk.api.spi.ConnectorInfo;
import solutions.dirx.identity.connector.sdk.scim.ScimConnector;

/**
 * SCIM connector for a service provider that supports the enterprise user extension
 * (RFC 7643, section 4.3). Compared to the generic {@link ScimConnector} it
 * <ul>
 * <li>exposes the object type {@code Account} for the {@code /Users} endpoint,</li>
 * <li>presents the extension attributes {@code employeeNumber}, {@code department} and
 * {@code manager} under these flat names instead of their schema-qualified names,</li>
 * <li>writes {@code userName} from the identifier and derives {@code displayName} from the
 * given and family name if it is not set.</li>
 * </ul>
 */
@ConnectorInfo(name = "sample-scim-enterprise", displayName = "SCIM with enterprise extension (sample)",
        description = "Sample SCIM connector mapping the enterprise user extension", objectTypes = "Account")
public class EnterpriseScimConnector extends ScimConnector {

    /** The object type for SCIM users. */
    public static final String ACCOUNT = "Account";
    /** The schema URI of the enterprise user extension. */
    public static final String ENTERPRISE_SCHEMA = "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User";

    private static final Set<String> ENTERPRISE_ATTRIBUTES = Set.of("employeeNumber", "department", "manager");

    /** Creates an uninitialized connector. */
    public EnterpriseScimConnector() {
        // initialized by init()
    }

    @Override
    protected String endpoint(String objectType) {
        return ACCOUNT.equalsIgnoreCase(objectType) ? "Users" : super.endpoint(objectType);
    }

    @Override
    protected List<String> schemas(String objectType) {
        if (ACCOUNT.equalsIgnoreCase(objectType)) {
            return List.of(USER_SCHEMA, ENTERPRISE_SCHEMA);
        }
        return super.schemas(objectType);
    }

    @Override
    protected ObjectNode toResource(CreateRequest request) {
        ObjectNode resource = super.toResource(request);
        if (!ACCOUNT.equalsIgnoreCase(request.objectType())) {
            return resource;
        }
        if (request.identifier() != null && !resource.has("userName")) {
            resource.put("userName", request.identifier().value());
        }
        if (!resource.has("displayName")) {
            String given = resource.path("name").path("givenName").asString("");
            String family = resource.path("name").path("familyName").asString("");
            String displayName = (given + " " + family).trim();
            if (!displayName.isEmpty()) {
                resource.put("displayName", displayName);
            }
        }
        ObjectNode extension = null;
        for (String name : ENTERPRISE_ATTRIBUTES) {
            JsonNode value = resource.remove(name);
            if (value != null) {
                if (extension == null) {
                    extension = resource.putObject(ENTERPRISE_SCHEMA);
                }
                extension.set(name, value);
            }
        }
        return resource;
    }

    @Override
    protected ConnectorObject toConnectorObject(SearchRequest request, JsonNode resource) throws ConnectorException {
        if (resource instanceof ObjectNode object && object.get(ENTERPRISE_SCHEMA) instanceof ObjectNode extension) {
            ObjectNode flat = object.deepCopy();
            flat.remove(ENTERPRISE_SCHEMA);
            for (Map.Entry<String, JsonNode> e : extension.properties()) {
                flat.set(e.getKey(), e.getValue());
            }
            return super.toConnectorObject(request, flat);
        }
        return super.toConnectorObject(request, resource);
    }

    @Override
    public UpdateResult update(UpdateRequest request) throws ConnectorException {
        if (!ACCOUNT.equalsIgnoreCase(request.objectType())) {
            return super.update(request);
        }
        List<Modification> mapped = new ArrayList<>();
        for (Modification m : request.modifications()) {
            boolean extension = ENTERPRISE_ATTRIBUTES.stream().anyMatch(m::hasName);
            // RFC 7644: attributes of an extension are addressed as "<schema URI>:<attribute>"
            mapped.add(extension ? new Modification(m.operation(), ENTERPRISE_SCHEMA + ":" + m.attributeName(),
                    m.values()) : m);
        }
        return super.update(new UpdateRequest(request.objectType(), request.identifier(), mapped,
                request.options()));
    }
}
