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
package org.structr.rest.resource;

import org.structr.common.error.FrameworkException;

/**
 * A resource constraint that implements the generic ability to be
 * combined with a ViewFilterResource in order to configure
 * a specific property view.
 *
 *
 */
public abstract class FilterableResource extends WrappingResource {

	@Override
	public Resource tryCombineWith(Resource next) throws FrameworkException {

		if (next instanceof ViewFilterResource) {
			((ViewFilterResource)next).wrapResource(this);
			return next;
		}

		return super.tryCombineWith(next);
	}
}
