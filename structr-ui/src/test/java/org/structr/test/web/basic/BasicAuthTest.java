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
import java.io.IOException;
import org.hamcrest.Matchers;
import org.structr.api.config.Settings;
import org.structr.common.error.FrameworkException;
import org.structr.core.app.StructrApp;
import org.structr.core.graph.NodeAttribute;
import org.structr.core.graph.Tx;
import org.structr.test.web.StructrUiTest;
import org.structr.web.common.FileHelper;
import org.structr.web.entity.File;
import org.structr.web.entity.Folder;
import org.structr.web.entity.User;
import org.structr.web.entity.dom.DOMNode;
import org.structr.web.entity.dom.Page;
import static org.testng.AssertJUnit.fail;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;


public class BasicAuthTest extends StructrUiTest {

	@BeforeClass(alwaysRun = true)
	@Override
	public void setup() {
		super.setup();
		Settings.HttpBasicAuthEnabled.setValue(true);
	}

	@Test
	public void test00BasicAuthOnPage() {

		RestAssured.basePath = "/";
		String userUUID = "";

		try (final Tx tx = app.tx()) {

			final Page page1 = makeVisibleToAuth(Page.createSimplePage(securityContext, "test1"));
			final Page page2 = makeVisibleToAuth(Page.createSimplePage(securityContext, "test2"));
			final Page page3 = makeVisibleToAuth(Page.createSimplePage(securityContext, "test3"));

			page1.setProperty(StructrApp.key(Page.class, "visibleToAuthenticatedUsers"), true);
			page1.setProperty(StructrApp.key(Page.class, "enableBasicAuth"), true);

			page2.setProperty(StructrApp.key(Page.class, "visibleToAuthenticatedUsers"), true);
			page2.setProperty(StructrApp.key(Page.class, "basicAuthRealm"), "realm");
			page2.setProperty(StructrApp.key(Page.class, "enableBasicAuth"), true);

			page3.setProperty(StructrApp.key(Page.class, "visibleToAuthenticatedUsers"), true);
			page3.setProperty(StructrApp.key(Page.class, "basicAuthRealm"), "Enter password for ${this.name}");
			page3.setProperty(StructrApp.key(Page.class, "enableBasicAuth"), true);

			final User tester = createUser();
			tester.setProperty(StructrApp.key(Page.class, "visibleToAuthenticatedUsers"), true);
			tester.setProperty(StructrApp.key(Page.class, "visibleToPublicUsers"), true);
			userUUID = tester.getUuid();

			tx.success();

		} catch (FrameworkException fex) {
			fex.printStackTrace();
			fail("Unexpected exception.");
		}

		// 1. Status code + Auto-generated Realm
		RestAssured.expect().statusCode(401).header("WWW-Authenticate", "BASIC realm=\"test1\"").when().get("/test1");

		// 2. Status Code + Fixed realm
		RestAssured.expect().statusCode(401).header("WWW-Authenticate", "BASIC realm=\"realm\"").when().get("/test2");

		// 3. Status Code + Fixed realm + request parameter
		RestAssured.expect().statusCode(401).header("WWW-Authenticate", "BASIC realm=\"realm\"").when().get("/test2?hacks=areReal");

		// 4. Status Code + script realm + current object for page
		RestAssured.expect().statusCode(401).header("WWW-Authenticate", "BASIC realm=\"Enter password for test3\"").when().get("/test3/" + userUUID);

		// 5. Status Code + script realm + current object for page + request parameter
		RestAssured.expect().statusCode(401).header("WWW-Authenticate", "BASIC realm=\"Enter password for test3\"").when().get("/test3/" + userUUID + "?hacks=areReal");


		// test successful basic auth
		RestAssured
			.given().authentication().basic("tester", "test")
			.expect().statusCode(200).body("html.head.title", Matchers.equalTo("Test1")).body("html.body.h1", Matchers.equalTo("Test1")).body("html.body.div", Matchers.equalTo("Initial body text"))
			.when().get("/test1");

		RestAssured
			.given().authentication().basic("tester", "test")
			.expect().statusCode(200).body("html.head.title", Matchers.equalTo("Test2")).body("html.body.h1", Matchers.equalTo("Test2")).body("html.body.div", Matchers.equalTo("Initial body text"))
			.when().get("/test2");

		RestAssured
			.given().authentication().basic("tester", "test")
			.expect().statusCode(200).body("html.head.title", Matchers.equalTo("Test3")).body("html.body.h1", Matchers.equalTo("Test3")).body("html.body.div", Matchers.equalTo("Initial body text"))
			.when().get("/test3");
	}

	@Test
	public void test01BasicAuthOnFile() {

		RestAssured.basePath = "/";

		try (final Tx tx = app.tx()) {

			final File file1 = FileHelper.createFile(securityContext, "test1".getBytes(), "text/plain", File.class, "test1.txt", true);
			final File file2 = FileHelper.createFile(securityContext, "test2".getBytes(), "text/plain", File.class, "test2.txt", true);

			final Folder folder1 = FileHelper.createFolderPath(securityContext, "/myFolder");
			final File file3 = FileHelper.createFile(securityContext, "test3".getBytes(), "text/plain", File.class, "test3.txt", true);
			file3.setParent(folder1);

			final File file4 = FileHelper.createFile(securityContext, "You said '${request.message}' and your name is '${me.name}'.".getBytes(), "text/plain", File.class, "test4.txt", true);
			file4.setProperty(StructrApp.key(File.class, "isTemplate"), true);
			file4.setParent(folder1);

			file1.setProperty(StructrApp.key(Page.class, "visibleToAuthenticatedUsers"), true);
			file1.setProperty(StructrApp.key(Page.class, "enableBasicAuth"), true);

			file2.setProperty(StructrApp.key(Page.class, "visibleToAuthenticatedUsers"), true);
			file2.setProperty(StructrApp.key(Page.class, "basicAuthRealm"), "realm");
			file2.setProperty(StructrApp.key(Page.class, "enableBasicAuth"), true);

			file3.setProperty(StructrApp.key(Page.class, "visibleToAuthenticatedUsers"), true);
			file3.setProperty(StructrApp.key(Page.class, "enableBasicAuth"), true);
			file3.setProperty(StructrApp.key(Page.class, "basicAuthRealm"), "Enter password for ${this.path}");

			file4.setProperty(StructrApp.key(Page.class, "visibleToAuthenticatedUsers"), true);
			file4.setProperty(StructrApp.key(Page.class, "enableBasicAuth"), true);
			file4.setProperty(StructrApp.key(Page.class, "basicAuthRealm"), "Enter password for ${this.path}");

			createUser();

			tx.success();

		} catch (FrameworkException | IOException fex) {
			fex.printStackTrace();
			fail("Unexpected exception.");
		}

		// 1. Status code + Auto-generated Realm
		RestAssured.expect().statusCode(401).header("WWW-Authenticate", "BASIC realm=\"test1.txt\"").when().get("test1.txt");

		// 2. Status Code + Fixed realm
		RestAssured.expect().statusCode(401).header("WWW-Authenticate", "BASIC realm=\"realm\"").when().get("test2.txt");

		// 3. Status Code + Fixed realm + request parameter
		RestAssured.expect().statusCode(401).header("WWW-Authenticate", "BASIC realm=\"realm\"").when().get("test2.txt?t=1234567890");

		// 4. Status Code + script realm + file in subfolder
		RestAssured.expect().statusCode(401).header("WWW-Authenticate", "BASIC realm=\"Enter password for /myFolder/test3.txt\"").when().get("/myFolder/test3.txt");

		// 5. Status Code + script realm + file in subfolder + request parameter
		RestAssured.expect().statusCode(401).header("WWW-Authenticate", "BASIC realm=\"Enter password for /myFolder/test3.txt\"").when().get("/myFolder/test3.txt?t=1234567890");

		// 6. Status Code + script realm + dynamic file in subfolder + request parameter
		RestAssured.expect().statusCode(401).header("WWW-Authenticate", "BASIC realm=\"Enter password for /myFolder/test4.txt\"").when().get("/myFolder/test4.txt?message=Hello");

		// test successful basic auth
		RestAssured
				.given().authentication().basic("tester", "test")
				.expect().statusCode(200).body(Matchers.equalTo("test1"))
				.when().get("test1.txt");

		RestAssured
				.given().authentication().basic("tester", "test")
				.expect().statusCode(200).body(Matchers.equalTo("test2"))
				.when().get("test2.txt");

		RestAssured
				.given().authentication().basic("tester", "test")
				.expect().statusCode(200).body(Matchers.equalTo("test3"))
				.when().get("/myFolder/test3.txt");

		RestAssured
				.given().authentication().basic("tester", "test")
				.expect().statusCode(200).body(Matchers.equalTo("You said 'Hello' and your name is 'tester'."))
				.when().get("/myFolder/test4.txt?message=Hello");

	}

	@Test
	public void test02BasicAuthOnPageWithErrorPage() {

		RestAssured.basePath = "/";

		try (final Tx tx = app.tx()) {

			final Page error = makeVisible(Page.createSimplePage(securityContext, "error"));
			error.setProperty(StructrApp.key(Page.class, "showOnErrorCodes"), "401");

			final Page page1 = makeVisibleToAuth(Page.createSimplePage(securityContext, "test1"));
			final Page page2 = makeVisibleToAuth(Page.createSimplePage(securityContext, "test2"));

			page1.setProperty(StructrApp.key(Page.class, "visibleToAuthenticatedUsers"), true);
			page1.setProperty(StructrApp.key(Page.class, "enableBasicAuth"), true);

			page2.setProperty(StructrApp.key(Page.class, "visibleToAuthenticatedUsers"), true);
			page2.setProperty(StructrApp.key(Page.class, "basicAuthRealm"), "realm");
			page2.setProperty(StructrApp.key(Page.class, "enableBasicAuth"), true);

			createUser();

			tx.success();

		} catch (FrameworkException fex) {
			fex.printStackTrace();
			fail("Unexpected exception.");
		}

		// test error page as result of unauthorized request
		RestAssured
			.expect().statusCode(401).header("WWW-Authenticate", "BASIC realm=\"test1\"").body("html.head.title", Matchers.equalTo("Error")).body("html.body.h1", Matchers.equalTo("Error")).body("html.body.div", Matchers.equalTo("Initial body text"))
			.when().get("/html/test1");

		RestAssured
			.expect().statusCode(401).header("WWW-Authenticate", "BASIC realm=\"realm\"").body("html.head.title", Matchers.equalTo("Error")).body("html.body.h1", Matchers.equalTo("Error")).body("html.body.div", Matchers.equalTo("Initial body text"))
			.when().get("/html/test2");


		// test successful basic auth
		RestAssured
			.given().authentication().basic("tester", "test")
			.expect().statusCode(200).body("html.head.title", Matchers.equalTo("Test1")).body("html.body.h1", Matchers.equalTo("Test1")).body("html.body.div", Matchers.equalTo("Initial body text"))
			.when().get("/html/test1");

		RestAssured
			.given().authentication().basic("tester", "test")
			.expect().statusCode(200).body("html.head.title", Matchers.equalTo("Test2")).body("html.body.h1", Matchers.equalTo("Test2")).body("html.body.div", Matchers.equalTo("Initial body text"))
			.when().get("/html/test2");

	}

	@Test
	public void test03BasicAuthOnFileWithErrorPage() {

		RestAssured.basePath = "/";

		try (final Tx tx = app.tx()) {

			final Page error = makeVisible(Page.createSimplePage(securityContext, "error"));
			error.setProperty(StructrApp.key(Page.class, "showOnErrorCodes"), "401");

			final File file1 = FileHelper.createFile(securityContext, "test1".getBytes(), "text/plain", File.class, "test1.txt", true);
			final File file2 = FileHelper.createFile(securityContext, "test2".getBytes(), "text/plain", File.class, "test2.txt", true);

			file1.setProperty(StructrApp.key(Page.class, "visibleToAuthenticatedUsers"), true);
			file1.setProperty(StructrApp.key(Page.class, "enableBasicAuth"), true);

			file2.setProperty(StructrApp.key(Page.class, "visibleToAuthenticatedUsers"), true);
			file2.setProperty(StructrApp.key(Page.class, "basicAuthRealm"), "realm");
			file2.setProperty(StructrApp.key(Page.class, "enableBasicAuth"), true);

			createUser();

			tx.success();

		} catch (FrameworkException | IOException fex) {
			fex.printStackTrace();
			fail("Unexpected exception.");
		}

		// test error page as result of unauthorized request
		RestAssured
			.expect().statusCode(401).header("WWW-Authenticate", "BASIC realm=\"test1.txt\"").body("html.head.title", Matchers.equalTo("Error")).body("html.body.h1", Matchers.equalTo("Error")).body("html.body.div", Matchers.equalTo("Initial body text"))
			.when().get("test1.txt");

		RestAssured
			.expect().statusCode(401).header("WWW-Authenticate", "BASIC realm=\"realm\"").body("html.head.title", Matchers.equalTo("Error")).body("html.body.h1", Matchers.equalTo("Error")).body("html.body.div", Matchers.equalTo("Initial body text"))
			.when().get("test2.txt");


		// test successful basic auth
		RestAssured
			.given().authentication().basic("tester", "test")
			.expect().statusCode(200).body(Matchers.equalTo("test1"))
			.when().get("test1.txt");

		RestAssured
			.given().authentication().basic("tester", "test")
			.expect().statusCode(200).body(Matchers.equalTo("test2"))
			.when().get("test2.txt");
	}

	// ----- private methods -----
	private User createUser() throws FrameworkException {

		return createTestNode(User.class,
			new NodeAttribute<>(StructrApp.key(User.class, "name"), "tester"),
			new NodeAttribute<>(StructrApp.key(User.class, "password"), "test")
		);
	}

	private <T extends DOMNode> T makeVisible(final T src) {

		try {

			src.setProperty(StructrApp.key(DOMNode.class, "visibleToAuthenticatedUsers"), true);
			src.setProperty(StructrApp.key(DOMNode.class, "visibleToPublicUsers"), true);

		} catch (FrameworkException fex) {}

		src.getAllChildNodes().stream().forEach((n) -> {

			try {
				n.setProperty(StructrApp.key(DOMNode.class, "visibleToAuthenticatedUsers"), true);
				n.setProperty(StructrApp.key(DOMNode.class, "visibleToPublicUsers"), true);

			} catch (FrameworkException fex) {}
		} );

		return src;
	}

	private <T extends DOMNode> T makeVisibleToAuth(final T src) {

		try {

			src.setProperty(StructrApp.key(DOMNode.class, "visibleToAuthenticatedUsers"), true);

		} catch (FrameworkException fex) {}

		src.getAllChildNodes().stream().forEach((n) -> {

			try {
				n.setProperty(StructrApp.key(DOMNode.class, "visibleToAuthenticatedUsers"), true);

			} catch (FrameworkException fex) {}
		} );

		return src;
	}
}
