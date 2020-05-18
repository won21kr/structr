/**
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
import org.structr.common.error.FrameworkException;

/**
 *
 */

public abstract class BPMNEnd extends BPMNAction {

	private static final Logger logger = LoggerFactory.getLogger(BPMNEnd.class);

	@Override
	public Object execute(final Map<String, Object> context) {

		try {
			setProperty(statusText, "Process finished");

		} catch (FrameworkException fex) {
			logger.warn("Unable to execute action {} ({}): {}", getUuid(), getClass().getSimpleName(), fex.getMessage());
		}

		return null;
	}

	@Override
	public boolean next(final Object t) {
		return true;
	}
}
