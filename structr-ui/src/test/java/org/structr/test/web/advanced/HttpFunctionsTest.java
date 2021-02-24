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
package org.structr.test.web.advanced;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.structr.common.error.FrameworkException;
import org.structr.core.GraphObjectMap;
import org.structr.core.app.StructrApp;
import org.structr.core.entity.Principal;
import org.structr.core.graph.NodeAttribute;
import org.structr.core.graph.Tx;
import org.structr.core.script.Scripting;
import org.structr.schema.action.ActionContext;
import org.structr.test.web.StructrUiTest;
import org.structr.web.entity.User;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.fail;
import org.testng.annotations.Test;

/**
 *
 */
public class HttpFunctionsTest extends StructrUiTest {

	@Test
	public void testHttpFunctions() {

		try (final Tx tx = app.tx()) {

			app.create(User.class,
				new NodeAttribute<>(StructrApp.key(Principal.class, "name"),     "admin"),
				new NodeAttribute<>(StructrApp.key(Principal.class, "password"), "admin"),
				new NodeAttribute<>(StructrApp.key(Principal.class, "isAdmin"),  true)
			);

			tx.success();

		} catch (FrameworkException fex) {

			fex.printStackTrace();
			fail("Unexpected exception");
		}

		// allow all access to group resource
		grant("Group", 16383, true);

		try {

			final ActionContext ctx = new ActionContext(securityContext);
			final Gson gson         = new GsonBuilder().create();

			ctx.addHeader("X-User",     "admin");
			ctx.addHeader("X-Password", "admin");

			// test POST
			final GraphObjectMap postResponse  = (GraphObjectMap)Scripting.evaluate(ctx, null, "${POST('http://localhost:"  + httpPort + "/structr/rest/Group', '{ name: post }')}", "test");

			// extract response headers
			final Map<String, Object> response = postResponse.toMap();
			final Map<String, Object> headers  = (Map)response.get("headers");
			final String location              = (String)headers.get("Location");

			// test PUT
			Scripting.evaluate(ctx, null, "${PUT('" + location + "', '{ name: put }')}", "test");
			final Map<String, Object> putResult = gson.fromJson((String)Scripting.evaluate(ctx, null, "${GET('" + location + "', 'application/json')}", "test"), Map.class);
			assertMapPathValueIs(putResult, "result.name", "put");

			// test PATCH
			Scripting.evaluate(ctx, null, "${PATCH('" + location + "', '{ name: patch }')}", "test");
			final Map<String, Object> patchResult = gson.fromJson((String)Scripting.evaluate(ctx, null, "${GET('" + location + "', 'application/json')}", "test"), Map.class);
			assertMapPathValueIs(patchResult, "result.name", "patch");

		} catch (final FrameworkException fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}

	}

	// static methods
	public static void assertMapPathValueIs(final Map<String, Object> map, final String mapPath, final Object value) {

		final String[] parts = mapPath.split("[\\.]+");
		Object current       = map;

		for (int i=0; i<parts.length; i++) {

			final String part = parts[i];
			if (StringUtils.isNumeric(part)) {

				int index = Integer.valueOf(part);
				if (current instanceof List) {

					final List list = (List)current;
					if (index >= list.size()) {

						// value for nonexisting fields must be null
						assertEquals("Invalid map path result for " + mapPath, value, null);

						// nothing more to check here
						return;

					} else {

						current = list.get(index);
					}
				}

			} else if ("#".equals(part) && current instanceof List) {

				assertEquals("Invalid collection size for " + mapPath, value, ((List)current).size());

				// nothing more to check here
				return;

			} else {

				if (current instanceof Map) {

					current = ((Map)current).get(part);
				}
			}
		}

		// ignore type of value if numerical (GSON defaults to double...)
		if (value instanceof Number && current instanceof Number) {

			assertEquals("Invalid map path result for " + mapPath, ((Number)value).doubleValue(), ((Number)current).doubleValue(), 0.0);

		} else {

			assertEquals("Invalid map path result for " + mapPath, value, current);
		}
	}
}
