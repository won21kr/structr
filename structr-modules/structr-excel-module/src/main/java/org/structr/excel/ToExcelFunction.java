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
package org.structr.excel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.poi.ss.usermodel.Comment;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.structr.common.error.FrameworkException;
import org.structr.core.GraphObject;
import org.structr.core.GraphObjectMap;
import org.structr.core.app.StructrApp;
import org.structr.core.function.LocalizeFunction;
import org.structr.core.property.DateProperty;
import org.structr.core.property.PropertyKey;
import org.structr.schema.action.ActionContext;
import org.structr.schema.action.Function;
import org.structr.schema.parser.DatePropertyParser;

public class ToExcelFunction extends Function<Object, Object> {

	public static final String ERROR_MESSAGE_TO_EXCEL    = "Usage: ${to_excel(nodes, propertiesOrView[, includeHeader[, localizeHeader[, headerLocalizationDomain[, maxCellLength[, overflowMode]]]]])}. Example: ${to_excel(find('Page'), 'ui')}";
	public static final String ERROR_MESSAGE_TO_EXCEL_JS = "Usage: ${{Structr.to_excel(nodes, propertiesOrView[, includeHeader[, localizeHeader[, headerLocalizationDomain[, maxCellLength[, overflowMode]]]]])}}. Example: ${{Structr.to_excel(Structr.find('Page'), 'ui'))}}";

	@Override
	public String getName() {
		return "to_excel";
	}

	@Override
	public String getSignature() {
		return "nodes, propertiesOrView [, ih, lh, ld, ml, om ]";
	}

	@Override
	public String getRequiredModule() {
		return "excel";
	}

	@Override
	public Object apply(ActionContext ctx, Object caller, Object[] sources) throws FrameworkException {

		try {

			assertArrayHasMinLengthAndMaxLengthAndAllElementsNotNull(sources, 2, 7);

			if ( !(sources[0] instanceof List) ) {
				logParameterError(caller, sources, ctx.isJavaScriptContext());
				return "ERROR: First parameter must be a collection! ".concat(usage(ctx.isJavaScriptContext()));
			}

			final List<GraphObject> nodes           = (List)sources[0];
			boolean includeHeader                   = true;
			boolean localizeHeader                  = false;
			String headerLocalizationDomain         = null;
			Integer maxCellLength                   = 32767;
			String overflowMode                     = "o";
			String propertyView                     = null;
			List<String> properties                 = null;

			// we are using size() instead of isEmpty() because NativeArray.isEmpty() always returns true
			if (nodes.size() == 0) {
				logger.warn("to_excel(): Can not create Excel if no nodes are given!");
				logParameterError(caller, sources, ctx.isJavaScriptContext());
				return "";
			}

			switch (sources.length) {
				case 7: overflowMode = sources[6].toString();
				case 6: maxCellLength = Math.min(maxCellLength, (Integer)sources[5]);
				case 5: headerLocalizationDomain = sources[4].toString();
				case 4: localizeHeader = (Boolean)sources[3];
				case 3: includeHeader = (Boolean)sources[2];
				case 2: {

					if (sources[1] instanceof CharSequence) {
						// view is given
						propertyView = sources[1].toString();

					} else if (sources[1] instanceof List) {
						// named properties are given
						properties = new ArrayList<>();

						for (CharSequence prop : ((List<CharSequence>)sources[1])) {
							properties.add(prop.toString());
						}

						// we are using size() instead of isEmpty() because NativeArray.isEmpty() always returns true
						if (properties.size() == 0) {
							logger.warn("to_excel(): Can not create Excel if list of properties is empty!");
							logParameterError(caller, sources, ctx.isJavaScriptContext());
							return "";
						}

					} else {
						logParameterError(caller, sources, ctx.isJavaScriptContext());
						return "ERROR: Second parameter must be a collection of property names or a single property view!".concat(usage(ctx.isJavaScriptContext()));
					}
				}
			}

			try {

				final Workbook wb = writeExcel(nodes, propertyView, properties, includeHeader, localizeHeader, headerLocalizationDomain, ctx.getLocale(), maxCellLength, overflowMode);
				final ByteArrayOutputStream baos = new ByteArrayOutputStream();
				wb.write(baos);
				return baos.toString("ISO-8859-1");

			} catch (Throwable t) {
				logger.warn("to_excel(): Exception occurred", t);
				return "";
			}

		} catch (IllegalArgumentException e) {

			logParameterError(caller, sources, e.getMessage(), ctx.isJavaScriptContext());
			return usage(ctx.isJavaScriptContext());
		}
	}

	@Override
	public String usage(boolean inJavaScriptContext) {
		return (inJavaScriptContext ? ERROR_MESSAGE_TO_EXCEL_JS : ERROR_MESSAGE_TO_EXCEL);
	}

	@Override
	public String shortDescription() {
		return "Creates Excel from given data";
	}

	public Workbook writeExcel(final List list, final String propertyView, final List<String> properties, final boolean includeHeader, final boolean localizeHeader, final String headerLocalizationDomain, final Locale locale, final Integer maxCellLength, final String overflowMode) throws IOException {

		final Workbook workbook = new XSSFWorkbook();
		final CreationHelper factory = workbook.getCreationHelper();
		final XSSFSheet sheet = (XSSFSheet) workbook.createSheet();
		final Drawing drawing = sheet.createDrawingPatriarch();

		int rowCount = 0;
		int cellCount = 0;

		XSSFRow currentRow = null;
		XSSFCell cell = null;

		if (includeHeader) {

			currentRow = (XSSFRow)sheet.createRow(rowCount++);
			cellCount = 0;

			if (propertyView != null) {

				final Object obj = list.get(0);

				if (obj instanceof GraphObject) {

					for (PropertyKey key : ((GraphObject)obj).getPropertyKeys(propertyView)) {

						cell = (XSSFCell)currentRow.createCell(cellCount++);

						String value = key.dbName();
						if (localizeHeader) {
							try {
								value = LocalizeFunction.getLocalization(locale, value, headerLocalizationDomain);
							} catch (FrameworkException fex) {
								logger.warn("to_excel(): Exception", fex);
							}
						}

						cell.setCellValue(value);
					}

				} else {
					cell = (XSSFCell)currentRow.createCell(cellCount++);
					cell.setCellValue("Error: Object is not of type GraphObject, can not determine properties of view for header row");
				}

			} else if (properties != null) {

				for (final String colName : properties) {

					cell = (XSSFCell)currentRow.createCell(cellCount++);
					String value = colName;
					if (localizeHeader) {
						try {
							value = LocalizeFunction.getLocalization(locale, value, headerLocalizationDomain);
						} catch (FrameworkException fex) {
							logger.warn("to_excel(): Exception", fex);
						}
					}

					cell.setCellValue(value);
				}
			}
		}

		for (final Object obj : list) {

			currentRow = (XSSFRow)sheet.createRow(rowCount++);
			cellCount = 0;

			if (propertyView != null) {

				if (obj instanceof GraphObject) {

					for (PropertyKey key : ((GraphObject)obj).getPropertyKeys(propertyView)) {

						final Object value = ((GraphObject)obj).getProperty(key);

						cell = (XSSFCell)currentRow.createCell(cellCount++);

						writeToCell(factory, drawing, cell, value, maxCellLength, overflowMode);
					}

				} else {
					cell = (XSSFCell)currentRow.createCell(cellCount++);
					cell.setCellValue("Error: Object is not of type GraphObject, can not determine properties of object");
				}

			} else if (properties != null) {

				if (obj instanceof GraphObjectMap) {

					final Map convertedMap = ((GraphObjectMap)obj).toMap();

					for (final String colName : properties) {
						final Object value = convertedMap.get(colName);
						cell = (XSSFCell)currentRow.createCell(cellCount++);

						writeToCell(factory, drawing, cell, value, maxCellLength, overflowMode);
					}

				} else if (obj instanceof GraphObject) {

					final GraphObject graphObj = (GraphObject)obj;

					for (final String colName : properties) {
						final PropertyKey key = StructrApp.key(obj.getClass(), colName);
						final Object value = graphObj.getProperty(key);
						cell = (XSSFCell)currentRow.createCell(cellCount++);

						writeToCell(factory, drawing, cell, value, maxCellLength, overflowMode);
					}

				} else if (obj instanceof Map) {

					final Map map = (Map)obj;

					for (final String colName : properties) {
						final Object value = map.get(colName);
						cell = (XSSFCell)currentRow.createCell(cellCount++);

						writeToCell(factory, drawing, cell, value, maxCellLength, overflowMode);
					}
				}
			}
		}

		return workbook;
	}

	public String escapeForExcel(final Object value) {

		String result;

		if (value == null) {

			result = "";

		} else if (value instanceof String[]) {

			List<String> quotedStrings = Arrays.asList((String[])value);
			result = quotedStrings.toString();

		} else if (value instanceof Collection) {

			// Special handling for collections of nodes
			ArrayList<String> quotedStrings = new ArrayList();
			for (final Object obj : (Collection)value) {
				quotedStrings.add(obj.toString());
			}

			result = quotedStrings.toString();

		} else if (value instanceof Date) {

			result = DatePropertyParser.format((Date) value, DateProperty.getDefaultFormat());

		} else {

			result = value.toString();

		}

		return result;
	}

	public void writeToCell(final CreationHelper factory, final Drawing drawing, final XSSFCell cell, final Object value, final Integer maxCellLength, final String overflowMode) {

		final String cellValue = escapeForExcel(value);

		if (cellValue.length() <= maxCellLength) {

			cell.setCellValue(cellValue);

		} else {

			cell.setCellValue(cellValue.substring(0, maxCellLength));

			if (!"t".equals(overflowMode)) {
				final Comment comment = drawing.createCellComment(factory.createClientAnchor());

				if ("o".equals(overflowMode)) {
					final String overflow = cellValue.substring(maxCellLength, Math.min(maxCellLength + 32767, cellValue.length()));
					comment.setString(factory.createRichTextString(overflow));
				} else {
					comment.setString(factory.createRichTextString(overflowMode));
				}

				cell.setCellComment(comment);
			}
		}
	}
}
