/*
 * Copyright (C) 2010-2021 Structr GmbH
 *
 * This file is part of Structr <http://structr.org>.
 *
 * Structr is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Structr is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Structr.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.structr.rest.resource;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.structr.api.search.SortOrder;
import org.structr.api.util.Iterables;
import org.structr.api.util.ResultStream;
import org.structr.common.CaseHelper;
import org.structr.common.Permission;
import org.structr.common.SecurityContext;
import org.structr.common.error.FrameworkException;
import org.structr.core.GraphObject;
import org.structr.core.Value;
import org.structr.core.app.App;
import org.structr.core.app.Query;
import org.structr.core.app.StructrApp;
import org.structr.core.entity.AbstractNode;
import org.structr.core.entity.Principal;
import org.structr.core.graph.NodeFactory;
import org.structr.core.graph.NodeInterface;
import org.structr.core.graph.RelationshipInterface;
import org.structr.core.graph.Tx;
import org.structr.core.graph.search.SearchCommand;
import org.structr.core.property.PropertyKey;
import org.structr.core.property.PropertyMap;
import org.structr.rest.RestMethodResult;
import org.structr.rest.exception.IllegalMethodException;
import org.structr.rest.exception.IllegalPathException;
import org.structr.rest.exception.NotFoundException;
import org.structr.rest.servlet.JsonRestServlet;

/**
 * Base class for all resource constraints. Constraints can be combined with succeeding constraints to avoid unneccesary evaluation.
 */
public abstract class Resource {

	private static final Logger logger = LoggerFactory.getLogger(Resource.class.getName());

	protected SecurityContext securityContext = null;

	public abstract Resource tryCombineWith(Resource next) throws FrameworkException;

	/**
	 * Check and configure this instance with the given values. Please note that you need to set the security context of your class in this method.
	 *
	 * @param part the uri part that matched this resource
	 * @param securityContext the security context of the current request
	 * @param request the current request
	 * @return whether this resource accepts the given uri part
	 * @throws FrameworkException
	 */
	public abstract boolean checkAndConfigure(String part, SecurityContext securityContext, HttpServletRequest request) throws FrameworkException;
	public abstract String getUriPart();
	public abstract Class<? extends GraphObject> getEntityClass();
	public abstract String getResourceSignature();
	public abstract boolean isCollectionResource() throws FrameworkException;

	public abstract ResultStream doGet(final SortOrder sortOrder, final int pageSize, final int page) throws FrameworkException;
	public abstract RestMethodResult doPost(final Map<String, Object> propertySet) throws FrameworkException;

	@Override
	public String toString() {
		return getClass().getName() + "(" + getResourceSignature() + ")";
	}

	public RestMethodResult doHead() throws FrameworkException {
		Thread.dumpStack();
		throw new IllegalStateException("Resource.doHead() called, this should not happen.");
	}

	public RestMethodResult doOptions() throws FrameworkException {
		return new RestMethodResult(HttpServletResponse.SC_OK);
	}

	public RestMethodResult doPatch(List<Map<String, Object>> propertySet) throws FrameworkException {
		throw new IllegalMethodException("PATCH not allowed on " + getClass().getSimpleName());
	}

	public RestMethodResult doDelete() throws FrameworkException {

		final App app      = StructrApp.getInstance(securityContext);
		final int pageSize = 500;
		int count          = 0;
		int chunk          = 0;
		boolean hasMore    = true;

		while (hasMore) {

			// will be set to true below if at least one result was processed
			hasMore = false;

			try (final Tx tx = app.tx(true, true, false)) {

				chunk++;

				// always fetch the first page
				try (final ResultStream<GraphObject> result = doGet(null, pageSize, 1)) {

					for (final GraphObject obj : result) {

						hasMore = true;

						if (obj.isNode()) {

							app.delete((NodeInterface)obj);

						} else {

							app.delete((RelationshipInterface)obj);
						}

						count++;
					}
				}

				tx.success();

				logger.info("DeleteObjects: {} objects processed", count);

			} catch (NotFoundException nfex) {

				// ignore NotFoundException

			} catch (Throwable t) {

				logger.warn("Exception in DELETE chunk #{}: {}", chunk, t.getMessage());

				// we need to break here, otherwise the delete call will loop
				// endlessly, trying to delete the erroneous objects.
				break;
			}
		}

		return new RestMethodResult(HttpServletResponse.SC_OK);
	}

	public RestMethodResult doPut(final Map<String, Object> propertySet) throws FrameworkException {

		final List<GraphObject> results = Iterables.toList(doGet(null, NodeFactory.DEFAULT_PAGE_SIZE, NodeFactory.DEFAULT_PAGE));

		if (results != null && !results.isEmpty()) {

			final Class type = results.get(0).getClass();

			// instruct deserialization strategies to set properties on related nodes
			securityContext.setAttribute("setNestedProperties", true);

			PropertyMap properties = PropertyMap.inputTypeToJavaType(securityContext, type, propertySet);

			for (final GraphObject obj : results) {

				if (obj.isNode() && !obj.getSyncNode().isGranted(Permission.write, securityContext)) {

					final Principal currentUser = securityContext.getUser(false);
					String user = null;

					if (currentUser == null) {
						user = securityContext.isSuperUser() ? "superuser" : "anonymous";
					} else {
						user = currentUser.getProperty(AbstractNode.id);
					}

					throw new FrameworkException(403, "Modification of node " + obj.getProperty(GraphObject.id) + " with type " + obj.getProperty(GraphObject.type) + " by user " + user + " not permitted.");
				}

				obj.setProperties(securityContext, properties);
			}

			return new RestMethodResult(HttpServletResponse.SC_OK);
		}

		throw new IllegalPathException(getResourceSignature() + " can only be applied to a non-empty resource");
	}

	/**
	 *
	 * @param propertyView
	 */
	public void configurePropertyView(final Value<String> propertyView) {
	}

	public void postProcessResultSet(final ResultStream result) {
	}

	public boolean isPrimitiveArray() {
		return false;
	}

	public void setSecurityContext(final SecurityContext securityContext) {
		this.securityContext = securityContext;
	}

	/**
	 * Override this method in your resource implementation and return false
	 * to prevent the creation of an encosing transaction context in your
	 * doPost() method. Default: true.
	 *
	 * @return whether to create transaction around the doPost() method
	 */
	public boolean createPostTransaction() {
		return true;
	}

	public Class getEntityClassOrDefault() {

		final Class entityClass = getEntityClass();
		if (entityClass != null) {

			return entityClass;
		}

		return AbstractNode.class;
	}

	// ----- protected methods -----
	protected PropertyKey findPropertyKey(final TypedIdResource typedIdResource, final TypeResource typeResource) {

		Class sourceNodeType = typedIdResource.getTypeResource().getEntityClass();
		String rawName = typeResource.getRawType();
		PropertyKey key = StructrApp.getConfiguration().getPropertyKeyForJSONName(sourceNodeType, rawName, false);

		if (key == null) {

			// try to convert raw name into lower-case variable name
			key = StructrApp.getConfiguration().getPropertyKeyForJSONName(sourceNodeType, CaseHelper.toLowerCamelCase(rawName), false);
		}

		return key;
	}

	protected String buildLocationHeader(final GraphObject newObject) {

		final StringBuilder uriBuilder = securityContext.getBaseURI();

		uriBuilder.append(getUriPart());
		uriBuilder.append("/");

		if (newObject != null) {

			uriBuilder.append(newObject.getUuid());
		}

		return uriBuilder.toString();
	}

	protected void applyDefaultSorting(final List<? extends GraphObject> list, final SortOrder sortOrder) {

		if (sortOrder != null && !sortOrder.isEmpty()) {

			Collections.sort(list, sortOrder);
		}
	}

	protected void extractDistanceSearch(final HttpServletRequest request, final Query query) {

		if (request != null) {

			final String distance = request.getParameter(SearchCommand.DISTANCE_SEARCH_KEYWORD);

			if (!request.getParameterMap().isEmpty() && StringUtils.isNotBlank(distance)) {

				final String latlon   = request.getParameter(SearchCommand.LAT_LON_SEARCH_KEYWORD);
				if (latlon != null) {

					final String[] parts = latlon.split("[,]+");
					if (parts.length == 2) {

						try {
							final double dist      = Double.parseDouble(distance);
							final double latitude  = Double.parseDouble(parts[0]);
							final double longitude = Double.parseDouble(parts[1]);

							query.location(latitude, longitude, dist);

						} catch (NumberFormatException nex) {
							logger.warn("Unable to parse latitude, longitude or distance for search query {}", latlon);
						}
					}

				} else {

					final double dist     = Double.parseDouble(distance);
					final String location = request.getParameter(SearchCommand.LOCATION_SEARCH_KEYWORD);

					String street     = request.getParameter(SearchCommand.STREET_SEARCH_KEYWORD);
					String house      = request.getParameter(SearchCommand.HOUSE_SEARCH_KEYWORD);
					String postalCode = request.getParameter(SearchCommand.POSTAL_CODE_SEARCH_KEYWORD);
					String city       = request.getParameter(SearchCommand.CITY_SEARCH_KEYWORD);
					String state      = request.getParameter(SearchCommand.STATE_SEARCH_KEYWORD);
					String country    = request.getParameter(SearchCommand.COUNTRY_SEARCH_KEYWORD);

					// if location, use city and street, else use all fields that are there!
					if (location != null) {

						String[] parts = location.split("[,]+");
						switch (parts.length) {

							case 3:
								country = parts[2];	// no break here intentionally

							case 2:
								city = parts[1];	// no break here intentionally

							case 1:
								street = parts[0];
								break;

							default:
								break;
						}
					}

					query.location(street, house, postalCode, city, state, country, dist);
				}
			}
		}
	}

	protected void extractSearchableAttributes(final SecurityContext securityContext, final Class type, final HttpServletRequest request, final Query query) throws FrameworkException {

		if (type != null && request != null && !request.getParameterMap().isEmpty()) {

			final boolean exactSearch          = !(parseInteger(request.getParameter(JsonRestServlet.REQUEST_PARAMETER_LOOSE_SEARCH)) == 1);
			final List<PropertyKey> searchKeys = new LinkedList<>();

			for (final String name : request.getParameterMap().keySet()) {

				final PropertyKey key = StructrApp.getConfiguration().getPropertyKeyForJSONName(type, getFirstPartOfString(name), false);
				if (key != null) {

					// add to list of searchable keys
					searchKeys.add(key);

				} else if (!JsonRestServlet.commonRequestParameters.contains(name)) {

					// exclude common request parameters here (should not throw exception)
					throw new FrameworkException(400, "Unknown search key " + name);
				}
			}

			// sort list of search keys according to their desired order
			// so that querying search attributes can use other attributes
			// to refine their partial results.
			Collections.sort(searchKeys, new PropertyKeyProcessingOrderComparator());

			for (final PropertyKey key : searchKeys) {

				// hand list of search attributes over to key
				key.extractSearchableAttribute(securityContext, request, exactSearch, query);
			}
		}
	}

	protected static int parseInteger(final Object source) {

		try {
			return Integer.parseInt(source.toString());

		} catch (final Throwable t) {}

		return -1;
	}

	// ----- private methods -----
	/**
	 * Returns the first part of the given source string when it contains a "."
	 *
	 * @return source
	 */
	private String getFirstPartOfString(final String source) {

		final int pos = source.indexOf(".");
		if (pos > -1) {

			return source.substring(0, pos);
		}

		return source;
	}

	// ----- nested classes -----
	private static class PropertyKeyProcessingOrderComparator implements Comparator<PropertyKey> {

		@Override
		public int compare(final PropertyKey key1, final PropertyKey key2) {
			return Integer.valueOf(key1.getProcessingOrderPosition()).compareTo(Integer.valueOf(key2.getProcessingOrderPosition()));
		}
	}
}
