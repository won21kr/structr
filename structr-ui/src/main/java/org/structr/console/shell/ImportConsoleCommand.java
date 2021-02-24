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
package org.structr.console.shell;

import java.io.IOException;
import java.util.List;
import org.structr.common.SecurityContext;
import org.structr.common.error.FrameworkException;
import org.structr.core.app.StructrApp;
import org.structr.core.entity.Principal;
import org.structr.util.Writable;
import org.structr.web.maintenance.DeployCommand;

/**
 * A console wrapper for DeployCommand, import mode.
 */
public class ImportConsoleCommand extends AdminConsoleCommand {

	static {
		AdminConsoleCommand.registerCommand("import", ImportConsoleCommand.class);
	}

	@Override
	public void run(final SecurityContext securityContext, final List<String> parameters, final Writable writable) throws FrameworkException, IOException {

		final Principal user = securityContext.getUser(false);
		if (user != null && user.isAdmin()) {

			final DeployCommand cmd = StructrApp.getInstance(securityContext).command(DeployCommand.class);

			cmd.setLogBuffer(writable);
			cmd.execute(toMap(
					"mode",   "import",
					"source", getParameter(parameters, 1)
			));

		} else {

			writable.println("You must be admin user to use this command.");
		}
	}

	@Override
	public void commandHelp(final Writable writable) throws IOException {
		writable.println("Imports a Structr application from a directory.");
	}

	@Override
	public void detailHelp(final Writable writable) throws IOException {
		writable.println("import <source>  -  imports an application from the given source directory.");
	}

	@Override
	public boolean requiresEnclosingTransaction() {
		return false;
	}
}
