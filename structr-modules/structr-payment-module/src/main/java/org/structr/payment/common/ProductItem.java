/*
 * Copyright (C) 2010-2020 Structr GmbH
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
package org.structr.payment.common;

import java.net.URI;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.structr.api.schema.JsonObjectType;
import org.structr.api.schema.JsonSchema;
import org.structr.common.PropertyView;
import org.structr.core.graph.NodeInterface;
import org.structr.schema.SchemaService;

public interface ProductItem extends NodeInterface {

	static class Impl { static {

		final JsonSchema schema      = SchemaService.getDynamicSchema();
		final JsonObjectType type    = schema.addType("ProductItem");

		type.setImplements(URI.create("https://structr.org/v1.1/definitions/ProductItem"));
		type.setCategory("payment");

		// properties
		type.addStringProperty("description", PropertyView.Public, PropertyView.Ui);
		type.addStringArrayProperty("images", PropertyView.Public, PropertyView.Ui);

		type.addPropertyGetter("description", String.class);

		// methods
		type.addMethod("getImages").setReturnType("String[]").setSource("return getProperty(imagesProperty);");
	}}

	String getDescription();
	String[] getImages();

	default Map<String, Object> toMap() {

		final Map<String, Object> map = new LinkedHashMap<>();

		if (getName() != null) {
			map.put("name", getName());
		}

		if (getDescription() != null) {
			map.put("description", getDescription());
		}

		if (getImages() != null) {
			map.put("images", Arrays.asList(getImages()));
		}

		return map;
	}
}

/*
price_data: {
	currency: item.currency || 'EUR',
	product_data: {
		name: item.name,
		description: item.description,
		images: item.images
	},
	unit_amount: item.cents
},
quantity: item.quantity
*/