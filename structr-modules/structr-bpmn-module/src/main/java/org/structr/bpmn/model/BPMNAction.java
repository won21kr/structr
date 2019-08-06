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
package org.structr.bpmn.model;

import java.util.Map;
import org.slf4j.LoggerFactory;
import org.structr.common.SecurityContext;
import org.structr.common.error.FrameworkException;

/**
 *
 */

public abstract class BPMNAction extends BPMNProcessStep<Object> {

	// method must be implemented by schema type
	public Object action(final SecurityContext securityContext, final Map<String, Object> parameters) throws FrameworkException {

		LoggerFactory.getLogger(BPMNAction.class).info("BPMNAction {}: no action specified.", getClass().getSimpleName());

		return null;
	}

	@Override
	public Object execute(final Map<String, Object> context) throws FrameworkException {
		return action(securityContext, context);
	}
}
