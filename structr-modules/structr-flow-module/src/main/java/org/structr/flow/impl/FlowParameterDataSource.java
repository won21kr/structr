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
import org.structr.core.property.EndNodes;
import org.structr.core.property.Property;
import org.structr.core.property.StringProperty;
import org.structr.flow.api.DataSource;
import org.structr.flow.engine.Context;
import org.structr.flow.engine.FlowException;
import org.structr.flow.impl.rels.FlowDataInput;
import org.structr.module.api.DeployableEntity;

import java.util.HashMap;
import java.util.Map;

public class FlowParameterDataSource extends FlowBaseNode implements DataSource, DeployableEntity {

	public static final Property<Iterable<FlowBaseNode>> dataTarget = new EndNodes<>("dataTarget", FlowDataInput.class);
	public static final Property<String> key = new StringProperty("key");

	public static final View defaultView = new View(FlowDataSource.class, PropertyView.Public, key, dataTarget);
	public static final View uiView = new View(FlowDataSource.class, PropertyView.Ui, key, dataTarget);

	@Override
	public Object get(Context context) throws FlowException {

		final String _key = getProperty(key);
		if (_key != null) {

			return context.getParameter(_key);
		}

		return null;
	}

	@Override
	public Map<String, Object> exportData() {

		final Map<String, Object> result = new HashMap<>();

		result.put("id", this.getUuid());
		result.put("type", this.getClass().getSimpleName());
		result.put("key", this.getProperty(key));
		result.put("visibleToPublicUsers", this.getProperty(visibleToPublicUsers));
		result.put("visibleToAuthenticatedUsers", this.getProperty(visibleToAuthenticatedUsers));

		return result;
	}
}
