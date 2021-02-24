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
package org.structr.schema.openapi.schema;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.structr.core.app.StructrApp;
import org.structr.core.entity.AbstractNode;
import org.structr.core.property.PropertyKey;
import org.structr.schema.ConfigurationProvider;
import org.structr.schema.export.StructrTypeDefinition;

public class OpenAPIStructrTypeSchemaOutput extends TreeMap<String, Object> {

	public OpenAPIStructrTypeSchemaOutput(final StructrTypeDefinition<?> type, final String viewName, final int level) {

		if (level > 3) {
			return;
		}

		final Map<String, Object> properties = new LinkedHashMap<>();
		final String typeName                = type.getName();

		put("type",        "object");
		put("description", typeName);
		put("properties",  properties);

		type.visitProperties(key -> {

			properties.put(key.jsonName(), key.describeOpenAPIOutputType(typeName, viewName, level));

		}, viewName);
	}

	public OpenAPIStructrTypeSchemaOutput(final Class type, final String viewName, final int level) {

		if (level > 3) {
			return;
		}

		final Map<String, Object> properties = new LinkedHashMap<>();
		final String typeName                = type.getSimpleName();

		put("type",        "object");
		put("description", typeName);
		put("properties",  properties);

		final ConfigurationProvider config = StructrApp.getConfiguration();
		final Set<PropertyKey> keys        = config.getPropertySet(type, viewName);

		if (keys != null && !keys.isEmpty()) {

			for (PropertyKey key : keys) {

				properties.put(key.jsonName(), key.describeOpenAPIOutputType(typeName, viewName, level));
			}

		} else {

			// default properties
			List.of(AbstractNode.id, AbstractNode.type, AbstractNode.name).stream().forEach(key -> {

				properties.put(key.jsonName(), key.describeOpenAPIOutputType(typeName, viewName, level));
			});
		}
	}
}
