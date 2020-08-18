/*
 * Copyright (C) 2010-2020 Structr GmbH
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
package org.structr.bpmn;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.chemistry.opencmis.commons.impl.IOUtils;
import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.http.HttpStatus;
import org.structr.bpmn.importer.BPMNImporter;
import org.structr.rest.service.HttpServiceServlet;
import org.structr.rest.servlet.AbstractDataServlet;

/**
 */
public class BPMNServlet extends AbstractDataServlet implements HttpServiceServlet {

	@Override
	public String getModuleName() {
		return "bpmn";
	}

	@Override
	protected void doGet(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {

		response.setStatus(HttpServletResponse.SC_OK);
	}

	@Override
	protected void doPost(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {

		final BPMNImporter importer = new BPMNImporter();

		try {
			final ServletFileUpload uploader         = new ServletFileUpload();
			final FileItemIterator fileItemsIterator = uploader.getItemIterator(request);

			while (fileItemsIterator.hasNext()) {

				final FileItemStream item = fileItemsIterator.next();
				final StringBuilder buf   = new StringBuilder();
				final String name         = item.getFieldName();

				try (final InputStream is = item.openStream()) {

					buf.append(IOUtils.readAllLines(is));
				}

				switch (name) {

					case "deployment-name":
						importer.setName(buf.toString());
						break;

					case "deploy-changed-only":
						importer.setChangedOnly(Boolean.valueOf(buf.toString()));
				}

				if ("text/xml".equals(item.getContentType()) && name.endsWith(".bpmn")) {

					importer.setXml(buf.toString());
				}
			}

		} catch (FileUploadException ex) {
			Logger.getLogger(BPMNServlet.class.getName()).log(Level.SEVERE, null, ex);
		}

		// import
		if (importer.isReady()) {

			if (importer.doImport()) {

				response.setStatus(HttpStatus.SC_OK);

			} else {

				response.getWriter().println("Unprocessable entity..");
				response.setStatus(HttpStatus.SC_UNPROCESSABLE_ENTITY);
			}
		}

		response.flushBuffer();
	}
}
