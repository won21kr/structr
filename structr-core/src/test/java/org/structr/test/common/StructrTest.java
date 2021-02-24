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
package org.structr.test.common;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.structr.api.config.Settings;
import org.structr.common.AccessMode;
import org.structr.common.SecurityContext;
import org.structr.common.error.FrameworkException;
import org.structr.core.Services;
import org.structr.core.app.App;
import org.structr.core.app.StructrApp;
import org.structr.core.entity.AbstractNode;
import org.structr.core.entity.GenericNode;
import org.structr.core.entity.Principal;
import org.structr.core.entity.Relation;
import org.structr.core.graph.FlushCachesCommand;
import org.structr.core.graph.NodeAttribute;
import org.structr.core.graph.NodeInterface;
import org.structr.core.graph.Tx;
import org.structr.core.property.PropertyKey;
import org.structr.core.property.PropertyMap;
import org.structr.schema.SchemaService;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertNull;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;

/**
 *
 */
public class StructrTest {

	private static final Logger logger = LoggerFactory.getLogger(StructrTest.class.getName());

	protected static SecurityContext securityContext = null;
	protected static String basePath                 = null;
	protected static App app                         = null;
	protected static String randomTenantId           = RandomStringUtils.randomAlphabetic(10).toUpperCase();
	private boolean first                            = true;

	@BeforeMethod
	protected void starting(Method method) {

		System.out.println("######################################################################################");
		System.out.println("# Starting " + this.getClass().getName() + "#" + method.getName() + " with tenant identifier " + randomTenantId);
		System.out.println("######################################################################################");
	}

	@AfterMethod
	protected void finished(Method method) {

		System.out.println("######################################################################################");
		System.out.println("# Finished " + getClass().getName() + "#" + method.getName() + " with tenant identifier " + randomTenantId);
		System.out.println("######################################################################################");
	}

	@BeforeMethod
	public void cleanDatabaseAndSchema() {

		if (!first) {

			try (final Tx tx = app.tx()) {

				// delete everything
				Services.getInstance().getDatabaseService().cleanDatabase();

				FlushCachesCommand.flushAll();

				tx.success();

			} catch (Throwable t) {

				t.printStackTrace();
				logger.error("Exception while trying to clean database: {}", t.getMessage());
			}


			try {

				SchemaService.ensureBuiltinTypesExist(app);

			} catch (Throwable t) {

				t.printStackTrace();
				logger.error("Exception while trying to clean database: {}", t.getMessage());
			}
		}

		first = false;
	}

	@BeforeClass(alwaysRun = true)
	public void startSystem() {

		final Date now          = new Date();
		final long timestamp    = now.getTime();

		basePath = "/tmp/structr-test-" + timestamp;

		Settings.Services.setValue("NodeService SchemaService");

		setupDatabaseConnection();

		// example for new configuration setup
		Settings.BasePath.setValue(basePath);
		Settings.DatabasePath.setValue(basePath + "/db");
		Settings.FilesPath.setValue(basePath + "/files");

		Settings.RelationshipCacheSize.setValue(10000);
		Settings.NodeCacheSize.setValue(10000);

		Settings.SuperUserName.setValue("superadmin");
		Settings.SuperUserPassword.setValue("sehrgeheim");

		//Settings.CypherDebugLogging.setValue(true);

		final Services services = Services.getInstance();

		// wait for service layer to be initialized
		do {
			try { Thread.sleep(100); } catch (Throwable t) {}

		} while (!services.isInitialized());

		securityContext = SecurityContext.getSuperUserInstance();
		app = StructrApp.getInstance(securityContext);
	}

	@AfterClass(alwaysRun = true)
	public void stopSystem() {

		Services.getInstance().shutdown();

		try {
			File testDir = new File(basePath);
			if (testDir.isDirectory()) {

				FileUtils.deleteDirectory(testDir);

			} else {

				testDir.delete();
			}

		} catch (Throwable t) {
			logger.warn("", t);
		}
	}

	protected <T extends NodeInterface> List<T> createTestNodes(final Class<T> type, final int number, final long delay) throws FrameworkException {

		try (final Tx tx = app.tx()) {

			final PropertyMap properties = new PropertyMap();
			final List<T> nodes          = new LinkedList<>();

			properties.put(NodeInterface.visibleToAuthenticatedUsers, false);
			properties.put(NodeInterface.visibleToPublicUsers, false);
			properties.put(NodeInterface.hidden, false);

			for (int i = 0; i < number; i++) {

				nodes.add(app.create(type, properties));

				try {
					Thread.sleep(delay);
				} catch (InterruptedException ex) {
				}
			}

			tx.success();

			return nodes;

		} catch (Throwable t) {

			logger.warn("Unable to create test nodes of type {}: {}", type, t.getMessage());
		}

		return null;
	}

	protected <T extends NodeInterface> List<T> createTestNodes(final Class<T> type, final int number) throws FrameworkException {

		return createTestNodes(type, number, 0);

	}

	protected <T extends NodeInterface> T createTestNode(final Class<T> type) throws FrameworkException {
		return (T) createTestNode(type, new PropertyMap());
	}

	protected <T extends NodeInterface> T createTestNode(final Class<T> type, final String name) throws FrameworkException {

		final PropertyMap map = new PropertyMap();

		map.put(AbstractNode.name, name);

		return (T) createTestNode(type, map);
	}

	protected <T extends NodeInterface> T createTestNode(final Class<T> type, final PropertyMap props) throws FrameworkException {

		props.put(AbstractNode.type, type.getSimpleName());

		try (final Tx tx = app.tx()) {

			final T newNode = app.create(type, props);

			tx.success();

			return newNode;
		}

	}

	protected <T extends NodeInterface> T createTestNode(final Class<T> type, final NodeAttribute... attributes) throws FrameworkException {

		try (final Tx tx = app.tx()) {

			final T newNode = app.create(type, attributes);

			tx.success();

			return newNode;
		}

	}

	protected <T extends Relation> List<T> createTestRelationships(final Class<T> relType, final int number) throws FrameworkException {

		List<GenericNode> nodes = createTestNodes(GenericNode.class, 2);
		final NodeInterface startNode = nodes.get(0);
		final NodeInterface endNode = nodes.get(1);

		try (final Tx tx = app.tx()) {

			List<T> rels = new LinkedList<>();

			for (int i = 0; i < number; i++) {

				rels.add((T) app.create(startNode, endNode, relType));
			}

			tx.success();

			return rels;
		}

	}

	protected <T extends Relation> T createTestRelationship(final NodeInterface startNode, final NodeInterface endNode, final Class<T> relType) throws FrameworkException {

		try (final Tx tx = app.tx()) {

			final T rel = (T) app.create(startNode, endNode, relType);

			tx.success();

			return rel;
		}
	}

	protected <T extends AbstractNode> T createTestNode(final Class<T> type, final Principal owner) throws FrameworkException {
		return (T)createTestNode(type, new PropertyMap(), owner);
	}

	protected <T extends AbstractNode> T createTestNode(final Class<T> type, final PropertyMap props, final Principal owner) throws FrameworkException {

		final App backendApp = StructrApp.getInstance(SecurityContext.getInstance(owner, AccessMode.Backend));

		try (final Tx tx = backendApp.tx()) {

			final T result = backendApp.create(type, props);
			tx.success();

			return result;
		}
	}

	protected void assertNodeExists(final String nodeId) throws FrameworkException {
		assertNotNull(app.getNodeById(nodeId));

	}

	protected void assertNodeNotFound(final String nodeId) throws FrameworkException {
		assertNull(app.getNodeById(nodeId));
	}

	protected <T> List<T> toList(T... elements) {
		return Arrays.asList(elements);
	}

	protected Map<String, Object> toMap(final String key1, final Object value1) {
		return toMap(key1, value1, null, null);
	}

	protected Map<String, Object> toMap(final String key1, final Object value1, final String key2, final Object value2) {
		return toMap(key1, value1, key2, value2, null, null);
	}

	protected Map<String, Object> toMap(final String key1, final Object value1, final String key2, final Object value2, final String key3, final Object value3) {

		final Map<String, Object> map = new LinkedHashMap<>();

		if (key1 != null && value1 != null) {
			map.put(key1, value1);
		}

		if (key2 != null && value2 != null) {
			map.put(key2, value2);
		}

		if (key3 != null && value3 != null) {
			map.put(key3, value3);
		}

		return map;
	}

	protected Class getType(final String typeName) {
		return StructrApp.getConfiguration().getNodeEntityClass(typeName);
	}

	protected PropertyKey<String> getKey(final String typeName, final String keyName) {
		return getKey(typeName, keyName, String.class);
	}

	protected <T> PropertyKey<T> getKey(final String typeName, final String keyName, final Class<T> desiredType) {

		final Class type = getType(typeName);
		if (type != null) {

			return StructrApp.key(type, keyName);
		}

		return null;
	}

	protected void setupDatabaseConnection() {

		// use database driver from system property, default to MemoryDatabaseService
		Settings.DatabaseDriver.setValue(System.getProperty("testDatabaseDriver", Settings.DEFAULT_DATABASE_DRIVER));
		Settings.ConnectionUser.setValue("neo4j");
		Settings.ConnectionPassword.setValue("admin");
		Settings.ConnectionUrl.setValue(Settings.TestingConnectionUrl.getValue());
		Settings.TenantIdentifier.setValue(randomTenantId);
	}

	protected void tryWithTimeout(final Supplier<Boolean> workload, final Runnable onTimeout, final int timeoutInMS) {

		if (workload != null && timeoutInMS >= 0) {
			final long startTime = System.currentTimeMillis();

			do {
				if (workload.get()) {
					return;
				}
			} while ((startTime + timeoutInMS) >= System.currentTimeMillis());
		}

		if (onTimeout != null) {
			onTimeout.run();
		}
	}

	protected void tryWithTimeout(final Supplier<Boolean> workload, final Runnable onTimeout, final int timeoutInMS, final int retryDelayInMS) {

		final long startTime = System.currentTimeMillis();

		if (workload != null && onTimeout != null && timeoutInMS >= 0 && retryDelayInMS > 0) {
			do {
				if (workload.get()) {
					return;
				}

				try {

					Thread.sleep(retryDelayInMS);
				} catch (InterruptedException ex) {

					return;
				}
			} while ((startTime + timeoutInMS) >= System.currentTimeMillis());

			onTimeout.run();
		}
	}
}
