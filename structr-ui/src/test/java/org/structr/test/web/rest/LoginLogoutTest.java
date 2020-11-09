/*
 * Copyright (C) 2010-2020 Structr GmbH
 *
 * This file is part of Structr <http://structr.org>.
 *
 * Structr is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Structr is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Structr.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.structr.test.web.rest;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.filter.log.ResponseLoggingFilter;
import org.testng.annotations.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.structr.common.error.FrameworkException;
import org.structr.core.graph.Tx;
import org.structr.test.web.StructrUiTest;
import org.structr.web.auth.UiAuthenticator;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.fail;

/**
 *
 *
 */
public class LoginLogoutTest extends StructrUiTest {

	private static final Logger logger = LoggerFactory.getLogger(LoginLogoutTest.class.getName());

	@Test
	public void testLoginLogout() {

		createEntityAsSuperUser("/User", "{ 'name': 'User1', 'password': 'geheim'}");

		try (final Tx tx = app.tx()) {

			grant("_login", UiAuthenticator.NON_AUTH_USER_POST, true);
			grant("_logout", UiAuthenticator.AUTH_USER_POST, false);
			grant("User", UiAuthenticator.AUTH_USER_GET, false);

			tx.success();

		} catch (FrameworkException fex) {

			logger.warn("", fex);
			fail("Unexpected exception.");
		}

		String sessionId = null;

		try (final Tx tx = app.tx()) {

			// first post is to login
			sessionId = RestAssured
				.given()
					.contentType("application/json; charset=UTF-8")
					.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(200))
					.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(201))
					.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(401))
					.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(403))
					.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(404))
					.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(500))
					.body("{ 'name': 'User1', 'password': 'geheim'}")
				.expect()
					.statusCode(200)
				.when()
					.post("/login")
				.getSessionId();

			tx.success();

		} catch (FrameworkException fex) {

			logger.warn("", fex);
			fail("Unexpected exception.");
		}

		assertNotNull(sessionId);

		try (final Tx tx = app.tx()) {

			RestAssured
				.given()
					.contentType("application/json; charset=UTF-8")
					.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(200))
					.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(201))
					.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(401))
					.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(403))
					.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(404))
					.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(500))
					.sessionId(sessionId)
				.expect()
					.statusCode(200)
				.when()
					.get("/me");

			tx.success();

		} catch (FrameworkException fex) {

			logger.warn("", fex);
			fail("Unexpected exception.");
		}

		try (final Tx tx = app.tx()) {

			RestAssured
				.given()
					.contentType("application/json; charset=UTF-8")
					.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(200))
					.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(201))
					.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(401))
					.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(403))
					.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(404))
					.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(500))
					.sessionId(sessionId)
				.expect()
					.statusCode(200)
				.when()
					.post("/logout");

			tx.success();

		} catch (FrameworkException fex) {

			logger.warn("", fex);
			fail("Unexpected exception.");
		}

		// Check that we're really logged out

		try (final Tx tx = app.tx()) {

			RestAssured
				.given()
					.contentType("application/json; charset=UTF-8")
					.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(200))
					.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(201))
					.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(401))
					.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(403))
					.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(404))
					.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(500))
					.sessionId(sessionId)
				.expect()
					.statusCode(401)
				.when()
					.get("/me");

			tx.success();

		} catch (FrameworkException fex) {

			logger.warn("", fex);
			fail("Unexpected exception.");
		}

	}
}
