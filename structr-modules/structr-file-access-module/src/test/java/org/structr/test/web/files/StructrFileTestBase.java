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
package org.structr.test.web.files;

import java.util.Random;
import org.structr.api.config.Settings;
import org.structr.test.web.StructrUiTest;
import org.testng.annotations.BeforeClass;

/**
 * Base class for all structr UI tests.
 * All tests are executed in superuser context.
 *
 */
public abstract class StructrFileTestBase extends StructrUiTest {

	protected int ftpPort = 8876 + new Random().nextInt(1000);
	protected int sshPort = 8877 + new Random().nextInt(1000);

	@BeforeClass(alwaysRun = true)
	@Override
	public void setup() {

		Settings.Services.setValue("NodeService SchemaService FtpService SSHService");

		Settings.FtpPort.setValue(ftpPort);
		Settings.SshPort.setValue(sshPort);

		super.setup();
	}
}

