/**
 * Copyright (C) 2010-2018 Structr GmbH
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

import java.util.*;
import org.apache.commons.collections.IteratorUtils;
import org.structr.api.graph.Direction;
import org.structr.common.PropertyView;
import org.structr.core.GraphObject;
import org.structr.core.IterableAdapter;
import org.structr.core.entity.AbstractNode;
import org.structr.core.graph.NodeInterface;
import org.structr.core.graph.RelationshipFactory;
import org.structr.core.graph.RelationshipInterface;
import org.structr.web.common.RelType;
import org.structr.websocket.StructrWebSocket;
import org.structr.websocket.message.WebSocketMessage;

//~--- classes ----------------------------------------------------------------

/**
 * Websocket command to return the children of the given node
 *
 *
 */
public class ChildrenCommand extends AbstractCommand {

	static {

		StructrWebSocket.addCommand(ChildrenCommand.class);

	}

	@Override
	public void processMessage(final WebSocketMessage webSocketData) {

		setDoTransactionNotifications(false);

		final RelationshipFactory factory = new RelationshipFactory(getWebSocket().getSecurityContext());
		final AbstractNode node           = getNode(webSocketData.getId());

		if (node == null) {

			return;
		}

		final Iterator<RelationshipInterface> rels = new IterableAdapter<>(node.getNode().getRelationships(Direction.OUTGOING, RelType.CONTAINS), factory).iterator();

		final List<RelationshipInterface> childRels = IteratorUtils.toList(rels);
		
		// sort relationships by position
		Collections.sort(childRels, new Comparator<RelationshipInterface>() {

			@Override
			public int compare(RelationshipInterface o1, RelationshipInterface o2) {

				try {
					if (!o1.getRelationship().hasProperty("position")) {
						return -1;
					}

					if (!o2.getRelationship().hasProperty("position")) {
						return 1;
					}

					Integer pos1 = o1.getProperty("position");
					Integer pos2 = o2.getProperty("position");

					if (pos1 != null && pos2 != null) {

						return pos1.compareTo(pos2);
					}

					return 0;
				
				} catch (final IllegalArgumentException iae) {
					return -1;
				}

			}
		});

		final List<GraphObject> result             = new LinkedList();

		for (RelationshipInterface rel : childRels) {

			NodeInterface endNode = rel.getTargetNode();
			if (endNode == null) {

				continue;
			}

			result.add(endNode);
		}
		
		webSocketData.setView(PropertyView.Ui);
		webSocketData.setResult(result);

		// send only over local connection
		getWebSocket().send(webSocketData, true);

	}

	//~--- get methods ----------------------------------------------------

	@Override
	public String getCommand() {

		return "CHILDREN";

	}

}
