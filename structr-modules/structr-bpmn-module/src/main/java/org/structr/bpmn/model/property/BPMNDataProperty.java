/*
 * Copyright (C) 2010-2020 Structr GmbH
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
package org.structr.bpmn.model.property;

import org.structr.api.Predicate;
import org.structr.api.search.SortType;
import org.structr.common.SecurityContext;
import org.structr.common.error.FrameworkException;
import org.structr.core.GraphObject;
import org.structr.core.converter.PropertyConverter;
import org.structr.core.entity.AbstractNode;
import org.structr.core.property.AbstractPrimitiveProperty;

/**
 */
public class BPMNDataProperty extends AbstractPrimitiveProperty<Object> {

	public BPMNDataProperty(final String name) {
		super(name);
	}

	@Override
	public Object fixDatabaseProperty(final Object value) {
		return value;
	}

	@Override
	public String typeName() {
		return null;
	}

	@Override
	public Class valueType() {
		return null;
	}

	@Override
	public PropertyConverter databaseConverter(final SecurityContext securityContext) {
		return null;
	}

	@Override
	public PropertyConverter databaseConverter(final SecurityContext securityContext, final GraphObject entity) {
		return null;
	}

	@Override
	public PropertyConverter inputConverter(final SecurityContext securityContext) {
		return null;
	}

	@Override
	public SortType getSortType() {
		return null;
	}

	@Override
	public Object getProperty(final SecurityContext securityContext, final GraphObject obj, final boolean applyConverter, final Predicate<GraphObject> predicate) {
		return null;
	}

	@Override
	public Object setProperty(final SecurityContext securityContext, final GraphObject obj, final Object value) throws FrameworkException {
		((AbstractNode)obj).getTemporaryStorage().put("data", value);
		return null;
	}

	@Override
	public Object getExampleValue(String type, String viewName) {
		return null;
	}
}
