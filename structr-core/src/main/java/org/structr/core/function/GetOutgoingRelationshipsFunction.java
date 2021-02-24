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
package org.structr.core.function;

import java.util.ArrayList;
import java.util.List;
import org.structr.common.error.ArgumentCountException;
import org.structr.common.error.ArgumentNullException;
import org.structr.common.error.FrameworkException;
import org.structr.core.entity.AbstractNode;
import org.structr.core.entity.AbstractRelationship;
import org.structr.core.graph.NodeInterface;
import org.structr.schema.action.ActionContext;

public class GetOutgoingRelationshipsFunction extends CoreFunction {

	public static final String ERROR_MESSAGE_GET_OUTGOING_RELATIONSHIPS    = "Usage: ${get_outgoing_relationships(from, to [, relType])}. Example: ${get_outgoing_relationships(me, user, 'FOLLOWS')}";
	public static final String ERROR_MESSAGE_GET_OUTGOING_RELATIONSHIPS_JS = "Usage: ${{Structr.get_outgoing_relationships(from, to [, relType])}}. Example: ${{Structr.get_outgoing_relationships(Structr.get('me'), user, 'FOLLOWS')}}";

	@Override
	public String getName() {
		return "get_outgoing_relationships";
	}

	@Override
	public String getSignature() {
		return "source, target [, relType ]";
	}

	@Override
	public Object apply(final ActionContext ctx, final Object caller, final Object[] sources) throws FrameworkException {

		final List<AbstractRelationship> list = new ArrayList<>();

		try {

			assertArrayHasMinLengthAndMaxLengthAndAllElementsNotNull(sources, 2, 3);

			final Object source = sources[0];
			final Object target = sources[1];

			AbstractNode sourceNode = null;
			AbstractNode targetNode = null;

			if (source instanceof AbstractNode && target instanceof AbstractNode) {

				sourceNode = (AbstractNode)source;
				targetNode = (AbstractNode)target;

			} else {

				logger.warn("Error: entities are not nodes. Parameters: {}", getParametersAsString(sources));
				return "Error: entities are not nodes.";
			}

			if (sources.length == 2) {

				for (final AbstractRelationship rel : sourceNode.getOutgoingRelationships()) {

					final NodeInterface s = rel.getSourceNode();
					final NodeInterface t = rel.getTargetNode();

					// We need to check if current user can see source and target node which is often not the case for OWNS or SECURITY rels
					if (s != null && t != null
						&& s.equals(sourceNode) && t.equals(targetNode)) {
						list.add(rel);
					}
				}

			} else if (sources.length == 3) {

				// dont try to create the relClass because we would need to do that both ways!!! otherwise it just fails if the nodes are in the "wrong" order (see tests:890f)
				final String relType = (String)sources[2];

				for (final AbstractRelationship rel : sourceNode.getOutgoingRelationships()) {

					final NodeInterface s = rel.getSourceNode();
					final NodeInterface t = rel.getTargetNode();

					// We need to check if current user can see source and target node which is often not the case for OWNS or SECURITY rels
					if (s != null && t != null
						&& rel.getRelType().name().equals(relType)
						&& s.equals(sourceNode) && t.equals(targetNode)) {
						list.add(rel);
					}
				}
			}

		} catch (ArgumentNullException pe) {

			logParameterError(caller, sources, pe.getMessage(), ctx.isJavaScriptContext());

		} catch (ArgumentCountException pe) {

			logParameterError(caller, sources, pe.getMessage(), ctx.isJavaScriptContext());
			return usage(ctx.isJavaScriptContext());
		}

		return list;
	}

	@Override
	public String usage(boolean inJavaScriptContext) {
		return (inJavaScriptContext ? ERROR_MESSAGE_GET_OUTGOING_RELATIONSHIPS_JS : ERROR_MESSAGE_GET_OUTGOING_RELATIONSHIPS);
	}

	@Override
	public String shortDescription() {
		return "Returns the outgoing relationships of the given entity with an optional relationship type";
	}
}
