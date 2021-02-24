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
package org.structr.schema.compiler;

import org.structr.common.error.ErrorToken;
import org.structr.common.error.FrameworkException;
import org.structr.common.error.InvalidPropertySchemaToken;
import org.structr.core.app.App;
import org.structr.core.app.StructrApp;
import org.structr.core.entity.SchemaNode;
import org.structr.core.entity.SchemaProperty;

/**
 *
 */
public class ExtendNotionPropertyWithUuid implements MigrationHandler {

	@Override
	public void handleMigration(final ErrorToken errorToken) throws FrameworkException {


		final Object messageObject = errorToken.getDetail();
		if (messageObject != null) {

			final String message  = (String)messageObject;

			if (message.startsWith("Invalid notion property expression for property ") && message.endsWith(".")) {

				if (errorToken instanceof InvalidPropertySchemaToken) {

					final App app                          = StructrApp.getInstance();
					final InvalidPropertySchemaToken token = (InvalidPropertySchemaToken)errorToken;
					final String typeName                  = token.getType();
					final String propertyName              = token.getProperty();
					final SchemaNode type = app.nodeQuery(SchemaNode.class).andName(typeName).getFirst();

					if (type != null) {

						final SchemaProperty property = app.nodeQuery(SchemaProperty.class)
							.and(SchemaProperty.schemaNode, type)
							.and(SchemaProperty.name, propertyName).getFirst();

						if (property != null) {

							// load format property
							final String format = property.getProperty(SchemaProperty.format);

							// store corrected version of format property
							property.setProperty(SchemaProperty.format, format + ", id");
						}
					}
				}

			}
		}
	}
}
