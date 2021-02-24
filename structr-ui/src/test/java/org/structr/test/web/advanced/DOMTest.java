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
package org.structr.test.web.advanced;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.structr.common.error.FrameworkException;
import org.structr.test.web.StructrUiTest;
import org.structr.web.entity.dom.Content;
import org.structr.web.entity.dom.Page;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/**
 *
 *
 */

public abstract class DOMTest extends StructrUiTest {
	
	private static final Logger logger = LoggerFactory.getLogger(DOMTest.class.getName());
	
	protected Document getDocument() {
		
		try {
			
			List<Page> pages = this.createTestNodes(Page.class, 1);

			if (!pages.isEmpty()) {
				
				return pages.get(0);
			}
			
		} catch (FrameworkException fex) {
			
			logger.warn("", fex);
		}

		return null;
		
		
	}
	
	protected Content getContentNode() {
		
		try {
			
			List<Content> contents = this.createTestNodes(Content.class, 1);

			if (!contents.isEmpty()) {
				
				return contents.get(0);
			}
			
		} catch (FrameworkException fex) {
			
			logger.warn("", fex);
		}

		return null;
	 }
	
	protected void printNode(Node node, int depth) {
		
		for (int i=0; i<depth; i++) {
			System.out.print("    ");
		}
		
		System.out.println(node.getNodeName());
		
		Node child = node.getFirstChild();
		while (child != null) {
			printNode(child, depth + 1);
			child = child.getNextSibling();
		}
	}

}
