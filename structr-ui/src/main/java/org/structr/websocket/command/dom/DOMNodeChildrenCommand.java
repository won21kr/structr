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
package org.structr.websocket.command.dom;

import java.util.*;
import org.structr.common.PropertyView;
import org.structr.core.GraphObject;
import org.structr.web.entity.dom.DOMNode;
import org.structr.websocket.StructrWebSocket;
import org.structr.websocket.command.AbstractCommand;
import org.structr.websocket.message.WebSocketMessage;

//~--- classes ----------------------------------------------------------------

/**
 * Websocket command to return the children of the given DOM node
 *
 *
 */
public class DOMNodeChildrenCommand extends AbstractCommand {

	static {

		StructrWebSocket.addCommand(DOMNodeChildrenCommand.class);

	}

	@Override
	public void processMessage(final WebSocketMessage webSocketData) {

		setDoTransactionNotifications(false);

		final DOMNode node = getDOMNode(webSocketData.getId());

		if (node == null) {

			return;
		}

		final List<GraphObject> result = new LinkedList<>();
		DOMNode currentNode      = (DOMNode) node.getFirstChild();

		while (currentNode != null) {

			result.add(currentNode);

			currentNode = (DOMNode) currentNode.getNextSibling();

		}

		webSocketData.setView(PropertyView.All);
		webSocketData.setResult(result);

		// send only over local connection
		getWebSocket().send(webSocketData, true);

	}

	//~--- get methods ----------------------------------------------------

	@Override
	public String getCommand() {

		return "DOM_NODE_CHILDREN";

	}

}
