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
package org.structr.test.core.script;

import com.google.gson.GsonBuilder;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.*;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.structr.api.config.Settings;
import org.structr.api.graph.Cardinality;
import org.structr.api.schema.*;
import org.structr.api.util.Iterables;
import org.structr.common.AccessControllable;
import org.structr.common.AccessMode;
import org.structr.common.Permission;
import org.structr.common.SecurityContext;
import org.structr.common.error.FrameworkException;
import org.structr.common.error.UnlicensedScriptException;
import org.structr.common.geo.GeoCodingResult;
import org.structr.common.geo.GeoHelper;
import org.structr.core.GraphObject;
import org.structr.core.app.App;
import org.structr.core.app.StructrApp;
import org.structr.core.entity.AbstractNode;
import org.structr.core.entity.Group;
import org.structr.core.entity.Principal;
import org.structr.core.entity.SchemaMethod;
import org.structr.core.entity.SchemaNode;
import org.structr.core.entity.SchemaProperty;
import org.structr.core.entity.SchemaRelationshipNode;
import org.structr.core.entity.SuperUser;
import org.structr.core.function.DateFormatFunction;
import org.structr.core.function.FindFunction;
import org.structr.core.function.NumberFormatFunction;
import org.structr.core.function.RoundFunction;
import org.structr.core.graph.NodeAttribute;
import org.structr.core.graph.NodeInterface;
import org.structr.core.graph.Tx;
import org.structr.core.property.EnumProperty;
import org.structr.core.property.PropertyKey;
import org.structr.core.property.PropertyMap;
import org.structr.core.script.ScriptTestHelper;
import org.structr.core.script.Scripting;
import org.structr.schema.ConfigurationProvider;
import org.structr.schema.action.ActionContext;
import org.structr.schema.action.Actions;
import org.structr.schema.export.StructrSchema;
import org.structr.test.common.StructrTest;
import org.structr.test.core.entity.TestFour;
import org.structr.test.core.entity.TestOne;
import org.structr.test.core.entity.TestOne.Status;
import org.structr.test.core.entity.TestSix;
import org.structr.test.core.entity.TestThree;
import org.structr.test.core.entity.TestTwo;
import org.testng.Assert;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.Test;


/**
 *
 *
 */
public class ScriptingTest extends StructrTest {

	private static final Logger logger = LoggerFactory.getLogger(ScriptingTest.class.getName());

	@Test
	public void testSetPropertyWithDynamicNodes() {

		this.cleanDatabaseAndSchema();

		/**
		 * This test creates two connected SchemaNodes and tests the script-based
		 * association of one instance with several others in the onCreate method.
		 */

		final long currentTimeMillis    = System.currentTimeMillis();
		Class sourceType                = null;
		Class targetType                = null;
		PropertyKey targetsProperty     = null;
		EnumProperty testEnumProperty   = null;
		PropertyKey testBooleanProperty = null;
		PropertyKey testIntegerProperty = null;
		PropertyKey testStringProperty  = null;
		PropertyKey testDoubleProperty  = null;
		PropertyKey testDateProperty    = null;
		Class testEnumType              = null;

		// setup phase: create schema nodes
		try (final Tx tx = app.tx()) {

			// create two nodes and associate them with each other
			final SchemaNode sourceNode  = createTestNode(SchemaNode.class, "Source");
			final SchemaNode targetNode  = createTestNode(SchemaNode.class, "Target");

			final List<SchemaProperty> properties = new LinkedList<>();
			properties.add(createTestNode(SchemaProperty.class, new NodeAttribute(AbstractNode.name, "testBoolean"), new NodeAttribute(SchemaProperty.propertyType, "Boolean")));
			properties.add(createTestNode(SchemaProperty.class, new NodeAttribute(AbstractNode.name, "testInteger"), new NodeAttribute(SchemaProperty.propertyType, "Integer")));
			properties.add(createTestNode(SchemaProperty.class, new NodeAttribute(AbstractNode.name, "testString"), new NodeAttribute(SchemaProperty.propertyType, "String")));
			properties.add(createTestNode(SchemaProperty.class, new NodeAttribute(AbstractNode.name, "testDouble"), new NodeAttribute(SchemaProperty.propertyType, "Double")));
			properties.add(createTestNode(SchemaProperty.class, new NodeAttribute(AbstractNode.name, "testEnum"), new NodeAttribute(SchemaProperty.propertyType, "Enum"), new NodeAttribute(SchemaProperty.format, "OPEN, CLOSED, TEST")));
			properties.add(createTestNode(SchemaProperty.class, new NodeAttribute(AbstractNode.name, "testDate"), new NodeAttribute(SchemaProperty.propertyType, "Date")));
			sourceNode.setProperty(SchemaNode.schemaProperties, properties);

			final List<SchemaMethod> methods = new LinkedList<>();
			methods.add(createTestNode(SchemaMethod.class, new NodeAttribute(AbstractNode.name, "onCreate"), new NodeAttribute(SchemaMethod.source, "{ var e = Structr.get('this'); e.targets = Structr.find('Target'); }")));
			methods.add(createTestNode(SchemaMethod.class, new NodeAttribute(AbstractNode.name, "doTest01"), new NodeAttribute(SchemaMethod.source, "{ var e = Structr.get('this'); e.testEnum = 'OPEN'; }")));
			methods.add(createTestNode(SchemaMethod.class, new NodeAttribute(AbstractNode.name, "doTest02"), new NodeAttribute(SchemaMethod.source, "{ var e = Structr.get('this'); e.testEnum = 'CLOSED'; }")));
			methods.add(createTestNode(SchemaMethod.class, new NodeAttribute(AbstractNode.name, "doTest03"), new NodeAttribute(SchemaMethod.source, "{ var e = Structr.get('this'); e.testEnum = 'TEST'; }")));
			methods.add(createTestNode(SchemaMethod.class, new NodeAttribute(AbstractNode.name, "doTest04"), new NodeAttribute(SchemaMethod.source, "{ var e = Structr.get('this'); e.testEnum = 'INVALID'; }")));
			methods.add(createTestNode(SchemaMethod.class, new NodeAttribute(AbstractNode.name, "doTest05"), new NodeAttribute(SchemaMethod.source, "{ var e = Structr.get('this'); e.testBoolean = true; e.testInteger = 123; e.testString = 'testing..'; e.testDouble = 1.2345; e.testDate = new Date(" + currentTimeMillis + "); }")));
			sourceNode.setProperty(SchemaNode.schemaMethods, methods);

			final PropertyMap propertyMap = new PropertyMap();

			propertyMap.put(SchemaRelationshipNode.sourceId,       sourceNode.getUuid());
			propertyMap.put(SchemaRelationshipNode.targetId,       targetNode.getUuid());
			propertyMap.put(SchemaRelationshipNode.sourceJsonName, "source");
			propertyMap.put(SchemaRelationshipNode.targetJsonName, "targets");
			propertyMap.put(SchemaRelationshipNode.sourceMultiplicity, "*");
			propertyMap.put(SchemaRelationshipNode.targetMultiplicity, "*");
			propertyMap.put(SchemaRelationshipNode.relationshipType, "HAS");

			app.create(SchemaRelationshipNode.class, propertyMap);

			tx.success();


		} catch(Throwable t) {

			t.printStackTrace();
			fail("Unexpected exception.");
		}

		try (final Tx tx = app.tx()) {

			final ConfigurationProvider config = StructrApp.getConfiguration();

			sourceType          = config.getNodeEntityClass("Source");
			targetType          = config.getNodeEntityClass("Target");
			targetsProperty     = StructrApp.key(sourceType, "targets");

			// we need to cast to EnumProperty in order to obtain the dynamic enum type
			testEnumProperty    = (EnumProperty)StructrApp.key(sourceType, "testEnum");
			testEnumType        = testEnumProperty.getEnumType();

			testBooleanProperty = StructrApp.key(sourceType, "testBoolean");
			testIntegerProperty = StructrApp.key(sourceType, "testInteger");
			testStringProperty  = StructrApp.key(sourceType, "testString");
			testDoubleProperty  = StructrApp.key(sourceType, "testDouble");
			testDateProperty    = StructrApp.key(sourceType, "testDate");

			assertNotNull(sourceType);
			assertNotNull(targetType);
			assertNotNull(targetsProperty);

			// create 5 target nodes
			createTestNodes(targetType, 5);

			// create source node
			createTestNodes(sourceType, 5);

			tx.success();


		} catch(Throwable t) {

			t.printStackTrace();
			fail("Unexpected exception.");
		}


		// check phase: source node should have all five target nodes associated with HAS
		try (final Tx tx = app.tx()) {

			// check all source nodes
			for (final Object obj : app.nodeQuery(sourceType).getAsList()) {

				assertNotNull("Invalid nodeQuery result", obj);

				final GraphObject sourceNode = (GraphObject)obj;

				// test contents of "targets" property
				final Object targetNodesObject = sourceNode.getProperty(targetsProperty);
				assertTrue("Invalid getProperty result for scripted association", targetNodesObject instanceof Iterable);

				final Iterable iterable = (Iterable)targetNodesObject;
				assertEquals("Invalid getProperty result for scripted association", 5, Iterables.count(iterable));
			}

			final GraphObject sourceNode = app.nodeQuery(sourceType).getFirst();

			// set testEnum property to OPEN via doTest01 function call, check result
			sourceNode.invokeMethod(securityContext, "doTest01", Collections.EMPTY_MAP, true);
			assertEquals("Invalid setProperty result for EnumProperty", testEnumType.getEnumConstants()[0], sourceNode.getProperty(testEnumProperty));

			// set testEnum property to CLOSED via doTest02 function call, check result
			sourceNode.invokeMethod(securityContext, "doTest02", Collections.EMPTY_MAP, true);
			assertEquals("Invalid setProperty result for EnumProperty", testEnumType.getEnumConstants()[1], sourceNode.getProperty(testEnumProperty));

			// set testEnum property to TEST via doTest03 function call, check result
			sourceNode.invokeMethod(securityContext, "doTest03", Collections.EMPTY_MAP, true);
			assertEquals("Invalid setProperty result for EnumProperty", testEnumType.getEnumConstants()[2], sourceNode.getProperty(testEnumProperty));

			// set testEnum property to INVALID via doTest03 function call, expect previous value & error
			try {
				sourceNode.invokeMethod(securityContext, "doTest04", Collections.EMPTY_MAP, true);
				assertEquals("Invalid setProperty result for EnumProperty",    testEnumType.getEnumConstants()[2], sourceNode.getProperty(testEnumProperty));
				fail("Setting EnumProperty to invalid value should result in an Exception!");

			} catch (FrameworkException fx) {}

			// test other property types
			sourceNode.invokeMethod(securityContext, "doTest05", Collections.EMPTY_MAP, true);
			assertEquals("Invalid setProperty result for BooleanProperty",                         true, sourceNode.getProperty(testBooleanProperty));
			assertEquals("Invalid setProperty result for IntegerProperty",                          123, sourceNode.getProperty(testIntegerProperty));
			assertEquals("Invalid setProperty result for StringProperty",                   "testing..", sourceNode.getProperty(testStringProperty));
			assertEquals("Invalid setProperty result for DoubleProperty",                        1.2345, sourceNode.getProperty(testDoubleProperty));
			assertEquals("Invalid setProperty result for DateProperty",     new Date(currentTimeMillis), sourceNode.getProperty(testDateProperty));

			tx.success();

		} catch(FrameworkException fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}
	}

	@Test
	public void testGrantViaScripting() {

		// setup phase: create schema nodes
		try (final Tx tx = app.tx()) {

			// create two nodes and associate them with each other
			final SchemaNode sourceNode  = createTestNode(SchemaNode.class, "Source");
			final SchemaMethod method    = createTestNode(SchemaMethod.class, new NodeAttribute(AbstractNode.name, "doTest01"), new NodeAttribute(SchemaMethod.source, "{ var e = Structr.get('this'); e.grant(Structr.find('Principal')[0], 'read', 'write'); }"));

			sourceNode.setProperty(SchemaNode.schemaMethods, Arrays.asList(new SchemaMethod[] { method } ));

			tx.success();

		} catch(FrameworkException fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}

		final ConfigurationProvider config = StructrApp.getConfiguration();
		final Class sourceType             = config.getNodeEntityClass("Source");
		Principal testUser                 = null;

		// create test node as superuser
		try (final Tx tx = app.tx()) {

			app.create(sourceType);
			tx.success();

		} catch(FrameworkException fex) {

			logger.warn("", fex);
			fail("Unexpected exception.");
		}

		// create test user
		try (final Tx tx = app.tx()) {

			testUser = app.create(Principal.class,
				new NodeAttribute<>(Principal.name,     "test"),
				new NodeAttribute<>(StructrApp.key(Principal.class, "password"), "test")
			);

			tx.success();

		} catch(FrameworkException fex) {

			logger.warn("", fex);
			fail("Unexpected exception.");
		}

		final App userApp = StructrApp.getInstance(SecurityContext.getInstance(testUser, AccessMode.Backend));

		// first test without grant, expect no test object to be found using the user context
		try (final Tx tx = userApp.tx()) { assertEquals("Invalid grant() scripting result", 0, userApp.nodeQuery(sourceType).getAsList().size()); tx.success(); } catch(FrameworkException fex) {
			logger.warn("", fex);
			fail("Unexpected exception.");
		}

		// grant read access to test user
		try (final Tx tx = app.tx()) {

			app.nodeQuery(sourceType).getFirst().invokeMethod(securityContext, "doTest01", Collections.EMPTY_MAP, true);
			tx.success();

		} catch(FrameworkException fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}

		// first test without grant, expect no test object to be found using the user context
		try (final Tx tx = userApp.tx()) { assertEquals("Invalid grant() scripting result", 1, userApp.nodeQuery(sourceType).getAsList().size()); tx.success(); } catch(FrameworkException fex) {
			logger.warn("", fex);
			fail("Unexpected exception.");
		}
	}

	@Test
	public void testScriptedFindWithJSONObject() {

		final Random random    = new Random();
		final long long1       = 13475233523455L;
		final long long2       = 327326252322L;
		final double double1   = 1234.56789;
		final double double2   = 5678.975321;

		List<TestSix> testSixs = null;
		TestOne testOne1       = null;
		TestOne testOne2       = null;
		TestTwo testTwo1       = null;
		TestTwo testTwo2       = null;
		TestThree testThree1   = null;
		TestThree testThree2   = null;
		TestFour testFour1     = null;
		TestFour testFour2     = null;
		Date date1             = null;
		Date date2             = null;

		// setup phase
		try (final Tx tx = app.tx()) {

			testSixs             = createTestNodes(TestSix.class, 10);
			testOne1             = app.create(TestOne.class);
			testOne2             = app.create(TestOne.class);
			testTwo1             = app.create(TestTwo.class);
			testTwo2             = app.create(TestTwo.class);
			testThree1           = app.create(TestThree.class);
			testThree2           = app.create(TestThree.class);
			testFour1            = app.create(TestFour.class);
			testFour2            = app.create(TestFour.class);
			date1                = new Date(random.nextLong());
			date2                = new Date();

			testOne1.setProperty(TestOne.anInt             , 42);
			testOne1.setProperty(TestOne.aLong             , long1);
			testOne1.setProperty(TestOne.aDouble           , double1);
			testOne1.setProperty(TestOne.aDate             , date1);
			testOne1.setProperty(TestOne.anEnum            , Status.One);
			testOne1.setProperty(TestOne.aString           , "aString1");
			testOne1.setProperty(TestOne.aBoolean          , true);
			testOne1.setProperty(TestOne.testTwo           , testTwo1);
			testOne1.setProperty(TestOne.testThree         , testThree1);
			testOne1.setProperty(TestOne.testFour          , testFour1);
			testOne1.setProperty(TestOne.manyToManyTestSixs, testSixs.subList(0, 5));

			testOne2.setProperty(TestOne.anInt             , 33);
			testOne2.setProperty(TestOne.aLong             , long2);
			testOne2.setProperty(TestOne.aDouble           , double2);
			testOne2.setProperty(TestOne.aDate             , date2);
			testOne2.setProperty(TestOne.anEnum            , Status.Two);
			testOne2.setProperty(TestOne.aString           , "aString2");
			testOne2.setProperty(TestOne.aBoolean          , false);
			testOne2.setProperty(TestOne.testTwo           , testTwo2);
			testOne2.setProperty(TestOne.testThree         , testThree2);
			testOne2.setProperty(TestOne.testFour          , testFour2);
			testOne2.setProperty(TestOne.manyToManyTestSixs, testSixs.subList(5, 10));

			tx.success();

		} catch(FrameworkException fex) {

			logger.warn("", fex);
			fail("Unexpected exception.");
		}

		// test phase, find all the things using scripting
		try (final Tx tx = app.tx()) {

			final ActionContext actionContext = new ActionContext(securityContext);

			assertEquals("Invalid scripted find() result", testOne1, Scripting.evaluate(actionContext, testOne1, "${{ return Structr.find('TestOne', { anInt: 42 })[0]; }}", "test"));
			assertEquals("Invalid scripted find() result", testOne2, Scripting.evaluate(actionContext, testOne1, "${{ return Structr.find('TestOne', { anInt: 33 })[0]; }}", "test"));

			assertEquals("Invalid scripted find() result", testOne1, Scripting.evaluate(actionContext, testOne1, "${{ return Structr.find('TestOne', { aLong: " + long1 + " })[0]; }}", "test"));
			assertEquals("Invalid scripted find() result", testOne2, Scripting.evaluate(actionContext, testOne1, "${{ return Structr.find('TestOne', { aLong: " + long2 + " })[0]; }}", "test"));

			assertEquals("Invalid scripted find() result", testOne1, Scripting.evaluate(actionContext, testOne1, "${{ return Structr.find('TestOne', { aDouble: " + double1 + " })[0]; }}", "test"));
			assertEquals("Invalid scripted find() result", testOne2, Scripting.evaluate(actionContext, testOne1, "${{ return Structr.find('TestOne', { aDouble: " + double2 + " })[0]; }}", "test"));

			assertEquals("Invalid scripted find() result", testOne1, Scripting.evaluate(actionContext, testOne1, "${{ return Structr.find('TestOne', { anEnum: 'One' })[0]; }}", "test"));
			assertEquals("Invalid scripted find() result", testOne2, Scripting.evaluate(actionContext, testOne1, "${{ return Structr.find('TestOne', { anEnum: 'Two' })[0]; }}", "test"));

			assertEquals("Invalid scripted find() result", testOne1, Scripting.evaluate(actionContext, testOne1, "${{ return Structr.find('TestOne', { aBoolean: true })[0]; }}", "test"));
			assertEquals("Invalid scripted find() result", testOne2, Scripting.evaluate(actionContext, testOne1, "${{ return Structr.find('TestOne', { aBoolean: false })[0]; }}", "test"));


			tx.success();

		} catch(UnlicensedScriptException |FrameworkException fex) {

			logger.warn("", fex);
			fail("Unexpected exception.");
		}
	}

	@Test
	public void testWrappingUnwrapping() {

		// setup phase
		try (final Tx tx = app.tx()) {

			final ActionContext actionContext = new ActionContext(securityContext);
			final TestOne context             = app.create(TestOne.class);

			Scripting.evaluate(actionContext, context, "${{ Structr.create('Group', { name: 'Group1' } ); }}", "test");
			Scripting.evaluate(actionContext, context, "${{ Structr.create('Group', 'name', 'Group2'); }}", "test");

			assertEquals("Invalid unwrapping result", 2, app.nodeQuery(Group.class).getAsList().size());


			tx.success();

		} catch(UnlicensedScriptException |FrameworkException fex) {

			logger.warn("", fex);
			fail("Unexpected exception.");
		}
	}

	@Test
	public void testEnumPropertyGet() {

		// setup phase
		try (final Tx tx = app.tx()) {

			final ActionContext actionContext = new ActionContext(securityContext);
			final TestOne context             = app.create(TestOne.class);

			Scripting.evaluate(actionContext, context, "${{ var e = Structr.get('this'); e.anEnum = 'One'; }}", "test");

			assertEquals("Invalid enum get result", "One", Scripting.evaluate(actionContext, context, "${{ var e = Structr.get('this'); return e.anEnum; }}", "test"));

			assertEquals("Invaliid Javascript enum comparison result", true, Scripting.evaluate(actionContext, context, "${{ var e = Structr.get('this'); return e.anEnum == 'One'; }}", "test"));

			tx.success();

		} catch(UnlicensedScriptException |FrameworkException fex) {

			logger.warn("", fex);
			fail("Unexpected exception.");
		}
	}

	@Test
	public void testCollectionOperations() {

		final Class groupType                          = StructrApp.getConfiguration().getNodeEntityClass("Group");
		final PropertyKey<Iterable<Principal>> members = StructrApp.key(groupType, "members");
		Group group                                    = null;
		Principal user1                                = null;
		Principal user2                                = null;
		TestOne testOne                                = null;

		// setup phase
		try (final Tx tx = app.tx()) {

			group = app.create(Group.class, "Group");
			user1  = app.create(Principal.class, "Tester1");
			user2  = app.create(Principal.class, "Tester2");

			group.setProperty(members, Arrays.asList(new Principal[] { user1 } ));


			testOne = app.create(TestOne.class);
			createTestNodes(TestSix.class, 10);

			tx.success();

		} catch(FrameworkException fex) {

			logger.warn("", fex);
			fail("Unexpected exception.");
		}

		// test phase, find all the things using scripting
		try (final Tx tx = app.tx()) {

			final ActionContext actionContext = new ActionContext(securityContext);

			// test prerequisites
			assertEquals("Invalid prerequisite",     1, Iterables.count(group.getProperty(members)));
			assertEquals("Invalid prerequisite", user2, Scripting.evaluate(actionContext, group, "${{ return Structr.find('Principal', { name: 'Tester2' })[0]; }}", "test"));

			// test scripting association
			Scripting.evaluate(actionContext, group, "${{ var group = Structr.find('Group')[0]; var users = group.members; users.push(Structr.find('Principal', { name: 'Tester2' })[0]); }}", "test");
			assertEquals("Invalid scripted array operation result", 2, Iterables.count(group.getProperty(members)));

			// reset group
			group.setProperty(members, Arrays.asList(new Principal[] { user1 } ));

			// test prerequisites
			assertEquals("Invalid prerequisite",     1, Iterables.count(group.getProperty(members)));

			// test direct push on member property
			Scripting.evaluate(actionContext, group, "${{ var group = Structr.find('Group')[0]; group.members.push(Structr.find('Principal', { name: 'Tester2' })[0]); }}", "test");
			assertEquals("Invalid scripted array operation result", 2, Iterables.count(group.getProperty(members)));



			// test scripting association
			Scripting.evaluate(actionContext, group, "${{ var test = Structr.find('TestOne')[0]; var testSixs = test.manyToManyTestSixs; testSixs.push(Structr.find('TestSix')[0]); }}", "test");
			assertEquals("Invalid scripted array operation result", 1, Iterables.count(testOne.getProperty(TestOne.manyToManyTestSixs)));

			// test direct push on member property
			Scripting.evaluate(actionContext, group, "${{ var test = Structr.find('TestOne')[0]; var testSixs = test.manyToManyTestSixs.push(Structr.find('TestSix')[1]); }}", "test");
			assertEquals("Invalid scripted array operation result", 2, Iterables.count(testOne.getProperty(TestOne.manyToManyTestSixs)));


			tx.success();

		} catch(UnlicensedScriptException |FrameworkException fex) {

			logger.warn("", fex);
			fail("Unexpected exception.");
		}
	}

	@Test
	public void testPropertyConversion() {

		TestOne testOne = null;

		// setup phase
		try (final Tx tx = app.tx()) {

			testOne = app.create(TestOne.class);

			tx.success();

		} catch(FrameworkException fex) {

			logger.warn("", fex);
			fail("Unexpected exception.");
		}

		// test phase, check value conversion
		try (final Tx tx = app.tx()) {

			final ActionContext actionContext = new ActionContext(securityContext);

			Scripting.evaluate(actionContext, testOne, "${{ var e = Structr.get('this'); e.aString = 12; }}", "test");
			assertEquals("Invalid scripted property conversion result", "12", testOne.getProperty(TestOne.aString));

			Scripting.evaluate(actionContext, testOne, "${{ var e = Structr.get('this'); e.anInt = '12'; }}", "test");
			assertEquals("Invalid scripted property conversion result", 12L, (long)testOne.getProperty(TestOne.anInt));

			Scripting.evaluate(actionContext, testOne, "${{ var e = Structr.get('this'); e.aDouble = '12.2342'; }}", "test");
			assertEquals("Invalid scripted property conversion result", 12.2342, (double)testOne.getProperty(TestOne.aDouble), 0.0);

			Scripting.evaluate(actionContext, testOne, "${{ var e = Structr.get('this'); e.aDouble = 2; }}", "test");
			assertEquals("Invalid scripted property conversion result", 2.0, (double)testOne.getProperty(TestOne.aDouble), 0.0);

			Scripting.evaluate(actionContext, testOne, "${{ var e = Structr.get('this'); e.aLong = 2352343457252; }}", "test");
			assertEquals("Invalid scripted property conversion result", 2352343457252L, (long)testOne.getProperty(TestOne.aLong));

			Scripting.evaluate(actionContext, testOne, "${{ var e = Structr.get('this'); e.aBoolean = true; }}", "test");
			assertEquals("Invalid scripted property conversion result", true, (boolean)testOne.getProperty(TestOne.aBoolean));

			tx.success();

		} catch(UnlicensedScriptException |FrameworkException fex) {

			logger.warn("", fex);
			fail("Unexpected exception.");
		}
	}

	@Test
	public void testQuotes() {

		try (final Tx tx = app.tx()) {

			final ActionContext actionContext = new ActionContext(securityContext);

			Scripting.evaluate(actionContext, app.create(TestOne.class), "${{\n // \"test\n}}", "test");

			tx.success();

		} catch(UnlicensedScriptException |FrameworkException fex) {

			logger.warn("", fex);
			fail("Unexpected exception.");
		}
	}

	@Test
	public void testPython() {

		try (final Tx tx = app.tx()) {

			final Principal testUser = createTestNode(Principal.class, "testuser");
			final ActionContext ctx = new ActionContext(SecurityContext.getInstance(testUser, AccessMode.Backend));

			//assertEquals("Invalid python scripting evaluation result", "Hello World from Python!\n", Scripting.evaluate(ctx, null, "${python{print \"Hello World from Python!\"}}"));

			System.out.println(Scripting.evaluate(ctx, null, "${python{Structr.print(Structr.get('me').id)}}", "test"));

			tx.success();

		} catch (UnlicensedScriptException |FrameworkException fex) {

			logger.warn("", fex);
			fail("Unexpected exception.");
		}
	}

	@Test
	public void testVariableReplacement() {

		final Date now                    = new Date();
		final SimpleDateFormat format1    = new SimpleDateFormat("dd.MM.yyyy");
		final SimpleDateFormat format2    = new SimpleDateFormat("HH:mm:ss");
		final SimpleDateFormat format3    = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
		final String nowString1           = format1.format(now);
		final String nowString2           = format2.format(now);
		final String nowString3           = format3.format(now);
		final DecimalFormat numberFormat1 = new DecimalFormat("###0.00", DecimalFormatSymbols.getInstance(Locale.ENGLISH));
		final DecimalFormat numberFormat2 = new DecimalFormat("0000.0000", DecimalFormatSymbols.getInstance(Locale.GERMAN));
		final DecimalFormat numberFormat3 = new DecimalFormat("####", DecimalFormatSymbols.getInstance(Locale.SIMPLIFIED_CHINESE));
		final String numberString1        = numberFormat1.format(2.234);
		final String numberString2        = numberFormat2.format(2.234);
		final String numberString3        = numberFormat3.format(2.234);
		final List<String> testSixNames   = new LinkedList<>();
		NodeInterface template            = null;
		NodeInterface template2           = null;
		TestOne testOne                   = null;
		TestTwo testTwo                   = null;
		TestThree testThree               = null;
		TestFour testFour                 = null;
		List<TestSix> testSixs            = null;
		int index                         = 0;

		try (final Tx tx = app.tx()) {

			testOne        = createTestNode(TestOne.class);
			testTwo        = createTestNode(TestTwo.class);
			testThree      = createTestNode(TestThree.class);
			testFour       = createTestNode(TestFour.class);
			testSixs       = createTestNodes(TestSix.class, 20, 1);

			// set string array on test four
			testFour.setProperty(TestFour.stringArrayProperty, new String[] { "one", "two", "three", "four" } );

			final Calendar cal = GregorianCalendar.getInstance();

			// set calendar to 2018-01-01T00:00:00+0000
			cal.set(2018, 0, 1, 0, 0, 0);


			for (final TestSix testSix : testSixs) {

				final String name = "TestSix" + StringUtils.leftPad(Integer.toString(index), 2, "0");

				testSix.setProperty(TestSix.name, name);
				testSix.setProperty(TestSix.index, index);
				testSix.setProperty(TestSix.date, cal.getTime());

				index++;
				cal.add(Calendar.DAY_OF_YEAR, 3);

				// build list of names
				testSixNames.add(name);
			}

			// create mail template
			template = createTestNode(getType("MailTemplate"));
			template.setProperty(getKey("MailTemplate", "name"), "TEST");
			template.setProperty(getKey("MailTemplate", "locale"), "en_EN");
			template.setProperty(getKey("MailTemplate", "text"), "This is a template for ${this.name}");

			// create mail template
			template2 = createTestNode(getType("MailTemplate"));
			template2.setProperty(getKey("MailTemplate", "name"), "TEST2");
			template2.setProperty(getKey("MailTemplate", "locale"), "en_EN");
			template2.setProperty(getKey("MailTemplate", "text"), "${this.aDouble}");

			// check existance
			assertNotNull(testOne);

			testOne.setProperty(TestOne.name, "A-nice-little-name-for-my-test-object");
			testOne.setProperty(TestOne.anInt, 1);
			testOne.setProperty(TestOne.aString, "String");
			testOne.setProperty(TestOne.anotherString, "{\n\ttest: test,\n\tnum: 3\n}");
			testOne.setProperty(TestOne.replaceString, "${this.name}");
			testOne.setProperty(TestOne.aLong, 235242522552L);
			testOne.setProperty(TestOne.aDouble, 2.234);
			testOne.setProperty(TestOne.aDate, now);
			testOne.setProperty(TestOne.anEnum, TestOne.Status.One);
			testOne.setProperty(TestOne.aBoolean, true);
			testOne.setProperty(TestOne.testTwo, testTwo);
			testOne.setProperty(TestOne.testThree, testThree);
			testOne.setProperty(TestOne.testFour,  testFour);
			testOne.setProperty(TestOne.manyToManyTestSixs, testSixs);
			testOne.setProperty(TestOne.cleanTestString, "a<b>c.d'e?f(g)h{i}j[k]l+m/n–o\\p\\q|r's!t,u-v_w`x-y-zöäüßABCDEFGH");
			testOne.setProperty(TestOne.stringWithQuotes, "A'B\"C");
			testOne.setProperty(TestOne.aStringArray, new String[] { "a", "b", "c" });

			testTwo.setProperty(TestTwo.name, "testTwo_name");
			testThree.setProperty(TestThree.name, "testThree_name");

			tx.success();

		} catch (FrameworkException fex) {

			logger.warn("", fex);

			fail("Unexpected exception");
		}

		try (final Tx tx = app.tx()) {

			final ActionContext ctx = new ActionContext(securityContext, null);

			// test quotes etc.
			assertEquals("Invalid result for quoted template expression", "''", Scripting.replaceVariables(ctx, testOne, "'${err}'"));
			assertEquals("Invalid result for quoted template expression", " '' ", Scripting.replaceVariables(ctx, testOne, " '${err}' "));
			assertEquals("Invalid result for quoted template expression", "\"\"", Scripting.replaceVariables(ctx, testOne, "\"${this.error}\""));
			assertEquals("Invalid result for quoted template expression", "''''''", Scripting.replaceVariables(ctx, testOne, "'''${this.this.this.error}'''"));
			assertEquals("Invalid result for quoted template expression", "''", Scripting.replaceVariables(ctx, testOne, "'${parent.error}'"));
			assertEquals("Invalid result for quoted template expression", "''", Scripting.replaceVariables(ctx, testOne, "'${this.owner}'"));
			assertEquals("Invalid result for quoted template expression", "''", Scripting.replaceVariables(ctx, testOne, "'${this.alwaysNull}'"));
			assertEquals("Invalid result for quoted template expression", "''", Scripting.replaceVariables(ctx, testOne, "'${parent.owner}'"));

			// test for "empty" return value
			assertEquals("Invalid expressions should yield an empty result", "", Scripting.replaceVariables(ctx, testOne, "${err}"));
			assertEquals("Invalid expressions should yield an empty result", "", Scripting.replaceVariables(ctx, testOne, "${this.error}"));
			assertEquals("Invalid expressions should yield an empty result", "", Scripting.replaceVariables(ctx, testOne, "${this.this.this.error}"));
			assertEquals("Invalid expressions should yield an empty result", "", Scripting.replaceVariables(ctx, testOne, "${parent.error}"));
			assertEquals("Invalid expressions should yield an empty result", "", Scripting.replaceVariables(ctx, testOne, "${this.owner}"));
			assertEquals("Invalid expressions should yield an empty result", "", Scripting.replaceVariables(ctx, testOne, "${this.alwaysNull}"));
			assertEquals("Invalid expressions should yield an empty result", "", Scripting.replaceVariables(ctx, testOne, "${parent.owner}"));

			assertEquals("${this} should evaluate to the current node", testOne.toString(), Scripting.replaceVariables(ctx, testOne, "${this}"));
			//assertEquals("${parent} should evaluate to the context parent node", testOne.toString(), Scripting.replaceVariables(ctx, testOne, "${parent}"));

			assertEquals("${this} should evaluate to the current node", testTwo.toString(), Scripting.replaceVariables(ctx, testTwo, "${this}"));
			//assertEquals("${parent} should evaluate to the context parent node", testOne.toString(), Scripting.replaceVariables(ctx, testOne, "${parent}"));

			assertEquals("Invalid variable reference", testTwo.toString(),   Scripting.replaceVariables(ctx, testOne, "${this.testTwo}"));
			assertEquals("Invalid variable reference", testThree.toString(), Scripting.replaceVariables(ctx, testOne, "${this.testThree}"));
			assertEquals("Invalid variable reference", testFour.toString(),  Scripting.replaceVariables(ctx, testOne, "${this.testFour}"));

			assertEquals("Invalid variable reference", testTwo.getUuid(), Scripting.replaceVariables(ctx, testOne, "${this.testTwo.id}"));
			assertEquals("Invalid variable reference", testThree.getUuid(), Scripting.replaceVariables(ctx, testOne, "${this.testThree.id}"));
			assertEquals("Invalid variable reference", testFour.getUuid(), Scripting.replaceVariables(ctx, testOne, "${this.testFour.id}"));

			assertEquals("Invalid size result", "20", Scripting.replaceVariables(ctx, testOne, "${this.manyToManyTestSixs.size}"));

			try {

				Scripting.replaceVariables(ctx, testOne, "${(this.alwaysNull.size}");
				fail("A mismatched opening bracket should throw an exception.");

			} catch (FrameworkException fex) {

				final String expectedMessage = "TestOne[" + testOne.getUuid() + "]:script source:1:2\nInvalid expression: mismatched closing bracket after this.alwaysNull.size";
				assertEquals(expectedMessage, fex.getMessage());
			}

			assertEquals("Invalid size result", "", Scripting.replaceVariables(ctx, testOne, "${this.alwaysNull.size}"));

			assertEquals("Invalid variable reference", "1",            Scripting.replaceVariables(ctx, testOne, "${this.anInt}"));
			assertEquals("Invalid variable reference", "String",       Scripting.replaceVariables(ctx, testOne, "${this.aString}"));
			assertEquals("Invalid variable reference", "235242522552", Scripting.replaceVariables(ctx, testOne, "${this.aLong}"));
			assertEquals("Invalid variable reference", "2.234",        Scripting.replaceVariables(ctx, testOne, "${this.aDouble}"));

			// test with property
			assertEquals("Invalid md5() result", "27118326006d3829667a400ad23d5d98",  Scripting.replaceVariables(ctx, testOne, "${md5(this.aString)}"));
			assertEquals("Invalid upper() result", "27118326006D3829667A400AD23D5D98",  Scripting.replaceVariables(ctx, testOne, "${upper(md5(this.aString))}"));
			assertEquals("Invalid upper(lower() result", "27118326006D3829667A400AD23D5D98",  Scripting.replaceVariables(ctx, testOne, "${upper(lower(upper(md5(this.aString))))}"));

			assertEquals("Invalid md5() result with null value", "",  Scripting.replaceVariables(ctx, testOne, "${md5(this.alwaysNull)}"));
			assertEquals("Invalid upper() result with null value", "",  Scripting.replaceVariables(ctx, testOne, "${upper(this.alwaysNull)}"));
			assertEquals("Invalid lower() result with null value", "",  Scripting.replaceVariables(ctx, testOne, "${lower(this.alwaysNull)}"));

			// test literal value as well
			assertEquals("Invalid md5() result", "cc03e747a6afbbcbf8be7668acfebee5",  Scripting.replaceVariables(ctx, testOne, "${md5(\"test123\")}"));

			assertEquals("Invalid lower() result", "string",       Scripting.replaceVariables(ctx, testOne, "${lower(this.aString)}"));
			assertEquals("Invalid upper() result", "STRING",       Scripting.replaceVariables(ctx, testOne, "${upper(this.aString)}"));

			// merge
			assertEquals("Invalid merge() result", "[one, two, three]", Scripting.replaceVariables(ctx, testOne, "${merge('one', 'two', 'three')}"));
			assertEquals("Invalid merge() result", "[one, two, three, two, one, two, three]", Scripting.replaceVariables(ctx, testOne, "${merge(merge('one', 'two', 'three'), 'two', merge('one', 'two', 'three'))}"));
			assertEquals("Invalid merge() result", "[1, 2, 3, 4, 5, 6, 7, 8]", Scripting.replaceVariables(ctx, testOne, "${merge(merge('1', '2', '3'), merge('4', '5', merge('6', '7', '8')))}"));
			assertEquals("Invalid merge() result", "[1, 2, 3, 4, 5, 6, 1, 2, 3, 8]", Scripting.replaceVariables(ctx, testOne, "${ ( store('list', merge('1', '2', '3')), merge(retrieve('list'), merge('4', '5', merge('6', retrieve('list'), '8'))) )}"));

			// merge_unique
			assertEquals("Invalid merge_unique() result", "[one, two, three]", Scripting.replaceVariables(ctx, testOne, "${merge_unique('one', 'two', 'three', 'two')}"));
			assertEquals("Invalid merge_unique() result", "[one, two, three]", Scripting.replaceVariables(ctx, testOne, "${merge_unique(merge_unique('one', 'two', 'three'), 'two', merge_unique('one', 'two', 'three'))}"));
			assertEquals("Invalid merge_unique() result", "[1, 2, 3, 4, 5, 6, 7, 8]", Scripting.replaceVariables(ctx, testOne, "${merge_unique(merge_unique('1', '2', '3'), merge_unique('4', '5', merge_unique('6', '7', '8')))}"));
			assertEquals("Invalid merge_unique() result", "[1, 2, 3, 4, 5, 6, 8]", Scripting.replaceVariables(ctx, testOne, "${ ( store('list', merge_unique('1', '2', '3')), merge_unique(retrieve('list'), merge_unique('4', '5', merge_unique('6', retrieve('list'), '8'))) )}"));

			// complement
			assertEquals("Invalid complement() result", "[]", Scripting.replaceVariables(ctx, testOne, "${complement(merge('one', 'two', 'three'), 'one', merge('two', 'three', 'four'))}"));
			assertEquals("Invalid complement() result", "[two]", Scripting.replaceVariables(ctx, testOne, "${complement(merge('one', 'two', 'three'), merge('one', 'four', 'three'))}"));

			assertEquals("Invalid complement() result", "[two, two]", Scripting.replaceVariables(ctx, testOne, "${complement(merge('one', 'two', 'three', 'two'), merge('one', 'four', 'three'))}"));
			assertEquals("Invalid complement() result", "[one]", Scripting.replaceVariables(ctx, testOne, "${complement(merge('one', 'two', 'three', 'two'), merge('two', 'four', 'three'))}"));
			assertEquals("Invalid complement() result", "[one, three]", Scripting.replaceVariables(ctx, testOne, "${complement(merge('one', 'two', 'three', 'two'), 'two')}"));

			// join
			assertEquals("Invalid join() result", "one,two,three", Scripting.replaceVariables(ctx, testOne, "${join(merge(\"one\", \"two\", \"three\"), \",\")}"));

			// concat
			assertEquals("Invalid concat() result", "onetwothree", Scripting.replaceVariables(ctx, testOne, "${concat(\"one\", \"two\", \"three\")}"));
			assertEquals("Invalid concat() result", "oneStringthree", Scripting.replaceVariables(ctx, testOne, "${concat(\"one\", this.aString, \"three\")}"));
			assertEquals("Invalid concat() result with null value", "", Scripting.replaceVariables(ctx, testOne, "${concat(this.alwaysNull, this.alwaysNull)}"));

			// split
			assertEquals("Invalid split() result", "onetwothree", Scripting.replaceVariables(ctx, testOne, "${concat(split(\"one,two,three\"))}"));
			assertEquals("Invalid split() result", "onetwothree", Scripting.replaceVariables(ctx, testOne, "${concat(split(\"one;two;three\"))}"));
			assertEquals("Invalid split() result", "onetwothree", Scripting.replaceVariables(ctx, testOne, "${concat(split(\"one two three\"))}"));
			assertEquals("Invalid split() result", "onetwothree", Scripting.replaceVariables(ctx, testOne, "${concat(split(\"one	two	three\"))}"));
			assertEquals("Invalid split() result", "onetwothree", Scripting.replaceVariables(ctx, testOne, "${concat(split(\"one;two;three\", \";\"))}"));
			assertEquals("Invalid split() result", "onetwothree", Scripting.replaceVariables(ctx, testOne, "${concat(split(\"one,two,three\", \",\"))}"));
			assertEquals("Invalid split() result", "onetwothree", Scripting.replaceVariables(ctx, testOne, "${concat(split(\"one.two.three\", \".\"))}"));
			assertEquals("Invalid split() result", "onetwothree", Scripting.replaceVariables(ctx, testOne, "${concat(split(\"one two three\", \" \"))}"));
			assertEquals("Invalid split() result", "onetwothree", Scripting.replaceVariables(ctx, testOne, "${concat(split(\"one+two+three\", \"+\"))}"));
			assertEquals("Invalid split() result", "onetwothree", Scripting.replaceVariables(ctx, testOne, "${concat(split(\"one|two|three\", \"|\"))}"));
			assertEquals("Invalid split() result", "onetwothree", Scripting.replaceVariables(ctx, testOne, "${concat(split(\"one::two::three\", \"::\"))}"));
			assertEquals("Invalid split() result", "onetwothree", Scripting.replaceVariables(ctx, testOne, "${concat(split(\"one-two-three\", \"-\"))}"));
			assertEquals("Invalid split() result with null value", "", Scripting.replaceVariables(ctx, testOne, "${split(this.alwaysNull)}"));

			// split_regex
			assertEquals("Invalid split_regex() result", "onetwothree", Scripting.replaceVariables(ctx, testOne, "${concat(split(\"one,two,three\"))}"));
			assertEquals("Invalid split_regex() result", "onetwothree", Scripting.replaceVariables(ctx, testOne, "${concat(split(\"one;two;three\"))}"));
			assertEquals("Invalid split_regex() result", "onetwothree", Scripting.replaceVariables(ctx, testOne, "${concat(split(\"one two three\"))}"));
			assertEquals("Invalid split_regex() result", "onetwothree", Scripting.replaceVariables(ctx, testOne, "${concat(split(\"one	two	three\"))}"));
			assertEquals("Invalid split_regex() result", "onetwothree", Scripting.replaceVariables(ctx, testOne, "${concat(split(\"one;two;three\", \";\"))}"));
			assertEquals("Invalid split_regex() result", "onetwothree", Scripting.replaceVariables(ctx, testOne, "${concat(split(\"one,two,three\", \",\"))}"));
			assertEquals("Invalid split_regex() result", "onetwothree", Scripting.replaceVariables(ctx, testOne, "${concat(split(\"one.two.three\", \"\\.\"))}"));
			assertEquals("Invalid split_regex() result", "onetwothree", Scripting.replaceVariables(ctx, testOne, "${concat(split(\"one two three\", \" \"))}"));
			assertEquals("Invalid split_regex() result", "onetwothree", Scripting.replaceVariables(ctx, testOne, "${concat(split(\"one+two+three\", \"+\"))}"));
			assertEquals("Invalid split_regex() result", "onetwothree", Scripting.replaceVariables(ctx, testOne, "${concat(split(\"one|two|three\", \"|\"))}"));
			assertEquals("Invalid split_regex() result", "onetwothree", Scripting.replaceVariables(ctx, testOne, "${concat(split(\"one::two::three\", \"::\"))}"));
			assertEquals("Invalid split_regex() result", "onetwothree", Scripting.replaceVariables(ctx, testOne, "${concat(split(\"one-two-three\", \"-\"))}"));
			assertEquals("Invalid split_regex() result with null value", "", Scripting.replaceVariables(ctx, testOne, "${split(this.alwaysNull)}"));

			// abbr
			assertEquals("Invalid abbr() result", "oneStringt…", Scripting.replaceVariables(ctx, testOne, "${abbr(concat(\"one\", this.aString, \"three\"), 10)}"));
			assertEquals("Invalid abbr() result with null value", "", Scripting.replaceVariables(ctx, testOne, "${abbr(this.alwaysNull, 10)}"));

			// capitalize..
			assertEquals("Invalid capitalize() result", "One_two_three", Scripting.replaceVariables(ctx, testOne, "${capitalize(concat(\"one_\", \"two_\", \"three\"))}"));
			assertEquals("Invalid capitalize() result", "One_Stringthree", Scripting.replaceVariables(ctx, testOne, "${capitalize(concat(\"one_\", this.aString, \"three\"))}"));
			assertEquals("Invalid capitalize() result with null value", "", Scripting.replaceVariables(ctx, testOne, "${capitalize(this.alwaysNull)}"));

			// titleize
			assertEquals("Invalid titleize() result", "One Two Three", Scripting.replaceVariables(ctx, testOne, "${titleize(concat(\"one_\", \"two_\", \"three\"), \"_\")}"));
			assertEquals("Invalid titleize() result", "One Stringthree", Scripting.replaceVariables(ctx, testOne, "${titleize(concat(\"one_\", this.aString, \"three\"), \"_\")}"));
			assertEquals("Invalid titleize() result with null value", "", Scripting.replaceVariables(ctx, testOne, "${titleize(this.alwaysNull)}"));

			// num (explicit number conversion)
			assertEquals("Invalid num() result", "2.234", Scripting.replaceVariables(ctx, testOne, "${num(2.234)}"));
			assertEquals("Invalid num() result", "2.234", Scripting.replaceVariables(ctx, testOne, "${num(this.aDouble)}"));
			assertEquals("Invalid num() result", "1.0", Scripting.replaceVariables(ctx, testOne, "${num(this.anInt)}"));
			assertEquals("Invalid num() result", "", Scripting.replaceVariables(ctx, testOne, "${num(\"abc\")}"));
			assertEquals("Invalid num() result", "", Scripting.replaceVariables(ctx, testOne, "${num(this.aString)}"));
			assertEquals("Invalid num() result with null value", "", Scripting.replaceVariables(ctx, testOne, "${num(this.alwaysNull)}"));

			// index_of
			assertEquals("Invalid index_of() result", "19", Scripting.replaceVariables(ctx, testOne, "${index_of(this.name, 'for')}"));
			assertEquals("Invalid index_of() result", "-1", Scripting.replaceVariables(ctx, testOne, "${index_of(this.name, 'entity')}"));
			assertEquals("Invalid index_of() result", "19", Scripting.replaceVariables(ctx, testOne, "${index_of('a-nice-little-name-for-my-test-object', 'for')}"));
			assertEquals("Invalid index_of() result", "-1", Scripting.replaceVariables(ctx, testOne, "${index_of('a-nice-little-name-for-my-test-object', 'entity')}"));

			// contains
			assertEquals("Invalid contains() result", "true", Scripting.replaceVariables(ctx, testOne, "${contains(this.name, 'for')}"));
			assertEquals("Invalid contains() result", "false", Scripting.replaceVariables(ctx, testOne, "${contains(this.name, 'entity')}"));
			assertEquals("Invalid contains() result", "true", Scripting.replaceVariables(ctx, testOne, "${contains('a-nice-little-name-for-my-test-object', 'for')}"));
			assertEquals("Invalid contains() result", "false", Scripting.replaceVariables(ctx, testOne, "${contains('a-nice-little-name-for-my-test-object', 'entity')}"));

			// contains with collection / entity
			assertEquals("Invalid contains() result", "true", Scripting.replaceVariables(ctx, testOne, "${contains(this.manyToManyTestSixs, first(find('TestSix')))}"));
			assertEquals("Invalid contains() result", "false", Scripting.replaceVariables(ctx, testOne, "${contains(this.manyToManyTestSixs, first(find('TestFive')))}"));

			// starts_with
			assertEquals("Invalid starts_with() result", "true", Scripting.replaceVariables(ctx, testOne, "${starts_with(null, null)}"));
			assertEquals("Invalid starts_with() result", "false", Scripting.replaceVariables(ctx, testOne, "${starts_with(null, 'abc')}"));
			assertEquals("Invalid starts_with() result", "false", Scripting.replaceVariables(ctx, testOne, "${starts_with('abcdef', null)}"));
			assertEquals("Invalid starts_with() result", "true", Scripting.replaceVariables(ctx, testOne, "${starts_with('abcdef', 'abc')}"));
			assertEquals("Invalid starts_with() result", "false", Scripting.replaceVariables(ctx, testOne, "${starts_with('ABCDEF', 'abc')}"));
			assertEquals("Invalid starts_with() result", "true", Scripting.replaceVariables(ctx, testOne, "${starts_with(merge('a', 'b'), 'a')}"));
			assertEquals("Invalid starts_with() result", "false", Scripting.replaceVariables(ctx, testOne, "${starts_with(merge('c', 'a', 'b'), 'a')}"));
			assertEquals("Invalid starts_with() result", "false", Scripting.replaceVariables(ctx, testOne, "${starts_with(merge('abc', 'b'), 'a')}"));

			// substring
			assertEquals("Invalid substring() result", "for", Scripting.replaceVariables(ctx, testOne, "${substring(this.name, 19, 3)}"));
			assertEquals("Invalid substring() result", "", Scripting.replaceVariables(ctx, testOne, "${substring(this.name, -1, -1)}"));
			assertEquals("Invalid substring() result", "", Scripting.replaceVariables(ctx, testOne, "${substring(this.name, 100, -1)}"));
			assertEquals("Invalid substring() result", "", Scripting.replaceVariables(ctx, testOne, "${substring(this.name, 5, -2)}"));
			assertEquals("Invalid substring() result", "for", Scripting.replaceVariables(ctx, testOne, "${substring('a-nice-little-name-for-my-test-object', 19, 3)}"));
			assertEquals("Invalid substring() result", "ice-little-name-for-my-test-object", Scripting.replaceVariables(ctx, testOne, "${substring('a-nice-little-name-for-my-test-object', 3)}"));
			assertEquals("Invalid substring() result", "ice", Scripting.replaceVariables(ctx, testOne, "${substring('a-nice-little-name-for-my-test-object', 3, 3)}"));
			assertEquals("Invalid substring() result", "", Scripting.replaceVariables(ctx, testOne, "${substring('a-nice-little-name-for-my-test-object', -1, -1)}"));
			assertEquals("Invalid substring() result", "", Scripting.replaceVariables(ctx, testOne, "${substring('a-nice-little-name-for-my-test-object', 100, -1)}"));
			assertEquals("Invalid substring() result", "", Scripting.replaceVariables(ctx, testOne, "${substring('a-nice-little-name-for-my-test-object', 5, -2)}"));
			assertEquals("Invalid substring() result", "y-short", Scripting.replaceVariables(ctx, testOne, "${substring('very-short', 3, 200)}"));

			// length
			assertEquals("Invalid length() result", "37", Scripting.replaceVariables(ctx, testOne, "${length(this.name)}"));
			assertEquals("Invalid length() result", "37", Scripting.replaceVariables(ctx, testOne, "${length('a-nice-little-name-for-my-test-object')}"));
			assertEquals("Invalid length() result", "4", Scripting.replaceVariables(ctx, testOne, "${length('test')}"));
			assertEquals("Invalid length() result", "", Scripting.replaceVariables(ctx, testOne, "${length(this.alwaysNull)}"));

			// clean
			assertEquals("Invalid clean() result", "abcd-efghijkl-m-n-o-p-q-r-stu-v-w-x-y-zoauabcdefgh", Scripting.replaceVariables(ctx, testOne, "${clean(this.cleanTestString)}"));
			assertEquals("Invalid clean() result", "abcd-efghijkl-m-n-o-p-q-r-stu-v-w-x-y-zoauabcdefgh", Scripting.replaceVariables(ctx, testOne, "${clean(get(this, \"cleanTestString\"))}"));
			assertEquals("Invalid clean() result with null value", "", Scripting.replaceVariables(ctx, testOne, "${clean(this.alwaysNull)}"));

			// trim
			assertEquals("Invalid trim() result", "test", Scripting.replaceVariables(ctx, testOne, "${trim('   \t\t\t\r\r\r\n\n\ntest')}"));
			assertEquals("Invalid trim() result", "test", Scripting.replaceVariables(ctx, testOne, "${trim('test   \t\t\t\r\r\r\n\n\n')}"));
			assertEquals("Invalid trim() result", "test", Scripting.replaceVariables(ctx, testOne, "${trim('   \t\t\t\r\r\r\n\n\ntest   \t\t\t\r\r\r\n\n\n')}"));
			assertEquals("Invalid trim() result", "test", Scripting.replaceVariables(ctx, testOne, "${trim('   test   ')}"));
			assertEquals("Invalid trim() result with null value", "", Scripting.replaceVariables(ctx, testOne, "${trim(null)}"));
			assertEquals("Invalid trim() result with null value", "", Scripting.replaceVariables(ctx, testOne, "${trim(this.alwaysNull)}"));

			// urlencode
			assertEquals("Invalid urlencode() result", "a%3Cb%3Ec.d%27e%3Ff%28g%29h%7Bi%7Dj%5Bk%5Dl%2Bm%2Fn%E2%80%93o%5Cp%5Cq%7Cr%27s%21t%2Cu-v_w%60x-y-z%C3%B6%C3%A4%C3%BC%C3%9FABCDEFGH", Scripting.replaceVariables(ctx, testOne, "${urlencode(this.cleanTestString)}"));
			assertEquals("Invalid urlencode() result", "a%3Cb%3Ec.d%27e%3Ff%28g%29h%7Bi%7Dj%5Bk%5Dl%2Bm%2Fn%E2%80%93o%5Cp%5Cq%7Cr%27s%21t%2Cu-v_w%60x-y-z%C3%B6%C3%A4%C3%BC%C3%9FABCDEFGH", Scripting.replaceVariables(ctx, testOne, "${urlencode(get(this, \"cleanTestString\"))}"));
			assertEquals("Invalid urlencode() result with null value", "", Scripting.replaceVariables(ctx, testOne, "${urlencode(this.alwaysNull)}"));

			// escape_javascript
			assertEquals("Invalid escape_javascript() result", "A\\'B\\\"C", Scripting.replaceVariables(ctx, testOne, "${escape_javascript(this.stringWithQuotes)}"));
			assertEquals("Invalid escape_javascript() result with null value", "", Scripting.replaceVariables(ctx, testOne, "${escape_javascript(this.alwaysNull)}"));

			// escape_json
			assertEquals("Invalid escape_json() result", "A'B\\\"C", Scripting.replaceVariables(ctx, testOne, "${escape_json(this.stringWithQuotes)}"));
			assertEquals("Invalid escape_json() result with null value", "", Scripting.replaceVariables(ctx, testOne, "${escape_json(this.alwaysNull)}"));

			// is
			assertEquals("Invalid is() result", "true",  Scripting.replaceVariables(ctx, testOne,  "${is(\"true\", \"true\")}"));
			assertEquals("Invalid is() result", "",      Scripting.replaceVariables(ctx, testOne,  "${is(\"false\", \"true\")}"));

			// is + equal
			assertEquals("Invalid is(equal()) result", "true",  Scripting.replaceVariables(ctx, testOne, "${is(equal(this.id, this.id), \"true\")}"));
			assertEquals("Invalid is(equal()) result", "true",  Scripting.replaceVariables(ctx, testOne, "${is(equal(\"abc\", \"abc\"), \"true\")}"));
			assertEquals("Invalid is(equal()) result", "true",  Scripting.replaceVariables(ctx, testOne, "${is(equal(3, 3), \"true\")}"));
			assertEquals("Invalid is(equal()) result", "true",  Scripting.replaceVariables(ctx, testOne, "${is(equal(\"3\", \"3\"), \"true\")}"));
			assertEquals("Invalid is(equal()) result", "true",  Scripting.replaceVariables(ctx, testOne, "${is(equal(3.1414, 3.1414), \"true\")}"));
			assertEquals("Invalid is(equal()) result", "true",  Scripting.replaceVariables(ctx, testOne, "${is(equal(\"3.1414\", \"3.1414\"), \"true\")}"));
			assertEquals("Invalid is(equal()) result", "true",  Scripting.replaceVariables(ctx, testOne, "${is(equal(23.44242222243633337234623462, 23.44242222243633337234623462), \"true\")}"));
			assertEquals("Invalid is(equal()) result", "true",  Scripting.replaceVariables(ctx, testOne, "${is(equal(\"23.44242222243633337234623462\", \"23.44242222243633337234623462\"), \"true\")}"));
			assertEquals("Invalid is(equal()) result", "true",  Scripting.replaceVariables(ctx, testOne, "${is(equal(13, 013), \"true\")}"));
			assertEquals("Invalid is(equal()) result", "true",  Scripting.replaceVariables(ctx, testOne, "${is(equal(13, \"013\"), \"true\")}"));
			assertEquals("Invalid is(equal()) result", "",      Scripting.replaceVariables(ctx, testOne, "${is(equal(\"13\", \"013\"), \"true\")}"));
			assertEquals("Invalid is(equal()) result", "",      Scripting.replaceVariables(ctx, testOne, "${is(equal(\"13\", \"00013\"), \"true\")}"));

			// if etc.
			assertEquals("Invalid if() result", "true",  Scripting.replaceVariables(ctx, testOne,  "${if(\"true\", \"true\", \"false\")}"));
			assertEquals("Invalid if() result", "false", Scripting.replaceVariables(ctx, testOne,  "${if(\"false\", \"true\", \"false\")}"));

			// empty
			assertEquals("Invalid empty() result", "true",  Scripting.replaceVariables(ctx, testOne,  "${empty(\"\")}"));
			assertEquals("Invalid empty() result", "false",  Scripting.replaceVariables(ctx, testOne, "${empty(\" \")}"));
			assertEquals("Invalid empty() result", "false",  Scripting.replaceVariables(ctx, testOne, "${empty(\"   \")}"));
			assertEquals("Invalid empty() result", "false",  Scripting.replaceVariables(ctx, testOne, "${empty(\"xyz\")}"));
			assertEquals("Invalid empty() result with null value", "true", Scripting.replaceVariables(ctx, testOne, "${empty(this.alwaysNull)}"));

			assertEquals("Invalid if(empty()) result", "false",  Scripting.replaceVariables(ctx, testOne,  "${if(empty(\"test\"), true, false)}"));
			assertEquals("Invalid if(empty()) result", "false",  Scripting.replaceVariables(ctx, testOne,  "${if(empty(\"test\n\"), true, false)}"));

			// functions can NOT handle literal strings containing newlines  (disabled for now, because literal strings pose problems in the matching process)
			assertEquals("Invalid if(empty()) result", "false",  Scripting.replaceVariables(ctx, testOne,  "${if(empty(\"\n\"), true, false)}"));
			assertEquals("Invalid if(empty()) result", "false",  Scripting.replaceVariables(ctx, testOne,  "${if(empty(\"\n\"), \"true\", \"false\")}"));

			// functions CAN handle variable values with newlines!
			assertEquals("Invalid if(empty()) result", "false",  Scripting.replaceVariables(ctx, testOne,  "${if(empty(this.anotherString), \"true\", \"false\")}"));

			// empty in JavaScript
			assertEquals("Invalid empty() result", "true",  Scripting.replaceVariables(ctx, testOne, "${{return $.empty(\"\")}}"));
			assertEquals("Invalid empty() result", "false", Scripting.replaceVariables(ctx, testOne, "${{return $.empty(\" \")}}"));
			assertEquals("Invalid empty() result", "false", Scripting.replaceVariables(ctx, testOne, "${{return $.empty(\"   \")}}"));
			assertEquals("Invalid empty() result", "false", Scripting.replaceVariables(ctx, testOne, "${{return $.empty(\"xyz\")}}"));
			assertEquals("Invalid empty() result", "true",  Scripting.replaceVariables(ctx, testOne, "${{return $.empty([])}}"));
			assertEquals("Invalid empty() result", "true",  Scripting.replaceVariables(ctx, testOne, "${{return $.empty({})}}"));

			// equal
			assertEquals("Invalid equal() result", "true",  Scripting.replaceVariables(ctx, testOne, "${equal(this.id, this.id)}"));
			assertEquals("Invalid equal() result", "true",  Scripting.replaceVariables(ctx, testOne, "${equal(\"1\", this.anInt)}"));
			assertEquals("Invalid equal() result", "true",  Scripting.replaceVariables(ctx, testOne, "${equal(1, this.anInt)}"));
			assertEquals("Invalid equal() result", "true",  Scripting.replaceVariables(ctx, testOne, "${equal(1.0, this.anInt)}"));
			assertEquals("Invalid equal() result", "true",  Scripting.replaceVariables(ctx, testOne, "${equal(this.anInt, \"1\")}"));
			assertEquals("Invalid equal() result", "true",  Scripting.replaceVariables(ctx, testOne, "${equal(this.anInt, 1)}"));
			assertEquals("Invalid equal() result", "true",  Scripting.replaceVariables(ctx, testOne, "${equal(this.anInt, 1.0)}"));
			assertEquals("Invalid equal() result", "true",  Scripting.replaceVariables(ctx, testOne, "${equal(this.aBoolean, \"true\")}"));
			assertEquals("Invalid equal() result", "false",  Scripting.replaceVariables(ctx, testOne, "${equal(this.aBoolean, \"false\")}"));
			assertEquals("Invalid equal() result", "true",  Scripting.replaceVariables(ctx, testOne, "${equal(this.aBoolean, true)}"));
			assertEquals("Invalid equal() result", "false",  Scripting.replaceVariables(ctx, testOne, "${equal(this.aBoolean, false)}"));
			assertEquals("Invalid equal() result", "true",  Scripting.replaceVariables(ctx, testOne, "${equal(this.anEnum, 'One')}"));

			// if + equal
			assertEquals("Invalid if(equal()) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(equal(this.id, this.id), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal()) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(equal(\"abc\", \"abc\"), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal()) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(equal(3, 3), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal()) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(equal(\"3\", \"3\"), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal()) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(equal(3.1414, 3.1414), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal()) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(equal(\"3.1414\", \"3.1414\"), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal()) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(equal(23.44242222243633337234623462, 23.44242222243633337234623462), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal()) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(equal(\"23.44242222243633337234623462\", \"23.44242222243633337234623462\"), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal()) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(equal(13, 013), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal()) result", "true", Scripting.replaceVariables(ctx, testOne, "${if(equal(13, \"013\"), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal()) result", "false", Scripting.replaceVariables(ctx, testOne, "${if(equal(\"13\", \"013\"), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal()) result", "false",  Scripting.replaceVariables(ctx, testOne, "${if(equal(\"13\", \"00013\"), \"true\", \"false\")}"));

			// disabled: java StreamTokenizer can NOT handle scientific notation
//			assertEquals("Invalid if(equal()) result", "true",  Scripting.replaceVariables(ctx, testOne, "${equal(23.4462, 2.34462e1)}"));
//			assertEquals("Invalid if(equal()) result", "true",  Scripting.replaceVariables(ctx, testOne, "${equal(0.00234462, 2.34462e-3)}"));
//			aFunction.logException(logger, ex, "Error in batch error handler: {}", new Object[]{ex.getMessage()});
			assertEquals("Invalid if(equal()) result with null value", "false",  Scripting.replaceVariables(ctx, testOne, "${equal(0.00234462, this.alwaysNull)}"));
			assertEquals("Invalid if(equal()) result with null value", "true",  Scripting.replaceVariables(ctx, testOne, "${equal(this.alwaysNull, this.alwaysNull)}"));

			// if + equal + add
			assertEquals("Invalid if(equal(add())) result", "true", Scripting.replaceVariables(ctx, testOne, "${if(equal(\"2\", add(\"1\", \"1\")), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal(add())) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(equal(2, add(\"1\", \"1\")), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal(add())) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(equal(2, add(1, 1)), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal(add())) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(equal(2, add(\"1\", 1)), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal(add())) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(equal(2, add(1, \"1\")), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal(add())) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(equal(2, add(1, 1.0)), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal(add())) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(equal(2.0, add(\"1\", \"1\")), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal(add())) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(equal(2.0, add(1, 1)), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal(add())) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(equal(2.0, add(\"1\", 1)), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal(add())) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(equal(2.0, add(1, \"1\")), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal(add())) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(equal(2.0, add(1, 1.0)), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal(add())) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(equal(20, add(\"10\", \"10\")), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal(add())) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(equal(20, add(\"10\", \"010\")), \"true\", \"false\")}"));

			// eq
			assertEquals("Invalideq) result", "true",  Scripting.replaceVariables(ctx, testOne, "${eq(this.id, this.id)}"));
			assertEquals("Invalid eq() result", "true",  Scripting.replaceVariables(ctx, testOne, "${eq(\"1\", this.anInt)}"));
			assertEquals("Invalid eq() result", "true",  Scripting.replaceVariables(ctx, testOne, "${eq(1, this.anInt)}"));
			assertEquals("Invalid eq() result", "true",  Scripting.replaceVariables(ctx, testOne, "${eq(1.0, this.anInt)}"));
			assertEquals("Invalid eq() result", "true",  Scripting.replaceVariables(ctx, testOne, "${eq(this.anInt, \"1\")}"));
			assertEquals("Invalid eq() result", "true",  Scripting.replaceVariables(ctx, testOne, "${eq(this.anInt, 1)}"));
			assertEquals("Invalid eq() result", "true",  Scripting.replaceVariables(ctx, testOne, "${eq(this.anInt, 1.0)}"));
			assertEquals("Invalid eq() result", "true",  Scripting.replaceVariables(ctx, testOne, "${eq(this.aBoolean, \"true\")}"));
			assertEquals("Invalid eq() result", "false",  Scripting.replaceVariables(ctx, testOne, "${eq(this.aBoolean, \"false\")}"));
			assertEquals("Invalid eq() result", "true",  Scripting.replaceVariables(ctx, testOne, "${eq(this.aBoolean, true)}"));
			assertEquals("Invalid eq() result", "false",  Scripting.replaceVariables(ctx, testOne, "${eq(this.aBoolean, false)}"));
			assertEquals("Invalid eq() result", "true",  Scripting.replaceVariables(ctx, testOne, "${eq(this.anEnum, 'One')}"));
			assertEquals("Invalid eq() result", "true",  Scripting.replaceVariables(ctx, testOne, "${eq('', '')}"));

			// eq with empty string and number
			assertEquals("Invalid eq() result", "false",  Scripting.replaceVariables(ctx, testOne, "${eq(3, '')}"));
			assertEquals("Invalid eq() result", "false",  Scripting.replaceVariables(ctx, testOne, "${eq('', 12.3456)}"));

			// eq with null
			assertEquals("Invalid eq() result", "true",  Scripting.replaceVariables(ctx, testOne, "${eq(this.alwaysNull, this.alwaysNull)}"));
			assertEquals("Invalid eq() result", "false",  Scripting.replaceVariables(ctx, testOne, "${eq(this.alwaysNull, 'xyz')}"));
			assertEquals("Invalid eq() result", "false",  Scripting.replaceVariables(ctx, testOne, "${eq('xyz', this.alwaysNull)}"));

			// if + eq
			assertEquals("Invalid if(eq()) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(eq(this.id, this.id), \"true\", \"false\")}"));
			assertEquals("Invalid if(eq()) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(eq(\"abc\", \"abc\"), \"true\", \"false\")}"));
			assertEquals("Invalid if(eq()) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(eq(3, 3), \"true\", \"false\")}"));
			assertEquals("Invalid if(eq()) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(eq(\"3\", \"3\"), \"true\", \"false\")}"));
			assertEquals("Invalid if(eq()) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(eq(3.1414, 3.1414), \"true\", \"false\")}"));
			assertEquals("Invalid if(eq()) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(eq(\"3.1414\", \"3.1414\"), \"true\", \"false\")}"));
			assertEquals("Invalid if(eq()) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(eq(23.44242222243633337234623462, 23.44242222243633337234623462), \"true\", \"false\")}"));
			assertEquals("Invalid if(eq()) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(eq(\"23.44242222243633337234623462\", \"23.44242222243633337234623462\"), \"true\", \"false\")}"));
			assertEquals("Invalid if(eq()) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(eq(13, 013), \"true\", \"false\")}"));
			assertEquals("Invalid if(eq()) result", "true", Scripting.replaceVariables(ctx, testOne, "${if(eq(13, \"013\"), \"true\", \"false\")}"));
			assertEquals("Invalid if(eq()) result", "false", Scripting.replaceVariables(ctx, testOne, "${if(eq(\"13\", \"013\"), \"true\", \"false\")}"));
			assertEquals("Invalid if(eq()) result", "false",  Scripting.replaceVariables(ctx, testOne, "${if(eq(\"13\", \"00013\"), \"true\", \"false\")}"));

			// disabled: java StreamTokenizer can NOT handle scientific notation
//			assertEquals("Invalid if(eq()) result", "true",  Scripting.replaceVariables(ctx, testOne, "${eq(23.4462, 2.34462e1)}"));
//			assertEquals("Invalid if(eq()) result", "true",  Scripting.replaceVariables(ctx, testOne, "${eq(0.00234462, 2.34462e-3)}"));
//			assertEquals("Invalid if(eq()) result with null value", "false",  Scripting.replaceVariables(ctx, testOne, "${eq(this.alwaysNull, 2.34462e-3)}"));
			assertEquals("Invalid if(eq()) result with null value", "false",  Scripting.replaceVariables(ctx, testOne, "${eq(0.00234462, this.alwaysNull)}"));
			assertEquals("Invalid if(eq()) result with null value", "true",  Scripting.replaceVariables(ctx, testOne, "${eq(this.alwaysNull, this.alwaysNull)}"));

			// if + eq + add
			assertEquals("Invalid if(eq(add())) result", "true", Scripting.replaceVariables(ctx, testOne, "${if(eq(\"2\", add(\"1\", \"1\")), \"true\", \"false\")}"));
			assertEquals("Invalid if(eq(add())) result", "false", Scripting.replaceVariables(ctx, testOne, "${if(eq(\"2\", add(\"2\", \"1\")), \"true\", \"false\")}"));
			assertEquals("Invalid if(eq(add())) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(eq(2, add(\"1\", \"1\")), \"true\", \"false\")}"));
			assertEquals("Invalid if(eq(add())) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(eq(2, add(1, 1)), \"true\", \"false\")}"));
			assertEquals("Invalid if(eq(add())) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(eq(2, add(\"1\", 1)), \"true\", \"false\")}"));
			assertEquals("Invalid if(eq(add())) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(eq(2, add(1, \"1\")), \"true\", \"false\")}"));
			assertEquals("Invalid if(eq(add())) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(eq(2, add(1, 1.0)), \"true\", \"false\")}"));
			assertEquals("Invalid if(eq(add())) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(eq(2.0, add(\"1\", \"1\")), \"true\", \"false\")}"));
			assertEquals("Invalid if(eq(add())) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(eq(2.0, add(1, 1)), \"true\", \"false\")}"));
			assertEquals("Invalid if(eq(add())) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(eq(2.0, add(\"1\", 1)), \"true\", \"false\")}"));
			assertEquals("Invalid if(eq(add())) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(eq(2.0, add(1, \"1\")), \"true\", \"false\")}"));
			assertEquals("Invalid if(eq(add())) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(eq(2.0, add(1, 1.0)), \"true\", \"false\")}"));
			assertEquals("Invalid if(eq(add())) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(eq(20, add(\"10\", \"10\")), \"true\", \"false\")}"));
			assertEquals("Invalid if(eq(add())) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(eq(20, add(\"10\", \"010\")), \"true\", \"false\")}"));


			// add with null
			assertEquals("Invalid add() result with null value", "10.0",  Scripting.replaceVariables(ctx, testOne, "${add(\"10\", this.alwaysNull)}"));
			assertEquals("Invalid add() result with null value", "11.0",  Scripting.replaceVariables(ctx, testOne, "${add(this.alwaysNull, \"11\")}"));
			assertEquals("Invalid add() result with null value", "0.0",  Scripting.replaceVariables(ctx, testOne, "${add(this.alwaysNull, this.alwaysNull)}"));

			// if + lt
			assertEquals("Invalid if(lt()) result", "false", Scripting.replaceVariables(ctx, testOne, "${if(lt(\"2\", \"2\"), \"true\", \"false\")}"));
			assertEquals("Invalid if(lt()) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(lt(\"2\", \"3\"), \"true\", \"false\")}"));
			assertEquals("Invalid if(lt()) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(lt(\"2000000\", \"3000000\"), \"true\", \"false\")}"));
			assertEquals("Invalid if(lt()) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(lt(\"2.0\", \"3.0\"), \"true\", \"false\")}"));
			assertEquals("Invalid if(lt()) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(lt(\"2000000.0\", \"3000000.0\"), \"true\", \"false\")}"));
			assertEquals("Invalid if(lt()) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(lt(\"12\", \"3\"), \"true\", \"false\")}"));
			assertEquals("Invalid if(lt()) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(lt(\"12000000\", \"3000000\"), \"true\", \"false\")}"));
			assertEquals("Invalid if(lt()) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(lt(\"12.0\", \"3.0\"), \"true\", \"false\")}"));
			assertEquals("Invalid if(lt()) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(lt(\"12000000.0\", \"3000000.0\"), \"true\", \"false\")}"));

			assertEquals("Invalid if(lt()) result", "false", Scripting.replaceVariables(ctx, testOne, "${if(lt(2, 2), \"true\", \"false\")}"));
			assertEquals("Invalid if(lt()) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(lt(2, 3), \"true\", \"false\")}"));
			assertEquals("Invalid if(lt()) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(lt(2000000, 3000000), \"true\", \"false\")}"));
			assertEquals("Invalid if(lt()) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(lt(2.0, 3.0), \"true\", \"false\")}"));
			assertEquals("Invalid if(lt()) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(lt(2000000.0, 3000000.0), \"true\", \"false\")}"));
			assertEquals("Invalid if(lt()) result", "false", Scripting.replaceVariables(ctx, testOne, "${if(lt(12, 3), \"true\", \"false\")}"));
			assertEquals("Invalid if(lt()) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(lt(1200000, 3000000), \"true\", \"false\")}"));
			assertEquals("Invalid if(lt()) result", "false", Scripting.replaceVariables(ctx, testOne, "${if(lt(12000000, 3000000), \"true\", \"false\")}"));
			assertEquals("Invalid if(lt()) result", "false", Scripting.replaceVariables(ctx, testOne, "${if(lt(12.0, 3.0), \"true\", \"false\")}"));
			assertEquals("Invalid if(lt()) result", "false", Scripting.replaceVariables(ctx, testOne, "${if(lt(12000000.0, 3000000.0), \"true\", \"false\")}"));

			// compare numbers written as strings as numbers
			assertEquals("Invalid if(lt()) result", "true", Scripting.replaceVariables(ctx, testOne, "${lt(\"1200\", \"30\")}"));

			// lt with numbers and empty string
			assertEquals("Invalid lt() result with null value", "false", Scripting.replaceVariables(ctx, testOne, "${lt(10, '')}"));
			assertEquals("Invalid lt() result with null value", "true",  Scripting.replaceVariables(ctx, testOne, "${lt('', 11)}"));
			assertEquals("Invalid lt() result with null value", "false", Scripting.replaceVariables(ctx, testOne, "${lt('', '')}"));

			// lt with null
			assertEquals("Invalid lt() result with null value", "false", Scripting.replaceVariables(ctx, testOne, "${lt(\"10\", this.alwaysNull)}"));
			assertEquals("Invalid lt() result with null value", "true",  Scripting.replaceVariables(ctx, testOne, "${lt(this.alwaysNull, \"11\")}"));
			assertEquals("Invalid lt() result with null value", "false", Scripting.replaceVariables(ctx, testOne, "${lt(this.alwaysNull, this.alwaysNull)}"));

			// if + gt
			assertEquals("Invalid if(gt()) result", "false", Scripting.replaceVariables(ctx, testOne, "${if(gt(\"2\", \"2\"), \"true\", \"false\")}"));
			assertEquals("Invalid if(gt()) result", "false", Scripting.replaceVariables(ctx, testOne, "${if(gt(\"2\", \"3\"), \"true\", \"false\")}"));
			assertEquals("Invalid if(gt()) result", "false", Scripting.replaceVariables(ctx, testOne, "${if(gt(\"2000000\", \"3000000\"), \"true\", \"false\")}"));
			assertEquals("Invalid if(gt()) result", "false", Scripting.replaceVariables(ctx, testOne, "${if(gt(\"2.0\", \"3.0\"), \"true\", \"false\")}"));
			assertEquals("Invalid if(gt()) result", "false", Scripting.replaceVariables(ctx, testOne, "${if(gt(\"2000000.0\", \"3000000.0\"), \"true\", \"false\")}"));
			assertEquals("Invalid if(gt()) result", "false", Scripting.replaceVariables(ctx, testOne, "${if(gt(\"12\", \"3\"), \"true\", \"false\")}"));
			assertEquals("Invalid if(gt()) result", "false", Scripting.replaceVariables(ctx, testOne, "${if(gt(\"12000000\", \"3000000\"), \"true\", \"false\")}"));
			assertEquals("Invalid if(gt()) result", "false", Scripting.replaceVariables(ctx, testOne, "${if(gt(\"12.0\", \"3.0\"), \"true\", \"false\")}"));
			assertEquals("Invalid if(gt()) result", "false",  Scripting.replaceVariables(ctx, testOne, "${if(gt(\"12000000.0\", \"3000000.0\"), \"true\", \"false\")}"));

			assertEquals("Invalid if(gt()) result", "false", Scripting.replaceVariables(ctx, testOne, "${if(gt(2, 2), \"true\", \"false\")}"));
			assertEquals("Invalid if(gt()) result", "false", Scripting.replaceVariables(ctx, testOne, "${if(gt(2, 3), \"true\", \"false\")}"));
			assertEquals("Invalid if(gt()) result", "false", Scripting.replaceVariables(ctx, testOne, "${if(gt(2000000, 3000000), \"true\", \"false\")}"));
			assertEquals("Invalid if(gt()) result", "false", Scripting.replaceVariables(ctx, testOne, "${if(gt(2.0, 3.0), \"true\", \"false\")}"));
			assertEquals("Invalid if(gt()) result", "false", Scripting.replaceVariables(ctx, testOne, "${if(gt(2000000.0, 3000000.0), \"true\", \"false\")}"));
			assertEquals("Invalid if(gt()) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(gt(12, 3), \"true\", \"false\")}"));
			assertEquals("Invalid if(gt()) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(gt(12000000, 3000000), \"true\", \"false\")}"));
			assertEquals("Invalid if(gt()) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(gt(12.0, 3.0), \"true\", \"false\")}"));
			assertEquals("Invalid if(gt()) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(gt(12000000.0, 3000000.0), \"true\", \"false\")}"));

			// gt with null
			assertEquals("Invalid gt() result with null value", "true",  Scripting.replaceVariables(ctx, testOne, "${gt(\"10\", this.alwaysNull)}"));
			assertEquals("Invalid gt() result with null value", "false", Scripting.replaceVariables(ctx, testOne, "${gt(this.alwaysNull, \"11\")}"));
			assertEquals("Invalid gt() result with null value", "false", Scripting.replaceVariables(ctx, testOne, "${gt(this.alwaysNull, this.alwaysNull)}"));

			// gt with numbers and empty string
			assertEquals("Invalid gt() result with null value", "true",  Scripting.replaceVariables(ctx, testOne, "${gt(10, '')}"));
			assertEquals("Invalid gt() result with null value", "false", Scripting.replaceVariables(ctx, testOne, "${gt('', 11)}"));
			assertEquals("Invalid gt() result with null value", "false", Scripting.replaceVariables(ctx, testOne, "${gt('', '')}"));

			// if + lte
			assertEquals("Invalid if(lte()) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(lte(\"2\", \"2\"), \"true\", \"false\")}"));
			assertEquals("Invalid if(lte()) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(lte(\"2\", \"3\"), \"true\", \"false\")}"));
			assertEquals("Invalid if(lte()) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(lte(\"2000000\", \"3000000\"), \"true\", \"false\")}"));
			assertEquals("Invalid if(lte()) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(lte(\"2.0\", \"3.0\"), \"true\", \"false\")}"));
			assertEquals("Invalid if(lte()) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(lte(\"2000000.0\", \"3000000.0\"), \"true\", \"false\")}"));
			assertEquals("Invalid if(lte()) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(lte(\"12\", \"3\"), \"true\", \"false\")}"));
			assertEquals("Invalid if(lte()) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(lte(\"12000000\", \"3000000\"), \"true\", \"false\")}"));
			assertEquals("Invalid if(lte()) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(lte(\"12.0\", \"3.0\"), \"true\", \"false\")}"));
			assertEquals("Invalid if(lte()) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(lte(\"12000000.0\", \"3000000.0\"), \"true\", \"false\")}"));

			assertEquals("Invalid if(lte()) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(lte(2, 2), \"true\", \"false\")}"));
			assertEquals("Invalid if(lte()) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(lte(2, 3), \"true\", \"false\")}"));
			assertEquals("Invalid if(lte()) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(lte(2000000, 3000000), \"true\", \"false\")}"));
			assertEquals("Invalid if(lte()) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(lte(2.0, 3.0), \"true\", \"false\")}"));
			assertEquals("Invalid if(lte()) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(lte(2000000.0, 3000000.0), \"true\", \"false\")}"));
			assertEquals("Invalid if(lte()) result", "false", Scripting.replaceVariables(ctx, testOne, "${if(lte(12, 3), \"true\", \"false\")}"));
			assertEquals("Invalid if(lte()) result", "false", Scripting.replaceVariables(ctx, testOne, "${if(lte(12000000, 3000000), \"true\", \"false\")}"));
			assertEquals("Invalid if(lte()) result", "false", Scripting.replaceVariables(ctx, testOne, "${if(lte(12.0, 3.0), \"true\", \"false\")}"));
			assertEquals("Invalid if(lte()) result", "false", Scripting.replaceVariables(ctx, testOne, "${if(lte(12000000.0, 3000000.0), \"true\", \"false\")}"));

			// lte with null
			assertEquals("Invalid lte() result with null value", "false", Scripting.replaceVariables(ctx, testOne, "${lte(\"10\", this.alwaysNull)}"));
			assertEquals("Invalid lte() result with null value", "true",  Scripting.replaceVariables(ctx, testOne, "${lte(this.alwaysNull, \"11\")}"));
			assertEquals("Invalid lte() result with null value", "true",  Scripting.replaceVariables(ctx, testOne, "${lte(this.alwaysNull, this.alwaysNull)}"));

			// if + gte
			assertEquals("Invalid if(gte()) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(gte(\"2\", \"2\"), \"true\", \"false\")}"));
			assertEquals("Invalid if(gte()) result", "false", Scripting.replaceVariables(ctx, testOne, "${if(gte(\"2\", \"3\"), \"true\", \"false\")}"));
			assertEquals("Invalid if(gte()) result", "false", Scripting.replaceVariables(ctx, testOne, "${if(gte(\"2000000\", \"3000000\"), \"true\", \"false\")}"));
			assertEquals("Invalid if(gte()) result", "false", Scripting.replaceVariables(ctx, testOne, "${if(gte(\"2.0\", \"3.0\"), \"true\", \"false\")}"));
			assertEquals("Invalid if(gte()) result", "false", Scripting.replaceVariables(ctx, testOne, "${if(gte(\"2000000.0\", \"3000000.0\"), \"true\", \"false\")}"));
			assertEquals("Invalid if(gte()) result", "false", Scripting.replaceVariables(ctx, testOne, "${if(gte(\"12\", \"3\"), \"true\", \"false\")}"));
			assertEquals("Invalid if(gte()) result", "false", Scripting.replaceVariables(ctx, testOne, "${if(gte(\"12000000\", \"3000000\"), \"true\", \"false\")}"));
			assertEquals("Invalid if(gte()) result", "false", Scripting.replaceVariables(ctx, testOne, "${if(gte(\"12.0\", \"3.0\"), \"true\", \"false\")}"));
			assertEquals("Invalid if(gte()) result", "false", Scripting.replaceVariables(ctx, testOne, "${if(gte(\"12000000.0\", \"3000000.0\"), \"true\", \"false\")}"));

			assertEquals("Invalid if(gte()) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(gte(2, 2), \"true\", \"false\")}"));
			assertEquals("Invalid if(gte()) result", "false", Scripting.replaceVariables(ctx, testOne, "${if(gte(2, 3), \"true\", \"false\")}"));
			assertEquals("Invalid if(gte()) result", "false", Scripting.replaceVariables(ctx, testOne, "${if(gte(2000000, 3000000), \"true\", \"false\")}"));
			assertEquals("Invalid if(gte()) result", "false", Scripting.replaceVariables(ctx, testOne, "${if(gte(2.0, 3.0), \"true\", \"false\")}"));
			assertEquals("Invalid if(gte()) result", "false", Scripting.replaceVariables(ctx, testOne, "${if(gte(2000000.0, 3000000.0), \"true\", \"false\")}"));
			assertEquals("Invalid if(gte()) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(gte(12, 3), \"true\", \"false\")}"));
			assertEquals("Invalid if(gte()) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(gte(12000000, 3000000), \"true\", \"false\")}"));
			assertEquals("Invalid if(gte()) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(gte(12.0, 3.0), \"true\", \"false\")}"));
			assertEquals("Invalid if(gte()) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(gte(12000000.0, 3000000.0), \"true\", \"false\")}"));

			// gte with null
			assertEquals("Invalid gte() result with null value", "true",  Scripting.replaceVariables(ctx, testOne, "${gte(\"10\", this.alwaysNull)}"));
			assertEquals("Invalid gte() result with null value", "false",  Scripting.replaceVariables(ctx, testOne, "${gte(this.alwaysNull, \"11\")}"));
			assertEquals("Invalid gte() result with null value", "true",  Scripting.replaceVariables(ctx, testOne, "${gte(this.alwaysNull, this.alwaysNull)}"));

			// if + equal + subt
			assertEquals("Invalid if(equal(subt())) result", "true", Scripting.replaceVariables(ctx, testOne, "${if(equal(\"2\", subt(\"3\", \"1\")), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal(subt())) result", "false", Scripting.replaceVariables(ctx, testOne, "${if(equal(\"2\", subt(\"4\", \"1\")), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal(subt())) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(equal(2, subt(\"3\", \"1\")), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal(subt())) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(equal(2, subt(3, 1)), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal(subt())) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(equal(2, subt(\"3\", 1)), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal(subt())) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(equal(2, subt(3, \"1\")), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal(subt())) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(equal(2, subt(3, 1.0)), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal(subt())) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(equal(2.0, subt(\"3\", \"1\")), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal(subt())) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(equal(2.0, subt(3, 1)), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal(subt())) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(equal(2.0, subt(\"3\", 1)), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal(subt())) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(equal(2.0, subt(3, \"1\")), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal(subt())) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(equal(2.0, subt(3, 1.0)), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal(subt())) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(equal(20, subt(\"30\", \"10\")), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal(subt())) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(equal(20, subt(\"30\", \"010\")), \"true\", \"false\")}"));

			// subt with null
			assertEquals("Invalid subt() result with null value", "",  Scripting.replaceVariables(ctx, testOne, "${subt(\"10\", this.alwaysNull)}"));
			assertEquals("Invalid subt() result with null value", "",  Scripting.replaceVariables(ctx, testOne, "${subt(this.alwaysNull, \"11\")}"));
			assertEquals("Invalid subt() result with null value", "",  Scripting.replaceVariables(ctx, testOne, "${subt(this.alwaysNull, this.alwaysNull)}"));

			// if + equal + mult
			assertEquals("Invalid if(equal(mult())) result", "true", Scripting.replaceVariables(ctx, testOne, "${if(equal(\"6\", mult(\"3\", \"2\")), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal(mult())) result", "false", Scripting.replaceVariables(ctx, testOne, "${if(equal(\"6\", mult(\"4\", \"2\")), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal(mult())) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(equal(6, mult(\"3\", \"2\")), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal(mult())) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(equal(6, mult(3, 2)), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal(mult())) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(equal(6, mult(\"3\", 2)), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal(mult())) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(equal(6, mult(3, \"2\")), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal(mult())) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(equal(6, mult(3, 2.0)), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal(mult())) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(equal(6.0, mult(\"3\", \"2\")), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal(mult())) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(equal(6.0, mult(3, 2)), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal(mult())) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(equal(6.0, mult(\"3\", 2)), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal(mult())) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(equal(6.0, mult(3, \"2\")), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal(mult())) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(equal(6.0, mult(3, 2.0)), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal(mult())) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(equal(600, mult(\"30\", \"20\")), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal(mult())) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(equal(600, mult(\"30\", \"020\")), \"true\", \"false\")}"));

			// mult with null
			assertEquals("Invalid mult() result with null value", "",  Scripting.replaceVariables(ctx, testOne, "${mult(\"10\", this.alwaysNull)}"));
			assertEquals("Invalid mult() result with null value", "",  Scripting.replaceVariables(ctx, testOne, "${mult(this.alwaysNull, \"11\")}"));
			assertEquals("Invalid mult() result with null value", "",  Scripting.replaceVariables(ctx, testOne, "${mult(this.alwaysNull, this.alwaysNull)}"));

			// if + equal + quot
			assertEquals("Invalid if(equal(quot())) result", "true", Scripting.replaceVariables(ctx, testOne, "${if(equal(\"1.5\", quot(\"3\", \"2\")), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal(quot())) result", "false", Scripting.replaceVariables(ctx, testOne, "${if(equal(\"1.5\", quot(\"5\", \"2\")), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal(quot())) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(equal(1.5, quot(\"3\", \"2\")), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal(quot())) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(equal(1.5, quot(3, 2)), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal(quot())) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(equal(1.5, quot(\"3\", 2)), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal(quot())) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(equal(1.5, quot(3, \"2\")), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal(quot())) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(equal(1.5, quot(3, 2.0)), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal(quot())) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(equal(15, quot(\"30\", \"2\")), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal(quot())) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(equal(15, quot(\"30\", \"02\")), \"true\", \"false\")}"));

			// quot with null
			assertEquals("Invalid quot() result with null value", "10.0",  Scripting.replaceVariables(ctx, testOne, "${quot(10, this.alwaysNull)}"));
			assertEquals("Invalid quot() result with null value", "10.0",  Scripting.replaceVariables(ctx, testOne, "${quot(\"10\", this.alwaysNull)}"));
			assertEquals("Invalid quot() result with null value", "",  Scripting.replaceVariables(ctx, testOne, "${quot(this.alwaysNull, \"11\")}"));
			assertEquals("Invalid quot() result with null value", "",  Scripting.replaceVariables(ctx, testOne, "${quot(this.alwaysNull, this.alwaysNull)}"));

			// if + equal + round
			assertEquals("Invalid if(equal(round())) result", "true", Scripting.replaceVariables(ctx, testOne, "${if(equal(\"1.9\", round(\"1.9\", \"2\")), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal(round())) result", "false", Scripting.replaceVariables(ctx, testOne, "${if(equal(\"2\", round(\"2.5\", \"2\")), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal(round())) result", "true", Scripting.replaceVariables(ctx, testOne, "${if(equal(\"2\", round(\"1.999999\", \"2\")), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal(round())) result", "false", Scripting.replaceVariables(ctx, testOne, "${if(equal(\"2\", round(\"2.499999\", \"2\")), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal(round())) result", "false", Scripting.replaceVariables(ctx, testOne, "${if(equal(2, round(1.9, 2)), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal(round())) result", "false", Scripting.replaceVariables(ctx, testOne, "${if(equal(2, round(2.5, 2)), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal(round())) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(equal(2, round(1.999999, 2)), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal(round())) result", "false", Scripting.replaceVariables(ctx, testOne, "${if(equal(2, round(2.499999, 2)), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal(round())) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(equal(2, round(2, 2)), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal(round())) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(equal(2.4, round(2.4, 2)), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal(round())) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(equal(2.23, round(2.225234, 2)), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal(round())) result", "false", Scripting.replaceVariables(ctx, testOne, "${if(equal(2, round(1.9, 8)), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal(round())) result", "false", Scripting.replaceVariables(ctx, testOne, "${if(equal(2, round(2.5, 8)), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal(round())) result", "false", Scripting.replaceVariables(ctx, testOne, "${if(equal(2, round(1.999999, 8)), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal(round())) result", "false", Scripting.replaceVariables(ctx, testOne, "${if(equal(2, round(2.499999, 8)), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal(round())) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(equal(1.999999, round(1.999999, 8)), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal(round())) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(equal(2.499999, round(2.499999, 8)), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal(round())) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(equal(2, round(1.999999999, 8)), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal(round())) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(equal(2, round(2, 8)), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal(round())) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(equal(2.4, round(2.4, 8)), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal(round())) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(equal(2.225234, round(2.225234, 8)), \"true\", \"false\")}"));

			// disabled because scientific notation is not supported :(
			//assertEquals("Invalid if(equal(round())) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(equal(0.00245, round(2.45e-3, 8)), \"true\", \"false\")}"));
			//assertEquals("Invalid if(equal(round())) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(equal(245, round(2.45e2, 8)), \"true\", \"false\")}"));

			// round with null
			assertEquals("Invalid round() result", "10",                                              Scripting.replaceVariables(ctx, testOne, "${round(\"10\")}"));
			assertEquals("Invalid round() result with null value", "",                                Scripting.replaceVariables(ctx, testOne, "${round(this.alwaysNull)}"));
			assertEquals("Invalid round() result with null value", RoundFunction.ERROR_MESSAGE_ROUND, Scripting.replaceVariables(ctx, testOne, "${round(this.alwaysNull, this.alwaysNull)}"));

			// if + equal + max
			assertEquals("Invalid if(equal(max())) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(equal(\"2\", max(\"1.9\", \"2\")), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal(max())) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(equal(2, max(1.9, 2)), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal(max())) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(equal(2.0, max(1.9, 2)), \"true\", \"false\")}"));

			// max with null
			assertEquals("Invalid max() result with null value", "",  Scripting.replaceVariables(ctx, testOne, "${max(\"10\", this.alwaysNull)}"));
			assertEquals("Invalid max() result with null value", "",  Scripting.replaceVariables(ctx, testOne, "${max(this.alwaysNull, \"11\")}"));
			assertEquals("Invalid max() result with null value", "",  Scripting.replaceVariables(ctx, testOne, "${max(this.alwaysNull, this.alwaysNull)}"));

			// if + equal + min
			assertEquals("Invalid if(equal(min())) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(equal(\"1.9\", min(\"1.9\", \"2\")), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal(min())) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(equal(1.9, min(1.9, 2)), \"true\", \"false\")}"));
			assertEquals("Invalid if(equal(min())) result", "true",  Scripting.replaceVariables(ctx, testOne, "${if(equal(1, min(1, 2)), \"true\", \"false\")}"));

			// min with null
			assertEquals("Invalid min() result with null value", "",  Scripting.replaceVariables(ctx, testOne, "${min(\"10\", this.alwaysNull)}"));
			assertEquals("Invalid min() result with null value", "",  Scripting.replaceVariables(ctx, testOne, "${min(this.alwaysNull, \"11\")}"));
			assertEquals("Invalid min() result with null value", "",  Scripting.replaceVariables(ctx, testOne, "${min(this.alwaysNull, this.alwaysNull)}"));

			// date_format
			assertEquals("Invalid date_format() result", nowString1, Scripting.replaceVariables(ctx, testOne, "${date_format(this.aDate, \"" + format1.toPattern() + "\")}"));
			assertEquals("Invalid date_format() result", nowString2, Scripting.replaceVariables(ctx, testOne, "${date_format(this.aDate, \"" + format2.toPattern() + "\")}"));
			assertEquals("Invalid date_format() result", nowString3, Scripting.replaceVariables(ctx, testOne, "${date_format(this.aDate, \"" + format3.toPattern() + "\")}"));

			// date_format with locale
			Locale locale = ctx.getLocale();
			assertEquals("Invalid set_locale() result", "", Scripting.replaceVariables(ctx, testOne, "${set_locale('us')}"));
			assertEquals("Invalid date_format() result", "01. Oct 2017", Scripting.replaceVariables(ctx, testOne, "${date_format(parse_date('01.10.2017', 'dd.MM.yyyy'), 'dd. MMM yyyy')}"));
			assertEquals("Invalid set_locale() result", "", Scripting.replaceVariables(ctx, testOne, "${set_locale('de')}"));
			assertEquals("Invalid date_format() result", "01. Okt. 2017", Scripting.replaceVariables(ctx, testOne, "${date_format(parse_date('01.10.2017', 'dd.MM.yyyy'), 'dd. MMM yyyy')}"));
			ctx.setLocale(locale);

			// date_format with null
			assertEquals("Invalid date_format() result with null value", "",                                           Scripting.replaceVariables(ctx, testOne, "${date_format(this.alwaysNull, \"yyyy\")}"));
			assertEquals("Invalid date_format() result with null value", DateFormatFunction.ERROR_MESSAGE_DATE_FORMAT, Scripting.replaceVariables(ctx, testOne, "${date_format(\"10\", this.alwaysNull)}"));
			assertEquals("Invalid date_format() result with null value", DateFormatFunction.ERROR_MESSAGE_DATE_FORMAT, Scripting.replaceVariables(ctx, testOne, "${date_format(this.alwaysNull, this.alwaysNull)}"));

			// date_format error messages
			assertEquals("Invalid date_format() result for wrong number of parameters", DateFormatFunction.ERROR_MESSAGE_DATE_FORMAT, Scripting.replaceVariables(ctx, testOne, "${date_format()}"));
			assertEquals("Invalid date_format() result for wrong number of parameters", DateFormatFunction.ERROR_MESSAGE_DATE_FORMAT, Scripting.replaceVariables(ctx, testOne, "${date_format(this.aDouble)}"));
			assertEquals("Invalid date_format() result for wrong number of parameters", DateFormatFunction.ERROR_MESSAGE_DATE_FORMAT, Scripting.replaceVariables(ctx, testOne, "${date_format(this.aDouble, this.aDouble, this.aDouble)}"));

			// parse_date
			//assertEquals("Invalid parse_date() result", ParseDateFunction.ERROR_MESSAGE_PARSE_DATE, Scripting.replaceVariables(ctx, testOne, "${parse_date('2015-12-12')}"));
			//assertEquals("Invalid parse_date() result", "2015-12-12T00:00:00+0000", Scripting.replaceVariables(ctx, testOne, "${parse_date('2015-12-12', 'yyyy-MM-dd')}"));
			//assertEquals("Invalid parse_date() result", "2015-12-12T00:00:00+0000", Scripting.replaceVariables(ctx, testOne, "${parse_date('2015-12-12', 'yyyy-MM-dd')}"));
			//assertEquals("Invalid parse_date() result for wrong number of parameters", ParseDateFunction.ERROR_MESSAGE_PARSE_DATE, Scripting.replaceVariables(ctx, testOne, "${date_format(parse_date('2017-09-20T18:23:22+0200'), 'dd. MMM yyyy')}"));

			// to_date
			//assertEquals("Invalid to_date() result", ToDateFunction.ERROR_MESSAGE_TO_DATE, Scripting.replaceVariables(ctx, testOne, "${to_date()}"));
			//assertEquals("Invalid to_date() result", "2016-09-06T22:44:45+0000", Scripting.replaceVariables(ctx, testOne, "${to_date(1473201885000)}"));

			// number_format error messages
			assertEquals("Invalid number_format() result for wrong number of parameters", NumberFormatFunction.ERROR_MESSAGE_NUMBER_FORMAT, Scripting.replaceVariables(ctx, testOne, "${number_format()}"));
			assertEquals("Invalid number_format() result for wrong number of parameters", NumberFormatFunction.ERROR_MESSAGE_NUMBER_FORMAT, Scripting.replaceVariables(ctx, testOne, "${number_format(this.aDouble)}"));
			assertEquals("Invalid number_format() result for wrong number of parameters", NumberFormatFunction.ERROR_MESSAGE_NUMBER_FORMAT, Scripting.replaceVariables(ctx, testOne, "${number_format(this.aDouble, this.aDouble)}"));
			assertEquals("Invalid number_format() result for wrong number of parameters", NumberFormatFunction.ERROR_MESSAGE_NUMBER_FORMAT, Scripting.replaceVariables(ctx, testOne, "${number_format(this.aDouble, this.aDouble, \"\", \"\")}"));
			assertEquals("Invalid number_format() result for wrong number of parameters", NumberFormatFunction.ERROR_MESSAGE_NUMBER_FORMAT, Scripting.replaceVariables(ctx, testOne, "${number_format(this.aDouble, this.aDouble, \"\", \"\", \"\")}"));

			assertEquals("Invalid number_format() result", numberString1, Scripting.replaceVariables(ctx, testOne, "${number_format(this.aDouble, \"en\", \"" + numberFormat1.toPattern() + "\")}"));
			assertEquals("Invalid number_format() result", numberString2, Scripting.replaceVariables(ctx, testOne, "${number_format(this.aDouble, \"de\", \"" + numberFormat2.toPattern() + "\")}"));
			assertEquals("Invalid number_format() result", numberString3, Scripting.replaceVariables(ctx, testOne, "${number_format(this.aDouble, \"zh\", \"" + numberFormat3.toPattern() + "\")}"));
			assertEquals("Invalid number_format() result",   "123456.79", Scripting.replaceVariables(ctx, testOne, "${number_format(123456.789012, \"en\", \"0.00\")}"));
			assertEquals("Invalid number_format() result", "123456.7890", Scripting.replaceVariables(ctx, testOne, "${number_format(123456.789012, \"en\", \"0.0000\")}"));
			assertEquals("Invalid number_format() result",   "123456,79", Scripting.replaceVariables(ctx, testOne, "${number_format(123456.789012, \"de\", \"0.00\")}"));
			assertEquals("Invalid number_format() result", "123456,7890", Scripting.replaceVariables(ctx, testOne, "${number_format(123456.789012, \"de\", \"0.0000\")}"));
			assertEquals("Invalid number_format() result",   "123456.79", Scripting.replaceVariables(ctx, testOne, "${number_format(123456.789012, \"zh\", \"0.00\")}"));
			assertEquals("Invalid number_format() result", "123456.7890", Scripting.replaceVariables(ctx, testOne, "${number_format(123456.789012, \"zh\", \"0.0000\")}"));

			// number_format with null
			assertEquals("Invalid number_format() result with null value", "",    Scripting.replaceVariables(ctx, testOne, "${number_format(this.alwaysNull, \"en\", \"#\")}"));
			assertEquals("Invalid number_format() result with null parameter(s)", NumberFormatFunction.ERROR_MESSAGE_NUMBER_FORMAT,  Scripting.replaceVariables(ctx, testOne, "${number_format(\"10\", this.alwaysNull, this.alwaysNull)}"));
			assertEquals("Invalid number_format() result with null parameter(s)", NumberFormatFunction.ERROR_MESSAGE_NUMBER_FORMAT,  Scripting.replaceVariables(ctx, testOne, "${number_format(\"10\", \"de\", this.alwaysNull)}"));
			assertEquals("Invalid number_format() result with null parameter(s)", NumberFormatFunction.ERROR_MESSAGE_NUMBER_FORMAT,  Scripting.replaceVariables(ctx, testOne, "${number_format(\"10\", this.alwaysNull, \"#\")}"));
			assertEquals("Invalid number_format() result with null parameter(s)", NumberFormatFunction.ERROR_MESSAGE_NUMBER_FORMAT,  Scripting.replaceVariables(ctx, testOne, "${number_format(this.alwaysNull, this.alwaysNull, this.alwaysNull)}"));

			// parse_number
			final Locale oldLocale = ctx.getLocale();
			assertEquals("Invalid set_locale() result", "", Scripting.replaceVariables(ctx, testOne, "${set_locale('en')}"));
			assertEquals("Invalid parse_number() result", "123.456", Scripting.replaceVariables(ctx, testOne, "${parse_number('123.456')}"));
			ctx.setLocale(oldLocale);

			assertEquals("Invalid parse_number() result", "123.456", Scripting.replaceVariables(ctx, testOne, "${parse_number('123.456', 'en')}"));
			assertEquals("Invalid parse_number() result", "123.456", Scripting.replaceVariables(ctx, testOne, "${parse_number('123,456', 'de')}"));
			assertEquals("Invalid parse_number() result", "123456", Scripting.replaceVariables(ctx, testOne, "${parse_number('123456', 'de')}"));
			assertEquals("Invalid parse_number() result", "123456", Scripting.replaceVariables(ctx, testOne, "${parse_number('123.456', 'de')}"));
			assertEquals("Invalid parse_number() result", "123456", Scripting.replaceVariables(ctx, testOne, "${parse_number('123.456 €', 'de')}"));
			assertEquals("Invalid parse_number() result", "123456789", Scripting.replaceVariables(ctx, testOne, "${parse_number('£ 123,456,789.00 ', 'en')}"));
			assertEquals("Invalid parse_number() result", "123456789", Scripting.replaceVariables(ctx, testOne, "${parse_number('123,foo456,bar789.00 ', 'en')}"));
			assertEquals("Invalid parse_number() result", "123.456", Scripting.replaceVariables(ctx, testOne, "${parse_number('£ 123,456,789.00 ', 'de')}"));
			assertEquals("Invalid parse_number() result", "123.456", Scripting.replaceVariables(ctx, testOne, "${parse_number('123,foo456,bar789.00 ', 'de')}"));

			// not
			assertEquals("Invalid not() result", "true",  Scripting.replaceVariables(ctx, testOne, "${not(false)}"));
			assertEquals("Invalid not() result", "false", Scripting.replaceVariables(ctx, testOne, "${not(true)}"));
			assertEquals("Invalid not() result", "true",  Scripting.replaceVariables(ctx, testOne, "${not(\"false\")}"));
			assertEquals("Invalid not() result", "false", Scripting.replaceVariables(ctx, testOne, "${not(\"true\")}"));

			// not with null
			assertEquals("Invalid not() result with null value", "true", Scripting.replaceVariables(ctx, testOne, "${not(this.alwaysNull)}"));

			// and
			assertEquals("Invalid and() result", "true",  Scripting.replaceVariables(ctx, testOne, "${and(true, true)}"));
			assertEquals("Invalid and() result", "false", Scripting.replaceVariables(ctx, testOne, "${and(true, false)}"));
			assertEquals("Invalid and() result", "false", Scripting.replaceVariables(ctx, testOne, "${and(false, true)}"));
			assertEquals("Invalid and() result", "false", Scripting.replaceVariables(ctx, testOne, "${and(false, false)}"));

			// and with null
			assertEquals("Invalid and() result with null value", "false", Scripting.replaceVariables(ctx, testOne, "${and(this.alwaysNull, this.alwaysNull)}"));

			// or
			assertEquals("Invalid or() result", "true",  Scripting.replaceVariables(ctx, testOne, "${or(true, true)}"));
			assertEquals("Invalid or() result", "true", Scripting.replaceVariables(ctx, testOne, "${or(true, false)}"));
			assertEquals("Invalid or() result", "true", Scripting.replaceVariables(ctx, testOne, "${or(false, true)}"));
			assertEquals("Invalid or() result", "false", Scripting.replaceVariables(ctx, testOne, "${and(false, false)}"));

			// or with null
			assertEquals("Invalid or() result with null value", "false", Scripting.replaceVariables(ctx, testOne, "${or(this.alwaysNull, this.alwaysNull)}"));

			// get
			assertEquals("Invalid get() result", "1",  Scripting.replaceVariables(ctx, testOne, "${get(this, \"anInt\")}"));
			assertEquals("Invalid get() result", "String",  Scripting.replaceVariables(ctx, testOne, "${get(this, \"aString\")}"));
			assertEquals("Invalid get() result", "2.234",  Scripting.replaceVariables(ctx, testOne, "${get(this, \"aDouble\")}"));
			assertEquals("Invalid get() result", testTwo.toString(),  Scripting.replaceVariables(ctx, testOne, "${get(this, \"testTwo\")}"));
			assertEquals("Invalid get() result", testTwo.getUuid(),  Scripting.replaceVariables(ctx, testOne, "${get(get(this, \"testTwo\"), \"id\")}"));
			assertEquals("Invalid get() result", testSixs.get(0).getUuid(),  Scripting.replaceVariables(ctx, testOne, "${get(first(get(this, \"manyToManyTestSixs\")), \"id\")}"));

			// size
			assertEquals("Invalid size() result", "20", Scripting.replaceVariables(ctx, testOne, "${size(this.manyToManyTestSixs)}"));
			assertEquals("Invalid size() result", "0", Scripting.replaceVariables(ctx, testOne, "${size(null)}"));
			assertEquals("Invalid size() result", "0", Scripting.replaceVariables(ctx, testOne, "${size(xyz)}"));

			// is_collection
			assertEquals("Invalid is_collection() result", "true", Scripting.replaceVariables(ctx, testOne, "${is_collection(this.manyToManyTestSixs)}"));
			assertEquals("Invalid is_collection() result", "false", Scripting.replaceVariables(ctx, testOne, "${is_collection(this.name)}"));
			assertEquals("Invalid is_collection() result", "false", Scripting.replaceVariables(ctx, testOne, "${is_collection(null)}"));
			assertEquals("Invalid is_collection() result", "false", Scripting.replaceVariables(ctx, testOne, "${is_collection(xyz)}"));

			// is_entity
			assertEquals("Invalid is_entity() result", "true", Scripting.replaceVariables(ctx, testOne, "${is_entity(this.testFour)}"));
			assertEquals("Invalid is_entity() result", "false", Scripting.replaceVariables(ctx, testOne, "${is_entity(this.manyToManyTestSixs)}"));
			assertEquals("Invalid is_entity() result", "false", Scripting.replaceVariables(ctx, testOne, "${is_entity(this.name)}"));
			assertEquals("Invalid is_entity() result", "false", Scripting.replaceVariables(ctx, testOne, "${is_entity(null)}"));
			assertEquals("Invalid is_entity() result", "false", Scripting.replaceVariables(ctx, testOne, "${is_entity(xyz)}"));

			// first / last / nth
			assertEquals("Invalid first() result", testSixs.get( 0).toString(), Scripting.replaceVariables(ctx, testOne, "${first(this.manyToManyTestSixs)}"));
			assertEquals("Invalid last() result",  testSixs.get(19).toString(), Scripting.replaceVariables(ctx, testOne, "${last(this.manyToManyTestSixs)}"));
			assertEquals("Invalid nth() result",   testSixs.get( 2).toString(), Scripting.replaceVariables(ctx, testOne, "${nth(this.manyToManyTestSixs,  2)}"));
			assertEquals("Invalid nth() result",   testSixs.get( 7).toString(), Scripting.replaceVariables(ctx, testOne, "${nth(this.manyToManyTestSixs,  7)}"));
			assertEquals("Invalid nth() result",   testSixs.get( 9).toString(), Scripting.replaceVariables(ctx, testOne, "${nth(this.manyToManyTestSixs,  9)}"));
			assertEquals("Invalid nth() result",   testSixs.get(12).toString(), Scripting.replaceVariables(ctx, testOne, "${nth(this.manyToManyTestSixs, 12)}"));
			assertEquals("Invalid nth() result",   "", Scripting.replaceVariables(ctx, testOne, "${nth(this.manyToManyTestSixs, 21)}"));

			// find with range
			assertEquals("Invalid find range result",  4, ((List)Scripting.evaluate(ctx, testOne, "${find('TestSix', 'index', range(   2,    5))}", "range test")).size());
			assertEquals("Invalid find range result", 20, ((List)Scripting.evaluate(ctx, testOne, "${find('TestSix', 'index', range(   0,   20))}", "range test")).size());
			assertEquals("Invalid find range result", 19, ((List)Scripting.evaluate(ctx, testOne, "${find('TestSix', 'index', range(   1,   20))}", "range test")).size());
			assertEquals("Invalid find range result",  6, ((List)Scripting.evaluate(ctx, testOne, "${find('TestSix', 'index', range(null,    5))}", "range test")).size());
			assertEquals("Invalid find range result", 12, ((List)Scripting.evaluate(ctx, testOne, "${find('TestSix', 'index', range(   8, null))}", "range test")).size());
			assertEquals("Invalid find range result", 20, ((List)Scripting.evaluate(ctx, testOne, "${find('TestSix', 'index', range(null, null))}", "range test")).size());

			// find with range
			assertEquals("Invalid find range result",  3, ((List)Scripting.evaluate(ctx, testOne, "${find('TestSix', 'index', range(   2,    5, true, false))}", "range test")).size());
			assertEquals("Invalid find range result", 20, ((List)Scripting.evaluate(ctx, testOne, "${find('TestSix', 'index', range(   0,   20, true, false))}", "range test")).size());
			assertEquals("Invalid find range result", 19, ((List)Scripting.evaluate(ctx, testOne, "${find('TestSix', 'index', range(   1,   20, true, false))}", "range test")).size());
			assertEquals("Invalid find range result",  5, ((List)Scripting.evaluate(ctx, testOne, "${find('TestSix', 'index', range(null,    5, true, false))}", "range test")).size());
			assertEquals("Invalid find range result", 12, ((List)Scripting.evaluate(ctx, testOne, "${find('TestSix', 'index', range(   8, null, true, false))}", "range test")).size());
			assertEquals("Invalid find range result", 20, ((List)Scripting.evaluate(ctx, testOne, "${find('TestSix', 'index', range(null, null, true, false))}", "range test")).size());

			// find with range
			assertEquals("Invalid find range result",  3, ((List)Scripting.evaluate(ctx, testOne, "${find('TestSix', 'index', range(   2,    5, false, true))}", "range test")).size());
			assertEquals("Invalid find range result", 19, ((List)Scripting.evaluate(ctx, testOne, "${find('TestSix', 'index', range(   0,   20, false, true))}", "range test")).size());
			assertEquals("Invalid find range result", 18, ((List)Scripting.evaluate(ctx, testOne, "${find('TestSix', 'index', range(   1,   20, false, true))}", "range test")).size());
			assertEquals("Invalid find range result",  6, ((List)Scripting.evaluate(ctx, testOne, "${find('TestSix', 'index', range(null,    5, false, true))}", "range test")).size());
			assertEquals("Invalid find range result", 11, ((List)Scripting.evaluate(ctx, testOne, "${find('TestSix', 'index', range(   8, null, false, true))}", "range test")).size());
			assertEquals("Invalid find range result", 20, ((List)Scripting.evaluate(ctx, testOne, "${find('TestSix', 'index', range(null, null, false, true))}", "range test")).size());

			// find with range
			assertEquals("Invalid find range result",  2, ((List)Scripting.evaluate(ctx, testOne, "${find('TestSix', 'index', range(   2,    5, false, false))}", "range test")).size());
			assertEquals("Invalid find range result", 19, ((List)Scripting.evaluate(ctx, testOne, "${find('TestSix', 'index', range(   0,   20, false, false))}", "range test")).size());
			assertEquals("Invalid find range result", 18, ((List)Scripting.evaluate(ctx, testOne, "${find('TestSix', 'index', range(   1,   20, false, false))}", "range test")).size());
			assertEquals("Invalid find range result",  5, ((List)Scripting.evaluate(ctx, testOne, "${find('TestSix', 'index', range(null,    5, false, false))}", "range test")).size());
			assertEquals("Invalid find range result", 11, ((List)Scripting.evaluate(ctx, testOne, "${find('TestSix', 'index', range(   8, null, false, false))}", "range test")).size());
			assertEquals("Invalid find range result", 20, ((List)Scripting.evaluate(ctx, testOne, "${find('TestSix', 'index', range(null, null, false, false))}", "range test")).size());

			// find with date range
			assertEquals("Invalid find date range result", 11, ((List)Scripting.evaluate(ctx, testOne, "${find('TestSix', 'date', range(parse_date('01.01.2018', 'dd.MM.yyyy'), parse_date('01.02.2018', 'dd.MM.yyyy'), true, true))}", "range test")).size());
			assertEquals("Invalid find date range result", 15, ((List)Scripting.evaluate(ctx, testOne, "${find('TestSix', 'date', range(parse_date('16.01.2018', 'dd.MM.yyyy'), parse_date('01.04.2018', 'dd.MM.yyyy'), true, true))}", "range test")).size());
			assertEquals("Invalid find date range result",  6, ((List)Scripting.evaluate(ctx, testOne, "${find('TestSix', 'date', range(parse_date('16.01.2018', 'dd.MM.yyyy'), parse_date('03.02.2018', 'dd.MM.yyyy'), true, true))}", "range test")).size());
			assertEquals("Invalid find date range result", 11, ((List)Scripting.evaluate(ctx, testOne, "${find('TestSix', 'date', range(                                  null, parse_date('03.02.2018', 'dd.MM.yyyy'), true, true))}", "range test")).size());
			assertEquals("Invalid find date range result",  8, ((List)Scripting.evaluate(ctx, testOne, "${find('TestSix', 'date', range(parse_date('06.02.2018', 'dd.MM.yyyy'),                                   null, true, true))}", "range test")).size());
			assertEquals("Invalid find date range result", 20, ((List)Scripting.evaluate(ctx, testOne, "${find('TestSix', 'date', range(                                  null,                                   null, true, true))}", "range test")).size());

			// find with date range
			assertEquals("Invalid find date range result", 11, ((List)Scripting.evaluate(ctx, testOne, "${find('TestSix', 'date', range(parse_date('01.01.2018', 'dd.MM.yyyy'), parse_date('01.02.2018', 'dd.MM.yyyy'), true, false))}", "range test")).size());
			assertEquals("Invalid find date range result", 15, ((List)Scripting.evaluate(ctx, testOne, "${find('TestSix', 'date', range(parse_date('16.01.2018', 'dd.MM.yyyy'), parse_date('01.04.2018', 'dd.MM.yyyy'), true, false))}", "range test")).size());
			assertEquals("Invalid find date range result",  6, ((List)Scripting.evaluate(ctx, testOne, "${find('TestSix', 'date', range(parse_date('16.01.2018', 'dd.MM.yyyy'), parse_date('03.02.2018', 'dd.MM.yyyy'), true, false))}", "range test")).size());
			assertEquals("Invalid find date range result", 11, ((List)Scripting.evaluate(ctx, testOne, "${find('TestSix', 'date', range(                                  null, parse_date('03.02.2018', 'dd.MM.yyyy'), true, false))}", "range test")).size());
			assertEquals("Invalid find date range result",  8, ((List)Scripting.evaluate(ctx, testOne, "${find('TestSix', 'date', range(parse_date('06.02.2018', 'dd.MM.yyyy'),                                   null, true, false))}", "range test")).size());
			assertEquals("Invalid find date range result", 20, ((List)Scripting.evaluate(ctx, testOne, "${find('TestSix', 'date', range(                                  null,                                   null, true, false))}", "range test")).size());

			// find with date range
			assertEquals("Invalid find date range result", 11, ((List)Scripting.evaluate(ctx, testOne, "${find('TestSix', 'date', range(parse_date('01.01.2018', 'dd.MM.yyyy'), parse_date('01.02.2018', 'dd.MM.yyyy'), false, true))}", "range test")).size());
			assertEquals("Invalid find date range result", 15, ((List)Scripting.evaluate(ctx, testOne, "${find('TestSix', 'date', range(parse_date('16.01.2018', 'dd.MM.yyyy'), parse_date('01.04.2018', 'dd.MM.yyyy'), false, true))}", "range test")).size());
			assertEquals("Invalid find date range result",  6, ((List)Scripting.evaluate(ctx, testOne, "${find('TestSix', 'date', range(parse_date('16.01.2018', 'dd.MM.yyyy'), parse_date('03.02.2018', 'dd.MM.yyyy'), false, true))}", "range test")).size());
			assertEquals("Invalid find date range result", 11, ((List)Scripting.evaluate(ctx, testOne, "${find('TestSix', 'date', range(                                  null, parse_date('03.02.2018', 'dd.MM.yyyy'), false, true))}", "range test")).size());
			assertEquals("Invalid find date range result",  8, ((List)Scripting.evaluate(ctx, testOne, "${find('TestSix', 'date', range(parse_date('06.02.2018', 'dd.MM.yyyy'),                                   null, false, true))}", "range test")).size());
			assertEquals("Invalid find date range result", 20, ((List)Scripting.evaluate(ctx, testOne, "${find('TestSix', 'date', range(                                  null,                                   null, false, true))}", "range test")).size());

			// find with date range
			assertEquals("Invalid find date range result", 11, ((List)Scripting.evaluate(ctx, testOne, "${find('TestSix', 'date', range(parse_date('01.01.2018', 'dd.MM.yyyy'), parse_date('01.02.2018', 'dd.MM.yyyy'), false, false))}", "range test")).size());
			assertEquals("Invalid find date range result", 15, ((List)Scripting.evaluate(ctx, testOne, "${find('TestSix', 'date', range(parse_date('16.01.2018', 'dd.MM.yyyy'), parse_date('01.04.2018', 'dd.MM.yyyy'), false, false))}", "range test")).size());
			assertEquals("Invalid find date range result",  6, ((List)Scripting.evaluate(ctx, testOne, "${find('TestSix', 'date', range(parse_date('16.01.2018', 'dd.MM.yyyy'), parse_date('03.02.2018', 'dd.MM.yyyy'), false, false))}", "range test")).size());
			assertEquals("Invalid find date range result", 11, ((List)Scripting.evaluate(ctx, testOne, "${find('TestSix', 'date', range(                                  null, parse_date('03.02.2018', 'dd.MM.yyyy'), false, false))}", "range test")).size());
			assertEquals("Invalid find date range result",  8, ((List)Scripting.evaluate(ctx, testOne, "${find('TestSix', 'date', range(parse_date('06.02.2018', 'dd.MM.yyyy'),                                   null, false, false))}", "range test")).size());
			assertEquals("Invalid find date range result", 20, ((List)Scripting.evaluate(ctx, testOne, "${find('TestSix', 'date', range(                                  null,                                   null, false, false))}", "range test")).size());

			// slice with find
			final List sliceResult2 = (List)Scripting.evaluate(ctx, testOne, "${slice(sort(find('TestSix'), 'name'),  0,  5)}", "slice test");
			final List sliceResult3 = (List)Scripting.evaluate(ctx, testOne, "${slice(sort(find('TestSix'), 'name'),  5, 10)}", "slice test");
			final List sliceResult4 = (List)Scripting.evaluate(ctx, testOne, "${slice(sort(find('TestSix'), 'name'), 10, 15)}", "slice test");
			final List sliceResult5 = (List)Scripting.evaluate(ctx, testOne, "${slice(sort(find('TestSix'), 'name'), 15, 20)}", "slice test");

			assertEquals("Invalid slice() result, must return a list of 5 objects", 5, sliceResult2.size());
			assertEquals("Invalid slice() result, must return a list of 5 objects", 5, sliceResult3.size());
			assertEquals("Invalid slice() result, must return a list of 5 objects", 5, sliceResult4.size());
			assertEquals("Invalid slice() result, must return a list of 5 objects", 5, sliceResult5.size());
			assertEquals("Invalid slice() result", testSixs.get( 0), sliceResult2.get(0));
			assertEquals("Invalid slice() result", testSixs.get( 1), sliceResult2.get(1));
			assertEquals("Invalid slice() result", testSixs.get( 2), sliceResult2.get(2));
			assertEquals("Invalid slice() result", testSixs.get( 3), sliceResult2.get(3));

			assertEquals("Invalid slice() result", testSixs.get( 4), sliceResult2.get(4));
			assertEquals("Invalid slice() result", testSixs.get( 5), sliceResult3.get(0));
			assertEquals("Invalid slice() result", testSixs.get( 6), sliceResult3.get(1));
			assertEquals("Invalid slice() result", testSixs.get( 7), sliceResult3.get(2));
			assertEquals("Invalid slice() result", testSixs.get( 8), sliceResult3.get(3));
			assertEquals("Invalid slice() result", testSixs.get( 9), sliceResult3.get(4));
			assertEquals("Invalid slice() result", testSixs.get(10), sliceResult4.get(0));
			assertEquals("Invalid slice() result", testSixs.get(11), sliceResult4.get(1));
			assertEquals("Invalid slice() result", testSixs.get(12), sliceResult4.get(2));
			assertEquals("Invalid slice() result", testSixs.get(13), sliceResult4.get(3));
			assertEquals("Invalid slice() result", testSixs.get(14), sliceResult4.get(4));
			assertEquals("Invalid slice() result", testSixs.get(15), sliceResult5.get(0));
			assertEquals("Invalid slice() result", testSixs.get(16), sliceResult5.get(1));
			assertEquals("Invalid slice() result", testSixs.get(17), sliceResult5.get(2));
			assertEquals("Invalid slice() result", testSixs.get(18), sliceResult5.get(3));
			assertEquals("Invalid slice() result", testSixs.get(19), sliceResult5.get(4));

			// first / last / nth with null
			assertEquals("Invalid first() result with null value", "", Scripting.replaceVariables(ctx, testOne, "${first(this.alwaysNull)}"));
			assertEquals("Invalid last() result with null value",  "", Scripting.replaceVariables(ctx, testOne, "${last(this.alwaysNull)}"));
			assertEquals("Invalid nth() result with null value",   "", Scripting.replaceVariables(ctx, testOne, "${nth(this.alwaysNull,  2)}"));
			assertEquals("Invalid nth() result with null value",   "", Scripting.replaceVariables(ctx, testOne, "${nth(this.alwaysNull,  7)}"));
			assertEquals("Invalid nth() result with null value",   "", Scripting.replaceVariables(ctx, testOne, "${nth(this.alwaysNull,  9)}"));
			assertEquals("Invalid nth() result with null value",  "", Scripting.replaceVariables(ctx, testOne, "${nth(this.alwaysNull, 12)}"));
			assertEquals("Invalid nth() result with null value",  "", Scripting.replaceVariables(ctx, testOne, "${nth(this.alwaysNull, this.alwaysNull)}"));
			assertEquals("Invalid nth() result with null value",  "", Scripting.replaceVariables(ctx, testOne, "${nth(this.alwaysNull, blah)}"));

			// each with null

			// get with null

			// set with null

			// set date (JS scripting)
			assertEquals("Setting the current date/time should not produce output (JS)", "", Scripting.replaceVariables(ctx, testOne, "${{var t = Structr.get('this'); t.aDate = new Date();}}"));

			try {

				// set date (old scripting)
				Scripting.replaceVariables(ctx, testOne, "${set(this, 'aDate', now)}");

			} catch (FrameworkException fex) {
				fail("Setting the current date/time should not cause an Exception (StructrScript)");
			}

			Scripting.replaceVariables(ctx, testOne, "${if(empty(this.alwaysNull), set(this, \"doResult\", true), set(this, \"doResult\", false))}");
			assertEquals("Invalid do() result", "true", Scripting.replaceVariables(ctx, testOne, "${this.doResult}"));

			Scripting.replaceVariables(ctx, testOne, "${if(empty(this.name), set(this, \"doResult\", true), set(this, \"doResult\", false))}");
			assertEquals("Invalid do() result", "false", Scripting.replaceVariables(ctx, testOne, "${this.doResult}"));

			// template method
			assertEquals("Invalid template() result", "This is a template for A-nice-little-name-for-my-test-object", Scripting.replaceVariables(ctx, testOne, "${template(\"TEST\", \"en_EN\", this)}"));

			// more complex tests
			Scripting.replaceVariables(ctx, testOne, "${each(split(\"setTestInteger1,setTestInteger2,setTestInteger3\"), set(this, data, 1))}");
			assertEquals("Invalid each() result", "1", Scripting.replaceVariables(ctx, testOne, "${get(this, \"setTestInteger1\")}"));
			assertEquals("Invalid each() result", "1", Scripting.replaceVariables(ctx, testOne, "${get(this, \"setTestInteger2\")}"));
			assertEquals("Invalid each() result", "1", Scripting.replaceVariables(ctx, testOne, "${get(this, \"setTestInteger3\")}"));

			// each expression
			assertEquals("Invalid each() result", "abc", Scripting.replaceVariables(ctx, testOne, "${each(split('a,b,c', ','), print(data))}"));
			assertEquals("Invalid each() result", "abc", Scripting.replaceVariables(ctx, testOne, "${each(this.aStringArray, print(data))}"));

			// complex each expression, sets the value of "testString" to the concatenated IDs of all testSixs that are linked to "this"
			Scripting.replaceVariables(ctx, testOne, "${each(this.manyToManyTestSixs, set(this, \"testString\", concat(get(this, \"testString\"), data.id)))}");
			assertEquals("Invalid each() result", "640", Scripting.replaceVariables(ctx, testOne, "${length(this.testString)}"));

			assertEquals("Invalid if(equal()) result", "String",  Scripting.replaceVariables(ctx, testOne, "${if(empty(this.alwaysNull), titleize(this.aString, '-'), this.alwaysNull)}"));
			assertEquals("Invalid if(equal()) result", "String",  Scripting.replaceVariables(ctx, testOne, "${if(empty(this.aString), titleize(this.alwaysNull, '-'), this.aString)}"));

			assertEquals("Invalid result for special null value", "", Scripting.replaceVariables(ctx, testOne, "${null}"));
			assertEquals("Invalid result for special null value", "", Scripting.replaceVariables(ctx, testOne, "${if(equal(this.anInt, 15), \"selected\", null)}"));

			// tests from real-life examples
			assertEquals("Invalid replacement result", "tile plan ", Scripting.replaceVariables(ctx, testOne, "tile plan ${plan.bannerTag}"));

			// more tests with pre- and postfixes
			assertEquals("Invalid replacement result", "abcdefghijklmnop", Scripting.replaceVariables(ctx, testOne, "abcdefgh${blah}ijklmnop"));
			assertEquals("Invalid replacement result", "abcdefghStringijklmnop", Scripting.replaceVariables(ctx, testOne, "abcdefgh${this.aString}ijklmnop"));
			assertEquals("Invalid replacement result", "#String", Scripting.replaceVariables(ctx, testOne, "#${this.aString}"));
			assertEquals("Invalid replacement result", "doc_sections/"+ testOne.getUuid() + "/childSections?sort=pos", Scripting.replaceVariables(ctx, testOne, "doc_sections/${this.id}/childSections?sort=pos"));
			assertEquals("Invalid replacement result", "A Nice Little Name For My Test Object", Scripting.replaceVariables(ctx, testOne, "${titleize(this.name, '-')}"));
			assertEquals("Invalid replacement result", "STRINGtrueFALSE", Scripting.replaceVariables(ctx, testOne, "${upper(this.aString)}${lower(true)}${upper(false)}"));

			// null and NULL_STRING
			assertEquals("Invalid result for ___NULL___", "", Scripting.replaceVariables(ctx, testOne, "${null}"));
			assertEquals("Invalid result for ___NULL___", "", Scripting.replaceVariables(ctx, testOne, "${___NULL___}"));
			assertEquals("Invalid result for ___NULL___", "", Scripting.replaceVariables(ctx, testOne, "${is(true, ___NULL___)}"));
			assertEquals("Invalid result for ___NULL___", "", Scripting.replaceVariables(ctx, testOne, "${is(false, ___NULL___)}"));
			assertEquals("Invalid result for ___NULL___", "xy", Scripting.replaceVariables(ctx, testOne, "x${___NULL___}y"));
			assertEquals("Invalid result for ___NULL___", "xz", Scripting.replaceVariables(ctx, testOne, "x${is(true, ___NULL___)}z"));
			assertEquals("Invalid result for ___NULL___", "xz", Scripting.replaceVariables(ctx, testOne, "x${is(false, ___NULL___)}z"));

			// test store and retrieve
			assertEquals("Invalid store() result", "", Scripting.replaceVariables(ctx, testOne, "${store('tmp', this.name)}"));
			assertEquals("Invalid stored value", "A-nice-little-name-for-my-test-object", ctx.retrieve("tmp"));
			assertEquals("Invalid retrieve() result", "A-nice-little-name-for-my-test-object", Scripting.replaceVariables(ctx, testOne, "${retrieve('tmp')}"));
			assertEquals("Invalid retrieve() result", "", Scripting.replaceVariables(new ActionContext(SecurityContext.getSuperUserInstance()), testOne, "${retrieve('tmp')}"));

			// test store and retrieve within filter expression
			assertEquals("Invalid store() result", "", Scripting.replaceVariables(ctx, testOne, "${store('tmp', 10)}"));
			assertEquals("Invalid retrieve() result in filter expression", "9",  Scripting.replaceVariables(ctx, testOne, "${size(filter(this.manyToManyTestSixs, gt(data.index, 10)))}"));
			assertEquals("Invalid retrieve() result in filter expression", "9",  Scripting.replaceVariables(ctx, testOne, "${size(filter(this.manyToManyTestSixs, gt(data.index, retrieve('tmp'))))}"));

			// retrieve object and access attribute
			assertEquals("Invalid store() result", "", Scripting.replaceVariables(ctx, testOne, "${store('testOne', this)}"));
			assertEquals("Invalid retrieve() result", "A-nice-little-name-for-my-test-object", Scripting.replaceVariables(ctx, testOne, "${retrieve('testOne').name}"));

			// retrieve stored object attribute in if() expression via get() function
			assertEquals("Invalid retrieve() result", "A-nice-little-name-for-my-test-object", Scripting.replaceVariables(ctx, testOne, "${if(false,'true', get(retrieve('testOne'), 'name'))}"));

			// retrieve stored object attribute in if() expression via 'dot-name'
			assertEquals("Invalid retrieve() result", "A-nice-little-name-for-my-test-object", Scripting.replaceVariables(ctx, testOne, "${if(false,'true', retrieve('testOne').name)}"));

			// test replace() method
			assertEquals("Invalid replace() result", "A-nice-little-name-for-my-test-object", Scripting.replaceVariables(ctx, testOne, "${replace(this.replaceString, this)}"));

			// test error method
			try {
				Actions.execute(securityContext, testTwo, "${error(\"base\", \"test1\")}", "test");
				fail("error() should throw an exception.");

			} catch (UnlicensedScriptException |FrameworkException fex) { }

			try {
				Actions.execute(securityContext, testTwo, "${error(\"base\", \"test1\", \"test2\")}", "test");
				fail("error() should throw an exception.");

			} catch (UnlicensedScriptException |FrameworkException fex) { }

			// test multiline statements
			assertEquals("Invalid replace() result", "equal", Scripting.replaceVariables(ctx, testOne, "${if(equal(2, 2),\n    (\"equal\"),\n    (\"not equal\")\n)}"));
			assertEquals("Invalid replace() result", "not equal", Scripting.replaceVariables(ctx, testOne, "${if(equal(2, 3),\n    (\"equal\"),\n    (\"not equal\")\n)}"));

			assertEquals("Invalid keys() / join() result", "id,name,owner,type,createdBy,hidden,createdDate,lastModifiedDate,visibleToPublicUsers,visibleToAuthenticatedUsers", Scripting.replaceVariables(ctx, testOne, "${join(keys(this, 'ui'), ',')}"));
			assertEquals("Invalid values() / join() result", "A-nice-little-name-for-my-test-object,1,String", Scripting.replaceVariables(ctx, testOne, "${join(values(this, 'protected'), ',')}"));

			// test default values
			assertEquals("Invalid string default value", "blah", Scripting.replaceVariables(ctx, testOne, "${this.alwaysNull!blah}"));
			assertEquals("Invalid numeric default value", "12", Scripting.replaceVariables(ctx, testOne, "${this.alwaysNull!12}"));

			// Number default value
			assertEquals("true", Scripting.replaceVariables(ctx, testOne, "${equal(42, this.alwaysNull!42)}"));

			// complex multi-statement tests
			Scripting.replaceVariables(ctx, testOne, "${(set(this, \"isValid\", true), each(this.manyToManyTestSixs, set(this, \"isValid\", and(this.isValid, equal(length(data.id), 32)))))}");
			assertEquals("Invalid multiline statement test result", "true", Scripting.replaceVariables(ctx, testOne, "${this.isValid}"));

			Scripting.replaceVariables(ctx, testOne, "${(set(this, \"isValid\", true), each(this.manyToManyTestSixs, set(this, \"isValid\", and(this.isValid, gte(now, data.createdDate)))))}");
			assertEquals("Invalid multiline statement test result", "true", Scripting.replaceVariables(ctx, testOne, "${this.isValid}"));

			Scripting.replaceVariables(ctx, testOne, "${(set(this, \"isValid\", false), each(this.manyToManyTestSixs, set(this, \"isValid\", and(this.isValid, gte(now, data.createdDate)))))}");
			assertEquals("Invalid multiline statement test result", "false", Scripting.replaceVariables(ctx, testOne, "${this.isValid}"));

			// test multiple nested dot-separated properties (this.parent.parent.parent)
			assertEquals("Invalid multilevel property expression result", "false", Scripting.replaceVariables(ctx, testOne, "${empty(this.testThree.testOne.testThree)}"));

			// test filter() with additional evaluation function
			assertEquals("Invalid filter() result", "1",  Scripting.replaceVariables(ctx, testOne, "${size(filter(this.manyToManyTestSixs, equal(data.index, 4)))}"));
			assertEquals("Invalid filter() result", "9",  Scripting.replaceVariables(ctx, testOne, "${size(filter(this.manyToManyTestSixs, gt(data.index, 10)))}"));
			assertEquals("Invalid filter() result", "10", Scripting.replaceVariables(ctx, testOne, "${size(filter(this.manyToManyTestSixs, gte(data.index, 10)))}"));

			// test complex multiline statement replacement
			final String test =
				"${if(lte(template('TEST2', 'en_EN', this), 2), '<2', '>2')}\n" +		// first expression should evaluate to ">2"
				"${if(lte(template('TEST2', 'en_EN', this), 3), '<3', '>3')}"			// second expression should evaluate to "<3"
			;

			final String result = Scripting.replaceVariables(ctx, testOne, test);

			assertEquals("Invalid multiline and template() result", ">2\n<3", result);

			// incoming
			assertEquals("Invalid number of incoming relationships", "20",  Scripting.replaceVariables(ctx, testOne, "${size(incoming(this))}"));
			assertEquals("Invalid number of incoming relationships", "20",  Scripting.replaceVariables(ctx, testOne, "${size(incoming(this, 'MANY_TO_MANY'))}"));
			assertEquals("Invalid number of incoming relationships", "1",   Scripting.replaceVariables(ctx, testTwo, "${size(incoming(this))}"));
			assertEquals("Invalid number of incoming relationships", "1",   Scripting.replaceVariables(ctx, testThree, "${size(incoming(this))}"));
			assertEquals("Invalid relationship type", "IS_AT",              Scripting.replaceVariables(ctx, testTwo, "${get(incoming(this), 'relType')}"));
			assertEquals("Invalid relationship type", "OWNS",               Scripting.replaceVariables(ctx, testThree, "${get(incoming(this), 'relType')}"));

			// outgoing
			assertEquals("Invalid number of outgoing relationships", "3",  Scripting.replaceVariables(ctx, testOne, "${size(outgoing(this))}"));
			assertEquals("Invalid number of outgoing relationships", "2",  Scripting.replaceVariables(ctx, testOne, "${size(outgoing(this, 'IS_AT'))}"));
			assertEquals("Invalid number of outgoing relationships", "1",  Scripting.replaceVariables(ctx, testOne, "${size(outgoing(this, 'OWNS' ))}"));
			assertEquals("Invalid relationship type", "IS_AT",             Scripting.replaceVariables(ctx, testOne, "${get(first(outgoing(this, 'IS_AT')), 'relType')}"));
			assertEquals("Invalid relationship type", "OWNS",              Scripting.replaceVariables(ctx, testOne, "${get(outgoing(this, 'OWNS'), 'relType')}"));

			// has_relationships
			assertEquals("Invalid result of has_relationship", "false",  Scripting.replaceVariables(ctx, testOne, "${has_relationship(this, this)}"));

			assertEquals("Invalid result of has_relationship", "true",  Scripting.replaceVariables(ctx, testOne, "${has_relationship(this, first(find('TestTwo', 'name', 'testTwo_name')))}"));
			assertEquals("Invalid result of has_relationship", "true",  Scripting.replaceVariables(ctx, testOne, "${has_relationship(this, first(find('TestTwo', 'name', 'testTwo_name')), 'IS_AT')}"));
			assertEquals("Invalid result of has_relationship", "true",  Scripting.replaceVariables(ctx, testOne, "${has_relationship(first(find('TestTwo', 'name', 'testTwo_name')), this, 'IS_AT')}"));
			assertEquals("Invalid result of has_relationship", "false",  Scripting.replaceVariables(ctx, testOne, "${has_relationship(this, first(find('TestTwo', 'name', 'testTwo_name')), 'THIS_DOES_NOT_EXIST')}"));

			assertEquals("Invalid result of has_relationship", "true",  Scripting.replaceVariables(ctx, testTwo, "${has_relationship(first(find('TestOne', 'name', 'A-nice-little-name-for-my-test-object')), this)}"));
			assertEquals("Invalid result of has_relationship", "true",  Scripting.replaceVariables(ctx, testTwo, "${has_relationship(this, first(find('TestOne', 'name', 'A-nice-little-name-for-my-test-object')))}"));

			assertEquals("Invalid result of has_relationship", "true",  Scripting.replaceVariables(ctx, testOne, "${has_relationship(this, first(find('TestThree', 'name', 'testThree_name')))}"));
			assertEquals("Invalid result of has_relationship", "true",  Scripting.replaceVariables(ctx, testOne, "${has_relationship(this, first(find('TestThree', 'name', 'testThree_name')), 'OWNS')}"));

			assertEquals("Invalid result of has_relationship", "false", Scripting.replaceVariables(ctx, testTwo, "${has_relationship(first(find('TestOne', 'name', 'A-nice-little-name-for-my-test-object')), this, 'THIS_DOES_NOT_EXIST')}"));
			assertEquals("Invalid result of has_relationship", "false",  Scripting.replaceVariables(ctx, testOne, "${has_relationship(this, first(find('TestThree', 'name', 'testThree_name')), 'THIS_DOES_NOT_EXIST')}"));

			// has_incoming_relationship
			assertEquals("Invalid result of has_incoming_relationship", "false",  Scripting.replaceVariables(ctx, testOne, "${has_incoming_relationship(this, this)}"));

			assertEquals("Invalid result of has_incoming_relationship", "true",  Scripting.replaceVariables(ctx, testOne, "${has_incoming_relationship(first(find('TestTwo', 'name', 'testTwo_name')), this)}"));
			assertEquals("Invalid result of has_incoming_relationship", "false", Scripting.replaceVariables(ctx, testOne, "${has_incoming_relationship(this, first(find('TestTwo', 'name', 'testTwo_name')))}"));

			assertEquals("Invalid result of has_incoming_relationship", "true",  Scripting.replaceVariables(ctx, testOne, "${has_incoming_relationship(first(find('TestTwo', 'name', 'testTwo_name')), this, 'IS_AT')}"));
			assertEquals("Invalid result of has_incoming_relationship", "false", Scripting.replaceVariables(ctx, testOne, "${has_incoming_relationship(this, first(find('TestTwo', 'name', 'testTwo_name')), 'IS_AT')}"));

			assertEquals("Invalid result of has_incoming_relationship", "true",  Scripting.replaceVariables(ctx, testOne, "${has_incoming_relationship(first(find('TestTwo', 'name', 'testTwo_name')), this, 'IS_AT')}"));
			assertEquals("Invalid result of has_incoming_relationship", "false",  Scripting.replaceVariables(ctx, testOne, "${has_incoming_relationship(this, first(find('TestTwo', 'name', 'testTwo_name')), 'THIS_DOES_NOT_EXIST')}"));
			assertEquals("Invalid result of has_incoming_relationship", "false",  Scripting.replaceVariables(ctx, testOne, "${has_incoming_relationship(first(find('TestTwo', 'name', 'testTwo_name')), this, 'THIS_DOES_NOT_EXIST')}"));

			assertEquals("Invalid result of has_incoming_relationship", "true",  Scripting.replaceVariables(ctx, testTwo, "${has_incoming_relationship(this, first(find('TestOne', 'name', 'A-nice-little-name-for-my-test-object')))}"));
			assertEquals("Invalid result of has_incoming_relationship", "false", Scripting.replaceVariables(ctx, testTwo, "${has_incoming_relationship(first(find('TestOne', 'name', 'A-nice-little-name-for-my-test-object')), this)}"));

			assertEquals("Invalid result of has_incoming_relationship", "false", Scripting.replaceVariables(ctx, testOne, "${has_incoming_relationship(this, first(find('TestThree', 'name', 'testThree_name')))}"));
			assertEquals("Invalid result of has_incoming_relationship", "true",  Scripting.replaceVariables(ctx, testOne, "${has_incoming_relationship(first(find('TestThree', 'name', 'testThree_name')), this)}"));

			assertEquals("Invalid result of has_incoming_relationship", "false", Scripting.replaceVariables(ctx, testOne, "${has_incoming_relationship(this, first(find('TestThree', 'name', 'testThree_name')), 'OWNS')}"));
			assertEquals("Invalid result of has_incoming_relationship", "true",  Scripting.replaceVariables(ctx, testOne, "${has_incoming_relationship(first(find('TestThree', 'name', 'testThree_name')), this, 'OWNS')}"));

			assertEquals("Invalid result of has_incoming_relationship", "false", Scripting.replaceVariables(ctx, testOne, "${has_incoming_relationship(this, first(find('TestThree', 'name', 'testThree_name')), 'THIS_DOES_NOT_EXIST')}"));
			assertEquals("Invalid result of has_incoming_relationship", "false", Scripting.replaceVariables(ctx, testOne, "${has_incoming_relationship(first(find('TestThree', 'name', 'testThree_name')), this, 'THIS_DOES_NOT_EXIST')}"));

			assertEquals("Invalid result of has_incoming_relationship", "false", Scripting.replaceVariables(ctx, testTwo, "${has_incoming_relationship(first(find('TestOne', 'name', 'A-nice-little-name-for-my-test-object')), this, 'THIS_DOES_NOT_EXIST')}"));
			assertEquals("Invalid result of has_incoming_relationship", "false", Scripting.replaceVariables(ctx, testTwo, "${has_incoming_relationship(this, first(find('TestOne', 'name', 'A-nice-little-name-for-my-test-object')), 'THIS_DOES_NOT_EXIST')}"));
			assertEquals("Invalid result of has_incoming_relationship", "false", Scripting.replaceVariables(ctx, testOne, "${has_incoming_relationship(this, first(find('TestThree', 'name', 'testThree_name')), 'THIS_DOES_NOT_EXIST')}"));
			assertEquals("Invalid result of has_incoming_relationship", "false", Scripting.replaceVariables(ctx, testOne, "${has_incoming_relationship(first(find('TestThree', 'name', 'testThree_name')), this, 'THIS_DOES_NOT_EXIST')}"));

			// has_outgoing_relationship (since has_outgoing_relationship is just the inverse method to has_outgoing_relationship we can basically reuse the tests and just invert the result - except for the always-false or always-true tests)
			assertEquals("Invalid result of has_outgoing_relationship", "false",  Scripting.replaceVariables(ctx, testOne, "${has_outgoing_relationship(this, this)}"));

			assertEquals("Invalid result of has_outgoing_relationship", "false", Scripting.replaceVariables(ctx, testOne, "${has_outgoing_relationship(first(find('TestTwo', 'name', 'testTwo_name')), this)}"));
			assertEquals("Invalid result of has_outgoing_relationship", "true",  Scripting.replaceVariables(ctx, testOne, "${has_outgoing_relationship(this, first(find('TestTwo', 'name', 'testTwo_name')))}"));

			assertEquals("Invalid result of has_outgoing_relationship", "false", Scripting.replaceVariables(ctx, testOne, "${has_outgoing_relationship(first(find('TestTwo', 'name', 'testTwo_name')), this, 'IS_AT')}"));
			assertEquals("Invalid result of has_outgoing_relationship", "true",  Scripting.replaceVariables(ctx, testOne, "${has_outgoing_relationship(this, first(find('TestTwo', 'name', 'testTwo_name')), 'IS_AT')}"));

			assertEquals("Invalid result of has_outgoing_relationship", "false", Scripting.replaceVariables(ctx, testOne, "${has_outgoing_relationship(first(find('TestTwo', 'name', 'testTwo_name')), this, 'IS_AT')}"));
			assertEquals("Invalid result of has_outgoing_relationship", "false", Scripting.replaceVariables(ctx, testOne, "${has_outgoing_relationship(this, first(find('TestTwo', 'name', 'testTwo_name')), 'THIS_DOES_NOT_EXIST')}"));
			assertEquals("Invalid result of has_outgoing_relationship", "false", Scripting.replaceVariables(ctx, testOne, "${has_outgoing_relationship(first(find('TestTwo', 'name', 'testTwo_name')), this, 'THIS_DOES_NOT_EXIST')}"));

			assertEquals("Invalid result of has_outgoing_relationship", "false", Scripting.replaceVariables(ctx, testTwo, "${has_outgoing_relationship(this, first(find('TestOne', 'name', 'A-nice-little-name-for-my-test-object')))}"));
			assertEquals("Invalid result of has_outgoing_relationship", "true",  Scripting.replaceVariables(ctx, testTwo, "${has_outgoing_relationship(first(find('TestOne', 'name', 'A-nice-little-name-for-my-test-object')), this)}"));

			assertEquals("Invalid result of has_outgoing_relationship", "true",  Scripting.replaceVariables(ctx, testOne, "${has_outgoing_relationship(this, first(find('TestThree', 'name', 'testThree_name')))}"));
			assertEquals("Invalid result of has_outgoing_relationship", "false", Scripting.replaceVariables(ctx, testOne, "${has_outgoing_relationship(first(find('TestThree', 'name', 'testThree_name')), this)}"));

			assertEquals("Invalid result of has_outgoing_relationship", "true",  Scripting.replaceVariables(ctx, testOne, "${has_outgoing_relationship(this, first(find('TestThree', 'name', 'testThree_name')), 'OWNS')}"));
			assertEquals("Invalid result of has_outgoing_relationship", "false", Scripting.replaceVariables(ctx, testOne, "${has_outgoing_relationship(first(find('TestThree', 'name', 'testThree_name')), this, 'OWNS')}"));

			assertEquals("Invalid result of has_outgoing_relationship", "false", Scripting.replaceVariables(ctx, testOne, "${has_outgoing_relationship(this, first(find('TestThree', 'name', 'testThree_name')), 'THIS_DOES_NOT_EXIST')}"));
			assertEquals("Invalid result of has_outgoing_relationship", "false", Scripting.replaceVariables(ctx, testOne, "${has_outgoing_relationship(first(find('TestThree', 'name', 'testThree_name')), this, 'THIS_DOES_NOT_EXIST')}"));

			assertEquals("Invalid result of has_outgoing_relationship", "false", Scripting.replaceVariables(ctx, testTwo, "${has_outgoing_relationship(first(find('TestOne', 'name', 'A-nice-little-name-for-my-test-object')), this, 'THIS_DOES_NOT_EXIST')}"));
			assertEquals("Invalid result of has_outgoing_relationship", "false", Scripting.replaceVariables(ctx, testTwo, "${has_outgoing_relationship(this, first(find('TestOne', 'name', 'A-nice-little-name-for-my-test-object')), 'THIS_DOES_NOT_EXIST')}"));
			assertEquals("Invalid result of has_outgoing_relationship", "false", Scripting.replaceVariables(ctx, testOne, "${has_outgoing_relationship(this, first(find('TestThree', 'name', 'testThree_name')), 'THIS_DOES_NOT_EXIST')}"));
			assertEquals("Invalid result of has_outgoing_relationship", "false", Scripting.replaceVariables(ctx, testOne, "${has_outgoing_relationship(first(find('TestThree', 'name', 'testThree_name')), this, 'THIS_DOES_NOT_EXIST')}"));

			// get_relationships (CAUTION! If the method returns a string (error-case) the size-method returns "1" => it seems like there is one relationsh)
			assertEquals("Invalid number of relationships", "0",  Scripting.replaceVariables(ctx, testOne, "${size(get_relationships(this, this))}"));

			// non-existent relType between nodes which have a relationship
			assertEquals("Invalid number of relationships", "0",  Scripting.replaceVariables(ctx, testOne, "${size(get_relationships(first(find('TestTwo', 'name', 'testTwo_name')), this, 'THIS_DOES_NOT_EXIST'))}"));
			// non-existent relType between a node and itself
			assertEquals("Invalid number of relationships", "0",  Scripting.replaceVariables(ctx, testOne, "${size(get_relationships(first(find('TestOne', 'name', 'A-nice-little-name-for-my-test-object')), this, 'THIS_DOES_NOT_EXIST'))}"));

			// identical result test (from and to are just switched around)
			assertEquals("Invalid number of relationships", "1",  Scripting.replaceVariables(ctx, testTwo, "${size(get_relationships(first(find('TestOne', 'name', 'A-nice-little-name-for-my-test-object')), this, 'IS_AT'))}"));
			assertEquals("Invalid number of relationships", "1",  Scripting.replaceVariables(ctx, testTwo, "${size(get_relationships(this, first(find('TestOne', 'name', 'A-nice-little-name-for-my-test-object')), 'IS_AT'))}"));


			// get_incoming_relationships (CAUTION! If the method returns a string (error-case) the size-method returns "1" => it seems like there is one relationsh)
			assertEquals("Invalid number of incoming relationships", "0",  Scripting.replaceVariables(ctx, testOne, "${size(get_incoming_relationships(this, this))}"));

			assertEquals("Invalid number of incoming relationships", "0",  Scripting.replaceVariables(ctx, testOne, "${size(get_incoming_relationships(this, first(find('TestTwo', 'name', 'testTwo_name'))))}"));
			assertEquals("Invalid number of incoming relationships", "1",  Scripting.replaceVariables(ctx, testOne, "${size(get_incoming_relationships(first(find('TestTwo', 'name', 'testTwo_name')), this))}"));
			assertEquals("Invalid number of incoming relationships", "0",  Scripting.replaceVariables(ctx, testOne, "${size(get_incoming_relationships(this, first(find('TestTwo', 'name', 'testTwo_name')), 'IS_AT'))}"));
			assertEquals("Invalid number of incoming relationships", "1",  Scripting.replaceVariables(ctx, testOne, "${size(get_incoming_relationships(first(find('TestTwo', 'name', 'testTwo_name')), this, 'IS_AT'))}"));

			assertEquals("Invalid number of incoming relationships", "1",  Scripting.replaceVariables(ctx, testTwo, "${size(get_incoming_relationships(this, first(find('TestOne', 'name', 'A-nice-little-name-for-my-test-object'))))}"));
			assertEquals("Invalid number of incoming relationships", "1",Scripting.replaceVariables(ctx, testThree, "${size(get_incoming_relationships(this, first(find('TestOne', 'name', 'A-nice-little-name-for-my-test-object'))))}"));
			assertEquals("Invalid relationship type", "IS_AT",             Scripting.replaceVariables(ctx, testTwo, "${get(first(get_incoming_relationships(this, first(find('TestOne', 'name', 'A-nice-little-name-for-my-test-object')))), 'relType')}"));

			assertEquals("Invalid relationship type", "OWNS",            Scripting.replaceVariables(ctx, testThree, "${get(first(get_incoming_relationships(this, first(find('TestOne', 'name', 'A-nice-little-name-for-my-test-object')))), 'relType')}"));


			// get_outgoing_relationships (CAUTION! If the method returns a string (error-case) the size-method returns "1" => it seems like there is one relationsh)
			assertEquals("Invalid number of outgoing relationships", "0",  Scripting.replaceVariables(ctx, testOne, "${size(get_outgoing_relationships(first(find('TestOne', 'name', 'A-nice-little-name-for-my-test-object')), this))}"));

			assertEquals("Invalid number of outgoing relationships", "0",  Scripting.replaceVariables(ctx, testTwo, "${size(get_outgoing_relationships(this, first(find('TestOne', 'name', 'A-nice-little-name-for-my-test-object'))))}"));

			assertEquals("Invalid number of outgoing relationships", "1",  Scripting.replaceVariables(ctx, testTwo, "${size(get_outgoing_relationships(first(find('TestOne', 'name', 'A-nice-little-name-for-my-test-object')), this))}"));
			assertEquals("Invalid number of outgoing relationships", "0",  Scripting.replaceVariables(ctx, testTwo, "${size(get_outgoing_relationships(first(find('TestOne', 'name', 'A-nice-little-name-for-my-test-object')), this, 'THIS_DOES_NOT_EXIST'))}"));

			assertEquals("Invalid number of outgoing relationships", "1",Scripting.replaceVariables(ctx, testThree, "${size(get_outgoing_relationships(first(find('TestOne', 'name', 'A-nice-little-name-for-my-test-object')), this))}"));
			assertEquals("Invalid relationship type", "IS_AT",             Scripting.replaceVariables(ctx, testTwo, "${get(first(get_outgoing_relationships(first(find('TestOne', 'name', 'A-nice-little-name-for-my-test-object')), this)), 'relType')}"));

			assertEquals("Invalid relationship type", "OWNS",            Scripting.replaceVariables(ctx, testThree, "${get(first(get_outgoing_relationships(first(find('TestOne', 'name', 'A-nice-little-name-for-my-test-object')), this)), 'relType')}"));

			// create_relationship
			// lifecycle for relationship t1-[:NEW_RELATIONSHIP_NAME]->t1
			assertEquals("Invalid number of relationships", "0", Scripting.replaceVariables(ctx, testOne, "${size(get_outgoing_relationships(this, this, 'IS_AT'))}"));
			assertEquals("unexpected result of create_relationship", "IS_AT",  Scripting.replaceVariables(ctx, testOne, "${get(create_relationship(this, this, 'IS_AT'), 'relType')}"));
			assertEquals("Invalid number of relationships", "1", Scripting.replaceVariables(ctx, testOne, "${size(get_outgoing_relationships(this, this, 'IS_AT'))}"));
			assertEquals("unexpected result of delete", "",  Scripting.replaceVariables(ctx, testOne, "${delete(first(get_outgoing_relationships(this, this, 'IS_AT')))}"));
			assertEquals("Invalid number of relationships", "0", Scripting.replaceVariables(ctx, testOne, "${size(get_outgoing_relationships(this, this, 'IS_AT'))}"));

			// lifecycle for relationship t2-[:NEW_RELATIONSHIP_NAME]->t1
			assertEquals("Invalid number of relationships", "0", Scripting.replaceVariables(ctx, testOne, "${size(get_outgoing_relationships(first(find('TestTwo', 'name', 'testTwo_name')), this, 'IS_AT'))}"));
			assertEquals("unexpected result of create_relationship", "IS_AT",  Scripting.replaceVariables(ctx, testOne, "${get(create_relationship(first(find('TestTwo', 'name', 'testTwo_name')), this, 'IS_AT'), 'relType')}"));
			assertEquals("Invalid number of relationships", "1", Scripting.replaceVariables(ctx, testOne, "${size(get_outgoing_relationships(first(find('TestTwo', 'name', 'testTwo_name')), this, 'IS_AT'))}"));
			assertEquals("unexpected result of delete", "",  Scripting.replaceVariables(ctx, testOne, "${delete(first(get_outgoing_relationships(first(find('TestTwo', 'name', 'testTwo_name')), this, 'IS_AT')))}"));
			assertEquals("Invalid number of relationships", "0", Scripting.replaceVariables(ctx, testOne, "${size(get_outgoing_relationships(first(find('TestTwo', 'name', 'testTwo_name')), this, 'IS_AT'))}"));

			// array index access
			assertEquals("Invalid array index accessor result", testSixs.get(0).getUuid(), Scripting.replaceVariables(ctx, testOne, "${this.manyToManyTestSixs[0]}"));
			assertEquals("Invalid array index accessor result", testSixs.get(2).getUuid(), Scripting.replaceVariables(ctx, testOne, "${this.manyToManyTestSixs[2]}"));
			assertEquals("Invalid array index accessor result", testSixs.get(4).getUuid(), Scripting.replaceVariables(ctx, testOne, "${this.manyToManyTestSixs[4]}"));

			// test new dot notation
			assertEquals("Invalid dot notation result", testSixs.get(0).getProperty(AbstractNode.name), Scripting.replaceVariables(ctx, testOne, "${this.manyToManyTestSixs[0].name}"));
			assertEquals("Invalid dot notation result", testSixs.get(0).getProperty(AbstractNode.name), Scripting.replaceVariables(ctx, testOne, "${sort(find('TestSix'), 'name')[0].name}"));
			assertEquals("Invalid dot notation result", testSixs.get(15).getProperty(AbstractNode.name), Scripting.replaceVariables(ctx, testOne, "${sort(find('TestSix'), 'name')[15].name}"));
			assertEquals("Invalid dot notation result", "20", Scripting.replaceVariables(ctx, testOne, "${this.manyToManyTestSixs.size}"));

			// test array property access
			assertEquals("Invalid string array access result", "one", Scripting.replaceVariables(ctx, testFour, "${this.stringArrayProperty[0]}"));
			assertEquals("Invalid string array access result", "two", Scripting.replaceVariables(ctx, testFour, "${this.stringArrayProperty[1]}"));
			assertEquals("Invalid string array access result", "three", Scripting.replaceVariables(ctx, testFour, "${this.stringArrayProperty[2]}"));
			assertEquals("Invalid string array access result", "four", Scripting.replaceVariables(ctx, testFour, "${this.stringArrayProperty[3]}"));

			// test string array property support in collection access methods
			assertEquals("Invalid string array access result with join()", "one,two,three,four", Scripting.replaceVariables(ctx, testFour, "${join(this.stringArrayProperty, ',')}"));
			assertEquals("Invalid string array access result with concat()", "onetwothreefour", Scripting.replaceVariables(ctx, testFour, "${concat(this.stringArrayProperty)}"));
			assertEquals("Invalid string array access result with slice()", "two,three", Scripting.replaceVariables(ctx, testFour, "${join(slice(this.stringArrayProperty, 1, 3), ',')}"));
			assertEquals("Invalid string array access result with first()", "one", Scripting.replaceVariables(ctx, testFour, "${first(this.stringArrayProperty)}"));
			assertEquals("Invalid string array access result with last()", "four", Scripting.replaceVariables(ctx, testFour, "${last(this.stringArrayProperty)}"));
			assertEquals("Invalid string array access result with size()", "4", Scripting.replaceVariables(ctx, testFour, "${size(this.stringArrayProperty)}"));
			assertEquals("Invalid string array access result with .size", "4", Scripting.replaceVariables(ctx, testFour, "${this.stringArrayProperty.size}"));
			assertEquals("Invalid string array access result with nth", "one", Scripting.replaceVariables(ctx, testFour, "${nth(this.stringArrayProperty, 0)}"));
			assertEquals("Invalid string array access result with nth", "two", Scripting.replaceVariables(ctx, testFour, "${nth(this.stringArrayProperty, 1)}"));
			assertEquals("Invalid string array access result with nth", "three", Scripting.replaceVariables(ctx, testFour, "${nth(this.stringArrayProperty, 2)}"));
			assertEquals("Invalid string array access result with nth", "four", Scripting.replaceVariables(ctx, testFour, "${nth(this.stringArrayProperty, 3)}"));
			assertEquals("Invalid string array access result with contains()", "true", Scripting.replaceVariables(ctx, testFour, "${contains(this.stringArrayProperty, 'two')}"));
			assertEquals("Invalid string array access result with contains()", "false", Scripting.replaceVariables(ctx, testFour, "${contains(this.stringArrayProperty, 'five')}"));

			// sort on arrays in JS
			assertEquals("Invalid sort result", "[TestSix19, TestSix18, TestSix17, TestSix16, TestSix15, TestSix14, TestSix13, TestSix12, TestSix11, TestSix10, TestSix09, TestSix08, TestSix07, TestSix06, TestSix05, TestSix04, TestSix03, TestSix02, TestSix01, TestSix00]", Scripting.replaceVariables(ctx, null, "${{ return $.extract($.sort($.find('TestOne')[0].manyToManyTestSixs, 'name', true), 'name'); }}"));

			// sort with extract
			assertEquals("Invalid sort result", "[b, a, c]", Scripting.replaceVariables(ctx, null, "${merge('b', 'a', 'c')}"));
			assertEquals("Invalid sort result", "[a, b, c]", Scripting.replaceVariables(ctx, null, "${sort(merge('b', 'a', 'c'))}"));
			assertEquals("Invalid sort result", "",          Scripting.replaceVariables(ctx, null, "${sort()}"));
			assertEquals("Invalid sort result", "[TestSix19, TestSix18, TestSix17, TestSix16, TestSix15, TestSix14, TestSix13, TestSix12, TestSix11, TestSix10, TestSix09, TestSix08, TestSix07, TestSix06, TestSix05, TestSix04, TestSix03, TestSix02, TestSix01, TestSix00]",          Scripting.replaceVariables(ctx, testOne, "${extract(sort(this.manyToManyTestSixs, 'index', true), 'name')}"));
			assertEquals("Invalid sort result", "[A-nice-little-name-for-my-test-object, testThree_name, testTwo_name]", Scripting.replaceVariables(ctx, testOne, "${extract(sort(merge(this, this.testTwo, this.testThree), 'name'), 'name')}"));
			assertEquals("Invalid sort result", "[A-nice-little-name-for-my-test-object, testThree_name, testTwo_name]", Scripting.replaceVariables(ctx, testOne, "${extract(sort(merge(this.testTwo, this, this.testThree), 'name'), 'name')}"));
			assertEquals("Invalid sort result", "[A-nice-little-name-for-my-test-object, testThree_name, testTwo_name]", Scripting.replaceVariables(ctx, testOne, "${extract(sort(merge(this.testTwo, this.testThree, this), 'name'), 'name')}"));

			// extract
			assertEquals("Invalid extract() result for relationship property", "[[" + StringUtils.join(testSixs, ", ") +  "]]", Scripting.replaceVariables(ctx, testOne, "${extract(find('TestOne'), 'manyToManyTestSixs')}"));
			assertEquals("Invalid extract() result for relationship property", "[" + StringUtils.join(testSixNames, ", ") +  "]", Scripting.replaceVariables(ctx, testOne, "${extract(unwind(extract(find('TestOne'), 'manyToManyTestSixs')), 'name')}"));

			// find
			assertEquals("Invalid find() result for empty values", testThree.getUuid(), Scripting.replaceVariables(ctx, testOne, "${first(find('TestThree', 'oneToOneTestSix', this.alwaysNull))}"));
			assertEquals("Invalid find() result for empty values", testThree.getUuid(), Scripting.replaceVariables(ctx, testOne, "${first(find('TestThree', 'oneToManyTestSix', this.alwaysNull))}"));

			// find with incorrect number of parameters
			assertEquals("Invalid find() result", FindFunction.ERROR_MESSAGE_FIND_NO_TYPE_SPECIFIED, Scripting.replaceVariables(ctx, testOne, "${find()}"));
			assertEquals("Invalid find() result", FindFunction.ERROR_MESSAGE_FIND_NO_TYPE_SPECIFIED, Scripting.replaceVariables(ctx, testOne, "${find(this.alwaysNull)}"));
			assertEquals("Invalid find() result", FindFunction.ERROR_MESSAGE_FIND_NO_TYPE_SPECIFIED, Scripting.replaceVariables(ctx, testOne, "${find(this.alwaysNull, this.alwaysNull)}"));
			assertEquals("Invalid find() result", FindFunction.ERROR_MESSAGE_FIND_TYPE_NOT_FOUND + "NonExistingType", Scripting.replaceVariables(ctx, testOne, "${find('NonExistingType')}"));

			// search
			assertEquals("Invalid search() result", testOne.getUuid(), Scripting.replaceVariables(ctx, testTwo, "${first(search('TestOne', 'name', 'A-nice-little-name-for-my-test-object'))}"));
			assertEquals("Invalid search() result", testOne.getUuid(), Scripting.replaceVariables(ctx, testTwo, "${first(search('TestOne', 'name', 'little-name-for-my-test-object'))}"));
			assertEquals("Invalid search() result", testOne.getUuid(), Scripting.replaceVariables(ctx, testTwo, "${first(search('TestOne', 'name', 'A-nice-little-name-for'))}"));

			// negative test for find()
			assertEquals("Invalid find() result", "", Scripting.replaceVariables(ctx, testTwo, "${first(find('TestOne', 'name', 'little-name-for-my-test-object'))}"));
			assertEquals("Invalid find() result", "", Scripting.replaceVariables(ctx, testTwo, "${first(find('TestOne', 'name', 'A-nice-little-name-for'))}"));

			// create
			Integer noOfOnes = 1;
			assertEquals("Invalid number of TestOne's", ""+noOfOnes, Scripting.replaceVariables(ctx, testOne, "${size(find('TestOne'))}"));

			// currently the creation of nodes must take place in a node of another type
			Scripting.replaceVariables(ctx, testFour, "${create('TestOne', 'name', 'createTestOne1')}");
			noOfOnes++;
			assertEquals("Invalid number of TestOne's", ""+noOfOnes, Scripting.replaceVariables(ctx, testOne, "${size(find('TestOne'))}"));
			assertEquals("Invalid number of TestOne's", "1", Scripting.replaceVariables(ctx, testOne, "${size(find('TestOne', 'name', 'createTestOne1'))}"));

			Scripting.replaceVariables(ctx, testFour, "${create('TestOne', 'name', 'createTestOne1')}");
			noOfOnes++;
			assertEquals("Invalid number of TestOne's", ""+noOfOnes, Scripting.replaceVariables(ctx, testOne, "${size(find('TestOne'))}"));
			assertEquals("Invalid number of TestOne's", "2", Scripting.replaceVariables(ctx, testOne, "${size(find('TestOne', 'name', 'createTestOne1'))}"));


			// currently this must be executed on another node type
			Scripting.replaceVariables(ctx, testFour, "${create('TestOne', 'name', 'createTestOne2', 'aCreateString', 'newCreateString1')}");
			noOfOnes++;
			assertEquals("Invalid number of TestOne's", ""+noOfOnes, Scripting.replaceVariables(ctx, testOne, "${size(find('TestOne'))}"));
			assertEquals("Invalid number of TestOne's", "1", Scripting.replaceVariables(ctx, testOne, "${size(find('TestOne', 'name', 'createTestOne2'))}"));
			assertEquals("Invalid number of TestOne's", "0", Scripting.replaceVariables(ctx, testOne, "${size(find('TestOne', 'aCreateString', 'DOES_NOT_EXIST'))}"));
			assertEquals("Invalid number of TestOne's", "1", Scripting.replaceVariables(ctx, testOne, "${size(find('TestOne', 'aCreateString', 'newCreateString1'))}"));
			assertEquals("Invalid number of TestOne's", "0", Scripting.replaceVariables(ctx, testOne, "${size(find('TestOne', 'name', 'createTestOne2', 'aCreateString', 'NOT_newCreateString1'))}"));
			assertEquals("Invalid number of TestOne's", "1", Scripting.replaceVariables(ctx, testOne, "${size(find('TestOne', 'name', 'createTestOne2', 'aCreateString', 'newCreateString1'))}"));


			// currently this must be executed on another node type
			Scripting.replaceVariables(ctx, testFour, "${create('TestOne', 'name', 'createTestOne2', 'aCreateInt', '256')}");
			noOfOnes++;
			assertEquals("Invalid number of TestOne's", ""+noOfOnes, Scripting.replaceVariables(ctx, testOne, "${size(find('TestOne'))}"));
			assertEquals("Invalid number of TestOne's", "2", Scripting.replaceVariables(ctx, testOne, "${size(find('TestOne', 'name', 'createTestOne2'))}"));
			assertEquals("Invalid number of TestOne's", "1", Scripting.replaceVariables(ctx, testOne, "${size(find('TestOne', 'aCreateInt', '256'))}"));
			assertEquals("Invalid number of TestOne's", "0", Scripting.replaceVariables(ctx, testOne, "${size(find('TestOne', 'name', 'createTestOne2', 'aCreateInt', '255'))}"));
			assertEquals("Invalid number of TestOne's", "1", Scripting.replaceVariables(ctx, testOne, "${size(find('TestOne', 'name', 'createTestOne2', 'aCreateInt', '256'))}"));

			// test parser with different quote leves etc.
			assertEquals("Parser does not handle quotes correctly,", "test\"test", Scripting.replaceVariables(ctx, testOne, "${join(merge('test', 'test'), '\"')}"));
			assertEquals("Parser does not handle quotes correctly,", "test\'test", Scripting.replaceVariables(ctx, testOne, "${join(merge('test', 'test'), '\\'')}"));
			assertEquals("Parser does not handle quotes correctly,", "test\"test", Scripting.replaceVariables(ctx, testOne, "${join(merge(\"test\", \"test\"), \"\\\"\")}"));
			assertEquals("Parser does not handle quotes correctly,", "test\'test", Scripting.replaceVariables(ctx, testOne, "${join(merge(\"test\", \"test\"), \"\\'\")}"));

			// get_or_create()
			final String newUuid1 = Scripting.replaceVariables(ctx, null, "${get_or_create('TestOne', 'name', 'new-object-1')}");
			assertNotNull("Invalid get_or_create() result", newUuid1);
			assertEquals("Invalid get_or_create() result", newUuid1, Scripting.replaceVariables(ctx, null, "${get_or_create('TestOne', 'name', 'new-object-1')}"));
			assertEquals("Invalid get_or_create() result", newUuid1, Scripting.replaceVariables(ctx, null, "${get_or_create('TestOne', 'name', 'new-object-1')}"));
			assertEquals("Invalid get_or_create() result", newUuid1, Scripting.replaceVariables(ctx, null, "${get_or_create('TestOne', 'name', 'new-object-1')}"));

			// get_or_create()
			final String newUuid2 = Scripting.replaceVariables(ctx, null, "${{ Structr.getOrCreate('TestOne', { 'name': 'new-object-2', 'anInt': 13, 'aString': 'string' }) }}");
			assertNotNull("Invalid get_or_create() result", newUuid2);
			assertEquals("Invalid get_or_create() result", newUuid2, Scripting.replaceVariables(ctx, null, "${{ Structr.getOrCreate('TestOne', { 'name': 'new-object-2', 'anInt': 13, 'aString': 'string' }) }}"));
			assertEquals("Invalid get_or_create() result", newUuid2, Scripting.replaceVariables(ctx, null, "${{ Structr.getOrCreate('TestOne', { 'name': 'new-object-2', 'anInt': 13, 'aString': 'string' }) }}"));
			assertEquals("Invalid get_or_create() result", newUuid2, Scripting.replaceVariables(ctx, null, "${{ Structr.getOrCreate('TestOne', { 'name': 'new-object-2', 'anInt': 13, 'aString': 'string' }) }}"));

			// sleep
			final long t0 = System.currentTimeMillis();
			Scripting.replaceVariables(ctx, null, "${sleep(1000)}");
			final long dt = System.currentTimeMillis() - t0;
			assertTrue("Sleep() function did not wait for the specified amount of time: " + dt, dt >= 1000);

			// random_uuid
			final String randomUuid = Scripting.replaceVariables(ctx, null, "${random_uuid()}");
			assertTrue("Invalid UUID returned by random_uuid(): " + randomUuid, randomUuid.matches("[a-fA-F0-9]{32}"));

			tx.success();

		} catch (FrameworkException fex) {

			logger.warn("", fex);

			fail(fex.getMessage());
		}
	}

	@Test
	public void testSystemProperties () {
		try {

			final Principal user  = createTestNode(Principal.class);

			// create new node
			TestOne t1 = createTestNode(TestOne.class, user);

			final SecurityContext userContext     = SecurityContext.getInstance(user, AccessMode.Frontend);
			final App userApp                     = StructrApp.getInstance(userContext);

			try (final Tx tx = userApp.tx()) {

				final ActionContext userActionContext = new ActionContext(userContext, null);

				assertEquals("node should be of type TestOne", "TestOne", Scripting.replaceVariables(userActionContext, t1, "${(get(this, 'type'))}"));

				try {

					assertEquals("setting the type should fail", "TestTwo", Scripting.replaceVariables(userActionContext, t1, "${(set(this, 'type', 'TestThree'), get(this, 'type'))}"));
					fail("setting a system property should fail");

				} catch (FrameworkException fx) { }

				assertEquals("setting the type should work after setting it with unlock_system_properties_once", "TestFour", Scripting.replaceVariables(userActionContext, t1, "${(unlock_system_properties_once(this), set(this, 'type', 'TestFour'), get(this, 'type'))}"));

				tx.success();
			}

		} catch (FrameworkException ex) {

			logger.warn("", ex);
			fail("Unexpected exception");

		}
	}

	@Test
	public void testFunctionRollbackOnError () {

		final ActionContext ctx = new ActionContext(securityContext, null);

		/**
		 * first the old scripting style
		 */
		TestOne testNodeOldScripting = null;

		try (final Tx tx = app.tx()) {

			testNodeOldScripting = createTestNode(TestOne.class);
			testNodeOldScripting.setProperty(TestOne.aString, "InitialString");
			testNodeOldScripting.setProperty(TestOne.anInt, 42);

			tx.success();

		} catch (FrameworkException ex) {

			logger.warn("", ex);
			fail("Unexpected exception");

		}

		try (final Tx tx = app.tx()) {

			Scripting.replaceVariables(ctx, testNodeOldScripting, "${ ( set(this, 'aString', 'NewString'), set(this, 'anInt', 'NOT_AN_INTEGER') ) }");
			fail("StructrScript: setting anInt to 'NOT_AN_INTEGER' should cause an Exception");

			tx.success();

		} catch (FrameworkException expected) { }


		try {

			try (final Tx tx = app.tx()) {

				assertEquals("StructrScript: String should still have initial value!", "InitialString", Scripting.replaceVariables(ctx, testNodeOldScripting, "${(get(this, 'aString'))}"));

				tx.success();
			}

		} catch (FrameworkException ex) {

			logger.warn("", ex);
			fail("Unexpected exception");

		}


		/**
		 * then the JS-style scripting
		 */
		TestOne testNodeJavaScript = null;

		try (final Tx tx = app.tx()) {

			testNodeJavaScript = createTestNode(TestOne.class);
			testNodeJavaScript.setProperty(TestOne.aString, "InitialString");
			testNodeJavaScript.setProperty(TestOne.anInt, 42);

			tx.success();

		} catch (FrameworkException ex) {

			logger.warn("", ex);
			fail("Unexpected exception");

		}

		try (final Tx tx = app.tx()) {

			Scripting.replaceVariables(ctx, testNodeJavaScript, "${{ var t1 = Structr.get('this'); t1.aString = 'NewString'; t1.anInt = 'NOT_AN_INTEGER'; }}");
			fail("StructrScript: setting anInt to 'NOT_AN_INTEGER' should cause an Exception");

			tx.success();

		} catch (FrameworkException expected) { }


		try {

			try (final Tx tx = app.tx()) {

				assertEquals("JavaScript: String should still have initial value!", "InitialString", Scripting.replaceVariables(ctx, testNodeJavaScript, "${{ var t1 = Structr.get('this'); Structr.print(t1.aString); }}"));

				tx.success();
			}

		} catch (FrameworkException ex) {

			logger.warn("", ex);
			fail("Unexpected exception");

		}
	}

	@Test
	public void testPrivilegedFind () {

		final ActionContext ctx = new ActionContext(securityContext, null);

		TestOne testNode = null;
		String uuid ="";

		try (final Tx tx = app.tx()) {

			testNode = createTestNode(TestOne.class);
			testNode.setProperty(TestOne.aString, "InitialString");
			testNode.setProperty(TestOne.anInt, 42);
			uuid = testNode.getProperty(AbstractNode.id);

			tx.success();

		} catch (FrameworkException ex) {

			logger.warn("", ex);
			fail("Unexpected exception");

		}

		try (final Tx tx = app.tx()) {

			assertEquals("JavaScript: Trying to find entity with type,key,value!", "InitialString", Scripting.replaceVariables(ctx, testNode, "${{ var t1 = Structr.first(Structr.find_privileged('TestOne','anInt','42')); Structr.print(t1.aString); }}"));

			assertEquals("JavaScript: Trying to find entity with type,id!", "InitialString", Scripting.replaceVariables(ctx, testNode, "${{ var t1 = Structr.find_privileged('TestOne','"+uuid+"'); Structr.print(t1.aString); }}"));

			assertEquals("JavaScript: Trying to find entity with type,key,value,key,value!", "InitialString", Scripting.replaceVariables(ctx, testNode, "${{ var t1 = Structr.first(Structr.find_privileged('TestOne','anInt','42','aString','InitialString')); Structr.print(t1.aString); }}"));

			tx.success();

		} catch (FrameworkException ex) {

			logger.warn("", ex);
			fail("Unexpected exception");
		}
	}

	@Test
	public void testDateCopy() {

		final Date now                       = new Date();
		final Date futureDate                = new Date(now.getTime() + 600000);
		final SimpleDateFormat isoDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");

		try (final Tx tx = app.tx()) {

			final ActionContext ctx = new ActionContext(securityContext, null);

			// Copy dates with/without format in StructrScript
			TestOne testOne          = createTestNode(TestOne.class);
			TestThree testThree      = createTestNode(TestThree.class);

			testOne.setProperty(TestOne.aDate, now);
			Scripting.replaceVariables(ctx, testThree, "${set(this, 'aDateWithFormat', get(find('TestOne', '" + testOne.getUuid() + "'), 'aDate'))}");
			assertEquals("Copying a date (with default format) to a date (with custom format) failed [StructrScript]", isoDateFormat.format(testOne.getProperty(TestOne.aDate)), isoDateFormat.format(testThree.getProperty(TestThree.aDateWithFormat)));

			testThree.setProperty(TestThree.aDateWithFormat, futureDate);
			Scripting.replaceVariables(ctx, testOne, "${set(this, 'aDate', get(find('TestThree', '" + testThree.getUuid() + "'), 'aDateWithFormat'))}");
			assertEquals("Copying a date (with custom format) to a date (with default format) failed [StructrScript]", isoDateFormat.format(testOne.getProperty(TestOne.aDate)), isoDateFormat.format(testThree.getProperty(TestThree.aDateWithFormat)));


			// Perform the same tests in JavaScript
			testOne.setProperty(TestOne.aDate, null);
			testThree.setProperty(TestThree.aDateWithFormat, null);

			testOne.setProperty(TestOne.aDate, now);
			Scripting.replaceVariables(ctx, testThree, "${{ var testThree = Structr.this; var testOne = Structr.find('TestOne', '" + testOne.getUuid() + "');  testThree.aDateWithFormat = testOne.aDate; }}");
			assertEquals("Copying a date (with default format) to a date (with custom format) failed [JavaScript]", isoDateFormat.format(testOne.getProperty(TestOne.aDate)), isoDateFormat.format(testThree.getProperty(TestThree.aDateWithFormat)));

			testThree.setProperty(TestThree.aDateWithFormat, futureDate);
			Scripting.replaceVariables(ctx, testOne, "${{ var testOne = Structr.this; var testThree = Structr.find('TestThree', '" + testThree.getUuid() + "');  testOne.aDate = testThree.aDateWithFormat; }}");
			assertEquals("Copying a date (with custom format) to a date (with default format) failed [JavaScript]", isoDateFormat.format(testOne.getProperty(TestOne.aDate)), isoDateFormat.format(testThree.getProperty(TestThree.aDateWithFormat)));

			tx.success();

		} catch (FrameworkException fex) {

			logger.warn("", fex);

			fail(fex.getMessage());
		}

	}

	@Test
	public void testDateOutput() {

		final Date now                       = new Date();
		final SimpleDateFormat isoDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");

		try (final Tx tx = app.tx()) {

			final ActionContext ctx = new ActionContext(securityContext, null);

			// Copy dates with/without format in StructrScript
			TestOne testOne          = createTestNode(TestOne.class);

			testOne.setProperty(TestOne.aDate, now);

			final String expectedDateOutput = isoDateFormat.format(now);
			final String dateOutput1 = Scripting.replaceVariables(ctx, testOne, "${this.aDate}");
			final String dateOutput2 = Scripting.replaceVariables(ctx, testOne, "${print(this.aDate)}");
			final String dateOutput3 = Scripting.replaceVariables(ctx, testOne, "${{Structr.print(Structr.this.aDate)}}");

			assertEquals("${this.aDate} should yield ISO 8601 date format", expectedDateOutput, dateOutput1);
			assertEquals("${print(this.aDate)} should yield ISO 8601 date format", expectedDateOutput, dateOutput2);
			assertEquals("${Structr.print(Structr.this.aDate)} should yield ISO 8601 date format", expectedDateOutput, dateOutput3);

			tx.success();

		} catch (FrameworkException fex) {

			logger.warn("", fex);
			fail(fex.getMessage());
		}
	}

	@Test
	public void testGeoCoding() {

		try (final Tx tx = app.tx()) {

			final ActionContext ctx = new ActionContext(securityContext, null);

			final String locationId = Scripting.replaceVariables(ctx, null, "${create('Location')}");

			final GeoCodingResult result = GeoHelper.geocode("", null, null, "Darmstadt", null, "");

			if (result != null) {
				// If geocoding itself fails, the test can not work => ignore

				Double lat = result.getLatitude();
				Double lon = result.getLongitude();

				Scripting.replaceVariables(ctx, null, "${set(find('Location', '" + locationId + "'), geocode('Darmstadt', '', ''))}");

				assertEquals("Latitude should be identical", lat.toString(), Scripting.replaceVariables(ctx, null, "${get(find('Location', '" + locationId + "'), 'latitude')}"));
				assertEquals("Longitude should be identical", lon.toString(), Scripting.replaceVariables(ctx, null, "${get(find('Location', '" + locationId + "'), 'longitude')}"));

			}

			tx.success();

		} catch (FrameworkException fex) {

			logger.warn("", fex);

			fail(fex.getMessage());
		}
	}

	@Test
	public void testNonPrimitiveReturnValue() {

		try (final Tx tx = app.tx()) {

			app.create(SchemaMethod.class,
				new NodeAttribute<>(SchemaMethod.name,   "testReturnValueOfGlobalSchemaMethod"),
				new NodeAttribute<>(SchemaMethod.source, "{ return { name: 'test', value: 123, me: Structr.me }; }")
			);

			app.create(SchemaProperty.class,
				new NodeAttribute<>(SchemaProperty.schemaNode,   app.create(SchemaNode.class, new NodeAttribute<>(SchemaNode.name, "Test"))),
				new NodeAttribute<>(SchemaProperty.name,         "returnTest"),
				new NodeAttribute<>(SchemaProperty.propertyType, "Function"),
				new NodeAttribute<>(SchemaProperty.readFunction, "{ return { name: 'test', value: 123, me: Structr.this }; }")
			);

			tx.success();

		} catch (FrameworkException fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}

		try (final Tx tx = app.tx()) {

			final ActionContext ctx = new ActionContext(securityContext, null);
			final Map map           = (Map)Scripting.evaluate(ctx, null, "${{return Structr.call('testReturnValueOfGlobalSchemaMethod')}}", "test");

			final Object name       = map.get("name");
			final Object value      = map.get("value");
			final Object me         = map.get("me");

			assertEquals("Invalid non-primitive scripting return value result, name should be of type string.",  "test", name);
			assertEquals("Invalid non-primitive scripting return value result, value should be of type integer", Integer.valueOf(123), value);
			assertTrue("Invalid non-primitive scripting return value result,   me should be of type SuperUser",    me instanceof SuperUser);

			tx.success();

		} catch (UnlicensedScriptException |FrameworkException fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}

		try (final Tx tx = app.tx()) {

			final Class type        = StructrApp.getConfiguration().getNodeEntityClass("Test");
			final NodeInterface obj = app.create(type, "test");
			final Map map           = (Map)obj.getProperty(StructrApp.key(type, "returnTest"));
			final Object name       = map.get("name");
			final Object value      = map.get("value");
			final Object me         = map.get("me");

			assertEquals("Invalid non-primitive scripting return value result, name should be of type string.",  "test", name);
			assertEquals("Invalid non-primitive scripting return value result, value should be of type integer", Integer.valueOf(123), value);
			assertEquals("Invalid non-primitive scripting return value result, me should be the entity",         obj, me);

			tx.success();

		} catch (FrameworkException fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}
	}

	@Test
	public void testJavascriptBatchFunction() {

		try (final Tx tx = app.tx()) {

			createTestNodes(TestOne.class, 1000);

			tx.success();

		} catch (FrameworkException fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}

		try (final Tx tx = app.tx()) {

			final ActionContext ctx  = new ActionContext(securityContext, null);
			final StringBuilder func = new StringBuilder();

			func.append("${{\n");
			func.append("    Structr.batch(function() {\n");
			func.append("        var toDelete = Structr.find('TestOne').slice(0, 100);\n");
			func.append("        if (toDelete && toDelete.length) {\n");
			func.append("            Structr.log('Deleting ' + toDelete.length + ' nodes..');\n");
			func.append("            Structr.delete(toDelete);\n");
			func.append("            return true;\n");
			func.append("        } else {\n");
			func.append("            Structr.log('Finished');\n");
			func.append("            return false;\n");
			func.append("        }\n");
			func.append("    });\n");
			func.append("}}");


			final Object result = Scripting.evaluate(ctx, null, func.toString(), "test");

			tx.success();

		} catch (FrameworkException fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}
	}

	@Test
	public void testStructrScriptBatchFunction() {

		try (final Tx tx = app.tx()) {

			createTestNodes(TestOne.class, 1000);

			tx.success();

		} catch (FrameworkException fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}

		try (final Tx tx = app.tx()) {

			final ActionContext ctx  = new ActionContext(securityContext, null);
			Scripting.evaluate(ctx, null, "${batch(each(find('TestOne'), set(data, 'name', 'test')), 100)}", "test");

			tx.success();

		} catch (FrameworkException fex) {

			fex.printStackTrace();
			fail("Unexpected exception: " + fex.getMessage());
		}

		try (final Tx tx = app.tx()) {

			final ActionContext ctx  = new ActionContext(securityContext, null);
			Scripting.evaluate(ctx, null, "${batch(delete(find('TestOne')), 100)}", "test");

			tx.success();

		} catch (FrameworkException fex) {

			fex.printStackTrace();
			fail("Unexpected exception: " + fex.getMessage());
		}
	}

	@Test
	public void testBulkDeleteWithoutBatching() {

		try (final Tx tx = app.tx()) {

			createTestNodes(TestOne.class, 1000);

			tx.success();

		} catch (FrameworkException fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}

		try (final Tx tx = app.tx()) {

			final ActionContext ctx  = new ActionContext(securityContext, null);
			Scripting.evaluate(ctx, null, "${delete(find('TestOne'))}", "test");

			tx.success();

		} catch (FrameworkException fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}
	}

	@Test
	public void testQuotesInScriptComments() {

		final String script =  "${{\n" +
				"\n" +
				"	// test'\n" +
				"	Structr.print('test');\n" +
				"\n" +
				"}}";

		try (final Tx tx = app.tx()) {

			final ActionContext ctx  = new ActionContext(securityContext, null);

			assertEquals("Single quotes in JavaScript comments should not prevent script evaluation.", "test", Scripting.evaluate(ctx, null, script, "test"));
			assertEquals("Single quotes in JavaScript comments should not prevent script evaluation.", "test", Scripting.replaceVariables(ctx, null, script));

			tx.success();

		} catch (FrameworkException fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}
	}

	@Test
	public void testNewlineAtEndOfScriptingCode() {

		final String script =  "${{ return 'test'; }}\n";

		try (final Tx tx = app.tx()) {

			final ActionContext ctx  = new ActionContext(securityContext, null);

			assertEquals("Newline at end of JavaScript scripting should not prevent script evaluation.", "test", Scripting.evaluate(ctx, null, script, "test"));
			assertEquals("Newline at end of JavaScript scripting should not prevent script evaluation.", "test\n", Scripting.replaceVariables(ctx, null, script));

			tx.success();

		} catch (FrameworkException fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}
	}

	@Test
	public void testIncludeJs() {

		final String script =  "${{ Structr.includeJs('test'); }}\n";

		try (final Tx tx = app.tx()) {

			final ActionContext ctx  = new ActionContext(securityContext, null);

			// just run without an error, that's enough for this test
			Scripting.evaluate(ctx, null, script, "test");

			tx.success();

		} catch (FrameworkException fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}
	}

	@Test
	public void testAfterCreateMethod() {

		final String expectedErrorToken = "create_not_allowed";

		// test setup
		try (final Tx tx = app.tx()) {

			final JsonSchema schema  = StructrSchema.createFromDatabase(app);
			final JsonType dummyType = schema.addType("DummyType");
			final JsonType newType   = schema.addType("MyDynamicType");

			newType.addMethod("onCreation",    "is(eq(this.name, 'forbiddenName'), error('myError', '" + expectedErrorToken + "', 'creating this object is not allowed'))", "");
			newType.addMethod("afterCreation", "create('DummyType', 'name', 'this should not be possible!')", "");

			StructrSchema.replaceDatabaseSchema(app, schema);

			tx.success();

		} catch (Throwable t) {
			logger.error("", t);
			fail("Unexpected exception during test setup.");
		}


		final Class myDynamicType = StructrApp.getConfiguration().getNodeEntityClass("MyDynamicType");
		final Class dummyType     = StructrApp.getConfiguration().getNodeEntityClass("DummyType");

		// test that afterCreate is called
		try (final Tx tx = app.tx()) {

			app.create(myDynamicType, new NodeAttribute<>(AbstractNode.name, "allowedName"));

			final Integer myDynamicTypeCount = app.nodeQuery(myDynamicType).getAsList().size();
			final Integer dummyTypeCount     = app.nodeQuery(dummyType).getAsList().size();

			final boolean correct = myDynamicTypeCount == 1 && dummyTypeCount == 0;

			assertTrue("Before tx.success() there should be exactly 1 node of type MyDynamicNode and 0 of type DummyType", correct);

			tx.success();

		} catch (Throwable t) {
			t.printStackTrace();
			fail("Unexpected exception.");
		}

		try (final Tx tx = app.tx()) {

			final Integer myDynamicTypeCount = app.nodeQuery(myDynamicType).getAsList().size();
			final Integer dummyTypeCount     = app.nodeQuery(dummyType).getAsList().size();

			final boolean correct = myDynamicTypeCount == 1 && dummyTypeCount == 1;

			assertTrue("After tx.success() there should be exactly 1 node of type MyDynamicNode and 1 of type DummyType", correct);

			tx.success();

		} catch (Throwable t) {
			t.printStackTrace();
			fail("Unexpected exception.");
		}

		// delete nodes
		try (final Tx tx = app.tx()) {

			final ActionContext ctx = new ActionContext(securityContext, null);
			Scripting.replaceVariables(ctx, null, "${delete(find('MyDynamicType'))}");
			Scripting.replaceVariables(ctx, null, "${delete(find('DummyType'))}");

			tx.success();

		} catch (Throwable t) {
			t.printStackTrace();
			fail("Unexpected exception.");
		}

		// test that afterCreate is not called if there was an error in onCreate
		try (final Tx tx = app.tx()) {

			app.create(myDynamicType, new NodeAttribute<>(AbstractNode.name, "forbiddenName"));

			final Integer myDynamicTypeCount = app.nodeQuery(myDynamicType).getAsList().size();
			final Integer dummyTypeCount     = app.nodeQuery(dummyType).getAsList().size();

			final boolean correct = myDynamicTypeCount == 1 && dummyTypeCount == 0;

			assertTrue("Before tx.success() there should be exactly 1 node of type MyDynamicNode and 0 of type DummyType", correct);

			tx.success();

		} catch (FrameworkException fex) {

			final boolean isExpectedErrorToken = fex.getErrorBuffer().getErrorTokens().get(0).getToken().equals(expectedErrorToken);

			assertTrue("Encountered unexpected error!", isExpectedErrorToken);

		} catch (Throwable t) {
			t.printStackTrace();
			fail("Unexpected exception.");
		}

		try (final Tx tx = app.tx()) {

			final Integer myDynamicTypeCount = app.nodeQuery(myDynamicType).getAsList().size();
			final Integer dummyTypeCount     = app.nodeQuery(dummyType).getAsList().size();

			final boolean correct = myDynamicTypeCount == 0 && dummyTypeCount == 0;

			assertTrue("After tx.success() there should be exactly 0 node of type MyDynamicNode and 0 of type DummyType (because we used a forbidden name)", correct);

			tx.success();

		} catch (Throwable t) {
			t.printStackTrace();
			fail("Unexpected exception.");
		}
	}

	@Test
	public void testFindNewlyCreatedObjectByOwner () {

		this.cleanDatabaseAndSchema();

		String userObjects        = "[";

		try (final Tx tx = app.tx()) {

			createTestNode(Principal.class, "testuser");

			tx.success();

		} catch (FrameworkException fex) {
			logger.warn("", fex);
			fail("Unexpected exception.");
		}

		// Create first object
		try (final Tx tx = app.tx()) {

			final Principal testUser = StructrApp.getInstance().nodeQuery(Principal.class).and(AbstractNode.name, "testuser").getFirst();
			final ActionContext ctx = new ActionContext(SecurityContext.getInstance(testUser, AccessMode.Frontend));

			userObjects += Scripting.replaceVariables(ctx, null, "${ create('TestOne') }");

			tx.success();

		} catch (FrameworkException fex) {

			logger.warn("", fex);
			fail("Unexpected exception.");
		}

		// find() it - this works because the cache is empty
		try (final Tx tx = app.tx()) {

			final Principal testUser = StructrApp.getInstance().nodeQuery(Principal.class).and(AbstractNode.name, "testuser").getFirst();
			final ActionContext ctx = new ActionContext(SecurityContext.getInstance(testUser, AccessMode.Frontend));

			assertEquals("User should be able to find newly created object!", userObjects + "]", Scripting.replaceVariables(ctx, null, "${ find('TestOne', 'owner', me.id) }"));

			tx.success();

		} catch (FrameworkException fex) {

			logger.warn("", fex);
			fail("Unexpected exception.");
		}

		// create second object
		try (final Tx tx = app.tx()) {

			final Principal testUser = StructrApp.getInstance().nodeQuery(Principal.class).and(AbstractNode.name, "testuser").getFirst();
			final ActionContext ctx = new ActionContext(SecurityContext.getInstance(testUser, AccessMode.Frontend));

			userObjects += ", " + Scripting.replaceVariables(ctx, null, "${ create('TestOne') }");

			tx.success();

		} catch (FrameworkException fex) {

			logger.warn("", fex);
			fail("Unexpected exception.");
		}

		// find() it - this does not work because there is a cache entry already and it was not invalidated after creating the last relationship to it
		try (final Tx tx = app.tx()) {

			final Principal testUser = StructrApp.getInstance().nodeQuery(Principal.class).and(AbstractNode.name, "testuser").getFirst();
			final ActionContext ctx = new ActionContext(SecurityContext.getInstance(testUser, AccessMode.Frontend));

			assertEquals("User should be able to find newly created object!", userObjects + "]", Scripting.replaceVariables(ctx, null, "${ find('TestOne', 'owner', me.id, sort('createdDate', 'desc')) }"));

			tx.success();

		} catch (FrameworkException fex) {

			logger.warn("", fex);
			fail("Unexpected exception.");
		}
	}

	@Test
	public void testConversionError() {

		// setup
		try (final Tx tx = app.tx()) {

			final JsonSchema schema = StructrSchema.createEmptySchema();

			schema.addType("Test").addBooleanProperty("boolTest").setIndexed(true);

			StructrSchema.extendDatabaseSchema(app, schema);

			tx.success();

		} catch (Throwable t) {

			t.printStackTrace();
			fail("Unexpected exception.");
		}

		final String script =  "${{ var test = Structr.create('Test'); test.boolTest = true; }}\n";

		try (final Tx tx = app.tx()) {

			final ActionContext ctx  = new ActionContext(securityContext, null);

			// just run without an error, that's enough for this test
			Scripting.evaluate(ctx, null, script, "test");
			tx.success();

		} catch (FrameworkException fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}
	}

	@Test
	public void testModifications() {

		// setup
		try (final Tx tx = app.tx()) {

			final JsonSchema schema = StructrSchema.createFromDatabase(app);

			final JsonObjectType customer = schema.addType("Customer");
			final JsonObjectType project  = schema.addType("Project");
			final JsonObjectType task     = schema.addType("Task");

			customer.addStringProperty("log");
			project.addStringProperty("log");
			task.addStringProperty("log");

			// create relation
			final JsonReferenceType rel = project.relate(task, "has", Cardinality.OneToMany, "project", "tasks");
			rel.setName("ProjectTasks");

			customer.relate(project, "project", Cardinality.OneToOne, "customer", "project");

			customer.addMethod("onModification", "{ var mods = Structr.retrieve('modifications'); Structr.this.log = JSON.stringify(mods); }", "");
			project.addMethod("onModification", "{ var mods = Structr.retrieve('modifications'); Structr.this.log = JSON.stringify(mods); }", "");
			task.addMethod("onModification", "{ var mods = Structr.retrieve('modifications'); Structr.this.log = JSON.stringify(mods); }", "");

			StructrSchema.extendDatabaseSchema(app, schema);

			tx.success();

		} catch (Throwable t) {

			t.printStackTrace();
			fail("Unexpected exception.");
		}

		final Class customer          = StructrApp.getConfiguration().getNodeEntityClass("Customer");
		final Class project           = StructrApp.getConfiguration().getNodeEntityClass("Project");
		final Class task              = StructrApp.getConfiguration().getNodeEntityClass("Task");
		final PropertyKey tasksKey    = StructrApp.getConfiguration().getPropertyKeyForJSONName(project, "tasks");
		final PropertyKey customerKey = StructrApp.getConfiguration().getPropertyKeyForJSONName(project, "customer");

		try (final Tx tx = app.tx()) {

			app.create(customer, "Testcustomer");
			app.create(project, "Testproject");
			app.create(task, new NodeAttribute<>(AbstractNode.name, "task1"));
			app.create(task, new NodeAttribute<>(AbstractNode.name, "task2"));

			tx.success();

		} catch (FrameworkException fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}

		try (final Tx tx = app.tx()) {

			final Principal tester    = app.create(Principal.class, "modifications-tester");
			final GraphObject c       = app.nodeQuery(customer).getFirst();
			final GraphObject p       = app.nodeQuery(project).getFirst();
			final List<GraphObject> t = app.nodeQuery(task).getAsList();

			p.setProperty(AbstractNode.name, "newName");
			p.setProperty(tasksKey, t);
			p.setProperty(customerKey, c);

			((AccessControllable)c).grant(Permission.write, tester);
			((AccessControllable)p).grant(Permission.write, tester);

			tx.success();

		} catch (FrameworkException fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}

		// test modifications
		try (final Tx tx = app.tx()) {

			final Principal tester = app.nodeQuery(Principal.class).andName("modifications-tester").getFirst();
			final GraphObject c = app.nodeQuery(customer).getFirst();
			final GraphObject p = app.nodeQuery(project).getFirst();
			final GraphObject t = app.nodeQuery(task).getFirst();

			final Map<String, Object> customerModifications = getLoggedModifications(c);
			final Map<String, Object> projectModifications  = getLoggedModifications(p);
			final Map<String, Object> taskModifications     = getLoggedModifications(t);

			assertMapPathValueIs(customerModifications, "added.project",   p.getUuid());
			assertMapPathValueIs(customerModifications, "removed",         new LinkedHashMap<>());
			assertMapPathValueIs(customerModifications, "added.grantees",  Arrays.asList(tester.getUuid()));

			assertMapPathValueIs(projectModifications, "before.name",     "Testproject");
			assertMapPathValueIs(projectModifications, "after.name",     "newName");
			assertMapPathValueIs(projectModifications, "added.customer", c.getUuid());
			assertMapPathValueIs(projectModifications, "removed",        new LinkedHashMap<>());

			final List<GraphObject> tasks = app.nodeQuery(task).getAsList();
			final List<String> taskIds = new LinkedList();
			for (GraphObject oneTask : tasks) {
				taskIds.add(oneTask.getUuid());
			}
			assertMapPathValueIs(projectModifications, "added.tasks",    taskIds);
			assertMapPathValueIs(projectModifications, "added.grantees",    Arrays.asList(tester.getUuid()));


			assertMapPathValueIs(taskModifications, "added.project",   p.getUuid());
			assertMapPathValueIs(taskModifications, "removed",         new LinkedHashMap<>());


			tx.success();

		} catch (FrameworkException fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}

		try (final Tx tx = app.tx()) {

			final GraphObject p = app.nodeQuery(project).getFirst();

			p.setProperty(customerKey, null);
			p.setProperty(tasksKey, Arrays.asList(app.nodeQuery(task).getFirst()));

			tx.success();

		} catch (FrameworkException fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}

		// test modifications
		try (final Tx tx = app.tx()) {

			final GraphObject c = app.nodeQuery(customer).getFirst();
			final GraphObject p = app.nodeQuery(project).getFirst();
			final GraphObject t = app.nodeQuery(task).getFirst();

			final Map<String, Object> customerModifications = getLoggedModifications(c);
			final Map<String, Object> projectModifications  = getLoggedModifications(p);
			final Map<String, Object> taskModifications     = getLoggedModifications(t);

			assertMapPathValueIs(customerModifications, "added",           new LinkedHashMap<>());
			assertMapPathValueIs(customerModifications, "removed.project", p.getUuid());

			assertMapPathValueIs(projectModifications, "removed.customer", c.getUuid());
			assertMapPathValueIs(projectModifications, "added",            new LinkedHashMap<>());

			assertMapPathValueIs(taskModifications, "added.project",   p.getUuid());
			assertMapPathValueIs(taskModifications, "removed",         new LinkedHashMap<>());

			tx.success();

		} catch (FrameworkException fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}
	}

	@Test
	public void testScriptCodeWithNewlines() {

		// setup
		try (final Tx tx = app.tx()) {

			final JsonSchema schema = StructrSchema.createFromDatabase(app);

			createTestType(schema, "Test1", " 	 set(this, 'c', 'passed')  ",   "   \n 	set(this, 's', 'passed')\n	\n    \n  ", "StructrScript with newlines");
			createTestType(schema, "Test2", "set(this, 'c', 'passed')",             "set(this, 's', 'passed')",                  "StructrScript without newlines");
			createTestType(schema, "Test3", "   { Structr.this.c = 'passed'; }   ", " 	 \n	  { Structr.this.s = 'passed'; }\n\n	\n    \n "  ,    "JavaScript with newlines");
			createTestType(schema, "Test4", "{ Structr.this.c = 'passed'; }",       "{ Structr.this.s = 'passed'; }",            "JavaScript without newlines");

			StructrSchema.extendDatabaseSchema(app, schema);

			tx.success();

		} catch (Throwable fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}

		final Class type1 = StructrApp.getConfiguration().getNodeEntityClass("Test1");
		final Class type2 = StructrApp.getConfiguration().getNodeEntityClass("Test2");
		final Class type3 = StructrApp.getConfiguration().getNodeEntityClass("Test3");
		final Class type4 = StructrApp.getConfiguration().getNodeEntityClass("Test4");

		// test onCreate
		try (final Tx tx = app.tx()) {

			app.create(type1, "test1");
			app.create(type2, "test2");
			app.create(type3, "test3");
			app.create(type4, "test4");

			tx.success();

		} catch (Throwable fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}

		// test onCreate
		try (final Tx tx = app.tx()) {

			final GraphObject test1 = app.nodeQuery(type1).getFirst();
                        final GraphObject test2 = app.nodeQuery(type2).getFirst();
			final GraphObject test3 = app.nodeQuery(type3).getFirst();
                        final GraphObject test4 = app.nodeQuery(type4).getFirst();

			assertEquals("Newlines in script code not trimmed correctly", "passed", (String)test1.getProperty("c"));
			assertEquals("Newlines in script code not trimmed correctly", "passed", (String)test2.getProperty("c"));
			assertEquals("Newlines in script code not trimmed correctly", "passed", (String)test3.getProperty("c"));
			assertEquals("Newlines in script code not trimmed correctly", "passed", (String)test4.getProperty("c"));

			assertNull("onSave method called for creation", test1.getProperty("s"));
			assertNull("onSave method called for creation", test2.getProperty("s"));
			assertNull("onSave method called for creation", test3.getProperty("s"));
			assertNull("onSave method called for creation", test4.getProperty("s"));

			test1.setProperty(AbstractNode.name, "modified");
			test2.setProperty(AbstractNode.name, "modified");
			test3.setProperty(AbstractNode.name, "modified");
			test4.setProperty(AbstractNode.name, "modified");

			tx.success();

		} catch (FrameworkException fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}

		// test onSave
		try (final Tx tx = app.tx()) {

			final GraphObject test1 = app.nodeQuery(type1).getFirst();
                        final GraphObject test2 = app.nodeQuery(type2).getFirst();
			final GraphObject test3 = app.nodeQuery(type3).getFirst();
                        final GraphObject test4 = app.nodeQuery(type4).getFirst();

			assertEquals("Newlines in script code not trimmed correctly", "passed", (String)test1.getProperty("s"));
			assertEquals("Newlines in script code not trimmed correctly", "passed", (String)test2.getProperty("s"));
			assertEquals("Newlines in script code not trimmed correctly", "passed", (String)test3.getProperty("s"));
			assertEquals("Newlines in script code not trimmed correctly", "passed", (String)test4.getProperty("s"));

			tx.success();

		} catch (FrameworkException fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}

		// test actions
		try (final Tx tx = app.tx()) {

			assertEquals("Newlines in script code not trimmed correctly", "passed", Actions.execute(securityContext, null, "	 ${ 'passed' }	 ",    "StructrScript with whitespace"));
			assertEquals("Newlines in script code not trimmed correctly", "passed", Actions.execute(securityContext, null, "${ 'passed' }",                "StructrScript without whitespace"));
			assertEquals("Newlines in script code not trimmed correctly", "passed", Actions.execute(securityContext, null, "  ${{ return 'passed'; }}   ", "JavaScript with whitespace"));
			assertEquals("Newlines in script code not trimmed correctly", "passed", Actions.execute(securityContext, null, "${{ return 'passed'; }}",      "JavaScript without whitespace"));

			tx.success();

		} catch (FrameworkException fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}
	}

	@Test
	public void testScriptCodeWithWhitespace() {

		// setup
		try (final Tx tx = app.tx()) {

			final JsonSchema schema = StructrSchema.createFromDatabase(app);

			createTestType(schema, "Test1", " 	 set(this, 'c', 'passed')  ",   "    	set(this, 's', 'passed')	", "StructrScript with whitespace");
			createTestType(schema, "Test2", "set(this, 'c', 'passed')",             "set(this, 's', 'passed')",                "StructrScript without whitespace");
			createTestType(schema, "Test3", "   { Structr.this.c = 'passed'; }   ", "   { Structr.this.s = 'passed'; }   ",    "JavaScript with whitespace");
			createTestType(schema, "Test4", "{ Structr.this.c = 'passed'; }",       "{ Structr.this.s = 'passed'; }",          "JavaScript without whitespace");

			StructrSchema.extendDatabaseSchema(app, schema);

			tx.success();

		} catch (Throwable fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}

		final Class type1 = StructrApp.getConfiguration().getNodeEntityClass("Test1");
		final Class type2 = StructrApp.getConfiguration().getNodeEntityClass("Test2");
		final Class type3 = StructrApp.getConfiguration().getNodeEntityClass("Test3");
		final Class type4 = StructrApp.getConfiguration().getNodeEntityClass("Test4");

		// test onCreate
		try (final Tx tx = app.tx()) {

			app.create(type1, "test1");
			app.create(type2, "test2");
			app.create(type3, "test3");
			app.create(type4, "test4");

			tx.success();

		} catch (Throwable fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}

		// test onCreate
		try (final Tx tx = app.tx()) {

			final GraphObject test1 = app.nodeQuery(type1).getFirst();
                        final GraphObject test2 = app.nodeQuery(type2).getFirst();
			final GraphObject test3 = app.nodeQuery(type3).getFirst();
                        final GraphObject test4 = app.nodeQuery(type4).getFirst();

			assertEquals("Whitespace in script code not trimmed correctly", "passed", (String)test1.getProperty("c"));
			assertEquals("Whitespace in script code not trimmed correctly", "passed", (String)test2.getProperty("c"));
			assertEquals("Whitespace in script code not trimmed correctly", "passed", (String)test3.getProperty("c"));
			assertEquals("Whitespace in script code not trimmed correctly", "passed", (String)test4.getProperty("c"));

			assertNull("onSave method called for creation", test1.getProperty("s"));
			assertNull("onSave method called for creation", test2.getProperty("s"));
			assertNull("onSave method called for creation", test3.getProperty("s"));
			assertNull("onSave method called for creation", test4.getProperty("s"));

			test1.setProperty(AbstractNode.name, "modified");
			test2.setProperty(AbstractNode.name, "modified");
			test3.setProperty(AbstractNode.name, "modified");
			test4.setProperty(AbstractNode.name, "modified");

			tx.success();

		} catch (FrameworkException fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}

		// test onSave
		try (final Tx tx = app.tx()) {

			final GraphObject test1 = app.nodeQuery(type1).getFirst();
                        final GraphObject test2 = app.nodeQuery(type2).getFirst();
			final GraphObject test3 = app.nodeQuery(type3).getFirst();
                        final GraphObject test4 = app.nodeQuery(type4).getFirst();

			assertEquals("Whitespace in script code not trimmed correctly", "passed", (String)test1.getProperty("s"));
			assertEquals("Whitespace in script code not trimmed correctly", "passed", (String)test2.getProperty("s"));
			assertEquals("Whitespace in script code not trimmed correctly", "passed", (String)test3.getProperty("s"));
			assertEquals("Whitespace in script code not trimmed correctly", "passed", (String)test4.getProperty("s"));

			tx.success();

		} catch (FrameworkException fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}

		// test actions
		try (final Tx tx = app.tx()) {

			assertEquals("Whitespace in script code not trimmed correctly", "passed", Actions.execute(securityContext, null, "	 ${ 'passed' }	 ",    "StructrScript with whitespace"));
			assertEquals("Whitespace in script code not trimmed correctly", "passed", Actions.execute(securityContext, null, "${ 'passed' }",                "StructrScript without whitespace"));
			assertEquals("Whitespace in script code not trimmed correctly", "passed", Actions.execute(securityContext, null, "  ${{ return 'passed'; }}   ", "JavaScript with whitespace"));
			assertEquals("Whitespace in script code not trimmed correctly", "passed", Actions.execute(securityContext, null, "${{ return 'passed'; }}",      "JavaScript without whitespace"));

			tx.success();

		} catch (FrameworkException fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}
	}

	@Test
	public void testContextStoreTransferToAndFromDoPrivileged() {

		final String storeKey        = "my-store-key";
		final String userValue       = "USER-value";
		final String privilegedValue = "PRIVILEGED-value";

		try (final Tx tx = app.tx()) {

			final ActionContext ctx  = new ActionContext(securityContext, null);
			final StringBuilder func = new StringBuilder();

			func.append("${{\n");
			func.append("	Structr.store('").append(storeKey).append("', '").append(userValue).append("');\n");
			func.append("\n");
			func.append("	Structr.doPrivileged(function () {\n");
			func.append("		Structr.print(Structr.retrieve('").append(storeKey).append("'));\n");
			func.append("	});\n");
			func.append("}}");

			final String retrievedValue = Scripting.replaceVariables(ctx, null, func.toString());

			assertEquals("A value (that was stored outside doPrivilged) should be available in the privileged context", userValue, retrievedValue);


			final StringBuilder func2 = new StringBuilder();

			func2.append("${{\n");
			func2.append("	Structr.store('").append(storeKey).append("', '").append(userValue).append("');\n");
			func2.append("\n");
			func2.append("	Structr.doPrivileged(function () {\n");
			func2.append("		Structr.store('").append(storeKey).append("', '").append(privilegedValue).append("');\n");
			func2.append("	});\n");
			func2.append("\n");
			func2.append("	Structr.print(Structr.retrieve('").append(storeKey).append("'));\n");
			func2.append("}}");

			final String retrievedValue2 = Scripting.replaceVariables(ctx, null, func2.toString());

			assertEquals("A value (that was stored in doPrivilged) should be available in the surrounding context", privilegedValue, retrievedValue2);

			tx.success();

		} catch (FrameworkException fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}

	}

	@Test
	public void testCryptoFunctions() {

		this.cleanDatabaseAndSchema();

		final ActionContext ctx = new ActionContext(securityContext);

		// test failures
		try {

			Scripting.replaceVariables(ctx, null, "${encrypt('plaintext')}");
			fail("Encrypt function should throw an exception when no initial encryption key is set.");

		} catch (FrameworkException fex) {
			assertEquals("Invalid error code", 422, fex.getStatus());
		}

		// test failures
		try {

			assertEquals("Decrypt function should return null when no initial encryption key is set.", "", Scripting.replaceVariables(ctx, null, "${decrypt('plaintext')}"));

		} catch (FrameworkException fex) {

			fex.printStackTrace();
			fail("Unexpected exception: " + fex.getMessage());
		}

		// test functions without global encryption key
		try {

			assertEquals("Invalid encryption result", "ZuAM6SQ7GTc2KW55M/apUA==", Scripting.replaceVariables(ctx, null, "${encrypt('plaintext', 'structr')}"));
			assertEquals("Invalid encryption result", "b4bn2+w7yaEve3YGtn4IGA==", Scripting.replaceVariables(ctx, null, "${encrypt('plaintext', 'password')}"));

			assertEquals("Invalid decryption result", "ZuAM6SQ7GTc2KW55M/apUA==", Scripting.replaceVariables(ctx, null, "${encrypt('plaintext', 'structr')}"));
			assertEquals("Invalid decryption result", "b4bn2+w7yaEve3YGtn4IGA==", Scripting.replaceVariables(ctx, null, "${encrypt('plaintext', 'password')}"));

		} catch (FrameworkException fex) {
			assertEquals("Invalid error code", 422, fex.getStatus());
		}

		// test functions with global encryption key
		try {

			assertEquals("Invalid response when setting encryption key via scriptin", "", Scripting.replaceVariables(ctx, null, "${set_encryption_key('structr')}"));

			assertEquals("Invalid encryption result", "ZuAM6SQ7GTc2KW55M/apUA==", Scripting.replaceVariables(ctx, null, "${encrypt('plaintext')}"));
			assertEquals("Invalid encryption result", "ZuAM6SQ7GTc2KW55M/apUA==", Scripting.replaceVariables(ctx, null, "${encrypt('plaintext', 'structr')}"));
			assertEquals("Invalid encryption result", "b4bn2+w7yaEve3YGtn4IGA==", Scripting.replaceVariables(ctx, null, "${encrypt('plaintext', 'password')}"));

			assertEquals("Invalid encryption result", "plaintext", Scripting.replaceVariables(ctx, null, "${decrypt('ZuAM6SQ7GTc2KW55M/apUA==')}"));
			assertEquals("Invalid encryption result", "plaintext", Scripting.replaceVariables(ctx, null, "${decrypt('ZuAM6SQ7GTc2KW55M/apUA==', 'structr')}"));
			assertEquals("Invalid encryption result", "plaintext", Scripting.replaceVariables(ctx, null, "${decrypt('b4bn2+w7yaEve3YGtn4IGA==', 'password')}"));

		} catch (FrameworkException fex) {
			assertEquals("Invalid error code", 422, fex.getStatus());
		}

		// test resetting encryption key using the built-in function
		try {

			assertEquals("Invalid response when setting encryption key via scriptin", "", Scripting.replaceVariables(ctx, null, "${set_encryption_key(null)}"));

		} catch (FrameworkException fex) {
			assertEquals("Invalid error code", 422, fex.getStatus());
		}

		// test failures
		try {

			Scripting.replaceVariables(ctx, null, "${encrypt('plaintext')}");
			fail("Encrypt function should throw an exception when no initial encryption key is set.");

		} catch (FrameworkException fex) {
			assertEquals("Invalid error code", 422, fex.getStatus());
		}
	}

	@Test
	public void testFunctionPropertyTypeHint() {

		// setup
		try (final Tx tx = app.tx()) {

			final JsonSchema schema = StructrSchema.createFromDatabase(app);
			final JsonType project  = schema.addType("Project");

			project.addStringProperty("name1");
			project.addStringProperty("name2");

			final JsonFunctionProperty p1 = project.addFunctionProperty("functionProperty1");
			final JsonFunctionProperty p2 = project.addFunctionProperty("functionProperty2");

			p1.setTypeHint("String");
			p2.setTypeHint("String");

			p1.setWriteFunction("set(this, 'name1', concat('from StructrScript', value))");
			p2.setWriteFunction("{ Structr.this.name2 = 'from JavaScript' + Structr.get('value'); }");

			StructrSchema.extendDatabaseSchema(app, schema);

			tx.success();

		} catch (Throwable fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}

		final Class type       = StructrApp.getConfiguration().getNodeEntityClass("Project");
		final PropertyKey key1 = StructrApp.key(type, "functionProperty1");
                final PropertyKey key2 = StructrApp.key(type, "functionProperty2");

		// test
		try (final Tx tx = app.tx()) {

			app.create(type,
				new NodeAttribute<>(key1, "test1"),
				new NodeAttribute<>(key2, "test2")
			);

			tx.success();

		} catch (Throwable fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}

		// check result
		try (final Tx tx = app.tx()) {

			final GraphObject node = app.nodeQuery(type).getFirst();

			assertEquals("Write function has no access to 'this' object when creating a node", "from StructrScripttest1", node.getProperty(StructrApp.key(type, "name1")));
			assertEquals("Write function has no access to 'this' object when creating a node", "from JavaScripttest2", node.getProperty(StructrApp.key(type, "name2")));

			tx.success();

		} catch (Throwable fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}
	}

	@Test
	public void testStringConcatenationInJavaScript() {

		// setup
		try (final Tx tx = app.tx()) {

			final ActionContext ctx = new ActionContext(securityContext);
			final JsonSchema schema = StructrSchema.createFromDatabase(app);
			final JsonType project  = schema.addType("Project");

			project.addFunctionProperty("test1").setFormat("{ return Structr.this.name + 'test' + 123; }");
			project.addFunctionProperty("test2").setFormat("{ return 'test' + 123 + Structr.this.name; }");

			StructrSchema.extendDatabaseSchema(app, schema);

			// create some test objects
			Scripting.evaluate(ctx, null, "${{ Structr.create('Group', { name: 'test' + 1231 + 'structr' }); }}", "test");
			Scripting.evaluate(ctx, null, "${{ var g = Structr.create('Group'); g.name = 'test' + 1232 + 'structr'; }}", "test");
			Scripting.evaluate(ctx, null, "${{ var g = Structr.create('Group'); Structr.set(g, 'name', 'test' + 1233 + 'structr'); }}", "test");
			Scripting.evaluate(ctx, null, "${{ Structr.create('Group', 'name', 'test' + 1234 + 'structr'); }}", "test");

			tx.success();

		} catch (Throwable fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}

		final Class projectType = StructrApp.getConfiguration().getNodeEntityClass("Project");

		// check result
		try (final Tx tx = app.tx()) {

			int index = 1;

			for (final Group group : app.nodeQuery(Group.class).sort(Group.name).getAsList()) {

				System.out.println(group.getName());

				assertEquals("Invalid JavaScript string concatenation result for script #" + index, "test123" + index++ + "structr", group.getName());
			}

			final NodeInterface project = app.create(projectType, "structr");

			assertEquals("Invalid JavaScript string concatenation result in read function", "structrtest123", project.getProperty("test1"));
			assertEquals("Invalid JavaScript string concatenation result in read function", "test123structr", project.getProperty("test2"));

			tx.success();

		} catch (Throwable fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}
	}

	@Test
	public void testNewFindSyntaxInStructrScript() {

		// setup
		try (final Tx tx = app.tx()) {

			final JsonSchema schema = StructrSchema.createFromDatabase(app);
			schema.addType("Test");

			final JsonType project  = schema.addType("Project");

			project.addStringProperty("name1").setIndexed(true);
			project.addStringProperty("name2").setIndexed(true);
			project.addStringProperty("name3").setIndexed(true);

			project.addIntegerProperty("age").setIndexed(true);
			project.addIntegerProperty("count").setIndexed(true);

			StructrSchema.extendDatabaseSchema(app, schema);

			tx.success();

		} catch (Throwable fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}

		final Class testType    = StructrApp.getConfiguration().getNodeEntityClass("Test");
		final Class projectType = StructrApp.getConfiguration().getNodeEntityClass("Project");
		final PropertyKey name1 = StructrApp.key(projectType, "name1");
                final PropertyKey name2 = StructrApp.key(projectType, "name2");
                final PropertyKey name3 = StructrApp.key(projectType, "name3");
                final PropertyKey age   = StructrApp.key(projectType, "age");
                final PropertyKey count = StructrApp.key(projectType, "count");

		// setup
		try (final Tx tx = app.tx()) {

			app.create(projectType,
				new NodeAttribute<>(AbstractNode.name, "group1"),
				new NodeAttribute<>(name1, "structr"),
				new NodeAttribute<>(name2, "test"),
				new NodeAttribute<>(name3, "other"),
				new NodeAttribute<>(age,    22),
				new NodeAttribute<>(count, 100)
			);

			app.create(projectType,
				new NodeAttribute<>(AbstractNode.name, "group2"),
				new NodeAttribute<>(name1, "structr"),
				new NodeAttribute<>(name2, "test"),
				new NodeAttribute<>(name3, "other"),
				new NodeAttribute<>(age,    33),
				new NodeAttribute<>(count, 102)
			);

			app.create(projectType,
				new NodeAttribute<>(name1, "structr"),
				new NodeAttribute<>(name2, "other"),
				new NodeAttribute<>(name3, "other"),
				new NodeAttribute<>(age,    44),
				new NodeAttribute<>(count, 104)
			);

			for (int i=0; i<100; i++) {
				createTestNode(testType, "test" + StringUtils.leftPad(Integer.toString(i), 3, "0"));
			}

			tx.success();

		} catch (Throwable fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}


		final ActionContext ctx = new ActionContext(securityContext);

		try (final Tx tx = app.tx()) {

			assertEquals("Non-namespaced contains() returns wrong result", true, Scripting.evaluate(ctx, null, "${contains('name2', 'e')}", "testFindNewSyntax"));
			assertEquals("Non-namespaced empty() returns wrong result", true, Scripting.evaluate(ctx, null, "${empty('')}", "testFindNewSyntax"));
			assertEquals("Non-namespaced empty() returns wrong result", true, Scripting.evaluate(ctx, null, "${empty(null)}", "testFindNewSyntax"));
			assertEquals("Non-namespaced contains() returns wrong result", false, Scripting.evaluate(ctx, null, "${contains('name2', 'x')}", "testFindNewSyntax"));

			final List<NodeInterface> page1 = (List)Scripting.evaluate(ctx, null, "${find('Test', sort('name'), page(1, 10))}", "testFindNewSyntax");
			final List<NodeInterface> page2 = (List)Scripting.evaluate(ctx, null, "${find('Test', sort('name'), page(1, 5))}", "testFindNewSyntax");
			final List<NodeInterface> page3 = (List)Scripting.evaluate(ctx, null, "${find('Test', sort('name'), page(3, 5))}", "testFindNewSyntax");

			assertEquals("Advanced find() with sort() and page() returns wrong result", 10, page1.size());
			assertEquals("Advanced find() with sort() and page() returns wrong result", "test000", page1.get(0).getName());
			assertEquals("Advanced find() with sort() and page() returns wrong result", "test001", page1.get(1).getName());
			assertEquals("Advanced find() with sort() and page() returns wrong result", "test009", page1.get(9).getName());

			assertEquals("Advanced find() with sort() and page() returns wrong result", 5, page2.size());
			assertEquals("Advanced find() with sort() and page() returns wrong result", "test000", page2.get(0).getName());
			assertEquals("Advanced find() with sort() and page() returns wrong result", "test001", page2.get(1).getName());
			assertEquals("Advanced find() with sort() and page() returns wrong result", "test004", page2.get(4).getName());

			assertEquals("Advanced find() with sort() and page() returns wrong result", 5, page3.size());
			assertEquals("Advanced find() with sort() and page() returns wrong result", "test010", page3.get(0).getName());
			assertEquals("Advanced find() with sort() and page() returns wrong result", "test011", page3.get(1).getName());
			assertEquals("Advanced find() with sort() and page() returns wrong result", "test014", page3.get(4).getName());

			assertEquals("find() with namespaced contains() return wrong result", 3, ((List)Scripting.evaluate(ctx, null, "${find('Project', contains('name2', 'e'))}", "testFindNewSyntax")).size());
			assertEquals("find() with namespaced contains() return wrong result", 1, ((List)Scripting.evaluate(ctx, null, "${find('Project', 'name', 'group1', 'name1', 'structr', 'name2', 'test')}", "testFindNewSyntax")).size());

			try {

				// test count assertion for simple parameters
				Scripting.evaluate(ctx, null, "${find('Project', 'name', 'group1', 'name1', 'structr', 'name2')}", "testFindNewSyntax");

				fail("Invalid number of parameters for find() should throw an exception.");

			} catch (FrameworkException fex) { }

			//assertEquals("find() with namespaced predicates return wrong result", 1, ((List)Scripting.evaluate(ctx, null, "${find('Project', empty('name'))}", "testFindNewSyntax")).size());
			assertEquals("find() with namespaced predicates return wrong result", 2, ((List)Scripting.evaluate(ctx, null, "${find('Project', or(empty('name'), equals('name', 'group2')))}", "testFindNewSyntax")).size());
			assertEquals("find() with namespaced predicates return wrong result", 3, ((List)Scripting.evaluate(ctx, null, "${find('Project', contains('name2', 'e'), contains('name2', 'e'), contains('name2', 'e'))}", "testFindNewSyntax")).size());
			assertEquals("find() with namespaced predicates return wrong result", 2, ((List)Scripting.evaluate(ctx, null, "${find('Project', and(equals('age', range(0, 35))))}", "testFindNewSyntax")).size());
			assertEquals("find() with namespaced predicates return wrong result", 1, ((List)Scripting.evaluate(ctx, null, "${find('Project', equals('age', range(0, 35)), equals('name', 'group2'))}", "testFindNewSyntax")).size());
			assertEquals("find() with namespaced predicates return wrong result", 1, ((List)Scripting.evaluate(ctx, null, "${find('Project', and(equals('age', range(0, 35)), equals('name', 'group2')))}", "testFindNewSyntax")).size());
			assertEquals("find() with namespaced predicates return wrong result", 3, ((List)Scripting.evaluate(ctx, null, "${find('Project', and(contains('name2', 'e')))}", "testFindNewSyntax")).size());
			assertEquals("find() with namespaced predicates return wrong result", 1, ((List)Scripting.evaluate(ctx, null, "${find('Project', and(equals('name', 'group1')))}", "testFindNewSyntax")).size());
			assertEquals("find() with namespaced predicates return wrong result", 1, ((List)Scripting.evaluate(ctx, null, "${find('Project', and(equals('name', 'group1'), equals('name1', 'structr')))}", "testFindNewSyntax")).size());
			assertEquals("find() with namespaced predicates return wrong result", 2, ((List)Scripting.evaluate(ctx, null, "${find('Project', and(equals('name1', 'structr'), equals('name2', 'test')))}", "testFindNewSyntax")).size());
			assertEquals("find() with namespaced predicates return wrong result", 0, ((List)Scripting.evaluate(ctx, null, "${find('Project', and(equals('name1', 'structr'), equals('name2', 'structr')))}", "testFindNewSyntax")).size());
			assertEquals("find() with namespaced predicates return wrong result", 2, ((List)Scripting.evaluate(ctx, null, "${find('Project', or(equals('age', 22), equals('age', 44)))}", "testFindNewSyntax")).size());
			assertEquals("find() with namespaced predicates return wrong result", 2, ((List)Scripting.evaluate(ctx, null, "${find('Project', and(equals('name3', 'other'), or(equals('age', 22), equals('age', 44))))}", "testFindNewSyntax")).size());

			tx.success();

		} catch (FrameworkException fex) {
			fex.printStackTrace();
			fail("Unexpected exception");
		}
	}

	@Test
	public void testAdvancedFindPrivilegedInJavaScript() {

		// setup
		try (final Tx tx = app.tx()) {

			final JsonSchema schema = StructrSchema.createFromDatabase(app);
			schema.addType("Test");

			final JsonType project  = schema.addType("Project");

			project.addStringProperty("name1").setIndexed(true);
			project.addStringProperty("name2").setIndexed(true);
			project.addStringProperty("name3").setIndexed(true);

			project.addIntegerProperty("age").setIndexed(true);
			project.addIntegerProperty("count").setIndexed(true);

			StructrSchema.extendDatabaseSchema(app, schema);

			tx.success();

		} catch (Throwable fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}


		final ActionContext ctx = new ActionContext(securityContext);
		final Class testType    = StructrApp.getConfiguration().getNodeEntityClass("Test");
		final Class type        = StructrApp.getConfiguration().getNodeEntityClass("Project");
		final PropertyKey name1 = StructrApp.key(type, "name1");
		final PropertyKey name2 = StructrApp.key(type, "name2");
		final PropertyKey name3 = StructrApp.key(type, "name3");
		final PropertyKey age   = StructrApp.key(type, "age");
		final PropertyKey count = StructrApp.key(type, "count");

		String group1 = null;
		String group2 = null;
		String group3 = null;

		// setup
		try (final Tx tx = app.tx()) {

			group1 = app.create(type,
				new NodeAttribute<>(AbstractNode.name, "group1"),
				new NodeAttribute<>(name1, "structr"),
				new NodeAttribute<>(name2, "test"),
				new NodeAttribute<>(name3, "other"),
				new NodeAttribute<>(age,    22),
				new NodeAttribute<>(count, 100)
			).getUuid();

			group2 = app.create(type,
				new NodeAttribute<>(AbstractNode.name, "group2"),
				new NodeAttribute<>(name1, "structr"),
				new NodeAttribute<>(name2, "test"),
				new NodeAttribute<>(name3, "other"),
				new NodeAttribute<>(age,    33),
				new NodeAttribute<>(count, 102)
			).getUuid();

			group3 = app.create(type,
				new NodeAttribute<>(name1, "structr"),
				new NodeAttribute<>(name2, "other"),
				new NodeAttribute<>(name3, "other"),
				new NodeAttribute<>(age,    44),
				new NodeAttribute<>(count, 104)
			).getUuid();

			for (int i=0; i<100; i++) {
				createTestNode(testType, "test" + StringUtils.leftPad(Integer.toString(i), 3, "0"));
			}

			tx.success();

		} catch (Throwable fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}

		try (final Tx tx = app.tx()) {

			final List<NodeInterface> result1 = (List)Scripting.evaluate(ctx, null, "${{ return $.findPrivileged('Project', { 'name2': $.predicate.contains('s') }, $.predicate.sort('name', true)); }}", "testFindNewSyntax");
			final List<NodeInterface> result2 = (List)Scripting.evaluate(ctx, null, "${{ return $.findPrivileged('Project', $.predicate.sort('name', true)); }}", "testFindNewSyntax");
			final List<NodeInterface> result3 = (List)Scripting.evaluate(ctx, null, "${{ return $.findPrivileged('Project', $.predicate.sort('name')); }}", "testFindNewSyntax");

			final String testFunction = "${{\n" +
			"    let users = $.find('Project', {\n" +
			"            $and: {\n" +
			"                'name1': 'structr',\n" +
			"                'age': $.predicate.range(30, 50)\n" +
			"            }\n" +
			"        },\n" +
			"        $.predicate.sort('name', true),\n" +
			"        $.predicate.page(1, 10)\n" +
			"    );\n" +
			"    return users;\n" +
			"}}";

			final Object result4Object        = Scripting.evaluate(ctx, null, testFunction, "testFindNewSyntax");
			final List<NodeInterface> result4 = (List)result4Object;

			assertEquals("Advanced find() does not filter correctly", 2, result1.size());
			assertEquals("Advanced find() does not filter correctly", result1.get(0).getUuid(), group2);
			assertEquals("Advanced find() does not filter correctly", result1.get(1).getUuid(), group1);

			assertEquals("sort() in advanced find() does not sort correctly", result2.get(0).getUuid(), group3);
			assertEquals("sort() in advanced find() does not sort correctly", result2.get(1).getUuid(), group2);
			assertEquals("sort() in advanced find() does not sort correctly", result2.get(2).getUuid(), group1);

			assertEquals("sort() in advanced find() does not sort correctly", result3.get(0).getUuid(), group1);
			assertEquals("sort() in advanced find() does not sort correctly", result3.get(1).getUuid(), group2);
			assertEquals("sort() in advanced find() does not sort correctly", result3.get(2).getUuid(), group3);

			assertEquals("Advanced find() does not filter correctly", 2, result4.size());
			assertEquals("Advanced find() does not filter correctly", result4.get(0).getUuid(), group3);
			assertEquals("Advanced find() does not filter correctly", result4.get(1).getUuid(), group2);

			assertEquals("Advanced find() returns wrong result", 1, ((List)Scripting.evaluate(ctx, null, "${{ return $.findPrivileged('Project', { name: $.predicate.contains('2') }); }}", "testFindNewSyntax")).size());
			assertEquals("Advanced find() returns wrong result", 3, ((List)Scripting.evaluate(ctx, null, "${{ return $.findPrivileged('Project', $.predicate.contains('name2', 'e')); }}", "testFindNewSyntax")).size());
			assertEquals("Advanced find() returns wrong result", 1, ((List)Scripting.evaluate(ctx, null, "${{ return $.findPrivileged('Project', { name: 'group1', name1: 'structr', name2: 'test' }); }}", "testFindNewSyntax")).size());
			assertEquals("Advanced find() returns wrong result", 1, ((List)Scripting.evaluate(ctx, null, "${{ return $.findPrivileged('Project', $.predicate.empty('name')); }}", "testFindNewSyntax")).size());
			assertEquals("Advanced find() returns wrong result", 2, ((List)Scripting.evaluate(ctx, null, "${{ return $.findPrivileged('Project', $.predicate.or($.predicate.empty('name'), $.predicate.equals('name', 'group2'))); }}", "testFindNewSyntax")).size());
			assertEquals("Advanced find() returns wrong result", 3, ((List)Scripting.evaluate(ctx, null, "${{ return $.findPrivileged('Project', $.predicate.contains('name2', 'e'), $.predicate.contains('name2', 'e'), $.predicate.contains('name2', 'e')); }}", "testFindNewSyntax")).size());
			assertEquals("Advanced find() returns wrong result", 2, ((List)Scripting.evaluate(ctx, null, "${{ return $.findPrivileged('Project', $.predicate.and($.predicate.equals('age', $.predicate.range(0, 35)))); }}", "testFindNewSyntax")).size());
			assertEquals("Advanced find() returns wrong result", 1, ((List)Scripting.evaluate(ctx, null, "${{ return $.findPrivileged('Project', $.predicate.equals('age', $.predicate.range(0, 35)), $.predicate.equals('name', 'group2')); }}", "testFindNewSyntax")).size());
			assertEquals("Advanced find() returns wrong result", 1, ((List)Scripting.evaluate(ctx, null, "${{ return $.findPrivileged('Project', $.predicate.and($.predicate.equals('age', $.predicate.range(0, 35)), $.predicate.equals('name', 'group2'))); }}", "testFindNewSyntax")).size());
			assertEquals("Advanced find() returns wrong result", 3, ((List)Scripting.evaluate(ctx, null, "${{ return $.findPrivileged('Project', $.predicate.and($.predicate.contains('name2', 'e'))); }}", "testFindNewSyntax")).size());
			assertEquals("Advanced find() returns wrong result", 1, ((List)Scripting.evaluate(ctx, null, "${{ return $.findPrivileged('Project', $.predicate.and($.predicate.equals('name', 'group1'))); }}", "testFindNewSyntax")).size());
			assertEquals("Advanced find() returns wrong result", 1, ((List)Scripting.evaluate(ctx, null, "${{ return $.findPrivileged('Project', $.predicate.and($.predicate.equals('name', 'group1'), $.predicate.equals('name1', 'structr'))); }}", "testFindNewSyntax")).size());
			assertEquals("Advanced find() returns wrong result", 2, ((List)Scripting.evaluate(ctx, null, "${{ return $.findPrivileged('Project', $.predicate.and($.predicate.equals('name1', 'structr'), $.predicate.equals('name2', 'test'))); }}", "testFindNewSyntax")).size());
			assertEquals("Advanced find() returns wrong result", 0, ((List)Scripting.evaluate(ctx, null, "${{ return $.findPrivileged('Project', $.predicate.and($.predicate.equals('name1', 'structr'), $.predicate.equals('name2', 'structr'))); }}", "testFindNewSyntax")).size());
			assertEquals("Advanced find() returns wrong result", 2, ((List)Scripting.evaluate(ctx, null, "${{ return $.findPrivileged('Project', $.predicate.or($.predicate.equals('age', 22), $.predicate.equals('age', 44))); }}", "testFindNewSyntax")).size());
			assertEquals("Advanced find() returns wrong result", 2, ((List)Scripting.evaluate(ctx, null, "${{ return $.findPrivileged('Project', $.predicate.and($.predicate.equals('name3', 'other'), $.predicate.or($.predicate.equals('age', 22), $.predicate.equals('age', 44)))); }}", "testFindNewSyntax")).size());

			final List<NodeInterface> page1 = (List)Scripting.evaluate(ctx, null, "${{ return $.findPrivileged('Test', $.predicate.sort('name'), $.predicate.page(1, 10)); }}", "testFindNewSyntax");
			final List<NodeInterface> page2 = (List)Scripting.evaluate(ctx, null, "${{ return $.findPrivileged('Test', $.predicate.sort('name'), $.predicate.page(1, 5)); }}", "testFindNewSyntax");
			final List<NodeInterface> page3 = (List)Scripting.evaluate(ctx, null, "${{ return $.findPrivileged('Test', $.predicate.sort('name'), $.predicate.page(3, 5)); }}", "testFindNewSyntax");

			assertEquals("Advanced find() with sort() and page() returns wrong result", 10, page1.size());
			assertEquals("Advanced find() with sort() and page() returns wrong result", "test000", page1.get(0).getName());
			assertEquals("Advanced find() with sort() and page() returns wrong result", "test001", page1.get(1).getName());
			assertEquals("Advanced find() with sort() and page() returns wrong result", "test009", page1.get(9).getName());

			assertEquals("Advanced find() with sort() and page() returns wrong result", 5, page2.size());
			assertEquals("Advanced find() with sort() and page() returns wrong result", "test000", page2.get(0).getName());
			assertEquals("Advanced find() with sort() and page() returns wrong result", "test001", page2.get(1).getName());
			assertEquals("Advanced find() with sort() and page() returns wrong result", "test004", page2.get(4).getName());

			assertEquals("Advanced find() with sort() and page() returns wrong result", 5, page3.size());
			assertEquals("Advanced find() with sort() and page() returns wrong result", "test010", page3.get(0).getName());
			assertEquals("Advanced find() with sort() and page() returns wrong result", "test011", page3.get(1).getName());
			assertEquals("Advanced find() with sort() and page() returns wrong result", "test014", page3.get(4).getName());

			tx.success();

		} catch (FrameworkException fex) {
			fex.printStackTrace();
			fail("Unexpected exception");
		}
	}

	@Test
	public void testAdvancedFindForRemoteProperties() {

		try (final Tx tx = app.tx()) {

			final JsonSchema schema      = StructrSchema.createFromDatabase(app);
			final JsonObjectType project = schema.addType("Project");
			final JsonObjectType task    = schema.addType("Task");

			// create relation
			final JsonReferenceType rel = project.relate(task, "has", Cardinality.ManyToMany, "projects", "tasks");
			rel.setName("ProjectTasks");

			StructrSchema.extendDatabaseSchema(app, schema);

			tx.success();

		} catch (Throwable t) {

			t.printStackTrace();
			fail("Unexpected exception");
		}

		final ActionContext ctx = new ActionContext(securityContext);
		final Class projectType = StructrApp.getConfiguration().getNodeEntityClass("Project");
		final Class taskType    = StructrApp.getConfiguration().getNodeEntityClass("Task");

		final PropertyKey projectName  = StructrApp.key(projectType, "name");
		final PropertyKey projectTasks = StructrApp.key(projectType, "tasks");

		final PropertyKey taskName     = StructrApp.key(taskType, "name");

		try (final Tx tx = app.tx()) {

			final NodeInterface task1 = app.create(taskType, new NodeAttribute<>(taskName, "t1") );
			final NodeInterface task2 = app.create(taskType, new NodeAttribute<>(taskName, "t2") );
			final NodeInterface task3 = app.create(taskType, new NodeAttribute<>(taskName, "t3") );

			final NodeInterface task4 = app.create(taskType, new NodeAttribute<>(taskName, "t4") );
			final NodeInterface task5 = app.create(taskType, new NodeAttribute<>(taskName, "t5") );

			app.create(projectType, new NodeAttribute<>(projectName, "p1a"), new NodeAttribute<>(projectTasks, Arrays.asList(task1)) );
			app.create(projectType, new NodeAttribute<>(projectName, "p2a"), new NodeAttribute<>(projectTasks, Arrays.asList(task2)) );
			app.create(projectType, new NodeAttribute<>(projectName, "p3a"), new NodeAttribute<>(projectTasks, Arrays.asList(task3)) );
			app.create(projectType, new NodeAttribute<>(projectName, "p4a"), new NodeAttribute<>(projectTasks, Arrays.asList(task1, task2)) );
			app.create(projectType, new NodeAttribute<>(projectName, "p5a"), new NodeAttribute<>(projectTasks, Arrays.asList(task2, task3)) );
			app.create(projectType, new NodeAttribute<>(projectName, "p6a"), new NodeAttribute<>(projectTasks, Arrays.asList(task1, task3)) );
			app.create(projectType, new NodeAttribute<>(projectName, "p7a"), new NodeAttribute<>(projectTasks, Arrays.asList(task1, task2, task3)) );

			app.create(projectType, new NodeAttribute<>(projectName, "p1b"), new NodeAttribute<>(projectTasks, Arrays.asList(task1)) );
			app.create(projectType, new NodeAttribute<>(projectName, "p2b"), new NodeAttribute<>(projectTasks, Arrays.asList(task2)) );
			app.create(projectType, new NodeAttribute<>(projectName, "p3b"), new NodeAttribute<>(projectTasks, Arrays.asList(task3)) );
			app.create(projectType, new NodeAttribute<>(projectName, "p4b"), new NodeAttribute<>(projectTasks, Arrays.asList(task1, task2)) );
			app.create(projectType, new NodeAttribute<>(projectName, "p5b"), new NodeAttribute<>(projectTasks, Arrays.asList(task2, task3)) );
			app.create(projectType, new NodeAttribute<>(projectName, "p6b"), new NodeAttribute<>(projectTasks, Arrays.asList(task1, task3)) );
			app.create(projectType, new NodeAttribute<>(projectName, "p7b"), new NodeAttribute<>(projectTasks, Arrays.asList(task1, task2, task3)) );

			app.create(projectType, new NodeAttribute<>(projectName, "p8a"), new NodeAttribute<>(projectTasks, Arrays.asList(task1, task2, task3, task4)) );

			tx.success();

		} catch (FrameworkException t) {

			t.printStackTrace();
			fail("Unexpected exception");
		}

		try (final Tx tx = app.tx()) {

			assertEquals("Normal find() should use OR to search for remote properties", 9, ((List)Scripting.evaluate(ctx, null, "${{ let t1 = $.find('Task', 'name', 't1'); return $.find('Project', 'tasks', t1); }}", "testFindOldSyntax")).size());
			assertEquals("Normal find() should use OR to search for remote properties", 9, ((List)Scripting.evaluate(ctx, null, "${{ let t2 = $.find('Task', 'name', 't2'); return $.find('Project', 'tasks', t2); }}", "testFindOldSyntax")).size());
			assertEquals("Normal find() should use OR to search for remote properties", 9, ((List)Scripting.evaluate(ctx, null, "${{ let t3 = $.find('Task', 'name', 't3'); return $.find('Project', 'tasks', t3); }}", "testFindOldSyntax")).size());

			assertEquals("Normal find() should use OR to search for remote properties", 13, ((List)Scripting.evaluate(ctx, null, "${{ let t1_t2 = $.find('Task', 'name', $.predicate.or($.predicate.equals('name', 't1'), $.predicate.equals('name', 't2'))); return $.find('Project', 'tasks', t1_t2); }}", "testFindOldSyntax")).size());
			assertEquals("Normal find() should use OR to search for remote properties", 13, ((List)Scripting.evaluate(ctx, null, "${{ let t1_t3 = $.find('Task', 'name', $.predicate.or($.predicate.equals('name', 't1'), $.predicate.equals('name', 't3'))); return $.find('Project', 'tasks', t1_t3); }}", "testFindOldSyntax")).size());
			assertEquals("Normal find() should use OR to search for remote properties", 13, ((List)Scripting.evaluate(ctx, null, "${{ let t2_t3 = $.find('Task', 'name', $.predicate.or($.predicate.equals('name', 't2'), $.predicate.equals('name', 't3'))); return $.find('Project', 'tasks', t2_t3); }}", "testFindOldSyntax")).size());

			assertEquals("Normal find() should use OR to search for remote properties", 15, ((List)Scripting.evaluate(ctx, null, "${{ let t1_t2_t3 = $.find('Task', 'name', $.predicate.or($.predicate.equals('name', 't1'), $.predicate.equals('name', 't2'), $.predicate.equals('name', 't3'))); return $.find('Project', 'tasks', t1_t2_t3); }}", "testFindOldSyntax")).size());
			assertEquals("Normal find() should use OR to search for remote properties", 15, ((List)Scripting.evaluate(ctx, null, "${{ return $.find('Project', 'tasks', $.find('Task')); }}", "testFindOldSyntax")).size());


			assertEquals("Advanced find() should use EXACT search for $.equals predicate on remote properties", 2, ((List)Scripting.evaluate(ctx, null, "${{ let t1 = $.find('Task', 'name', 't1'); return $.find('Project', 'tasks', $.predicate.equals(t1)); }}", "testFindNewSyntax")).size());

			assertEquals("Advanced find() should use EXACT search for $.equals predicate on remote properties", 2, ((List)Scripting.evaluate(ctx, null, "${{ let t1_t2 = $.find('Task', 'name', $.predicate.or($.predicate.equals('name', 't1'), $.predicate.equals('name', 't2'))); return $.find('Project', 'tasks', $.predicate.equals(t1_t2)); }}", "testFindNewSyntax")).size());
			assertEquals("Advanced find() should use EXACT search for $.equals predicate on remote properties", 2, ((List)Scripting.evaluate(ctx, null, "${{ let t1_t3 = $.find('Task', 'name', $.predicate.or($.predicate.equals('name', 't1'), $.predicate.equals('name', 't3'))); return $.find('Project', 'tasks', $.predicate.equals(t1_t3)); }}", "testFindNewSyntax")).size());

			assertEquals("Advanced find() should use CONTAINS search for $.contains predicate on remote properties", 9, ((List)Scripting.evaluate(ctx, null, "${{ let t1 = $.find('Task', 'name', 't1'); return $.find('Project', 'tasks', $.predicate.contains(t1)); }}", "testFindNewSyntax")).size());

			assertEquals("Advanced find() should use CONTAINS search for $.contains predicate on remote properties", 5, ((List)Scripting.evaluate(ctx, null, "${{ let t1_t2 = $.find('Task', 'name', $.predicate.or($.predicate.equals('name', 't1'), $.predicate.equals('name', 't2'))); return $.find('Project', 'tasks', $.predicate.contains(t1_t2)); }}", "testFindNewSyntax")).size());

			assertEquals("Advanced find() should use CONTAINS search for $.contains predicate on remote properties", 3, ((List)Scripting.evaluate(ctx, null, "${{ let t1_t2_t3 = $.find('Task', 'name', $.predicate.or($.predicate.equals('name', 't1'), $.predicate.equals('name', 't2'), $.predicate.equals('name', 't3'))); return $.find('Project', 'tasks', $.predicate.contains(t1_t2_t3)); }}", "testFindOldSyntax")).size());

			// test with unconnected Task
			assertEquals("Advanced find() should use EXACT search for $.equals predicate on remote properties", 0, ((List)Scripting.evaluate(ctx, null, "${{ let t1_t5 = $.find('Task', 'name', $.predicate.or($.predicate.equals('name', 't1'), $.predicate.equals('name', 't5'))); return $.find('Project', 'tasks', $.predicate.equals(t1_t5)); }}", "testFindNewSyntax")).size());
			assertEquals("Advanced find() should use CONTAINS search for $.contains predicate on remote properties", 0, ((List)Scripting.evaluate(ctx, null, "${{ let t1_t5 = $.find('Task', 'name', $.predicate.or($.predicate.equals('name', 't1'), $.predicate.equals('name', 't5'))); return $.find('Project', 'tasks', $.predicate.contains(t1_t5)); }}", "testFindNewSyntax")).size());

			// test unconnected Task
			assertEquals("Normal find() should use OR to search for remote properties", 0, ((List)Scripting.evaluate(ctx, null, "${{ let t5 = $.find('Task', 'name', 't5'); return $.find('Project', 'tasks', t5); }}", "testFindOldSyntax")).size());
			assertEquals("Advanced find() should use EXACT search for $.equals predicate on remote properties", 0, ((List)Scripting.evaluate(ctx, null, "${{ let t5 = $.find('Task', 'name', 't5'); return $.find('Project', 'tasks', $.predicate.equals(t5)); }}", "testFindNewSyntax")).size());
			assertEquals("Advanced find() should use CONTAINS search for $.contains predicate on remote properties", 0, ((List)Scripting.evaluate(ctx, null, "${{ let t5 = $.find('Task', 'name', 't5'); return $.find('Project', 'tasks', $.predicate.contains(t5)); }}", "testFindNewSyntax")).size());


			// ($.and and $.or with $.contains)
			assertEquals("Advanced find() should use CONTAINS search for $.contains predicate on remote properties", 9, ((List)Scripting.evaluate(ctx, null, "${{ let t1 = $.find('Task', 'name', 't1'); let t5 = $.find('Task', 'name', 't5'); return $.find('Project', 'tasks', $.predicate.or($.predicate.contains(t1), $.predicate.contains(t5))); }}", "testFindNewSyntax")).size());
			assertEquals("Advanced find() should use CONTAINS search for $.contains predicate on remote properties", 0, ((List)Scripting.evaluate(ctx, null, "${{ let t1 = $.find('Task', 'name', 't1'); let t5 = $.find('Task', 'name', 't5'); return $.find('Project', 'tasks', $.predicate.and($.predicate.contains(t1), $.predicate.contains(t5))); }}", "testFindNewSyntax")).size());

			// ($.not and $.empty)
			assertEquals("Advanced find() should understand $.not predicate for remote properties", 4, ((List)Scripting.evaluate(ctx, null, "${{ return $.find('Task', $.predicate.not($.predicate.empty('projects'))); }}", "testFindNewSyntax")).size());
			assertEquals("Advanced find() should understand $.empty predicate for remote properties", 1, ((List)Scripting.evaluate(ctx, null, "${{ return $.find('Task', $.predicate.empty('projects')); }}", "testFindNewSyntax")).size());

		} catch (FrameworkException t) {

			t.printStackTrace();
			fail("Unexpected exception");
		}
	}

	@Test
	public void testEmptyPredicateForRemoteProperties() {

		try (final Tx tx = app.tx()) {

			final JsonSchema schema      = StructrSchema.createFromDatabase(app);
			final JsonObjectType project = schema.addType("Project");
			final JsonObjectType task    = schema.addType("Task");

			// create relation
			project.relate(project, "SUB", Cardinality.OneToMany, "parent", "children");
			 project.relate(task, "TASK", Cardinality.OneToMany, "project", "tasks");

			StructrSchema.extendDatabaseSchema(app, schema);

			tx.success();

		} catch (Throwable t) {

			t.printStackTrace();
			fail("Unexpected exception");
		}

		final ActionContext ctx = new ActionContext(securityContext);
		final Class projectType = StructrApp.getConfiguration().getNodeEntityClass("Project");
		final Class taskType    = StructrApp.getConfiguration().getNodeEntityClass("Task");

		final PropertyKey projectChildren = StructrApp.key(projectType, "children");
		final PropertyKey projectTasks    = StructrApp.key(projectType, "tasks");

		final PropertyKey taskName     = StructrApp.key(taskType, "name");

		try (final Tx tx = app.tx()) {

			final NodeInterface task1 = app.create(taskType, new NodeAttribute<>(taskName, "t1") );
			final NodeInterface task2 = app.create(taskType, new NodeAttribute<>(taskName, "t2") );
			final NodeInterface task3 = app.create(taskType, new NodeAttribute<>(taskName, "t3") );
			final NodeInterface task4 = app.create(taskType, new NodeAttribute<>(taskName, "t4") );
			final NodeInterface task5 = app.create(taskType, new NodeAttribute<>(taskName, "t5") );
			final NodeInterface task6 = app.create(taskType, new NodeAttribute<>(taskName, "t6") );
			final NodeInterface task7 = app.create(taskType, new NodeAttribute<>(taskName, "t7") );
			final NodeInterface task8 = app.create(taskType, new NodeAttribute<>(taskName, "t8") );

			final NodeInterface project1 = app.create(projectType,
				new NodeAttribute<>(AbstractNode.name, "Project #1"),
				new NodeAttribute<>(projectTasks, List.of(task1, task2))
			);

			final NodeInterface project2 = app.create(projectType,
				new NodeAttribute<>(AbstractNode.name, "Project #2"),
				new NodeAttribute<>(projectTasks, List.of(task3, task4)),
				new NodeAttribute<>(projectChildren, List.of(project1))
			);

			app.create(projectType,
				new NodeAttribute<>(AbstractNode.name, "Project #3"),
				new NodeAttribute<>(projectTasks, List.of(task5, task6)),
				new NodeAttribute<>(projectChildren, List.of(project2))
			);

			app.create(projectType, new NodeAttribute<>(AbstractNode.name, "Project #4"));
			app.create(projectType, new NodeAttribute<>(AbstractNode.name, "Project #5"));

			tx.success();

		} catch (FrameworkException t) {

			t.printStackTrace();
			fail("Unexpected exception");
		}

		try (final Tx tx = app.tx()) {

			final String errorMessage = "Advanced find() should understand $.empty predicate for remote properties";

			Settings.CypherDebugLogging.setValue(true);

			assertEquals(errorMessage, 3, ((List)Scripting.evaluate(ctx, null, "${{ return $.find('Project', $.predicate.empty('parent')); }}", "testFindNewSyntax1")).size());
			assertEquals(errorMessage, 3, ((List)Scripting.evaluate(ctx, null, "${{ return $.find('Project', $.predicate.empty('children')); }}", "testFindNewSyntax2")).size());
			assertEquals(errorMessage, 2, ((List)Scripting.evaluate(ctx, null, "${{ return $.find('Task', $.predicate.empty('project')); }}", "testFindNewSyntax3")).size());

		} catch (FrameworkException t) {

			t.printStackTrace();
			fail("Unexpected exception");

		} finally {

			Settings.CypherDebugLogging.setValue(false);
		}
	}

	@Test
	public void testNewFindSyntaxInJavaScript() {

		// setup
		try (final Tx tx = app.tx()) {

			final JsonSchema schema = StructrSchema.createFromDatabase(app);
			schema.addType("Test");

			final JsonType project  = schema.addType("Project");

			project.addStringProperty("name1").setIndexed(true);
			project.addStringProperty("name2").setIndexed(true);
			project.addStringProperty("name3").setIndexed(true);

			project.addIntegerProperty("age").setIndexed(true);
			project.addIntegerProperty("count").setIndexed(true);

			StructrSchema.extendDatabaseSchema(app, schema);

			tx.success();

		} catch (Throwable fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}


		final ActionContext ctx = new ActionContext(securityContext);
		final Class testType    = StructrApp.getConfiguration().getNodeEntityClass("Test");
		final Class type        = StructrApp.getConfiguration().getNodeEntityClass("Project");
		final PropertyKey name1 = StructrApp.key(type, "name1");
		final PropertyKey name2 = StructrApp.key(type, "name2");
		final PropertyKey name3 = StructrApp.key(type, "name3");
		final PropertyKey age   = StructrApp.key(type, "age");
		final PropertyKey count = StructrApp.key(type, "count");

		String group1 = null;
		String group2 = null;
		String group3 = null;

		// setup
		try (final Tx tx = app.tx()) {

			group1 = app.create(type,
				new NodeAttribute<>(AbstractNode.name, "group1"),
				new NodeAttribute<>(name1, "structr"),
				new NodeAttribute<>(name2, "test"),
				new NodeAttribute<>(name3, "other"),
				new NodeAttribute<>(age,    22),
				new NodeAttribute<>(count, 100)
			).getUuid();

			group2 = app.create(type,
				new NodeAttribute<>(AbstractNode.name, "group2"),
				new NodeAttribute<>(name1, "structr"),
				new NodeAttribute<>(name2, "test"),
				new NodeAttribute<>(name3, "other"),
				new NodeAttribute<>(age,    33),
				new NodeAttribute<>(count, 102)
			).getUuid();

			group3 = app.create(type,
				new NodeAttribute<>(name1, "structr"),
				new NodeAttribute<>(name2, "other"),
				new NodeAttribute<>(name3, "other"),
				new NodeAttribute<>(age,    44),
				new NodeAttribute<>(count, 104)
			).getUuid();

			for (int i=0; i<100; i++) {
				createTestNode(testType, "test" + StringUtils.leftPad(Integer.toString(i), 3, "0"));
			}

			tx.success();

		} catch (Throwable fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}

		try (final Tx tx = app.tx()) {

			final List<NodeInterface> result1 = (List)Scripting.evaluate(ctx, null, "${{ return $.find('Project', { 'name2': $.predicate.contains('s') }, $.predicate.sort('name', true)); }}", "testFindNewSyntax");
			final List<NodeInterface> result2 = (List)Scripting.evaluate(ctx, null, "${{ return $.find('Project', $.predicate.sort('name', true)); }}", "testFindNewSyntax");
			final List<NodeInterface> result3 = (List)Scripting.evaluate(ctx, null, "${{ return $.find('Project', $.predicate.sort('name')); }}", "testFindNewSyntax");

			final String testFunction = "${{\n" +
			"    let users = $.find('Project', {\n" +
			"            $and: {\n" +
			"                'name1': 'structr',\n" +
			"                'age': $.predicate.range(30, 50)\n" +
			"            }\n" +
			"        },\n" +
			"        $.predicate.sort('name', true),\n" +
			"        $.predicate.page(1, 10)\n" +
			"    );\n" +
			"    return users;\n" +
			"}}";

			final Object result4Object        = Scripting.evaluate(ctx, null, testFunction, "testFindNewSyntax");
			final List<NodeInterface> result4 = (List)result4Object;

			// make results visible in log file
			System.out.println("#### result1");
			result1.stream().forEach(n -> System.out.println(n.getProperty(AbstractNode.name)));

			System.out.println("#### result2");
			result2.stream().forEach(n -> System.out.println(n.getProperty(AbstractNode.name)));

			System.out.println("#### result3");
			result3.stream().forEach(n -> System.out.println(n.getProperty(AbstractNode.name)));

			System.out.println("#### result4");
			result4.stream().forEach(n -> System.out.println(n.getProperty(AbstractNode.name)));

			assertEquals("Advanced find() does not filter correctly", 2, result1.size());
			assertEquals("Advanced find() does not filter correctly", result1.get(0).getUuid(), group2);
			assertEquals("Advanced find() does not filter correctly", result1.get(1).getUuid(), group1);

			assertEquals("sort() in advanced find() does not sort correctly", result2.get(0).getUuid(), group3);
			assertEquals("sort() in advanced find() does not sort correctly", result2.get(1).getUuid(), group2);
			assertEquals("sort() in advanced find() does not sort correctly", result2.get(2).getUuid(), group1);

			assertEquals("sort() in advanced find() does not sort correctly", result3.get(0).getUuid(), group1);
			assertEquals("sort() in advanced find() does not sort correctly", result3.get(1).getUuid(), group2);
			assertEquals("sort() in advanced find() does not sort correctly", result3.get(2).getUuid(), group3);

			assertEquals("Advanced find() does not filter correctly", 2, result4.size());
			assertEquals("Advanced find() does not filter correctly", result4.get(0).getUuid(), group3);
			assertEquals("Advanced find() does not filter correctly", result4.get(1).getUuid(), group2);

			assertEquals("Advanced find() returns wrong result", 1, ((List)Scripting.evaluate(ctx, null, "${{ return $.find('Project', { name: $.predicate.contains('2') }); }}", "testFindNewSyntax")).size());
			assertEquals("Advanced find() returns wrong result", 3, ((List)Scripting.evaluate(ctx, null, "${{ return $.find('Project', $.predicate.contains('name2', 'e')); }}", "testFindNewSyntax")).size());
			assertEquals("Advanced find() returns wrong result", 1, ((List)Scripting.evaluate(ctx, null, "${{ return $.find('Project', { name: 'group1', name1: 'structr', name2: 'test' }); }}", "testFindNewSyntax")).size());
			assertEquals("Advanced find() returns wrong result", 1, ((List)Scripting.evaluate(ctx, null, "${{ return $.find('Project', $.predicate.empty('name')); }}", "testFindNewSyntax")).size());
			assertEquals("Advanced find() returns wrong result", 2, ((List)Scripting.evaluate(ctx, null, "${{ return $.find('Project', $.predicate.or($.predicate.empty('name'), $.predicate.equals('name', 'group2'))); }}", "testFindNewSyntax")).size());
			assertEquals("Advanced find() returns wrong result", 3, ((List)Scripting.evaluate(ctx, null, "${{ return $.find('Project', $.predicate.contains('name2', 'e'), $.predicate.contains('name2', 'e'), $.predicate.contains('name2', 'e')); }}", "testFindNewSyntax")).size());
			assertEquals("Advanced find() returns wrong result", 2, ((List)Scripting.evaluate(ctx, null, "${{ return $.find('Project', $.predicate.and($.predicate.equals('age', $.predicate.range(0, 35)))); }}", "testFindNewSyntax")).size());
			assertEquals("Advanced find() returns wrong result", 1, ((List)Scripting.evaluate(ctx, null, "${{ return $.find('Project', $.predicate.equals('age', $.predicate.range(0, 35)), $.predicate.equals('name', 'group2')); }}", "testFindNewSyntax")).size());
			assertEquals("Advanced find() returns wrong result", 1, ((List)Scripting.evaluate(ctx, null, "${{ return $.find('Project', $.predicate.and($.predicate.equals('age', $.predicate.range(0, 35)), $.predicate.equals('name', 'group2'))); }}", "testFindNewSyntax")).size());
			assertEquals("Advanced find() returns wrong result", 3, ((List)Scripting.evaluate(ctx, null, "${{ return $.find('Project', $.predicate.and($.predicate.contains('name2', 'e'))); }}", "testFindNewSyntax")).size());
			assertEquals("Advanced find() returns wrong result", 1, ((List)Scripting.evaluate(ctx, null, "${{ return $.find('Project', $.predicate.and($.predicate.equals('name', 'group1'))); }}", "testFindNewSyntax")).size());
			assertEquals("Advanced find() returns wrong result", 1, ((List)Scripting.evaluate(ctx, null, "${{ return $.find('Project', $.predicate.and($.predicate.equals('name', 'group1'), $.predicate.equals('name1', 'structr'))); }}", "testFindNewSyntax")).size());
			assertEquals("Advanced find() returns wrong result", 2, ((List)Scripting.evaluate(ctx, null, "${{ return $.find('Project', $.predicate.and($.predicate.equals('name1', 'structr'), $.predicate.equals('name2', 'test'))); }}", "testFindNewSyntax")).size());
			assertEquals("Advanced find() returns wrong result", 0, ((List)Scripting.evaluate(ctx, null, "${{ return $.find('Project', $.predicate.and($.predicate.equals('name1', 'structr'), $.predicate.equals('name2', 'structr'))); }}", "testFindNewSyntax")).size());
			assertEquals("Advanced find() returns wrong result", 2, ((List)Scripting.evaluate(ctx, null, "${{ return $.find('Project', $.predicate.or($.predicate.equals('age', 22), $.predicate.equals('age', 44))); }}", "testFindNewSyntax")).size());
			assertEquals("Advanced find() returns wrong result", 2, ((List)Scripting.evaluate(ctx, null, "${{ return $.find('Project', $.predicate.and($.predicate.equals('name3', 'other'), $.predicate.or($.predicate.equals('age', 22), $.predicate.equals('age', 44)))); }}", "testFindNewSyntax")).size());

			final List<NodeInterface> page1 = (List)Scripting.evaluate(ctx, null, "${{ return $.find('Test', $.predicate.sort('name'), $.predicate.page(1, 10)); }}", "testFindNewSyntax");
			final List<NodeInterface> page2 = (List)Scripting.evaluate(ctx, null, "${{ return $.find('Test', $.predicate.sort('name'), $.predicate.page(1, 5)); }}", "testFindNewSyntax");
			final List<NodeInterface> page3 = (List)Scripting.evaluate(ctx, null, "${{ return $.find('Test', $.predicate.sort('name'), $.predicate.page(3, 5)); }}", "testFindNewSyntax");

			assertEquals("Advanced find() with sort() and page() returns wrong result", 10, page1.size());
			assertEquals("Advanced find() with sort() and page() returns wrong result", "test000", page1.get(0).getName());
			assertEquals("Advanced find() with sort() and page() returns wrong result", "test001", page1.get(1).getName());
			assertEquals("Advanced find() with sort() and page() returns wrong result", "test009", page1.get(9).getName());

			assertEquals("Advanced find() with sort() and page() returns wrong result", 5, page2.size());
			assertEquals("Advanced find() with sort() and page() returns wrong result", "test000", page2.get(0).getName());
			assertEquals("Advanced find() with sort() and page() returns wrong result", "test001", page2.get(1).getName());
			assertEquals("Advanced find() with sort() and page() returns wrong result", "test004", page2.get(4).getName());

			assertEquals("Advanced find() with sort() and page() returns wrong result", 5, page3.size());
			assertEquals("Advanced find() with sort() and page() returns wrong result", "test010", page3.get(0).getName());
			assertEquals("Advanced find() with sort() and page() returns wrong result", "test011", page3.get(1).getName());
			assertEquals("Advanced find() with sort() and page() returns wrong result", "test014", page3.get(4).getName());

			tx.success();

		} catch (FrameworkException fex) {
			fex.printStackTrace();
			fail("Unexpected exception");
		}
	}

	@Test
	public void testAdvancedFindRangeQueryLeak() {

		// setup
		try (final Tx tx = app.tx()) {

			final JsonSchema schema = StructrSchema.createFromDatabase(app);
			schema.addType("Test");

			final JsonType testType  = schema.addType("TestType");

			testType.addIntegerProperty("count").setIndexed(true);

			StructrSchema.extendDatabaseSchema(app, schema);

			tx.success();

		} catch (Throwable fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}


		final ActionContext ctx                = new ActionContext(securityContext);
		final Class type                       = StructrApp.getConfiguration().getNodeEntityClass("TestType");
		final PropertyKey count                = StructrApp.key(type, "count");
		final PropertyKey visibleToPublicUsers = StructrApp.key(type, "visibleToPublicUsers");

		// setup
		try (final Tx tx = app.tx()) {

			int cnt = 0;

			while (cnt < 10) {

				app.create(type,
					new NodeAttribute<>(visibleToPublicUsers, true),
					new NodeAttribute<>(count, cnt)
				);

				app.create(type,
					new NodeAttribute<>(visibleToPublicUsers, false),
					new NodeAttribute<>(count, cnt + 10)
				);

				cnt++;
			}

			tx.success();

		} catch (Throwable fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}

		try (final Tx tx = app.tx()) {

			// AND: works
			final String testRangeFunctionInANDGroup = "${{\n" +
			"    let nodes = $.find('TestType', {\n" +
			"            $and: {\n" +
			"                'visibleToPublicUsers': true,\n" +
			"                'count': $.predicate.range(5, 14)\n" +
			"            }\n" +
			"        }\n" +
			"    );\n" +
			"    return nodes;\n" +
			"}}";

			final List<NodeInterface> res1 = (List)Scripting.evaluate(ctx, null, testRangeFunctionInANDGroup, "testAdvancedFindRangeQueryLeak");
			assertEquals("Advanced find range predicate does not filter correctly for surrounding AND", 5, res1.size());


			// OR with workaround AND around range: works
			final String testRangeFunctionORWrapRangeInAND = "${{\n" +
			"    let nodes = $.find('TestType', {\n" +
			"            $or: {\n" +
			"                'visibleToPublicUsers': true,\n" +
			"                $and: {\n" +
			"                    'count': $.predicate.range(5, 14)\n" +
			"                }\n" +
			"            }\n" +
			"        }\n" +
			"    );\n" +
			"    return nodes;\n" +
			"}}";

			final List<NodeInterface> res2 = (List)Scripting.evaluate(ctx, null, testRangeFunctionORWrapRangeInAND, "testAdvancedFindRangeQueryLeak");
			assertEquals("Advanced find range predicate does not filter correctly for surrounding OR (even when wrapped in and() itself)", 15, res2.size());


			// Plain OR with structrscript syntax: does not work
			final String testRangeFunctionInORGroupStructrScriptSyntax = "${{\n" +
			"    let nodes = $.find('TestType', \n" +
			"            $.predicate.or(\n" +
			"                $.predicate.equals('visibleToPublicUsers', true),\n" +
			"                $.predicate.equals('count', $.predicate.range(5, 14))\n" +
			"            )\n" +
			"    );\n" +
			"    return nodes;\n" +
			"}}";

			final List<NodeInterface> res3 = (List)Scripting.evaluate(ctx, null, testRangeFunctionInORGroupStructrScriptSyntax, "testAdvancedFindRangeQueryLeak");
			assertEquals("Advanced find range predicate does not filter correctly for surrounding OR (range() leaks outward and turns OR into AND) [StructrScript Syntax]", 15, res3.size());


			// Plain OR with JavaScript syntax: does not work
			final String testRangeFunctionInORGroupOtherSyntax = "${{\n" +
			"    let nodes = $.find('TestType', {\n" +
			"            $or: {\n" +
			"                'visibleToPublicUsers': true,\n" +
			"                'count': $.predicate.range(5, 14)\n" +
			"            }\n" +
			"        }\n" +
			"    );\n" +
			"    return nodes;\n" +
			"}}";

			final List<NodeInterface> res4 = (List)Scripting.evaluate(ctx, null, testRangeFunctionInORGroupOtherSyntax, "testAdvancedFindRangeQueryLeak");
			assertEquals("Advanced find range predicate does not filter correctly for surrounding OR (range() leaks outward and turns OR into AND) [JavaScript Syntax]", 15, res4.size());


			tx.success();

		} catch (FrameworkException fex) {
			fex.printStackTrace();
			fail("Unexpected exception");
		}
	}

	@Test
	public void testJavascriptArrayWrapping() {

		// setup
		try (final Tx tx = app.tx()) {

			final JsonSchema schema = StructrSchema.createFromDatabase(app);
			final JsonType test  = schema.addType("Test");

			test.addMethod("doTest", "{ let arr = []; arr.push({ name: 'test1' }); arr.push({ name: 'test2' }); arr.push({ name: 'test2' }); return arr; }", "");

			StructrSchema.extendDatabaseSchema(app, schema);

			tx.success();

		} catch (Throwable fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}

		final ActionContext ctx = new ActionContext(securityContext);
		final Class testType    = StructrApp.getConfiguration().getNodeEntityClass("Test");

		// setup
		try (final Tx tx = app.tx()) {

			app.create(testType, "test");

			final Object result = Scripting.evaluate(ctx, null, "${{ var test = $.find('Test')[0]; var arr = test.doTest(); for (e of arr) { Structr.log(e); }; return arr; }}", "test");

			assertTrue("Invalid wrapping of native Javascript array", result instanceof List);
			assertEquals("Invalid wrapping of native Javascript array", 3, ((List)result).size());

			tx.success();

		} catch (Throwable fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}
	}

	@Test
	public void testNamespaceHandling() {

		final ActionContext ctx = new ActionContext(securityContext);

		try (final Tx tx = app.tx()) {

			// test that the empty() function is resolved to the non-namespaced one after exiting find()
			assertEquals("StructrScript: namespace is not exited correctly after entering find()", "[]true", (Scripting.evaluate(ctx, null, "${(find('Group'),empty(null))}", "test")));
			assertEquals("JavaScript: namespace is not exited correctly after entering find()", true, (Scripting.evaluate(ctx, null, "${{ $.find('Group'); return $.empty(null); }}", "test")));

			tx.success();

		} catch (FrameworkException fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}
	}

	@Test
	public void testJavascriptFindDateRange() {

		// setup
		try (final Tx tx = app.tx()) {

			final JsonSchema schema = StructrSchema.createFromDatabase(app);
			schema.addType("Test");

			final JsonType project  = schema.addType("Project");

			project.addDateProperty("date").setIndexed(true);

			StructrSchema.extendDatabaseSchema(app, schema);

			tx.success();

		} catch (Throwable fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}


		final ActionContext ctx = new ActionContext(securityContext);
		final Class type        = StructrApp.getConfiguration().getNodeEntityClass("Project");
		final PropertyKey date  = StructrApp.key(type, "date");
		final Calendar calendar = GregorianCalendar.getInstance();

		// setup
		try (final Tx tx = app.tx()) {

			// 01.01.2019
			calendar.set(2019, 0, 1, 0, 0, 0);
			app.create(type, new NodeAttribute<>(AbstractNode.name, "p1"), new NodeAttribute<>(date, calendar.getTime()));

			// 01.02.2019
			calendar.set(2019, 1, 1, 0, 0, 0);
			app.create(type, new NodeAttribute<>(AbstractNode.name, "p2"), new NodeAttribute<>(date, calendar.getTime()));

			// 01.03.2019
			calendar.set(2019, 2, 1, 0, 0, 0);
			app.create(type, new NodeAttribute<>(AbstractNode.name, "p3"), new NodeAttribute<>(date, calendar.getTime()));

			// 01.04.2019
			calendar.set(2019, 3, 1, 0, 0, 0);
			app.create(type, new NodeAttribute<>(AbstractNode.name, "p4"), new NodeAttribute<>(date, calendar.getTime()));

			// 01.05.2019
			calendar.set(2019, 4, 1, 0, 0, 0);
			app.create(type, new NodeAttribute<>(AbstractNode.name, "p5"), new NodeAttribute<>(date, calendar.getTime()));

			tx.success();

		} catch (Throwable fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}

		// setup
		try (final Tx tx = app.tx()) {

			final String errorMessage = "Advanced find() with date range returns wrong result";

			assertEquals(errorMessage, 5, ((List)Scripting.evaluate(ctx, null, formatDateTestScript("new Date",   "2000-01-01", "2100-01-01", "createdDate"), "testFindNewSyntax")).size());
			assertEquals(errorMessage, 5, ((List)Scripting.evaluate(ctx, null, formatDateTestScript("Date.parse", "2000-01-01", "2100-01-01", "createdDate"), "testFindNewSyntax")).size());

			assertEquals(errorMessage, 5, ((List)Scripting.evaluate(ctx, null, formatDateTestScript("new Date",   "2018-01-01", "2020-01-01", "date"), "testFindNewSyntax")).size());
			assertEquals(errorMessage, 5, ((List)Scripting.evaluate(ctx, null, formatDateTestScript("Date.parse", "2018-01-01", "2020-01-01", "date"), "testFindNewSyntax")).size());

			assertEquals(errorMessage, 2, ((List)Scripting.evaluate(ctx, null, formatDateTestScript("new Date",   "2018-12-31", "2019-02-15", "date"), "testFindNewSyntax")).size());
			assertEquals(errorMessage, 2, ((List)Scripting.evaluate(ctx, null, formatDateTestScript("Date.parse", "2018-12-31", "2019-02-15", "date"), "testFindNewSyntax")).size());

			tx.success();

		} catch (Throwable fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}
	}

	@Test
	public void testDatePropertyWithNonStandardFormatInScripting() {

		// setup
		try (final Tx tx = app.tx()) {

			final JsonSchema schema = StructrSchema.createFromDatabase(app);
			schema.addType("Test");

			final JsonType project  = schema.addType("Project");
			project.addDateProperty("date").setIndexed(true).setFormat("yyyy");

			StructrSchema.extendDatabaseSchema(app, schema);

			tx.success();

		} catch (Throwable fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}

		final ActionContext ctx = new ActionContext(securityContext);
		final Class type        = StructrApp.getConfiguration().getNodeEntityClass("Project");
		final PropertyKey date  = StructrApp.key(type, "date");
		final Calendar calendar = GregorianCalendar.getInstance();

		// setup
		try (final Tx tx = app.tx()) {

			calendar.set(2019, 0, 1, 10, 20, 30);
			app.create(type, new NodeAttribute<>(AbstractNode.name, "p1"), new NodeAttribute<>(date, calendar.getTime()));

			tx.success();

		} catch (Throwable fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}

		try (final Tx tx = app.tx()) {

			final Object value1 = Scripting.evaluate(ctx, null, "${{ return $.find('Project', 'name', 'p1')[0].date; }}", "");
			final Object value2 = Scripting.evaluate(ctx, null, "${{ return $.get($.find('Project', 'name', 'p1')[0], 'date'); }}", "");

			final Object value3 = Scripting.evaluate(ctx, null, "${find('Project', 'name', 'p1')[0].date}", "");
			final Object value4 = Scripting.evaluate(ctx, null, "${get(first(find('Project', 'name', 'p1')), 'date')}", "");

			assertTrue("dot notation should yield unformatted date object", value1 instanceof Date);
			assertTrue("get function should yield formatted date string", value2 instanceof String);
			assertTrue("dot notation should yield unformatted date object", value3 instanceof Date);
			assertTrue("get function should yield formatted date string", value4 instanceof String);

			tx.success();

		} catch (Throwable fex) {

			fex.printStackTrace();
			fail(fex.getMessage());
		}
	}

	@Test
	public void testBatchErrorHandler() {

		final ActionContext ctx = new ActionContext(securityContext);

		try (final Tx tx = app.tx()) {

			assertEquals("", "", (Scripting.evaluate(ctx, null, "${{ $.batch(function() { $.error('base', 'nope', 'detail'); }, function() { $.store('test-result', 'error_handled'); }); }}", "test")));
			assertEquals("Error handler in batch function was not called.", "error_handled", ctx.retrieve("test-result"));

			tx.success();

		} catch (FrameworkException fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}
	}

	@Test
	public void testNotEqual() {

		final ActionContext ctx = new ActionContext(securityContext);

		// setup
		try (final Tx tx = app.tx()) {

			app.create(Group.class, "group1");
			app.create(Group.class, "group2");
			app.create(Group.class, "group3");
			app.create(Group.class, "group4");

			tx.success();

		} catch (FrameworkException fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}

		try (final Tx tx = app.tx()) {

			final Group group1   = app.nodeQuery(Group.class).andName("group1").getFirst();
			final Group group2   = app.nodeQuery(Group.class).andName("group2").getFirst();
			final String script1 = "${{ return $.find('Group', { $and: { name: 'group1', id: $.predicate.not($.predicate.equals('" + group1.getUuid() + "')) }}); }}";
			final String script2 = "${{ return $.find('Group', { $and: { name: 'group1', id: $.predicate.not($.predicate.equals('" + group2.getUuid() + "')) }}); }}";

			// test that not(equal()) works for the id property
			final Object result1 = Scripting.evaluate(ctx, null, script1, "test1");
			final Object result2 = Scripting.evaluate(ctx, null, script2, "test2");

			assertEquals("Invalid advanced find result for not(equals)) with ID", 0, ((List)result1).size());
			assertEquals("Invalid advanced find result for not(equals)) with ID", 1, ((List)result2).size());

			tx.success();

		} catch (FrameworkException fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}
	}

	@Test
	public void testFindNotEmpty() {

		final ActionContext ctx = new ActionContext(securityContext);

		// setup
		try (final Tx tx = app.tx()) {

			final JsonSchema schema      = StructrSchema.createEmptySchema();
			final JsonObjectType contact = schema.addType("Contact");

			contact.addIntegerProperty("num").setIndexed(true);

			// add
			StructrSchema.extendDatabaseSchema(app, schema);

			tx.success();

		} catch (Throwable t) {

			t.printStackTrace();
			fail("Unexpected exception.");
		}

		final Class contactClass = StructrApp.getConfiguration().getNodeEntityClass("Contact");
		final PropertyKey numKey = StructrApp.key(contactClass, "num");

		try (final Tx tx = app.tx()) {

			app.create(contactClass, new NodeAttribute<>(AbstractNode.name, "contact01"), new NodeAttribute<>(numKey,   12));
			app.create(contactClass, new NodeAttribute<>(AbstractNode.name, "contact02"), new NodeAttribute<>(numKey,   11));
			app.create(contactClass, new NodeAttribute<>(AbstractNode.name, "contact03"), new NodeAttribute<>(numKey, null));
			app.create(contactClass, new NodeAttribute<>(AbstractNode.name,          ""), new NodeAttribute<>(numKey,    9));
			app.create(contactClass, new NodeAttribute<>(AbstractNode.name, "contact05"), new NodeAttribute<>(numKey,    8));
			app.create(contactClass, new NodeAttribute<>(AbstractNode.name, "contact06"), new NodeAttribute<>(numKey,    7));
			app.create(contactClass,                                                      new NodeAttribute<>(numKey,    6));
			app.create(contactClass, new NodeAttribute<>(AbstractNode.name, "contact08"), new NodeAttribute<>(numKey,    5));
			app.create(contactClass, new NodeAttribute<>(AbstractNode.name, "contact09"), new NodeAttribute<>(numKey, null));
			app.create(contactClass,                                                      new NodeAttribute<>(numKey,    3));
			app.create(contactClass, new NodeAttribute<>(AbstractNode.name, "contact11"), new NodeAttribute<>(numKey,    2));
			app.create(contactClass, new NodeAttribute<>(AbstractNode.name, "contact12"), new NodeAttribute<>(numKey,    1));
			tx.success();

		} catch (FrameworkException fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}

		try (final Tx tx = app.tx()) {

			Settings.CypherDebugLogging.setValue(true);

			final String query1               = "${find('Contact', not(empty('name')), sort('name'))}";
			final List<NodeInterface> result1 = (List)Scripting.evaluate(ctx, null, query1, "test1");

			final String query2               = "${find('Contact', not(empty('num')), sort('num'))}";
			final List<NodeInterface> result2 = (List)Scripting.evaluate(ctx, null, query2, "test2");

			Settings.CypherDebugLogging.setValue(false);

			// expected: 1, 2, 3, 5, 6, 8, 9, 11, 12
			assertEquals("Invalid result for advanced find with graph predicate", "contact01", result1.get(0).getProperty(AbstractNode.name));
			assertEquals("Invalid result for advanced find with graph predicate", "contact02", result1.get(1).getProperty(AbstractNode.name));
			assertEquals("Invalid result for advanced find with graph predicate", "contact03", result1.get(2).getProperty(AbstractNode.name));
			assertEquals("Invalid result for advanced find with graph predicate", "contact05", result1.get(3).getProperty(AbstractNode.name));
			assertEquals("Invalid result for advanced find with graph predicate", "contact06", result1.get(4).getProperty(AbstractNode.name));
			assertEquals("Invalid result for advanced find with graph predicate", "contact08", result1.get(5).getProperty(AbstractNode.name));
			assertEquals("Invalid result for advanced find with graph predicate", "contact09", result1.get(6).getProperty(AbstractNode.name));
			assertEquals("Invalid result for advanced find with graph predicate", "contact11", result1.get(7).getProperty(AbstractNode.name));
			assertEquals("Invalid result for advanced find with graph predicate", "contact12", result1.get(8).getProperty(AbstractNode.name));

			// expected: 12, 11, 9, 8, 7, 6, 5, 3, 2, 1
			assertEquals("Invalid result for advanced find with graph predicate",  1, result2.get(0).getProperty(numKey));
			assertEquals("Invalid result for advanced find with graph predicate",  2, result2.get(1).getProperty(numKey));
			assertEquals("Invalid result for advanced find with graph predicate",  3, result2.get(2).getProperty(numKey));
			assertEquals("Invalid result for advanced find with graph predicate",  5, result2.get(3).getProperty(numKey));
			assertEquals("Invalid result for advanced find with graph predicate",  6, result2.get(4).getProperty(numKey));
			assertEquals("Invalid result for advanced find with graph predicate",  7, result2.get(5).getProperty(numKey));
			assertEquals("Invalid result for advanced find with graph predicate",  8, result2.get(6).getProperty(numKey));
			assertEquals("Invalid result for advanced find with graph predicate",  9, result2.get(7).getProperty(numKey));
			assertEquals("Invalid result for advanced find with graph predicate", 11, result2.get(8).getProperty(numKey));
			assertEquals("Invalid result for advanced find with graph predicate", 12, result2.get(9).getProperty(numKey));

			tx.success();

		} catch (FrameworkException fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}
	}

	@Test
	public void testAdvancedFindNotEqualWithQuery() {

		final ActionContext ctx = new ActionContext(securityContext);

		// setup
		try (final Tx tx = app.tx()) {

			final JsonSchema schema          = StructrSchema.createEmptySchema();
			final JsonObjectType contact     = schema.addType("Contact");
			final JsonObjectType contactType = schema.addType("ContactType");

			contactType.relate(contact, "has", Cardinality.OneToMany, "contactType", "contacts");

			contact.addIntegerProperty("num").setIndexed(true);

			// add
			StructrSchema.extendDatabaseSchema(app, schema);

			tx.success();

		} catch (Throwable t) {

			t.printStackTrace();
			fail("Unexpected exception.");
		}

		final Class contactClass  = StructrApp.getConfiguration().getNodeEntityClass("Contact");
		final Class typeClass     = StructrApp.getConfiguration().getNodeEntityClass("ContactType");
		final PropertyKey typeKey = StructrApp.key(contactClass, "contactType");
		final PropertyKey numKey  = StructrApp.key(contactClass, "num");

		try (final Tx tx = app.tx()) {

			final NodeInterface type1 = app.create(typeClass, "type1");
			final NodeInterface type2 = app.create(typeClass, "type2");
			final NodeInterface type3 = app.create(typeClass, "type3");

			app.create(contactClass, new NodeAttribute<>(AbstractNode.name, "contact01"), new NodeAttribute<>(numKey,  1), new NodeAttribute<>(typeKey, type1));
			app.create(contactClass, new NodeAttribute<>(AbstractNode.name, "contact02"), new NodeAttribute<>(numKey,  2), new NodeAttribute<>(typeKey, type2)); // this
			app.create(contactClass, new NodeAttribute<>(AbstractNode.name, "contact03"), new NodeAttribute<>(numKey,  3), new NodeAttribute<>(typeKey, type2)); // this
			app.create(contactClass, new NodeAttribute<>(AbstractNode.name, "contact04"),                                  new NodeAttribute<>(typeKey, type1));
			app.create(contactClass, new NodeAttribute<>(AbstractNode.name, "contact05"),                                  new NodeAttribute<>(typeKey, type1));
			app.create(contactClass, new NodeAttribute<>(AbstractNode.name, "contact06"), new NodeAttribute<>(numKey,  6), new NodeAttribute<>(typeKey, type1));
			app.create(contactClass, new NodeAttribute<>(AbstractNode.name, "contact07"), new NodeAttribute<>(numKey,  7), new NodeAttribute<>(typeKey, type2)); // this
			app.create(contactClass, new NodeAttribute<>(AbstractNode.name, "contact08"), new NodeAttribute<>(numKey,  8), new NodeAttribute<>(typeKey, type2)); // this
			app.create(contactClass, new NodeAttribute<>(AbstractNode.name, "contact09"),                                  new NodeAttribute<>(typeKey, type3));
			app.create(contactClass, new NodeAttribute<>(AbstractNode.name, "contact10"),                                  new NodeAttribute<>(typeKey, type3));
			app.create(contactClass, new NodeAttribute<>(AbstractNode.name, "contact11"), new NodeAttribute<>(numKey, 12), new NodeAttribute<>(typeKey, type3)); // this
			app.create(contactClass, new NodeAttribute<>(AbstractNode.name, "contact12"), new NodeAttribute<>(numKey, 13), new NodeAttribute<>(typeKey, type3)); // this
			app.create(contactClass, new NodeAttribute<>(AbstractNode.name, "contact13"),                                  new NodeAttribute<>(typeKey, type1));
			app.create(contactClass, new NodeAttribute<>(AbstractNode.name, "contact14"), new NodeAttribute<>(numKey, 15), new NodeAttribute<>(typeKey, type2)); // this
			app.create(contactClass, new NodeAttribute<>(AbstractNode.name, "contact15"), new NodeAttribute<>(numKey, 16), new NodeAttribute<>(typeKey, type3)); // this
			app.create(contactClass, new NodeAttribute<>(AbstractNode.name, "contact16"), new NodeAttribute<>(numKey, 17), new NodeAttribute<>(typeKey, type1));
			app.create(contactClass, new NodeAttribute<>(AbstractNode.name, "contact17"), new NodeAttribute<>(numKey, 18), new NodeAttribute<>(typeKey, type2)); // this
			app.create(contactClass, new NodeAttribute<>(AbstractNode.name, "contact18"), new NodeAttribute<>(numKey, 19), new NodeAttribute<>(typeKey, type3)); // this
			app.create(contactClass, new NodeAttribute<>(AbstractNode.name, "contact19"), new NodeAttribute<>(numKey, 20), new NodeAttribute<>(typeKey, type1));

			tx.success();

		} catch (FrameworkException fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}

		try (final Tx tx = app.tx()) {

			Settings.CypherDebugLogging.setValue(true);

			final String query1               = "${find('Contact', and(not(empty('num')), not(equals('contactType', first(find('ContactType', 'name', 'type1'))))), sort('num', true), page(1, 20))}";
			final List<NodeInterface> result1 = (List)Scripting.evaluate(ctx, null, query1, "test1");

			Settings.CypherDebugLogging.setValue(false);

			// expected: 19, 18, 16, 15, 13, 12, 8, 7, 3, 2
			assertEquals("Invalid result for advanced find with graph predicate", 19, result1.get(0).getProperty(numKey));
			assertEquals("Invalid result for advanced find with graph predicate", 18, result1.get(1).getProperty(numKey));
			assertEquals("Invalid result for advanced find with graph predicate", 16, result1.get(2).getProperty(numKey));
			assertEquals("Invalid result for advanced find with graph predicate", 15, result1.get(3).getProperty(numKey));
			assertEquals("Invalid result for advanced find with graph predicate", 13, result1.get(4).getProperty(numKey));
			assertEquals("Invalid result for advanced find with graph predicate", 12, result1.get(5).getProperty(numKey));
			assertEquals("Invalid result for advanced find with graph predicate",  8, result1.get(6).getProperty(numKey));
			assertEquals("Invalid result for advanced find with graph predicate",  7, result1.get(7).getProperty(numKey));
			assertEquals("Invalid result for advanced find with graph predicate",  3, result1.get(8).getProperty(numKey));
			assertEquals("Invalid result for advanced find with graph predicate",  2, result1.get(9).getProperty(numKey));

			tx.success();

		} catch (FrameworkException fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}
	}

	@Test
	public void testLoggingOfGraphObjects() {

		final ActionContext ctx = new ActionContext(securityContext);

		// setup
		try (final Tx tx = app.tx()) {

			app.create(Group.class, "group1");
			app.create(Group.class, "group2");

			tx.success();

		} catch (FrameworkException fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}

		try (final Tx tx = app.tx()) {

			final List<Group> groups = app.nodeQuery(Group.class).getAsList();

			assertEquals("Invalid number of groups in test setup", 2, groups.size());

			final Group group1    = groups.get(0);
			final Group group2    = groups.get(1);
			final String expected = "[" + group1.getUuid() + ", " + group2.getUuid() + "]";

			assertEquals("Invalid print output", expected,         Scripting.evaluate(ctx, group1, "${print(find('Group'))}", "test1"));
			assertEquals("Invalid print output", group1.getUuid(), Scripting.evaluate(ctx, group1, "${print(this)}", "test1"));
			assertEquals("Invalid print output", "", Scripting.evaluate(ctx, group2, "${log(this)}", "test2"));

			tx.success();

		} catch (FrameworkException fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}
	}

	@Test
	public void testAssignmentOfStringArrayProperties() {

		final ActionContext ctx = new ActionContext(securityContext);

		// setup
		try (final Tx tx = app.tx()) {

			final JsonSchema schema = StructrSchema.createFromDatabase(app);
			final JsonType type     = schema.addType("Test");

			type.addStringArrayProperty("test");

			StructrSchema.extendDatabaseSchema(app, schema);

			tx.success();

		} catch (Throwable t) {

			t.printStackTrace();
			fail("Unexpected exception.");
		}

		// setup 2
		try (final Tx tx = app.tx()) {

			final Class type      = StructrApp.getConfiguration().getNodeEntityClass("Test");
			final PropertyKey key = StructrApp.key(type, "test");

			app.create(type,
				new NodeAttribute<>(AbstractNode.name, "source"),
				new NodeAttribute<>(key, new String[] { "one", "two", "three" })
			);

			app.create(type,
				new NodeAttribute<>(AbstractNode.name, "target")
			);

			tx.success();

		} catch (FrameworkException fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}

		// test
		try (final Tx tx = app.tx()) {

			Scripting.evaluate(ctx, null, "${{ var source = $.find('Test', { name: 'source' })[0]; var target = $.find('Test', { name: 'target' })[0]; target.test = source.test; }}", "test");

			tx.success();

		} catch (FrameworkException fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}
	}

	@Test
	public void testEmptyArrayProperty() {

		try (final Tx tx = app.tx()) {

			final JsonSchema schema = StructrSchema.createFromDatabase(app);

			final JsonType testType = schema.addType("TestEmptyArrayPropertyType");
			testType.addStringArrayProperty("arr");

			StructrSchema.extendDatabaseSchema(app, schema);

			tx.success();

		} catch (FrameworkException ex) {
			fail();
		}


		try (final Tx tx = app.tx()) {

			final Class clazz = StructrApp.getConfiguration().getNodeEntityClass("TestEmptyArrayPropertyType");

			NodeInterface node = app.create(clazz);

			final ActionContext ac = new ActionContext(securityContext);

			Scripting.evaluate(ac, node, "${{Structr.get('this').arr.push('test');}}", null);

			String[] arr = (String[])node.getProperty(StructrApp.getConfiguration().getPropertyKeyForDatabaseName(clazz, "arr"));

			Assert.assertEquals(1, arr.length);

		} catch (FrameworkException ex) {

			fail();
		}
	}

	@Test
	public void testDifferentArrayPropertyAssignmentAndPush() {

		try (final Tx tx = app.tx()) {

			final JsonSchema schema = StructrSchema.createFromDatabase(app);

			final JsonType testType = schema.addType("ArrayPropertiesTest");
			testType.addStringArrayProperty("strings");
			testType.addIntegerArrayProperty("ints");
			testType.addDoubleArrayProperty("doubles");
			testType.addLongArrayProperty("longs");
			testType.addBooleanArrayProperty("bools");
			testType.addDateArrayProperty("dates");

			StructrSchema.extendDatabaseSchema(app, schema);

			tx.success();

		} catch (FrameworkException ex) {
			fail();
		}


		/**
		 * Test using .push() on the empty object
		 */
		try (final Tx tx = app.tx()) {

			final ActionContext ac = new ActionContext(securityContext);

			Scripting.evaluate(ac, null, "${{"
					+ "let n = $.create('ArrayPropertiesTest', {name: 'emptyTest'});"
					+ "}}", null);

			Scripting.evaluate(ac, null, "${{"
					+ "let n = $.create('ArrayPropertiesTest', {name: 'emptyPushTest'});"
					+ "n.strings.push('test');"
					+ "n.ints.push(42);"
					+ "n.doubles.push(42);"
					+ "n.longs.push(42);"
					+ "n.bools.push(true);"
					+ "n.dates.push(new Date());"
					+ "}}", null);

			Scripting.evaluate(ac, null, "${{"
					+ "let n = $.create('ArrayPropertiesTest', {name: 'emptySetTest'});"
					+ "n.strings = ['a', 'b', 'c'];"
					+ "n.ints = [3, 4, 5];"
					+ "n.doubles = [3.14, 4, 5.05];"
					+ "n.longs = [3, 4, 5];"
					+ "n.bools = [true, false, true];"
					+ "n.dates = [new Date(), $.get('now'), new Date()];"
					+ "}}", null);

			Scripting.evaluate(ac, null, "${{"
					+ "let n = $.create('ArrayPropertiesTest', {name: 'setAndPushTest'});"
					+ "n.strings = ['a', 'b', 'c'];"
					+ "n.strings.push('d');"
					+ "n.ints = [3, 4, 5];"
					+ "n.ints.push(6);"
					+ "n.doubles = [3.14, 4, 5.05];"
					+ "n.doubles.push(6);"
					+ "n.longs = [3, 4, 5];"
					+ "n.longs.push(6);"
					+ "n.bools = [true, false, true];"
					+ "n.bools.push(false);"
					+ "n.dates = [new Date(), $.get('now'), new Date()];"
					+ "n.dates.push(new Date());"
					+ "}}", null);

			tx.success();

		} catch (FrameworkException ex) {
			fail();
		}

		// test
		try (final Tx tx = app.tx()) {

			final ActionContext ac = new ActionContext(securityContext);
			assertEquals("All array properties should return 0 length", "000000",                                         Scripting.evaluate(ac, null, "${{ let n = $.find('ArrayPropertiesTest', { name: 'emptyTest' })[0]; $.print($.int(n.strings.length), $.int(n.ints.length), $.int(n.doubles.length), $.int(n.longs.length), $.int(n.bools.length), $.int(n.dates.length)); }}", "emptyPushTest"));
			assertEquals("All array properties should return 1 length after one push()", "111111",                        Scripting.evaluate(ac, null, "${{ let n = $.find('ArrayPropertiesTest', { name: 'emptyPushTest' })[0]; $.print($.int(n.strings.length), $.int(n.ints.length), $.int(n.doubles.length), $.int(n.longs.length), $.int(n.bools.length), $.int(n.dates.length)); }}", "emptyPushTest"));
			assertEquals("All array properties should return 3 length after assignment of [x,y,z]", "333333",             Scripting.evaluate(ac, null, "${{ let n = $.find('ArrayPropertiesTest', { name: 'emptySetTest' })[0]; $.print($.int(n.strings.length), $.int(n.ints.length), $.int(n.doubles.length), $.int(n.longs.length), $.int(n.bools.length), $.int(n.dates.length)); }}", "emptyPushTest"));
			assertEquals("All array properties should return 4 length after assignment of [x,y,z] plus push()", "444444", Scripting.evaluate(ac, null, "${{ let n = $.find('ArrayPropertiesTest', { name: 'setAndPushTest' })[0]; $.print($.int(n.strings.length), $.int(n.ints.length), $.int(n.doubles.length), $.int(n.longs.length), $.int(n.bools.length), $.int(n.dates.length)); }}", "emptyPushTest"));

			tx.success();

		} catch (FrameworkException fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}
	}

	@Test
	public void testAdvancedFindNamespaceHandlingWithException() {

		/*
		 * This test verifies that the find() namespace is correctly exited even when an exception
		 * occurs inside of a find() function call (e.g. caused by a wrongly typed search parameter).
		 */

		final ActionContext ctx = new ActionContext(securityContext);

		// setup
		try (final Tx tx = app.tx()) {

			final JsonSchema schema = StructrSchema.createFromDatabase(app);
			final JsonType type     = schema.addType("Test");

			type.addIntegerProperty("test");

			StructrSchema.extendDatabaseSchema(app, schema);

			tx.success();

		} catch (Throwable t) {

			t.printStackTrace();
			fail("Unexpected exception.");
		}

		// test
		try (final Tx tx = app.tx()) {

			Scripting.evaluate(ctx, null, "${{ var source = $.find('Test', { test: 'error' }); let test = $.empty(source); $.store('result', test); }}", "test");

			tx.success();

		} catch (FrameworkException fex) {

			assertEquals("Wrong error code for exception inside of advanced find() context.",   422, fex.getStatus());
			assertEquals("Wrong error message for exception inside of advanced find() context", "Cannot parse input error for property test", fex.getMessage());
		}

		// expected result is boolean "true" since the empty() function call checks the result of the erroneous find()
		assertEquals("Advanced find() namespace not exited correctly", true, securityContext.getContextStore().retrieve("result"));

	}

	@Test
	public void testStructrScriptArrayIndexingWithVariable() {

		final ActionContext ctx  = new ActionContext(securityContext);
		final List<Group> groups = new LinkedList<>();

		// setup
		try (final Tx tx = app.tx()) {

			groups.add(app.create(Group.class, "group4"));
			groups.add(app.create(Group.class, "group2"));
			groups.add(app.create(Group.class, "group1"));
			groups.add(app.create(Group.class, "group5"));
			groups.add(app.create(Group.class, "group7"));
			groups.add(app.create(Group.class, "group6"));
			groups.add(app.create(Group.class, "group3"));
			groups.add(app.create(Group.class, "group8"));

			tx.success();

		} catch (FrameworkException fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}

		try (final Tx tx = app.tx()) {

			final Group group1 = groups.get(2);

			ctx.setConstant("index1", 3);
			ctx.setConstant("index2", "3");


			assertEquals("StructrScript array indexing returns wrong result",   group1, Scripting.evaluate(ctx, null, "${find('Group', sort('name'))[0]}", "test1"));
			assertEquals("StructrScript array indexing returns wrong result", "group2", Scripting.evaluate(ctx, null, "${find('Group', sort('name'))[1].name}", "test1"));
			assertEquals("StructrScript array indexing returns wrong result", "group4", Scripting.evaluate(ctx, null, "${find('Group', sort('name'))[index1].name}", "test1"));
			assertEquals("StructrScript array indexing returns wrong result", "group4", Scripting.evaluate(ctx, null, "${find('Group', sort('name'))[index2].name}", "test1"));

			// FIXME: this test fails because [] binds to the wrong expression
			//final List<Group> check1 = groups.subList(3, 4);
			//assertEquals("StructrScript array indexing returns wrong result",   check1, Scripting.evaluate(ctx, null, "${merge(find('Group', sort('name'))[index2])}", "test1"));

			tx.success();

		} catch (FrameworkException fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}
	}

	@Test
	public void testSortingByMultipleKeysInJavascript() {

		final ActionContext ctx = new ActionContext(securityContext);

		try (final Tx tx = app.tx()) {

			app.create(TestOne.class, new NodeAttribute<>(AbstractNode.name, "name7"), new NodeAttribute<>(TestOne.anInt, 3), new NodeAttribute<>(TestOne.aLong, 20L));
			app.create(TestOne.class, new NodeAttribute<>(AbstractNode.name, "name5"), new NodeAttribute<>(TestOne.anInt, 2), new NodeAttribute<>(TestOne.aLong, 20L));
			app.create(TestOne.class, new NodeAttribute<>(AbstractNode.name, "name2"), new NodeAttribute<>(TestOne.anInt, 1), new NodeAttribute<>(TestOne.aLong, 20L));
			app.create(TestOne.class, new NodeAttribute<>(AbstractNode.name, "name1"), new NodeAttribute<>(TestOne.anInt, 3), new NodeAttribute<>(TestOne.aLong, 20L));
			app.create(TestOne.class, new NodeAttribute<>(AbstractNode.name, "name3"), new NodeAttribute<>(TestOne.anInt, 2), new NodeAttribute<>(TestOne.aLong, 20L));
			app.create(TestOne.class, new NodeAttribute<>(AbstractNode.name, "name4"), new NodeAttribute<>(TestOne.anInt, 1), new NodeAttribute<>(TestOne.aLong, 10L));
			app.create(TestOne.class, new NodeAttribute<>(AbstractNode.name, "name9"), new NodeAttribute<>(TestOne.anInt, 3), new NodeAttribute<>(TestOne.aLong, 10L));
			app.create(TestOne.class, new NodeAttribute<>(AbstractNode.name, "name8"), new NodeAttribute<>(TestOne.anInt, 2), new NodeAttribute<>(TestOne.aLong, 10L));
			app.create(TestOne.class, new NodeAttribute<>(AbstractNode.name, "name6"), new NodeAttribute<>(TestOne.anInt, 1), new NodeAttribute<>(TestOne.aLong, 10L));

			tx.success();

		} catch (FrameworkException fex) {
			fail("Unexpected exception.");
		}

		try (final Tx tx = app.tx()) {

			//final List<TestOne> result1 = app.nodeQuery(TestOne.class).sort(TestOne.aLong).sort(TestOne.name).getAsList();
			final List<TestOne> result1 = (List)Scripting.evaluate(ctx, null, "${{ return $.find('TestOne', $.predicate.sort('aLong'), $.predicate.sort('name'))}}", "test1");

			assertEquals("Sorting by multiple keys returns wrong result", "name4", result1.get(0).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name6", result1.get(1).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name8", result1.get(2).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name9", result1.get(3).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name1", result1.get(4).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name2", result1.get(5).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name3", result1.get(6).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name5", result1.get(7).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name7", result1.get(8).getName());

			//final List<TestOne> result2 = app.nodeQuery(TestOne.class).sort(TestOne.aLong, true).sort(TestOne.name).getAsList();
			final List<TestOne> result2 = (List)Scripting.evaluate(ctx, null, "${{ return $.find('TestOne', $.predicate.sort('aLong', true), $.predicate.sort('name'))}}", "test2");

			assertEquals("Sorting by multiple keys returns wrong result", "name1", result2.get(0).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name2", result2.get(1).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name3", result2.get(2).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name5", result2.get(3).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name7", result2.get(4).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name4", result2.get(5).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name6", result2.get(6).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name8", result2.get(7).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name9", result2.get(8).getName());

			//final List<TestOne> result3 = app.nodeQuery(TestOne.class).sort(TestOne.aLong).sort(TestOne.name, true).getAsList();
			final List<TestOne> result3 = (List)Scripting.evaluate(ctx, null, "${{ return $.find('TestOne', $.predicate.sort('aLong'), $.predicate.sort('name', true))}}", "test3");

			assertEquals("Sorting by multiple keys returns wrong result", "name9", result3.get(0).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name8", result3.get(1).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name6", result3.get(2).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name4", result3.get(3).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name7", result3.get(4).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name5", result3.get(5).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name3", result3.get(6).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name2", result3.get(7).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name1", result3.get(8).getName());

			//final List<TestOne> result4 = app.nodeQuery(TestOne.class).sort(TestOne.aLong).sort(TestOne.anInt).sort(TestOne.name).getAsList();
			final List<TestOne> result4 = (List)Scripting.evaluate(ctx, null, "${{ return $.find('TestOne', $.predicate.sort('aLong'), $.predicate.sort('anInt'), $.predicate.sort('name'))}}", "test4");

			assertEquals("Sorting by multiple keys returns wrong result", "name4", result4.get(0).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name6", result4.get(1).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name8", result4.get(2).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name9", result4.get(3).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name2", result4.get(4).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name3", result4.get(5).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name5", result4.get(6).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name1", result4.get(7).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name7", result4.get(8).getName());

			//final List<TestOne> result5 = app.nodeQuery(TestOne.class).sort(TestOne.aLong).sort(TestOne.anInt, true).sort(TestOne.name).getAsList();
			final List<TestOne> result5 = (List)Scripting.evaluate(ctx, null, "${{ return $.find('TestOne', $.predicate.sort('aLong'), $.predicate.sort('anInt', true), $.predicate.sort('name'))}}", "test5");

			assertEquals("Sorting by multiple keys returns wrong result", "name9", result5.get(0).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name8", result5.get(1).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name4", result5.get(2).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name6", result5.get(3).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name1", result5.get(4).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name7", result5.get(5).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name3", result5.get(6).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name5", result5.get(7).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name2", result5.get(8).getName());

			tx.success();

		} catch (FrameworkException fex) {
			fex.printStackTrace();
			System.out.println(fex.getMessage());
			fail("Unexpected exception.");
		}
	}

	@Test
	public void testSortingByMultipleKeysInStructrScriptAdvancedFind() {

		final ActionContext ctx = new ActionContext(securityContext);

		try (final Tx tx = app.tx()) {

			app.create(TestOne.class, new NodeAttribute<>(AbstractNode.name, "name7"), new NodeAttribute<>(TestOne.anInt, 3), new NodeAttribute<>(TestOne.aLong, 20L));
			app.create(TestOne.class, new NodeAttribute<>(AbstractNode.name, "name5"), new NodeAttribute<>(TestOne.anInt, 2), new NodeAttribute<>(TestOne.aLong, 20L));
			app.create(TestOne.class, new NodeAttribute<>(AbstractNode.name, "name2"), new NodeAttribute<>(TestOne.anInt, 1), new NodeAttribute<>(TestOne.aLong, 20L));
			app.create(TestOne.class, new NodeAttribute<>(AbstractNode.name, "name1"), new NodeAttribute<>(TestOne.anInt, 3), new NodeAttribute<>(TestOne.aLong, 20L));
			app.create(TestOne.class, new NodeAttribute<>(AbstractNode.name, "name3"), new NodeAttribute<>(TestOne.anInt, 2), new NodeAttribute<>(TestOne.aLong, 20L));
			app.create(TestOne.class, new NodeAttribute<>(AbstractNode.name, "name4"), new NodeAttribute<>(TestOne.anInt, 1), new NodeAttribute<>(TestOne.aLong, 10L));
			app.create(TestOne.class, new NodeAttribute<>(AbstractNode.name, "name9"), new NodeAttribute<>(TestOne.anInt, 3), new NodeAttribute<>(TestOne.aLong, 10L));
			app.create(TestOne.class, new NodeAttribute<>(AbstractNode.name, "name8"), new NodeAttribute<>(TestOne.anInt, 2), new NodeAttribute<>(TestOne.aLong, 10L));
			app.create(TestOne.class, new NodeAttribute<>(AbstractNode.name, "name6"), new NodeAttribute<>(TestOne.anInt, 1), new NodeAttribute<>(TestOne.aLong, 10L));

			tx.success();

		} catch (FrameworkException fex) {
			fail("Unexpected exception.");
		}

		try (final Tx tx = app.tx()) {

			//final List<TestOne> result1 = app.nodeQuery(TestOne.class).sort(TestOne.aLong).sort(TestOne.name).getAsList();
			final List<TestOne> result1 = (List)Scripting.evaluate(ctx, null, "${find('TestOne', sort('aLong'), sort('name'))}", "test1");

			assertEquals("Sorting by multiple keys returns wrong result", "name4", result1.get(0).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name6", result1.get(1).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name8", result1.get(2).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name9", result1.get(3).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name1", result1.get(4).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name2", result1.get(5).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name3", result1.get(6).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name5", result1.get(7).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name7", result1.get(8).getName());

			//final List<TestOne> result2 = app.nodeQuery(TestOne.class).sort(TestOne.aLong, true).sort(TestOne.name).getAsList();
			final List<TestOne> result2 = (List)Scripting.evaluate(ctx, null, "${find('TestOne', sort('aLong', true), sort('name'))}", "test2");

			assertEquals("Sorting by multiple keys returns wrong result", "name1", result2.get(0).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name2", result2.get(1).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name3", result2.get(2).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name5", result2.get(3).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name7", result2.get(4).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name4", result2.get(5).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name6", result2.get(6).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name8", result2.get(7).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name9", result2.get(8).getName());

			//final List<TestOne> result3 = app.nodeQuery(TestOne.class).sort(TestOne.aLong).sort(TestOne.name, true).getAsList();
			final List<TestOne> result3 = (List)Scripting.evaluate(ctx, null, "${find('TestOne', sort('aLong'), sort('name', true))}", "test3");

			assertEquals("Sorting by multiple keys returns wrong result", "name9", result3.get(0).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name8", result3.get(1).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name6", result3.get(2).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name4", result3.get(3).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name7", result3.get(4).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name5", result3.get(5).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name3", result3.get(6).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name2", result3.get(7).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name1", result3.get(8).getName());

			//final List<TestOne> result4 = app.nodeQuery(TestOne.class).sort(TestOne.aLong).sort(TestOne.anInt).sort(TestOne.name).getAsList();
			final List<TestOne> result4 = (List)Scripting.evaluate(ctx, null, "${find('TestOne', sort('aLong'), sort('anInt'), sort('name'))}", "test4");

			assertEquals("Sorting by multiple keys returns wrong result", "name4", result4.get(0).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name6", result4.get(1).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name8", result4.get(2).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name9", result4.get(3).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name2", result4.get(4).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name3", result4.get(5).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name5", result4.get(6).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name1", result4.get(7).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name7", result4.get(8).getName());

			//final List<TestOne> result5 = app.nodeQuery(TestOne.class).sort(TestOne.aLong).sort(TestOne.anInt, true).sort(TestOne.name).getAsList();
			final List<TestOne> result5 = (List)Scripting.evaluate(ctx, null, "${find('TestOne', sort('aLong'), sort('anInt', true), sort('name'))}", "test5");

			assertEquals("Sorting by multiple keys returns wrong result", "name9", result5.get(0).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name8", result5.get(1).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name4", result5.get(2).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name6", result5.get(3).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name1", result5.get(4).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name7", result5.get(5).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name3", result5.get(6).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name5", result5.get(7).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name2", result5.get(8).getName());

			tx.success();

		} catch (FrameworkException fex) {
			fex.printStackTrace();
			System.out.println(fex.getMessage());
			fail("Unexpected exception.");
		}
	}

	@Test
	public void testSortingByMultipleKeysInStructrScriptNormalFind() {

		final ActionContext ctx = new ActionContext(securityContext);

		try (final Tx tx = app.tx()) {

			app.create(TestOne.class, new NodeAttribute<>(AbstractNode.name, "name7"), new NodeAttribute<>(TestOne.anInt, 3), new NodeAttribute<>(TestOne.aLong, 20L));
			app.create(TestOne.class, new NodeAttribute<>(AbstractNode.name, "name5"), new NodeAttribute<>(TestOne.anInt, 2), new NodeAttribute<>(TestOne.aLong, 20L));
			app.create(TestOne.class, new NodeAttribute<>(AbstractNode.name, "name2"), new NodeAttribute<>(TestOne.anInt, 1), new NodeAttribute<>(TestOne.aLong, 20L));
			app.create(TestOne.class, new NodeAttribute<>(AbstractNode.name, "name1"), new NodeAttribute<>(TestOne.anInt, 3), new NodeAttribute<>(TestOne.aLong, 20L));
			app.create(TestOne.class, new NodeAttribute<>(AbstractNode.name, "name3"), new NodeAttribute<>(TestOne.anInt, 2), new NodeAttribute<>(TestOne.aLong, 20L));
			app.create(TestOne.class, new NodeAttribute<>(AbstractNode.name, "name4"), new NodeAttribute<>(TestOne.anInt, 1), new NodeAttribute<>(TestOne.aLong, 10L));
			app.create(TestOne.class, new NodeAttribute<>(AbstractNode.name, "name9"), new NodeAttribute<>(TestOne.anInt, 3), new NodeAttribute<>(TestOne.aLong, 10L));
			app.create(TestOne.class, new NodeAttribute<>(AbstractNode.name, "name8"), new NodeAttribute<>(TestOne.anInt, 2), new NodeAttribute<>(TestOne.aLong, 10L));
			app.create(TestOne.class, new NodeAttribute<>(AbstractNode.name, "name6"), new NodeAttribute<>(TestOne.anInt, 1), new NodeAttribute<>(TestOne.aLong, 10L));

			tx.success();

		} catch (FrameworkException fex) {
			fail("Unexpected exception.");
		}

		try (final Tx tx = app.tx()) {

			//final List<TestOne> result1 = app.nodeQuery(TestOne.class).sort(TestOne.aLong).sort(TestOne.name).getAsList();
			final List<TestOne> result1 = (List)Scripting.evaluate(ctx, null, "${sort(find('TestOne') 'aLong', false, 'name', false)}", "test1");

			assertEquals("Sorting by multiple keys returns wrong result", "name4", result1.get(0).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name6", result1.get(1).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name8", result1.get(2).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name9", result1.get(3).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name1", result1.get(4).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name2", result1.get(5).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name3", result1.get(6).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name5", result1.get(7).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name7", result1.get(8).getName());

			//final List<TestOne> result2 = app.nodeQuery(TestOne.class).sort(TestOne.aLong, true).sort(TestOne.name).getAsList();
			final List<TestOne> result2 = (List)Scripting.evaluate(ctx, null, "${sort(find('TestOne'), 'aLong', true, 'name')}", "test2");

			assertEquals("Sorting by multiple keys returns wrong result", "name1", result2.get(0).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name2", result2.get(1).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name3", result2.get(2).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name5", result2.get(3).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name7", result2.get(4).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name4", result2.get(5).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name6", result2.get(6).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name8", result2.get(7).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name9", result2.get(8).getName());

			//final List<TestOne> result3 = app.nodeQuery(TestOne.class).sort(TestOne.aLong).sort(TestOne.name, true).getAsList();
			final List<TestOne> result3 = (List)Scripting.evaluate(ctx, null, "${sort(find('TestOne'), 'aLong', false, 'name', true)}", "test3");

			assertEquals("Sorting by multiple keys returns wrong result", "name9", result3.get(0).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name8", result3.get(1).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name6", result3.get(2).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name4", result3.get(3).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name7", result3.get(4).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name5", result3.get(5).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name3", result3.get(6).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name2", result3.get(7).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name1", result3.get(8).getName());

			//final List<TestOne> result4 = app.nodeQuery(TestOne.class).sort(TestOne.aLong).sort(TestOne.anInt).sort(TestOne.name).getAsList();
			final List<TestOne> result4 = (List)Scripting.evaluate(ctx, null, "${sort(find('TestOne'), 'aLong', false, 'anInt', false, 'name', false)}", "test4");

			assertEquals("Sorting by multiple keys returns wrong result", "name4", result4.get(0).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name6", result4.get(1).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name8", result4.get(2).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name9", result4.get(3).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name2", result4.get(4).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name3", result4.get(5).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name5", result4.get(6).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name1", result4.get(7).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name7", result4.get(8).getName());

			//final List<TestOne> result5 = app.nodeQuery(TestOne.class).sort(TestOne.aLong).sort(TestOne.anInt, true).sort(TestOne.name).getAsList();
			final List<TestOne> result5 = (List)Scripting.evaluate(ctx, null, "${sort(find('TestOne'), 'aLong', false, 'anInt', true, 'name')}", "test5");

			assertEquals("Sorting by multiple keys returns wrong result", "name9", result5.get(0).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name8", result5.get(1).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name4", result5.get(2).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name6", result5.get(3).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name1", result5.get(4).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name7", result5.get(5).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name3", result5.get(6).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name5", result5.get(7).getName());
			assertEquals("Sorting by multiple keys returns wrong result", "name2", result5.get(8).getName());

			tx.success();

		} catch (FrameworkException fex) {
			fex.printStackTrace();
			System.out.println(fex.getMessage());
			fail("Unexpected exception.");
		}
	}

	@Test
	public void testComments() {

		/*
		 * This test verifies that comments in JavaScript blocks are detected and interpreded correctly.
		 */

		final ActionContext ctx = new ActionContext(securityContext);

		// test
		try (final Tx tx = app.tx()) {

			final Object result = Scripting.evaluate(ctx, null, "${{\n\n\t$.log('Testing');\n\n\t/*}*/\n\n}}", "test");

			System.out.println("'" + result + "'");

			tx.success();

		} catch (FrameworkException fex) {

			assertEquals("Wrong error code for exception inside of advanced find() context.",   422, fex.getStatus());
			assertEquals("Wrong error message for exception inside of advanced find() context", "Cannot parse input error for property test", fex.getMessage());
		}

	}

	@Test
	public void testJavaScriptQuirksDuckTypingNumericalMapIndex () {

		/*
			This test makes sure that map access works even though javascript interprets numerical strings (e.g. "1", "25") as ints (after the map has undergone wrapping/unwrapping
		*/

		final ActionContext ctx = new ActionContext(securityContext);

		// test
		try (final Tx tx = app.tx()) {

			final Object result = ScriptTestHelper.testExternalScript(ctx, ScriptingTest.class.getResourceAsStream("/test/scripting/testJavaScriptQuirksDuckTypingNumericalMapIndex.js"));

			assertEquals("Result should not be undefined! Access to maps at numerical indexes should work.", true, result);

			tx.success();

		} catch (FrameworkException fex) {

			fail("Unexpected exception");
		}

	}

	@Test
	public void testHMCAFunction () {
		/*
			This test ensures that the core function hmac() returns the correct HEX String for the given values.
		*/

		final ActionContext ctx = new ActionContext(securityContext);

		// test
		try (final Tx tx = app.tx()) {

			final String resultSHA256 = (String) ScriptTestHelper.testExternalScript(ctx, ScriptingTest.class.getResourceAsStream("/test/scripting/testHMACFunctionSHA256.js"));
			assertEquals("Result does match the expected SHA256 hmac", "88cd2108b5347d973cf39cdf9053d7dd42704876d8c9a9bd8e2d168259d3ddf7", resultSHA256);

			final String resultMD5 = (String) ScriptTestHelper.testExternalScript(ctx, ScriptingTest.class.getResourceAsStream("/test/scripting/testHMACFunctionMD5.js"));
			assertEquals("Result does match the expected MD5 hmac", "cd4b0dcbe0f4538b979fb73664f51abe", resultMD5);

			tx.success();

		} catch (FrameworkException fex) {
			logger.warn("", fex);

			fail("Unexpected exception");
		}
	}

	@Test
	public void testSlice() {

		final ActionContext ctx           = new ActionContext(securityContext);
		final List<String> testSixNames   = new LinkedList<>();
		TestOne testOne                   = null;
		List<TestSix> testSixs            = null;
		int index                         = 0;

		try (final Tx tx = app.tx()) {

			testOne        = createTestNode(TestOne.class);
			testSixs       = createTestNodes(TestSix.class, 20, 1);

			final Calendar cal = GregorianCalendar.getInstance();

			// set calendar to 2018-01-01T00:00:00+0000
			cal.set(2018, 0, 1, 0, 0, 0);

			for (final TestSix testSix : testSixs) {

				final String name = "TestSix" + StringUtils.leftPad(Integer.toString(index), 2, "0");

				testSix.setProperty(TestSix.name, name);
				testSix.setProperty(TestSix.index, index);
				testSix.setProperty(TestSix.date, cal.getTime());

				index++;
				cal.add(Calendar.DAY_OF_YEAR, 3);

				// build list of names
				testSixNames.add(name);
			}

			testOne.setProperty(TestOne.manyToManyTestSixs, testSixs);

			tx.success();

		} catch (FrameworkException fex) {
			fex.printStackTrace();
		}

		try (final Tx tx = app.tx()) {

			// slice
			final Object sliceResult = Scripting.evaluate(ctx, testOne, "${slice(this.manyToManyTestSixs, 0, 5)}", "slice test");
			assertTrue("Invalid slice() result, must return collection for valid results", sliceResult instanceof Collection);
			assertTrue("Invalid slice() result, must return list for valid results", sliceResult instanceof List);
			final List sliceResultList = (List)sliceResult;
			assertEquals("Invalid slice() result, must return a list of 5 objects", 5, sliceResultList.size());

			// slice with find
			final Object sliceWithFindResult = Scripting.evaluate(ctx, null, "${slice(find('TestSix'), 0, 2)}", "slice test with find");
			assertTrue("Invalid slice() result, must return collection for valid results", sliceWithFindResult instanceof Collection);
			assertTrue("Invalid slice() result, must return list for valid results", sliceWithFindResult instanceof List);
			final List sliceWithFindResultList = (List)sliceWithFindResult;
			assertEquals("Invalid slice() result, must return a list of 2 object", 2, sliceWithFindResultList.size());

			// slice with find JS
			//final Object sliceWithFindJSResult = Scripting.evaluate(ctx, null, "${{ return $.slice(function() { return $.find('TestSix') }, 0, 2); }}", "slice test with find in JS");
			//assertTrue("Invalid slice() result, must return collection for valid results", sliceWithFindJSResult instanceof Collection);
			//assertTrue("Invalid slice() result, must return list for valid results", sliceWithFindJSResult instanceof List);
			//final List sliceWithFindJSResultList = (List)sliceWithFindJSResult;
			//assertEquals("Invalid slice() result, must return a list of 2 objects", 2, sliceWithFindJSResultList.size());

			// test error cases
			assertEquals("Invalid slice() result for invalid inputs", "", Scripting.replaceVariables(ctx, testOne, "${slice(this.alwaysNull, 1, 2)}"));
			assertEquals("Invalid slice() result for invalid inputs", "", Scripting.replaceVariables(ctx, testOne, "${slice(this.manyToManyTestSixs, -1, 1)}"));
			assertEquals("Invalid slice() result for invalid inputs", "", Scripting.replaceVariables(ctx, testOne, "${slice(this.manyToManyTestSixs, 2, 1)}"));

			// test with interval larger than number of elements
			assertEquals("Invalid slice() result for invalid inputs",
				Iterables.toList(testOne.getProperty(TestOne.manyToManyTestSixs)).toString(),
				Scripting.replaceVariables(ctx, testOne, "${slice(this.manyToManyTestSixs, 0, 1000)}")
			);

			tx.success();

		} catch (FrameworkException fex) {

			fex.printStackTrace();
		}
	}

	@Test
	public void testPolyglotArraySorting () {

		/*
			Ensure that PolyglotProxyArray entries can be properly set by ensuring the proper function of sort on a proxy array.
		*/

		final ActionContext ctx = new ActionContext(securityContext);

		try (final Tx tx = app.tx()) {

			final Object result = ScriptTestHelper.testExternalScript(ctx, ScriptingTest.class.getResourceAsStream("/test/scripting/testPolyglotArraySorting.js"));

			assertNotNull(result);
			assertTrue(result instanceof List);
			List resultList = (List)result;

			assertEquals(resultList.size(), 10);
			assertTrue(resultList.get(0) instanceof TestOne);
			assertEquals(((TestOne)resultList.get(0)).getName(), "TestOne9");

			tx.success();

		} catch (FrameworkException fex) {

			fail("Unexpected exception");
		}

	}

	@Test
	public void testContextStorePollutionInSchemaMethodCall() {

		// setup
		try (final Tx tx = app.tx()) {

			final JsonSchema schema = StructrSchema.createFromDatabase(app);
			final JsonType project  = schema.addType("ContextTest");

			project.addIntegerProperty("result");

			final JsonFunctionProperty p1 = project.addFunctionProperty("input1");
			final JsonFunctionProperty p2 = project.addFunctionProperty("input2");

			p1.setTypeHint("int");
			p2.setTypeHint("int");

			p1.setWriteFunction("{$.this.doTest($.get('value'));}");
			p2.setWriteFunction("{$.this.doTest($.get('value'));}");

			project.addMethod("doTest", "{ $.this.result =  $.retrieve('key1') + $.retrieve('key2'); }", "");

			StructrSchema.extendDatabaseSchema(app, schema);

			tx.success();

		} catch (Throwable fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}

		final Class type       = StructrApp.getConfiguration().getNodeEntityClass("ContextTest");
		final PropertyKey key1 = StructrApp.key(type, "input1");
		final PropertyKey key2 = StructrApp.key(type, "input2");

		// test
		try (final Tx tx = app.tx()) {

			Map<String, Object> p1 = Map.ofEntries(new AbstractMap.SimpleEntry<>("key1", 1));
			Map<String, Object> p2 = Map.ofEntries(new AbstractMap.SimpleEntry<>("key2", 1));

			app.create(type,
					new NodeAttribute<>(key1, p1),
					new NodeAttribute<>(key2, p2)
			);

			tx.success();

		} catch (Throwable fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}

		// check result
		try (final Tx tx = app.tx()) {

			final GraphObject node = app.nodeQuery(type).getFirst();
			assertEquals(1, (int)node.getProperty("result"));
			tx.success();

		} catch (Throwable fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}
	}

	@Test
	public void testStaticAndDynamicMethodCall() {

		/**
		 * 1. Call context tests
		 * 2. Reference this tests
		 * 3. Binding order test
		 * */

		// setup schema
		try (final Tx tx = app.tx()) {

			final JsonSchema schema = StructrSchema.createFromDatabase(app);
			final JsonType project  = schema.addType("StaticMethodTest");

			JsonMethod staticCallTestMethod  = project.addMethod("doStaticTest", "{}", "");
			JsonMethod dynamicCallTestMethod = project.addMethod("doDynamicTest", "{}", "");

			JsonMethod staticThisTestMethod  = project.addMethod("doStaticTestWithThis", "{ return $.this.type; }", "");
			JsonMethod dynamicThisTestMethod = project.addMethod("doDynamicTestWithThis", "{ return $.this.type; }", "");

			StructrSchema.extendDatabaseSchema(app, schema);

			// make methods static
			SchemaNode schemaNode = StructrApp.getInstance().nodeQuery(SchemaNode.class).andName("StaticMethodTest").getFirst();
			List<SchemaMethod> schemaMethods = Iterables.toList(schemaNode.getSchemaMethods());

			for(SchemaMethod method : schemaMethods) {

				if(method.getName().equals("doStaticTest") || method.getName().equals("doStaticTestWithThis")) {
					method.setProperty(SchemaMethod.isStatic, true);
				}

			}

			tx.success();

		} catch (Throwable fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");

		}

		/**
		 * 1. Call context tests
		 *
		 * Method  | Context | Expected Result
		 * --------+---------+----------------
		 * static  | static  | success
		 * static  | dynamic | failure
		 * dynamic | static  | failure
		 * dynamic | dynamic | success
		 *
		 * */

		final ActionContext ctx = new ActionContext(securityContext);
		final Class testType    = StructrApp.getConfiguration().getNodeEntityClass("StaticMethodTest");

		// call static method from static context
		try (final Tx tx = app.tx()) {

			final Object result = Scripting.evaluate(ctx, null, "${{ $.StaticMethodTest.doStaticTest(); }}", "doStaticTest");

		} catch (Throwable fex) {

			fex.printStackTrace();

		}

		// call static method from dynamic context
		try (final Tx tx = app.tx()) {

			app.create(testType, "test");

			final Object result = Scripting.evaluate(ctx, null, "${{ const test = $.find('StaticMethodTest')[0]; test.doStaticTest(); }}", "test");

			fail("Calling static method from dynamic context should result in an Exception!");

		} catch (Throwable fex) {}

		// call dynamic method from static context
		try (final Tx tx = app.tx()) {

			final Object result = Scripting.evaluate(ctx, null, "${{ $.StaticMethodTest.doDynamicTest(); }}", "doDynamicTest");
			fail("Calling dynamic method from static context should result in an Exception!");

		} catch (Throwable fex) {}

		// call dynamic method from dynamic context
		try (final Tx tx = app.tx()) {

			app.create(testType, "test");

			final Object result = Scripting.evaluate(ctx, null, "${{ const test = $.find('StaticMethodTest')[0]; test.doDynamicTest(); }}", "doDynamicTest");

		} catch (Throwable fex) {

			fex.printStackTrace();

		}

		/**
		 * 2. Reference this tests
		 *
		 * Method  | Access $.this | Expected Result
		 * --------+---------------+----------------
		 * static  | true          | failure
		 * dynamic | true          | success
		 *
		 * */

		// reference $.this from static method
		try (final Tx tx = app.tx()) {

			final Object result = Scripting.evaluate(ctx, null, "${{ $.StaticMethodTest.doStaticTestWithThis(); }}", "doStaticTestWithThis");
			fail("Referencing $.this from a static method should result in an Exception!");

		} catch (Throwable fex) {}

		// reference $.this from dynamic method
		try (final Tx tx = app.tx()) {

			app.create(testType, "test");

			final Object result = Scripting.evaluate(ctx, null, "${{ const test = $.find('StaticMethodTest')[0]; test.doDynamicTestWithThis(); }}", "doDynamicTestWithThis");

		} catch (Throwable fex) {

			fex.printStackTrace();

		}

		/**
		 * 3. Binding order test
		 * local variables bind stronger than class names
		 * */


		// reference $.this from static method
		try (final Tx tx = app.tx()) {

			ctx.setConstant("StaticMethodTest", Boolean.TRUE);
			final Object result = Scripting.evaluate(ctx, null, "${{ $.StaticMethodTest.doStaticTest(); }}", "doStaticTest");
			fail("Local variable or constant should overwrite the Class constant!");

		} catch (Throwable fex) {}

	}

	@Test
	public void testCacheFunction () {

		final ActionContext ctx = new ActionContext(securityContext);

		// test
		try (final Tx tx = app.tx()) {


			final Object cachedResult = ScriptTestHelper.testExternalScript(ctx, ScriptingTest.class.getResourceAsStream("/test/scripting/testCacheFunction.js"));
			Object result = ScriptTestHelper.testExternalScript(ctx, ScriptingTest.class.getResourceAsStream("/test/scripting/testCacheFunction.js"));
			assertEquals(cachedResult, result);

			tryWithTimeout(()-> {
				try {
					return !ScriptTestHelper.testExternalScript(ctx, ScriptingTest.class.getResourceAsStream("/test/scripting/testCacheFunction.js")).equals(cachedResult);
				} catch (FrameworkException ex) {

					return false;
				}
			}, ()-> fail("Timeout reached while waiting for cached value to change after timeout"), 20000, 1000);

			final Object secondCachedResult = ScriptTestHelper.testExternalScript(ctx, ScriptingTest.class.getResourceAsStream("/test/scripting/testCacheFunction.js"));

			assertFalse("Cached value didn't change after timeout.", cachedResult.equals(secondCachedResult));
			result = ScriptTestHelper.testExternalScript(ctx, ScriptingTest.class.getResourceAsStream("/test/scripting/testCacheFunction.js"));
			assertEquals(secondCachedResult, result);

			tx.success();

		} catch (FrameworkException ex) {

			fail("Unexpected exception");
		}

	}

	@Test
	public void testVarsKeyword () {

		final ActionContext ctx = new ActionContext(securityContext);

		// test
		try (final Tx tx = app.tx()) {

			final Object result = ScriptTestHelper.testExternalScript(ctx, ScriptingTest.class.getResourceAsStream("/test/scripting/testVarsKeyword.js"));

			assertNotNull(result);
			assertTrue("Result is not a map", result instanceof Map);
			for (final Map.Entry<String, Integer> entry : Set.of(Map.entry("a", 0), Map.entry("b", 1), Map.entry("c", 2))) {
				assertEquals(entry.getValue(), ((Map) result).get(entry.getKey()));
			}

			tx.success();

		} catch (FrameworkException ex) {

			fail("Unexpected exception");
		}

	}

	@Test
	public void testMultilineStructrScriptExpression() {

		try (final Tx tx = app.tx()) {

			Scripting.evaluate(new ActionContext(securityContext), null,
				"${if (\n" +
				"	is_collection(request.param),\n" +
				"	(\n" +
				"		print('collection! '),\n" +
				"		each(request.param, print(data))\n" +
				"	),\n" +
				"	(\n" +
				"		print('single param!'),\n" +
				"		print(request.param)\n" +
				"	)\n" +
				")}", "test", null);

			tx.success();

		} catch (FrameworkException ex) {
			ex.printStackTrace();
			fail("Unexpected exception");
		}
	}

	@Test
	public void testDoubleBackslashEscaping() {

		try (final Tx tx = app.tx()) {

			final List<Group> groups = new LinkedList<>();

			groups.add(app.create(Group.class, "Group1"));
			groups.add(app.create(Group.class, "Group2"));
			groups.add(app.create(Group.class, "Group3"));

			final String result = Scripting.replaceVariables(new ActionContext(securityContext), null, "${concat(';', \"'\", '\\r\\n')}");

			assertEquals("Invalid StructrScript tokenizer result: ", ";'\r\n", result);

			tx.success();

		} catch (FrameworkException ex) {
			ex.printStackTrace();
			fail("Unexpected exception");
		}
	}


	// ----- private methods ----
	private void createTestType(final JsonSchema schema, final String name, final String createSource, final String saveSource, final String comment) {

		final JsonType test1    = schema.addType(name);

		test1.addStringProperty("c");
		test1.addStringProperty("s");

		test1.addMethod("onCreation",     createSource, comment);
		test1.addMethod("onModification", saveSource, comment);

	}

	private Map<String, Object> getLoggedModifications(final GraphObject obj) {

		final String log = (String)obj.getProperty("log");

		return new GsonBuilder().create().fromJson(log, Map.class);
	}

	private void assertMapPathValueIs(final Map<String, Object> map, final String mapPath, final Object value) {

		final String[] parts = mapPath.split("[\\.]+");
		Object current       = map;

		for (int i=0; i<parts.length; i++) {

			final String part = parts[i];
			if (StringUtils.isNumeric(part)) {

				int index = Integer.valueOf(part);
				if (current instanceof List) {

					final List list = (List)current;
					if (index >= list.size()) {

						// value for nonexisting fields must be null
						assertEquals("Invalid map path result for " + mapPath, value, null);

						// nothing more to check here
						return;

					} else {

						current = list.get(index);
					}
				}

			} else if ("#".equals(part) && current instanceof List) {

				assertEquals("Invalid collection size for " + mapPath, value, ((List)current).size());

				// nothing more to check here
				return;

			} else {

				if (current instanceof Map) {

					current = ((Map)current).get(part);
				}
			}
		}

		// ignore type of value if numerical (GSON defaults to double...)
		if (value instanceof Number && current instanceof Number) {

			assertEquals("Invalid map path result for " + mapPath, ((Number)value).doubleValue(), ((Number)current).doubleValue(), 0.0);

		} else {

			assertEquals("Invalid map path result for " + mapPath, value, current);
		}
	}

	private String formatDateTestScript(final String parseMethod, final String start, final String end, final String fieldName) {

		final StringBuilder buf = new StringBuilder();

		buf.append("${{ let startDate = ");
		buf.append(parseMethod);
		buf.append("('");
		buf.append(start);
		buf.append("'); let endDate = ");
		buf.append(parseMethod);
		buf.append("('");
		buf.append(end);
		buf.append("'); return $.find('Project', { ");
		buf.append(fieldName);
		buf.append(": $.predicate.range(startDate, endDate) }, $.predicate.sort('name')); }}");

		return buf.toString();
	}
}
