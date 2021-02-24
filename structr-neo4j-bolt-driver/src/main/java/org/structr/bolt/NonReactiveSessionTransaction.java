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

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.neo4j.driver.*;
import org.neo4j.driver.async.AsyncSession;
import org.neo4j.driver.async.AsyncTransaction;
import org.neo4j.driver.exceptions.ClientException;
import org.neo4j.driver.exceptions.DatabaseException;
import org.neo4j.driver.exceptions.NoSuchRecordException;
import org.neo4j.driver.exceptions.ServiceUnavailableException;
import org.neo4j.driver.exceptions.TransientException;
import org.neo4j.driver.internal.async.InternalAsyncTransaction;
import org.neo4j.driver.internal.shaded.reactor.core.publisher.Flux;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.structr.api.NetworkException;
import org.structr.api.NotFoundException;
import org.structr.api.RetryException;
import org.structr.api.util.Iterables;
import org.structr.bolt.reactive.QueueingFlux;
import org.structr.bolt.reactive.QueueingMono;
import org.structr.bolt.reactive.StructrFlux;
import org.structr.bolt.reactive.StructrMono;

/**
 *
 */
class NonReactiveSessionTransaction extends SessionTransaction {

	private static final Logger logger                = LoggerFactory.getLogger(NonReactiveSessionTransaction.class);
	private AsyncSession session                      = null;
	private CompletionStage<AsyncTransaction> tx      = null;

	public NonReactiveSessionTransaction(final BoltDatabaseService db, final AsyncSession session) {

		this.transactionId = ID_SOURCE.getAndIncrement();
		this.session       = session;
		this.tx            = session.beginTransactionAsync(db.getTransactionConfig(transactionId));
		this.db            = db;
	}

	public NonReactiveSessionTransaction(final BoltDatabaseService db, final AsyncSession session, final int timeoutInSeconds) {

		final TransactionConfig config = db.getTransactionConfigForTimeout(timeoutInSeconds, transactionId);

		this.transactionId = ID_SOURCE.getAndIncrement();
		this.session       = session;
		this.tx            = session.beginTransactionAsync(config);
		this.db            = db;
	}

	@Override
	public void failure() {

		return;

	}

	@Override
	public void success() {

		success = true;

	}

	@Override
	public void close() {

		if (!success) {

			//tx.thenCompose(AsyncTransaction::rollbackAsync);

			for (final EntityWrapper entity : accessedEntities) {

				entity.rollback(transactionKey);
				entity.removeFromCache();
			}

			for (final EntityWrapper entity : modifiedEntities) {
				entity.stale();
			}

		} else {

			AtomicBoolean transactionClosed = new AtomicBoolean(false);

			tx
			.thenCompose(asyncTransaction -> asyncTransaction.commitAsync())
			.thenCompose((Void v) -> {
				transactionClosed.set(true);
				return new CompletableFuture<>();
			});

			waitOrTimeout(transactionClosed);

			if(!transactionClosed.get()) {
				System.out.println("Error");
			}

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

			//tx.close();
			session.closeAsync().toCompletableFuture().get();

		} catch (TransientException tex) {

			// transient exceptions can be retried
			throw new RetryException(tex);

		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (ExecutionException e) {
			e.printStackTrace();
		} finally {

			// Notify all nodes that are modified in this transaction
			// so that the relationship caches are rebuilt.
			for (final EntityWrapper entity : modifiedEntities) {
				entity.onClose();
			}

			// make sure that the resources are freed
			//if (session.isOpen()) {
			//	session.close();
			//}
		}



	}

	private void waitOrTimeout(AtomicBoolean done) {

		final long waitStart = System.currentTimeMillis();

		while(!done.get()) {

			final long curentIteration = System.currentTimeMillis();

			if(curentIteration > waitStart + 6_000) {
				break;
			}

			try {
				Thread.yield();
			} catch (Exception e) {
				e.printStackTrace();
			}

		}

	}


	public Record runTransaction(final String statement, final Map<String, Object> map) {

		StructrMono mono = collectRecord(statement, map);
		Record record = mono.getRecord();

		return record;

	}

	@Override
	public Value firstTransactionValue(final String statement, final Map<String, Object> map, boolean eager) {

		Record record = runTransaction(statement, map);
		if(record.size() >= 0) {
			Value value = record.get(0);
			return value;
		}

		return null;

	}

	@Override
	public Value singleTransactionValue(final String statement, final Map<String, Object> map, boolean eager) {

		return firstTransactionValue(statement, map, eager);

	}

	public StructrMono collectRecord(final String statement, final Map<String, Object> parameters) {

		final StructrMono mono = new QueueingMono(db, statement, parameters);
		mono.initialize();

		return mono;

	}

	public void collectRecord(final String statement, final Map<String, Object> parameters, StructrMono mono) {

		logQuery(statement, parameters);

		tx.thenCompose(asyncTransaction -> asyncTransaction.runAsync(statement, parameters))
			.thenCompose(resultCursor -> mono.start(resultCursor))
			.thenCompose(resultCursor -> resultCursor.forEachAsync(mono::accept))
			.thenAccept(summary -> mono.finish())
			.exceptionally(e -> mono.exception(e));

	}

	public StructrFlux collectRecords(final String statement, final Map<String, Object> parameters) {

		final StructrFlux flux = new QueueingFlux(db, statement, parameters);
		flux.initialize();

		return flux;

	}

	@Override
	//public void collectRecords(final String statement, final Map<String, Object> map, final IterableQueueingRecordConsumer consumer) {
	public void collectRecords(final String statement, final Map<String, Object> parameters, StructrFlux flux) {

		logQuery(statement, parameters);

		tx.thenCompose(asyncTransaction -> {

			if(asyncTransaction instanceof InternalAsyncTransaction) {
				InternalAsyncTransaction internalAsyncTransaction = (InternalAsyncTransaction)asyncTransaction;
				if(!internalAsyncTransaction.isOpen()) {
					System.out.println("Error here!!!");
				}
			}

			return asyncTransaction.runAsync(statement, parameters);
		})
		.thenCompose(resultCursor -> flux.start(resultCursor))
		.thenCompose(resultCursor -> resultCursor.forEachAsync(flux::accept))
		.thenAccept(summary -> flux.finish())
		.exceptionally(e -> flux.exception(e));

		/*tx.runAsync(statement, map)
				.thenCompose(cursor -> consumer.start(cursor))
				.thenCompose(cursor -> cursor.forEachAsync(consumer::accept))
				.thenAccept(summary -> consumer.finish())
				.exceptionally(t -> consumer.exception(t));*/

	}

	@Override
	public Iterable<Map<String, Object>> run(final String statement, final Map<String, Object> map) {

		logQuery(statement, map);

		StructrFlux flux = collectRecords(statement, map);
		return Iterables.map(
			record -> new MapResultWrapper(db, record.asMap()),
			flux
		);

	}

	@Override
	public void set(final String statement, final Map<String, Object> map) {

		try {

			logQuery(statement, map);

			StructrMono mono = collectRecord(statement, map);
			mono.getRecord();

		} catch (TransientException tex) {
			closed = true;
			throw new RetryException(tex);
		} catch (NoSuchRecordException nex) {
			throw new NotFoundException(nex);
		} catch (ServiceUnavailableException ex) {
			throw new NetworkException(ex.getMessage(), ex);
		} catch (DatabaseException dex) {
			throw NonReactiveSessionTransaction.translateDatabaseException(dex);
		} catch (ClientException cex) {
			throw NonReactiveSessionTransaction.translateClientException(cex);
		}
	}

	@Override
	public void logQuery(final String statement, final Map<String, Object> map) {

		if(true) return;
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
