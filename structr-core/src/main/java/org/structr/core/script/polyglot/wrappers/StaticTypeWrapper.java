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
package org.structr.core.script.polyglot.wrappers;

import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.structr.api.util.Iterables;
import org.structr.common.error.FrameworkException;
import org.structr.core.app.App;
import org.structr.core.app.StructrApp;
import org.structr.core.entity.SchemaMethod;
import org.structr.core.entity.SchemaNode;
import org.structr.core.graph.Tx;
import org.structr.core.script.polyglot.PolyglotWrapper;
import org.structr.core.script.polyglot.cache.ExecutableStaticTypeMethodCache;
import org.structr.schema.action.ActionContext;
import org.structr.schema.action.Actions;

import java.util.HashMap;
import java.util.Map;

public class StaticTypeWrapper implements ProxyObject {
	private final static Logger logger = LoggerFactory.getLogger(StaticTypeWrapper.class);
	private final App app;
	private final Class referencedClass;
	private final ActionContext actionContext;

	public StaticTypeWrapper(final ActionContext actionContext, final Class referencedClass) {

		this.actionContext = actionContext;
		this.app = StructrApp.getInstance();
		this.referencedClass = referencedClass;
	}


	@Override
	public Object getMember(String key) {

		// Try to lookup cached executable before initializing a new one
		ExecutableStaticTypeMethodCache staticMethodCache = actionContext.getStaticExecutableTypeMethodCache();

		ProxyExecutable cachedStaticExecutable = staticMethodCache.getExecutable(referencedClass.getSimpleName(), key);
		if (cachedStaticExecutable != null) {

			return cachedStaticExecutable;
		}

		// Ensure that  method is static
		try (final Tx tx = app.tx()) {

			SchemaNode schemaNode = app.nodeQuery(SchemaNode.class).andName(referencedClass.getSimpleName()).getFirst();
			if (schemaNode != null) {

				for (SchemaMethod m : Iterables.toList(schemaNode.getSchemaMethodsIncludingInheritance())) {

					if (m.getName().equals(key) && m.isStaticMethod()) {

						ProxyExecutable executable = arguments -> {

							try {
								Map<String, Object> parameters = new HashMap<>();

								if (arguments.length == 1) {

									Object parameter = PolyglotWrapper.unwrap(actionContext, arguments[0]);
									if (parameter instanceof Map) {

										parameters = (Map<String, Object>)parameter;
									}
								}

								return PolyglotWrapper.wrap(actionContext, Actions.execute(actionContext.getSecurityContext(), null, SchemaMethod.getCachedSourceCode(m.getUuid()), parameters, referencedClass.getSimpleName() + "." + key, m.getUuid()));
								
							} catch (FrameworkException ex) {

								throw new RuntimeException(ex);
							}

						};

						staticMethodCache.cacheExecutable(referencedClass.getSimpleName(), key, executable);

						return executable;
					}
				}

			}

		} catch (FrameworkException ex) {

			logger.warn("Unexpected exception while trying to look up schema method.", ex);
			return null;
		}

		return null;
	}

	@Override
	public Object getMemberKeys() {
		return null;
	}

	@Override
	public boolean hasMember(String key) {
		return true;
	}

	@Override
	public void putMember(String key, Value value) {
	}
}
