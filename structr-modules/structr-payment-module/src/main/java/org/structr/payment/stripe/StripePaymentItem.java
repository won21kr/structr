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
package org.structr.payment.stripe;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.structr.api.graph.Cardinality;
import org.structr.api.schema.JsonObjectType;
import org.structr.api.schema.JsonSchema;
import org.structr.core.graph.NodeInterface;
import org.structr.payment.common.PriceItem;
import org.structr.schema.SchemaService;

public interface StripePaymentItem extends NodeInterface {

	static class Impl { static {

		final JsonSchema schema    = SchemaService.getDynamicSchema();
		final JsonObjectType type  = schema.addType("StripePaymentItem");
		final JsonObjectType price = (JsonObjectType)schema.getType("PriceItem");

		// StripeCheckoutSession
		type.setImplements(URI.create("https://structr.org/v1.1/definitions/StripePaymentItem"));
		type.setCategory("payment");

		// properties
		type.addIntegerProperty("quantity");

		type.addPropertyGetter("quantity", Integer.TYPE);
		type.addPropertyGetter("priceItem", PriceItem.class);

		// relationship
		type.relate(price, "price", Cardinality.ManyToOne, "paymentItems", "priceItem");
	}}

	int getQuantity();
	PriceItem getPriceItem();

	default Map<String, Object> toMap() {

		final Map<String, Object> map = new LinkedHashMap<>();

		map.put("quantity", getQuantity());

		if (getPriceItem() != null) {
			map.put("price_data", getPriceItem().toMap());
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