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
package org.structr.bolt;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.time.Duration;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.neo4j.driver.*;
import org.neo4j.driver.exceptions.AuthenticationException;
import org.neo4j.driver.exceptions.ClientException;
import org.neo4j.driver.exceptions.DatabaseException;
import org.neo4j.driver.exceptions.ServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.structr.api.AbstractDatabaseService;
import org.structr.api.DatabaseFeature;
import org.structr.api.NativeQuery;
import org.structr.api.NetworkException;
import org.structr.api.NotInTransactionException;
import org.structr.api.RetryException;
import org.structr.api.Transaction;
import org.structr.api.config.Settings;
import org.structr.api.graph.GraphProperties;
import org.structr.api.graph.Identity;
import org.structr.api.graph.Node;
import org.structr.api.graph.Relationship;
import org.structr.api.index.Index;
import org.structr.api.search.ExactQuery;
import org.structr.api.search.Occurrence;
import org.structr.api.search.QueryContext;
import org.structr.api.search.QueryPredicate;
import org.structr.api.search.SortOrder;
import org.structr.api.search.TypeQuery;
import org.structr.api.util.CountResult;
import org.structr.api.util.NodeWithOwnerResult;

/**
 *
 */
public class BoltDatabaseService extends AbstractDatabaseService implements GraphProperties {

	private static final Logger logger                                = LoggerFactory.getLogger(BoltDatabaseService.class.getName());
	private static final ThreadLocal<SessionTransaction> sessions     = new ThreadLocal<>();
	private final Set<String> supportedQueryLanguages                 = new LinkedHashSet<>();
	private Properties globalGraphProperties                          = null;
	private CypherRelationshipIndex relationshipIndex                 = null;
	private CypherNodeIndex nodeIndex                                 = null;
	private String errorMessage                                       = null;
	private String databaseUrl                                        = null;
	private String databasePath                                       = null;
	private Driver driver                                             = null;

	@Override
	public boolean initialize(final String name) {

		String serviceName = null;

		if (!"default".equals(name)) {

			serviceName = name;
		}

		this.databasePath        = Settings.DatabasePath.getPrefixedValue(serviceName);
		databaseUrl              = Settings.ConnectionUrl.getPrefixedValue(serviceName);
		final String username    = Settings.ConnectionUser.getPrefixedValue(serviceName);
		final String password    = Settings.ConnectionPassword.getPrefixedValue(serviceName);
		String databaseDriverUrl = "bolt://" + databaseUrl;

		// build list of supported query languages
		supportedQueryLanguages.add("application/x-cypher-query");
		supportedQueryLanguages.add("application/cypher");
		supportedQueryLanguages.add("text/cypher");

		if (databaseUrl.length() >= 7 && databaseUrl.substring(0, 7).equalsIgnoreCase("bolt://")) {

			databaseDriverUrl = databaseUrl;

		} else if (databaseUrl.length() >= 15 && databaseUrl.substring(0, 15).equalsIgnoreCase("bolt+routing://")) {

			databaseDriverUrl = databaseUrl;
		}

		// create db directory if it does not exist
		new File(databasePath).mkdirs();

		try {

			final boolean isTesting = Settings.ConnectionUrl.getValue().equals(Settings.TestingConnectionUrl.getValue());

			try {

				driver = GraphDatabase.driver(databaseDriverUrl,
						AuthTokens.basic(username, password),
						Config.build().withEncryption().toConfig()
				);

			} catch (final AuthenticationException auex) {

				if (!isTesting && password != null && !Settings.Neo4jDefaultPassword.getValue().equals(password)) {

					logger.info("Login with credentials from config file failed, trying default credentials...");

					try {
						driver = GraphDatabase.driver(databaseDriverUrl,
								AuthTokens.basic(Settings.Neo4jDefaultUsername.getValue(), Settings.Neo4jDefaultPassword.getValue()),
								Config.build().withEncryption().toConfig()
						);

						logger.info("Successfully logged in with default credentials.");

						setInitialPassword(password);

						logger.info("Initial database password set to value from config file.");

						driver = GraphDatabase.driver(databaseDriverUrl,
								AuthTokens.basic(username, password),
								Config.build().withEncryption().toConfig()
						);

						logger.info("Successfully logged in with configured credentials.");

					} catch (final AuthenticationException auex2) {
						logger.info("Login with default credentials failed.");
					}

				}
			}

			final int relCacheSize  = Settings.RelationshipCacheSize.getPrefixedValue(serviceName);
			final int nodeCacheSize = Settings.NodeCacheSize.getPrefixedValue(serviceName);

			NodeWrapper.initialize(nodeCacheSize);
			logger.info("Node cache size set to {}", nodeCacheSize);

			RelationshipWrapper.initialize(relCacheSize);
			logger.info("Relationship cache size set to {}", relCacheSize);

			// signal success
			return true;

		} catch (AuthenticationException auex) {
			errorMessage = auex.getMessage() + " If you are connecting to this Neo4j instance for the first time, you might need to change the default password in the Neo4j Browser.";
		} catch (ServiceUnavailableException ex) {
			errorMessage = ex.getMessage();
		}

		// service failed to initialize
		return false;
	}

	@Override
	public void shutdown() {

		clearCaches();
		driver.close();
	}

	@Override
	public Transaction beginTx() {

		SessionTransaction session = sessions.get();
		if (session == null || session.isClosed()) {

			try {
				//session = new ReactiveSessionTransaction(this, driver.rxSession());/*
				session = new NonReactiveSessionTransaction(this, driver.asyncSession());//*/
				sessions.set(session);

			} catch (ServiceUnavailableException ex) {
				throw new NetworkException(ex.getMessage(), ex);
			} catch (ClientException cex) {
				logger.warn("Cannot connect to Neo4j database server at {}: {}", databaseUrl, cex.getMessage());
			}
		}

		return session;
	}

	@Override
	public Transaction beginTx(final int timeoutInSeconds) {

		SessionTransaction session = sessions.get();
		if (session == null || session.isClosed()) {

			try {
				//session = new ReactiveSessionTransaction(this, driver.rxSession(), timeoutInSeconds);/*
				session = new NonReactiveSessionTransaction(this, driver.asyncSession(), timeoutInSeconds);//*/
				sessions.set(session);

			} catch (ServiceUnavailableException ex) {
				throw new NetworkException(ex.getMessage(), ex);
			} catch (ClientException cex) {
				logger.warn("Cannot connect to Neo4j database server at {}: {}", databaseUrl, cex.getMessage());
			}
		}

		return session;
	}

	@Override
	public Node createNode(final String type, final Set<String> labels, final Map<String, Object> properties) {

		final StringBuilder buf       = new StringBuilder("CREATE (n");
		final Map<String, Object> map = new HashMap<>();
		final String tenantId         = getTenantIdentifier();

		if (tenantId != null) {

			buf.append(":");
			buf.append(tenantId);
		}

		for (final String label : labels) {

			buf.append(":");
			buf.append(label);
		}

		buf.append(" $properties) RETURN n");

		// make properties available to Cypher statement
		map.put("properties", properties);

		final NodeWrapper newNode = NodeWrapper.newInstance(this, getCurrentTransaction().getNode(buf.toString(), map));

		newNode.setModified();

		return newNode;
	}

	@Override
	public NodeWithOwnerResult createNodeWithOwner(final Identity userId, final String type, final Set<String> labels, final Map<String, Object> nodeProperties, final Map<String, Object> ownsProperties, final Map<String, Object> securityProperties) {

		final Map<String, Object> parameters = new HashMap<>();
		final StringBuilder buf              = new StringBuilder();
		final String tenantId         = getTenantIdentifier();

		buf.append("MATCH (u:NodeInterface:Principal");

		if (tenantId != null) {

			buf.append(":");
			buf.append(tenantId);
		}

		buf.append(") WHERE ID(u) = $userId");
		buf.append(" CREATE (u)-[o:OWNS $ownsProperties]->(n");

		if (tenantId != null) {

			buf.append(":");
			buf.append(tenantId);
		}

		for (final String label : labels) {

			buf.append(":");
			buf.append(label);
		}

		buf.append(" $nodeProperties)<-[s:SECURITY $securityProperties]-(u)");
		buf.append(" RETURN n, s, o");

		// store properties in statement
		parameters.put("userId",             unwrap(userId));
		parameters.put("ownsProperties",     ownsProperties);
		parameters.put("securityProperties", securityProperties);
		parameters.put("nodeProperties",     nodeProperties);

		try {

			for (final Map<String, Object> data : execute(buf.toString(), parameters)) {

				final NodeWrapper newNode             = (NodeWrapper)         data.get("n");
				final RelationshipWrapper securityRel = (RelationshipWrapper) data.get("s");
				final RelationshipWrapper ownsRel     = (RelationshipWrapper) data.get("o");

				newNode.setModified();

				securityRel.setModified();
				securityRel.stale();

				ownsRel.setModified();
				ownsRel.stale();

				((NodeWrapper)ownsRel.getStartNode()).setModified();

				return new NodeWithOwnerResult(newNode, securityRel, ownsRel);
			}

		} catch (ClientException dex) {
			throw SessionTransaction.translateClientException(dex);
		} catch (DatabaseException dex) {
			throw SessionTransaction.translateDatabaseException(dex);
		}

		return null;
	}

	@Override
	public Node getNodeById(final Identity id) {
		return getNodeById(unwrap(id));
	}

	@Override
	public Relationship getRelationshipById(final Identity id) {
		return getRelationshipById(unwrap(id));
	}

	@Override
	public Iterable<Node> getAllNodes() {

		final QueryContext context     = new QueryContext(true);
		final QueryPredicate predicate = new TypePredicate();
		final Index<Node> index        = nodeIndex();

		return index.query(context, predicate, Integer.MAX_VALUE, 1);
	}

	@Override
	public Iterable<Node> getNodesByLabel(final String type) {

		if (type == null) {
			return getAllNodes();
		}

		final QueryContext context     = new QueryContext(true);
		final QueryPredicate predicate = new TypePredicate(type);
		final Index<Node> index        = nodeIndex();

		return index.query(context, predicate, Integer.MAX_VALUE, 1);
	}

	@Override
	public Iterable<Node> getNodesByTypeProperty(final String type) {

		if (type == null) {
			return getAllNodes();
		}

		final QueryContext context     = new QueryContext(true);
		final QueryPredicate predicate = new TypePropertyPredicate(type);
		final Index<Node> index        = nodeIndex();

		return index.query(context, predicate, Integer.MAX_VALUE, 1);
	}

	@Override
	public void deleteNodesByLabel(final String label) {

		final StringBuilder buf = new StringBuilder();
		final String tenantId   = getTenantIdentifier();

		buf.append("MATCH (n");

		if (tenantId != null) {
			buf.append(":");
			buf.append(tenantId);
		}

		buf.append(":");
		buf.append(label);
		buf.append(") DETACH DELETE n");

		consume(buf.toString());
	}

	@Override
	public Iterable<Relationship> getAllRelationships() {

		final Index<Relationship> index = relationshipIndex();
		final QueryPredicate predicate  = new TypePredicate();
		final QueryContext context      = new QueryContext(true);

		return index.query(context, predicate, Integer.MAX_VALUE, 1);
	}

	@Override
	public Iterable<Relationship> getRelationshipsByType(final String type) {

		if (type == null) {
			return getAllRelationships();
		}

		final Index<Relationship> index = relationshipIndex();
		final QueryPredicate predicate  = new TypePredicate(type);
		final QueryContext context      = new QueryContext(true);

		return index.query(context, predicate, Integer.MAX_VALUE, 1);
	}

	@Override
	public GraphProperties getGlobalProperties() {
		return this;
	}

	@Override
	public Index<Node> nodeIndex() {

		if (nodeIndex == null) {
			nodeIndex = new CypherNodeIndex(this);
		}

		return nodeIndex;
	}

	@Override
	public Index<Relationship> relationshipIndex() {

		if (relationshipIndex == null) {
			relationshipIndex = new CypherRelationshipIndex(this);
		}

		return relationshipIndex;
	}

	@Override
	public void updateIndexConfiguration(final Map<String, Map<String, Boolean>> schemaIndexConfigSource, final Map<String, Map<String, Boolean>> removedClassesSource, final boolean createOnly) {

		final ExecutorService executor                           = Executors.newCachedThreadPool();
		final Map<String, Map<String, Object>> existingDbIndexes = new HashMap<>();
		final int timeoutSeconds                                 = 10;

		try {

			executor.submit(() -> {

				try (final Transaction tx = beginTx(timeoutSeconds)) {

					for (final Map<String, Object> row : execute("CALL db.indexes() YIELD name, type, state, labelsOrTypes, properties RETURN {name: name, type: type, labels: labelsOrTypes, properties: properties, state: state}")) {

						for (final Object value : row.values()) {

							final Map<String, Object> valueMap = (Map<String, Object>)value;
							final List<String> labels          = (List<String>)valueMap.get("labels");
							final List<String> properties      = (List<String>)valueMap.get("properties");
							final String indexIdentifier       = StringUtils.join(labels, ", ") + "." + StringUtils.join(properties, ", ");

							// store index config
							existingDbIndexes.put(indexIdentifier, valueMap);
						}
					}

					tx.success();
				}

			}).get(timeoutSeconds, TimeUnit.SECONDS);

		} catch (Throwable t) {
			logger.error(ExceptionUtils.getStackTrace(t));
		}

		logger.debug("Found {} existing indexes", existingDbIndexes.size());

		final AtomicInteger createdIndexes = new AtomicInteger(0);
		final AtomicInteger droppedIndexes = new AtomicInteger(0);

		// create indices for properties of existing classes
		for (final Map.Entry<String, Map<String, Boolean>> entry : schemaIndexConfigSource.entrySet()) {

			final String typeName = entry.getKey();

			for (final Map.Entry<String, Boolean> propertyIndexConfig : entry.getValue().entrySet()) {

				final String indexIdentifier        = typeName + "." + propertyIndexConfig.getKey();
				final Map<String, Object> neoConfig = existingDbIndexes.get(indexIdentifier);
				String indexName                    = null;
				String currentState                 = null;
				boolean indexAlreadyOnline          = false;

				if (neoConfig != null) {

					currentState       = (String)neoConfig.get("state");
					indexAlreadyOnline = Boolean.TRUE.equals("ONLINE".equals(currentState));
					indexName          = (String)neoConfig.get("name");

				}
				final boolean configuredAsIndexed     = propertyIndexConfig.getValue();
				final String propertyKey              = propertyIndexConfig.getKey();
				final boolean finalIndexAlreadyOnline = indexAlreadyOnline;
				final String finalIndexName           = indexName;

				if ("FAILED".equals(currentState)) {

					logger.warn("Index is in FAILED state - dropping the index before handling it further. {}.{}. If this error is recurring, please verify that the data in the concerned property is indexable by Neo4j", typeName, propertyKey);

					final AtomicBoolean retry = new AtomicBoolean(true);
					final AtomicInteger retryCount = new AtomicInteger(0);

					while (retry.get()) {

						retry.set(false);

						try {

							executor.submit(() -> {

								try (final Transaction tx = beginTx(timeoutSeconds)) {

									consume("DROP INDEX " + finalIndexName + " IF EXISTS");

									tx.success();

								} catch (RetryException rex) {

									retry.set(retryCount.incrementAndGet() < 3);
									logger.info("DROP INDEX: retry {}", retryCount.get());

								} catch (Throwable t) {
									logger.warn("Unable to drop failed index: {}", t.getMessage());
								}

								return null;

							}).get(timeoutSeconds, TimeUnit.SECONDS);

						} catch (Throwable t) {
							logger.error(ExceptionUtils.getStackTrace(t));
						}
					}
				}

				final AtomicBoolean retry = new AtomicBoolean(true);
				final AtomicInteger retryCount = new AtomicInteger(0);

				while (retry.get()) {

					retry.set(false);

					try {

						executor.submit(() -> {

							try (final Transaction tx = beginTx(timeoutSeconds)) {

								if (configuredAsIndexed) {

									if (!finalIndexAlreadyOnline) {

										try {

											consume("CREATE INDEX IF NOT EXISTS FOR (n:" + typeName + ") ON (n.`" + propertyKey + "`)");
											createdIndexes.incrementAndGet();

										} catch (Throwable t) {
											logger.warn("Unable to create index for {}.{}: {}", typeName, propertyKey, t.getMessage());
										}
									}

								} else if (finalIndexAlreadyOnline && !createOnly) {

									try {

										consume("DROP INDEX " + finalIndexName + " IF EXISTS");
										droppedIndexes.incrementAndGet();

									} catch (Throwable t) {
										logger.warn("Unable to drop index {}.{}: {}", typeName, propertyKey, t.getMessage());
									}
								}

								tx.success();

							} catch (RetryException rex) {

								retry.set(retryCount.incrementAndGet() < 3);
								logger.info("INDEX update: retry {}", retryCount.get());

							} catch (IllegalStateException i) {

								// if the driver instance is already closed, there is nothing we can do => exit
								return;

							} catch (Throwable t) {

								logger.warn("Unable to update index configuration: {}", t.getMessage());
							}

						}).get(timeoutSeconds, TimeUnit.SECONDS);

					} catch (Throwable t) {}
				}
			}
		}

		if (createdIndexes.get() > 0) {
			logger.debug("Created {} indexes", createdIndexes.get());
		}

		if (droppedIndexes.get() > 0) {
			logger.debug("Dropped {} indexes", droppedIndexes.get());
		}

		if (!createOnly) {

			final AtomicInteger droppedIndexesOfRemovedTypes = new AtomicInteger(0);
			final List removedTypes = new LinkedList();

			// drop indices for all indexed properties of removed classes
			for (final Map.Entry<String, Map<String, Boolean>> entry : removedClassesSource.entrySet()) {

				final String typeName = entry.getKey();
				removedTypes.add(typeName);

				for (final Map.Entry<String, Boolean> propertyIndexConfig : entry.getValue().entrySet()) {

					final String indexIdentifier        = typeName + "." + propertyIndexConfig.getKey();
					final Map<String, Object> neoConfig = existingDbIndexes.get(indexIdentifier);
					final boolean configuredAsIndexed   = propertyIndexConfig.getValue();
					final String indexName              = (String)neoConfig.get("name");
					final boolean indexExists           = (existingDbIndexes.get(indexIdentifier) != null);
					final String propertyKey            = propertyIndexConfig.getKey();

					if (indexExists && configuredAsIndexed) {

						final AtomicBoolean retry = new AtomicBoolean(true);
						final AtomicInteger retryCount = new AtomicInteger(0);

						while (retry.get()) {

							retry.set(false);

							try {

								executor.submit(() -> {

									try (final Transaction tx = beginTx(timeoutSeconds)) {

										// drop index
										consume("DROP INDEX " + indexName + " IF EXISTS");
										droppedIndexesOfRemovedTypes.incrementAndGet();

										tx.success();

									} catch (RetryException rex) {

										retry.set(retryCount.incrementAndGet() < 3);
										logger.info("DROP INDEX: retry {}", retryCount.get());

									} catch (Throwable t) {
										logger.warn("Unable to drop {}{}: {}", typeName, propertyKey, t.getMessage());
									}

								}).get(timeoutSeconds, TimeUnit.SECONDS);

							} catch (Throwable t) {}
						}
					}
				}
			}

			if (droppedIndexesOfRemovedTypes.get() > 0) {
				logger.debug("Dropped {} indexes of deleted types ({})", droppedIndexesOfRemovedTypes.get(), StringUtils.join(removedTypes, ", "));
			}
		}
	}

	@Override
	public boolean isIndexUpdateFinished() {
		return true;
	}

	@Override
	public <T> T execute(final NativeQuery<T> nativeQuery) {

		if (nativeQuery instanceof AbstractNativeQuery) {

			return (T)((AbstractNativeQuery)nativeQuery).execute(getCurrentTransaction());
		}

		throw new IllegalArgumentException("Unsupported query type " + nativeQuery.getClass().getName() + ".");
	}

	@Override
	public <T> NativeQuery<T> query(final Object query, final Class<T> resultType) {

		if (!(query instanceof String)) {
			throw new IllegalArgumentException("Unsupported query type " + query.getClass().getName() + ", expected String.");
		}

		return createQuery((String)query, resultType);
	}

	@Override
	public void clearCaches() {

		NodeWrapper.clearCache();
		RelationshipWrapper.clearCache();
	}

	@Override
	public void cleanDatabase() {

		final String tenantId = getTenantIdentifier();
		if (tenantId != null) {

			consume("MATCH (n:" + tenantId + ") DETACH DELETE n", Collections.emptyMap());

		} else {

			consume("MATCH (n) DETACH DELETE n", Collections.emptyMap());
		}
	}

	public SessionTransaction getCurrentTransaction() {
		return getCurrentTransaction(true);
	}

	public SessionTransaction getCurrentTransaction(final boolean throwNotInTransactionException) {

		final SessionTransaction tx = sessions.get();
		if (throwNotInTransactionException && (tx == null || tx.isClosed())) {

			throw new NotInTransactionException("Not in transaction");
		}

		return tx;
	}

	boolean logQueries() {
		return Settings.CypherDebugLogging.getValue();
	}

	boolean logPingQueries() {
		return Settings.CypherDebugLoggingPing.getValue();
	}

	long unwrap(final Identity identity) {

		if (identity instanceof BoltIdentity) {

			return ((BoltIdentity)identity).getId();
		}

		throw new IllegalArgumentException("This implementation cannot handle Identity objects of type " + identity.getClass().getName() + ".");
	}

	Node getNodeById(final long id) {
		return NodeWrapper.newInstance(this, id);
	}

	Relationship getRelationshipById(final long id) {
		return RelationshipWrapper.newInstance(this, id);
	}

	void consume(final String nativeQuery) {
		consume(nativeQuery, Collections.EMPTY_MAP);
	}

	void consume(final String nativeQuery, final Map<String, Object> parameters) {
		getCurrentTransaction().set(nativeQuery, parameters);
	}

	Iterable<Map<String, Object>> execute(final String nativeQuery) {
		return execute(nativeQuery, Collections.EMPTY_MAP);
	}

	Iterable<Map<String, Object>> execute(final String nativeQuery, final Map<String, Object> parameters) {
		return getCurrentTransaction().run(nativeQuery, parameters);
	}

	TransactionConfig getTransactionConfig(final long id) {

		final Map<String, Object> metadata = new HashMap<>();
		final Thread currentThread         = Thread.currentThread();

		metadata.put("id",         id);
		metadata.put("pid",        ProcessHandle.current().pid());
		metadata.put("threadId",   currentThread.getId());

		if (currentThread.getName() != null) {

			metadata.put("threadName", currentThread.getName());
		}

		return TransactionConfig
			.builder()
			.withMetadata(metadata)
			.build();
	}

	TransactionConfig getTransactionConfigForTimeout(final int seconds, final long id) {

		final Map<String, Object> metadata = new HashMap<>();
		final Thread currentThread         = Thread.currentThread();

		metadata.put("id",         id);
		metadata.put("pid",        ProcessHandle.current().pid());
		metadata.put("threadId",   currentThread.getId());

		if (currentThread.getName() != null) {

			metadata.put("threadName", currentThread.getName());
		}

		return TransactionConfig
			.builder()
			.withTimeout(Duration.ofSeconds(seconds))
			.withMetadata(metadata)
			.build();
	}

	// ----- interface GraphProperties -----
	@Override
	public void setProperty(final String name, final Object value) {

		final Properties properties = getProperties();
		boolean hasChanges          = false;

		if (value == null) {

			if (properties.containsKey(name)) {

				properties.remove(name);
				hasChanges = true;
			}

		} else {

			properties.setProperty(name, value.toString());
			hasChanges = true;
		}

		if (hasChanges) {

			final File propertiesFile   = new File(databasePath + "/graph.properties");

			try (final Writer writer = new FileWriter(propertiesFile)) {

				properties.store(writer, "Created by Structr at " + new Date());

			} catch (IOException ioex) {

				logger.warn("Unable to write properties file", ioex);
			}
		}
	}

	@Override
	public Object getProperty(final String name) {
		return getProperties().getProperty(name);
	}

	@Override
	public CountResult getNodeAndRelationshipCount() {

		final String tenantId = getTenantIdentifier();
		final String part     = tenantId != null ? ":" + tenantId : "";
		final long nodeCount  = getCount("MATCH (n" + part + ":NodeInterface) RETURN COUNT(n) AS count", "count");
		final long relCount   = getCount("MATCH (n" + part + ":NodeInterface)-[r]->() RETURN COUNT(r) AS count", "count");
		final long userCount  = getCount("MATCH (n" + part + ":User) RETURN COUNT(n) AS count", "count");

		return new CountResult(nodeCount, relCount, userCount);
	}

	@Override
	public boolean supportsFeature(final DatabaseFeature feature, final Object... parameters) {

		switch (feature) {

			case LargeStringIndexing:
				return false;

			case QueryLanguage:

				final String param = getStringParameter(parameters);
				if (param != null) {

					return supportedQueryLanguages.contains(param.toLowerCase());
				}

			case SpatialQueries:
				return true;

			case AuthenticationRequired:
				return true;
		}

		return false;
	}

	@Override
	public String getErrorMessage() {
		return errorMessage;
	}

	@Override
	public Map<String, Map<String, Integer>> getCachesInfo() {
		return Map.of(
			"nodes",         NodeWrapper.nodeCache.getCacheInfo(),
			"relationships", RelationshipWrapper.relationshipCache.getCacheInfo()
		);
	}

	// ----- private methods -----
	private void createUUIDConstraint() {

		// add UUID uniqueness constraint
		try (final Session session = driver.session()) {

			// this call may fail silently (e.g. if the index does not exist yet)
			try (final org.neo4j.driver.Transaction tx = session.beginTransaction()) {

				tx.run("DROP INDEX ON :NodeInterface(id)");
				tx.commit();

			} catch (Throwable t) { }

			// this call may NOT fail silently, hence we don't catch any exceptions
			try (final org.neo4j.driver.Transaction tx = session.beginTransaction()) {

				tx.run("CREATE CONSTRAINT ON (node:NodeInterface) ASSERT node.id IS UNIQUE");
				tx.commit();
			}
		}
	}

	private Properties getProperties() {

		if (globalGraphProperties == null) {

			globalGraphProperties = new Properties();
			final File propertiesFile   = new File(databasePath + "/graph.properties");

			try (final Reader reader = new FileReader(propertiesFile)) {

				globalGraphProperties.load(reader);

			} catch (IOException ioex) {}
		}

		return globalGraphProperties;
	}

	private long getCount(final String query, final String resultKey) {

		for (final Map<String, Object> row : execute(query)) {

			if (row.containsKey(resultKey)) {

				final Object value = row.get(resultKey);
				if (value != null && value instanceof Number) {

					final Number number = (Number)value;
					return number.intValue();
				}
			}
		}

		return 0;
	}

	// ----- nested classes -----
	private static class TypePredicate implements TypeQuery {

		protected String mainType = null;
		protected String name     = null;

		public TypePredicate() {
		}

		public TypePredicate(final String mainType) {
			this.mainType = mainType;
		}

		@Override
		public Class getSourceType() {
			return null;
		}

		@Override
		public Class getTargetType() {
			return null;
		}

		@Override
		public Class getQueryType() {
			return TypeQuery.class;
		}

		@Override
		public String getName() {
			return "type";
		}

		@Override
		public Class getType() {
			return String.class;
		}

		@Override
		public Object getValue() {
			return mainType;
		}

		@Override
		public String getLabel() {
			return null;
		}

		@Override
		public Occurrence getOccurrence() {
			return Occurrence.REQUIRED;
		}

		@Override
		public boolean isExactMatch() {
			return true;
		}

		@Override
		public SortOrder getSortOrder() {
			return null;
		}
	}

	private static class TypePropertyPredicate implements ExactQuery {

		protected String type = null;

		public TypePropertyPredicate(final String type) {
			this.type = type;
		}

		@Override
		public Class getQueryType() {
			return ExactQuery.class;
		}

		@Override
		public String getName() {
			return "type";
		}

		@Override
		public Class getType() {
			return String.class;
		}

		@Override
		public Object getValue() {
			return type;
		}

		@Override
		public String getLabel() {
			return null;
		}

		@Override
		public Occurrence getOccurrence() {
			return Occurrence.REQUIRED;
		}

		@Override
		public boolean isExactMatch() {
			return true;
		}

		@Override
		public SortOrder getSortOrder() {
			return null;
		}

	}

	private <T> NativeQuery<T> createQuery(final String query, final Class<T> type) {

		if (Iterable.class.equals(type)) {
			return (NativeQuery<T>)new IterableQuery(query);
		}

		if (Boolean.class.equals(type)) {
			return (NativeQuery<T>)new BooleanQuery(query);
		}

		if (Long.class.equals(type)) {
			return (NativeQuery<T>)new LongQuery(query);
		}

		return null;
	}

	private String getStringParameter(final Object[] parameters) {

		if (parameters != null && parameters.length > 0) {

			final Object param = parameters[0];
			if (param instanceof String) {

				return (String)param;
			}
		}

		return null;
	}

	private void setInitialPassword(final String initialPassword) {

		try (final Session session = driver.session()) {

			// this call may fail silently (e.g. if the index does not exist yet)
			try (final org.neo4j.driver.v1.Transaction tx = session.beginTransaction()) {

				tx.run("CALL dbms.changePassword('" + initialPassword + "')");
				tx.success();

			} catch (Throwable t) {
				logger.warn("Unable to change password properties file", t);
			}
		}
	}

}
