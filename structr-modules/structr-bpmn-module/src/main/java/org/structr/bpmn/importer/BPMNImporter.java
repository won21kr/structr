/**
 * Copyright (C) 2010-2019 Structr GmbH
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
package org.structr.bpmn.importer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.codehaus.plexus.util.StringUtils;
import org.structr.core.app.App;
import org.structr.core.app.StructrApp;
import org.structr.core.entity.SchemaNode;
import org.structr.core.graph.Tx;
import org.structr.schema.export.StructrSchema;
import org.structr.schema.json.JsonSchema;

/**
 */
public class BPMNImporter implements BPMNTransform, BPMNPropertyProcessor {

	private boolean testing     = false;
	private boolean changedOnly = false;
	private String name         = null;
	private String xml          = null;

	public boolean isChangedOnly() {
		return changedOnly;
	}

	public void setChangedOnly(boolean changedOnly) {
		this.changedOnly = changedOnly;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name.trim();
	}

	public String getXml() {
		return xml;
	}

	public void setXml(String xml) {
		this.xml = xml;
	}

	public void setTesting(final boolean isTesting) {
		this.testing = isTesting;
	}


	public boolean isReady() {
		return StringUtils.isNotBlank(xml);
	}

	public boolean doImport() {

		final Map<String, Object> config = new LinkedHashMap<>();
		final Gson gson                  = new GsonBuilder().setPrettyPrinting().create();

		// read config from file..
		try (final InputStream is = BPMNImporter.class.getResourceAsStream("/importer.json")) {

			config.putAll(gson.fromJson(new InputStreamReader(is), Map.class));

		} catch (IOException ex) {
			ex.printStackTrace();
		}

		try {

			final BPMNHandler handler = new BPMNHandler(config, new StringReader(xml));
			handler.setPostProcessHandler(this);
			handler.setTransform(this);

			if (handler.hasNext()) {

				final Map<String, Object> data = handler.next();

				// tranformed data needs schema ID, so we use the MD5 hash of the name
				data.put("id", DigestUtils.md5Hex(name));
				data.put("methods", new LinkedList<>());

				final String source = gson.toJson(data);

				if (testing) {

					System.out.println(source);

				} else {

					final App app           = StructrApp.getInstance();
					final JsonSchema schema = StructrSchema.createFromSource(source);

					try (final Tx tx = app.tx()) {

						// delete all BPMN schema nodes that belong to this process
						for (final SchemaNode node : app.nodeQuery(SchemaNode.class).and(SchemaNode.category, getCategory()).getResultStream()) {

							app.delete(node);
						}

						// apply new schema
						StructrSchema.extendDatabaseSchema(app, schema);


						tx.success();
					}
				}
			}

		} catch (Throwable t) {

			t.printStackTrace();
			return false;
		}

		return true;
	}

	@Override
	public String transform(final String path, final String name, final String value, final Map<String, String> data) {

		if ("sourceRef".equals(name) || "targetRef".equals(name)) {
			return "#/definitions/" + getCategory() + "_" + value;
		}

		if ("category".equals(name)) {
			return getCategory();
		}

		if ("id".equals(name) && value != null) {

			return getCategory() + "_" + value;
		}

		if ("content".equals(name) && StringUtils.isNotBlank(value)) {

			return getCategory() + "_" + value;
		}

		return value;
	}

	private String getCategory() {
		return this.name;
	}

	@Override
	public void processProperties(final Map<String, Object> root, final BPMNPropertyReference reference) {

		final Map<String, String> replacements = new LinkedHashMap<>();
		final Map<String, Object> data         = reference.getData();
		final List<String> bpmnView            = new LinkedList<>();
		final String sourceType                = reference.getType();
		final Map<String, Object> properties   = (Map)data.get("properties");

		if (properties != null) {

			properties.remove("name");

			for (final Entry<String, Object> entry : properties.entrySet()) {

				final String key   = entry.getKey();
				final Object value = entry.getValue();

				if (value instanceof Map) {

					final Map<String, Object> map   = (Map)entry.getValue();
					final Map<String, Object> props = (Map)map.get("properties");

					if (props != null) {

						final String name = (String)props.get("name");

						replacements.put(key, name);
					}
				}
			}

			for (final Entry<String, String> entry : replacements.entrySet()) {

				final String key             = entry.getKey();
				final String value           = entry.getValue();
				Map<String, Object> property = (Map)properties.remove(key);

				// move one level up
				property = (Map)property.remove("properties");

				// replace
				properties.put(value, property);

				// modify "required" property
				if (property.containsKey("required")) {

					if ("true".equalsIgnoreCase(property.get("required").toString())) {

						property.put("required", true);

					} else {

						property.remove("required");
					}
				}

				// create references for non-primitive properties
				final String type = (String)property.get("type");
				if (type != null) {

					switch (type) {

						// ignore these property types
						case "date":
							property.put("type", "string");
							property.put("format", "date-time");
							property.put("datePattern", "");
							break;

						case "string":
						case "password":
						case "thumbnail":
						case "count":
						case "script":
						case "function":
						case "boolean":
						case "number":
						case "integer":
						case "long":
						case "custom":
						case "encrypted":
						case "object":
						case "array":
							break;

						default:
							// non-primitive => create reference
							createPropertyReference(root, sourceType, property);
							break;
					}
				}

				// cleanup
				property.remove("displayName");
				property.remove("name");

				bpmnView.add(value);
			}
		}

		Map<String, Object> views = (Map)data.get("views");
		if (views == null) {

			views = new TreeMap<>();
			data.put("views", views);
		}

		// add bpmn view
		final List<String> view = (List)views.get("bpmn");
		if (view != null) {

			view.addAll(bpmnView);

		} else {

			views.put("bpmn", bpmnView);
		}
	}

	private void createPropertyReference(final Map<String, Object> root, final String taskType, final Map<String, Object> property) {

		final String relatedType = (String)property.get("type");
		final String relType     = (String)property.get("relType");
		final String name        = (String)property.get("name");
		final String relTypeName = relatedType + relType + taskType;

		// remove attributes that are used to set up the relationship
		property.remove("relType");
		property.remove("required");

		// create relationship
		property.put("type", "object");
		property.put("$link", "#/definitions/" + relTypeName);
		property.put("$ref",  "#/definitions/" + relatedType);

		final Map<String, Object> relationshipTypeDefinition = new LinkedHashMap<>();

		relationshipTypeDefinition.put("$source",     "#/definitions/" + relatedType);
		relationshipTypeDefinition.put("$target",     "#/definitions/" + taskType);
		relationshipTypeDefinition.put("cardinality", "OneToOne");
		relationshipTypeDefinition.put("rel",         relType);
		relationshipTypeDefinition.put("sourceName",  name);

		final Map<String, Object> definitions = (Map)root.get("definitions");
		if (definitions != null) {

			// store new relationship in root
			definitions.put(relTypeName, relationshipTypeDefinition);

			// store stub that allows us to reference a type that is not part of this schema
			if (!definitions.containsKey(relatedType)) {

				final Map<String, Object> stub = new LinkedHashMap<>();

				stub.put("name", relatedType);

				definitions.put(relatedType, stub);
			}
		}
	}

	public static void main(final String[] args) {

		final BPMNImporter importer = new BPMNImporter();

		importer.setTesting(true);

		importer.setName("UserRegistration");

		try (final InputStream is = new FileInputStream("/home/chrisi/camunda/test1.bpmn")) {

			importer.setXml(IOUtils.toString(is, "utf-8"));

		} catch (IOException ex) {
			ex.printStackTrace();
		}

		importer.doImport();
	}
}
