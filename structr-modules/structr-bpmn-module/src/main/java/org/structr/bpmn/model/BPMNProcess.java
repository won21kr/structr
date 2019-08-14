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

import org.structr.bpmn.model.property.BPMNInfoProperty;
import org.structr.bpmn.model.property.BPMNDataProperty;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.structr.bpmn.model.property.BPMNStepsProperty;
import org.structr.common.PropertyView;
import org.structr.common.SecurityContext;
import org.structr.common.View;
import org.structr.common.error.ErrorBuffer;
import org.structr.common.error.FrameworkException;
import org.structr.core.app.StructrApp;
import org.structr.core.entity.SchemaNode;
import org.structr.core.property.Property;
import org.structr.core.property.PropertyKey;
import org.structr.core.property.PropertyMap;
import org.structr.core.property.StringProperty;

/**
 * The "head" of a BPMN process. A BPMNPRocess instance bootstraps the BPMN process
 * and provides control and status functions for the running process.
 */

public abstract class BPMNProcess extends BPMNProcessStep<Object> {

	private static final Logger logger = LoggerFactory.getLogger(BPMNProcess.class);

	public static final Property<Object> steps = new BPMNStepsProperty("steps");
	public static final Property<Object> info  = new BPMNInfoProperty("info");
	public static final Property<Object> data  = new BPMNDataProperty("data");
	public static final Property<String> state = new StringProperty("state");

	public static final View publicView = new View(BPMNProcess.class, PropertyView.Public,
		info
	);

	@Override
	public Object execute(final Map<String, Object> context) {
		return null;
	}

	@Override
	public void onCreation(final SecurityContext securityContext, final ErrorBuffer errorBuffer) throws FrameworkException {

		super.onCreation(securityContext, errorBuffer);

		// allow creation of process step from here
		securityContext.setAttribute("BPMNService", true);

		// finish this step (next will be created below)
		setProperty(isFinished, true);
		setProperty(statusText, "Process not started yet");

		// create next step
		next(null);
	}

	@Override
	public BPMNProcessStep getNextStep(final Object data) {

		try {

			final PropertyKey nextKey = StructrApp.getConfiguration().getPropertyKeyForJSONName(getClass(), "next", false);
			if (nextKey != null) {

				final Class<BPMNProcessStep> nextType = nextKey.relatedType();
				if (nextType != null) {

					return StructrApp.getInstance().create(nextType, getDataForNextStep(nextType));
				}
			}

		} catch (FrameworkException fex) {
			logger.warn("Unable to determine next step for {} ({}): {}", getUuid(), getClass().getSimpleName(), fex.getMessage());
		}

		return null;
	}

	public Map<String, Object> getStatus() throws FrameworkException {

		final BPMNProcessInfo info        = new BPMNProcessInfo(null);
		final BPMNProcessStep currentStep = findCurrentStep(info);
		final Map<String, Object> status  = new LinkedHashMap<>();

		status.put("currentStep",   currentStep);
		status.put("type",          getProcessName());
		status.put("status",        currentStep.getStatusText());
		status.put("finished",      currentStep instanceof BPMNEnd);  // isFinished != process finished
		status.put("suspended",     currentStep.isSuspended());
		status.put("canBeExecuted", currentStep.canBeExecuted());
		status.put("steps",         info.getNumberOfSteps());

		return status;
	}

	public List<BPMNProcessStep> getSteps() {

		final List<BPMNProcessStep> steps = new LinkedList<>();
		final BPMNProcessInfo info        = new BPMNProcessInfo(steps);

		findCurrentStep(info);

		return steps;
	}

	// ----- private methods -----
	private PropertyMap getDataForNextStep(final Class type) throws FrameworkException {

		final Map<String, Object> storage = getTemporaryStorage();
		final Map<String, Object> data    = (Map)storage.get("data");

		// remove temporary data
		storage.remove("data");

		return PropertyMap.inputTypeToJavaType(securityContext, type, data);
	}

	private BPMNProcessStep findCurrentStep(final BPMNProcessInfo info) {

		BPMNProcessStep current = this;
		BPMNProcessStep next    = null;
		int count               = 0;

		do {

			next = current.getProperty(nextStep);
			if (next != null) {

				current = next;

				info.countStep(current);
			}

			// prevent endless loop
			if (count++ > 10000) {
				logger.warn("Process step chain is longer than 10000, this is most likely an error. Aborting.");
				return null;
			}

		} while (next != null);

		return current;
	}

	private String getProcessName() throws FrameworkException {

		final SchemaNode schemaNode = StructrApp.getInstance().nodeQuery(SchemaNode.class).andName(getClass().getSimpleName()).getFirst();
		if (schemaNode != null) {

			final String processName = schemaNode.getProperty(SchemaNode.category);
			if (processName != null) {

				return processName;

			} else {

				return "Unknown, schema type does not contain process name.";
			}
		}

		return "Unknown, schema type does not exist.";
	}

	// ----- nested classes -----
	private class BPMNProcessInfo {

		private List<BPMNProcessStep> steps = null;
		private int numberOfSteps           = 0;

		public BPMNProcessInfo(final List<BPMNProcessStep> steps) {
			this.steps = steps;
		}

		public void countStep(final BPMNProcessStep step) {

			numberOfSteps++;

			if (steps != null) {
				steps.add(step);
			}
		}

		public int getNumberOfSteps() {
			return numberOfSteps;
		}
	}
}
