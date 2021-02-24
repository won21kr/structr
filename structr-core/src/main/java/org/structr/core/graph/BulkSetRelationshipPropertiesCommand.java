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
package org.structr.core.graph;

import java.util.Map;
import java.util.Map.Entry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.structr.api.DatabaseService;
import org.structr.api.util.Iterables;
import org.structr.common.SecurityContext;
import org.structr.common.error.FrameworkException;
import org.structr.core.app.StructrApp;
import org.structr.core.entity.AbstractRelationship;
import org.structr.core.property.PropertyKey;

/**
 * Sets the properties found in the property set on all relationships matching the type.
 * If no type property is found, set the properties on all relationships.
 */
public class BulkSetRelationshipPropertiesCommand extends NodeServiceCommand implements MaintenanceCommand {

	private static final Logger logger = LoggerFactory.getLogger(BulkSetRelationshipPropertiesCommand.class.getName());

	@Override
	public void execute(final Map<String, Object> properties) throws FrameworkException {

		final DatabaseService graphDb            = (DatabaseService) arguments.get("graphDb");
		final RelationshipFactory relationshipFactory = new RelationshipFactory(securityContext);

		if (graphDb != null) {

			Iterable<AbstractRelationship> relationships = null;
			final String typeName                        = "type";

			if (properties.containsKey(typeName)) {

				relationships = Iterables.map(relationshipFactory, graphDb.getRelationshipsByType(typeName));
				properties.remove(typeName);

			} else {

				relationships = Iterables.map(relationshipFactory, graphDb.getAllRelationships());
			}

			final long count = bulkGraphOperation(securityContext, relationships, 1000, "SetRelationshipProperties", new BulkGraphOperation<AbstractRelationship>() {

				@Override
				public boolean handleGraphObject(SecurityContext securityContext, AbstractRelationship rel) {

					// Treat only "our" nodes
					if (rel.getProperty(AbstractRelationship.id) != null) {

						for (Entry entry : properties.entrySet()) {

							String key = (String) entry.getKey();
							Object val = entry.getValue();

							PropertyKey propertyKey = StructrApp.getConfiguration().getPropertyKeyForDatabaseName(rel.getClass(), key);
							if (propertyKey != null) {

								try {

									rel.setProperty(propertyKey, val);

								} catch (FrameworkException fex) {

									logger.warn("Unable to set relationship property {} of relationship {} to {}: {}", new Object[] { propertyKey, rel.getUuid(), val, fex.getMessage() } );
								}
							}
						}
					}

					return true;
				}

				@Override
				public void handleThrowable(SecurityContext securityContext, Throwable t, AbstractRelationship rel) {
					logger.warn("Unable to set properties of relationship {}: {}", new Object[] { rel.getUuid(), t.getMessage() } );
				}

				@Override
				public void handleTransactionFailure(SecurityContext securityContext, Throwable t) {
					logger.warn("Unable to set relationship properties: {}", t.getMessage() );
				}
			});

			logger.info("Finished setting properties on {} relationships", count);
		}
	}

	@Override
	public boolean requiresEnclosingTransaction() {
		return false;
	}

	@Override
	public boolean requiresFlushingOfCaches() {
		return false;
	}
}
