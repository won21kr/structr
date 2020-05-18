/**
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
package org.structr.core.graph;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.structr.api.DatabaseService;
import org.structr.api.util.Iterables;
import org.structr.common.SecurityContext;
import org.structr.common.error.FrameworkException;
import org.structr.core.GraphObject;
import org.structr.core.app.StructrApp;
import org.structr.core.entity.AbstractRelationship;
import org.structr.core.property.PropertyKey;

/**
 * Sets the properties found in the property set on all nodes matching the type.
 * If no type property is found, set the properties on all nodes.
 */
public class BulkCopyRelationshipPropertyCommand extends NodeServiceCommand implements MaintenanceCommand {

	private static final Logger logger = LoggerFactory.getLogger(BulkCopyRelationshipPropertyCommand.class.getName());

	@Override
	public void execute(final Map<String, Object> map) throws FrameworkException {

		final DatabaseService graphDb        = (DatabaseService)arguments.get("graphDb");
		final RelationshipFactory relFactory = new RelationshipFactory(securityContext);

		final String sourceKey = (String)map.get("sourceKey");
		final String destKey   = (String)map.get("destKey");

		if(sourceKey == null || destKey == null) {

			throw new IllegalArgumentException("This command requires one argument of type Map. Map must contain values for 'sourceKey' and 'destKey'.");
		}

		if(graphDb != null) {

			final Iterable<AbstractRelationship> relIterator = Iterables.map(relFactory, graphDb.getAllRelationships());
			final long count = bulkGraphOperation(securityContext, relIterator, 1000, "CopyRelationshipProperties", new BulkGraphOperation<AbstractRelationship>() {

				@Override
				public boolean handleGraphObject(SecurityContext securityContext, AbstractRelationship rel) {

					// Treat only "our" rels
					if(rel.getProperty(GraphObject.id) != null) {

						Class type                    = rel.getClass();
						PropertyKey destPropertyKey   = StructrApp.getConfiguration().getPropertyKeyForDatabaseName(type, destKey);
						PropertyKey sourcePropertyKey = StructrApp.getConfiguration().getPropertyKeyForDatabaseName(type, sourceKey);

						try {
							// copy properties
							rel.setProperty(destPropertyKey, rel.getProperty(sourcePropertyKey));

						} catch (FrameworkException fex) {

							logger.warn("Unable to copy relationship property {} of relationship {} to {}: {}", new Object[] { sourcePropertyKey, rel.getUuid(), destPropertyKey, fex.getMessage() } );
						}
					}

					return true;
				}

				@Override
				public void handleThrowable(SecurityContext securityContext, Throwable t, AbstractRelationship rel) {
					logger.warn("Unable to copy relationship properties of relationship {}: {}", new Object[] { rel.getUuid(), t.getMessage() } );
				}

				@Override
				public void handleTransactionFailure(SecurityContext securityContext, Throwable t) {
					logger.warn("Unable to copy relationship properties: {}", t.getMessage() );
				}
			});

			logger.info("Finished setting properties on {} nodes", count);

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
