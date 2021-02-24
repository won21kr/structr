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
package org.structr.web.resource;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.structr.api.search.SortOrder;
import org.structr.api.util.ResultStream;
import org.structr.common.MailHelper;
import org.structr.common.SecurityContext;
import org.structr.common.error.FrameworkException;
import org.structr.core.app.Query;
import org.structr.core.app.StructrApp;
import org.structr.core.entity.MailTemplate;
import org.structr.core.entity.Principal;
import org.structr.core.property.PropertyKey;
import org.structr.core.property.PropertyMap;
import org.structr.rest.RestMethodResult;
import org.structr.rest.auth.AuthHelper;
import org.structr.rest.exception.NotAllowedException;
import org.structr.rest.resource.Resource;
import org.structr.schema.action.ActionContext;
import org.structr.web.entity.User;
import org.structr.web.servlet.HtmlServlet;

/**
 * A resource to reset a user's password
 *
 *
 */
public class ResetPasswordResource extends Resource {

	private static final Logger logger = LoggerFactory.getLogger(ResetPasswordResource.class.getName());

	private enum TemplateKey {
		RESET_PASSWORD_SENDER_NAME,
		RESET_PASSWORD_SENDER_ADDRESS,
		RESET_PASSWORD_SUBJECT,
		RESET_PASSWORD_TEXT_BODY,
		RESET_PASSWORD_HTML_BODY,
		RESET_PASSWORD_BASE_URL,
		RESET_PASSWORD_TARGET_PAGE,
		RESET_PASSWORD_ERROR_PAGE,
		RESET_PASSWORD_PAGE,
		RESET_PASSWORD_CONFIRM_KEY_KEY,
		RESET_PASSWORD_TARGET_PAGE_KEY,
		RESET_PASSWORD_ERROR_PAGE_KEY
	}

	@Override
	public boolean checkAndConfigure(String part, SecurityContext securityContext, HttpServletRequest request) {

		this.securityContext = securityContext;

		return (getUriPart().equals(part));
	}

	@Override
	public ResultStream doGet(final SortOrder sortOrder, int pageSize, int page) throws FrameworkException {
		throw new NotAllowedException("GET not allowed on " + getResourceSignature());
	}

	@Override
	public RestMethodResult doPut(Map<String, Object> propertySet) throws FrameworkException {
		throw new NotAllowedException("PUT not allowed on " + getResourceSignature());
	}

	@Override
	public RestMethodResult doPost(Map<String, Object> propertySet) throws FrameworkException {

		if (propertySet.containsKey("eMail")) {

			String emailString  = (String) propertySet.get("eMail");

			if (StringUtils.isEmpty(emailString)) {
				return new RestMethodResult(HttpServletResponse.SC_BAD_REQUEST);
			}

			// cleanup user input
			emailString = emailString.trim().toLowerCase();

			final PropertyKey<String> confirmationKey = StructrApp.key(User.class, "confirmationKey");
			final PropertyKey<String> eMail           = StructrApp.key(User.class, "eMail");
			final String localeString                 = (String) propertySet.get("locale");
			final String confKey                      = AuthHelper.getConfirmationKey();
			final Principal user                      = StructrApp.getInstance().nodeQuery(User.class).and(eMail, emailString).getFirst();

			if (user != null) {

				// update confirmation key
				user.setProperties(SecurityContext.getSuperUserInstance(), new PropertyMap(confirmationKey, confKey));

				if (!sendResetPasswordLink(user, propertySet, localeString, confKey)) {

					// return 400 Bad request
					return new RestMethodResult(HttpServletResponse.SC_BAD_REQUEST);

				}

				// return 200 OK
				return new RestMethodResult(HttpServletResponse.SC_OK);

			} else {

				// We only handle existing users but we don't want to disclose if this e-mail address exists,
				// so we're failing silently here
				return new RestMethodResult(HttpServletResponse.SC_OK);
			}

		} else {

			// return 400 Bad request
			return new RestMethodResult(HttpServletResponse.SC_BAD_REQUEST);
		}
	}

	@Override
	public RestMethodResult doOptions() throws FrameworkException {
		throw new NotAllowedException("OPTIONS not allowed on " + getResourceSignature());
	}

	@Override
	public Resource tryCombineWith(Resource next) throws FrameworkException {

		return null;
	}

	private boolean sendResetPasswordLink(final Principal user, final Map<String, Object> propertySetFromUserPOST, final String localeString, final String confKey) {

		final Map<String, String> replacementMap = new HashMap();

		// Populate the replacement map with all POSTed values
		// WARNING! This is unchecked user input!!
		populateReplacementMap(replacementMap, propertySetFromUserPOST);

		final String userEmail = user.getProperty("eMail");

		replacementMap.put(toPlaceholder("eMail"), userEmail);
		replacementMap.put(toPlaceholder("link"),
			getTemplateText(TemplateKey.RESET_PASSWORD_BASE_URL, ActionContext.getBaseUrl(securityContext.getRequest()), localeString, confKey)
			      + getTemplateText(TemplateKey.RESET_PASSWORD_PAGE, HtmlServlet.RESET_PASSWORD_PAGE, localeString, confKey)
			+ "?" + getTemplateText(TemplateKey.RESET_PASSWORD_CONFIRM_KEY_KEY, HtmlServlet.CONFIRM_KEY_KEY, localeString, confKey) + "=" + confKey
			+ "&" + getTemplateText(TemplateKey.RESET_PASSWORD_TARGET_PAGE_KEY, HtmlServlet.TARGET_PAGE_KEY, localeString, confKey) + "=" + getTemplateText(TemplateKey.RESET_PASSWORD_TARGET_PAGE, HtmlServlet.RESET_PASSWORD_PAGE, localeString, confKey)
		);

		String textMailTemplate = getTemplateText(TemplateKey.RESET_PASSWORD_TEXT_BODY, "Go to ${link} to reset your password.", localeString, confKey);
		String htmlMailTemplate = getTemplateText(TemplateKey.RESET_PASSWORD_HTML_BODY, "<div>Click <a href='${link}'>here</a> to reset your password.</div>", localeString, confKey);
		String textMailContent  = MailHelper.replacePlaceHoldersInTemplate(textMailTemplate, replacementMap);
		String htmlMailContent  = MailHelper.replacePlaceHoldersInTemplate(htmlMailTemplate, replacementMap);

		try {

			MailHelper.sendHtmlMail(
				getTemplateText(TemplateKey.RESET_PASSWORD_SENDER_ADDRESS, "structr-mail-daemon@localhost", localeString, confKey),
				getTemplateText(TemplateKey.RESET_PASSWORD_SENDER_NAME, "Structr Mail Daemon", localeString, confKey),
				userEmail, "", null, null, null,
				getTemplateText(TemplateKey.RESET_PASSWORD_SUBJECT, "Request to reset your Structr password", localeString, confKey),
				htmlMailContent, textMailContent);

		} catch (Exception e) {

			logger.error("Unable to send reset password e-mail", e);
			return false;
		}

		return true;

	}

	private String getTemplateText(final TemplateKey key, final String defaultValue, final String localeString, final String confKey) {

		try {

			final Query<MailTemplate> query = StructrApp.getInstance().nodeQuery(MailTemplate.class).andName(key.name());

			if (localeString != null) {
				query.and("locale", localeString);
			}

			MailTemplate template = query.getFirst();
			if (template != null) {

				final String text = template.getProperty("text");
				return text != null ? text : defaultValue;

			} else {

				return defaultValue;

			}

		} catch (FrameworkException ex) {

			LoggerFactory.getLogger(ResetPasswordResource.class.getName()).warn("Could not get mail template for key " + key, ex);

		}

		return null;
	}

	private static void populateReplacementMap(final Map<String, String> replacementMap, final Map<String, Object> props) {

		for (Entry<String, Object> entry : props.entrySet()) {

			replacementMap.put(toPlaceholder(entry.getKey()), entry.getValue().toString());
		}
	}

	private static String toPlaceholder(final String key) {

		return "${".concat(key).concat("}");
	}

	@Override
	public Class getEntityClass() {

		return null;
	}

	@Override
	public String getUriPart() {

		return "reset-password";
	}

	@Override
	public String getResourceSignature() {

		return "_resetPassword";
	}

	@Override
	public boolean isCollectionResource() {

		return false;
	}
}
