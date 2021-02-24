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
package org.structr.web.entity;

import java.net.URI;
import org.structr.common.ConstantBooleanTrue;
import org.structr.common.PropertyView;
import org.structr.core.graph.NodeInterface;
import org.structr.schema.SchemaService;
import org.structr.api.schema.JsonObjectType;
import org.structr.api.schema.JsonSchema;

/**
 * Base class for all content items.
 */
public interface ContentItem extends NodeInterface {

	static class Impl { static {

		final JsonSchema schema   = SchemaService.getDynamicSchema();
		final JsonObjectType type = schema.addType("ContentItem");

		type.setImplements(URI.create("https://structr.org/v1.1/definitions/ContentItem"));
		type.setCategory("ui");

		type.addBooleanProperty("isContentItem", PropertyView.Public, PropertyView.Ui).setReadOnly(true).addTransformer(ConstantBooleanTrue.class.getName());
		type.addIntegerProperty("position", PropertyView.Public, PropertyView.Ui).setIndexed(true);
		type.addEnumProperty("kind", PropertyView.Public, PropertyView.Ui).setEnums("heading", "paragraph");

		type.addViewProperty(PropertyView.Public, "containers");
		type.addViewProperty(PropertyView.Public, "name");
		type.addViewProperty(PropertyView.Public, "owner");

		type.addViewProperty(PropertyView.Ui, "containers");
		type.addViewProperty(PropertyView.Ui, "name");
		type.addViewProperty(PropertyView.Ui, "owner");
	}}

	/*
	public static final Property<List<ContentContainer>>   containers    = new StartNodes<>("containers", ContainerContentItems.class);
	public static final Property<Boolean>                  isContentItem = new ConstantBooleanProperty("isContentItem", true);

	public static final View publicView = new View(Folder.class, PropertyView.Public, id, type, name, owner, containers, isContentItem);
	public static final View uiView     = new View(Folder.class, PropertyView.Ui, id, type, name, owner, containers, isContentItem);
	*/

}
