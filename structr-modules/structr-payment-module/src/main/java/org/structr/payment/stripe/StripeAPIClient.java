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

import com.stripe.exception.StripeException;
import com.stripe.model.WebhookEndpoint;
import com.stripe.net.RequestOptions;
import com.stripe.net.RequestOptions.RequestOptionsBuilder;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.slf4j.LoggerFactory;
import org.structr.api.graph.Cardinality;
import org.structr.api.schema.JsonObjectType;
import org.structr.api.schema.JsonSchema;
import org.structr.common.SecurityContext;
import org.structr.common.error.ErrorBuffer;
import org.structr.common.error.FrameworkException;
import org.structr.core.app.App;
import org.structr.core.app.StructrApp;
import org.structr.core.entity.AbstractNode;
import org.structr.core.graph.ModificationQueue;
import org.structr.core.graph.NodeAttribute;
import org.structr.core.graph.NodeInterface;
import org.structr.core.property.PropertyKey;
import org.structr.schema.SchemaService;

public interface StripeAPIClient extends NodeInterface {

	static class Impl { static {

		final JsonSchema schema       = SchemaService.getDynamicSchema();
		final JsonObjectType type     = schema.addType("StripeAPIClient");
		final JsonObjectType webhook  = (JsonObjectType)schema.getType("StripeWebhook");
		final JsonObjectType sessions = (JsonObjectType)schema.getType("StripeCheckoutSession");

		// StripeAPIClient
		type.setImplements(URI.create("https://structr.org/v1.1/definitions/StripeAPIClient"));
		type.setCategory("payment");

		type.addStringProperty("publicAPIKey");
		type.addStringProperty("secretAPIKey");
		type.addBooleanProperty("createWebhook");

		type.addStringProperty("webhookSecret");
		type.addStringProperty("webhookId");

		type.addPropertyGetter("webhookId", String.class);
		type.addPropertySetter("webhookId", String.class);

		type.addPropertyGetter("webhookSecret", String.class);
		type.addPropertySetter("webhookSecret", String.class);

		type.addPropertySetter("createWebhook", Boolean.TYPE);
		type.addPropertyGetter("webhook", StripeWebhook.class);
		type.addMethod("setWebhook")
			.setSource("setProperty(webhookProperty, (org.structr.dynamic.StripeWebhook)webhook);")
			.addException(FrameworkException.class.getName())
			.addParameter("webhook", "org.structr.payment.stripe.StripeWebhook");

		// relationships
		type.relate(webhook,  "webhook", Cardinality.OneToOne,  "client", "webhook");
		type.relate(sessions, "session", Cardinality.OneToMany, "client", "sessions");

		// methods
		type.overrideMethod("onCreation",      true, StripeAPIClient.class.getName() + ".onCreation(this, arg0, arg1);");
		type.overrideMethod("onModification",  true, StripeAPIClient.class.getName() + ".onModification(this, arg0, arg1, arg2);");

		// API methods
		type.overrideMethod("startCheckout",  false, "return " + StripeAPIClient.class.getName() + ".startCheckout(this, arg0, arg1);")
			.setDoExport(true);
	}}

	StripeWebhook getWebhook();
	String getWebhookId();
	String getWebhookSecret();

	void setWebhook(final StripeWebhook webhook) throws FrameworkException;
	void setWebhookId(final String webhookId) throws FrameworkException;
	void setWebhookSecret(final String webhookSecret) throws FrameworkException;
	void setCreateWebhook(final boolean createWebhook) throws FrameworkException;

	StripeCheckoutSession startCheckout(final SecurityContext securityContext, final Map<String, Object> parameters) throws FrameworkException;

	static void onCreation(final StripeAPIClient thisClient, final SecurityContext securityContext, final ErrorBuffer errorBuffer) throws FrameworkException {

		manageWebhook(securityContext, thisClient);
	}

	static void onModification(final StripeAPIClient thisClient, final SecurityContext securityContext, final ErrorBuffer errorBuffer, final ModificationQueue modificationQueue) throws FrameworkException {

		manageWebhook(securityContext, thisClient);
	}

	static void manageWebhook(final SecurityContext securityContext, final StripeAPIClient thisClient) throws FrameworkException {

		final PropertyKey<Boolean> createWebhookProperty = StructrApp.key(StripeAPIClient.class, "createWebhook");
		final PropertyKey<StripeWebhook> webhookProperty = StructrApp.key(StripeAPIClient.class, "webhook");
		final StripeWebhook webhook                      = thisClient.getProperty(webhookProperty);

		// create webhook?
		if (thisClient.getProperty(createWebhookProperty)) {

			// createWebhook == true
			if (webhook == null) {

				// create webhook
				createWebhook(securityContext, thisClient);
			}

		} else {

			// createWebhook == false
			if (webhook != null) {

				// delete webhook
				deleteWebhook(securityContext, thisClient);
			}
		}

	}

	static void deleteWebhook(final SecurityContext securityContext, final StripeAPIClient client) throws FrameworkException {

		LoggerFactory.getLogger(StripeAPIClient.class).info("Deleting webhook.");

		final PropertyKey<String> secretAPIKeyProperty = StructrApp.key(StripeAPIClient.class, "secretAPIKey");
		final String secretAPIKey                      = client.getProperty(secretAPIKeyProperty);
		final RequestOptions options                   = new RequestOptionsBuilder().setApiKey(secretAPIKey).build();

		try {

			final StripeWebhook webhook = client.getWebhook();
			if (webhook != null) {

				final WebhookEndpoint webhookEndpoint = WebhookEndpoint.retrieve(client.getWebhookId(), options);
				final App app                         = StructrApp.getInstance(securityContext);

				webhookEndpoint.delete(options);

				app.delete(webhook);
			}


		} catch (StripeException strex) {

			strex.printStackTrace();
		}
	}

	static void createWebhook(final SecurityContext securityContext, final StripeAPIClient client) throws FrameworkException {

		LoggerFactory.getLogger(StripeAPIClient.class).info("Creating webhook.");

		final PropertyKey<String> secretAPIKeyProperty = StructrApp.key(StripeAPIClient.class, "secretAPIKey");
		final String secretAPIKey                      = client.getProperty(secretAPIKeyProperty);
		final RequestOptions options                   = new RequestOptionsBuilder().setApiKey(secretAPIKey).build();
		final Map<String, Object> params               = Map.of(

			"url",            "https://example.com/my/webhook/endpoint",
			"enabled_events", List.of("charge.failed", "charge.succeeded")
		);

		try {

			final WebhookEndpoint webhookEndpoint = WebhookEndpoint.create(params, options);
			final App app                         = StructrApp.getInstance(securityContext);

			// store properties in API client, not in publicly visible webhook endpoint (which is only for callbacks)
			client.setWebhookSecret(webhookEndpoint.getSecret());
			client.setWebhookId(webhookEndpoint.getId());

			// associate endpoint with API client
			client.setWebhook(app.create(StripeWebhook.class,
				new NodeAttribute<>(AbstractNode.visibleToPublicUsers, true)
			));

		} catch (StripeException strex) {

			strex.printStackTrace();
		}
	}

	static StripeCheckoutSession startCheckout(final StripeAPIClient client, final SecurityContext securityContext, final Map<String, Object> parameters) throws FrameworkException {
		return StripeCheckoutSession.startCheckout(client, securityContext, parameters);
	}
}


/*
{
	$.this.checkConfiguration();

	// ID (or other info) of item to purchase
	let success   = $.retrieve('success');
	let cancel    = $.retrieve('cancel');
	let itemIDs   = $.retrieve('items');
	let itemNodes = [];
	let items     = [];

	//$.assert(condition, statusCode, message)
	$.assert(!$.empty(success), 422, 'Missing parameter "success" containing the success redirect URL.');
	$.assert(!$.empty(cancel),  422, 'Missing parameter "cancel" containing the cancel redirect URL.');
	$.assert(!$.empty(itemIDs), 422, 'Missing parameter "items" containing a list of UUIDs of purchase items.');

	for (itemId of itemIDs) {

		let item = $.find('PaymentItem', itemId);

		$.assert(!$.empty(item),             422, 'Item with ID ' + itemId + ' not found.');
		$.assert(!$.empty(item.name),        422, 'Item with Id ' + itemId + ' must have a name.');
		$.assert(!$.empty(item.description), 422, 'Item with Id ' + itemId + ' must have a description.');
		$.assert(!$.empty(item.cents),       422, 'Item with Id ' + itemId + ' must have a price ("cents" property).');
		$.assert(!$.empty(item.quantity),    422, 'Item with Id ' + itemId + ' must have a quantity.');

		items.push({
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
		});

		itemNodes.push(item);
	}

	// data for Stripe API
	let data = {
		success_url: success,
		cancel_url: cancel,
		payment_method_types: ['card'],
		line_items: items,
		mode: 'payment',
		metadata: {
			ids: itemIDs.join(',')
		}
	};

	// bearer token authorization
	$.addHeader('Authorization', 'Bearer ' + $.this.privateAPIKey);
	$.addHeader('Content-Type', 'application/x-www-form-urlencoded');

	// send request
	let response = $.POST('https://api.stripe.com/v1/checkout/sessions?' + $.formurlencode(data), '');
	if (response && response.status === 200) {

		$.create('StripePaymentIntent', {
			name: response.body.payment_intent,
			items: itemNodes
		});

		// success: return status and session ID
		return {
			status: response.status,
			sessionId: response.body.id
		}
	}

	// error: return error body
	return {
		status: response.status,
		body: response.body
	}
}
*/