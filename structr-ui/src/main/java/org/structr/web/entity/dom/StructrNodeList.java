/**
 * Copyright (C) 2010-2013 Axel Morgner, structr <structr@structr.org>
 *
 * This file is part of structr <http://structr.org>.
 *
 * structr is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * structr is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with structr.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.structr.web.entity.dom;

import java.util.ArrayList;
import java.util.List;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 *
 * @author Christian Morgner
 */
public class StructrNodeList<T extends Node> extends ArrayList<T> implements NodeList {

	public StructrNodeList() {
		super();
	}

	public StructrNodeList(List<T> children) {
		super(children);
	}

	@Override
	public Node item(int i) {

		return get(i);
	}

	@Override
	public int getLength() {

		return size();
	}
	
	public void addAll(NodeList nodeList) {
		
		int len = nodeList.getLength();
		
		for (int i=0; i<len; i++) {
			add((T)nodeList.item(i));
		}
	}
}	
