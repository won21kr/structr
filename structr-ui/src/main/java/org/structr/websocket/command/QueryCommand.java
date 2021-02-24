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
package org.structr.websocket.command;

import com.drew.lang.Iterables;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.structr.api.util.ResultStream;
import org.structr.common.SecurityContext;
import org.structr.common.error.FrameworkException;
import org.structr.core.app.Query;
import org.structr.core.app.StructrApp;
import org.structr.core.entity.AbstractNode;
import org.structr.core.property.PropertyKey;
import org.structr.core.property.PropertyMap;
import org.structr.schema.SchemaHelper;
import org.structr.websocket.StructrWebSocket;
import org.structr.websocket.message.MessageBuilder;
import org.structr.websocket.message.WebSocketMessage;

/**
 * Websocket command to retrieve nodes of a given type which are on root level,
 * i.e. not children of another node.
 *
 * To get all nodes of a certain type, see the {@link GetCommand}.
 */
public class QueryCommand extends AbstractCommand {

	private static final Logger logger = LoggerFactory.getLogger(QueryCommand.class.getName());

	static {

		StructrWebSocket.addCommand(QueryCommand.class);

	}

	@Override
	public void processMessage(final WebSocketMessage webSocketData) throws FrameworkException {

		setDoTransactionNotifications(false);

		final SecurityContext securityContext = getWebSocket().getSecurityContext();

		final String rawType                  = webSocketData.getNodeDataStringValue("type");
		final String properties               = webSocketData.getNodeDataStringValue("properties");
		final Boolean exact                   = webSocketData.getNodeDataBooleanValue("exact");
		final String customView               = webSocketData.getNodeDataStringValue("customView");
		final Class type                      = SchemaHelper.getEntityClassForRawType(rawType);

		if (type == null) {
			getWebSocket().send(MessageBuilder.status().code(404).message("Type " + rawType + " not found").build(), true);
			return;
		}

		if (customView != null) {
			securityContext.setCustomView(StringUtils.split(customView, ","));
		}

		final String sortKey           = webSocketData.getSortKey();
		final int pageSize             = webSocketData.getPageSize();
		final int page                 = webSocketData.getPage();

		final Query query = StructrApp.getInstance(securityContext)
			.nodeQuery(type)
			.page(page)
			.pageSize(pageSize);

		if (sortKey != null) {
			final PropertyKey sortProperty = StructrApp.key(type, sortKey);
			final String sortOrder         = webSocketData.getSortOrder();

			query.sort(sortProperty, "desc".equals(sortOrder));
		}

		if (properties != null) {

			try {
				final Gson gson                       = new GsonBuilder().create();
				final Map<String, Object> querySource = gson.fromJson(properties, new TypeToken<Map<String, Object>>() {}.getType());
				final PropertyMap queryMap            = PropertyMap.inputTypeToJavaType(securityContext, type, querySource);
				final boolean inexactQuery            = exact != null && exact == false;

				// add properties to query
				for (final Entry<PropertyKey, Object> entry : queryMap.entrySet()) {
					query.and(entry.getKey(), entry.getValue(), !inexactQuery);
				}

			} catch (FrameworkException fex) {

				logger.warn("Exception occured", fex);
				getWebSocket().send(MessageBuilder.status().code(fex.getStatus()).message(fex.getMessage()).build(), true);

				return;
			}
		}

		try {

			// do search
			final ResultStream<AbstractNode> result = query.getResultStream();
			final List<AbstractNode> list           = Iterables.toList(result);

			// set full result list
			webSocketData.setResult(list);
			webSocketData.setRawResultCount(result.calculateTotalResultCount(null, securityContext.getSoftLimit(pageSize)));

			// send only over local connection
			getWebSocket().send(webSocketData, true);

		} catch (FrameworkException fex) {

			logger.warn("Exception occured", fex);
			getWebSocket().send(MessageBuilder.status().code(fex.getStatus()).message(fex.getMessage()).build(), true);
		}
	}

	@Override
	public String getCommand() {
		return "QUERY";
	}
}
