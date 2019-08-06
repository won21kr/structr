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

import java.util.LinkedHashMap;
import java.util.Map;
import org.structr.common.PropertyView;
import org.structr.common.SecurityContext;
import org.structr.common.View;
import org.structr.common.error.ErrorBuffer;
import org.structr.common.error.FrameworkException;
import org.structr.core.app.StructrApp;
import org.structr.core.property.Property;
import org.structr.core.property.PropertyKey;
import org.structr.core.property.PropertyMap;

/**
 * The start of a BPMN process. A BPMNStart instance bootstraps the BPMN process,
 * creates an instance of the first process task and provides input data for it.
 * If the process can be started successfully, the BPMNStart instance sets
 * isFinished to true to avoid repeated execution.
 */

public abstract class BPMNProcess extends BPMNProcessStep<Object> {

	public static final Property<Object> status = new BPMNStatusProperty("status");
	public static final Property<Object> data   = new BPMNDataProperty("data");

	public static final View publicView = new View(BPMNProcess.class, PropertyView.Public,
		status
	);

	@Override
	public Object execute(final Map<String, Object> context) throws FrameworkException {
		return null;
	}

	@Override
	public void onCreation(final SecurityContext securityContext, final ErrorBuffer errorBuffer) throws FrameworkException {

		// allow creation of process step from here
		securityContext.setAttribute("BPMNService", true);

		// finish this step (next will be created below)
		setProperty(isFinished, true);

		// create next step
		next(null);
	}

	@Override
	public BPMNProcessStep getNextStep(final Object data) throws FrameworkException {

		final PropertyKey nextKey = StructrApp.getConfiguration().getPropertyKeyForJSONName(getClass(), "next", false);
		if (nextKey != null) {

			final Class<BPMNProcessStep> nextType = nextKey.relatedType();
			if (nextType != null) {

				return StructrApp.getInstance().create(nextType, getDataForNextStep(nextType));
			}
		}

		return null;
	}

	public Map<String, Object> getStatus() {

		final BPMNProcessStep currentStep = findCurrentStep();
		final Map<String, Object> status  = new LinkedHashMap<>();

		status.put("currentStep", currentStep);
		status.put("status",      currentStep.getStatusText());
		status.put("finished",    currentStep instanceof BPMNEnd);  // isFinished != process finished
		status.put("suspended",   currentStep.isSuspended());

		return status;
	}

	// ----- private methods -----
	private PropertyMap getDataForNextStep(final Class type) throws FrameworkException {

		final Map<String, Object> storage = getTemporaryStorage();
		final Map<String, Object> data    = (Map)storage.get("data");

		// remove temporary data
		storage.remove("data");

		return PropertyMap.inputTypeToJavaType(securityContext, type, data);
	}

	private BPMNProcessStep findCurrentStep() {

		BPMNProcessStep current = this;
		BPMNProcessStep next    = null;


		do {

			next = current.getProperty(nextStep);
			if (next != null) {

				current = next;
			}

		} while (next != null);

		return current;
	}

}
