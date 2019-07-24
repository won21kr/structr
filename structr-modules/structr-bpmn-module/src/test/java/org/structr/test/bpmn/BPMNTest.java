/**
 * Copyright (C) 2010-2019 Structr GmbH
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
package org.structr.test.bpmn;

import org.structr.api.config.Settings;
import org.structr.common.error.FrameworkException;
import org.structr.core.app.StructrApp;
import org.structr.core.entity.AbstractNode;
import org.structr.core.graph.NodeAttribute;
import org.structr.core.graph.Tx;
import org.structr.test.web.StructrUiTest;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.structr.bpmn.model.BPMNProcessNode;

/**
 */
public class BPMNTest extends StructrUiTest {

	@Test
	public void testBPMN() {

		try (final Tx tx = app.tx()) {

			app.create(BPMNProcessNode.class,
				new NodeAttribute<>(StructrApp.key(BPMNProcessNode.class, "active"), true),
				new NodeAttribute<>(AbstractNode.name, "p1")
			);

			tx.success();

		} catch (FrameworkException fex) {
			fex.printStackTrace();
		}

		// wait for process to do some steps..
		try { Thread.sleep(5000); } catch (Throwable t) {
			t.printStackTrace();
		}
	}

	@BeforeClass(alwaysRun = true)
	@Override
	public void setup() {

		Settings.Services.setValue("NodeService SchemaService BPMNService");

		super.setup();
	}
}

