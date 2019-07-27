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
import java.util.Map;
import org.apache.commons.codec.digest.DigestUtils;
import org.asciidoctor.internal.IOUtils;
import org.codehaus.plexus.util.StringUtils;
import org.structr.core.app.App;
import org.structr.core.app.StructrApp;
import org.structr.core.entity.SchemaNode;
import org.structr.core.graph.Tx;
import org.structr.schema.export.StructrSchema;
import org.structr.schema.json.JsonSchema;

/**
 */
public class BPMNImporter implements BPMNTransform {

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
		this.name = name;
	}

	public String getXml() {
		return xml;
	}

	public void setXml(String xml) {
		this.xml = xml;
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
			handler.setTransform(this);

			if (handler.hasNext()) {

				final Map<String, Object> data = handler.next();

				// tranformed data needs schema ID, so we use the MD5 hash of the name
				data.put("id", DigestUtils.md5Hex(name));
				data.put("methods", new LinkedList<>());

				final App app                  = StructrApp.getInstance();
				final String source            = gson.toJson(data);
				final JsonSchema schema        = StructrSchema.createFromSource(source);

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

		} catch (Throwable t) {

			t.printStackTrace();
			return false;
		}

		return true;
	}

	@Override
	public String transform(final String path, final String name, final String value) {

		if ("sourceRef".equals(name) || "targetRef".equals(name)) {
			return "#/definitions/" + value;
		}

		if ("category".equals(name)) {
			return getCategory();
		}

		return value;
	}

	private String getCategory() {
		return "BPMN/" + this.name;
	}

	public static void main(final String[] args) {

		final BPMNImporter importer = new BPMNImporter();

		importer.setName("test1.bpmn");

		try (final InputStream is = new FileInputStream("/home/chrisi/camunda/test1.bpmn")) {

			importer.setXml(IOUtils.readFull(is));

		} catch (IOException ex) {
			ex.printStackTrace();
		}

		importer.doImport();
	}
}
