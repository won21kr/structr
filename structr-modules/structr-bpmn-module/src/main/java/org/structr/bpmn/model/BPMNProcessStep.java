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

import java.util.Date;
import java.util.Map;
import org.structr.common.error.FrameworkException;
import org.structr.core.entity.AbstractNode;
import org.structr.core.property.BooleanProperty;
import org.structr.core.property.EndNode;
import org.structr.core.property.ISO8601DateProperty;
import org.structr.core.property.Property;
import org.structr.core.property.StartNode;

/**
 *
 */

public abstract class BPMNProcessStep<T> extends AbstractNode {

	public static final Property<BPMNProcessStep> nextStep     = new EndNode<>("nextStep", BPMNProcessStepNext.class);
	public static final Property<BPMNProcessStep> previousStep = new StartNode<>("previousStep", BPMNProcessStepNext.class);
	public static final Property<Date> dueDate                 = new ISO8601DateProperty("dueDate").indexed();
	public static final Property<Boolean> isFinished           = new BooleanProperty("isFinished").indexed();
	public static final Property<Boolean> isManual             = new BooleanProperty("isManual").indexed();
	public static final Property<Boolean> isPaused             = new BooleanProperty("isPaused").indexed().hint("This flag can be used to manually pause a process.");

	public abstract T execute(final Map<String, Object> context) throws FrameworkException;
	public abstract BPMNProcessStep getNextStep(final T t) throws FrameworkException;

	public void next(final T t) throws FrameworkException {

		final BPMNProcessStep nextStep = getNextStep(t);
		if (nextStep != null) {

			setProperty(BPMNProcessStep.nextStep, nextStep);
		}
	}

	public boolean isFinished() {
		return getProperty(isFinished);
	}

	public void finish() throws FrameworkException {
		setProperty(isFinished, true);
	}
}
