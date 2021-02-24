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
package org.structr.test.rest.entity;

import org.structr.common.PropertyView;
import org.structr.common.View;
import org.structr.core.entity.AbstractNode;
import org.structr.core.notion.PropertySetNotion;
import org.structr.core.property.EndNode;
import org.structr.core.property.FunctionProperty;
import org.structr.core.property.Property;

/**
 *
 *
 */
public class TestTen extends AbstractNode {

	public static final Property<TestSeven> testSeven 		= new EndNode<>("testSeven", TenSevenOneToOne.class, new PropertySetNotion(true, TestSeven.id, TestSeven.aString));
	public static final Property<Object> functionTest 		= new FunctionProperty<>("functionTest").readFunction("{ return { name: 'test', value: 123, me: Structr.this }; }");
	public static final Property<Object> getNameProperty 	= new FunctionProperty<>("getNameProperty").readFunction("{ return Structr.this.name; }").cachingEnabled(true);
	public static final Property<Object> getRandomNumProp	= new FunctionProperty<>("getRandomNumProp").readFunction("{ return Math.random()*10000; }").cachingEnabled(true);


	public static final View defaultView = new View(TestTen.class, PropertyView.Public,
		name, testSeven, functionTest, getNameProperty, getRandomNumProp
	);

}
