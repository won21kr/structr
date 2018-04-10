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
package org.structr.flow.rels;

import org.structr.core.entity.OneToOne;
import org.structr.flow.FlowNode;

/**
 *
 */
public class FlowNodes extends OneToOne<FlowNode, FlowNode> {

	@Override
	public Class<FlowNode> getSourceType() {
		return FlowNode.class;
	}

	@Override
	public Class<FlowNode> getTargetType() {
		return FlowNode.class;
	}

	@Override
	public String name() {
		return "NEXT_FLOW_NODE";
	}
}
