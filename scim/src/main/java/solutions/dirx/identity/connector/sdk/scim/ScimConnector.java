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

package solutions.dirx.identity.connector.sdk.scim;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;
import solutions.dirx.identity.connector.sdk.api.Connector;
import solutions.dirx.identity.connector.sdk.api.ConnectorException;
import solutions.dirx.identity.connector.sdk.api.CreateOp;
import solutions.dirx.identity.connector.sdk.api.CreateRequest;
import solutions.dirx.identity.connector.sdk.api.CreateResult;
import solutions.dirx.identity.connector.sdk.api.DeleteOp;
import solutions.dirx.identity.connector.sdk.api.DeleteRequest;
import solutions.dirx.identity.connector.sdk.api.ErrorCode;
import solutions.dirx.identity.connector.sdk.api.ResultHandler;
import solutions.dirx.identity.connector.sdk.api.SearchOp;
import solutions.dirx.identity.connector.sdk.api.SearchRequest;
import solutions.dirx.identity.connector.sdk.api.SearchResult;
import solutions.dirx.identity.connector.sdk.api.TestOp;
import solutions.dirx.identity.connector.sdk.api.UpdateOp;
import solutions.dirx.identity.connector.sdk.api.UpdateRequest;
import solutions.dirx.identity.connector.sdk.api.UpdateResult;
import solutions.dirx.identity.connector.sdk.api.config.ConnectorConfiguration;
import solutions.dirx.identity.connector.sdk.api.config.ConnectorContext;
import solutions.dirx.identity.connector.sdk.api.model.Attribute;
import solutions.dirx.identity.connector.sdk.api.model.ConnectorObject;
import solutions.dirx.identity.connector.sdk.api.model.FilterEvaluator;
import solutions.dirx.identity.connector.sdk.api.model.Identifier;
import solutions.dirx.identity.connector.sdk.api.model.SearchScope;
import solutions.dirx.identity.connector.sdk.api.spi.ConnectorInfo;
import solutions.dirx.identity.connector.sdk.rest.Json;
import solutions.dirx.identity.connector.sdk.rest.PageCookies;
import solutions.dirx.identity.connector.sdk.rest.RestClient;
import solutions.dirx.identity.connector.sdk.rest.RestRequest;
import solutions.dirx.identity.connector.sdk.rest.RestResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * A generic SCIM 2.0 connector (RFC 7643/7644). It can be used as is or subclassed to adapt
 * it to a specific service provider.
 *
 * <p>Objects are identified by the SCIM {@code id}. Resources are mapped to attributes with
 * {@link Json#toAttributes}: nested attributes get dotted names (e.g. {@code name.givenName}),
 * complex values of multi-valued attributes become JSON object strings. Searches use SCIM
 * filters and index-based paging.
 *
 * <p>Configuration (besides the connection URL, which is the SCIM base URL, and the
 * authentication properties of {@link RestClient#fromConfiguration}):
 * <table>
 * <caption>Configuration properties</caption>
 * <tr><th>Property</th><th>Default</th><th>Meaning</th></tr>
 * <tr><td>{@value #PROP_ENDPOINTS}</td><td>{@code User=Users, Group=Groups}</td>
 *     <td>multi-valued {@code objectType=path} mappings; unmapped types use the type as path</td></tr>
 * <tr><td>{@value #PROP_MULTI_VALUED}</td><td>see {@link #DEFAULT_MULTI_VALUED}</td>
 *     <td>multi-valued list of attributes that are always sent as arrays</td></tr>
 * <tr><td>{@value #PROP_NON_STRING}</td><td>{@code active}</td>
 *     <td>attributes whose filter values are written unquoted (booleans, numbers)</td></tr>
 * <tr><td>{@value #PROP_CREATE_IDENTIFIER_ATTRIBUTE}</td><td>(none)</td>
 *     <td>attribute that receives the identifier of create requests, e.g. {@code userName};
 *         without it, the identifier is ignored and the provider assigns the id</td></tr>
 * <tr><td>{@value #PROP_UPDATE_METHOD}</td><td>{@code patch}</td>
 *     <td>{@code patch}, or {@code put} for providers without PATCH support
 *         (read-modify-write)</td></tr>
 * <tr><td>{@value #PROP_PAGE_SIZE}</td><td>100</td>
 *     <td>page size used when the caller requests no paging</td></tr>
 * <tr><td>{@value #PROP_TEST_PATH}</td><td>{@code ServiceProviderConfig}</td>
 *     <td>path read by the test operation</td></tr>
 * </table>
 */
@ConnectorInfo(name = "scim", displayName = "SCIM 2.0", description = "Generic SCIM 2.0 connector",
        objectTypes = {"User", "Group"})
public class ScimConnector implements Connector, TestOp, CreateOp, SearchOp, UpdateOp, DeleteOp {

    /** Property: object type to endpoint mappings. */
    public static final String PROP_ENDPOINTS = "scim.endpoints";
    /** Property: multi-valued attributes. */
    public static final String PROP_MULTI_VALUED = "scim.multiValuedAttributes";
    /** Property: attributes with non-string values in filters. */
    public static final String PROP_NON_STRING = "scim.nonStringAttributes";
    /** Property: attribute receiving the identifier on create. */
    public static final String PROP_CREATE_IDENTIFIER_ATTRIBUTE = "scim.createIdentifierAttribute";
    /** Property: update method ({@code patch} or {@code put}). */
    public static final String PROP_UPDATE_METHOD = "scim.updateMethod";
    /** Property: default page size. */
    public static final String PROP_PAGE_SIZE = "scim.pageSize";
    /** Property: path read by the test operation. */
    public static final String PROP_TEST_PATH = "scim.testPath";

    /** The schema URI of the core user resource. */
    public static final String USER_SCHEMA = "urn:ietf:params:scim:schemas:core:2.0:User";
    /** The schema URI of the core group resource. */
    public static final String GROUP_SCHEMA = "urn:ietf:params:scim:schemas:core:2.0:Group";

    /** Multi-valued attributes of the SCIM core schemas. */
    public static final Set<String> DEFAULT_MULTI_VALUED = Set.of("emails", "phoneNumbers", "ims", "photos",
            "addresses", "groups", "entitlements", "roles", "x509Certificates", "members", "schemas");

    private @Nullable RestClient client;
    private @Nullable ConnectorConfiguration configuration;
    private final Map<String, String> endpoints = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private final Set<String> multiValued = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    private @Nullable ScimFilterTranslator filterTranslator;
    private @Nullable String createIdentifierAttribute;
    private boolean usePut;
    private int defaultPageSize;
    private @Nullable String testPath;

    /** Creates the connector. */
    public ScimConnector() {
    }

    @Override
    public void init(ConnectorContext context) throws ConnectorException {
        configuration = context.configuration();
        endpoints.put("User", "Users");
        endpoints.put("Group", "Groups");
        for (String mapping : configuration.getList(PROP_ENDPOINTS)) {
            int eq = mapping.indexOf('=');
            if (eq <= 0 || eq == mapping.length() - 1) {
                throw new ConnectorException(ErrorCode.CONFIGURATION,
                        PROP_ENDPOINTS + ": expected objectType=path but got '" + mapping + "'");
            }
            endpoints.put(mapping.substring(0, eq).strip(), mapping.substring(eq + 1).strip());
        }
        List<String> configured = configuration.getList(PROP_MULTI_VALUED);
        multiValued.addAll(configured.isEmpty() ? DEFAULT_MULTI_VALUED : configured);
        List<String> nonString = configuration.getList(PROP_NON_STRING);
        Set<String> nonStringSet = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        nonStringSet.addAll(nonString.isEmpty() ? List.of("active") : nonString);
        filterTranslator = new ScimFilterTranslator(nonStringSet::contains);
        createIdentifierAttribute = configuration.get(PROP_CREATE_IDENTIFIER_ATTRIBUTE).orElse(null);
        String method = configuration.get(PROP_UPDATE_METHOD).orElse("patch").toLowerCase(Locale.ROOT);
        if (!method.equals("patch") && !method.equals("put")) {
            throw new ConnectorException(ErrorCode.CONFIGURATION,
                    PROP_UPDATE_METHOD + " must be 'patch' or 'put' but is '" + method + "'");
        }
        usePut = method.equals("put");
        defaultPageSize = configuration.getInt(PROP_PAGE_SIZE, 100);
        if (defaultPageSize < 1) {
            throw new ConnectorException(ErrorCode.CONFIGURATION, PROP_PAGE_SIZE + " must be positive");
        }
        testPath = configuration.get(PROP_TEST_PATH).orElse("ServiceProviderConfig");
        client = createClient(configuration);
    }

    /**
     * Creates the REST client. Override to add headers or a custom authentication.
     *
     * @param config the configuration
     * @return the client
     * @throws ConnectorException if the configuration is invalid
     */
    protected RestClient createClient(ConnectorConfiguration config) throws ConnectorException {
        return RestClient.fromConfiguration(config)
                .errorMapper(ScimErrorMapper.INSTANCE)
                .defaultHeader("Accept", "application/scim+json, application/json")
                .build();
    }

    /**
     * Returns the REST client.
     *
     * @return the client, available after {@link #init}
     */
    protected final RestClient client() {
        return client;
    }

    /**
     * Returns the configuration.
     *
     * @return the configuration, available after {@link #init}
     */
    protected final ConnectorConfiguration configuration() {
        return configuration;
    }

    /**
     * Returns the endpoint path of an object type, relative to the base URL.
     *
     * @param objectType the object type
     * @return the path
     */
    protected String endpoint(String objectType) {
        String type = objectType == null ? "User" : objectType;
        return endpoints.getOrDefault(type, type);
    }

    /**
     * Returns the schema URIs written into new resources.
     *
     * @param objectType the object type
     * @return the schema URIs; empty to omit the {@code schemas} attribute
     */
    protected List<String> schemas(String objectType) {
        String path = endpoint(objectType);
        if (path.equalsIgnoreCase("Users")) {
            return List.of(USER_SCHEMA);
        }
        if (path.equalsIgnoreCase("Groups")) {
            return List.of(GROUP_SCHEMA);
        }
        return List.of();
    }

    /**
     * Tells whether an attribute is multi-valued and must be sent as array.
     *
     * @param objectType the object type
     * @param attribute  the attribute name
     * @return {@code true} if multi-valued
     */
    protected boolean isMultiValued(String objectType, String attribute) {
        return multiValued.contains(attribute);
    }

    @Override
    public void test() throws ConnectorException {
        client.send(RestRequest.get(testPath).build());
    }

    @Override
    public CreateResult create(CreateRequest request) throws ConnectorException {
        ObjectNode resource = toResource(request);
        JsonNode created = client.postJson(endpoint(request.objectType()), resource);
        String id = created.path("id").asString(null);
        if (id == null || id.isEmpty()) {
            throw new ConnectorException(ErrorCode.OTHER, "SCIM provider returned no id for the new resource");
        }
        return CreateResult.of(Identifier.of(id));
    }

    /**
     * Builds the resource sent by {@link #create}.
     *
     * @param request the create request
     * @return the resource
     */
    protected ObjectNode toResource(CreateRequest request) {
        String type = request.objectType();
        List<Attribute> attributes = new ArrayList<>(request.attributes());
        if (createIdentifierAttribute != null && request.attribute(createIdentifierAttribute).isEmpty()) {
            attributes.add(Attribute.of(createIdentifierAttribute, request.identifier().value()));
        }
        ObjectNode resource = Json.toObject(attributes, n -> isMultiValued(type, n));
        expandComplexValues(resource);
        List<String> schemas = schemas(type);
        if (!schemas.isEmpty() && !resource.has("schemas")) {
            ArrayNode array = resource.putArray("schemas");
            schemas.forEach(array::add);
        }
        return resource;
    }

    private static void expandComplexValues(ObjectNode node) {
        for (Map.Entry<String, JsonNode> e : node.properties()) {
            JsonNode value = e.getValue();
            if (value.isArray()) {
                ArrayNode array = (ArrayNode) value;
                for (int i = 0; i < array.size(); i++) {
                    if (array.get(i).isString()) {
                        array.set(i, ScimPatch.toNode(array.get(i).asString()));
                    }
                }
            } else if (value.isObject()) {
                expandComplexValues((ObjectNode) value);
            }
        }
    }

    @Override
    public SearchResult search(SearchRequest request, ResultHandler handler) throws ConnectorException {
        String path = endpoint(request.objectType());
        if (request.base() != null && request.scope() == SearchScope.OBJECT) {
            RestResponse response = client.send(RestRequest.get(path + "/" + encodeId(request.base().value()))
                    .query("attributes", attributesParameter(request)).accept(404).build());
            if (response.statusCode() != 404) {
                ConnectorObject object = toConnectorObject(request, RestClient.json(response));
                if (request.filter() == null || FilterEvaluator.matches(request.filter(), object)) {
                    handler.handle(object);
                }
            }
            return SearchResult.done();
        }
        String filter = request.filter() == null ? null : filterTranslator.translate(request.filter());
        long startIndex = PageCookies.offset(request.pageCookie(), 1);
        int count = request.pageSize() > 0 ? request.pageSize() : defaultPageSize;
        while (true) {
            JsonNode page = RestClient.json(client.send(RestRequest.get(path)
                    .query("filter", filter)
                    .query("attributes", attributesParameter(request))
                    .query("startIndex", startIndex)
                    .query("count", count)
                    .build()));
            JsonNode resources = page.path("Resources");
            int received = 0;
            for (JsonNode resource : resources.values()) {
                received++;
                if (!handler.handle(toConnectorObject(request, resource))) {
                    return SearchResult.done();
                }
            }
            long total = page.path("totalResults").asLong(0);
            long next = startIndex + received;
            if (received == 0 || next > total) {
                return SearchResult.done();
            }
            if (request.pageSize() > 0) {
                return SearchResult.nextPage(PageCookies.ofOffset(next));
            }
            startIndex = next;
        }
    }

    private static @Nullable String attributesParameter(SearchRequest request) {
        if (request.attributesToGet().isEmpty()) {
            return null;
        }
        return String.join(",", request.attributesToGet());
    }

    /**
     * Converts a SCIM resource into a connector object.
     *
     * @param request  the search request
     * @param resource the resource
     * @return the object
     * @throws ConnectorException if the resource has no id
     */
    protected ConnectorObject toConnectorObject(SearchRequest request, JsonNode resource) throws ConnectorException {
        @Nullable String id = resource.path("id").asString(null);
        if (id == null) {
            throw new ConnectorException(ErrorCode.OTHER, "SCIM resource without id: " + resource);
        }
        ConnectorObject.Builder builder = ConnectorObject.builder(request.objectType(), Identifier.of(id));
        for (Attribute a : Json.toAttributes(resource, "id", "schemas")) {
            if (isRequested(request, a.name())) {
                builder.attribute(a);
            }
        }
        return builder.build();
    }

    private static boolean isRequested(SearchRequest request, String name) {
        int dot = name.indexOf('.');
        return request.isRequested(name) || dot > 0 && request.isRequested(name.substring(0, dot));
    }

    @Override
    public UpdateResult update(UpdateRequest request) throws ConnectorException {
        if (request.modifications().isEmpty()) {
            return UpdateResult.unchanged();
        }
        String type = request.objectType();
        String path = endpoint(type) + "/" + encodeId(request.identifier().value());
        if (usePut) {
            JsonNode current = client.getJson(path);
            if (!current.isObject()) {
                throw new ConnectorException(ErrorCode.OTHER, "unexpected SCIM response for " + path);
            }
            ObjectNode resource = (ObjectNode) current;
            resource.remove("meta");
            Json.apply(resource, request.modifications(), n -> isMultiValued(type, n));
            expandComplexValues(resource);
            client.putJson(path, resource);
        } else {
            client.patchJson(path, ScimPatch.build(request.modifications(), n -> isMultiValued(type, n)));
        }
        return UpdateResult.unchanged();
    }

    @Override
    public void delete(DeleteRequest request) throws ConnectorException {
        client.delete(endpoint(request.objectType()) + "/" + encodeId(request.identifier().value()));
    }

    private static String encodeId(String id) {
        return java.net.URLEncoder.encode(id, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
    }

    @Override
    public void close() {
        client = null;
    }
}
