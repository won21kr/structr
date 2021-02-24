/*
 * Copyright (C) 2010-2021 Structr GmbH
 *
 * This file is part of Structr <http://structr.org>.
 *
 * Structr is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Structr is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Structr.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.structr.core.notion;

import java.util.Map;
import org.structr.core.GraphObject;
import org.structr.core.graph.NodeInterface;
import org.structr.core.property.PropertyKey;

/**
 * Combines a {@link PropertySetSerializationStrategy} and a {@link TypeAndPropertySetDeserializationStrategy}
 * to read/write a {@link GraphObject}.
 *
 *
 */
public class PropertySetNotion<S extends NodeInterface> extends Notion<S, Map<String, Object>> {

	public PropertySetNotion(PropertyKey... propertyKeys) {
		this(false, propertyKeys);
	}
	
	public PropertySetNotion(boolean createIfNotExisting, PropertyKey... propertyKeys) {
		this(
			new PropertySetSerializationStrategy(propertyKeys),
			new TypeAndPropertySetDeserializationStrategy(createIfNotExisting, propertyKeys)
		);

	}

	public PropertySetNotion(SerializationStrategy serializationStrategy, DeserializationStrategy deserializationStrategy) {
		super(serializationStrategy, deserializationStrategy);
	}

	@Override
	public PropertyKey getPrimaryPropertyKey() {
		return null; // this notion cannot deserialize objects from a single key
	}
}
