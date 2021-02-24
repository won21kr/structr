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
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.structr.common.SecurityContext;
import org.structr.common.error.FrameworkException;
import org.structr.core.GraphObject;
import org.structr.core.app.App;
import org.structr.core.app.StructrApp;
import org.structr.core.graph.Tx;
import org.structr.core.property.PropertyKey;
import org.structr.core.property.PropertyMap;
import org.structr.web.common.FileHelper;
import org.structr.web.entity.dom.DOMNode;
import org.structr.web.entity.dom.Page;
import org.structr.web.importer.Importer;
import org.structr.web.maintenance.DeployCommand;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 *
 */
public class PageImportVisitor implements FileVisitor<Path> {

	private static final Logger logger        = LoggerFactory.getLogger(PageImportVisitor.class.getName());
	private static final String DoctypeString = "<!DOCTYPE";

	private Map<String, Object> pagesConfiguration = null;
	private SecurityContext securityContext        = null;
	private boolean relativeVisibility             = false;
	private Path basePath                          = null;
	private App app                                = null;

	public PageImportVisitor(final Path basePath, final Map<String, Object> pagesConfiguration, final boolean relativeVisibility) {

		this.pagesConfiguration = pagesConfiguration;
		this.securityContext    = SecurityContext.getSuperUserInstance();
		this.securityContext.setDoTransactionNotifications(false);
		this.basePath           = basePath;
		this.app                = StructrApp.getInstance(this.securityContext);
		this.relativeVisibility = relativeVisibility;
	}

	@Override
	public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs) throws IOException {
		return FileVisitResult.CONTINUE;
	}

	@Override
	public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {

		try {

			if (attrs.isDirectory()) {

				createFolder(file);

			} else if (attrs.isRegularFile()) {

				final String fileName = file.getFileName().toString();
				if (fileName.endsWith(".html")) {

					try {

						createPage(file, fileName);

					} catch (FrameworkException fex) {
						logger.warn("Exception while importing page {}: {}", new Object[] { fileName, fex.toString()});
					}
				}
			}

		} catch (Throwable t) {

			logger.warn("", t);
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

	// ----- private methods -----
	private Page getExistingPage(final String name) throws FrameworkException {
		return StructrApp.getInstance().nodeQuery(Page.class).andName(name).getFirst();
	}

	private void deletePage(final App app, final String name) throws FrameworkException {

		final Page page = app.nodeQuery(Page.class).andName(name).getFirst();
		if (page != null) {

			for (final DOMNode child : page.getElements()) {
				app.delete(child);
			}

			app.delete(page);
		}
	}

	private PropertyMap getPropertiesForPage(final String name) {

		final Object data = pagesConfiguration.get(name);
		if (data != null && data instanceof Map) {

			try {

				DeployCommand.checkOwnerAndSecurity((Map<String, Object>)data);

				return PropertyMap.inputTypeToJavaType(SecurityContext.getSuperUserInstance(), Page.class, (Map<String, Object>)data);

			} catch (FrameworkException ex) {
				logger.warn("Unable to resolve properties for page: {}", ex.getMessage());
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

	private void createFolder(final Path file) {

		try (final Tx tx = app.tx(true, false, false)) {

			// create folder
			FileHelper.createFolderPath(securityContext, basePath.relativize(file).toString());

			tx.success();

		} catch (FrameworkException fex) {
			logger.warn("", fex);
		}
	}

	private void createPage(final Path file, final String fileName) throws IOException, FrameworkException {

		final String name = StringUtils.substringBeforeLast(fileName, ".html");

		try (final Tx tx = app.tx(true, false, false)) {

			tx.disableChangelog();

			final PropertyMap properties = getPropertiesForPage(name);

			if (properties == null) {

				logger.info("Ignoring {} (not in pages.json)", fileName);
			} else {

				final Page existingPage      = getExistingPage(name);

				if (existingPage != null) {

					deletePage(app, name);
				}

				final String src         = new String(Files.readAllBytes(file),Charset.forName("UTF-8"));
				final String contentType = get(properties, StructrApp.key(Page.class, "contentType"), "text/html");

				boolean visibleToPublic = get(properties, GraphObject.visibleToPublicUsers, false);
				boolean visibleToAuth   = get(properties, GraphObject.visibleToAuthenticatedUsers, false);

				final Importer importer = new Importer(securityContext, src, null, name, visibleToPublic, visibleToAuth, false, relativeVisibility);

				// enable literal import of href attributes
				importer.setIsDeployment(true);

				if (StringUtils.startsWithIgnoreCase(src, DoctypeString) && "text/html".equals(contentType)) {

					// Import document starts with <!DOCTYPE> definition, so we treat it as an HTML
					// document and use the Structr HTML importer.

					final boolean parseOk = importer.parse();
					if (parseOk) {

						logger.info("Importing page {} from {}..", new Object[] { name, fileName } );

						// set comment handler that can parse and apply special Structr comments in HTML source files
						importer.setCommentHandler(new DeploymentCommentHandler());

						// parse page
						final Page newPage = importer.readPage();

						// remove duplicate elements
						fixDocumentElements(newPage);

						// store properties from pages.json
						newPage.setProperties(securityContext, properties);
					}

				} else {

					// Import document does NOT start with a <!DOCTYPE> definition, so we assume a
					// template or shared component that we need to parse.

					final boolean parseOk = importer.parse(true);
					if (parseOk) {

						logger.info("Importing page {} from {}..", new Object[] { name, fileName } );

						// set comment handler that can parse and apply special Structr comments in HTML source files
						importer.setCommentHandler(new DeploymentCommentHandler());

						// parse page
						final Page newPage = app.create(Page.class, name);

						// store properties from pages.json
						newPage.setProperties(securityContext, properties);

						// add children
						importer.createChildNodes(newPage, newPage);
					}
				}
			}

			tx.success();
		}
	}

	/**
	 * Remove duplicate Head element from import process.
	 * @param page
	 */
	private void fixDocumentElements(final Page page) {

		final NodeList heads = page.getElementsByTagName("head");
		if (heads.getLength() > 1) {

			final Node head1   = heads.item(0);
			final Node head2   = heads.item(1);
			final Node parent  = head1.getParentNode();

			final boolean h1 = head1.hasChildNodes();
			final boolean h2 = head2.hasChildNodes();

			if (h1 && h2) {

				// merge
				for (Node child = head2.getFirstChild(); child != null; child = child.getNextSibling()) {

					head2.removeChild(child);
					head1.appendChild(child);
				}

				parent.removeChild(head2);

			} else if (h1 && !h2) {

				// remove head2
				parent.removeChild(head2);

			} else if (!h1 && h2) {

				// remove head1
				parent.removeChild(head1);

			} else {

				// remove first, doesn't matter
				parent.removeChild(head1);
			}
		}
	}
}
