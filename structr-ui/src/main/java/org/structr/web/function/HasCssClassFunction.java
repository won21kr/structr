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
package org.structr.web.function;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.structr.common.error.ArgumentCountException;
import org.structr.common.error.ArgumentNullException;
import org.structr.common.error.FrameworkException;
import org.structr.core.app.StructrApp;
import org.structr.schema.action.ActionContext;
import org.structr.web.entity.dom.DOMElement;
import org.structr.web.entity.dom.DOMNode;

public class HasCssClassFunction extends UiAdvancedFunction {

	public static final String ERROR_MESSAGE_HAS_CSS_CLASS    = "Usage: ${has_css_class(element, css)}. Example: ${has_css_class(this, 'active')}";
	public static final String ERROR_MESSAGE_HAS_CSS_CLASS_JS = "Usage: ${{Structr.hasCssClass(element, css)}}. Example: ${{Structr.hasCssClass(this, 'active')}}";

	@Override
	public String getName() {
		return "has_css_class";
	}

	@Override
	public String getSignature() {
		return "element, css";
	}

	@Override
	public Object apply(final ActionContext ctx, final Object caller, final Object[] sources) throws FrameworkException {

		try {

			assertArrayHasMinLengthAndTypes(sources, 2, DOMNode.class, String.class);

			// we can safely cast here because of the assert.. above

			final DOMNode element       = (DOMNode)sources[0];
			final String css            = (String)sources[1];
			final String elementClasses = element.getProperty(StructrApp.key(DOMElement.class, "_html_class"));

			if (StringUtils.isNotBlank(css) && StringUtils.isNotBlank(elementClasses)) {

				final Set<String> elementParts = new LinkedHashSet<>(Arrays.asList(elementClasses.split(" ")));
				final Set<String> inputParts   = new LinkedHashSet<>(Arrays.asList(css.split(" ")));

				return elementParts.containsAll(inputParts);
			}

			return false;

		} catch (ArgumentNullException pe) {

			// silently ignore null arguments

		} catch (ArgumentCountException pe) {

			logParameterError(caller, sources, pe.getMessage(), ctx.isJavaScriptContext());
			return usage(ctx.isJavaScriptContext());
		}

		return null;
	}

	@Override
	public String usage(boolean inJavaScriptContext) {
		return (inJavaScriptContext ? ERROR_MESSAGE_HAS_CSS_CLASS_JS : ERROR_MESSAGE_HAS_CSS_CLASS);
	}

	@Override
	public String shortDescription() {
		return "Returns whether the given element has the given CSS class(es).";
	}
}
