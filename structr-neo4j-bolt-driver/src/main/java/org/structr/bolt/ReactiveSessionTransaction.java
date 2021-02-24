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
import java.util.Map;

import org.neo4j.driver.*;
import org.neo4j.driver.exceptions.ClientException;
import org.neo4j.driver.exceptions.DatabaseException;
import org.neo4j.driver.exceptions.NoSuchRecordException;
import org.neo4j.driver.exceptions.ServiceUnavailableException;
import org.neo4j.driver.exceptions.TransientException;
import org.neo4j.driver.internal.shaded.reactor.core.publisher.Flux;
import org.neo4j.driver.internal.shaded.reactor.core.publisher.Mono;
import org.neo4j.driver.reactive.RxResult;
import org.neo4j.driver.reactive.RxSession;
import org.neo4j.driver.reactive.RxTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.structr.api.NetworkException;
import org.structr.api.NotFoundException;
import org.structr.api.RetryException;
import org.structr.api.util.Iterables;
import org.structr.bolt.reactive.StructrFlux;
import org.structr.bolt.reactive.StructrMono;

/**
 *
 */
class ReactiveSessionTransaction extends SessionTransaction {

	private static final Logger logger                = LoggerFactory.getLogger(ReactiveSessionTransaction.class);
	private RxSession session                         = null;
	private RxTransaction tx                          = null;

	public ReactiveSessionTransaction(final BoltDatabaseService db, final RxSession session) {

		this.transactionId = ID_SOURCE.getAndIncrement();
		this.session       = session;
		this.tx            = Flux.from(session.beginTransaction(db.getTransactionConfig(transactionId))).blockFirst();
		this.db            = db;
	}

	public ReactiveSessionTransaction(final BoltDatabaseService db, final RxSession session, final int timeoutInSeconds) {

		final TransactionConfig config = db.getTransactionConfigForTimeout(timeoutInSeconds, transactionId);

		this.transactionId = ID_SOURCE.getAndIncrement();
		this.session       = session;
		this.tx            = Flux.from(session.beginTransaction(config)).blockFirst();
		this.db            = db;
	}

	@Override
	public void failure() {}

	@Override
	public void success() {

		// transaction must be marked successfull explicitly
		success = true;
	}

	@Override
	public void close() {

		if (!success) {

			Mono.from(tx.rollback()).block();

			for (final EntityWrapper entity : accessedEntities) {

				entity.rollback(transactionKey);
				entity.removeFromCache();
			}

			for (final EntityWrapper entity : modifiedEntities) {
				entity.stale();
			}

		} else {

			Mono.from(tx.commit()).block();

			RelationshipWrapper.expunge(deletedRels);
			NodeWrapper.expunge(deletedNodes);

			for (final EntityWrapper entity : accessedEntities) {
				entity.commit(transactionKey);
			}

			for (final EntityWrapper entity : modifiedEntities) {
				entity.clearCaches();
			}
		}

		// mark this transaction as closed BEFORE trying to actually close it
		// so that it is closed in case of a failure
		closed = true;

		try {

			session.close();

		} catch (TransientException tex) {

			// transient exceptions can be retried
			throw new RetryException(tex);

		} finally {

			// Notify all nodes that are modified in this transaction
			// so that the relationship caches are rebuilt.
			for (final EntityWrapper entity : modifiedEntities) {
				entity.onClose();
			}

			// make sure that the resources are freed
			session.close();
		}
	}



	@Override
	public Value firstTransactionValue(final String statement, final Map<String, Object> map, boolean eager) {

		final RxResult result = tx.run(statement, map);
		final Value value = Mono.from(result.records()).block().get(0);

		if(eager) {

			// touch result to force evaluation
			Mono.from(result.consume()).block();

		}

		return value;

	}

	@Override
	public Value singleTransactionValue(final String statement, final Map<String, Object> map, boolean eager) {

		return firstTransactionValue(statement, map, eager);

	}

	@Override
	public void collectRecord(String statement, Map<String, Object> map, StructrMono mono) {

		try {

			logQuery(statement, map);
			//return new ReactiveFluxWrapper(Flux.from(tx.run(statement, map).records()));

		} catch (TransientException tex) {
			closed = true;
			throw new RetryException(tex);
		} catch (NoSuchRecordException nex) {
			throw new NotFoundException(nex);
		} catch (ServiceUnavailableException ex) {
			throw new NetworkException(ex.getMessage(), ex);
		} catch (DatabaseException dex) {
			throw ReactiveSessionTransaction.translateDatabaseException(dex);
		} catch (ClientException cex) {
			throw ReactiveSessionTransaction.translateClientException(cex);
		}

	}

	@Override
	public void collectRecords(final String statement, final Map<String, Object> map, StructrFlux flux) {

		try {

			logQuery(statement, map);
			//return new ReactiveFluxWrapper(Flux.from(tx.run(statement, map).records()));

		} catch (TransientException tex) {
			closed = true;
			throw new RetryException(tex);
		} catch (NoSuchRecordException nex) {
			throw new NotFoundException(nex);
		} catch (ServiceUnavailableException ex) {
			throw new NetworkException(ex.getMessage(), ex);
		} catch (DatabaseException dex) {
			throw ReactiveSessionTransaction.translateDatabaseException(dex);
		} catch (ClientException cex) {
			throw ReactiveSessionTransaction.translateClientException(cex);
		}

	}

	@Override
	public Iterable<Map<String, Object>> run(final String statement, final Map<String, Object> map) {

		try {

			logQuery(statement, map);

			final Iterable<Map<String, Object>> iterable = Iterables.map(new RecordMapMapper(db), Flux.from(tx.run(statement, map).records()).toIterable());

			// TODO: fix me, currently needed to execute queries who's result is not read
			final Iterator<Map<String, Object>> iterator = iterable.iterator();
			iterator.hasNext();

			return iterable;

		} catch (TransientException tex) {
			closed = true;
			throw new RetryException(tex);
		} catch (NoSuchRecordException nex) {
			throw new NotFoundException(nex);
		} catch (ServiceUnavailableException ex) {
			throw new NetworkException(ex.getMessage(), ex);
		} catch (DatabaseException dex) {
			throw ReactiveSessionTransaction.translateDatabaseException(dex);
		} catch (ClientException cex) {
			throw ReactiveSessionTransaction.translateClientException(cex);
		}
	}

	@Override
	public void set(final String statement, final Map<String, Object> map) {

		try {

			logQuery(statement, map);
			Mono.from(tx.run(statement, map).consume()).block();

		} catch (TransientException tex) {
			closed = true;
			throw new RetryException(tex);
		} catch (NoSuchRecordException nex) {
			throw new NotFoundException(nex);
		} catch (ServiceUnavailableException ex) {
			throw new NetworkException(ex.getMessage(), ex);
		} catch (DatabaseException dex) {
			throw ReactiveSessionTransaction.translateDatabaseException(dex);
		} catch (ClientException cex) {
			throw ReactiveSessionTransaction.translateClientException(cex);
		}
	}

	@Override
	public void logQuery(final String statement, final Map<String, Object> map) {

		if (db.logQueries()) {

			if (!isPing || db.logPingQueries()) {

				if (map != null && map.size() > 0) {

					if (statement.contains("extractedContent")) {
						logger.info("{}: {}\t\t SET on extractedContent - value suppressed", Thread.currentThread().getId(), statement);
					} else {
						logger.info("{}: {}\t\t Parameters: {}", Thread.currentThread().getId(), statement, map.toString());
					}

				} else {

					logger.info("{}: {}", Thread.currentThread().getId(), statement);
				}
			}
		}
	}

}
