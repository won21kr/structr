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

import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.net.RequestOptions.RequestOptionsBuilder;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.structr.api.graph.Cardinality;
import org.structr.api.schema.JsonObjectType;
import org.structr.api.schema.JsonSchema;
import org.structr.api.util.Iterables;
import org.structr.common.PropertyView;
import org.structr.common.SecurityContext;
import org.structr.common.error.FrameworkException;
import org.structr.core.app.App;
import org.structr.core.app.StructrApp;
import org.structr.core.graph.NodeInterface;
import org.structr.core.property.PropertyKey;
import org.structr.core.property.PropertyMap;
import org.structr.schema.SchemaService;

public interface StripeCheckoutSession extends NodeInterface {

	static class Impl { static {

		final JsonSchema schema      = SchemaService.getDynamicSchema();
		final JsonObjectType type    = schema.addType("StripeCheckoutSession");
		final JsonObjectType payment = (JsonObjectType)schema.getType("StripePaymentItem");

		// StripeCheckoutSession
		type.setImplements(URI.create("https://structr.org/v1.1/definitions/StripeCheckoutSession"));
		type.setCategory("payment");

		// properties
		type.addStringProperty("sessionId",               PropertyView.Public, PropertyView.Ui).setIndexed(true);
		type.addStringProperty("paymentIntent",           PropertyView.Public, PropertyView.Ui).setIndexed(true);
		type.addStringProperty("successUrl",              PropertyView.Public, PropertyView.Ui);
		type.addStringProperty("cancelUrl",               PropertyView.Public, PropertyView.Ui);
		type.addStringProperty("mode",                    PropertyView.Public, PropertyView.Ui);
		type.addStringArrayProperty("paymentMethodTypes", PropertyView.Public, PropertyView.Ui);

		type.addPropertyGetter("client",              StripeAPIClient.class);
		type.addPropertyGetter("sessionId",           String.class);
		type.addPropertyGetter("successUrl",          String.class);
		type.addPropertyGetter("cancelUrl",           String.class);
		type.addPropertyGetter("mode",                String.class);
		type.addPropertyGetter("paymentItems",        Iterable.class);

		type.addPropertySetter("sessionId",     String.class);
		type.addPropertySetter("paymentIntent", String.class);

		// relationship
		type.relate(payment, "payment", Cardinality.OneToMany, "checkoutSession", "paymentItems");

		// methods
		type.addMethod("getPaymentMethodTypes").setReturnType("String[]").setSource("return getProperty(paymentMethodTypesProperty);");
	}}

	StripeAPIClient getClient();
	String getSessionId();
	String getSuccessUrl();
	String getCancelUrl();
	String getMode();
	String[] getPaymentMethodTypes();
	Iterable<StripePaymentItem> getPaymentItems();

	void setPaymentIntent(final String paymentIntent) throws FrameworkException;
	void setSessionId(final String sessionId) throws FrameworkException;

	default void initializeFrom(final Session session) throws FrameworkException {

		setPaymentIntent(session.getPaymentIntent());
		setSessionId(session.getId());
	}

	public static StripeCheckoutSession startCheckout(final StripeAPIClient client, final SecurityContext securityContext, final Map<String, Object> parameters) throws FrameworkException {

		try {

			final PropertyMap propertyMap = PropertyMap.inputTypeToJavaType(securityContext, StripeCheckoutSession.class, parameters);
			final App app                 = StructrApp.getInstance(client.getSecurityContext());

			// mandatory settings (internal)
			propertyMap.put(StructrApp.key(StripeCheckoutSession.class, "client"), client);
			propertyMap.put(StructrApp.key(StripeCheckoutSession.class, "mode"), "payment");

			// optional settings
			propertyMap.putIfAbsent(StructrApp.key(StripeCheckoutSession.class,          "successUrl"), "http://localhost/success");
			propertyMap.putIfAbsent(StructrApp.key(StripeCheckoutSession.class,           "cancelUrl"), "http://localhost/cancel");
			propertyMap.putIfAbsent(StructrApp.key(StripeCheckoutSession.class,  "paymentMethodTypes"), new String[] { "card" });

			// create new CheckoutSession object from propertyMap
			final StripeCheckoutSession session = app.create(StripeCheckoutSession.class, propertyMap);

			// use request properties to create a new StripeCheckoutSession
			final PropertyKey<String> secretAPIKeyProperty = StructrApp.key(StripeAPIClient.class, "secretAPIKey");
			final List<StripePaymentItem> paymentItemList  = Iterables.toList((Iterable)propertyMap.get(StructrApp.key(StripeCheckoutSession.class, "paymentItems")));
			final String secretAPIKey                      = client.getProperty(secretAPIKeyProperty);
			final RequestOptions options                   = new RequestOptionsBuilder().setApiKey(secretAPIKey).build();

			final Map<String, Object> params = Map.of(

				"success_url",          session.getSuccessUrl(),
				"cancel_url",           session.getCancelUrl(),
				"payment_method_types", Arrays.asList(session.getPaymentMethodTypes()),
				"mode",                 session.getMode(),
				"line_items",           paymentItemList.stream().map(StripePaymentItem::toMap).collect(Collectors.toList())
			);

			// create corresponding checkout session via Stripe API
			final Session stripeSession = Session.create(params, options);

			// copy data into Structr object
			session.initializeFrom(stripeSession);

			return session;

		} catch (FrameworkException fex) {

			throw fex;

		} catch (Throwable t) {

			final FrameworkException fex = new FrameworkException(422, "Unable to create checkout session");

			fex.initCause(t);

			throw fex;
		}
	}
}

/*
  "id": "cs_test_MRdWr4ItBI1uuBhBKaCq7A7bL2XCJIv0rMFI8IuJe4dlrWsRxav4RqJl",
  "object": "checkout.session",
  "allow_promotion_codes": null,
  "amount_subtotal": null,
  "amount_total": null,
  "billing_address_collection": null,
  "cancel_url": "https://example.com/cancel",
  "client_reference_id": null,
  "currency": null,
  "customer": null,
  "customer_email": null,
  "livemode": false,
  "locale": null,
  "metadata": {},
  "mode": "payment",
  "payment_intent": "pi_1Dg9IE2eZvKYlo2CW2Hks5D6",
  "payment_method_types": [
    "card"
  ],
  "setup_intent": null,
  "shipping": null,
  "shipping_address_collection": null,
  "submit_type": null,
  "subscription": null,
  "success_url": "https://example.com/success",
  "total_details": null,
  "line_items": [
    {
      "price": "price_H5ggYwtDq4fbrJ",
      "quantity": 2
    }
  ]
*/