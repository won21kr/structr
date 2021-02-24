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

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

public class QueueingMono implements StructrMono {

    // GLOBAL QUEUEING MONO PARAMETERS
    private final static long FETCH_TIMEOUT = 60_000;

    // maintenance parameters
    private BoltDatabaseService databaseService;

    private String statement;
    private Map<String, Object> parameters;

    // queue parameters
    private final AtomicBoolean closed   = new AtomicBoolean(false);
    private final AtomicBoolean finished = new AtomicBoolean(false);

    private ResultCursor cursor = null;
    private Record record       = null;
    private Throwable throwable = null;

    public QueueingMono(BoltDatabaseService databaseService, final String statement, final Map<String, Object> parameters) {
        this.databaseService = databaseService;
        this.statement  = statement;
        this.parameters = parameters;
    }

    @Override
    public CompletionStage<ResultCursor> start(ResultCursor cursor) {
        this.cursor = cursor;
        return CompletableFuture.completedFuture(cursor);
    }

    @Override
    public void initialize() {

        final SessionTransaction tx = databaseService.getCurrentTransaction();
        tx.collectRecord(statement, parameters, this);

    }

    @Override
    public void finish() {

        finished.set(true);

    }

    @Override
    public Void exception(Throwable throwable) {
        this.throwable = throwable;
        return null;
    }

    @Override
    public Record getRecord() {

        final long yieldStart = System.currentTimeMillis();

        while(!finished.get() && !closed.get()) {

            // handle exceptions
            if(throwable != null) {

                if(throwable instanceof RuntimeException) {
                    throw (RuntimeException)throwable;
                } else {
                    throw new RuntimeException(throwable);
                }

            }

            // wait for result or timeout
            try {
                Thread.yield();
            } catch (Throwable throwable) {
                this.throwable = throwable;
            }

            long timeAfterWait = System.currentTimeMillis();
            if(timeAfterWait > yieldStart + QueueingMono.FETCH_TIMEOUT) {
                return null;
            }

        }

        return record;

    }

    @Override
    public void close() throws Exception {

        if(!closed.getAndSet(true)) {

            cursor.consumeAsync();

        }

    }

    @Override
    public void accept(Record record) {

        this.record = record;

    }
}
