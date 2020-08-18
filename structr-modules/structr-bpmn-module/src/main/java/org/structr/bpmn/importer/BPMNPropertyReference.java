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
package org.structr.bpmn.importer;

import java.util.Map;

/**
 */
public class BPMNPropertyReference {

	private Map<String, Object> properties = null;
	private String referenceName           = null;
	private String type                    = null;

	public BPMNPropertyReference(final String type, final Map<String, Object> data, final String referenceName) {

		this.referenceName = referenceName;
		this.properties    = data;
		this.type          = type;
	}

	public Map<String, Object> getData() {
		return properties;
	}

	public String getType() {
		return type;
	}

	public String getReferenceName() {
		return referenceName;
	}
}
