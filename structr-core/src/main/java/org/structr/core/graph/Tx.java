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
package org.structr.core.graph;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;
import org.structr.api.RetryException;
import org.structr.common.SecurityContext;
import org.structr.common.error.FrameworkException;
import org.structr.core.StructrTransactionListener;

/**
 *
 *
 */
public class Tx implements AutoCloseable {

	private final AtomicBoolean guard       = new AtomicBoolean(false);
	private SecurityContext securityContext = null;
	private boolean success                 = false;
	private boolean doValidation            = true;
	private boolean doCallbacks             = true;
	private boolean doNotifications         = true;

	public Tx(final SecurityContext securityContext) {
		this(securityContext, true, true);
	}

	public Tx(final SecurityContext securityContext, final boolean doValidation, final boolean doCallbacks) {
		this(securityContext, doValidation, doCallbacks, ((securityContext == null) ? false : securityContext.doTransactionNotifications()));
	}

	public Tx(final SecurityContext securityContext, final boolean doValidation, final boolean doCallbacks, final boolean doNotifications) {

		this.securityContext = securityContext;
		this.doNotifications = doNotifications;
		this.doValidation    = doValidation;
		this.doCallbacks     = doCallbacks;
	}

	public Tx begin() throws FrameworkException {

		TransactionCommand.beginTx(securityContext);

		return this;
	}

	public void success() throws FrameworkException {

		TransactionCommand.commitTx(securityContext, doValidation);
		success = true;
	}

	@Override
	public void close() throws FrameworkException {

		final ModificationQueue modificationQueue = TransactionCommand.finishTx();

		if (success && guard.compareAndSet(false, true)) {

			boolean retry  = true;
			while (retry) {

				retry = false;

				// experimental
				try (final Tx tx = begin()) {

					if (doCallbacks && modificationQueue != null) {

						modificationQueue.doOuterCallbacks(securityContext);

						// notify listeners if desired, and allow this setting to be overriden locally AND remotely
						if ( (securityContext == null) ? doNotifications : doNotifications && securityContext.doTransactionNotifications() ) {

							final Collection<ModificationEvent> modificationEvents = modificationQueue.getModificationEvents();
							for (final StructrTransactionListener listener : TransactionCommand.getTransactionListeners()) {

								listener.afterCommit(securityContext, modificationEvents);
							}
						}

						modificationQueue.updateChangelog();
						modificationQueue.clear();
					}

					tx.success();

				} catch (RetryException rex) {
					retry = true;
				}
			}

			guard.set(false);
		}
	}

	/**
	 * Allow setting the securityContext if it was null.
	 * Important for Login transactions.
	 * @param sc
	 */
	public void setSecurityContext(final SecurityContext sc) {

		if (securityContext == null) {

			if (sc.isSuperUserSecurityContext() == Boolean.FALSE) {

				securityContext = sc;
			}
		}
	}

	public void disableChangelog() {
		TransactionCommand.disableChangelog();
	}
}
