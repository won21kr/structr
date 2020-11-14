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
package org.structr.bolt;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.TransactionConfig;
import org.neo4j.driver.Values;
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
import org.neo4j.driver.types.Entity;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Relationship;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.structr.api.ConstraintViolationException;
import org.structr.api.DataFormatException;
import org.structr.api.NetworkException;
import org.structr.api.NotFoundException;
import org.structr.api.RetryException;
import org.structr.api.UnknownClientException;
import org.structr.api.UnknownDatabaseException;
import org.structr.api.util.Iterables;

/**
 *
 */
class ReactiveSessionTransaction implements org.structr.api.Transaction {

	private static final Logger logger                = LoggerFactory.getLogger(ReactiveSessionTransaction.class);
	private static final AtomicLong ID_SOURCE         = new AtomicLong();
	private final Set<EntityWrapper> accessedEntities = new HashSet<>();
	private final Set<EntityWrapper> modifiedEntities = new HashSet<>();
	private final Set<Long> deletedNodes              = new HashSet<>();
	private final Set<Long> deletedRels               = new HashSet<>();
	private final Object transactionKey               = new Object();
	private BoltDatabaseService db                    = null;
	private RxSession session                         = null;
	private RxTransaction tx                          = null;
	private long transactionId                        = 0L;
	private boolean closed                            = false;
	private boolean success                           = false;
	private boolean isPing                            = false;

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
	public void failure() {
	}

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

	public boolean isClosed() {
		return closed;
	}

	public void setClosed(final boolean closed) {
		this.closed = closed;
	}

	public boolean getBoolean(final String statement) {

		try {

			logQuery(statement);
			return getBoolean(statement, Collections.EMPTY_MAP);

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

	public boolean getBoolean(final String statement, final Map<String, Object> map) {

		try {

			logQuery(statement, map);
			return Mono.from(tx.run(statement, map).records()).block().get(0).asBoolean();

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

	public long getLong(final String statement) {

		try {

			logQuery(statement);
			return getLong(statement, Collections.EMPTY_MAP);

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

	public long getLong(final String statement, final Map<String, Object> map) {

		try {

			logQuery(statement, map);
			return Mono.from(tx.run(statement, map).records()).block().get(0).asLong();

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

	public Object getObject(final String statement, final Map<String, Object> map) {

		try {

			logQuery(statement, map);
			return Mono.from(tx.run(statement, map).records()).block().get(0).asObject();

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

	public Entity getEntity(final String statement, final Map<String, Object> map) {

		try {

			logQuery(statement, map);
			return Mono.from(tx.run(statement, map).records()).block().get(0).asEntity();

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

	public Node getNode(final String statement, final Map<String, Object> map) {

		try {

			logQuery(statement, map);

			final RxResult result = tx.run(statement, map);
			final Node node       =  Mono.from(result.records()).block().get(0).asNode();

			Mono.from(result.consume()).block();

			return node;

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

	public Relationship getRelationship(final String statement, final Map<String, Object> map) {

		try {

			logQuery(statement, map);
			return Mono.from(tx.run(statement, map).records()).block().get(0).asRelationship();

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

	public Flux<Record> collectRecords(final String statement, final Map<String, Object> map) {

		try {

			logQuery(statement, map);
			return Flux.from(tx.run(statement, map).records());

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

	public Iterable<String> getStrings(final String statement, final Map<String, Object> map) {

		try {

			logQuery(statement, map);
			return new IteratorWrapper<>(Mono.from(tx.run(statement, map).records()).block().get(0).asList(Values.ofString()).iterator());

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

	public Iterable<Map<String, Object>> run(final String statement, final Map<String, Object> map) {

		try {

			logQuery(statement, map);

			final Iterable<Map<String, Object>> iterable = Iterables.map(new RecordMapMapper(db), Flux.from(tx.run(statement, map).records()).toIterable());

			iterable.iterator().hasNext();

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

	public void logQuery(final String statement) {
		logQuery(statement, null);
	}

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

	public void deleted(final NodeWrapper wrapper) {
		deletedNodes.add(wrapper.getDatabaseId());
	}

	public void deleted(final RelationshipWrapper wrapper) {
		deletedRels.add(wrapper.getDatabaseId());
	}

	public boolean isDeleted(final EntityWrapper wrapper) {

		if (wrapper instanceof NodeWrapper) {
			return deletedNodes.contains(wrapper.getDatabaseId());
		}

		if (wrapper instanceof RelationshipWrapper) {
			return deletedRels.contains(wrapper.getDatabaseId());
		}

		return false;
	}

	public void modified(final EntityWrapper wrapper) {
		modifiedEntities.add(wrapper);
	}

	public void accessed(final EntityWrapper wrapper) {
		accessedEntities.add(wrapper);
	}

	public void setIsPing(final boolean isPing) {
		this.isPing = isPing;
	}

	@Override
	public long getTransactionId() {
		return this.transactionId;
	}

	public Object getTransactionKey() {
		// we need a simple object that can be used in a weak hash map
		return transactionKey;
	}

	// ----- public static methods -----
	public static RuntimeException translateClientException(final ClientException cex) {

		switch (cex.code()) {

			case "Neo.ClientError.Schema.ConstraintValidationFailed":
				throw new ConstraintViolationException(cex, cex.code(), cex.getMessage());

			// add handlers / translated exceptions for ClientExceptions here..
		}

		// wrap exception if no other cause could be found
		throw new UnknownClientException(cex, cex.code(), cex.getMessage());
	}

	public static RuntimeException translateDatabaseException(final DatabaseException dex) {

		switch (dex.code()) {

			case "Neo.DatabaseError.General.UnknownError":
				throw new DataFormatException(dex, dex.code(), dex.getMessage());

			// add handlers / translated exceptions for DatabaseExceptions here..
		}

		// wrap exception if no other cause could be found
		throw new UnknownDatabaseException(dex, dex.code(), dex.getMessage());
	}

	// ----- nested classes -----
	public class IteratorWrapper<T> implements Iterable<T> {

		private Iterator<T> iterator = null;

		public IteratorWrapper(final Iterator<T> iterator) {
			this.iterator = iterator;
		}

		@Override
		public Iterator<T> iterator() {
			return new CloseableIterator<>(iterator);
		}
	}

	public class CloseableIterator<T> implements Iterator<T>, AutoCloseable {

		private Iterator<T> iterator = null;

		public CloseableIterator(final Iterator<T> iterator) {
			this.iterator = iterator;
		}

		@Override
		public boolean hasNext() {

			try {
				return iterator.hasNext();

			} catch (ClientException dex) {
				throw ReactiveSessionTransaction.translateClientException(dex);
			} catch (DatabaseException dex) {
				throw ReactiveSessionTransaction.translateDatabaseException(dex);
			}
		}

		@Override
		public T next() {

			try {

				return iterator.next();

			} catch (ClientException dex) {
				throw ReactiveSessionTransaction.translateClientException(dex);
			} catch (DatabaseException dex) {
				throw ReactiveSessionTransaction.translateDatabaseException(dex);
			}
		}

		@Override
		public void close() throws Exception {

			if (iterator instanceof Result) {

				((Result)iterator).consume();
			}
		}
	}
}