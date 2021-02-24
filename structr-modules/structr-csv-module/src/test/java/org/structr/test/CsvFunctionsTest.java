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
package org.structr.test;

import java.util.List;
import java.util.Locale;
import org.apache.commons.lang3.StringUtils;
import org.testng.annotations.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.structr.common.error.FrameworkException;
import org.structr.core.app.StructrApp;
import org.structr.core.entity.Localization;
import org.structr.core.graph.Tx;
import org.structr.core.property.PropertyMap;
import org.structr.core.script.Scripting;
import org.structr.test.entity.CsvTestEnum;
import org.structr.test.entity.CsvTestOne;
import org.structr.test.entity.CsvTestTwo;
import org.structr.schema.action.ActionContext;
import org.structr.test.web.StructrUiTest;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.fail;

public class CsvFunctionsTest extends StructrUiTest {

	private static final Logger logger = LoggerFactory.getLogger(CsvFunctionsTest.class.getName());

	@Test
	public void testCsvFunctions() {

		List<CsvTestOne> csvTestOnes                = null;
		CsvTestTwo csvTestTwo                       = null;
		int index                                   = 0;

		try (final Tx tx = app.tx()) {

			csvTestOnes = createTestNodes(CsvTestOne.class, 5);
			csvTestTwo  = createTestNode(CsvTestTwo.class);

			final PropertyMap indexLocalizationProperties = new PropertyMap();
			final PropertyMap nameLocalizationProperties = new PropertyMap();
			final PropertyMap indexLocalizationPropertiesWithDomain = new PropertyMap();
			final PropertyMap nameLocalizationPropertiesWithDomain = new PropertyMap();

			indexLocalizationProperties.put(StructrApp.key(Localization.class, "name"),                    "index");
			indexLocalizationProperties.put(StructrApp.key(Localization.class, "localizedName"),           "Localized INDEX");
			indexLocalizationProperties.put(StructrApp.key(Localization.class, "locale"),                  "en");

			nameLocalizationProperties.put(StructrApp.key(Localization.class, "name"),                     "name");
			nameLocalizationProperties.put(StructrApp.key(Localization.class, "localizedName"),            "Localized NAME");
			nameLocalizationProperties.put(StructrApp.key(Localization.class, "locale"),                   "en");

			indexLocalizationPropertiesWithDomain.put(StructrApp.key(Localization.class, "name"),          "index");
			indexLocalizationPropertiesWithDomain.put(StructrApp.key(Localization.class, "localizedName"), "Localized INDEX with DOMAIN");
			indexLocalizationPropertiesWithDomain.put(StructrApp.key(Localization.class, "locale"),        "en");
			indexLocalizationPropertiesWithDomain.put(StructrApp.key(Localization.class, "domain"),        "CSV TEST Domain");

			nameLocalizationPropertiesWithDomain.put(StructrApp.key(Localization.class, "name"),           "name");
			nameLocalizationPropertiesWithDomain.put(StructrApp.key(Localization.class, "localizedName"),  "Localized NAME with DOMAIN");
			nameLocalizationPropertiesWithDomain.put(StructrApp.key(Localization.class, "locale"),         "en");
			nameLocalizationPropertiesWithDomain.put(StructrApp.key(Localization.class, "domain"),         "CSV TEST Domain");

			app.create(Localization.class, indexLocalizationProperties);
			app.create(Localization.class, nameLocalizationProperties);
			app.create(Localization.class, indexLocalizationPropertiesWithDomain);
			app.create(Localization.class, nameLocalizationPropertiesWithDomain);

			for (final CsvTestOne csvTestOne : csvTestOnes) {

				csvTestOne.setProperty(CsvTestOne.name, "CSV Test Node " + StringUtils.leftPad(Integer.toString(index+1), 4, "0"));
				csvTestOne.setProperty(CsvTestOne.index, index+1);

				if (index == 0) {
					// set string array on test four
					csvTestOne.setProperty(CsvTestOne.stringArrayProperty, new String[] { "one", "two", "three", "four" } );
				}

				if (index == 2) {
					// set string array on test four
					csvTestOne.setProperty(CsvTestOne.enumProperty, CsvTestEnum.EnumValue2 );
				}

				index++;
			}

			tx.success();

		} catch (FrameworkException fex) {

			logger.warn("", fex);

			fail("Unexpected exception");
		}

		try (final Tx tx = app.tx()) {

			final ActionContext ctx = new ActionContext(securityContext, null);
			ctx.setLocale(Locale.ENGLISH);

			/**
			 * The expected results
			 */
			final String expectedDefaultCsv = "\"name\";\"index\";\"type\";\"stringArrayProperty\";\"enumProperty\"\n"
					+ "\"CSV Test Node 0001\";\"1\";\"CsvTestOne\";\"[\\\"one\\\", \\\"two\\\", \\\"three\\\", \\\"four\\\"]\";\"\"\n"
					+ "\"CSV Test Node 0002\";\"2\";\"CsvTestOne\";\"\";\"\"\n"
					+ "\"CSV Test Node 0003\";\"3\";\"CsvTestOne\";\"\";\"EnumValue2\"\n"
					+ "\"CSV Test Node 0004\";\"4\";\"CsvTestOne\";\"\";\"\"\n"
					+ "\"CSV Test Node 0005\";\"5\";\"CsvTestOne\";\"\";\"\"\n";
			final String expectedCsvWithNameAndIndex = "\"name\";\"index\"\n"
					+ "\"CSV Test Node 0001\";\"1\"\n"
					+ "\"CSV Test Node 0002\";\"2\"\n"
					+ "\"CSV Test Node 0003\";\"3\"\n"
					+ "\"CSV Test Node 0004\";\"4\"\n"
					+ "\"CSV Test Node 0005\";\"5\"\n";
			final String expectedCsvWithIndexAndName = "\"index\";\"name\"\n"
					+ "\"1\";\"CSV Test Node 0001\"\n"
					+ "\"2\";\"CSV Test Node 0002\"\n"
					+ "\"3\";\"CSV Test Node 0003\"\n"
					+ "\"4\";\"CSV Test Node 0004\"\n"
					+ "\"5\";\"CSV Test Node 0005\"\n";
			final String expectedCsvWithNameAndIndexAndCustomDelimiterPipe = "\"name\"|\"index\"\n"
					+ "\"CSV Test Node 0001\"|\"1\"\n"
					+ "\"CSV Test Node 0002\"|\"2\"\n"
					+ "\"CSV Test Node 0003\"|\"3\"\n"
					+ "\"CSV Test Node 0004\"|\"4\"\n"
					+ "\"CSV Test Node 0005\"|\"5\"\n";
			final String expectedCsvWithNameAndIndexAndCustomDelimiterXXX = "\"name\"X\"index\"\n"
					+ "\"CSV Test Node 0001\"X\"1\"\n"
					+ "\"CSV Test Node 0002\"X\"2\"\n"
					+ "\"CSV Test Node 0003\"X\"3\"\n"
					+ "\"CSV Test Node 0004\"X\"4\"\n"
					+ "\"CSV Test Node 0005\"X\"5\"\n";
			final String expectedCsvWithIndexAndNameAndSingleQuote = "'index';'name'\n"
					+ "'1';'CSV Test Node 0001'\n"
					+ "'2';'CSV Test Node 0002'\n"
					+ "'3';'CSV Test Node 0003'\n"
					+ "'4';'CSV Test Node 0004'\n"
					+ "'5';'CSV Test Node 0005'\n";
			final String expectedCsvWithIndexAndNameAndSingleQuoteAfterRoundTrip = "'index';'name'\n"
					+ "'1';'CSV Test Node 0001'\n"
					+ "'2';'CSV Test Node 0002'\n"
					+ "'3';'CSV Test Node 0003'\n"
					+ "'4';'CSV Test Node 0004'\n"
					+ "'5';'CSV Test Node 0005'\n";
			final String expectedCsvWithIndexAndNameAndSingleQuoteAndCRLF = "'index';'name'\r\n"
					+ "'1';'CSV Test Node 0001'\r\n"
					+ "'2';'CSV Test Node 0002'\r\n"
					+ "'3';'CSV Test Node 0003'\r\n"
					+ "'4';'CSV Test Node 0004'\r\n"
					+ "'5';'CSV Test Node 0005'\r\n";
			final String expectedCsvWithIndexAndNameAndSingleQuoteAndCRLFNoHeader = "'1';'CSV Test Node 0001'\r\n"
					+ "'2';'CSV Test Node 0002'\r\n"
					+ "'3';'CSV Test Node 0003'\r\n"
					+ "'4';'CSV Test Node 0004'\r\n"
					+ "'5';'CSV Test Node 0005'\r\n";
			final String expectedCsvWithIndexAndNameAndSingleQuoteAndCRLFWithExplicitHeader = "'index';'name'\r\n"
					+ "'1';'CSV Test Node 0001'\r\n"
					+ "'2';'CSV Test Node 0002'\r\n"
					+ "'3';'CSV Test Node 0003'\r\n"
					+ "'4';'CSV Test Node 0004'\r\n"
					+ "'5';'CSV Test Node 0005'\r\n";
			final String expectedCsvWithIndexAndNameAndSingleQuoteAndCRLFWithLocalizedHeader = "'Localized INDEX';'Localized NAME'\r\n"
					+ "'1';'CSV Test Node 0001'\r\n"
					+ "'2';'CSV Test Node 0002'\r\n"
					+ "'3';'CSV Test Node 0003'\r\n"
					+ "'4';'CSV Test Node 0004'\r\n"
					+ "'5';'CSV Test Node 0005'\r\n";
			final String expectedCsvWithIndexAndNameAndSingleQuoteAndCRLFWithLocalizedHeaderWithDomain = "'Localized INDEX with DOMAIN';'Localized NAME with DOMAIN'\r\n"
					+ "'1';'CSV Test Node 0001'\r\n"
					+ "'2';'CSV Test Node 0002'\r\n"
					+ "'3';'CSV Test Node 0003'\r\n"
					+ "'4';'CSV Test Node 0004'\r\n"
					+ "'5';'CSV Test Node 0005'\r\n";
			final String expectedCsvForCustomJavaScriptObjects = "\"id\";\"customField\";\"name\"\n"
					+ "\"abcd0001\";\"extra1\";\"my 1st custom object\"\n"
					+ "\"bcde0002\";\"extra2\";\"my 2nd custom object\"\n"
					+ "\"cdef0003\";\"extra3\";\"my 3rd custom object\"\n";
			final String expectedCsvForObjectsWithNewlineCharacters = "\"multi\"\n"
					+ "\"Multi\\nLine\\nTest\"\n";

			/**
			 * First everything in StructrScript
			 */

			assertEquals(
					"Invalid result of default to_csv() call (StructrScript)",
					expectedDefaultCsv,
					Scripting.replaceVariables(ctx, csvTestTwo, "${to_csv(find('CsvTestOne', sort('name')), 'csv')}")
			);

			assertEquals(
					"Invalid result of to_csv() call with only name,index (StructrScript)",
					expectedCsvWithNameAndIndex,
					Scripting.replaceVariables(ctx, csvTestTwo, "${to_csv(find('CsvTestOne', sort('name')), merge('name', 'index'))}")
			);

			assertEquals(
					"Invalid result of to_csv() call with only name,index (StructrScript)",
					expectedCsvWithIndexAndName,
					Scripting.replaceVariables(ctx, csvTestTwo, "${to_csv(find('CsvTestOne', sort('name')), merge('index', 'name'))}")
			);

			assertEquals(
					"Invalid result of to_csv() with delimiterChar = '|'. (StructrScript)",
					expectedCsvWithNameAndIndexAndCustomDelimiterPipe,
					Scripting.replaceVariables(ctx, csvTestTwo, "${to_csv(find('CsvTestOne', sort('name')), merge('name', 'index'), '|')}")
			);

			assertEquals(
					"Invalid result of to_csv() with delimiterChar = 'XXX'. Only first character should be used! (StructrScript)",
					expectedCsvWithNameAndIndexAndCustomDelimiterXXX,
					Scripting.replaceVariables(ctx, csvTestTwo, "${to_csv(find('CsvTestOne', sort('name')), merge('name', 'index'), 'XXX')}")
			);

			assertEquals(
					"Invalid result of to_csv() call with only index,name AND quote character = '  (StructrScript)",
					expectedCsvWithIndexAndNameAndSingleQuote,
					Scripting.replaceVariables(ctx, csvTestTwo, "${to_csv(find('CsvTestOne', sort('name')), merge('index', 'name'), ';', \"'\")}")
			);

			assertEquals(
					"Invalid result of to_csv() call with only index,name AND quote character = ' AFTER round-trip through to_csv, from_csv and to_csv (StructrScript)",
					expectedCsvWithIndexAndNameAndSingleQuoteAfterRoundTrip,
					Scripting.replaceVariables(ctx, csvTestTwo, "${to_csv(from_csv(to_csv(find('CsvTestOne', sort('name')), merge('index', 'name'), ';', \"'\"), ';', \"'\"), merge('index', 'name'), ';', \"'\")}")
			);

			assertEquals(
					"Invalid result of to_csv() call with only index,name, singleQuoted and CRLF as recordSeparator (StructrScript)",
					expectedCsvWithIndexAndNameAndSingleQuoteAndCRLF,
					Scripting.replaceVariables(ctx, csvTestTwo, "${to_csv(find('CsvTestOne', sort('name')), merge('index', 'name'), ';', \"'\", '\\r\\n')}")
			);

			assertEquals(
					"Invalid result of to_csv() call with only index,name, singleQuoted and CRLF as recordSeparator and no header (StructrScript)",
					expectedCsvWithIndexAndNameAndSingleQuoteAndCRLFNoHeader,
					Scripting.replaceVariables(ctx, csvTestTwo, "${to_csv(find('CsvTestOne', sort('name')), merge('index', 'name'), ';', \"'\", '\\r\\n', false)}")
			);

			assertEquals(
					"Invalid result of to_csv() call with only index,name, singleQuoted and CRLF as recordSeparator and explicit header (StructrScript)",
					expectedCsvWithIndexAndNameAndSingleQuoteAndCRLFWithExplicitHeader,
					Scripting.replaceVariables(ctx, csvTestTwo, "${to_csv(find('CsvTestOne', sort('name')), merge('index', 'name'), ';', \"'\", '\\r\\n', true)}")
			);

			assertEquals(
					"Invalid result of to_csv() call with only index,name, singleQuoted and CRLF as recordSeparator and localized header (without domain) (StructrScript)",
					expectedCsvWithIndexAndNameAndSingleQuoteAndCRLFWithLocalizedHeader,
					Scripting.replaceVariables(ctx, csvTestTwo, "${to_csv(find('CsvTestOne', sort('name')), merge('index', 'name'), ';', \"'\", '\\r\\n', true, true)}")
			);

			assertEquals(
					"Invalid result of to_csv() call with only index,name, singleQuoted and CRLF as recordSeparator and localized header (with domain) (StructrScript)",
					expectedCsvWithIndexAndNameAndSingleQuoteAndCRLFWithLocalizedHeaderWithDomain,
					Scripting.replaceVariables(ctx, csvTestTwo, "${to_csv(find('CsvTestOne', sort('name')), merge('index', 'name'), ';', \"'\", '\\r\\n', true, true, 'CSV TEST Domain')}")
			);

			/**
			 * Then everything again in JavaScript
			 */

			assertEquals(
					"Invalid result of default Structr.to_csv() call (JavaScript)",
					expectedDefaultCsv,
					Scripting.replaceVariables(ctx, csvTestTwo, "${{Structr.print(Structr.to_csv(Structr.find('CsvTestOne', $.predicate.sort('name')), 'csv'))}}")
			);

			assertEquals(
					"Invalid result of Structr.to_csv() call with only name,index (JavaScript)",
					expectedCsvWithNameAndIndex,
					Scripting.replaceVariables(ctx, csvTestTwo, "${{Structr.print(Structr.to_csv(Structr.find('CsvTestOne', $.predicate.sort('name')), ['name', 'index']))}}")
			);

			assertEquals(
					"Invalid result of Structr.to_csv() call with only name,index (JavaScript)",
					expectedCsvWithIndexAndName,
					Scripting.replaceVariables(ctx, csvTestTwo, "${{Structr.print(Structr.to_csv(Structr.find('CsvTestOne', $.predicate.sort('name')), ['index', 'name']))}}")
			);

			assertEquals(
					"Invalid result of Structr.to_csv() with delimiterChar = '|'. (JavaScript)",
					expectedCsvWithNameAndIndexAndCustomDelimiterPipe,
					Scripting.replaceVariables(ctx, csvTestTwo, "${{Structr.print(Structr.to_csv(Structr.find('CsvTestOne', $.predicate.sort('name')), ['name', 'index'], '|'))}}")
			);

			assertEquals(
					"Invalid result of Structr.to_csv() with delimiterChar = 'XXX'. Only first character should be used! (JavaScript)",
					expectedCsvWithNameAndIndexAndCustomDelimiterXXX,
					Scripting.replaceVariables(ctx, csvTestTwo, "${{Structr.print(Structr.to_csv(Structr.find('CsvTestOne', $.predicate.sort('name')), ['name', 'index'], 'XXX'))}}")
			);

			assertEquals(
					"Invalid result of Structr.to_csv() call with only index,name AND quote character = '  (JavaScript)",
					expectedCsvWithIndexAndNameAndSingleQuote,
					Scripting.replaceVariables(ctx, csvTestTwo, "${{Structr.print(Structr.to_csv(Structr.find('CsvTestOne', $.predicate.sort('name')), ['index', 'name'], ';', \"'\"))}}")
			);

			assertEquals(
					"Invalid result of Structr.to_csv() call with only index,name AND quote character = ' AFTER round-trip through to_csv, from_csv and to_csv (JavaScript)",
					expectedCsvWithIndexAndNameAndSingleQuoteAfterRoundTrip,
					Scripting.replaceVariables(ctx, csvTestTwo, "${{Structr.print(Structr.to_csv(Structr.from_csv(Structr.to_csv(Structr.find('CsvTestOne', $.predicate.sort('name')), ['index', 'name'], ';', \"'\"), ';', \"'\"), ['index', 'name'], ';', \"'\"))}}")
			);

			assertEquals(
					"Invalid result of Structr.to_csv() call with only index,name, singleQuoted and CRLF as recordSeparator (JavaScript)",
					expectedCsvWithIndexAndNameAndSingleQuoteAndCRLF,
					Scripting.replaceVariables(ctx, csvTestTwo, "${{Structr.print(Structr.to_csv(Structr.find('CsvTestOne', $.predicate.sort('name')), ['index', 'name'], ';', \"'\", '\\r\\n'))}}")
			);

			assertEquals(
					"Invalid result of Structr.to_csv() call with only index,name, singleQuoted and CRLF as recordSeparator and no header (JavaScript)",
					expectedCsvWithIndexAndNameAndSingleQuoteAndCRLFNoHeader,
					Scripting.replaceVariables(ctx, csvTestTwo, "${{Structr.print(Structr.to_csv(Structr.find('CsvTestOne', $.predicate.sort('name')), ['index', 'name'], ';', \"'\", '\\r\\n', false))}}")
			);

			assertEquals(
					"Invalid result of Structr.to_csv() call with only index,name, singleQuoted and CRLF as recordSeparator and explicit header (JavaScript)",
					expectedCsvWithIndexAndNameAndSingleQuoteAndCRLFWithExplicitHeader,
					Scripting.replaceVariables(ctx, csvTestTwo, "${{Structr.print(Structr.to_csv(Structr.find('CsvTestOne', $.predicate.sort('name')), ['index', 'name'], ';', \"'\", '\\r\\n', true))}}")
			);

			assertEquals(
					"Invalid result of Structr.to_csv() call with only index,name, singleQuoted and CRLF as recordSeparator and localized header (without domain) (JavaScript)",
					expectedCsvWithIndexAndNameAndSingleQuoteAndCRLFWithLocalizedHeader,
					Scripting.replaceVariables(ctx, csvTestTwo, "${{Structr.print(Structr.to_csv(Structr.find('CsvTestOne', $.predicate.sort('name')), ['index', 'name'], ';', \"'\", '\\r\\n', true, true))}}")
			);

			assertEquals(
					"Invalid result of Structr.to_csv() call with only index,name, singleQuoted and CRLF as recordSeparator and localized header (with domain) (JavaScript)",
					expectedCsvWithIndexAndNameAndSingleQuoteAndCRLFWithLocalizedHeaderWithDomain,
					Scripting.replaceVariables(ctx, csvTestTwo, "${{Structr.print(Structr.to_csv(Structr.find('CsvTestOne', $.predicate.sort('name')), ['index', 'name'], ';', \"'\", '\\r\\n', true, true, 'CSV TEST Domain'))}}")
			);

			assertEquals(
					"Invalid result of Structr.to_csv() call for a collection of custom objects (JavaScript)",
					expectedCsvForCustomJavaScriptObjects,
					Scripting.replaceVariables(ctx, csvTestTwo, "${{Structr.print(Structr.to_csv([{id: 'abcd0001', name: 'my 1st custom object', customField: 'extra1'}, {id: 'bcde0002', name: 'my 2nd custom object', customField: 'extra2'}, {id: 'cdef0003', name: 'my 3rd custom object', customField: 'extra3'}], ['id', 'customField', 'name']))}}")
			);

			assertEquals(
					"Invalid result of Structr.to_csv() call for source objects with newlines (JavaScript)",
					expectedCsvForObjectsWithNewlineCharacters,
					Scripting.replaceVariables(ctx, csvTestTwo, "${{Structr.print(Structr.to_csv([{multi:'Multi\\nLine\\nTest'}], ['multi']))}}")
			);

			tx.success();

		} catch (FrameworkException fex) {

			logger.warn("", fex);

			fail(fex.getMessage());
		}
	}

}
