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
package org.structr.knowledge.iso25964;

import java.net.URI;
import org.structr.api.graph.Cardinality;
import org.structr.api.schema.JsonObjectType;
import org.structr.api.schema.JsonSchema;
import org.structr.common.PropertyView;
import org.structr.core.graph.NodeInterface;
import org.structr.schema.SchemaService;

/**
 * Class as defined in ISO 25964 data model
 */
public interface ThesaurusArray extends NodeInterface {

	static class Impl { static {

		final JsonSchema schema   = SchemaService.getDynamicSchema();

		final JsonObjectType type    = schema.addType("ThesaurusArray");
		final JsonObjectType label   = schema.addType("NodeLabel");
		final JsonObjectType concept = schema.addType("ThesaurusConcept");

		type.setImplements(URI.create("https://structr.org/v1.1/definitions/ThesaurusArray"));

		type.addStringProperty("identifier", PropertyView.All, PropertyView.Ui).setIndexed(true).setRequired(true);
		type.addBooleanProperty("ordered", PropertyView.All, PropertyView.Ui).setDefaultValue("false").setRequired(true);
		type.addStringArrayProperty("notation", PropertyView.All, PropertyView.Ui);

		type.relate(label,   "hasNodeLabel",     Cardinality.OneToMany, "thesaurusArray", "nodeLabels");
		type.relate(type,    "hasMemberArray",   Cardinality.OneToMany, "superOrdinateArray", "memberArrays");
		type.relate(concept, "hasMemberConcept", Cardinality.ManyToMany, "thesaurusArrays", "memberConcepts");
	}}
}
