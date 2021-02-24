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
package org.structr.web.servlet;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.lingala.zip4j.ZipFile;
import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.structr.api.config.Settings;
import org.structr.common.AccessMode;
import org.structr.common.PathHelper;
import org.structr.common.SecurityContext;
import org.structr.common.error.FrameworkException;
import org.structr.core.app.StructrApp;
import org.structr.core.auth.exception.AuthenticationException;
import org.structr.core.graph.Tx;
import org.structr.rest.common.HttpHelper;
import org.structr.rest.service.HttpServiceServlet;
import org.structr.rest.service.StructrHttpServiceConfig;
import org.structr.rest.servlet.AbstractServletBase;
import org.structr.web.auth.UiAuthenticator;
import org.structr.web.maintenance.DeployCommand;

//~--- classes ----------------------------------------------------------------
/**
 * Endpoint for deployment file upload
 */
public class DeploymentServlet extends AbstractServletBase implements HttpServiceServlet {

	private static final Logger logger = LoggerFactory.getLogger(DeploymentServlet.class.getName());

	private static final String DOWNLOAD_URL_PARAMETER = "downloadUrl";
	private static final String REDIRECT_URL_PARAMETER = "redirectUrl";
	private static final String NAME_PARAMETER         = "name";
	private static final int MEGABYTE = 1024 * 1024;
	private static final int MEMORY_THRESHOLD = 10 * MEGABYTE;  // above 10 MB, files are stored on disk

	// non-static fields
	private ServletFileUpload uploader = null;
	private File filesDir = null;
	private final StructrHttpServiceConfig config = new StructrHttpServiceConfig();

	public DeploymentServlet() {
	}

	//~--- methods --------------------------------------------------------
	@Override
	public StructrHttpServiceConfig getConfig() {
		return config;
	}

	@Override
	public String getModuleName() {
		return "deployment";
	}

	@Override
	public void init() {

		try (final Tx tx = StructrApp.getInstance().tx()) {
			DiskFileItemFactory fileFactory = new DiskFileItemFactory();
			fileFactory.setSizeThreshold(MEMORY_THRESHOLD);

			filesDir = new File(Settings.TmpPath.getValue()); // new File(Services.getInstance().getTmpPath());
			if (!filesDir.exists()) {

				filesDir.mkdir();
			}

			fileFactory.setRepository(filesDir);
			uploader = new ServletFileUpload(fileFactory);

			tx.success();

		} catch (FrameworkException t) {

			logger.warn("", t);
		}
	}

	@Override
	public void destroy() {
	}

	@Override
	protected void doGet(final HttpServletRequest request, final HttpServletResponse response) throws ServletException {

		initRequest(request, response);

		SecurityContext securityContext = null;

		setCustomResponseHeaders(response);

		try (final Tx tx = StructrApp.getInstance().tx()) {

			try {

				securityContext = getConfig().getAuthenticator().initializeAndExamineRequest(request, response);

			} catch (AuthenticationException ae) {

				response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
				response.getOutputStream().write("ERROR (401): Invalid user or password.\n".getBytes("UTF-8"));
				return;
			}

			if (!securityContext.isSuperUser()) {
				response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
				response.getOutputStream().write("ERROR (401): Download of export ZIP file only allowed for admins.\n".getBytes("UTF-8"));
				return;
			}

			tx.success();

		} catch (Throwable t) {

			logger.error("Exception while processing request", t);
		}

		try {

			final String name = request.getParameter(NAME_PARAMETER);

			if (name != null) {

				logger.info("Preparing deployment export for download as zip");

				DeployCommand deployCommand = StructrApp.getInstance(securityContext).command(DeployCommand.class);
				final Path tmpDir = Paths.get(System.getProperty("java.io.tmpdir"), Long.toString(System.currentTimeMillis()), name);
				final String exportTargetFolder = tmpDir.toString();

				if (!exportTargetFolder.equals(tmpDir.normalize().toString())) {

					final String message = "ERROR (403): Path traversal not allowed - not serving deployment zip!\n";
					logger.error(message);

					response.setStatus(HttpServletResponse.SC_FORBIDDEN);
					response.getOutputStream().write(message.getBytes("UTF-8"));

					return;
				}

				final Map<String, Object> attributes = new HashMap<>();

				attributes.put("mode", "export");
				attributes.put("target", exportTargetFolder);

				deployCommand.execute(attributes);

				try {

					logger.info("Creating zip");

					final File file = zip(exportTargetFolder);

					response.setContentType("application/zip");
					response.addHeader("Content-Disposition", "attachment; filename=\"" + file.getName() + "\"");

					final FileInputStream     in  = new FileInputStream(file);
					final ServletOutputStream out = response.getOutputStream();

					final long fileSize = IOUtils.copyLarge(in, out);
					final int status    = response.getStatus();

					response.addHeader("Content-Length", Long.toString(fileSize));
					response.setStatus(status);

					out.flush();
					out.close();

				} catch (IOException ex) {

					logger.error("Exception while processing request", ex);
				}
			}

		} catch (Throwable t) {

			logger.error("Exception while processing request", t);
		}
	}

	@Override
	protected void doPost(final HttpServletRequest request, final HttpServletResponse response) throws ServletException {

		initRequest(request, response);

		SecurityContext securityContext = null;

		setCustomResponseHeaders(response);

		try (final Tx tx = StructrApp.getInstance().tx()) {

			if (!ServletFileUpload.isMultipartContent(request)) {
				response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				response.getOutputStream().write("ERROR (400): Request does not contain multipart content.\n".getBytes("UTF-8"));
				return;
			}

			try {

				securityContext = getConfig().getAuthenticator().initializeAndExamineRequest(request, response);

			} catch (AuthenticationException ae) {

				response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
				response.getOutputStream().write("ERROR (401): Invalid user or password.\n".getBytes("UTF-8"));
				return;
			}

			if (securityContext.getUser(false) == null && !Settings.DeploymentAllowAnonymousUploads.getValue()) {
				response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
				response.getOutputStream().write("ERROR (401): Anonymous uploads forbidden.\n".getBytes("UTF-8"));
				return;
			}

			tx.success();

		} catch (Throwable t) {

			logger.error("Exception while processing request", t);
			return;
		}

		String redirectUrl = null;

		try {

			// Ensure access mode is frontend
			securityContext.setAccessMode(AccessMode.Frontend);

			request.setCharacterEncoding("UTF-8");

			// Important: Set character encoding before calling response.getWriter() !!, see Servlet Spec 5.4
			response.setCharacterEncoding("UTF-8");

			// don't continue on redirects
			if (response.getStatus() == 302) {
				return;
			}

			uploader.setFileSizeMax(MEGABYTE * Settings.DeploymentMaxFileSize.getValue());
			uploader.setSizeMax(MEGABYTE * Settings.DeploymentMaxRequestSize.getValue());

			response.setContentType("text/html");

			final FileItemIterator fileItemsIterator   = uploader.getItemIterator(request);
			final Map<String, Object> params           = new HashMap<>();
			final String directoryPath                 = "/tmp/" + UUID.randomUUID();
			final String filePath                      = directoryPath + ".zip";
			final File file                            = new File(filePath);

			String downloadUrl = null;
			String fileName    = null;

			while (fileItemsIterator.hasNext()) {

				final FileItemStream item = fileItemsIterator.next();

				if (item.isFormField()) {

					final String fieldName = item.getFieldName();
					final String fieldValue = IOUtils.toString(item.openStream(), "UTF-8");

					if (DOWNLOAD_URL_PARAMETER.equals(fieldName)) {

						downloadUrl = StringUtils.trim(fieldValue);
						params.put(fieldName, fieldValue);

					} else if (REDIRECT_URL_PARAMETER.equals(fieldName)) {

						redirectUrl = fieldValue;
						params.put(fieldName, fieldValue);
					}

				} else {

					try (final InputStream is = item.openStream()) {

						Files.write(file.toPath(), IOUtils.toByteArray(is));
						fileName = item.getName();
					}
				}
			}

			if (StringUtils.isNotBlank(downloadUrl)) {

				try {

					HttpHelper.streamURLToFile(downloadUrl, file);
					fileName = PathHelper.getName(downloadUrl);

				} catch (final Throwable t) {

					final String message = "ERROR (400): Unable to download from URL.\n" + t.getMessage() + "\n";

					response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
					response.getOutputStream().write(message.getBytes("UTF-8"));

					return;
				}

			} else {

				response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				response.getOutputStream().write("ERROR (400): No download URL given\n".getBytes("UTF-8"));

				return;
			}

			if (file.exists() && file.length() > 0L) {

				unzip(file, directoryPath);

				DeployCommand deployCommand = StructrApp.getInstance(securityContext).command(DeployCommand.class);

				final Map<String, Object> attributes = new HashMap<>();

				attributes.put("mode", "import");
				attributes.put("source", directoryPath  + "/" + StringUtils.substringBeforeLast(fileName, "."));

				deployCommand.execute(attributes);

				file.delete();
				File dir = new File(directoryPath);
				dir.delete();
			}

			// send redirect to allow form-based file upload without JavaScript..
			if (StringUtils.isNotBlank(redirectUrl)) {

				response.sendRedirect(redirectUrl);
			}

		} catch (Exception t) {

			logger.error("Exception while processing request", t);

			// send redirect to allow form-based file upload without JavaScript..
			if (StringUtils.isNotBlank(redirectUrl)) {

				try {
					response.sendRedirect(redirectUrl);
				} catch (IOException ex) {
					logger.error("Unable to redirect to " + redirectUrl);
				}

			} else {

				UiAuthenticator.writeInternalServerError(response);
			}
		}
	}

	/**
	 * Zip given directory to file.
	 *
	 * @param sourceDirectoryPath
	 * @throws IOException
	 * @return file
	 */
	private File zip(final String sourceDirectoryPath) throws IOException {

		final String zipFilePath   = StringUtils.stripEnd(sourceDirectoryPath, "/").concat(".zip");

		final ZipFile zipFile = new ZipFile(zipFilePath);
		zipFile.addFolder(new File(sourceDirectoryPath));

		return zipFile.getFile();
	}

	/**
	 * Unzip given file to given output directory.
	 *
	 * @param file
	 * @param outputDir
	 * @throws IOException
	 */
	private void unzip(final File file, final String outputDir) throws IOException {
		new ZipFile(file).extractAll(outputDir);
	}


	/**
	 * Initalize request.
	 *
	 * @param request
	 * @param response
	 * @throws ServletException
	 */
	private void initRequest(final HttpServletRequest request, final HttpServletResponse response) throws ServletException {

		try {

			assertInitialized();

		} catch (FrameworkException fex) {

			try {
				response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
				response.getOutputStream().write(fex.getMessage().getBytes("UTF-8"));

			} catch (IOException ioex) {

				logger.warn("Unable to send response", ioex);
			}

		}
	}
}

