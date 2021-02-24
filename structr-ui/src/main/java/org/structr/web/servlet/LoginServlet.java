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


import java.io.IOException;
import java.net.HttpCookie;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.structr.common.SecurityContext;
import org.structr.common.error.FrameworkException;
import org.structr.core.app.StructrApp;
import org.structr.core.auth.Authenticator;
import org.structr.core.graph.Tx;
import org.structr.rest.RestMethodResult;
import org.structr.rest.service.HttpServiceServlet;
import org.structr.rest.servlet.AbstractDataServlet;
import org.structr.web.auth.UiAuthenticator;
import org.structr.web.entity.dom.Page;
import org.structr.web.resource.LoginResource;

/**
 * Simple login servlet, acts as a bridge for form-base HTTP login.
 */
public class LoginServlet extends AbstractDataServlet implements HttpServiceServlet {

	private static final Logger logger = LoggerFactory.getLogger(LoginServlet.class.getName());

	public LoginServlet() {
	}

	@Override
	public String getModuleName() {
		return "ui";
	}

	@Override
	protected void doPost(final HttpServletRequest request, final HttpServletResponse response) throws ServletException {

		try {

			assertInitialized();

		} catch (FrameworkException fex) {

			try {
				response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
				response.getOutputStream().write(fex.getMessage().getBytes("UTF-8"));

			} catch (IOException ioex) {

				logger.warn("Unable to send response", ioex);
			}

			return;
		}

		final SecurityContext securityContext;
		final Authenticator authenticator;

		setCustomResponseHeaders(response);

		try {

			// first thing to do!
			request.setCharacterEncoding("UTF-8");
			response.setCharacterEncoding("UTF-8");
			response.setContentType("text/html; charset=utf-8");

			// isolate request authentication in a transaction
			try (final Tx tx = StructrApp.getInstance().tx()) {
				authenticator = config.getAuthenticator();
				securityContext = authenticator.initializeAndExamineRequest(request, response);
				tx.success();
			}

			if (securityContext != null) {

				final LoginResource loginResource = getLoginResource();

				if (loginResource.checkAndConfigure(getUriPart(), securityContext, request)) {

					final Map<String, Object> properties = new LinkedHashMap<>();

					for (final Entry<String, String[]> entry : request.getParameterMap().entrySet()) {

						final String key = entry.getKey();
						final String[] values = entry.getValue();

						if (values.length > 0) {

							properties.put(key, values[0]);
						}
					}

					final RestMethodResult result = loginResource.doPost(properties);

					// send HTTP headers and redirect
					for (final Entry<String, String> entry : result.getHeaders().entrySet()) {

						response.addHeader(entry.getKey(), entry.getValue());
					}

					try (final Tx tx = StructrApp.getInstance(securityContext).tx()) {

						switch (result.getResponseCode()) {

							case HttpServletResponse.SC_OK:

								// redirect to requested target page or /
								response.sendRedirect(coalesce(HtmlServlet.filterMaliciousRedirects(request.getParameter(HtmlServlet.TARGET_PAGE_KEY)), "/"));
								break;

							default:
								response.sendRedirect(getRedirectPage(request, result.getResponseCode()));
								break;
						}

						tx.success();
					}
				}
			}

		} catch (FrameworkException fex) {

			logger.error("Exception while processing request: {}", fex.getMessage());
			UiAuthenticator.writeFrameworkException(response, fex);

		} catch (IOException ioex) {

			logger.error("Exception while processing request: {}", ioex.getMessage());
			UiAuthenticator.writeInternalServerError(response);
		}
	}

	protected String getUriPart() {
		return "login";
	}

	protected LoginResource getLoginResource() {
		return new LoginResource();
	}

	// ----- private methods -----
	private String getRedirectPage(final HttpServletRequest request, final Integer statusCode) throws FrameworkException {

		final Page errorPage = StructrApp.getInstance().nodeQuery(Page.class).and(StructrApp.key(Page.class, "showOnErrorCodes"), statusCode.toString(), false).getFirst();
		if (errorPage != null && HtmlServlet.isVisibleForSite(request, errorPage)) {

			final String path = errorPage.getPagePath();
			if (path != null) {

				return path + "?status=" + statusCode.toString();
			}

			return "/" + errorPage.getName() + "?status=" + statusCode.toString();
		}

		return "/";
	}
}
