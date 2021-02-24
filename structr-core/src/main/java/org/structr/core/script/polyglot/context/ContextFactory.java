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
package org.structr.core.script.polyglot.context;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.structr.common.error.FrameworkException;
import org.structr.core.GraphObject;
import org.structr.core.script.polyglot.AccessProvider;
import org.structr.core.script.polyglot.StructrBinding;
import org.structr.schema.action.ActionContext;

import java.util.concurrent.Callable;
import java.util.function.Predicate;

public abstract class ContextFactory {

	private static final Engine engine = Engine.create();

	public static Context getContext(final String language) throws FrameworkException {

		return getContext(language, null, null);
	}

	public static Context getContext(final String language, final ActionContext actionContext, final GraphObject entity) throws FrameworkException {

		switch (language) {
			case "js":
				return getAndUpdateContext(language, actionContext, entity, ()->buildJSContext(actionContext, entity));
			case "python":
			case "R":
				return getAndUpdateContext(language, actionContext, entity, ()->buildGenericContext(language, actionContext, entity));
			default:
				throw new FrameworkException(500, "Could not initialize context for language: " + language);
		}
	}

	private static Context getAndUpdateContext(final String language, final ActionContext actionContext, final GraphObject entity, final Callable<Context> contextCreationFunc) throws FrameworkException {

		Context storedContext = actionContext != null ? actionContext.getScriptingContext(language) : null;

		if (actionContext != null && storedContext != null) {

			storedContext = updateBindings(storedContext, language, actionContext, entity);
			actionContext.putScriptingContext(language, storedContext);
		} else {

			try {

				storedContext = contextCreationFunc.call();
				actionContext.putScriptingContext(language, storedContext);
			} catch (Exception ex) {

				throw new FrameworkException(500, "Exception while trying to initialize new context for language: " + language + ". Cause: " + ex.getMessage());
			}
		}

		return  storedContext;
	}

	private static Context buildJSContext(final ActionContext actionContext, final GraphObject entity) {

		final Context context = Context.newBuilder("js")
				.engine(engine)
				.allowPolyglotAccess(AccessProvider.getPolyglotAccessConfig())
				.allowHostAccess(AccessProvider.getHostAccessConfig())
				// TODO: Add config switch to toggle Host Class Lookup
				.allowHostClassLookup(new StructrClassPredicate())
				// TODO: Add configurable chrome debug
				//.option("inspect", "4242")
				//.option("inspect.Path", "/structr/scripting/remotedebugger/" + java.util.UUID.randomUUID().toString())
				.allowExperimentalOptions(true)
				.option("js.foreign-object-prototype", "true")
				.option("js.nashorn-compat", "true")
				.option("js.ecmascript-version", "2021")
				.build();

		return updateBindings(context, "js", actionContext, entity);
	}

	private static Context buildGenericContext(final String language, final ActionContext actionContext, final GraphObject entity) {

		final Context context = Context.newBuilder()
				.engine(engine)
				.allowAllAccess(true)
				.allowHostAccess(AccessProvider.getHostAccessConfig())
				.allowHostClassLookup(new StructrClassPredicate())
				.build();

		return updateBindings(context, language, actionContext, entity);
	}

	private static Context updateBindings(final Context context, final String language, final ActionContext actionContext, final GraphObject entity) {
		final StructrBinding structrBinding = new StructrBinding(actionContext, entity);

		context.getBindings(language).putMember("Structr", structrBinding);
		if (!language.equals("python") && !language.equals("R")) {
			context.getBindings(language).putMember("$", structrBinding);
		}

		return context;
	}

	private static class StructrClassPredicate implements Predicate<String> {
		// Allows manually selected Structr classes to be accessed from scripting contexts

		@Override
		public boolean test(String s) {
			return s.startsWith("org.structr.api.config.Settings");
		}
	}
}
