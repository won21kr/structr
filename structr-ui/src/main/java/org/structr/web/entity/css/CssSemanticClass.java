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
package org.structr.web.entity.css;

import java.net.URI;
import org.structr.api.graph.Cardinality;
import org.structr.schema.SchemaService;
import org.structr.api.schema.JsonObjectType;
import org.structr.api.schema.JsonSchema;
import org.structr.common.PropertyView;
import org.structr.core.graph.NodeInterface;

public interface CssSemanticClass extends NodeInterface {

	static class Impl { static {

		final JsonSchema schema            = SchemaService.getDynamicSchema();
		final JsonObjectType semanticClass = schema.addType("CssSemanticClass");
		final JsonObjectType selector      = schema.addType("CssSelector");

		semanticClass.setImplements(URI.create("https://structr.org/v1.1/definitions/CssSemanticClass"));
		semanticClass.setCategory("html");

		semanticClass.relate(selector, "MAPS_TO", Cardinality.ManyToMany, "semanticClasses", "selectors");

		semanticClass.addViewProperty(PropertyView.Ui, "selectors");
	}}
}