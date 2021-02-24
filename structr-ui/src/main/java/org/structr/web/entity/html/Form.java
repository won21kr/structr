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
package org.structr.web.entity.html;

import java.net.URI;
import org.structr.common.PropertyView;
import org.structr.schema.SchemaService;
import org.structr.api.schema.JsonObjectType;
import org.structr.api.schema.JsonSchema;
import org.structr.web.entity.dom.DOMElement;

public interface Form extends DOMElement {

	static class Impl { static {

		final JsonSchema schema   = SchemaService.getDynamicSchema();
		final JsonObjectType type = schema.addType("Form");

		type.setImplements(URI.create("https://structr.org/v1.1/definitions/Form"));
		type.setExtends(URI.create("#/definitions/DOMElement"));
		type.setCategory("html");

		type.addStringProperty("_html_accept-charset", PropertyView.Html);
		type.addStringProperty("_html_action",         PropertyView.Html);
		type.addStringProperty("_html_autocomplete",   PropertyView.Html);
		type.addStringProperty("_html_enctype",        PropertyView.Html);
		type.addStringProperty("_html_method",         PropertyView.Html);
		type.addStringProperty("_html_name",           PropertyView.Html);
		type.addStringProperty("_html_novalidate",     PropertyView.Html);
		type.addStringProperty("_html_target",         PropertyView.Html);

		type.overrideMethod("getHtmlAttributes", false, DOMElement.GET_HTML_ATTRIBUTES_CALL);
	}}
}
