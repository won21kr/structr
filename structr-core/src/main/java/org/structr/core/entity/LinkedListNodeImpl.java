/*
 * Copyright (C) 2010-2021 Structr GmbH
 *
 * This file is part of Structr <http://structr.org>.
 *
 * Structr is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Structr is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Structr.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.structr.core.entity;

import java.util.List;
import org.structr.api.graph.Node;
import org.structr.api.util.Iterables;
import org.structr.common.error.FrameworkException;
import org.structr.core.app.App;
import org.structr.core.app.StructrApp;
import org.structr.core.graph.NodeInterface;
import org.structr.core.graph.RelationshipInterface;
import org.structr.core.property.PropertyMap;

/**
 * Abstract base class for a multi-dimensional linked list data structure.
 *
 *
 * @param <T>
 */
public abstract class LinkedListNodeImpl<T extends NodeInterface> extends AbstractNode implements LinkedListNode<T> {

	/**
	 * Returns the predecessor of the given element in the list structure
	 * defined by this LinkedListManager.
	 *
	 * @param currentElement
	 * @return previous element
	 */
	@Override
	public  T listGetPrevious(final T currentElement) {

		Relation<T, T, OneStartpoint<T>, OneEndpoint<T>> prevRel = currentElement.getIncomingRelationship(getSiblingLinkType());
		if (prevRel != null) {

			return (T)prevRel.getSourceNode();
		}

		return null;
	}

	/**
	 * Returns the successor of the given element in the list structure
	 * defined by this LinkedListManager.
	 *
	 * @param currentElement
	 * @return next element
	 */
	@Override
	public T listGetNext(final T currentElement) {

		Relation<T, T, OneStartpoint<T>, OneEndpoint<T>> nextRel = currentElement.getOutgoingRelationship(getSiblingLinkType());
		if (nextRel != null) {

			return (T)nextRel.getTargetNode();
		}

		return null;
	}

	/**
	 * Inserts newElement before currentElement in the list defined by this
	 * LinkedListManager.
	 *
	 * @param currentElement the reference element
	 * @param newElement the new element
	 * @throws org.structr.common.error.FrameworkException
	 */
	@Override
	public void listInsertBefore(final T currentElement, final T newElement) throws FrameworkException {

		if (currentElement.getUuid().equals(newElement.getUuid())) {
			throw new IllegalStateException("Cannot link a node to itself!");
		}

		final T previousElement = listGetPrevious(currentElement);
		if (previousElement == null) {

			linkNodes(getSiblingLinkType(), newElement, currentElement);

		} else {
			// delete old relationship
			unlinkNodes(getSiblingLinkType(), previousElement, currentElement);

			// dont create self link
			if (!previousElement.getUuid().equals(newElement.getUuid())) {
				linkNodes(getSiblingLinkType(), previousElement, newElement);
			}

			// dont create self link
			if (!newElement.getUuid().equals(currentElement.getUuid())) {
				linkNodes(getSiblingLinkType(), newElement, currentElement);
			}
		}
	}

	/**
	 * Inserts newElement after currentElement in the list defined by this
	 * LinkedListManager.
	 *
	 * @param currentElement the reference element
	 * @param newElement the new element
	 */
	@Override
	public void listInsertAfter(final T currentElement, final T newElement) throws FrameworkException {
		if (currentElement.getUuid().equals(newElement.getUuid())) {
			throw new IllegalStateException("Cannot link a node to itself!");
		}

		final T next = listGetNext(currentElement);
		if (next == null) {

			linkNodes(getSiblingLinkType(), currentElement, newElement);

		} else {

			// unlink predecessor and successor
			unlinkNodes(getSiblingLinkType(), currentElement, next);

			// link predecessor to new element
			linkNodes(getSiblingLinkType(), currentElement, newElement);

			// dont create self link
			if (!newElement.getUuid().equals(next.getUuid())) {

				// link new element to successor
				linkNodes(getSiblingLinkType(), newElement, next);
			}
		}
	}

	/**
	 * Removes the current element from the list defined by this
	 * LinkedListManager.
	 *
	 * @param currentElement the element to be removed
	 */
	@Override
	public void listRemove(final T currentElement) throws FrameworkException {

		final T previousElement = listGetPrevious(currentElement);
		final T nextElement     = listGetNext(currentElement);

		if (currentElement != null) {

			if (previousElement != null) {
				unlinkNodes(getSiblingLinkType(), previousElement, currentElement);
			}

			if (nextElement != null) {
				unlinkNodes(getSiblingLinkType(), currentElement, nextElement);
			}
		}

		if (previousElement != null && nextElement != null) {

			Node previousNode = previousElement.getNode();
			Node nextNode     = nextElement.getNode();

			if (previousNode != null && nextNode != null) {

				linkNodes(getSiblingLinkType(), previousElement, nextElement);
			}

		}
	}

	@Override
	public <R extends Relation<T, T, ?, ?>> void linkNodes(final Class<R> linkType, final T startNode, final T endNode) throws FrameworkException {
		linkNodes(linkType, startNode, endNode, null);
	}

	@Override
	public <R extends Relation<T, T, ?, ?>> void linkNodes(final Class<R> linkType, final T startNode, final T endNode, final PropertyMap properties) throws FrameworkException {
		StructrApp.getInstance(securityContext).create(startNode, endNode, linkType, properties);
	}

	private <R extends Relation<T, T, OneStartpoint<T>, OneEndpoint<T>>> void unlinkNodes(final Class<R> linkType, final T startNode, final T endNode) throws FrameworkException {

		final App app      = StructrApp.getInstance(securityContext);
		final List<R> list = Iterables.toList(startNode.getRelationships(linkType));

		for (RelationshipInterface rel : list) {

			if (rel != null && rel.getTargetNode().equals(endNode)) {
				app.delete(rel);
			}
		}
	}
}
