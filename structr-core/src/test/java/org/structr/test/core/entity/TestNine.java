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
package org.structr.test.core.entity;

import org.structr.common.error.ErrorBuffer;
import org.structr.core.entity.AbstractNode;
import org.structr.core.property.Property;
import org.structr.core.property.StartNode;

/**
 * A simple entity for testing constraint-based cascading delete.
 *
 *
 */
public class TestNine extends AbstractNode {

	public static final Property<TestSix>  oneToManyTestSixConstraint  = new StartNode<>("oneToManyTestSixConstraint", SixNineOneToManyCascadeConstraint.class);

	@Override
	public boolean isValid(final ErrorBuffer errorBuffer) {
		return super.isValid(errorBuffer) && getProperty(oneToManyTestSixConstraint) != null;
	}
}
