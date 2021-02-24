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
package org.structr.core.graph.search;

import org.slf4j.LoggerFactory;
import org.structr.api.search.ComparisonQuery;
import org.structr.api.search.Occurrence;
import org.structr.common.SecurityContext;
import org.structr.common.error.FrameworkException;
import org.structr.core.GraphObject;
import org.structr.core.converter.PropertyConverter;
import org.structr.core.property.FunctionProperty;
import org.structr.core.property.PropertyKey;

public class ComparisonSearchAttribute<T> extends SearchAttribute<T> implements ComparisonQuery {

	private PropertyKey<T> searchKey = null;
	private T searchValue		     = null;
	private Operation operation      = null;

	public ComparisonSearchAttribute(final PropertyKey<T> searchKey, final Operation operation, final Object value, final Occurrence occur) {
		super(occur);

		this.searchKey = searchKey;
		this.operation = operation;

		try {

			if (!(searchKey instanceof FunctionProperty)) {

				PropertyConverter converter = searchKey.inputConverter(SecurityContext.getSuperUserInstance());

				if (converter != null) {
					this.searchValue = (T) converter.convert(value);
				} else {
					try {
						
						this.searchValue = (T)value.toString();
					} catch (Throwable t) {

						LoggerFactory.getLogger(ComparisonSearchAttribute.class).warn("Could not convert given value. " + t.getMessage());
					}
				}

			} else {

				this.searchValue = this.searchKey.convertSearchValue(SecurityContext.getSuperUserInstance(), value.toString());
			}

		} catch (FrameworkException ex) {

			LoggerFactory.getLogger(ComparisonSearchAttribute.class).warn("Could not convert given value. " + ex.getMessage());
		}
	}

	@Override
	public T getSearchValue() {
		return this.searchValue;
	}

	@Override
	public String toString() {
		return "ComparisonSearchAttribute()";
	}

	@Override
	public PropertyKey getKey() {
		return searchKey;
	}

	@Override
	public Operation getOperation() {
		return operation;
	}

	@Override
	public boolean includeInResult(GraphObject entity) {

		final T value = entity.getProperty(searchKey);

		if (value != null && this.searchValue != null) {

			if (value instanceof Comparable && this.searchValue instanceof Comparable) {

				final Comparable a = (Comparable)value;
				final Comparable b = (Comparable)this.searchValue;

				switch (this.operation) {
					case equal:
						return a.compareTo(b) == 0;
					case notEqual:
						return a.compareTo(b) != 0;
					case greater:
						return a.compareTo(b) > 0;
					case greaterOrEqual:
						return a.compareTo(b) >= 0;
					case less:
						return a.compareTo(b) < 0;
					case lessOrEqual:
						return a.compareTo(b) <= 0;
					// FixMe: The following operations need special handling, which isn't included in the Comparable interface.
					case startsWith:
						return true;
					case endsWith:
						return true;
					case contains:
						return true;
					case caseInsensitiveStartsWith:
						return true;
					case caseInsensitiveEndsWith:
						return true;
					case caseInsensitiveContains:
						return true;

				}

			}
		} else if (value == null && operation.equals(Operation.isNull)) {
			return true;
		} else if (value != null && operation.equals(Operation.isNotNull)) {
			return true;
		}

		return false;
	}

	@Override
	public Class getQueryType() {
		return ComparisonQuery.class;
	}

	@Override
	public boolean isExactMatch() {
		return true;
	}
}
