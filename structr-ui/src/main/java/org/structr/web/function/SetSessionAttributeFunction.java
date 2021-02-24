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
package org.structr.web.function;

import javax.servlet.http.HttpSession;
import org.structr.common.error.ArgumentCountException;
import org.structr.common.error.ArgumentNullException;
import org.structr.common.error.FrameworkException;
import org.structr.schema.action.ActionContext;

public class SetSessionAttributeFunction extends UiAdvancedFunction {

	public static final String ERROR_MESSAGE_SET_SESSION_ATTRIBUTE    = "Usage: ${set_session_attribute(key, value)}. Example: ${set_session_attribute(\"do_no_track\", true)}";
	public static final String ERROR_MESSAGE_SET_SESSION_ATTRIBUTE_JS = "Usage: ${{Structr.set_session_attribute(key, value)}}. Example: ${{Structr.set_session_attribute(\"do_not_track\", true)}}";

	@Override
	public String getName() {
		return "set_session_attribute";
	}

	@Override
	public String getSignature() {
		return "key, value";
	}

	@Override
	public Object apply(final ActionContext ctx, final Object caller, final Object[] sources) throws FrameworkException {

		try {

			assertArrayHasLengthAndAllElementsNotNull(sources, 2);

			final HttpSession session = ctx.getSecurityContext().getSession();

			if (session != null) {
				session.setAttribute(ActionContext.SESSION_ATTRIBUTE_PREFIX.concat(sources[0].toString()), sources[1]);
			} else {
				logger.warn("{}: No session available to set session attribute! (this can happen in onStructrLogin/onStructrLogout)", getReplacement());
			}

			return "";

		} catch (ArgumentNullException pe) {

			// silently ignore null arguments
			return null;

		} catch (ArgumentCountException pe) {

			logParameterError(caller, sources, pe.getMessage(), ctx.isJavaScriptContext());
			return usage(ctx.isJavaScriptContext());
		}
	}

	@Override
	public String usage(boolean inJavaScriptContext) {
		return (inJavaScriptContext ? ERROR_MESSAGE_SET_SESSION_ATTRIBUTE_JS : ERROR_MESSAGE_SET_SESSION_ATTRIBUTE);
	}

	@Override
	public String shortDescription() {
		return "Store a value under the given key in the users session";
	}
}