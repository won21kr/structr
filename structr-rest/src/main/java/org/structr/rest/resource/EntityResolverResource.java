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

import java.util.Collection;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.structr.api.search.SortOrder;
import org.structr.api.util.ResultStream;
import org.structr.common.SecurityContext;
import org.structr.common.error.FrameworkException;
import org.structr.core.Value;
import org.structr.core.app.StructrApp;
import org.structr.core.entity.AbstractNode;
import org.structr.rest.RestMethodResult;
import org.structr.rest.exception.IllegalMethodException;

/**
 *
 *
 */
public class EntityResolverResource extends WrappingResource {

	@Override
	public boolean checkAndConfigure(String part, SecurityContext securityContext, HttpServletRequest request) {

		return true;
	}

	@Override
	public ResultStream doGet(final SortOrder sortOrder, int pageSize, int page) throws FrameworkException {
		throw new IllegalMethodException("GET not allowed on " + getResourceSignature());
	}

	@Override
	public RestMethodResult doPost(final Map<String, Object> propertySet) throws FrameworkException {

		final RestMethodResult result = new RestMethodResult(200);
		final Object src              = propertySet.get("ids");

		if (src != null && src instanceof Collection) {

			final Collection list = (Collection)src;
			for (final Object obj : list) {

				if (obj instanceof String) {

					AbstractNode node = (AbstractNode) StructrApp.getInstance().getNodeById((String)obj);
					if (node != null) {

						result.addContent(node);
					}
				}
			}

		} else {

			throw new FrameworkException(422, "Send a JSON object containing an array named 'ids' to use this endpoint.");
		}

		return result;
	}

	@Override
	public RestMethodResult doDelete() throws FrameworkException {
		throw new IllegalMethodException("DELETE not allowed on " + getResourceSignature());
	}

	@Override
	public RestMethodResult doPut(final Map<String, Object> propertySet) throws FrameworkException {
		throw new IllegalMethodException("PUT not allowed on " + getResourceSignature());
	}

        @Override
        public String getResourceSignature() {
                return getUriPart();
        }

	@Override
	public String getUriPart() {
		return "resolver";
	}

	@Override
	public void configurePropertyView(Value<String> propertyView) {
	}
}
