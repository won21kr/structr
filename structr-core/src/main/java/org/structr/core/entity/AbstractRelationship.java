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
package org.structr.core.entity;

import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.chemistry.opencmis.commons.enums.PropertyType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.structr.api.Predicate;
import org.structr.api.graph.Direction;
import org.structr.api.graph.Node;
import org.structr.api.graph.PropertyContainer;
import org.structr.api.graph.Relationship;
import org.structr.api.graph.RelationshipType;
import org.structr.cmis.CMISInfo;
import org.structr.common.PropertyView;
import org.structr.common.SecurityContext;
import org.structr.common.ValidationHelper;
import org.structr.common.View;
import org.structr.common.error.ErrorBuffer;
import org.structr.common.error.FrameworkException;
import org.structr.common.error.IdNotFoundToken;
import org.structr.common.error.InternalSystemPropertyToken;
import org.structr.common.error.NullArgumentToken;
import org.structr.common.error.ReadOnlyPropertyToken;
import org.structr.core.GraphObject;
import org.structr.core.app.App;
import org.structr.core.app.StructrApp;
import org.structr.core.converter.PropertyConverter;
import org.structr.core.graph.ModificationQueue;
import org.structr.core.graph.NodeFactory;
import org.structr.core.graph.NodeInterface;
import org.structr.core.graph.RelationshipInterface;
import org.structr.core.property.Property;
import org.structr.core.property.PropertyKey;
import org.structr.core.property.PropertyMap;
import org.structr.core.property.RelationshipTypeProperty;
import org.structr.core.property.SourceId;
import org.structr.core.property.SourceNodeProperty;
import org.structr.core.property.StringProperty;
import org.structr.core.property.TargetId;
import org.structr.core.property.TargetNodeProperty;
import org.structr.core.script.Scripting;
import org.structr.schema.action.ActionContext;
import org.structr.schema.action.Function;


/**
 * Abstract base class for all relationship entities in structr.
 *
 *
 * @param <S>
 * @param <T>
 */
public abstract class AbstractRelationship<S extends NodeInterface, T extends NodeInterface> implements Comparable<AbstractRelationship>, RelationshipInterface {

	private static final Logger logger = LoggerFactory.getLogger(AbstractRelationship.class.getName());

	public static final Property<String>        internalTimestamp  = new StringProperty("internalTimestamp").systemInternal().indexed().unvalidated().writeOnce().partOfBuiltInSchema().category(SYSTEM_CATEGORY);
	public static final Property<String>        relType            = new RelationshipTypeProperty();
	public static final SourceId                sourceId           = new SourceId("sourceId");
	public static final TargetId                targetId           = new TargetId("targetId");
	public static final Property<NodeInterface> sourceNode         = new SourceNodeProperty("sourceNode");
	public static final Property<NodeInterface> targetNode         = new TargetNodeProperty("targetNode");

	public static final View defaultView = new View(AbstractRelationship.class, PropertyView.Public,
		id, type, relType, sourceId, targetId
	);

	public static final View uiView = new View(AbstractRelationship.class, PropertyView.Ui,
		id, type, relType, sourceId, targetId
	);

	public static final View graphView = new View(AbstractRelationship.class, View.INTERNAL_GRAPH_VIEW,
		id, type, relType, sourceNode, targetNode
	);

	public boolean internalSystemPropertiesUnlocked = false;

	private long transactionId                 = -1;
	private boolean readOnlyPropertiesUnlocked = false;

	private String cachedEndNodeId             = null;
	private String cachedStartNodeId           = null;
	private PropertyKey sourceProperty         = null;
	private PropertyKey targetProperty         = null;

	protected SecurityContext securityContext  = null;
	protected Relationship dbRelationship      = null;
	protected Class entityType                 = null;

	public AbstractRelationship() {}

	public AbstractRelationship(final SecurityContext securityContext, final Relationship dbRel, final Class entityType, final long transactionId) {
		init(securityContext, dbRel, entityType, transactionId);
	}

	@Override
	public final void init(final SecurityContext securityContext, final Relationship dbRel, final Class entityType, final long transactionId) {

		this.transactionId   = transactionId;
		this.dbRelationship  = dbRel;
		this.entityType      = entityType;
		this.securityContext = securityContext;
	}

	public Property<String> getSourceIdProperty() {
		return sourceId;
	}

	public Property<String> getTargetIdProperty() {
		return targetId;
	}

	@Override
	public void onRelationshipCreation() {
	}

	@Override
	public Class getEntityType() {
		return entityType;
	}

	@Override
	public long getSourceTransactionId() {
		return transactionId;
	}

	/**
	 * Called when a relationship of this combinedType is instantiated. Please note that
	 * a relationship can (and will) be instantiated several times during a
	 * normal rendering turn.
	 */
	@Override
	public void onRelationshipInstantiation() {

		try {

			if (dbRelationship != null) {

				Node startNode = dbRelationship.getStartNode();
				Node endNode   = dbRelationship.getEndNode();

				if ((startNode != null) && (endNode != null) && startNode.hasProperty(GraphObject.id.dbName()) && endNode.hasProperty(GraphObject.id.dbName())) {

					cachedStartNodeId = (String) startNode.getProperty(GraphObject.id.dbName());
					cachedEndNodeId   = (String) endNode.getProperty(GraphObject.id.dbName());

				}

			}

		} catch (Throwable t) {
		}
	}

	@Override
	public void onRelationshipDeletion() {
	}

	@Override
	public final void setSecurityContext(final SecurityContext securityContext) {
		this.securityContext = securityContext;
	}

	@Override
	public final SecurityContext getSecurityContext() {
		return this.securityContext;
	}

	@Override
	public final void unlockSystemPropertiesOnce() {
		this.internalSystemPropertiesUnlocked = true;
		unlockReadOnlyPropertiesOnce();
	}

	@Override
	public final void unlockReadOnlyPropertiesOnce() {
		this.readOnlyPropertiesUnlocked = true;
	}

	@Override
	public final void removeProperty(final PropertyKey key) throws FrameworkException {

		dbRelationship.removeProperty(key.dbName());
	}

	@Override
	public boolean equals(final Object o) {

		return (o != null && new Integer(this.hashCode()).equals(new Integer(o.hashCode())));

	}

	@Override
	public int hashCode() {
		final String uuid = getUuid();
		if (uuid != null) {
			return uuid.hashCode();
		} else {
			return dbRelationship.getId().hashCode();
		}
	}

	@Override
	public final int compareTo(final AbstractRelationship rel) {

		// TODO: implement finer compare methods, e.g. taking title and position into account
		if (rel == null) {

			return -1;
		}

		return getUuid().compareTo(rel.getUuid());
	}

	/**
	 * Indicates whether this relationship type propagates modifications
	 * in the given direction. Overwrite this method and return true for
	 * the desired direction to enable a callback on non-local node
	 * modification.
	 *
	 * @param direction the direction for which the propagation should is to be returned
	 * @return the propagation status for the given direction
	 */
	public boolean propagatesModifications(Direction direction) {
		return false;
	}

	@Override
	public final String getUuid() {
		return getProperty(AbstractRelationship.id);
	}

	@Override
	public final PropertyMap getProperties() throws FrameworkException {

		Map<String, Object> properties = new LinkedHashMap<>();

		for (String key : dbRelationship.getPropertyKeys()) {

			properties.put(key, dbRelationship.getProperty(key));
		}

		// convert the database properties back to their java types
		return PropertyMap.databaseTypeToJavaType(securityContext, this, properties);

	}

	@Override
	public <T> T getProperty(final PropertyKey<T> key) {
		return getProperty(key, true, null);
	}

	@Override
	public <T> T getProperty(final PropertyKey<T> key, final Predicate<GraphObject> predicate) {
		return getProperty(key, true, predicate);
	}

	private <T> T getProperty(final PropertyKey<T> key, boolean applyConverter, final Predicate<GraphObject> predicate) {

		// early null check, this should not happen...
		if (key == null || key.dbName() == null) {
			return null;
		}

		return key.getProperty(securityContext, this, applyConverter, predicate);
	}

	@Override
	public final <T> Comparable getComparableProperty(final PropertyKey<T> key) {

		if (key != null) {

			final T propertyValue = getProperty(key, false, null);	// get "raw" property without converter

			// check property converter
			PropertyConverter converter = key.databaseConverter(securityContext, this);
			if (converter != null) {

				try {
					return converter.convertForSorting(propertyValue);

				} catch(FrameworkException fex) {
					logger.warn("Unable to convert property {} of type {}: {}", new Object[] {
						key.dbName(),
						getClass().getSimpleName(),
						fex.getMessage()
					});
				}
			}

			// conversion failed, may the property value itself is comparable
			if(propertyValue instanceof Comparable) {
				return (Comparable)propertyValue;
			}

			// last try: convertFromInput to String to make comparable
			if(propertyValue != null) {
				return propertyValue.toString();
			}
		}

		return null;
	}

	/**
	 * Return database relationship
	 *
	 * @return database relationship
	 */
	@Override
	public Relationship getRelationship() {

		return dbRelationship;

	}

	@Override
	public final T getTargetNode() {
		NodeFactory<T> nodeFactory = new NodeFactory<>(securityContext);
		return nodeFactory.instantiate(dbRelationship.getEndNode());
	}

	@Override
	public final T getTargetNodeAsSuperUser() {
		NodeFactory<T> nodeFactory = new NodeFactory<>(SecurityContext.getSuperUserInstance());
		return nodeFactory.instantiate(dbRelationship.getEndNode());
	}

	@Override
	public final S getSourceNode() {
		NodeFactory<S> nodeFactory = new NodeFactory<>(securityContext);
		return nodeFactory.instantiate(dbRelationship.getStartNode());
	}

	@Override
	public final S getSourceNodeAsSuperUser() {
		NodeFactory<S> nodeFactory = new NodeFactory<>(SecurityContext.getSuperUserInstance());
		return nodeFactory.instantiate(dbRelationship.getStartNode());
	}

	@Override
	public final NodeInterface getOtherNode(final NodeInterface node) {
		NodeFactory nodeFactory = new NodeFactory(securityContext);
		return nodeFactory.instantiate(dbRelationship.getOtherNode(node.getNode()));
	}

	public final NodeInterface getOtherNodeAsSuperUser(final NodeInterface node) {
		NodeFactory nodeFactory = new NodeFactory(SecurityContext.getSuperUserInstance());
		return nodeFactory.instantiate(dbRelationship.getOtherNode(node.getNode()));
	}

	@Override
	public final RelationshipType getRelType() {

		if (dbRelationship != null) {

			return dbRelationship.getType();
		}

		return null;
	}

	/**
	 * Return all property keys.
	 *
	 * @return property keys
	 */
	public final Set<PropertyKey> getPropertyKeys() {
		return getPropertyKeys(PropertyView.All);
	}

	// ----- interface GraphObject -----
	@Override
	public Set<PropertyKey> getPropertyKeys(final String propertyView) {
		return StructrApp.getConfiguration().getPropertySet(this.getClass(), propertyView);
	}

	public final Map<String, Long> getRelationshipInfo(Direction direction) {
		return null;
	}

	public final List<AbstractRelationship> getRelationships(String type, Direction dir) {
		return null;
	}

	@Override
	public final String getType() {
		return getRelType().name();
	}

	@Override
	public final PropertyContainer getPropertyContainer() {
		return dbRelationship;
	}

	@Override
	public final String getSourceNodeId() {
		return cachedStartNodeId;
	}

	@Override
	public final String getTargetNodeId() {
		return cachedEndNodeId;

	}

	public final String getOtherNodeId(final AbstractNode node) {

		return getOtherNode(node).getProperty(AbstractRelationship.id);

	}

	@Override
	public void onCreation(SecurityContext securityContext, ErrorBuffer errorBuffer) throws FrameworkException {
	}

	@Override
	public void onModification(SecurityContext securityContext, ErrorBuffer errorBuffer, final ModificationQueue modificationQueue) throws FrameworkException {
	}

	@Override
	public void onDeletion(SecurityContext securityContext, ErrorBuffer errorBuffer, PropertyMap properties) throws FrameworkException {
	}

	@Override
	public void afterCreation(SecurityContext securityContext) throws FrameworkException {
	}

	@Override
	public void afterModification(SecurityContext securityContext) {
	}

	@Override
	public void afterDeletion(SecurityContext securityContext, PropertyMap properties) {
	}

	@Override
	public void ownerModified(SecurityContext securityContext) {
	}

	@Override
	public void securityModified(SecurityContext securityContext) {
	}

	@Override
	public void locationModified(SecurityContext securityContext) {
	}

	@Override
	public void propagatedModification(SecurityContext securityContext) {
	}

	@Override
	public boolean isValid(ErrorBuffer errorBuffer) {

		boolean valid = true;

		valid &= ValidationHelper.isValidStringNotBlank(this, AbstractRelationship.id, errorBuffer);

		return valid;

	}

	@Override
	public void setProperties(final SecurityContext securityContext, final PropertyMap properties) throws FrameworkException {
		setProperties(securityContext, properties, false);
	}

	@Override
	public void setProperties(final SecurityContext securityContext, final PropertyMap properties, final boolean isCreation) throws FrameworkException {

		for (final PropertyKey key : properties.keySet()) {

			if (dbRelationship != null && dbRelationship.hasProperty(key.dbName())) {

				// check for system properties
				if (key.isSystemInternal() && !internalSystemPropertiesUnlocked) {

					throw new FrameworkException(422, "Property " + key.jsonName() + " is an internal system property", new InternalSystemPropertyToken(getClass().getSimpleName(), key));
				}

				// check for read-only properties
				if ((key.isReadOnly() || key.isWriteOnce()) && !readOnlyPropertiesUnlocked && !securityContext.isSuperUser()) {

					throw new FrameworkException(422, "Property " + key.jsonName() + " is read-only", new ReadOnlyPropertyToken(getClass().getSimpleName(), key));
				}
			}
		}

		RelationshipInterface.super.setPropertiesInternal(securityContext, properties, isCreation);
	}

	@Override
	public final <T> Object setProperty(final PropertyKey<T> key, final T value) throws FrameworkException {
		return setProperty(key, value, false);
	}

	@Override
	public final <T> Object setProperty(final PropertyKey<T> key, final T value, final boolean isCreation) throws FrameworkException {

		// clear function property cache in security context since we are about to invalidate past results
		if (securityContext != null) {

			securityContext.getContextStore().clearFunctionPropertyCache();
		}

		if (key == null) {

			logger.error("Tried to set property with null key (action was denied)");

			throw new FrameworkException(422, "Tried to set property with null key (action was denied)", new NullArgumentToken(getClass().getSimpleName(), base));

		}

		try {

			if (dbRelationship != null && dbRelationship.hasProperty(key.dbName())) {

				// check for system properties
				if (key.isSystemInternal() && !internalSystemPropertiesUnlocked) {

					throw new FrameworkException(422, "Property " + key.jsonName() + " is an internal system property", new InternalSystemPropertyToken(getClass().getSimpleName(), key));

				}

				// check for read-only properties
				if ((key.isReadOnly() || key.isWriteOnce()) && !readOnlyPropertiesUnlocked && !securityContext.isSuperUser()) {

					throw new FrameworkException(422, "Property " + key.jsonName() + " is read-only", new ReadOnlyPropertyToken(getClass().getSimpleName(), key));

				}

			}

			return key.setProperty(securityContext, this, value);

		} finally {

			// unconditionally lock read-only properties after every write (attempt) to avoid security problems
			// since we made "unlock_readonly_properties_once" available through scripting
			internalSystemPropertiesUnlocked = false;
			readOnlyPropertiesUnlocked       = false;

		}
	}

	@Override
	public final void setSourceNodeId(final String sourceNodeId) throws FrameworkException {

		// Do nothing if new id equals old
		if (getSourceNodeId().equals(sourceNodeId)) {
			return;
		}

		final App app = StructrApp.getInstance(securityContext);

		final NodeInterface newStartNode = app.getNodeById(sourceNodeId);
		final NodeInterface endNode      = getTargetNode();
		final Class relationType         = getClass();
		final PropertyMap _props         = getProperties();
		final String type                = this.getClass().getSimpleName();

		if (newStartNode == null) {
			throw new FrameworkException(404, "Node with ID " + sourceNodeId + " not found", new IdNotFoundToken(type, sourceNodeId));
		}

		// delete this as the new rel will be the container afterwards
		app.delete(this);

		// create new relationship
		app.create(newStartNode, endNode, relationType, _props);
	}

	@Override
	public final void setTargetNodeId(final String targetNodeId) throws FrameworkException {

		// Do nothing if new id equals old
		if (getTargetNodeId().equals(targetNodeId)) {
			return;
		}

		final App app = StructrApp.getInstance(securityContext);

		final NodeInterface newTargetNode = app.getNodeById(targetNodeId);
		final NodeInterface startNode     = getSourceNode();
		final Class relationType          = getClass();
		final PropertyMap _props          = getProperties();
		final String type                 = this.getClass().getSimpleName();

		if (newTargetNode == null) {
			throw new FrameworkException(404, "Node with ID " + targetNodeId + " not found", new IdNotFoundToken(type, targetNodeId));
		}

		// delete this as the new rel will be the container afterwards
		app.delete(this);

		// create new relationship and store here
		app.create(startNode, newTargetNode, relationType, _props);
	}

	@Override
	public final String getPropertyWithVariableReplacement(final ActionContext renderContext, final PropertyKey<String> key) throws FrameworkException {
		return Scripting.replaceVariables(renderContext, this, getProperty(key), key.jsonName());
	}

	@Override
	public final Object evaluate(final ActionContext actionContext, final String key, final String defaultValue) throws FrameworkException {

		switch (key) {

			case "_source":
				return getSourceNode();

			case "_target":
				return getTargetNode();

			default:

				// evaluate object value or return default
				final Object value = getProperty(StructrApp.getConfiguration().getPropertyKeyForJSONName(entityType, key), actionContext.getPredicate());
				if (value == null) {

					return Function.numberOrString(defaultValue);
				}
				return value;
		}
	}

	@Override
	public Object invokeMethod(final SecurityContext securityContext, final String methodName, final Map<String, Object> parameters, final boolean throwException) throws FrameworkException {
		throw new UnsupportedOperationException("Invoking a method on a relationship is not supported at the moment.");
	}

	public void setSourceProperty(final PropertyKey source) {
		this.sourceProperty = source;
	}

	public void setTargetProperty(final PropertyKey target) {
		this.targetProperty = target;
	}

	public PropertyKey getSourceProperty() {
		return sourceProperty;
	}

	public PropertyKey getTargetProperty() {
		return targetProperty;
	}

	@Override
	public boolean changelogEnabled() {
		return true;
	}

	// ----- protected methods -----
	protected final Direction getDirectionForType(final Class<S> sourceType, final Class<T> targetType, final Class<? extends NodeInterface> type) {

		// FIXME: this method will most likely not do what it's supposed to do..
		if (sourceType.equals(type) && targetType.equals(type)) {
			return Direction.BOTH;
		}

		if (sourceType.equals(type)) {
			return Direction.OUTGOING;
		}

		if (targetType.equals(type)) {
			return Direction.INCOMING;
		}

		/* one of these blocks is wrong...*/
		if (sourceType.isAssignableFrom(type) && targetType.isAssignableFrom(type)) {
			return Direction.BOTH;
		}

		if (sourceType.isAssignableFrom(type)) {
			return Direction.OUTGOING;
		}

		if (targetType.isAssignableFrom(type)) {
			return Direction.INCOMING;
		}

		/* one of these blocks is wrong...*/
		if (type.isAssignableFrom(sourceType) && type.isAssignableFrom(targetType)) {
			return Direction.BOTH;
		}

		if (type.isAssignableFrom(sourceType)) {
			return Direction.OUTGOING;
		}

		if (type.isAssignableFrom(targetType)) {
			return Direction.INCOMING;
		}

		return Direction.BOTH;
	}

	@Override
	public final CMISInfo getCMISInfo() {
		return null;
	}

	// ----- Cloud synchronization and replication -----
	@Override
	public List<GraphObject> getSyncData() {
		return new ArrayList<>(); // provide a basis for super.getSyncData() calls
	}

	@Override
	public boolean isNode() {
		return false;
	}

	@Override
	public boolean isRelationship() {
		return true;
	}

	@Override
	public NodeInterface getSyncNode() {
		throw new ClassCastException(this.getClass() + " cannot be cast to org.structr.core.graph.NodeInterface");
	}

	@Override
	public RelationshipInterface getSyncRelationship() {
		return this;
	}

	// ----- CMIS support methods -----
	public String getCreatedBy() {
		return getProperty(AbstractNode.createdBy);
	}

	public String getLastModifiedBy() {
		return getProperty(AbstractNode.lastModifiedBy);
	}

	public GregorianCalendar getLastModificationDate() {

		final Date creationDate = getProperty(AbstractNode.lastModifiedDate);
		if (creationDate != null) {

			final GregorianCalendar calendar = new GregorianCalendar();
			calendar.setTime(creationDate);

			return calendar;
		}

		return null;
	}

	public GregorianCalendar getCreationDate() {

		final Date creationDate = getProperty(AbstractNode.createdDate);
		if (creationDate != null) {

			final GregorianCalendar calendar = new GregorianCalendar();
			calendar.setTime(creationDate);

			return calendar;
		}

		return null;
	}

	public PropertyMap getDynamicProperties() {

		final PropertyMap propertyMap       = new PropertyMap();
		final Class type                    = getClass();

		for (final PropertyKey key : StructrApp.getConfiguration().getPropertySet(type, PropertyView.All)) {

			// include all dynamic keys in definition
			if (key.isDynamic() || key.isCMISProperty()) {

				// only include primitives here
				final PropertyType dataType = key.getDataType();
				if (dataType != null) {

					propertyMap.put(key, getProperty(key));
				}
			}
		}



		return propertyMap;
	}
}
