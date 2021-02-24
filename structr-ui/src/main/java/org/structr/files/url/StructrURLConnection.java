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
package org.structr.files.url;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.structr.common.SecurityContext;
import org.structr.common.error.FrameworkException;
import org.structr.core.app.App;
import org.structr.core.app.StructrApp;
import org.structr.core.graph.Tx;
import org.structr.web.entity.File;

/**
 * A URLConnection that fetches a SecurityContext using a custom URL scheme
 * to be able to identify and use the current user with no access to Structr
 * classes.
 */
public class StructrURLConnection extends URLConnection {

	private static final Logger logger = LoggerFactory.getLogger(StructrURLConnection.class);
	private SecurityContext securityContext = null;

	protected StructrURLConnection(final SecurityContext securityContext, final URL url) {

		super(url);

		this.securityContext = securityContext;
	}

	@Override
	public void connect() throws IOException {
	}

	@Override
	public InputStream getInputStream() throws IOException {

		if (securityContext != null) {

			final App app = StructrApp.getInstance(securityContext);

			try (final Tx tx = app.tx()) {

				final String path = url.getPath();
				final File file   = app.nodeQuery(File.class).and(StructrApp.key(File.class, "path"), path).getFirst();

				if (file != null) {

					return file.getInputStream();
				}

				tx.success();

			} catch (FrameworkException fex) {

			}
		}

		throw new FileNotFoundException(url.toString());
	}
}