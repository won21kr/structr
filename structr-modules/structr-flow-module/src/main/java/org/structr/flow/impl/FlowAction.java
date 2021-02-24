/*
 * Copyright (C) 2010-2021 Structr GmbH
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
package org.structr.flow.impl;

import org.structr.common.PropertyView;
import org.structr.common.View;
import org.structr.common.error.FrameworkException;
import org.structr.core.property.*;
import org.structr.core.script.Scripting;
import org.structr.flow.api.DataSource;
import org.structr.flow.api.ThrowingElement;
import org.structr.flow.engine.Context;
import org.structr.flow.engine.FlowException;
import org.structr.flow.impl.rels.FlowDataInput;
import org.structr.flow.impl.rels.FlowExceptionHandlerNodes;
import org.structr.module.api.DeployableEntity;

import java.util.HashMap;
import java.util.Map;

public class FlowAction extends FlowActionNode implements DataSource, DeployableEntity, ThrowingElement {

	public static final Property<DataSource> dataSource                 = new StartNode<>("dataSource", FlowDataInput.class);
	public static final Property<Iterable<FlowBaseNode>> dataTarget     = new EndNodes<>("dataTarget", FlowDataInput.class);
	public static final Property<FlowExceptionHandler> exceptionHandler = new EndNode<>("exceptionHandler", FlowExceptionHandlerNodes.class);
	public static final Property<String> script                         = new StringProperty("script");

	public static final View defaultView = new View(FlowAction.class, PropertyView.Public, script, dataSource, dataTarget, exceptionHandler, isStartNodeOfContainer);
	public static final View uiView      = new View(FlowAction.class, PropertyView.Ui,     script, dataSource, dataTarget, exceptionHandler, isStartNodeOfContainer);

	@Override
	public void execute(final Context context) throws FlowException {

		final String _script = getProperty(script);
		if (_script != null) {

			try {

				final DataSource _dataSource = getProperty(FlowAction.dataSource);

				// make data available to action if present
				if (_dataSource != null) {
					context.setData(getUuid(), _dataSource.get(context));
				}

				// Evaluate script and write result to context
				Object result = Scripting.evaluate(context.getActionContext(securityContext, this), this, "${" + _script.trim() + "}", "FlowAction(" + getUuid() + ")");
				context.setData(getUuid(), result);

			} catch (FrameworkException fex) {

				throw new FlowException(fex, this);
			}
		}

	}

	@Override
	public Object get(Context context) throws FlowException {
		if (!context.hasData(getUuid())) {
			this.execute(context);
		}
		return context.getData(getUuid());
	}

	@Override
	public FlowExceptionHandler getExceptionHandler(Context context) {
		return getProperty(exceptionHandler);
	}

	@Override
	public Map<String, Object> exportData() {
		
		Map<String, Object> result = new HashMap<>();

		result.put("id", this.getUuid());
		result.put("type", this.getClass().getSimpleName());
		result.put("script", this.getProperty(script));

		result.put("visibleToPublicUsers", this.getProperty(visibleToPublicUsers));
		result.put("visibleToAuthenticatedUsers", this.getProperty(visibleToAuthenticatedUsers));

		return result;
	}

}
