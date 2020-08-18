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
import org.junit.platform.commons.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.structr.common.SecurityContext;
import org.structr.common.error.ErrorBuffer;
import org.structr.common.error.FrameworkException;
import org.structr.core.app.StructrApp;
import org.structr.core.property.PropertyKey;

/**
 *
 */

public abstract class BPMNCondition extends BPMNProcessStep<Object> {

	private static final Logger logger = LoggerFactory.getLogger(BPMNCondition.class);

	@Override
	public void onCreation(final SecurityContext securityContext, final ErrorBuffer errorBuffer) throws FrameworkException {

		// user actions are suspended by default since we are waiting for user interaction
		setProperty(statusText, "Executing condition action");
	}

	// method must be implemented by schema type
	public Object action(final SecurityContext securityContext, final Map<String, Object> parameters) throws FrameworkException {

		logger.info("BPMNCondition {}: no action specified.", getClass().getSimpleName());

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

	@Override
	public BPMNProcessStep getNextStep(final Object param) {

		try {

			if (param != null) {

				final String resultName   = param.toString();
				if (StringUtils.isNotBlank(resultName)) {

					final PropertyKey nextKey = StructrApp.getConfiguration().getPropertyKeyForJSONName(getClass(), resultName, false);

					if (nextKey != null) {

						final Class<BPMNProcessStep> nextType = nextKey.relatedType();
						if (nextType != null) {

							return StructrApp.getInstance(securityContext).create(nextType);
						}

					} else {

						setProperty(statusText, "No outgoing connection for '" + resultName + "'");
					}
				} else {

					setProperty(statusText, "Missing return value of action() method");
				}

			} else {

				setProperty(statusText, "Missing return value of action() method");
			}

		} catch (FrameworkException fex) {
			logger.warn("Unable to determine next step for {} ({}): {}", getUuid(), getClass().getSimpleName(), fex.getMessage());
		}

		return null;
	}
}
