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
package org.structr.bolt;

import java.util.Iterator;
import org.neo4j.driver.Record;
import org.neo4j.driver.internal.shaded.reactor.core.publisher.Flux;
import org.structr.bolt.reactive.PaginatedQueueingFlux;
import org.structr.bolt.reactive.QueueingFlux;
import org.structr.bolt.reactive.StructrFlux;

/**
 */
public class QueryIterable implements Iterable<Record> {

	private BoltDatabaseService db    = null;
	private AdvancedCypherQuery query = null;

	public QueryIterable(final BoltDatabaseService db, final AdvancedCypherQuery query) {
		this.query = query;
		this.db    = db;
	}

	@Override
	public Iterator<Record> iterator() {

		final SessionTransaction tx = db.getCurrentTransaction();

		final StructrFlux flux = new PaginatedQueueingFlux(this.db, query);
		flux.initialize();
		return flux.iterator();

		//final Flux<Record> flux             = tx.collectRecords(query.getStatement(false), query.getParameters());

		//return flux.toIterable().iterator();
	}
}