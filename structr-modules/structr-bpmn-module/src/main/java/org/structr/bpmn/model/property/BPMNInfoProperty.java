/**
 * Copyright (C) 2010-2019 Structr GmbH
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.structr.api.Predicate;
import org.structr.api.search.SortType;
import org.structr.bpmn.model.BPMNProcess;
import org.structr.common.SecurityContext;
import org.structr.common.error.FrameworkException;
import org.structr.core.GraphObject;
import org.structr.core.property.AbstractReadOnlyProperty;

/**
 */
public class BPMNInfoProperty extends AbstractReadOnlyProperty<Object> {

	private static final Logger logger = LoggerFactory.getLogger(BPMNInfoProperty.class);

	public BPMNInfoProperty(final String name) {
		super(name);
	}

	@Override
	public Class valueType() {
		return null;
	}

	@Override
	public Class relatedType() {
		return null;
	}

	@Override
	public Object getProperty(SecurityContext securityContext, GraphObject obj, boolean applyConverter) {
		return getProperty(securityContext, obj, applyConverter, null);
	}

	@Override
	public Object getProperty(SecurityContext securityContext, GraphObject obj, boolean applyConverter, Predicate<GraphObject> predicate) {

		if (obj instanceof BPMNProcess) {

			try {

				return ((BPMNProcess)obj).getStatus();

			} catch (FrameworkException ex) {
				logger.warn("Unable to fetch status from {}: {}", obj.getUuid(), ex.getMessage());
			}
		}

		return null;
	}

	@Override
	public boolean isCollection() {
		return false;
	}

	@Override
	public SortType getSortType() {
		return null;
	}
}
