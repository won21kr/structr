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
package org.structr.core.function;

import org.structr.common.error.ArgumentCountException;
import org.structr.common.error.ArgumentNullException;
import org.structr.common.error.FrameworkException;
import org.structr.core.entity.Group;
import org.structr.core.entity.Principal;
import org.structr.schema.action.ActionContext;

public class AddToGroupFunction extends AdvancedScriptingFunction {

	public static final String ERROR_MESSAGE    = "Usage: ${add_to_group(group, user)}";
	public static final String ERROR_MESSAGE_JS = "Usage: ${{Structr.addToGroup(group, user);}}";

	@Override
	public String getName() {
		return "add_to_group";
	}

	@Override
	public String getSignature() {
		return "group, user";
	}

	@Override
	public Object apply(final ActionContext ctx, final Object caller, final Object[] sources) throws FrameworkException {

		try {

			assertArrayHasLengthAndAllElementsNotNull(sources, 2);

			if (!(sources[0] instanceof Group)) {

				logger.warn("Error: first argument is not a Group. Parameters: {}", getParametersAsString(sources));
				return "Error: first argument is not a Group.";
			}

			if (!(sources[1] instanceof Principal)) {

				logger.warn("Error: second argument is not a Principal. Parameters: {}", getParametersAsString(sources));
				return "Error: second argument is not a Principal.";
			}

			final Group group    = (Group)sources[0];
			final Principal user = (Principal)sources[1];

			group.addMember(ctx.getSecurityContext(), user);

		} catch (ArgumentNullException pe) {

			// silently ignore null arguments

		} catch (ArgumentCountException pe) {

			logParameterError(caller, sources, pe.getMessage(), ctx.isJavaScriptContext());
			return usage(ctx.isJavaScriptContext());
		}

		return "";
	}

	@Override
	public String usage(boolean inJavaScriptContext) {
		return (inJavaScriptContext ? ERROR_MESSAGE_JS : ERROR_MESSAGE);
	}

	@Override
	public String shortDescription() {
		return "Adds a user to a group.";
	}
}
