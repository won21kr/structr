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

import org.structr.api.Predicate;
import org.structr.api.search.SortType;
import org.structr.common.ContextAwareEntity;
import org.structr.common.SecurityContext;
import org.structr.core.GraphObject;
import org.structr.core.entity.Favoritable;

/**
 */
public class InternalPathProperty extends AbstractReadOnlyProperty<String> {

	public InternalPathProperty(final String name) {
		super(name);
	}

	@Override
	public Class valueType() {
		return String.class;
	}

	@Override
	public Class relatedType() {
		return null;
	}

	@Override
	public String getProperty(final SecurityContext securityContext, final GraphObject obj, boolean applyConverter) {
		return getProperty(securityContext, obj, applyConverter, null);
	}

	@Override
	public String getProperty(final SecurityContext securityContext, final GraphObject obj, final boolean applyConverter, final Predicate<GraphObject> predicate) {

		// instanceof returns false for null objects
		if (obj instanceof ContextAwareEntity) {
			return ((ContextAwareEntity)obj).getEntityContextPath();
		}

		// instanceof returns false for null objects
		if (obj instanceof Favoritable) {
			return ((Favoritable)obj).getContext();
		}

		return null;
	}

	@Override
	public boolean isCollection() {
		return false;
	}

	@Override
	public SortType getSortType() {
		return SortType.Default;
	}

	// ----- OpenAPI -----
	@Override
	public Object getExampleValue(final String type, final String viewName) {
		return null;
	}
}
