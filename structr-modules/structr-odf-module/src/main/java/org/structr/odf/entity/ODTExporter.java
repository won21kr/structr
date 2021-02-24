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
package org.structr.odf.entity;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.odftoolkit.simple.TextDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.structr.api.schema.JsonObjectType;
import org.structr.api.schema.JsonSchema;
import org.structr.api.util.Iterables;
import org.structr.api.util.ResultStream;
import org.structr.common.SecurityContext;
import org.structr.common.error.FrameworkException;
import org.structr.core.GraphObject;
import org.structr.core.GraphObjectMap;
import org.structr.core.app.App;
import org.structr.core.app.StructrApp;
import org.structr.core.entity.AbstractNode;
import org.structr.schema.SchemaService;
import org.structr.transform.VirtualType;
import org.structr.web.entity.File;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Reads a nodes attributes and tries to replace matching attributes in the
 * given ODT-File template.
 */
public interface ODTExporter extends ODFExporter {

	static class Impl { static {

		final JsonSchema schema   = SchemaService.getDynamicSchema();
		final JsonObjectType type = schema.addType("ODTExporter");

		type.setImplements(URI.create("https://structr.org/v1.1/definitions/ODTExporter"));
		type.setExtends(URI.create("#/definitions/ODFExporter"));

		type.addMethod("exportAttributes")
			.addParameter("ctx", SecurityContext.class.getName())
			.addParameter("uuid", String.class.getName())
			.setSource(ODTExporter.class.getName() + ".exportAttributes(this, uuid, ctx);")
			.addException(FrameworkException.class.getName())
			.setDoExport(true);
	}}

	static final String ODT_FIELD_TAG_NAME        = "text:user-field-decl";
	static final String ODT_FIELD_ATTRIBUTE_NAME  = "text:name";
	static final String ODT_FIELD_ATTRIBUTE_VALUE = "office:string-value";

	static void exportAttributes(final ODTExporter thisNode, final String uuid, final SecurityContext securityContext) throws FrameworkException {

		final File output                     = thisNode.getResultDocument();
		final VirtualType transformation      = thisNode.getTransformationProvider();

		try {

			final App app = StructrApp.getInstance(securityContext);
			final ResultStream result = app.nodeQuery(AbstractNode.class).and(GraphObject.id, uuid).getResultStream();
			final ResultStream transformedResult = transformation.transformOutput(securityContext, AbstractNode.class, result);

			Map<String, Object> nodeProperties = new HashMap<>();
			GraphObjectMap node = (GraphObjectMap) Iterables.first(transformedResult);
			node.getPropertyKeys(null).forEach(
				p -> nodeProperties.put(p.dbName(), node.getProperty(p))
			);

			TextDocument text = TextDocument.loadDocument(output.getFileOnDisk().getAbsolutePath());

			NodeList nodes = text.getContentRoot().getElementsByTagName(ODT_FIELD_TAG_NAME);
			for (int i = 0; i < nodes.getLength(); i++) {

				Node currentNode = nodes.item(i);
				NamedNodeMap attrs = currentNode.getAttributes();
				Node fieldName = attrs.getNamedItem(ODT_FIELD_ATTRIBUTE_NAME);
				Object nodeFieldValue = nodeProperties.get(fieldName.getNodeValue());
				Node currentContent = attrs.getNamedItem(ODT_FIELD_ATTRIBUTE_VALUE);

				if (nodeFieldValue != null) {
					if (nodeFieldValue instanceof String[]) {

						String[] arr = (String[]) nodeFieldValue;
						List<String> list = new ArrayList<>(Arrays.asList(arr));

						StringBuilder sb = new StringBuilder();
						list.forEach(
							s -> sb.append(s + "\n")
						);

						currentContent.setNodeValue(sb.toString());

					} else if (nodeFieldValue instanceof Collection) {

						Collection col = (Collection) nodeFieldValue;
						StringBuilder sb = new StringBuilder();
						col.forEach(
							s -> sb.append(s + "\n")
						);

						currentContent.setNodeValue(sb.toString());

					} else {

						currentContent.setNodeValue(nodeFieldValue.toString());

					}
				}

			}

			text.save(output.getFileOnDisk().getAbsolutePath());
			text.close();

		} catch (Exception e) {
			final Logger logger = LoggerFactory.getLogger(ODTExporter.class);
			logger.error("Error while exporting to ODT", e);
		}
	}
}
