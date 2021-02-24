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
package org.structr.core.property;

import java.util.Collection;
import org.apache.commons.lang3.StringUtils;
import org.structr.api.Predicate;
import org.structr.api.search.SortType;
import org.structr.common.SecurityContext;
import org.structr.common.error.FrameworkException;
import org.structr.core.GraphObject;
import org.structr.core.converter.PropertyConverter;

/**
 * A read-only property that returns the number of elements in a collection returned from a given property.
 *
 *
 */
public class ElementCounter extends AbstractReadOnlyProperty<Integer> {

	private Property<? extends Iterable> collectionProperty = null;

	public ElementCounter(String name) {
		this(name, null);
	}

	public ElementCounter(String name, Property<? extends Iterable> collectionProperty) {
		super(name);

		this.collectionProperty = collectionProperty;
	}

	@Override
	public Integer getProperty(SecurityContext securityContext, GraphObject obj, boolean applyConverter) {
		return getProperty(securityContext, obj, applyConverter, null);
	}

	@Override
	public Integer getProperty(SecurityContext securityContext, GraphObject obj, boolean applyConverter, final Predicate<GraphObject> predicate) {

		int count = 0;

		if(obj != null) {

			Object toCount = obj.getProperty(collectionProperty);
			if(toCount != null) {

				if (toCount instanceof Collection) {

					count = ((Collection)toCount).size();

				} else if (toCount instanceof Iterable) {

					for(Object o : ((Iterable)toCount)) {
						count++;
					}

				} else {

					// a single object
					count = 1;
				}
			}
		}

		return count;
	}

	@Override
	public Class relatedType() {
		return null;
	}

	@Override
	public Class valueType() {
		return Integer.class;
	}

	@Override
	public boolean isCollection() {
		return false;
	}

	@Override
	public SortType getSortType() {
		return SortType.Integer;
	}

	@Override
	public PropertyConverter<?, Integer> inputConverter(SecurityContext securityContext) {
		return new InputConverter(securityContext);
	}

	// ----- OpenAPI -----
	@Override
	public Object getExampleValue(final String type, final String viewName) {
		return null;
	}

	protected class InputConverter extends PropertyConverter<Object, Integer> {

		public InputConverter(SecurityContext securityContext) {
			super(securityContext, null);
		}

		@Override
		public Object revert(Integer source) throws FrameworkException {
			return source;
		}

		@Override
		public Integer convert(Object source) {

			if (source == null) return null;

			if (source instanceof Number) {

				return ((Number)source).intValue();

			}

			if (source instanceof String && StringUtils.isNotBlank((String) source)) {

				return Integer.parseInt(source.toString());
			}

			return null;

		}
	}
}
