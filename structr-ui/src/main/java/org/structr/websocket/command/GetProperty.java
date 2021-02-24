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

import org.structr.common.error.FrameworkException;
import org.structr.core.GraphObject;
import org.structr.core.app.StructrApp;
import org.structr.core.converter.PropertyConverter;
import org.structr.core.property.PropertyKey;
import org.structr.websocket.StructrWebSocket;
import org.structr.websocket.message.MessageBuilder;
import org.structr.websocket.message.WebSocketMessage;

//~--- classes ----------------------------------------------------------------
/**
 *
 *
 */
public class GetProperty extends AbstractCommand {

	static {

		StructrWebSocket.addCommand(GetProperty.class);

	}

	@Override
	public void processMessage(final WebSocketMessage webSocketData) {

		setDoTransactionNotifications(false);

		final GraphObject obj = getGraphObject(webSocketData.getId());
		String key = webSocketData.getNodeDataStringValue("key");

		if (obj != null) {

			PropertyKey propertyKey = StructrApp.getConfiguration().getPropertyKeyForJSONName(obj.getClass(), key, true);
			PropertyConverter converter = propertyKey.inputConverter(getWebSocket().getSecurityContext());

			Object value = obj.getProperty(propertyKey);
			if (converter != null) {

				try {
					value = converter.revert(value);

				} catch (FrameworkException ex) {

					getWebSocket().send(MessageBuilder.status().code(400).message(ex.getMessage()).build(), true);

				}
			}

			webSocketData.setNodeData(key, value);

			// send only over local connection (no broadcast)
			getWebSocket().send(webSocketData, true);

		} else {

			getWebSocket().send(MessageBuilder.status().code(404).build(), true);

		}
	}

	//~--- get methods ----------------------------------------------------
	@Override
	public String getCommand() {
		return "GET_PROPERTY";
	}
}
