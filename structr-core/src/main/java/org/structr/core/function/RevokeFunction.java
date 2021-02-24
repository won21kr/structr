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

import java.util.HashSet;
import java.util.Set;
import org.structr.common.Permission;
import org.structr.common.Permissions;
import org.structr.common.error.ArgumentCountException;
import org.structr.common.error.ArgumentNullException;
import org.structr.common.error.FrameworkException;
import org.structr.core.entity.AbstractNode;
import org.structr.core.entity.Principal;
import org.structr.schema.action.ActionContext;

public class RevokeFunction extends AdvancedScriptingFunction {

	public static final String ERROR_MESSAGE_REVOKE    = "Usage: ${revoke(principal, node, permissions)}. Example: ${revoke(me, this, 'write, delete'))}";
	public static final String ERROR_MESSAGE_REVOKE_JS = "Usage: ${{Structr.revoke(principal, node, permissions)}}. Example: ${{Structr.revoke(Structr.('me'), Structr.this, 'write, delete'))}}";

	@Override
	public String getName() {
		return "revoke";
	}

	@Override
	public String getSignature() {
		return "user, node, permissions";
	}

	@Override
	public Object apply(final ActionContext ctx, final Object caller, final Object[] sources) throws FrameworkException {

		try {

			assertArrayHasLengthAndAllElementsNotNull(sources, 3);

			if (sources[0] instanceof Principal) {

				final Principal principal = (Principal)sources[0];

				if (sources[1] instanceof AbstractNode) {

					final AbstractNode node = (AbstractNode)sources[1];

					if (sources[2] instanceof String) {

						final Set<Permission> permissions = new HashSet();
						final String[] parts = ((String)sources[2]).split("[,]+");

						for (final String part : parts) {

							final String trimmedPart = part.trim();
							if (trimmedPart.length() > 0) {

								final Permission permission = Permissions.valueOf(trimmedPart);
								if (permission != null) {

									permissions.add(permission);

								} else {

									logger.warn("Error: unknown permission \"{}\". Parameters: {}", new Object[] { trimmedPart, getParametersAsString(sources) });
									return "Error: unknown permission " + trimmedPart;
								}
							}
						}

						if (permissions.size() > 0) {
							node.revoke(permissions, principal, ctx.getSecurityContext());
						}

						return "";

					} else {

						logger.warn("Error: third argument is not a string. Parameters: {}", getParametersAsString(sources));
						return "Error: third argument is not a string.";
					}

				} else {

					logger.warn("Error: second argument is not a node. Parameters: {}", getParametersAsString(sources));
					return "Error: second argument is not a node.";
				}

			} else {

				logger.warn("Error: first argument is not of type Principal. Parameters: {}", getParametersAsString(sources));
				return "Error: first argument is not of type Principal.";
			}

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
		return (inJavaScriptContext ? ERROR_MESSAGE_REVOKE_JS : ERROR_MESSAGE_REVOKE);
	}

	@Override
	public String shortDescription() {
		return "Revokes the given permissions on the given entity from a user";
	}
}
