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
import org.structr.common.PropertyView;
import org.structr.core.graph.NodeInterface;
import org.structr.schema.SchemaService;
import org.structr.api.schema.JsonObjectType;
import org.structr.api.schema.JsonSchema;

/**
 *
 *
 */
public interface Linkable extends NodeInterface {

	static class Impl { static {

		final JsonSchema schema   = SchemaService.getDynamicSchema();
		final JsonObjectType type = schema.addType("Linkable");

		type.setIsInterface();
		type.setImplements(URI.create("https://structr.org/v1.1/definitions/Linkable"));
		type.setCategory("ui");

		type.addBooleanProperty("enableBasicAuth", PropertyView.Ui).setDefaultValue("false").setIndexed(true);
		type.addStringProperty("basicAuthRealm",   PropertyView.Ui);

		// view configuration
		type.addViewProperty(PropertyView.Ui, "linkingElements");
		type.addViewProperty(PropertyView.Ui, "linkingElementsIds");
	}}

	boolean getEnableBasicAuth();
	String getBasicAuthRealm();

	public String getPath();

	/*
	public static final Property<List<LinkSource>> linkingElements = new StartNodes<>("linkingElements", ResourceLink.class, new PropertyNotion(GraphObject.id));
	public static final Property<Boolean> enableBasicAuth          = new BooleanProperty("enableBasicAuth").defaultValue(false).indexed();
	public static final Property<String> basicAuthRealm            = new StringProperty("basicAuthRealm");

	public static final org.structr.common.View uiView = new org.structr.common.View(Linkable.class, PropertyView.Ui, linkingElements, enableBasicAuth, basicAuthRealm);
	*/
}
