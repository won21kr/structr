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

import java.util.Arrays;
import org.structr.common.SecurityContext;
import org.structr.common.error.FrameworkException;
import org.structr.core.app.App;
import org.structr.core.app.StructrApp;
import org.structr.core.entity.AbstractNode;
import org.structr.core.graph.RelationshipInterface;
import org.structr.core.graph.TransactionCommand;
import org.structr.websocket.StructrWebSocket;
import org.structr.websocket.message.MessageBuilder;
import org.structr.websocket.message.WebSocketMessage;

//~--- classes ----------------------------------------------------------------
/**
 * Websocket command to create a relationship.
 *
 *
 */
public class CreateRelationshipCommand extends AbstractCommand {

	static {

		StructrWebSocket.addCommand(CreateRelationshipCommand.class);
	}

	@Override
	public void processMessage(final WebSocketMessage webSocketData) {

		setDoTransactionNotifications(true);

		final String sourceId                = webSocketData.getRelDataStringValue("sourceId");
		final String targetId                = webSocketData.getRelDataStringValue("targetId");
		final String relType                 = webSocketData.getRelDataStringValue("relType");
		final AbstractNode sourceNode        = getNode(sourceId);
		final AbstractNode targetNode        = getNode(targetId);

		if ((sourceNode != null) && (targetNode != null)) {

			final SecurityContext securityContext = getWebSocket().getSecurityContext();
			final App app                         = StructrApp.getInstance(securityContext);

			try {

				final Class relationClass       = StructrApp.getConfiguration().getRelationClassForCombinedType(sourceNode.getType(), relType, targetNode.getType());

				final RelationshipInterface rel = app.create(sourceNode, targetNode, relationClass);

				TransactionCommand.registerRelCallback(rel, callback);

				if (rel != null) {

					webSocketData.setResult(Arrays.asList(rel));

					getWebSocket().send(webSocketData, true);

				}

			} catch (FrameworkException t) {

				getWebSocket().send(MessageBuilder.status().code(400).message(t.getMessage()).build(), true);

			}

		} else {

			getWebSocket().send(MessageBuilder.status().code(400).message("The CREATE_RELATIONSHIP command needs source and target!").build(), true);
		}

	}

	//~--- get methods ----------------------------------------------------
	@Override
	public String getCommand() {

		return "CREATE_RELATIONSHIP";

	}

}
