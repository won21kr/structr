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
package org.structr.memgraph;

import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.neo4j.driver.v1.Record;
import org.neo4j.driver.v1.StatementResultCursor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class IterableQueueingRecordConsumer implements Iterable<Record>, Iterator<Record>, AutoCloseable, Consumer<Record> {

	private static final Logger logger        = LoggerFactory.getLogger(IterableQueueingRecordConsumer.class);
	private final BlockingQueue<Record> queue = new LinkedBlockingQueue<>();
	private final AtomicInteger elementCount  = new AtomicInteger(0);
	private final AtomicInteger queueSize     = new AtomicInteger(0);
	private final AtomicBoolean aborted       = new AtomicBoolean(false);
	private final AtomicBoolean finished      = new AtomicBoolean(false);
	private final AtomicBoolean added         = new AtomicBoolean(false);
	private final AtomicBoolean started       = new AtomicBoolean(false);
	private MemgraphDatabaseService db            = null;
	private StatementResultCursor cursor      = null;
	private AdvancedCypherQuery query         = null;
	private Throwable throwable               = null;

	public IterableQueueingRecordConsumer(final MemgraphDatabaseService db, final AdvancedCypherQuery query) {

		this.query = query;
		this.db    = db;
	}

	public void start() {

		final SessionTransaction tx = db.getCurrentTransaction();
		tx.setIsPing(query.getQueryContext().isPing());
		tx.collectRecords(query.getStatement(true), query.getParameters(), this);

		started.set(true);
	}

	@Override
	public Iterator<Record> iterator() {
		return this;
	}

	@Override
	public void close() {
		aborted.set(true);
		cursor.consumeAsync();
	}

	public void finish() {

		// This method will only be called when all the records from the current
		// result cursor have been consumed. We now need to decide whether we want
		// to fetch more.

		// This method will be called from a different Thread, so there is no
		// transaction context..

		if (elementCount.get() == query.pageSize() && !aborted.get()) {

			query.nextPage();

			// there are probably more results available
			elementCount.set(0);
			finished.set(false);

			// signal other thread that new results should be fetched
			started.set(false);

		} else {

			finished.set(true);
			added.set(true);
		}
	}

	@Override
	public boolean hasNext() {

		// make the consuming thread wait for results util elements have been
		// added OR the producer has no more results (finished == true)
		final long yieldStart = System.currentTimeMillis();

		while (!added.get() || (!finished.get() && queue.isEmpty())) {

			if (throwable != null) {

				if (throwable instanceof RuntimeException) {

					throw (RuntimeException)throwable;

				} else {

					throw new RuntimeException(throwable);
				}
			}

			// start fetching of next result portion while waiting for results
			if (!started.getAndSet(true)) {

				final SessionTransaction tx = db.getCurrentTransaction(false);
				if (tx != null && !tx.isClosed()) {

					tx.collectRecords(query.getStatement(true), query.getParameters(), this);
				}
			}

			// wait for data (or exception)
			try { Thread.yield(); } catch (Throwable t) {}

			if (System.currentTimeMillis() > yieldStart + 180_000) {

				logger.warn("#######################################################################################################");
				logger.warn("IterableQueueingRecordConsumer waited for 2 minutes, aborting");
				logger.warn("statement: {}", query.getStatement(true));
				logger.warn("throwable: {}", throwable);
				logger.warn("finished:  {}", finished.get());
				logger.warn("added:     {}", added.get());
				logger.warn("queue:     {}", queue);
				logger.warn("#######################################################################################################");

				break;
			}
		}

		return !queue.isEmpty();
	}

	@Override
	public Record next() {

		queueSize.decrementAndGet();
		return queue.poll();
	}

	@Override
	public void accept(final Record t) {

		if (aborted.get()) {
			return;
		}

		queue.add(t);
		added.set(true);

		queueSize.incrementAndGet();
		elementCount.incrementAndGet();
	}

	public CompletionStage<StatementResultCursor> start(final StatementResultCursor cursor) {
		this.cursor = cursor;
		return CompletableFuture.completedFuture(cursor);
	}

	public Void exception(final Throwable t) {
		this.throwable = t;
		return null;
	}
}