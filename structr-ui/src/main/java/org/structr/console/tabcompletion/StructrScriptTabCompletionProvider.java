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
package org.structr.console.tabcompletion;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.structr.common.SecurityContext;
import org.structr.core.function.Functions;

/**
 *
 */
public class StructrScriptTabCompletionProvider extends AbstractTabCompletionProvider {

	private final Set<String> words = new TreeSet<>();

	public StructrScriptTabCompletionProvider() {

		words.add("me");
		words.add("page");
		words.add("request");
		words.add("this");
	}


	@Override
	public List<TabCompletionResult> getTabCompletion(final SecurityContext securityContext, final String line) {

		final List<TabCompletionResult> results = new LinkedList<>();
		final String token                      = getToken(line, " ()[]{}=+-,.*#/&$!");

		results.addAll(getExactResultsForCollection(Functions.getNames(), token, "("));
		results.addAll(getExactResultsForCollection(words, token, ""));

		Collections.sort(results);

		return results;
	}
}
