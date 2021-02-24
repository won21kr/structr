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

import java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.structr.common.SecurityContext;
import org.structr.common.error.FrameworkException;
import org.structr.core.app.App;
import org.structr.core.app.StructrApp;
import org.structr.core.entity.AbstractRelationship;
import org.structr.core.graph.NodeInterface;
import org.structr.core.graph.TransactionCommand;
import org.structr.core.property.PropertyMap;
import org.structr.web.entity.dom.DOMNode;
import org.structr.websocket.StructrWebSocket;
import org.structr.websocket.message.MessageBuilder;
import org.structr.websocket.message.WebSocketMessage;
import org.w3c.dom.DOMException;

/**
 *
 */
public class RemoveCommand extends AbstractCommand {

	private static final Logger logger = LoggerFactory.getLogger(RemoveCommand.class.getName());

	static {

		StructrWebSocket.addCommand(RemoveCommand.class);
	}

	@Override
	public void processMessage(final WebSocketMessage webSocketData) {

		setDoTransactionNotifications(true);

		final SecurityContext securityContext = getWebSocket().getSecurityContext();
		final String id = webSocketData.getId();

		if (id != null) {

			final NodeInterface node = getNode(id);

			if (node != null) {

				if (node instanceof DOMNode) {

					// Use new DOM interface
					DOMNode domNode = (DOMNode) node;

					try {

						domNode.getParentNode().removeChild(domNode);

						// remove pageId from node and all children ("move to trash")
						RemoveCommand.recursivelyRemoveNodesFromPage(domNode, securityContext);

						TransactionCommand.registerNodeCallback(node, callback);

					} catch (DOMException | FrameworkException ex) {

						logger.error("Could not remove node from page " + domNode, ex);
						getWebSocket().send(MessageBuilder.status().code(422).message(ex.getMessage()).build(), true);
					}

				} else {

					// is this even used any more?
					logger.warn("Deprecated use of RemoveCommand, please report this error and the following stack trace to the Structr team on https://github.com/structr/structr. Thanks!");
					Thread.dumpStack();

					final App app = StructrApp.getInstance(securityContext);

					try {

						// Old style: Delete all incoming CONTAINS rels
						for (AbstractRelationship rel : node.getIncomingRelationships()) {

							if ("CONTAINS".equals(rel.getType())) {

								app.delete(rel);
							}
						}

						TransactionCommand.registerNodeCallback(node, callback);

					} catch (Throwable t) {

						logger.error("Could not delete relationship", t);
						getWebSocket().send(MessageBuilder.status().code(400).message("Error in RemoveCommand: " + t.getMessage()).build(), true);

					}
				}

			} else {

				getWebSocket().send(MessageBuilder.status().code(404).build(), true);
			}

		} else {

			getWebSocket().send(MessageBuilder.status().code(400).message("RemoveCommand called with empty id").build(), true);
		}

	}

	@Override
	public String getCommand() {

		return "REMOVE";
	}

	// ----- private methods -----
	public static void recursivelyRemoveNodesFromPage(final DOMNode parent, final SecurityContext securityContext) throws FrameworkException {

		// Remove node from page
		final PropertyMap changedProperties = new PropertyMap();

		changedProperties.put(StructrApp.key(DOMNode.class, "syncedNodes"), Collections.EMPTY_LIST);
		changedProperties.put(StructrApp.key(DOMNode.class, "pageId"),      null);

		parent.setProperties(securityContext, changedProperties);

		// recurse
		for (final DOMNode child : parent.getChildren()) {

			recursivelyRemoveNodesFromPage(child, securityContext);
		}
	}
}
