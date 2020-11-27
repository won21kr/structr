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

import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Value;
import org.neo4j.driver.Values;
import org.neo4j.driver.exceptions.*;
import org.neo4j.driver.internal.shaded.reactor.core.publisher.Flux;
import org.neo4j.driver.types.Entity;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Relationship;
import org.structr.api.*;
import org.structr.bolt.reactive.StructrFlux;
import org.structr.bolt.reactive.StructrMono;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 *
 */
public abstract class SessionTransaction implements org.structr.api.Transaction {

    /**
     * The following attributes are only temporarily declared as protected,
     * will be changed once clean api is defined
     * */

    protected static final AtomicLong ID_SOURCE         = new AtomicLong();
    protected final Set<EntityWrapper> accessedEntities = new HashSet<>();
    protected final Set<EntityWrapper> modifiedEntities = new HashSet<>();
    protected final Set<Long> deletedNodes              = new HashSet<>();
    protected final Set<Long> deletedRels               = new HashSet<>();
    protected final Object transactionKey               = new Object();
    protected BoltDatabaseService db                    = null;
    protected long transactionId                        = 0L;
    protected boolean closed                            = false;
    protected boolean success                           = false;
    protected boolean isPing                            = false;

    public boolean isClosed() {
        return closed;
    }

    public void setClosed(final boolean closed) {
        this.closed = closed;
    }

    public abstract Value firstTransactionValue(final String statement, final Map<String, Object> map, boolean eager);
    public Value firstTransactionValue(final String statement, final Map<String, Object> map) {

        return firstTransactionValue(statement, map, false);

    }

    public abstract Value singleTransactionValue(final String statement, final Map<String, Object> map, boolean eager);
    public Value singleTransactionValue(final String statement, final Map<String, Object> map) {

        return singleTransactionValue(statement, map, false);

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
            throw SessionTransaction.translateDatabaseException(dex);
        } catch (ClientException cex) {
            throw SessionTransaction.translateClientException(cex);
        }
    }

    public boolean getBoolean(final String statement, final Map<String, Object> map) {

        try {

            logQuery(statement, map);
            return firstTransactionValue(statement, map).asBoolean();

        } catch (TransientException tex) {
            closed = true;
            throw new RetryException(tex);
        } catch (NoSuchRecordException nex) {
            throw new NotFoundException(nex);
        } catch (ServiceUnavailableException ex) {
            throw new NetworkException(ex.getMessage(), ex);
        } catch (DatabaseException dex) {
            throw SessionTransaction.translateDatabaseException(dex);
        } catch (ClientException cex) {
            throw SessionTransaction.translateClientException(cex);
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
            throw SessionTransaction.translateDatabaseException(dex);
        } catch (ClientException cex) {
            throw SessionTransaction.translateClientException(cex);
        }
    }

    public long getLong(final String statement, final Map<String, Object> map) {

        try {

            logQuery(statement, map);
            return firstTransactionValue(statement, map).asLong();

        } catch (TransientException tex) {
            closed = true;
            throw new RetryException(tex);
        } catch (NoSuchRecordException nex) {
            throw new NotFoundException(nex);
        } catch (ServiceUnavailableException ex) {
            throw new NetworkException(ex.getMessage(), ex);
        } catch (DatabaseException dex) {
            throw SessionTransaction.translateDatabaseException(dex);
        } catch (ClientException cex) {
            throw SessionTransaction.translateClientException(cex);
        }
    }

    public Object getObject(final String statement, final Map<String, Object> map) {

        try {

            logQuery(statement, map);
            return firstTransactionValue(statement, map).asObject();

        } catch (TransientException tex) {
            closed = true;
            throw new RetryException(tex);
        } catch (NoSuchRecordException nex) {
            throw new NotFoundException(nex);
        } catch (ServiceUnavailableException ex) {
            throw new NetworkException(ex.getMessage(), ex);
        } catch (DatabaseException dex) {
            throw SessionTransaction.translateDatabaseException(dex);
        } catch (ClientException cex) {
            throw SessionTransaction.translateClientException(cex);
        }
    }

    public Entity getEntity(final String statement, final Map<String, Object> map) {

        try {

            logQuery(statement, map);
            return firstTransactionValue(statement, map).asEntity();

        } catch (TransientException tex) {
            closed = true;
            throw new RetryException(tex);
        } catch (NoSuchRecordException nex) {
            throw new NotFoundException(nex);
        } catch (ServiceUnavailableException ex) {
            throw new NetworkException(ex.getMessage(), ex);
        } catch (DatabaseException dex) {
            throw SessionTransaction.translateDatabaseException(dex);
        } catch (ClientException cex) {
            throw SessionTransaction.translateClientException(cex);
        }
    }

    public Node getNode(final String statement, final Map<String, Object> map) {

        try {

            logQuery(statement, map);
            return singleTransactionValue(statement, map, true).asNode();

        } catch (TransientException tex) {
            closed = true;
            throw new RetryException(tex);
        } catch (NoSuchRecordException nex) {
            throw new NotFoundException(nex);
        } catch (ServiceUnavailableException ex) {
            throw new NetworkException(ex.getMessage(), ex);
        } catch (DatabaseException dex) {
            throw SessionTransaction.translateDatabaseException(dex);
        } catch (ClientException cex) {
            throw SessionTransaction.translateClientException(cex);
        }
    }

    public Relationship getRelationship(final String statement, final Map<String, Object> map) {

        try {

            logQuery(statement, map);
            return singleTransactionValue(statement, map).asRelationship();

        } catch (TransientException tex) {
            closed = true;
            throw new RetryException(tex);
        } catch (NoSuchRecordException nex) {
            throw new NotFoundException(nex);
        } catch (ServiceUnavailableException ex) {
            throw new NetworkException(ex.getMessage(), ex);
        } catch (DatabaseException dex) {
            throw SessionTransaction.translateDatabaseException(dex);
        } catch (ClientException cex) {
            throw SessionTransaction.translateClientException(cex);
        }
    }

    public abstract void collectRecord(final String statement, final Map<String, Object> map, StructrMono mono);

    public abstract void collectRecords(final String statement, final Map<String, Object> map, StructrFlux flux);

    public Iterable<String> getStrings(final String statement, final Map<String, Object> map) {

        try {

            logQuery(statement, map);
            final Value value = firstTransactionValue(statement, map);

            return new IteratorWrapper<>(value.asList(Values.ofString()).iterator());

        } catch (TransientException tex) {
            closed = true;
            throw new RetryException(tex);
        } catch (NoSuchRecordException nex) {
            throw new NotFoundException(nex);
        } catch (ServiceUnavailableException ex) {
            throw new NetworkException(ex.getMessage(), ex);
        } catch (DatabaseException dex) {
            throw SessionTransaction.translateDatabaseException(dex);
        } catch (ClientException cex) {
            throw SessionTransaction.translateClientException(cex);
        }
    }

    public abstract Iterable<Map<String, Object>> run(final String statement, final Map<String, Object> map);

    public abstract void set(final String statement, final Map<String, Object> map);

    public void logQuery(final String statement) {
        logQuery(statement, null);
    }

    public abstract void logQuery(final String statement, final Map<String, Object> map);

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
            return new SessionTransaction.CloseableIterator<>(iterator);
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
