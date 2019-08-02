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
import org.structr.common.SecurityContext;
import org.structr.common.error.ErrorBuffer;
import org.structr.common.error.FrameworkException;
import org.structr.core.app.StructrApp;
import org.structr.core.property.PropertyKey;

/**
 *
 */

public abstract class BPMNUserInputAction extends BPMNProcessStep<Object> {

	@Override
	public void onCreation(final SecurityContext securityContext, final ErrorBuffer errorBuffer) throws FrameworkException {

		// user actions are suspended by default since we are waiting for user interaction
		setProperty(isSuspended, true);
	}


	@Override
	public Object execute(final Map<String, Object> context) throws FrameworkException {
		return null;
	}

	@Override
	public BPMNProcessStep getNextStep(final Object data) throws FrameworkException {

		final PropertyKey nextKey = StructrApp.getConfiguration().getPropertyKeyForJSONName(getClass(), "next", false);
		if (nextKey != null) {

			final Class<BPMNProcessStep> nextType = nextKey.relatedType();
			if (nextType != null) {

				return StructrApp.getInstance(securityContext).create(nextType);
			}
		}

		return null;
	}
}
