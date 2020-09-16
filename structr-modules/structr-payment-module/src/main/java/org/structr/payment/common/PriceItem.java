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
import java.util.LinkedHashMap;
import java.util.Map;
import org.structr.api.graph.Cardinality;
import org.structr.api.schema.JsonObjectType;
import org.structr.api.schema.JsonSchema;
import org.structr.common.PropertyView;
import org.structr.core.graph.NodeInterface;
import org.structr.schema.SchemaService;

public interface PriceItem extends NodeInterface {

	static class Impl { static {

		final JsonSchema schema      = SchemaService.getDynamicSchema();
		final JsonObjectType type    = schema.addType("PriceItem");
		final JsonObjectType product = (JsonObjectType)schema.getType("ProductItem");

		// StripeCheckoutSession
		type.setImplements(URI.create("https://structr.org/v1.1/definitions/PriceItem"));
		type.setCategory("payment");

		// properties
		type.addStringProperty("currency",    PropertyView.Public, PropertyView.Ui);
		type.addIntegerProperty("priceCents", PropertyView.Public, PropertyView.Ui);

		type.addPropertyGetter("currency",     String.class);
		type.addPropertyGetter("priceCents",   Integer.TYPE);
		type.addPropertyGetter("productItem",  ProductItem.class);

		// relationship
		type.relate(product, "product", Cardinality.ManyToOne, "priceItems", "productItem");
	}}

	String getCurrency();
	int getPriceCents();
	ProductItem getProductItem();

	default Map<String, Object> toMap() {

		final Map<String, Object> map = new LinkedHashMap<>();

		map.put("unit_amount",  getPriceCents());

		if (getCurrency() != null) {
			map.put("currency", getCurrency());
		}

		if (getProductItem() != null) {
			map.put("product_data", getProductItem().toMap());
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