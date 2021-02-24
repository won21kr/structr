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
package org.structr.test.rest.test.property;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.filter.log.ResponseLoggingFilter;
import static org.hamcrest.Matchers.*;
import org.testng.annotations.Test;
import org.structr.test.rest.common.StructrRestTestBase;
import static org.testng.AssertJUnit.fail;

/**
 *
 *
 */
public class RelatedNodePropertyTest extends StructrRestTestBase {

	@Test
	public void test01StaticRelationshipResourceFilter() {

		String test01 = createEntity("/test_sixs", "{ name: test01, aString: string01, anInt: 1 }");
		String test02 = createEntity("/test_sixs", "{ name: test02, aString: string02, anInt: 2 }");
		String test03 = createEntity("/test_sixs", "{ name: test03, aString: string03, anInt: 3 }");
		String test04 = createEntity("/test_sixs", "{ name: test04, aString: string04, anInt: 4 }");

		String test09 = createEntity("/test_sevens", "{ name: test09, testSixIds: [", test01, ",", test02, "] }");

		// test simple related node search
		RestAssured

			.given()
				.contentType("application/json; charset=UTF-8")
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(200))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(201))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(400))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(404))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(422))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(500))

			.expect()
				.statusCode(200)

				.body("result.testSixIds[0]", equalTo(test01))
			        .body("result.testSixIds[1]", equalTo(test02))

			.when()
				.get(concat("/test_sevens/", test09));

		// test update via put
		RestAssured

			.given()
				.contentType("application/json; charset=UTF-8")
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(200))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(201))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(400))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(404))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(422))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(500))
				.body(" { testSixIds: [" + test03 + "," + test04 + "] } ")

			.expect()
				.statusCode(200)

			.when()
				.put(concat("/test_sevens/", test09));

		RestAssured

			.given()
				.contentType("application/json; charset=UTF-8")
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(200))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(201))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(400))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(404))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(422))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(500))

			.expect()
				.statusCode(200)

				.body("result.testSixIds", containsInAnyOrder(test03, test04))

			.when()
				.get(concat("/test_sevens/", test09));
	}

	/**
	 * Create a TestFive entity with the id of a related TestThree entity.
	 */
	@Test
	public void test02CreateWithRelatedNode() {

		String test01 = createEntity("/test_threes", "{ name: test01 }");

		String test02 = createEntity("/test_fives", "{ name: test02, oneToOneTestThree: ", test01, " }");

		// test simple related node search
		RestAssured

			.given()
				.contentType("application/json; charset=UTF-8")
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(200))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(201))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(400))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(404))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(422))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(500))

			.expect()
				.statusCode(200)

				.body("result.oneToOneTestThree.id", equalTo(test01))

			.when()
				.get(concat("/test_fives/", test02));

	}

	/**
	 * Create a TestFive entity with the id of a related TestFour entity.
	 *
	 * The second POST should fail because TestFour doesn't fit the expected type of the oneToOneTestThree attribute.
	 */
	@Test
	public void test03CreateWithRelatedNodeOfWrongType() {

		String test01 = createEntity("/test_fours", "{ name: test01 }");

		try {

			createEntity("/test_fives", "{ name: test02, oneToOneTestThree: ", test01, " }");

			fail("Creation of TestFive entity should fail because entity with id " + test01 + " is of type TestFour, but oneToOneTestThree does only takes an entity of type TestThree");

		} catch (AssertionError err) {

		}

	}


	/**
	 * Create a TestFive entity with the id of a related TestThree entity.
	 */
	@Test
	public void test04CreateWithRelatedNode() {

		String test01 = createEntity("/test_threes", "{ name: test01 }");

		String test02 = createEntity("/test_fives", "{ name: test02, manyToOneTestThree: ", test01, " }");

		// test simple related node search
		RestAssured

			.given()
				.contentType("application/json; charset=UTF-8")
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(200))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(201))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(400))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(404))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(422))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(500))

			.expect()
				.statusCode(200)

				.body("result.manyToOneTestThree.id", equalTo(test01))

			.when()
				.get(concat("/test_fives/", test02));

	}

	/**
	 * Create a TestFive entity with the id of a related TestFour entity.
	 *
	 * The second POST should fail because TestFour doesn't fit the expected type of the oneToOneTestThree attribute.
	 */
	@Test
	public void test05CreateWithRelatedNodeOfWrongType() {

		String test01 = createEntity("/test_fours", "{ name: test01 }");

		try {
			createEntity("/test_fives", "{ name: test02, manyToOneTestThree: ", test01, " }");

			fail("Creation of TestFive entity should fail because entity with id " + test01 + " is of type TestFour, but manyToOneTestThree does only takes an entity of type TestThree");

		} catch (AssertionError err) {

		}

	}

	/**
	 * Create a TestFive entity with the id of a related TestOne entity.
	 */
	@Test
	public void test06CreateWithRelatedNode() {

		String test01 = createEntity("/test_ones", "{ name: test01 }");

		String test02 = createEntity("/test_fives", "{ name: test02, manyToManyTestOnes: [", test01, "] }");

		// test simple related node search
		RestAssured

			.given()
				.contentType("application/json; charset=UTF-8")
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(200))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(201))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(400))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(404))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(422))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(500))

			.expect()
				.statusCode(200)

				.body("result.manyToManyTestOnes[0]", equalTo(test01))

			.when()
				.get(concat("/test_fives/", test02));

	}

	/**
	 * Create a TestFive entity with the id of a related TestFour entity.
	 *
	 * The second POST should fail because TestFour doesn't fit the expected type of the oneToOneTestThree attribute.
	 */
	@Test
	public void test07CreateWithRelatedNodeOfWrongType() {

		String test01 = createEntity("/test_fours", "{ name: test01 }");

		try {
			createEntity("/test_fives", "{ name: test02, manyToManyTestOnes: [", test01, "] }");

			fail("Creation of TestFive entity should fail because entity with id " + test01 + " is of type TestFour, but manyToManyTestOnes does only take entities of type TestOne");

		} catch (AssertionError err) {

		}


	}

	/**
	 * Create a TestFive entity with the id of a related TestOne entity.
	 */
	@Test
	public void test08CreateWithRelatedNode() {

		String test01 = createEntity("/test_ones", "{ name: test01 }");

		String test02 = createEntity("/test_fives", "{ name: test02, oneToManyTestOnes: [", test01, "] }");

		// test simple related node search
		RestAssured

			.given()
				.contentType("application/json; charset=UTF-8")
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(200))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(201))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(400))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(404))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(422))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(500))

			.expect()
				.statusCode(200)

				.body("result.oneToManyTestOnes[0]", equalTo(test01))

			.when()
				.get(concat("/test_fives/", test02));

	}

	/**
	 * Create a TestFive entity with the id of a related TestFour entity.
	 *
	 * The second POST should fail because TestFour doesn't fit the expected type of the oneToOneTestThree attribute.
	 */
	@Test
	public void test09CreateWithRelatedNodeOfWrongType() {

		String test01 = createEntity("/test_fours", "{ name: test01 }");

		try {

			createEntity("/test_fives", "{ name: test02, oneToManyTestOnes: [", test01, "] }");

			fail("Creation of TestFive entity should fail because entity with id " + test01 + " is of type TestFour, but oneToManyTestOnes does only take entities of type TestOne");

		} catch (AssertionError err) {

		}


	}

}
