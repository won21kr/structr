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
package org.structr.web.function;

import org.structr.common.error.ArgumentCountException;
import org.structr.common.error.ArgumentNullException;
import org.structr.common.error.FrameworkException;
import org.structr.core.Services;
import org.structr.schema.action.ActionContext;

public class ApplicationStoreSetAttributeFunction extends UiAdvancedFunction {

	public static final String ERROR_MESSAGE_APPLICATION_STORE_SET_ATTRIBUTE    = "Usage: ${application_store_set_attribute(key,value)}. Example: ${application_store_set_attribute(\"do_no_track\", true)}";
	public static final String ERROR_MESSAGE_APPLICATION_STORE_SET_ATTRIBUTE_JS = "Usage: ${{Structr.application_store_set_attribute(key,value)}}. Example: ${{Structr.application_store_set_attribute(\"do_not_track\", true)}}";


	@Override
	public String getName() {
		return "application_store_set_attribute";
	}

	@Override
	public String getSignature() {
		return "key, value";
	}

	@Override
	public Object apply(ActionContext ctx, Object caller, Object[] sources) throws FrameworkException {
		try {

			assertArrayHasLengthAndAllElementsNotNull(sources, 2);
			return Services.getInstance().applicationStoreSetAttribute(sources[0].toString(), sources[1]);
		} catch (ArgumentNullException pe) {

			// silently ignore null arguments
			return null;

		} catch (ArgumentCountException pe) {

			logParameterError(caller, sources, pe.getMessage(), ctx.isJavaScriptContext());
			return usage(ctx.isJavaScriptContext());
		}
	}

	@Override
	public String usage(boolean inJavaScriptContext) {
		return (inJavaScriptContext ? ERROR_MESSAGE_APPLICATION_STORE_SET_ATTRIBUTE_JS : ERROR_MESSAGE_APPLICATION_STORE_SET_ATTRIBUTE);
	}

	@Override
	public String shortDescription() {
		return "Stores a value in the application level store.";
	}




}