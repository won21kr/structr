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
package org.structr.bolt.reactive;

import org.neo4j.driver.Record;
import org.neo4j.driver.async.ResultCursor;
import org.structr.bolt.BoltDatabaseService;
import org.structr.bolt.SessionTransaction;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class QueueingFlux implements StructrFlux {

    // GLOBAL QUEUEING FLUX PARAMETERS
    private final static long FETCH_TIMEOUT = 60_000;

    // maintenance parameters
    protected BoltDatabaseService databaseService = null;

    protected String statement = null;
    protected Map<String, Object> parameters = null;

    // queue parameters
    private final BlockingQueue<Record> queue = new LinkedBlockingQueue<>();
    private final AtomicBoolean closed = new AtomicBoolean(false);

    protected final AtomicBoolean added    = new AtomicBoolean(false);
    protected final AtomicInteger elementCount = new AtomicInteger(0);
    protected final AtomicBoolean finished = new AtomicBoolean(false);

    // auxiliary parameters
    private ResultCursor cursor;
    protected Throwable throwable = null;

    public QueueingFlux(final BoltDatabaseService databaseService, final String statement, final Map<String, Object> parameters) {
        this.databaseService = databaseService;
        this.statement  = statement;
        this.parameters = parameters;
    }

    @Override
    public void initialize() {

        final SessionTransaction tx = databaseService.getCurrentTransaction();
        tx.collectRecords(statement, parameters, this);

    }

    @Override
    public CompletionStage<ResultCursor> start(final ResultCursor cursor) {
        this.cursor = cursor;
        return CompletableFuture.completedFuture(cursor);
    }

    @Override
    public void accept(Record record) {

        if(closed.get()) {
            return;
        }

        queue.add(record);
        added.set(true);

        elementCount.incrementAndGet();

    }

    @Override
    public void finish() {

        finished.set(true);
        added.set(true); // why?

    }

    public Void exception(final Throwable throwable) {
        this.throwable = throwable;
        return null;
    }

    @Override
    public Iterator<Record> iterator() {
        return this;
    }

    public void fetchEntries() {}

    @Override
    public boolean hasNext() {

        final long yieldStart = System.currentTimeMillis();

        while(!added.get() || (!finished.get() && queue.isEmpty())) {

            // handle exceptions
            if(throwable != null) {

                if(throwable instanceof RuntimeException) {
                    throw (RuntimeException)throwable;
                } else {
                    throw new RuntimeException(throwable);
                }

            }

            fetchEntries();

            // wait for data or exception
            try {
                Thread.yield();
            } catch (Throwable throwable) {
                this.throwable = throwable;
            }

            long timeAfterWait = System.currentTimeMillis();
            if(timeAfterWait > yieldStart + QueueingFlux.FETCH_TIMEOUT) {
                return false;
            }

        }

        return !queue.isEmpty();
    }

    @Override
    public Record next() {
        return queue.poll();
    }

    @Override
    public void close() throws Exception {

        if(!closed.getAndSet(true)) {

            cursor.consumeAsync();

        }

    }

    public boolean isClosed() {
        return closed.get();
    }

    public boolean isEmpty() {

        return queue.isEmpty();

    }

    public boolean gotAdded() {

        return added.get();

    }

}
