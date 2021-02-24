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
package org.structr.test.entity;

import java.util.Date;
import org.structr.common.View;
import org.structr.common.error.ErrorBuffer;
import org.structr.core.entity.AbstractNode;
import org.structr.core.property.ArrayProperty;
import org.structr.core.property.BooleanProperty;
import org.structr.core.property.DateProperty;
import org.structr.core.property.DoubleProperty;
import org.structr.core.property.EnumProperty;
import org.structr.core.property.IntProperty;
import org.structr.core.property.LongProperty;
import org.structr.core.property.Property;
import org.structr.core.property.StringProperty;

/**
 * A simple entity for the most basic tests.
 *
 * The isValid method does always return true for testing purposes only.
 */
public class CsvTestOne extends AbstractNode {

	public static final Property<String[]>      stringArrayProperty = new ArrayProperty<>("stringArrayProperty", String.class).indexed();
	public static final Property<Boolean>       booleanProperty     = new BooleanProperty("booleanProperty").indexed();
	public static final Property<Double>        doubleProperty      = new DoubleProperty("doubleProperty").indexed();
	public static final Property<Integer>       integerProperty     = new IntProperty("integerProperty").indexed();
	public static final Property<Long>          longProperty        = new LongProperty("longProperty").indexed();
	public static final Property<Date>          dateProperty        = new DateProperty("dateProperty").indexed();
	public static final Property<String>        stringProperty      = new StringProperty("stringProperty").indexed();
	public static final Property<CsvTestEnum>   enumProperty        = new EnumProperty("enumProperty", CsvTestEnum.class).indexed();
	public static final Property<Integer>       index               = new IntProperty("index");


	public static final View protectedView = new View(CsvTestOne.class, "csv",
		name, index, type, stringArrayProperty, enumProperty
	);

	@Override
	public boolean isValid(ErrorBuffer errorBuffer) {
		return true;
	}

}
