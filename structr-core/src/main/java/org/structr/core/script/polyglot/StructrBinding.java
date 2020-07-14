/*
 * Copyright (C) 2010-2020 Structr GmbH
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
package org.structr.core.script.polyglot;

import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.structr.common.CaseHelper;
import org.structr.common.error.FrameworkException;
import org.structr.common.error.ScriptingError;
import org.structr.core.GraphObject;
import org.structr.core.function.Functions;
import org.structr.core.script.Scripting;
import org.structr.core.script.polyglot.function.BatchFunction;
import org.structr.core.script.polyglot.function.DoPrivilegedFunction;
import org.structr.core.script.polyglot.function.IncludeJSFunction;
import org.structr.schema.action.ActionContext;
import org.structr.schema.action.Function;

import java.sql.Struct;
import java.util.Arrays;
import java.util.Set;

import static org.structr.core.script.polyglot.PolyglotWrapper.wrap;

public class StructrBinding implements ProxyObject {
	private final GraphObject entity;
	private final ActionContext actionContext;

	public StructrBinding(final ActionContext actionContext, final GraphObject entity) {

		this.actionContext = actionContext;
		this.entity        = entity;
	}

	@Override
	public Object getMember(String name) {

		switch (name) {
			case "get":
				return getGetFunctionWrapper();
			case "this":
				return wrap(actionContext, entity);
			case "me":
				return wrap(actionContext,actionContext.getSecurityContext().getUser(false));
			case "predicate":
				return new PredicateBinding(actionContext, entity);
			case "batch":
				return new BatchFunction(actionContext);
			case "includeJs":
				return new IncludeJSFunction(actionContext);
			case "doPrivileged":
				return new DoPrivilegedFunction(actionContext);
			default:
				if (actionContext.getConstant(name) != null) {
					return wrap(actionContext,actionContext.getConstant(name));
				}

				if (actionContext.getAllVariables().containsKey(name)) {
					return wrap(actionContext, actionContext.getAllVariables().get(name));
				}

				Function<Object, Object> func = Functions.get(CaseHelper.toUnderscore(name, false));
				if (func != null) {

					return new FunctionWrapper(actionContext, entity, func);
				}

				return null;
		}
	}

	@Override
	public Object getMemberKeys() {
		Set<String> keys = actionContext.getAllVariables().keySet();
		keys.add("this");
		keys.add("me");
		keys.add("predicate");
		keys.add("batch");
		keys.add("includeJs");
		keys.add("doPrivileged");
		return keys;
	}

	@Override
	public boolean hasMember(String key) {
		return getMember(key) != null;
	}

	@Override
	public void putMember(String key, Value value) {

	}

	private ProxyExecutable getGetFunctionWrapper() {

		return arguments -> {

			try {
				Object[] args = Arrays.stream(arguments).map(arg -> PolyglotWrapper.unwrap(actionContext, arg)).toArray();

				if (args.length == 1) {

					return PolyglotWrapper.wrap(actionContext, actionContext.evaluate(entity, args[0].toString(), null, null, 0));
				} else if (args.length > 1) {

					final Function<Object, Object> function = Functions.get("get");

					return wrap(actionContext, function.apply(actionContext, entity, args));
				}

			} catch (FrameworkException ex) {

				actionContext.raiseError(422, new ScriptingError(ex));
			}

			return null;
		};
	}

}