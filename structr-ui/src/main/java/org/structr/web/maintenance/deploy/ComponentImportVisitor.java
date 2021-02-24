/*
 * Copyright (C) 2010-2021 Structr GmbH
 *
 * This file is part of Structr <http://structr.org>.
 *
 * Structr is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Structr is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Structr.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.structr.web.maintenance.deploy;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.structr.common.SecurityContext;
import org.structr.common.error.FrameworkException;
import org.structr.core.GraphObject;
import org.structr.core.app.App;
import org.structr.core.app.StructrApp;
import org.structr.core.entity.AbstractNode;
import org.structr.core.graph.FlushCachesCommand;
import org.structr.core.graph.Tx;
import org.structr.core.property.GenericProperty;
import org.structr.core.property.PropertyKey;
import org.structr.core.property.PropertyMap;
import org.structr.web.entity.dom.DOMNode;
import org.structr.web.entity.dom.ShadowDocument;
import org.structr.web.entity.dom.Template;
import org.structr.web.importer.Importer;
import org.structr.web.maintenance.DeployCommand;
import org.structr.websocket.command.CreateComponentCommand;

/**
 *
 */
public class ComponentImportVisitor implements FileVisitor<Path> {

	private static final Logger logger       = LoggerFactory.getLogger(ComponentImportVisitor.class.getName());
	private static final GenericProperty internalSharedTemplateKey = new GenericProperty("shared");

	private final List<Path> deferredPaths    = new LinkedList<>();
	private Map<String, Object> configuration = null;
	private SecurityContext securityContext   = null;
	private boolean relativeVisibility        = false;
	private App app                           = null;

	public ComponentImportVisitor(final Map<String, Object> pagesConfiguration, final boolean relativeVisibility) {

		this.relativeVisibility = relativeVisibility;
		this.configuration      = pagesConfiguration;
		this.securityContext    = SecurityContext.getSuperUserInstance();
		this.app                = StructrApp.getInstance();
	}

	@Override
	public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs) throws IOException {
		return FileVisitResult.CONTINUE;
	}

	@Override
	public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {

		if (attrs.isRegularFile()) {

			final String fileName = file.getFileName().toString();
			if (fileName.endsWith(".html")) {

				try {

					createComponent(file, fileName);

				} catch (FrameworkException fex) {
					logger.warn("Exception while importing shared component {}: {}", fileName, fex.toString());
				}
			}

		} else {

			logger.warn("Unexpected directory {} found in components/ directory, ignoring", file.getFileName().toString());
		}

		return FileVisitResult.CONTINUE;
	}

	@Override
	public FileVisitResult visitFileFailed(final Path file, final IOException exc) throws IOException {

		logger.warn("Exception while importing file {}: {}", new Object[] { file.toString(), exc.getMessage() });
		return FileVisitResult.CONTINUE;
	}

	@Override
	public FileVisitResult postVisitDirectory(final Path dir, final IOException exc) throws IOException {
		return FileVisitResult.CONTINUE;
	}

	public List<Path> getDeferredPaths() {
		return deferredPaths;
	}

	// ----- private methods -----
	private DOMNode getExistingComponent(final String name) {

		final App app  = StructrApp.getInstance();
		DOMNode result = null;

		try (final Tx tx = app.tx()) {

			if (DeployCommand.isUuid(name)) {

				result = (DOMNode) StructrApp.getInstance().nodeQuery(DOMNode.class).and(GraphObject.id, name).getFirst();
			} else {

				result = Importer.findSharedComponentByName(name);
			}

			tx.success();

		} catch (FrameworkException fex) {
			logger.warn("Unable to determine if component {} already exists, ignoring.", name);
		}

		return result;
	}

	private void deleteRecursively(final App app, final DOMNode node) throws FrameworkException {

		for (DOMNode child : node.getChildren()) {
			deleteRecursively(app, child);
		}

		for (DOMNode sync : node.getSyncedNodes()) {

			deleteRecursively(app, sync);
		}

		app.delete(node);

		FlushCachesCommand.flushAll();
	}

	private PropertyMap getPropertiesForComponent(final String name) {

		final Object data = configuration.get(name);
		if (data != null && data instanceof Map) {

			try {

				final Map dataMap = ((Map<String, Object>)data);

				// remove unnecessary "shared" key
				dataMap.remove(internalSharedTemplateKey.jsonName());

				DeployCommand.checkOwnerAndSecurity(dataMap);

				return PropertyMap.inputTypeToJavaType(SecurityContext.getSuperUserInstance(), DOMNode.class, dataMap);

			} catch (FrameworkException ex) {
				logger.warn("Unable to resolve properties for shared component: {}", ex.getMessage());
			}
		}

		return null;
	}

	private <T> T get(final PropertyMap src, final PropertyKey<T> key, final T defaultValue) {

		if (src != null) {

			final T t = src.get(key);
			if (t != null) {

				return t;
			}
		}

		return defaultValue;
	}

	private void createComponent(final Path file, final String fileName) throws IOException, FrameworkException {

		final String componentName      = StringUtils.substringBeforeLast(fileName, ".html");

		// either the template was exported with name + uuid or just the uuid
		final boolean byNameAndId       = DeployCommand.endsWithUuid(componentName);
		final boolean byId              = DeployCommand.isUuid(componentName);

		try (final Tx tx = app.tx(true, false, false)) {

			tx.disableChangelog();

			final DOMNode existingComponent = (byId ? app.get(DOMNode.class, componentName) : (byNameAndId ? app.get(DOMNode.class, componentName.substring(componentName.length() - 32)) : getExistingComponent(componentName)));

			final PropertyMap properties    = getPropertiesForComponent(componentName);

			if (properties == null) {

				logger.info("Ignoring {} (not in components.json)", fileName);

			} else {

				if (existingComponent != null) {

					final PropertyKey<String> contentKey = StructrApp.key(Template.class, "content");

					properties.put(contentKey, existingComponent.getProperty(contentKey));

					existingComponent.setOwnerDocument(null);

					if (existingComponent instanceof Template) {

						properties.put(contentKey, existingComponent.getProperty(contentKey));
						existingComponent.setOwnerDocument(null);

					} else {

						deleteRecursively(app, existingComponent);
					}
				}

				final String src        = new String(Files.readAllBytes(file), Charset.forName("UTF-8"));
				boolean visibleToPublic = get(properties, GraphObject.visibleToPublicUsers, false);
				boolean visibleToAuth   = get(properties, GraphObject.visibleToAuthenticatedUsers, false);
				final Importer importer = new Importer(securityContext, src, null, componentName, visibleToPublic, visibleToAuth, false, relativeVisibility);

				// enable literal import of href attributes
				importer.setIsDeployment(true);

				final boolean parseOk = importer.parse(false);
				if (parseOk) {

					logger.info("Importing component {} from {}..", new Object[] { componentName, fileName } );

					// set comment handler that can parse and apply special Structr comments in HTML source files
					importer.setCommentHandler(new DeploymentCommentHandler());

					// parse page
					final ShadowDocument shadowDocument = CreateComponentCommand.getOrCreateHiddenDocument();
					final DOMNode rootElement           = importer.createComponentChildNodes(shadowDocument);

					if (rootElement != null) {

						if (byId) {

							// set UUID
							rootElement.unlockSystemPropertiesOnce();
							rootElement.setProperty(GraphObject.id, componentName);

						} else if (byNameAndId) {

							// the last characters in the name string are the uuid
							final String uuid = componentName.substring(componentName.length() - 32);
							final String name = componentName.substring(0, componentName.length() - 33);

							rootElement.unlockSystemPropertiesOnce();
							rootElement.setProperty(GraphObject.id, uuid);
							properties.put(AbstractNode.name, name);

						} else {

							// set name
							rootElement.setProperty(AbstractNode.name, componentName);
						}

						// store properties from components.json if present
						rootElement.setProperties(securityContext, properties);
					}

					final List<String> missingComponentNames = importer.getMissingComponentNames();
					if (!missingComponentNames.isEmpty()) {

						// there are missing components => defer import for this file
						deferredPaths.add(file);
					}
				}
			}

			tx.success();
		}
	}
}
