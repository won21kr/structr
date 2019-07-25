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

import java.net.URI;
import org.structr.api.config.Settings;
import org.structr.core.app.StructrApp;
import org.structr.core.entity.AbstractNode;
import org.structr.core.entity.Principal;
import org.structr.core.entity.Relation;
import org.structr.core.graph.NodeAttribute;
import org.structr.core.graph.Tx;
import org.structr.test.web.StructrUiTest;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.structr.schema.export.StructrSchema;
import org.structr.schema.json.JsonObjectType;
import org.structr.schema.json.JsonSchema;
import org.structr.web.entity.User;

/**
 */
public class BPMNTest extends StructrUiTest {

	@Test
	public void testBPMN() {

		// user setup
		try (final Tx tx = app.tx()) {

			app.create(User.class,
				new NodeAttribute<>(StructrApp.key(Principal.class, "name"), "admin"),
				new NodeAttribute<>(StructrApp.key(Principal.class, "password"), "admin"),
				new NodeAttribute<>(StructrApp.key(Principal.class, "isAdmin"), true)
			);

			tx.success();

		} catch (Throwable t) {
			t.printStackTrace();
		}

		// schema setup
		try (final Tx tx = app.tx()) {

			final JsonSchema schema = StructrSchema.createFromDatabase(app);
			final URI stepId        = URI.create("https://structr.org/v1.1/definitions/BPMNAction");

			// create steps
			final JsonObjectType step1 = schema.addType("Step1");
			final JsonObjectType step2 = schema.addType("Step2");

			// extend ProcessStep
			step1.setExtends(stepId);
			step2.setExtends(stepId);

			// create relationships
			step1.relate(step2, "NEXT", Relation.Cardinality.ManyToMany, "previous", "next");

			step1.addViewProperty("bpmn", "next");
			step2.addViewProperty("bpmn", "next");

			// define actions
			step1.addMethod("action", "log('STEP 1')", "");
			step2.addMethod("action", "log('STEP 2')", "");

			StructrSchema.extendDatabaseSchema(app, schema);

			System.out.println(schema.toString());

			tx.success();

		} catch (Throwable t) {
			t.printStackTrace();
		}

		// instances setup
		try (final Tx tx = app.tx()) {

			app.create(
				StructrApp.getConfiguration().getNodeEntityClass("Step1"),
				new NodeAttribute<>(AbstractNode.name, "Blah1")
			);

			tx.success();

		} catch (Throwable t) {
			t.printStackTrace();
		}

	}

	@BeforeClass(alwaysRun = true)
	@Override
	public void setup() {

		Settings.Services.setValue("NodeService SchemaService HttpService BPMNService");

		super.setup();
	}
}

