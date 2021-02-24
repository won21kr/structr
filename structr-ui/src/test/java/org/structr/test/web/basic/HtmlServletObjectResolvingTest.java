/*
 * Copyright (C) 2010-2021 Structr GmbH
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
package org.structr.test.web.basic;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.filter.log.ResponseLoggingFilter;
import java.util.LinkedList;
import java.util.List;
import org.hamcrest.Matchers;
import org.testng.annotations.Test;
import org.structr.api.config.Settings;
import org.structr.common.error.FrameworkException;
import org.structr.core.GraphObject;
import org.structr.core.app.Query;
import org.structr.core.app.StructrApp;
import org.structr.core.graph.NodeAttribute;
import org.structr.core.graph.Tx;
import org.structr.test.web.StructrUiTest;
import org.structr.test.web.entity.TestOne;
import org.structr.web.common.FileHelper;
import org.structr.web.entity.File;
import org.structr.web.entity.Linkable;
import org.structr.web.entity.dom.Page;
import org.testng.annotations.BeforeClass;
import org.w3c.dom.DOMException;
import org.w3c.dom.Element;
import org.w3c.dom.Text;

/**
 *
 */
public class HtmlServletObjectResolvingTest extends StructrUiTest {

	@BeforeClass(alwaysRun = true)
	@Override
	public void setup() {

		// important: these settings must be made before HttpService is initialized..
		Settings.HtmlResolveProperties.setValue("TestOne.anInt, TestOne.aString, TestOne.aDouble");

		super.setup();
	}

	@Test
	public void testObjectResolvingInHtmlServlet() {

		final List<String> testObjectIDs = new LinkedList<>();

		try (final Tx tx = app.tx()) {

			// setup three different test objects to be found by HtmlServlet
			testObjectIDs.add(app.create(TestOne.class, new NodeAttribute<>(TestOne.anInt, 123)).getUuid());
			testObjectIDs.add(app.create(TestOne.class, new NodeAttribute<>(TestOne.aDouble, 0.345)).getUuid());
			testObjectIDs.add(app.create(TestOne.class, new NodeAttribute<>(TestOne.aString, "abcdefg")).getUuid());

			// create a page
			final Page newPage = Page.createNewPage(securityContext, "testPage");
			if (newPage != null) {

				Element html  = newPage.createElement("html");
				Element head  = newPage.createElement("head");
				Element body  = newPage.createElement("body");
				Text textNode = newPage.createTextNode("${current.id}");

				try {
					// add HTML element to page
					newPage.appendChild(html);
					html.appendChild(head);
					html.appendChild(body);
					body.appendChild(textNode);

				} catch (DOMException dex) {

					logger.warn("", dex);

					throw new FrameworkException(422, dex.getMessage());
				}
			}

			tx.success();

		} catch (FrameworkException fex) {
			logger.warn("", fex);
		}

		RestAssured.basePath = "/";
		RestAssured.baseURI  = "http://" + host + ":" + httpPort;
		RestAssured.port     = httpPort;

		RestAssured
			.given()
			.header("X-User", "superadmin")
			.header("X-Password", "sehrgeheim")
			.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(200))
			.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(201))
			.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(400))
			.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(401))
			.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(403))
			.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(404))
			.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(422))
			.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(500))
			.expect()
			.response()
			.contentType("text/html")
			.statusCode(200)
			.body(Matchers.containsString(testObjectIDs.get(0)))
			.when()
			.get("/testPage/123");

		RestAssured
			.given()
			.header("X-User", "superadmin")
			.header("X-Password", "sehrgeheim")
			.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(200))
			.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(201))
			.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(400))
			.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(401))
			.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(403))
			.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(404))
			.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(422))
			.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(500))
			.expect()
			.response()
			.statusCode(200)
			.body(Matchers.containsString(testObjectIDs.get(1)))
			.when()
			.get("/testPage/0.345");

		RestAssured
			.given()
			.header("X-User", "superadmin")
			.header("X-Password", "sehrgeheim")
			.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(200))
			.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(201))
			.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(400))
			.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(401))
			.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(403))
			.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(404))
			.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(422))
			.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(500))
			.expect()
			.response()
			.statusCode(200)
			.body(Matchers.containsString(testObjectIDs.get(2)))
			.when()
			.get("/testPage/abcdefg");
	}

	@Test
	public void testFileResolutionQuery() {

		String uuid = null;

		try (final Tx tx = app.tx()) {

			final File file = FileHelper.createFile(securityContext, "test".getBytes(), "text/plain", File.class, "test.txt", true);

			uuid = file.getUuid();

			tx.success();

		} catch (Throwable t) {
			t.printStackTrace();
		}

		try (final Tx tx = app.tx()) {

			final Query query = StructrApp.getInstance(securityContext).nodeQuery();

			query
				.and()
					.or()
					.andTypes(Page.class)
					.andTypes(File.class)
					.parent()
				.and(GraphObject.id, uuid);

			// Searching for pages needs super user context anyway
			List<Linkable> results = query.getAsList();

			System.out.println(results);

			tx.success();

		} catch (FrameworkException fex) {
			fex.printStackTrace();
		}

	}
}
