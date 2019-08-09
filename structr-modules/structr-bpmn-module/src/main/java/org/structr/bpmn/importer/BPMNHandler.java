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

import java.io.Reader;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import org.junit.platform.commons.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class BPMNHandler implements Iterator<Map<String, Object>> {

	private static final Logger logger = LoggerFactory.getLogger(BPMNHandler.class);

	public static final String EXTRACT          = "extract";
	public static final String ACTION           = "action";
	public static final String CONTENT          = "content";
	public static final String IS_PROCESS       = "isProcess";
	public static final String ISROOT           = "isRoot";
	public static final String MULTIPLICITY     = "multiplicity";
	public static final String DEFAULTS         = "defaults";
	public static final String MAPPINGS         = "mappings";
	public static final String PROPERTIES       = "properties";
	public static final String PROPERTY_NAME    = "propertyName";
	public static final String ROOT_ELEMENT     = "root";
	public static final String SKIP_ELEMENTS    = "ignore";
	public static final String TARGET_TYPE      = "targetType";
	public static final String TYPE_MAPPING     = "types";
	public static final String TYPE             = "type";
	public static final String CREATE_NODE      = "createNode";
	public static final String SET_PROPERTY     = "setProperty";
	public static final String IGNORE           = "ignore";
	public static final String REPLACE_WITH     = "replaceWith";
	public static final String POST_PROCESS     = "postProcess";

	private final List<BPMNPropertyReference> postProcessMaps = new LinkedList<>();
	private final Map<String, Object> globalReferences       = new LinkedHashMap<>();
	private final Map<String, Object> configuration          = new LinkedHashMap<>();
	private BPMNPropertyProcessor postProcessHandler         = null;
	private Map<String, Object> nextElement                  = null;
	private BPMNTransform transform                          = null;
	private XMLInputFactory factory                          = null;
	private XMLEventReader reader                            = null;
	private Element current                                  = null;

	public BPMNHandler(final Map<String, Object> configuration, final Reader input) throws XMLStreamException {

		this.configuration.putAll(configuration);
		this.factory = XMLInputFactory.newInstance();

		// disable DTD referencing, namespaces etc
		factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
		factory.setProperty(XMLInputFactory.IS_VALIDATING, Boolean.FALSE);
		factory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, Boolean.FALSE);
		factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, Boolean.FALSE);
		factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);

		// create XML reader
		this.reader  = factory.createXMLEventReader(input);
	}

	public void startDocument() {
	}

	public void endDocument() {

	}

	public void startElement(final StartElement element) {

		final String tagName = element.getName().toString();

		current = new Element(current, tagName);

		final Map<String, Object> typeHandler = getConfigForElement(current);
		if (typeHandler != null) {

			final Map<String, Object> properties  = (Map)typeHandler.get(PROPERTIES);
			final Map<String, String> defaults    = (Map)typeHandler.get(DEFAULTS);
			final Object isProcess                = typeHandler.get(IS_PROCESS);
			final Object isRoot                   = typeHandler.get(ISROOT);
			final Map<String, String> data        = new LinkedHashMap<>();

			current.isProcess = Boolean.TRUE.equals(isProcess);
			current.isRoot    = Boolean.TRUE.equals(isRoot);

			for (final Iterator it = element.getAttributes(); it.hasNext();) {

				final Object attr = it.next();

				if (attr instanceof Attribute) {

					final Attribute attribute = (Attribute)attr;
					final String name         = attribute.getName().toString();
					String value              = attribute.getValue();

					if (transform != null) {

						value = transform.transform(current.getPath(), name, value, data);
					}

					if (properties != null && properties.containsKey(name))  {

						final String mappedName = (String)properties.get(name);
						data.put(mappedName, value);

					} else {

						data.put(name, value);
					}
				}
			}

			if (defaults != null) {

				defaults.forEach((key, value) -> {

					if (!data.containsKey(key)) {

						if (transform != null) {
							value = transform.transform(current.getPath(), key, value, data);
						}

						data.put(key, value);
					}
				});
			}

			current.setData(data);
		}
	}

	public void endElement(final EndElement element) {

		if (current != null) {

			if (configuration.containsKey(current.getPath())) {

				if (current.isRoot) {

					// object is complete, can be created
					handleObject(current);
				}
			}

			// one level up
			current = current.parent;
		}
	}

	public void characters(final Characters text) {

		if (current != null && !text.isIgnorableWhiteSpace() && !text.isWhiteSpace()) {

			String value = text.getData();

			if (transform != null) {
				value = transform.transform(current.getPath(), "content", value, new LinkedHashMap<>());
			}

			current.setText(value);
		}
	}

	void setTransform(final BPMNTransform transform) {
		this.transform = transform;
	}

	void setPostProcessHandler(final BPMNPropertyProcessor handler) {
		this.postProcessHandler = handler;
	}

	// ----- private methods -----
	private void handleObject(final Element element) {

		nextElement = new LinkedHashMap<>();

		convertAndTransform(element, nextElement);
	}

	private void convertAndTransform(final Element element, final Map<String, Object> entityData) {

		final Map<String, Object> config = getConfigForElement(element);
		if (config != null) {

			final String action = (String)config.get(ACTION);
			if (action != null) {

				switch (action) {

					case CREATE_NODE:
						handleCreateNode(element, entityData, config);
						return;

					case SET_PROPERTY:
						handleSetProperty(element, entityData, config);
						break;

					case IGNORE:
						return;
				}

			} else {

				System.out.println("No action for tag " + element.tagName + ", ignoring");
			}
		}

		// recurse into children
		for (final Element child: element.children) {
			convertAndTransform(child, entityData);
		}
	}

	private void recurseAndReplace(final Map<String, Object> root, final Map<String, Object> entityData) {

		for (final Entry<String, Object> entry : entityData.entrySet()) {

			final String key   = entry.getKey();
			final Object value = entry.getValue();

			if (value instanceof Map) {

				recurseAndReplace(root, (Map)value);
			}

			if (globalReferences.containsKey(key)) {

				final Map<String, Object> reference = (Map)globalReferences.get(key);
				final Map<String, Object> source    = (Map)reference.get("source");
				final String path                   = (String)reference.get("path");
				final Object resolvedData           = resolve(root, path, source);

				entry.setValue(resolvedData);
			}
		}
	}

	private void handleCreateNode(final Element element, final Map<String, Object> entityData, final Map<String, Object> config) {

		// copy and transform entity data into
		final String type = (String)config.get(TYPE);

		if (element.isRoot) {

			// handle data for toplevel element
			// add config.properties to entityData

			final Collection<Object> mappedProperties = ((Map)config.get(PROPERTIES)).values();
			element.data.forEach((String key, Object value) -> {
				if (mappedProperties.contains(key)) {
					entityData.put(key, value);
				}
			});

			entityData.put(TYPE, type);

			for (final Element child : element.children) {

				convertAndTransform(child, entityData);
			}

		} else {

			String propertyName = (String)config.get(PROPERTY_NAME);
			if (propertyName == null) {

				// fallback 1: use name
				propertyName = (String)element.data.get("name");
			}

			if (propertyName == null) {

				// fallback 1: use content
				propertyName = element.text;
			}

			if (propertyName != null) {

				final Map<String, Object> childData = new LinkedHashMap<>();
				final Map<String, Object> properties = ((Map)config.get(PROPERTIES));

				if (properties != null) {

					// add config.properties to childData
					final Collection<Object>  mappedProperties = properties.values();

					element.data.forEach((String key, Object value) -> {

						if (mappedProperties.contains(key)) {

							childData.put(key, value);
						}
					});

				} else {

					System.out.println("Missing property mappings for nested createNode action in " + element.tagName);
				}

				if (element.isProcess) {

					final List<String> implementsList = new LinkedList<>();

					implementsList.add("https://structr.org/v1.1/definitions/BPMNInactive");

					childData.put("$implements", implementsList);
				}

				if ("1".equals(config.get(MULTIPLICITY))) {

					if (type != null) {
						childData.put(TYPE, type);
					}

					for (final Element child : element.children) {

						convertAndTransform(child, childData);
					}

					// handle data for nested child element
					if (entityData.containsKey(propertyName) && entityData.get(propertyName) instanceof Map) {

						// try to merge..
						final Map<String, Object> data = (Map)entityData.get(propertyName);
						data.putAll(childData);

					} else {

						entityData.put(propertyName, childData);
					}

				} else {

					List<Map<String, Object>> elements = (List)entityData.get(propertyName);
					if (elements == null) {

						elements = new LinkedList<>();
						entityData.put(propertyName, elements);
					}

					// add element to collection
					elements.add(childData);

					for (final Element child : element.children) {

						if (type != null) {
							childData.put(TYPE, type);
						}

						convertAndTransform(child, childData);
					}
				}

				if (element.text != null) {

					// store content if present
					String contentName = (String)config.get(CONTENT);
					if (contentName == null) {

						contentName = "content";
					}

					childData.put(contentName, element.text);
				}

				if (config.containsKey(REPLACE_WITH)) {

					final Map<String, Object> reference = new LinkedHashMap<>();

					reference.put("path", config.get(REPLACE_WITH));
					reference.put("source", childData);

					globalReferences.put(propertyName, reference);
				}

				if (config.containsKey(EXTRACT)) {

					final String extractedKey   = (String)config.get(EXTRACT);
					final Object extractedValue = childData.get(extractedKey);

					entityData.put(propertyName, extractedValue);
				}

			} else {

				System.out.println("Missing property name for nested createNode action in " + element.tagName);
			}
		}

		if (config.containsKey(POST_PROCESS)) {

			final String taskType = (String)element.parent.data.get("name");

			postProcessMaps.add(new BPMNPropertyReference(taskType, entityData, element.text));
		}
	}

	/**
	 * The setProperty action will not descend further into the collection
	 * of children, but will instead evaluate a transformation expression.
	 *
	 * @param element element
	 * @param entityData parent's entity data
	 * @param config type configuration
	 */
	private void handleSetProperty(final Element element, final Map<String, Object> entityData, final Map<String, Object> config) {

		Map<String, String> propertyMappings = (Map)config.get(MAPPINGS);
		if (propertyMappings != null) {

			for (final Entry<String, String> entry : propertyMappings.entrySet()) {

				final String key   = entry.getKey();
				final String value = entry.getValue();
				final Object data  = element.data.get(value);

				entityData.put(key, data);
			}

			final Map<String, String> defaults = (Map)config.get(DEFAULTS);
			if (defaults != null) {

				defaults.forEach((key, value) -> {

					final Object existingValue = entityData.get(key);
					if (existingValue instanceof String) {

						final String existingString = (String)existingValue;
						if (StringUtils.isBlank(existingString)) {

							entityData.put(key, value);
						}
					}
				});
			}

		} else {

			String propertyName = (String)config.get(PROPERTY_NAME);
			if (propertyName == null) {

				propertyName = element.tagName;
			}

			if ("$content".equals(propertyName)) {

				propertyName = element.text;
			}

			entityData.put(propertyName, element.text);
		}
	}

	private Map<String, Object> getConfigForElement(final Element element) {

		Map<String, Object> map = (Map)configuration.get(element.getPath());
		if (map == null) {

			map = (Map)configuration.get(element.tagName);
		}

		if (map == null) {

			final String path = element.getPath();

			// resolve wildcards in path
			for (final String key : configuration.keySet()) {

				if (matches(path, key)) {

					return (Map)configuration.get(key);
				}
			}
		}

		return map;
	}

	private Object resolve(final Map<String, Object> root, final String path, final Map<String, Object> data) {

		final String[] parts = path.split("[/]+");
		Object current       = root;

		for (int i=0; i<parts.length; i++) {

			final String part = parts[i];

			// skip empty parts
			if (StringUtils.isNotBlank(part)) {

				if (part.startsWith("$")) {

					final String reference = part.substring(1);
					final String key       = (String)data.get(reference);

					if (current instanceof Map) {

						current = ((Map)current).get(key);
					}

				} else if ("#".equals(part) && current instanceof List) {

					return null;

				} else if ("*".equals(part)) {

					if (current instanceof Map) {

						return ((Map)current).values();
					}

				} else {

					if (current instanceof Map) {

						current = ((Map)current).get(part);
					}
				}
			}
		}

		return current;
	}

	private boolean matches(final String path, final String pattern) {

		if (pattern.contains("*")) {

			final String[] patternParts = pattern.split("[/:]+");
			final String[] pathParts    = path.split("[/:]+");
			final int len               = pathParts.length;

			if (patternParts.length == pathParts.length) {

				for (int i=0; i<len; i++) {

					if ("*".equals(patternParts[i]) || patternParts[i].equals(pathParts[i])) {

						// no nothing

					} else {

						return false;
					}
				}

				return true;
			}
		}

		return false;
	}


	// ----- nested classes -----
	private class Element {

		private Map<String, String> data = new LinkedHashMap<>();
		private List<Element> children   = new LinkedList<>();
		private boolean isProcess        = false;
		private boolean isRoot           = false;
		private Element parent           = null;
		private String tagName           = null;
		private String text              = null;

		public Element(final Element parent, final String tagName) {
			this.parent  = parent;
			this.tagName = tagName;

			if (parent != null) {
				parent.children.add(this);
			}
		}

		@Override
		public String toString() {

			final StringBuilder buf = new StringBuilder();

			buf.append(tagName);
			buf.append("(");
			buf.append(data);
			buf.append(")");

			return buf.toString();
		}

		public void setData(final Map<String, String> data) {
			this.data.putAll(data);
		}

		public void setText(final String text) {
			this.text = text;
		}

		public String getPath() {

			if (parent != null) {
				return parent.getPath() + "/" + tagName;
			}

			return "/" + tagName;
		}

		public Map<String, String> getData() {
			return data;
		}
	}

	// ----- interface Iterator<JsonInput> -----
	@Override
	public boolean hasNext() {

		// iterate over input data until an element is created
		while (reader.hasNext() && nextElement == null) {

			try {
				final XMLEvent event = reader.nextEvent();

				switch (event.getEventType()) {

					case XMLEvent.START_DOCUMENT:
						startDocument();
						break;

					case XMLEvent.START_ELEMENT:
						startElement(event.asStartElement());
						break;

					case XMLEvent.END_DOCUMENT:
						endDocument();
						break;

					case XMLEvent.END_ELEMENT:
						endElement(event.asEndElement());
						break;

					case XMLEvent.CHARACTERS:
					case XMLEvent.CDATA:
						characters(event.asCharacters());
						break;

				}

			} catch (XMLStreamException strex) {
				logger.warn(strex.getMessage());
				break;
			}
		}

		// do replacement actions here..
		if (nextElement != null) {

			recurseAndReplace(nextElement, nextElement);

			((Map)nextElement.get("definitions")).keySet().removeAll(globalReferences.keySet());
		}

		// post-processing
		for (final BPMNPropertyReference ref : postProcessMaps) {

			postProcessHandler.processProperties(nextElement, ref);
		}

		// either an element has been created, or the stream is at its end
		return nextElement != null;
	}

	@Override
	public Map<String, Object> next() {

		// transfer reference
		final Map<String, Object> result = nextElement;

		// reset local reference
		nextElement = null;

		// return transferred reference
		return result;
	}
}
