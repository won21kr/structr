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
package org.structr.bpmn;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.structr.api.service.Command;
import org.structr.api.service.RunnableService;
import org.structr.api.service.ServiceDependency;
import org.structr.api.service.StructrServices;
import org.structr.common.error.FrameworkException;
import org.structr.core.app.App;
import org.structr.core.app.StructrApp;
import org.structr.core.graph.Tx;
import org.structr.schema.SchemaService;
import org.structr.bpmn.model.BPMNProcessStep;
import org.structr.common.SecurityContext;

/**
 */
@ServiceDependency(SchemaService.class)
public class BPMNService extends Thread implements RunnableService {

	private static final Logger logger = LoggerFactory.getLogger(BPMNService.class);

	private long lastRun    = 0L;
	private boolean running = false;

	public BPMNService() {

		super("BPMNService");

		this.setDaemon(true);
	}

	@Override
	public void startService() throws Exception {
		logger.info("Starting..");
		start();
	}

	@Override
	public void stopService() {
		logger.info("Shutting down..");
		running = false;
	}

	@Override
	public boolean isRunning() {
		return running;
	}

	@Override
	public void injectArguments(final Command command) {
	}

	@Override
	public boolean initialize(final StructrServices services) throws ClassNotFoundException, InstantiationException, IllegalAccessException {
		return true;
	}

	@Override
	public void shutdown() {
	}

	@Override
	public void initialized() {
	}

	@Override
	public boolean isVital() {
		return false;
	}

	@Override
	public boolean waitAndRetry() {
		return false;
	}

	@Override
	public String getModuleName() {
		return "bpmn";
	}

	@Override
	public void run() {

		final SecurityContext securityContext = SecurityContext.getSuperUserInstance();

		securityContext.setAttribute("BPMNService", true);

		running = true;

		while (running) {

			for (final BPMNProcessStep step : fetchActiveSteps()) {

				try (final Tx tx = StructrApp.getInstance(securityContext).tx()) {

					if (step.canBeExecuted()) {

						final Object value = step.execute(new LinkedHashMap<>());

						// find and assign next step if possible
						if (step.next(value)) {

							step.finish();

						} else {

							// don't start this step again without user interaction
							step.suspend();

							logger.info("Step {} ({}) suspended because next step could not be determined.", step, step.getClass().getSimpleName());
						}

					} else {

						// don't start this step again without user interaction
						step.suspend();
					}

					tx.success();

				} catch (FrameworkException fex) {

					fex.printStackTrace();
				}
			}

			try { Thread.sleep(1000); } catch (Throwable t) {
				t.printStackTrace();
			}
		}
	}

	// ----- private methods -----
	private List<BPMNProcessStep> fetchActiveSteps() {

		final App app                     = StructrApp.getInstance();
		final List<BPMNProcessStep> steps = new LinkedList<>();

		try (final Tx tx = app.tx()) {

			final List<BPMNProcessStep> nodes = app.nodeQuery(BPMNProcessStep.class)

				.and(StructrApp.key(BPMNProcessStep.class, "isFinished"),  false)
				.and(StructrApp.key(BPMNProcessStep.class, "isSuspended"), false)
				.and()
					.or(BPMNProcessStep.dueDate, null)
					.orRange(BPMNProcessStep.dueDate, new Date(), null)

				.getAsList();

			steps.addAll(nodes);

			tx.success();

		} catch (FrameworkException fex) {
			fex.printStackTrace();
		}

		return steps;
	}
}
