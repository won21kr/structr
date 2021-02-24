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
package org.structr.memory.index.predicate;

import java.util.Arrays;
import org.structr.api.Predicate;
import org.structr.api.graph.PropertyContainer;

/**
 */
public class ValuePredicate<T extends PropertyContainer, V> implements Predicate<T> {

	private String key     = null;
	private V desiredValue = null;

	public ValuePredicate(final String key, final V desiredValue) {

		this.key          = key;
		this.desiredValue = desiredValue;
	}

	@Override
	public String toString() {

		return "VALUE(" + key + " = " + desiredValue + ")";
	}

	@Override
	public boolean accept(final T entity) {

		final Object value = entity.getProperty(key);

		// support for null values
		if (desiredValue == null) {
			return value == null;
		}

		if (value != null) {

			if (desiredValue instanceof Number) {

				final Double expected = ((Number)desiredValue).doubleValue();
				final Double actual   = ((Number)value).doubleValue();

				return expected.compareTo(actual) == 0;
			}

			if (value.getClass().isArray()) {

				return Arrays.deepEquals((Object[])desiredValue, (Object[])value);

			}

			return desiredValue.equals(value);
		}

		return false;
	}
}
