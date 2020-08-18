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
package org.structr.bpmn.model;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.structr.common.SecurityContext;
import org.structr.common.error.ErrorBuffer;
import org.structr.common.error.FrameworkException;

/**
 *
 */

public abstract class BPMNManualAction extends BPMNProcessStep<Object> {

	private static final Logger logger = LoggerFactory.getLogger(BPMNManualAction.class);

	@Override
	public void onCreation(final SecurityContext securityContext, final ErrorBuffer errorBuffer) throws FrameworkException {

		// user actions are suspended by default since we are waiting for user interaction
		setProperty(isSuspended, true);
		setProperty(statusText, "Waiting for manual activation");
	}

	// method must be implemented by schema type
	public Object action(final SecurityContext securityContext, final Map<String, Object> parameters) throws FrameworkException {

		LoggerFactory.getLogger(BPMNAction.class).info("BPMNManualAction {}: no action specified.", getClass().getSimpleName());

		return null;
	}

	@Override
	public Object execute(final Map<String, Object> context) {

		try {
			return action(securityContext, context);

		} catch (FrameworkException fex) {
			logger.warn("Unable to execute condition {} ({}): {}", getUuid(), getClass().getSimpleName(), fex.getMessage());
		}

		return null;
	}
}
