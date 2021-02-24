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
package org.structr.core.app;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.structr.agent.AgentService;
import org.structr.agent.Task;
import org.structr.api.DatabaseService;
import org.structr.api.NotFoundException;
import org.structr.api.config.Settings;
import org.structr.api.graph.GraphProperties;
import org.structr.api.graph.Identity;
import org.structr.api.graph.PropertyContainer;
import org.structr.api.service.Command;
import org.structr.api.service.Service;
import org.structr.api.util.FixedSizeCache;
import org.structr.common.SecurityContext;
import org.structr.common.error.FrameworkException;
import org.structr.common.fulltext.ContentAnalyzer;
import org.structr.common.fulltext.DummyContentAnalyzer;
import org.structr.common.fulltext.DummyFulltextIndexer;
import org.structr.common.fulltext.FulltextIndexer;
import org.structr.core.GraphObject;
import org.structr.core.GraphObjectMap;
import org.structr.core.Services;
import org.structr.core.entity.AbstractNode;
import org.structr.core.entity.Relation;
import org.structr.core.graph.CreateNodeCommand;
import org.structr.core.graph.CreateRelationshipCommand;
import org.structr.core.graph.NativeQueryCommand;
import org.structr.core.graph.DeleteNodeCommand;
import org.structr.core.graph.DeleteRelationshipCommand;
import org.structr.core.graph.GraphDatabaseCommand;
import org.structr.core.graph.MaintenanceCommand;
import org.structr.core.graph.NodeAttribute;
import org.structr.core.graph.NodeFactory;
import org.structr.core.graph.NodeInterface;
import org.structr.core.graph.NodeServiceCommand;
import org.structr.core.graph.RelationshipFactory;
import org.structr.core.graph.RelationshipInterface;
import org.structr.core.graph.Tx;
import org.structr.core.graph.search.SearchNodeCommand;
import org.structr.core.graph.search.SearchRelationshipCommand;
import org.structr.core.property.GenericProperty;
import org.structr.core.property.PropertyKey;
import org.structr.core.property.PropertyMap;
import org.structr.module.StructrModule;
import org.structr.schema.ConfigurationProvider;

/**
 * Stateful facade for accessing the Structr core layer.
 */
public class StructrApp implements App {

	private static final Logger logger = LoggerFactory.getLogger(StructrApp.class);

	private static FixedSizeCache<String, Identity> nodeUuidMap = null;
	private static FixedSizeCache<String, Identity> relUuidMap  = null;
	private static final URI schemaBaseURI                      = URI.create("https://structr.org/v1.1/#");
	private static final Object globalConfigLock                = new Object();
	private Map<String, Object> appContextStore                 = new LinkedHashMap<>();
	private RelationshipFactory relFactory                      = null;
	private NodeFactory nodeFactory                             = null;
	private DatabaseService graphDb                             = null;
	private SecurityContext securityContext                     = null;

	private StructrApp(final SecurityContext securityContext) {

		this.securityContext = securityContext;
		this.relFactory      = new RelationshipFactory<>(securityContext);
		this.nodeFactory     = new NodeFactory<>(securityContext);
	}

	// ----- public methods -----
	@Override
	public <T extends NodeInterface> T create(final Class<T> type, final String name) throws FrameworkException {
		return create(type, new NodeAttribute(key(type, "name"), name));
	}

	@Override
	public <T extends NodeInterface> T create(final Class<T> type, final PropertyMap source) throws FrameworkException {

		if (type == null) {
			throw new FrameworkException(422, "Empty type (null). Please supply a valid class name in the type property.");
		}

		final CreateNodeCommand<T> command = command(CreateNodeCommand.class);
		final PropertyMap properties       = new PropertyMap(source);
		String finalType                   = type.getSimpleName();

		// try to identify the actual type from input set (creation wouldn't work otherwise anyway)
		final String typeFromInput = properties.get(NodeInterface.type);
		if (typeFromInput != null) {

			Class actualType = StructrApp.getConfiguration().getNodeEntityClass(typeFromInput);
			if (actualType == null) {

				// overwrite type information when creating a node (adhere to type specified by resource!)
				properties.put(AbstractNode.type, type.getSimpleName());

			} else if (actualType.isInterface() || Modifier.isAbstract(actualType.getModifiers())) {

				throw new FrameworkException(422, "Invalid abstract type " + type.getSimpleName() + ", please supply a non-abstract class name in the type property");

			} else {

				finalType = actualType.getSimpleName();
			}
		}

		// set type
		properties.put(AbstractNode.type, finalType);

		return command.execute(properties);
	}

	@Override
	public <T extends NodeInterface> T create(final Class<T> type, final NodeAttribute<?>... attributes) throws FrameworkException {

		final List<NodeAttribute<?>> attrs = new LinkedList<>(Arrays.asList(attributes));
		final CreateNodeCommand<T> command = command(CreateNodeCommand.class);

		// add type information when creating a node
		attrs.add(new NodeAttribute(AbstractNode.type, type.getSimpleName()));

		return command.execute(attrs);
	}

	@Override
	public <T extends NodeInterface> void deleteAllNodesOfType(final Class<T> type) throws FrameworkException {

		final DeleteNodeCommand cmd = command(DeleteNodeCommand.class);
		boolean hasMore             = true;

		while (hasMore) {

			// will be set to true below if at least one result was processed
			hasMore = false;

			for (final T t : this.nodeQuery(type).pageSize(Settings.FetchSize.getValue()).page(1).getAsList()) {

				cmd.execute(t);
				hasMore = true;
			}
		}
	}

	@Override
	public void delete(final NodeInterface node) {
		removeNodeFromCache(node);
		command(DeleteNodeCommand.class).execute(node);
	}

	@Override
	public <A extends NodeInterface, B extends NodeInterface, R extends Relation<A, B, ?, ?>> R create(final A fromNode, final B toNode, final Class<R> relType) throws FrameworkException {
		return command(CreateRelationshipCommand.class).execute(fromNode, toNode, relType);
	}

	@Override
	public <A extends NodeInterface, B extends NodeInterface, R extends Relation<A, B, ?, ?>> R create(final A fromNode, final B toNode, final Class<R> relType, final PropertyMap properties) throws FrameworkException {
		return command(CreateRelationshipCommand.class).execute(fromNode, toNode, relType, properties);
	}

	@Override
	public void delete(final RelationshipInterface relationship) {
		removeRelFromCache(relationship);
		command(DeleteRelationshipCommand.class).execute(relationship);
	}

	@Override
	public NodeInterface getNodeById(final String uuid) throws FrameworkException {
		return getNodeById(null, uuid);
	}

	@Override
	public NodeInterface getNodeById(final Class type, final String uuid) throws FrameworkException {

		if (uuid == null) {
			return null;
		}

		final Identity nodeId = getNodeFromCache(uuid);
		if (nodeId == null) {

			final Query query = nodeQuery().uuid(uuid);

			// set type for faster query
			if (type != null) {
				query.andType(type);
			}

			final GraphObject entity = query.getFirst();
			if (entity != null) {

				final PropertyContainer container = entity.getPropertyContainer();

				nodeUuidMap.put(uuid, container.getId());
				return (NodeInterface)entity;
			}

		} else {

			try {
				return nodeFactory.instantiate(getDatabaseService().getNodeById(nodeId));

			} catch (NotFoundException ignore) {
				nodeUuidMap.remove(uuid);
			}
		}

		return null;
	}

	@Override
	public RelationshipInterface getRelationshipById(final String uuid) throws FrameworkException {
		return getRelationshipById(null, uuid);
	}

	@Override
	public RelationshipInterface getRelationshipById(final Class type, final String uuid) throws FrameworkException {

		if (uuid == null) {
			return null;
		}

		final Identity id = getRelFromCache(uuid);
		if (id == null) {

			final Query query = relationshipQuery().uuid(uuid);

			// set type for faster query
			if (type != null) {
				query.andType(type);
			} else {

				logger.warn("Relationship access by UUID is deprecated and not supported by Neo4j, this can take a very long time. Please examine the following stack trace and amend.");
				Thread.dumpStack();
			}

			final GraphObject entity = query.getFirst();
			if (entity != null) {

				final PropertyContainer container = entity.getPropertyContainer();

				relUuidMap.put(uuid, container.getId());
				return (RelationshipInterface)entity;
			}

		} else {

			try {
				return relFactory.instantiate(getDatabaseService().getRelationshipById(id));

			} catch (NotFoundException ignore) {
				relUuidMap.remove(uuid);
			}
		}

		return null;
	}

	@Override
	public <T extends GraphObject> T get(final Class<T> type, final String uuid) throws FrameworkException {

		if (type != null) {

			if (NodeInterface.class.isAssignableFrom(type)) {

				return (T)getNodeById(type, uuid);

			} else if (RelationshipInterface.class.isAssignableFrom(type)) {

				return (T)getRelationshipById(type, uuid);

			} else {

				throw new IllegalStateException("Invalid type " + type + ", cannot be used in query.");
			}
		}

		return null;
	}

	@Override
	public Query<NodeInterface> nodeQuery() {
		return command(SearchNodeCommand.class);
	}

	@Override
	public <T extends NodeInterface> Query<T> nodeQuery(final Class<T> type) {
		return command(SearchNodeCommand.class).andTypes(type);
	}

	@Override
	public Query<RelationshipInterface> relationshipQuery() {
		return command(SearchRelationshipCommand.class);
	}

	@Override
	public <T extends RelationshipInterface> Query<T> relationshipQuery(final Class<T> type) {
		return command(SearchRelationshipCommand.class).andType(type);
	}

	@Override
	public Tx tx() throws FrameworkException {
		return tx(true);
	}

	@Override
	public Tx tx(final boolean doValidation) throws FrameworkException {
		return tx(doValidation, true);
	}

	@Override
	public Tx tx(final boolean doValidation, final boolean doCallbacks) throws FrameworkException {
		return new Tx(securityContext, doValidation, doCallbacks).begin();
	}

	@Override
	public Tx tx(final boolean doValidation, final boolean doCallbacks, final boolean doNotifications) throws FrameworkException {
		return new Tx(securityContext, doValidation, doCallbacks, doNotifications).begin();
	}

	@Override
	public void shutdown() {
		Services.getInstance().shutdown();
	}

	@Override
	public void close() throws IOException {
		shutdown();
	}

	@Override
	public <T extends Command> T command(Class<T> commandType) {
		return Services.getInstance().command(securityContext, commandType);
	}

	@Override
	public void processTasks(Task... tasks) {

		final AgentService agentService = getService(AgentService.class);
		if(agentService != null) {

			for(final Task task : tasks) {

				agentService.processTask(task);
			}
		}
	}

	@Override
	public <T extends Command & MaintenanceCommand> void maintenance(final Class<T> commandClass, final Map<String, Object> propertySet) throws FrameworkException {
		((MaintenanceCommand)Services.getInstance().command(securityContext, commandClass)).execute(propertySet);
	}

	@Override
	public Iterable<GraphObject> query(final String nativeQuery, final Map<String, Object> parameters) throws FrameworkException {
		return Services.getInstance().command(securityContext, NativeQueryCommand.class).execute(nativeQuery, parameters);
	}

	@Override
	public <T extends Service> T getService(final Class<T> serviceClass) {
		return Services.getInstance().getService(serviceClass, "default");
	}

	@Override
	public DatabaseService getDatabaseService() {

		// cache graphdb instance
		if (graphDb == null) {
			graphDb = Services.getInstance().command(securityContext, GraphDatabaseCommand.class).execute();
		}

		return graphDb;
	}

	@Override
	public <T> T getGlobalSetting(final String key, final T defaultValue) throws FrameworkException {

		final DatabaseService service = getDatabaseService();
		if (service != null) {

			final GraphProperties config = service.getGlobalProperties();
			T value                      = null;

			if (config != null) {
				value = (T)config.getProperty(key);
			}

			if (value == null) {
				return defaultValue;
			}

			return value;
		}

		return defaultValue;
	}

	@Override
	public void setGlobalSetting(final String key, final Object value) throws FrameworkException {

		final DatabaseService service = getDatabaseService();
		if (service != null) {

			final GraphProperties config = service.getGlobalProperties();
			if (config != null) {

				config.setProperty(key, value);
			}
		}
	}

	@Override
	public String getInstanceId() throws FrameworkException {

		synchronized (globalConfigLock) {

			String instanceId = (String) getGlobalSetting("structr.instance.id", null);
			if (instanceId == null) {

				instanceId = NodeServiceCommand.getNextUuid();
				setGlobalSetting("structr.instance.id", instanceId);
			}
			return instanceId;
		}
	}

	@Override
	public FulltextIndexer getFulltextIndexer(final Object... params) {

		final Map<String, StructrModule> modules = StructrApp.getConfiguration().getModules();
		final StructrModule module               = modules.get("text-search");

		if (module != null && module instanceof FulltextIndexer) {
			return (FulltextIndexer)module;
		}

		return new DummyFulltextIndexer();
	}

	@Override
	public ContentAnalyzer getContentAnalyzer(final Object... params) {

		final Map<String, StructrModule> modules = StructrApp.getConfiguration().getModules();
		final StructrModule module               = modules.get("text-search");

		if (module != null && module instanceof ContentAnalyzer) {
			return (ContentAnalyzer)module;
		}

		return new DummyContentAnalyzer();
	}

	// ----- public static methods ----
	/**
	 * Constructs a new stateful App instance, initialized with a superuser security context
	 *
	 * @return superuser app instance
	 */
	public static App getInstance() {
		return new StructrApp(SecurityContext.getSuperUserInstance());
	}

	/**
	 * Constructs a new stateful App instance, initialized with the given
	 * security context.
	 *
	 * @param securityContext
	 * @return app instance
	 */
	public static App getInstance(final SecurityContext securityContext) {
		return new StructrApp(securityContext);
	}

	public static ConfigurationProvider getConfiguration() {
		return Services.getInstance().getConfigurationProvider();
	}

	public static <T extends GraphObject> URI getSchemaId(final Class<T> type) {
		initializeSchemaIds();
		return typeIdMap.get(type);
	}

	public static Class resolveSchemaId(final URI uri) {
		initializeSchemaIds();
		return schemaIdMap.get(uri);
	}

	public static URI getSchemaBaseURI() {
		return schemaBaseURI;
	}

	public static void invalidate(final String uuid) {

		if (nodeUuidMap != null) {
			nodeUuidMap.remove(uuid);
		}

		if (relUuidMap != null) {
			relUuidMap.remove(uuid);
		}
	}

	public static <T> PropertyKey<T> key(final Class type, final String name) {
		return StructrApp.key(type, name, true);
	}

	public static <T> PropertyKey<T> key(final Class type, final String name, final boolean logMissing) {

		final ConfigurationProvider config = StructrApp.getConfiguration();
		PropertyKey<T> key                 = config.getPropertyKeyForJSONName(type, name, false);

		if (key == null) {

			// not found, next try: dynamic type
			final Class dynamicType = config.getNodeEntityClass(type.getSimpleName());
			if (dynamicType != null) {

				key = config.getPropertyKeyForJSONName(dynamicType, name, false);

			} else {

				// next try: interface
				final Class iface = config.getInterfaces().get(type.getSimpleName());
				if (iface != null) {

					key = config.getPropertyKeyForJSONName(iface, name, false);
				}
			}

			// store key in cache
			if (key != null) {

				config.setPropertyKeyForJSONName(type, name, key);
			}
		}

		if (key == null) {

			key = new GenericProperty(name);

			if (logMissing && !type.equals(GraphObjectMap.class)) {

				logger.warn("Unknown property key {}.{}! Using generic property key. This may lead to conversion problems. If you encounter problems please report the following source of the call.", type.getSimpleName(), name);

				try {

					// output for first stack trace element "above" this class
					for (StackTraceElement ste : Thread.currentThread().getStackTrace()) {

						if (ste.getClassName().equals(Thread.class.getCanonicalName()) || ste.getClassName().equals(StructrApp.class.getCanonicalName())) {
							continue;
						}

						logger.warn("Source of this call: {}", ste.toString());
						break;
					}

				} catch (SecurityException se) {
					logger.warn("Unable to determine the stack source because the checkPermission flag is set.");
				}
			}
		}

		return key;
	}

	@Override
	public void invalidateCache(){

		if (nodeUuidMap != null) {
			nodeUuidMap.clear();
		}

		if (relUuidMap != null) {
			relUuidMap.clear();
		}

	}

	@Override
	public Map<String, Object> getAppContextStore() {
		return appContextStore;
	}

	// ----- private static methods -----
	private static void initializeSchemaIds() {

		final Map<String, Class> interfaces = StructrApp.getConfiguration().getInterfaces();

		// add Structr interfaces here
		for (final Class type : interfaces.values()) {

			// only register node types
			if (type.isInterface() && NodeInterface.class.isAssignableFrom(type) && !type.getName().startsWith("org.structr.dynamic.")) {

				registerType(type);
			}
		}
	}

	private static void registerType(final Class type) {

		final URI id = schemaBaseURI.resolve(URI.create(("definitions/" + type.getSimpleName())));

		schemaIdMap.put(id, type);
		typeIdMap.put(type, id);
	}

	private static final Map<URI, Class> schemaIdMap = new LinkedHashMap<>();
	private static final Map<Class, URI> typeIdMap   = new LinkedHashMap<>();

	// ---------- private methods -----
	private synchronized Identity getNodeFromCache(final String uuid) {

		if (nodeUuidMap == null) {

			nodeUuidMap = new FixedSizeCache<>("Node UUID cache", Settings.UuidCacheSize.getValue());
		}

		return nodeUuidMap.get(uuid);
	}

	private synchronized Identity getRelFromCache(final String uuid) {

		if (relUuidMap == null) {

			relUuidMap = new FixedSizeCache<>("Relationship UUID cache", Settings.UuidCacheSize.getValue());
		}

		return relUuidMap.get(uuid);
	}

	private synchronized void removeNodeFromCache(final NodeInterface node) {

		if (node != null) {

			final String uuid = node.getUuid();
			if (uuid != null) {

				if (nodeUuidMap == null) {

					nodeUuidMap = new FixedSizeCache<>("Node UUID cache", Settings.UuidCacheSize.getValue());
				}

			}
		}
	}

	private synchronized void removeRelFromCache(final RelationshipInterface rel) {

		if (rel != null) {

			final String uuid = rel.getUuid();
			if (uuid != null) {

				if (relUuidMap == null) {

					relUuidMap = new FixedSizeCache<>("Relationship UUID cache", Settings.UuidCacheSize.getValue());
				}

			}
		}
	}
}
