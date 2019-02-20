/**
 * Copyright (C) 2010-2018 Structr GmbH
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
package org.structr.memory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import org.structr.api.NotFoundException;
import org.structr.api.graph.Identity;
import org.structr.api.graph.PropertyContainer;

/**
 */
public abstract class MemoryEntity implements PropertyContainer {

	private final Map<Long, Map<String, Object>> txData = new LinkedHashMap<>();
	private final Map<String, Object> data              = new LinkedHashMap<>();
	private ReentrantLock lock                          = new ReentrantLock();
	protected MemoryDatabaseService db                  = null;
	private MemoryIdentity id                           = null;

	public MemoryEntity(final MemoryDatabaseService db, final MemoryIdentity identity) {

		this.id = identity;
		this.db = db;

		lock();
	}

	@Override
	public Identity getId() {
		return id;
	}

	public MemoryIdentity getIdentity() {
		return id;
	}

	@Override
	public boolean hasProperty(final String name) {
		return getData(true).containsKey(name);
	}

	@Override
	public Object getProperty(String name) {
		return getData(true).get(name);
	}

	@Override
	public Object getProperty(String name, Object defaultValue) {

		final Object value = getProperty(name);
		if (value != null) {

			return value;
		}

		return defaultValue;
	}

	@Override
	public void setProperty(final String name, final Object value) {
		lock();
		getData(false).put(name, value);
	}

	@Override
	public void setProperties(final Map<String, Object> values) {
		lock();
		getData(false).putAll(values);
	}

	@Override
	public void removeProperty(final String name) {
		lock();
		getData(false).remove(name);
	}

	@Override
	public Iterable<String> getPropertyKeys() {
		return getData(true).keySet();
	}

	@Override
	public boolean isSpatialEntity() {
		return false;
	}

	@Override
	public boolean isDeleted() {
		return false;
	}

	// ----- package-private methods -----
	void commit(final long transactionId) {

		data.clear();
		data.putAll(txData.get(transactionId));

		txData.remove(transactionId);
		unlock();
	}

	void rollback(final long transactionId) {
		txData.remove(transactionId);
		unlock();
	}

	void lock() {

		if (!lock.isHeldByCurrentThread()) {
			lock.lock();
		}
	}

	void unlock() {

		if (lock.isHeldByCurrentThread()) {
			lock.unlock();
		}
	}

	// ----- private methods -----
	private Map<String, Object> getData(final boolean read) {

		// read-only access does not need a transaction
		final MemoryTransaction tx = db.getCurrentTransaction(!read);
		if (tx != null) {

			if (tx.isDeleted(id) || !tx.exists(id)) {
				throw new NotFoundException("Entity with ID " + id + " not found.");
			}

			final long transactionId   = tx.getTransactionId();
			Map<String, Object> copy   = txData.get(transactionId);
			if (copy == null) {

				copy = new LinkedHashMap<>(data);
				txData.put(transactionId, copy);

				tx.modify(this);
			}

			return copy;

		} else {

			return data;
		}
	}
}