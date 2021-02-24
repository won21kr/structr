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
package org.structr.schema.export;

import java.util.Map;
import org.structr.common.SecurityContext;
import org.structr.common.error.FrameworkException;
import org.structr.core.app.App;
import org.structr.core.entity.AbstractSchemaNode;
import org.structr.core.entity.SchemaNode;
import org.structr.core.entity.SchemaProperty;
import org.structr.core.property.PropertyMap;
import org.structr.schema.SchemaHelper.Type;
import org.structr.api.schema.JsonSchema;
import org.structr.api.schema.JsonScriptProperty;

/**
 *
 *
 */
public class StructrScriptProperty extends StructrPropertyDefinition implements JsonScriptProperty {

	protected String contentType = null;
	protected String source      = null;

	public StructrScriptProperty(final StructrTypeDefinition parent, final String name) {

		super(parent, name);
	}

	@Override
	public JsonScriptProperty setSource(final String source) {

		this.source = source;
		return this;
	}

	@Override
	public String getType() {
		return "script";
	}

	@Override
	public String getSource() {
		return source;
	}

	@Override
	public JsonScriptProperty setContentType(String contentType) {

		this.contentType = contentType;
		return this;
	}

	@Override
	public String getContentType() {
		return contentType;
	}

	@Override
	Map<String, Object> serialize() {

		final Map<String, Object> map = super.serialize();

		if (source != null) {
			map.put(JsonSchema.KEY_SOURCE, source);
		}

		if (contentType != null) {
			map.put(JsonSchema.KEY_CONTENT_TYPE, contentType);
		}

		return map;
	}

	@Override
	void deserialize(final Map<String, Object> source) {

		super.deserialize(source);

		final Object sourceValue = source.get(JsonSchema.KEY_SOURCE);
		if (sourceValue != null) {

			if (sourceValue instanceof String) {

				this.source = (String)sourceValue;

			} else {

				throw new IllegalStateException("Invalid source for property " + name + ", expected string.");
			}

		} else {

			throw new IllegalStateException("Missing source value for property " + name);
		}

		final Object contentTypeValue = source.get(JsonSchema.KEY_CONTENT_TYPE);
		if (contentTypeValue != null) {

			if (contentTypeValue instanceof String) {

				this.contentType = (String)contentTypeValue;

			} else {

				throw new IllegalStateException("Invalid contentType for property " + name + ", expected string.");
			}
		}
	}

	@Override
	void deserialize(final Map<String, SchemaNode> schemaNodes, final SchemaProperty property) {

		super.deserialize(schemaNodes, property);

		setContentType(property.getSourceContentType());
		setSource(property.getFormat());

	}

	@Override
	SchemaProperty createDatabaseSchema(final App app, final AbstractSchemaNode schemaNode) throws FrameworkException {

		final SchemaProperty property = super.createDatabaseSchema(app, schemaNode);
		final PropertyMap properties  = new PropertyMap();
		final String contentType      = getContentType();

		if (contentType != null) {

			switch (contentType) {

				case "application/x-structr-javascript":
				case "application/x-structr-script":
					properties.put(SchemaProperty.propertyType, Type.Function.name());
					break;

				case "application/x-cypher":
					properties.put(SchemaProperty.propertyType, Type.Cypher.name());

			}

		} else {

			// default
			properties.put(SchemaProperty.propertyType, Type.Function.name());
		}

		properties.put(SchemaProperty.format, source);

		// set properties in bulk
		property.setProperties(SecurityContext.getSuperUserInstance(), properties);

		return property;
	}
}
