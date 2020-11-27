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
package org.structr.bolt.reactive;

import org.structr.api.util.QueryTimer;
import org.structr.bolt.AdvancedCypherQuery;
import org.structr.bolt.BoltDatabaseService;
import org.structr.bolt.SessionTransaction;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class PaginatedQueueingFlux extends QueueingFlux {

    // maintenance parameters
    private AdvancedCypherQuery query = null;

    // queue parameters
    private QueryTimer queryTimer       = null;

    private final AtomicBoolean started  = new AtomicBoolean(false);

    public PaginatedQueueingFlux(BoltDatabaseService databaseService, AdvancedCypherQuery query) {
        super(databaseService, query.getStatement(true), query.getParameters());
        this.query = query;
    }

    @Override
    public void initialize() {

        if (this.queryTimer != null) {
            this.queryTimer.started(statement);
        }

        final SessionTransaction tx = databaseService.getCurrentTransaction();
        tx.setIsPing(query.getQueryContext().isPing());

        super.initialize();

        started.set(true);

        if (queryTimer != null) {
            queryTimer.querySent();
        }

    }

    @Override
    public void fetchEntries() {

        // fetch next result portion
        if(!started.getAndSet(true)) {

            final SessionTransaction tx = databaseService.getCurrentTransaction(false);

            if(tx != null && !tx.isClosed()) {
                final String statement = query.getStatement(true);
                tx.collectRecords(statement, parameters, this);
            }

        }

    }

    @Override
    public void finish() {

        if (queryTimer != null) {
            queryTimer.finishReceived();
        }

        // This method will only be called when all the records from the current
        // result cursor have been consumed. We now need to decide whether we want
        // to fetch more.

        // This method will be called from a different thread, so there is no
        // transaction context..

        if (elementCount.get() == query.pageSize() && !isClosed()) {

            query.nextPage();

            // there are probably more results available
            elementCount.set(0);
            finished.set(false);

            // signal other thread that new results should be fetched
            started.set(false);

            if (queryTimer != null) {
                queryTimer.nextPage();
            }

        } else {

            finished.set(true);
            added.set(true);

            if (queryTimer != null) {
                queryTimer.finished();
            }
        }

    }

    @Override
    public void close() throws Exception {

        if(!isClosed()) {

            if(queryTimer != null) {
                queryTimer.closed();
            }

            super.close();

            if(queryTimer != null) {
                queryTimer.consumed();
            }

        }

    }

}
