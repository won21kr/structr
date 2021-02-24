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
package org.structr.core.entity;

import graphql.Scalars;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLType;
import static graphql.schema.GraphQLTypeReference.typeRef;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang.StringUtils;
import org.structr.api.util.Iterables;
import org.structr.common.PropertyView;
import org.structr.common.SecurityContext;
import org.structr.common.ValidationHelper;
import org.structr.common.View;
import org.structr.common.error.ErrorBuffer;
import org.structr.common.error.FrameworkException;
import org.structr.common.error.UnlicensedTypeException;
import org.structr.core.Export;
import org.structr.core.Services;
import org.structr.core.app.StructrApp;
import org.structr.core.entity.relationship.SchemaGrantSchemaNodeRelationship;
import org.structr.core.entity.relationship.SchemaRelationshipSourceNode;
import org.structr.core.entity.relationship.SchemaRelationshipTargetNode;
import org.structr.core.graph.ModificationQueue;
import org.structr.core.graph.NodeInterface;
import org.structr.core.graphql.GraphQLListType;
import org.structr.core.property.ArrayProperty;
import org.structr.core.property.BooleanProperty;
import org.structr.core.property.EndNode;
import org.structr.core.property.EndNodes;
import org.structr.core.property.IntProperty;
import org.structr.core.property.Property;
import org.structr.core.property.PropertyKey;
import org.structr.core.property.StartNode;
import org.structr.core.property.StartNodes;
import org.structr.core.property.StringProperty;
import org.structr.schema.SchemaHelper;
import org.structr.schema.SchemaHelper.Type;
import org.structr.schema.SchemaService;
import org.structr.schema.SourceFile;

/**
 *
 *
 */
public class SchemaNode extends AbstractSchemaNode {

	public static final String GraphQLNodeReferenceName = "StructrNodeReference";
	public static final String schemaNodeNamePattern    = "[A-Z][a-zA-Z0-9_]*";

	private static final Set<String> EntityNameBlacklist = new LinkedHashSet<>(Arrays.asList(new String[] {
		"Relation", "Property"
	}));

	public static final Property<Iterable<SchemaRelationshipNode>> relatedTo              = new EndNodes<>("relatedTo", SchemaRelationshipSourceNode.class);
	public static final Property<Iterable<SchemaRelationshipNode>> relatedFrom            = new StartNodes<>("relatedFrom", SchemaRelationshipTargetNode.class);
	public static final Property<Iterable<SchemaGrant>>            schemaGrants           = new StartNodes<>("schemaGrants", SchemaGrantSchemaNodeRelationship.class);
	public static final Property<String>                           extendsClass           = new StringProperty("extendsClass").indexed();
	public static final Property<String>                           implementsInterfaces   = new StringProperty("implementsInterfaces").indexed();
	public static final Property<String>                           defaultSortKey         = new StringProperty("defaultSortKey");
	public static final Property<String>                           defaultSortOrder       = new StringProperty("defaultSortOrder");
	public static final Property<Boolean>                          defaultVisibleToPublic = new BooleanProperty("defaultVisibleToPublic").readOnly().indexed();
	public static final Property<Boolean>                          defaultVisibleToAuth   = new BooleanProperty("defaultVisibleToAuth").readOnly().indexed();
	public static final Property<Boolean>                          isBuiltinType          = new BooleanProperty("isBuiltinType").readOnly().indexed();
	public static final Property<Integer>                          hierarchyLevel         = new IntProperty("hierarchyLevel").indexed();
	public static final Property<Integer>                          relCount               = new IntProperty("relCount").indexed();
	public static final Property<Boolean>                          isInterface            = new BooleanProperty("isInterface").indexed();
	public static final Property<Boolean>                          isAbstract             = new BooleanProperty("isAbstract").indexed();
	public static final Property<String>                           category               = new StringProperty("category").indexed();
	public static final Property<String[]>                         tags                   = new ArrayProperty("tags", String.class).indexed();
	public static final Property<String>                           summary                = new StringProperty("summary").indexed();
	public static final Property<String>                           description            = new StringProperty("description").indexed();

	private static final Set<PropertyKey> PropertiesThatDoNotRequireRebuild = new LinkedHashSet<>(Arrays.asList(tags, summary, description));

	public static final View defaultView = new View(SchemaNode.class, PropertyView.Public,
		extendsClass, implementsInterfaces, relatedTo, relatedFrom, defaultSortKey, defaultSortOrder, isBuiltinType, hierarchyLevel, relCount, isInterface, isAbstract, defaultVisibleToPublic, defaultVisibleToAuth, tags, summary, description
	);

	public static final View uiView = new View(SchemaNode.class, PropertyView.Ui,
		name, extendsClass, implementsInterfaces, relatedTo, relatedFrom, defaultSortKey, defaultSortOrder, isBuiltinType, hierarchyLevel, relCount, isInterface, isAbstract, category, defaultVisibleToPublic, defaultVisibleToAuth, tags, summary, description
	);

	public static final View schemaView = new View(SchemaNode.class, "schema",
		name, extendsClass, implementsInterfaces, relatedTo, relatedFrom, defaultSortKey, defaultSortOrder, isBuiltinType, hierarchyLevel, relCount, isInterface, isAbstract, schemaGrants, defaultVisibleToPublic, defaultVisibleToAuth, tags, summary, description
	);

	public static final View exportView = new View(SchemaNode.class, "export",
		extendsClass, implementsInterfaces, defaultSortKey, defaultSortOrder, isBuiltinType, hierarchyLevel, relCount, isInterface, isAbstract, defaultVisibleToPublic, defaultVisibleToAuth, tags, summary, description
	);

	@Override
	public void onCreation(SecurityContext securityContext, ErrorBuffer errorBuffer) throws FrameworkException {

		super.onCreation(securityContext, errorBuffer);

		throwExceptionIfTypeAlreadyExists();
	}

	@Override
	public void onModification(SecurityContext securityContext, ErrorBuffer errorBuffer, final ModificationQueue modificationQueue) throws FrameworkException {

		super.onModification(securityContext, errorBuffer, modificationQueue);

		if (modificationQueue.isPropertyModified(this, name)) {
			throwExceptionIfTypeAlreadyExists();
		}
	}

	@Override
	public Set<PropertyKey> getPropertyKeys(final String propertyView) {

		final List<PropertyKey> propertyKeys = Iterables.toList(super.getPropertyKeys(propertyView));

		// add "custom" property keys as String properties
		for (final String key : SchemaHelper.getProperties(getNode())) {

			final PropertyKey newKey = new StringProperty(key);
			newKey.setDeclaringClass(getClass());

			propertyKeys.add(newKey);
		}

		Collections.sort(propertyKeys, (o1, o2) -> { return o1.jsonName().compareTo(o2.jsonName()); });

		return new LinkedHashSet<>(propertyKeys);
	}

	@Override
	public boolean isValid(final ErrorBuffer errorBuffer) {

		boolean valid = super.isValid(errorBuffer);

		valid &= ValidationHelper.isValidUniqueProperty(this, name, errorBuffer);
		valid &= ValidationHelper.isValidStringMatchingRegex(this, name, schemaNodeNamePattern, errorBuffer);

		return valid;
	}

	@Override
	public String getMultiplicity(final Map<String, SchemaNode> schemaNodes, final String propertyNameToCheck) {

		String multiplicity = getMultiplicity(this, propertyNameToCheck);

		if (multiplicity == null) {

			final String parentClass = getProperty(SchemaNode.extendsClass);
			if (parentClass != null) {

				// check if property is defined in parent class
				final SchemaNode parentSchemaNode = schemaNodes.get(StringUtils.substringAfterLast(parentClass, "."));
				if (parentSchemaNode != null) {

					multiplicity = getMultiplicity(parentSchemaNode, propertyNameToCheck);

				}
			}
		}

		if (multiplicity != null) {
			return multiplicity;
		}

		// fallback, search NodeInterface (this allows the owner relationship to be used in Notions!)
		final PropertyKey key = StructrApp.getConfiguration().getPropertyKeyForJSONName(NodeInterface.class, propertyNameToCheck, false);
		if (key != null) {


			// return "extended" multiplicity when the falling back to a NodeInterface property
			// to signal the code generator that it must not append "Property" to the name of
			// the generated NotionProperty parameter, i.e. NotionProperty(owner, ...) instead
			// of NotionProperty(ownerProperty, ...)..

			if (key instanceof StartNode || key instanceof EndNode) {
				return "1X";
			}

			if (key instanceof StartNodes || key instanceof EndNodes) {
				return "*X";
			}
		}

		return null;
	}

	private String getMultiplicity(final SchemaNode schemaNode, final String propertyNameToCheck) {

		final Set<String> existingPropertyNames = new LinkedHashSet<>();
		final String _className                 = schemaNode.getProperty(name);

		for (final SchemaRelationshipNode outRel : schemaNode.getProperty(SchemaNode.relatedTo)) {

			if (propertyNameToCheck.equals(outRel.getPropertyName(_className, existingPropertyNames, true))) {
				return outRel.getMultiplicity(true);
			}
		}

		// output related node definitions, collect property views
		for (final SchemaRelationshipNode inRel : schemaNode.getProperty(SchemaNode.relatedFrom)) {

			if (propertyNameToCheck.equals(inRel.getPropertyName(_className, existingPropertyNames, false))) {
				return inRel.getMultiplicity(false);
			}
		}

		return null;
	}

	@Override
	public String getRelatedType(final Map<String, SchemaNode> schemaNodes, final String propertyNameToCheck) {

		String relatedType = getRelatedType(this, propertyNameToCheck);
		if (relatedType == null) {

			final String parentClass = getProperty(SchemaNode.extendsClass);
			if (parentClass != null) {

				// check if property is defined in parent class
				final SchemaNode parentSchemaNode = schemaNodes.get(StringUtils.substringAfterLast(parentClass, "."));
				if (parentSchemaNode != null) {

					relatedType = getRelatedType(parentSchemaNode, propertyNameToCheck);

				}
			}
		}

		if (relatedType != null) {
			return relatedType;
		}

		// fallback, search NodeInterface (this allows the owner relationship to be used in Notions!)
		final PropertyKey key = StructrApp.getConfiguration().getPropertyKeyForJSONName(NodeInterface.class, propertyNameToCheck, false);
		if (key != null) {

			final Class relatedTypeClass = key.relatedType();
			if (relatedTypeClass != null) {

				return relatedTypeClass.getSimpleName();
			}
		}

		return null;
	}

	public void handleMigration() throws FrameworkException {

		// we need to consider the following cases:
		//  - class extends other dynamic class => no change
		//  - class extends FileBase => make it extend AbstractNode and implement File
		//  - class extends built-in type => make it extend AbstractNode and implement dynamic interface

		final String tmp = getProperty(extendsClass);
		if (tmp != null) {

			final String interfaces = getProperty(implementsInterfaces);
			String _extendsClass = StringUtils.substringBefore(tmp, "<"); // remove optional generic parts from class name

			// migrate FileBase
			if (_extendsClass.equals("org.structr.web.entity.FileBase")) {

				removeProperty(extendsClass);
				setProperty(implementsInterfaces, addToList(interfaces, "org.structr.web.entity.File"));
				return;
			}

			// migrate Image
			if (_extendsClass.equals("org.structr.web.entity.Image")) {

				setProperty(extendsClass, "org.structr.dynamic.Image");
				return;
			}

			// migrate RemoteDocument
			if (_extendsClass.equals("org.structr.web.entity.RemoteDocument")) {

				setProperty(extendsClass, "org.structr.feed.entity.RemoteDocument");
				return;
			}

			// migrate Person
			if (_extendsClass.equals("org.structr.core.entity.Person")) {

				setProperty(extendsClass, "org.structr.dynamic.Person");
				return;
			}

			// migrate XMPPClient
			if (_extendsClass.equals("org.structr.xmpp.XMPPClient")) {

				setProperty(implementsInterfaces, addToList(interfaces, "org.structr.xmpp.XMPPClient"));
				removeProperty(extendsClass);
				return;
			}

			// migrate ContentContainer
			if (_extendsClass.equals("org.structr.web.entity.ContentContainer")) {

				setProperty(extendsClass, "org.structr.dynamic.ContentContainer");
				return;
			}

			// we need to migrate the feed package
			if (_extendsClass.startsWith("org.structr.web.entity.feed.")) {

				_extendsClass = _extendsClass.replace("org.structr.web.entity.feed.", "org.structr.feed.entity.");
				setProperty(extendsClass, _extendsClass);
			}

			// migrate Messaging
			if (_extendsClass.startsWith("org.structr.messaging.engine.entities.")) {

				removeProperty(extendsClass);
				setProperty(implementsInterfaces, addToList(interfaces, _extendsClass));
				return;
			}

			// move most of the extendsClass to implementsInterfaces
			if (
				_extendsClass.startsWith("org.structr.knowledge.")
				||
				(
					!_extendsClass.endsWith("Impl") &&
					_extendsClass.contains(".entity.") &&
					_extendsClass.startsWith("org.structr.") &&
					!_extendsClass.startsWith("org.structr.dynamic.") &&
					!AbstractNode.class.getName().equals(_extendsClass)
				)
			) {

				setProperty(implementsInterfaces, addToList(interfaces, _extendsClass));
				removeProperty(extendsClass);
			}

			// migrate LDAPUser
			if ("LDAPUser".equals(getName())) {

				// remove method printDebug()
				for (final SchemaMethod m : getProperty(SchemaNode.schemaMethods)) {

					if ("printDebug".equals(m.getName())) {

						StructrApp.getInstance().delete(m);
					}
				}
			}

			// migrate Page
			if ("Page".equals(getName())) {

				// remove method getSite()
				for (final SchemaMethod m : getProperty(SchemaNode.schemaMethods)) {

					if ("getSite".equals(m.getName())) {

						StructrApp.getInstance().delete(m);
					}
				}
			}
		}

		// migrate Mail and Mailbox
		// change name and implemented class
		if ("Mail".equals(getName())) {

			String interfaceStrings = getProperty(implementsInterfaces);

			if (interfaceStrings != null && Arrays.stream(interfaceStrings.split(",")).anyMatch("org.structr.mail.entity.Mail"::equals)) {

				String[] splitInterfaces = interfaceStrings.split(",");

				setProperty(name, "EMailMessage");

				for (int i = 0; i < splitInterfaces.length; i++) {
					if (splitInterfaces[i].equals("org.structr.mail.entity.Mail")) {
						splitInterfaces[i] = "org.structr.mail.entity.EMailMessage";
					}
				}

				setProperty(implementsInterfaces, String.join(",", splitInterfaces));
			}
		}

		if("Mailbox".equals(getName())) {

			String interfaceStrings = getProperty(implementsInterfaces);

			if (interfaceStrings != null && Arrays.stream(interfaceStrings.split(",")).anyMatch("org.structr.mail.entity.Mailbox"::equals)) {

				for (final SchemaProperty p : getProperty(SchemaNode.schemaProperties)) {

					if ("mails".equals(p.getName())) {

						StructrApp.getInstance().delete(p);
					}
				}
			}
		}

		if("LDAPUser".equals(getName())) {

			for (final SchemaMethod method : getProperty(SchemaNode.schemaMethods)) {

				if ("onModification".equals(method.getName())) {

					StructrApp.getInstance().delete(method);
				}
			}
		}

	}

	public void initializeGraphQL(final Map<String, SchemaNode> schemaNodes, final Map<String, GraphQLType> graphQLTypes, final Set<String> blacklist) throws FrameworkException {

		// check if some base class already initialized us
		if (graphQLTypes.containsKey(getClassName())) {

			// nothing to do
			return;
		}

		// register node reference type to filter related nodes
		if (!graphQLTypes.containsKey(GraphQLNodeReferenceName)) {

			graphQLTypes.put(GraphQLNodeReferenceName, GraphQLInputObjectType.newInputObject()
			.name(GraphQLNodeReferenceName)
			.field(GraphQLInputObjectField.newInputObjectField().name("id").type(Scalars.GraphQLString).build())
			.field(GraphQLInputObjectField.newInputObjectField().name("name").type(Scalars.GraphQLString).build())
			.build());
		}

		// variables
		final Map<String, GraphQLFieldDefinition> fields = new LinkedHashMap<>();
		final SchemaNode parentSchemaNode                = getParentSchemaNode(schemaNodes);
		final String className                           = getClassName();

		// add inherited fields from superclass
		registerParentType(schemaNodes, parentSchemaNode, graphQLTypes, fields, blacklist);

		// add inherited fields from interfaces
		for (final SchemaNode ifaceNode : getInterfaceSchemaNodes(schemaNodes)) {

			registerParentType(schemaNodes, ifaceNode, graphQLTypes, fields, blacklist);
		}

		// add dynamic fields
		for (final SchemaProperty property : getSchemaProperties()) {

			final GraphQLFieldDefinition field = property.getGraphQLField();
			if (field != null) {

				fields.put(field.getName(), field);
			}
		}

		// outgoing relationships
		for (final SchemaRelationshipNode outNode : getProperty(SchemaNode.relatedTo)) {
			registerOutgoingGraphQLFields(fields, outNode, blacklist);
		}

		// incoming relationships
		for (final SchemaRelationshipNode inNode : getProperty(SchemaNode.relatedFrom)) {
			registerIncomingGraphQLFields(fields, inNode, blacklist);
		}

		fields.put("id", GraphQLFieldDefinition.newFieldDefinition().name("id").type(Scalars.GraphQLString).arguments(SchemaProperty.getGraphQLArgumentsForUUID()).build());

		// add static fields (name etc., can be overwritten)
		fields.putIfAbsent("type", GraphQLFieldDefinition.newFieldDefinition().name("type").type(Scalars.GraphQLString).build());
		fields.putIfAbsent("name", GraphQLFieldDefinition.newFieldDefinition().name("name").type(Scalars.GraphQLString).arguments(SchemaProperty.getGraphQLArgumentsForType(Type.String)).build());
		fields.putIfAbsent("owner", GraphQLFieldDefinition.newFieldDefinition().name("owner").type(typeRef("Principal")).arguments(SchemaProperty.getGraphQLArgumentsForRelatedType("Principal")).build());
		fields.putIfAbsent("createdBy", GraphQLFieldDefinition.newFieldDefinition().name("createdBy").type(Scalars.GraphQLString).build());
		fields.putIfAbsent("createdDate", GraphQLFieldDefinition.newFieldDefinition().name("createdDate").type(Scalars.GraphQLString).build());
		fields.putIfAbsent("lastModifiedBy", GraphQLFieldDefinition.newFieldDefinition().name("lastModifiedBy").type(Scalars.GraphQLString).build());
		fields.putIfAbsent("lastModifiedDate", GraphQLFieldDefinition.newFieldDefinition().name("lastModifiedDate").type(Scalars.GraphQLString).build());
		fields.putIfAbsent("visibleToPublicUsers", GraphQLFieldDefinition.newFieldDefinition().name("visibleToPublicUsers").type(Scalars.GraphQLString).build());
		fields.putIfAbsent("visibleToAuthenticatedUsers", GraphQLFieldDefinition.newFieldDefinition().name("visibleToAuthenticatedUsers").type(Scalars.GraphQLString).build());

		// register type in GraphQL schema
		final GraphQLObjectType.Builder builder = GraphQLObjectType.newObject();

		builder.name(className);
		builder.fields(new LinkedList<>(fields.values()));

		graphQLTypes.put(className, builder.build());
	}

	@Export
	public String getGeneratedSourceCode(final SecurityContext securityContext) throws FrameworkException, UnlicensedTypeException {

		final SourceFile sourceFile               = new SourceFile("");
		final Map<String, SchemaNode> schemaNodes = new LinkedHashMap<>();

		// collect list of schema nodes
		StructrApp.getInstance().nodeQuery(SchemaNode.class).getAsList().stream().forEach(n -> { schemaNodes.put(n.getName(), n); });

		// return generated source code for this class
		SchemaHelper.getSource(sourceFile, this, schemaNodes, SchemaService.getBlacklist(), new ErrorBuffer());

		return sourceFile.getContent();
	}

	@Override
	public boolean reloadSchemaOnCreate() {
		return true;
	}

	@Override
	public boolean reloadSchemaOnModify(final ModificationQueue modificationQueue) {

		final Set<PropertyKey> modifiedProperties = modificationQueue.getModifiedProperties();
		for (final PropertyKey modifiedProperty : modifiedProperties) {

			if (!PropertiesThatDoNotRequireRebuild.contains(modifiedProperty)) {
				return true;
			}
		}

		return false;
	}

	@Override
	public boolean reloadSchemaOnDelete() {
		return true;
	}

	@Override
	public Iterable<SchemaGrant> getSchemaGrants() {
		return getProperty(schemaGrants);
	}

	// ----- private methods -----
	private void registerParentType(final Map<String, SchemaNode> schemaNodes, final SchemaNode parentSchemaNode, final Map<String, GraphQLType> graphQLTypes, final Map<String, GraphQLFieldDefinition> fields, final Set<String> blacklist) throws FrameworkException {

		if (parentSchemaNode != null && !parentSchemaNode.equals(this)) {

			final String parentName = parentSchemaNode.getClassName();
			if (parentName != null && !blacklist.contains(parentName)) {

				if (!graphQLTypes.containsKey(parentName)) {

					// initialize parent type
					parentSchemaNode.initializeGraphQL(schemaNodes, graphQLTypes, blacklist);
				}

				// second try: add fields from parent type
				if (graphQLTypes.containsKey(parentName)) {

					final GraphQLObjectType parentType = (GraphQLObjectType)graphQLTypes.get(parentName);
					if (parentType != null) {

						for (final GraphQLFieldDefinition field : parentType.getFieldDefinitions()) {

							fields.put(field.getName(), field);
						}
					}
				}
			}
		}

	}

	private void registerOutgoingGraphQLFields(final Map<String, GraphQLFieldDefinition> fields, final SchemaRelationshipNode outNode, final Set<String> blacklist) {

		final SchemaNode targetNode = outNode.getTargetNode();
		final String targetTypeName = targetNode.getClassName();
		final String propertyName   = outNode.getPropertyName(targetTypeName, new LinkedHashSet<>(), true);

		if (blacklist.contains(targetTypeName)) {
			return;
		}

		final GraphQLFieldDefinition.Builder builder = GraphQLFieldDefinition
			.newFieldDefinition()
			.name(propertyName)
			.type(getGraphQLTypeForCardinality(outNode, targetTypeName, true))
			.argument(GraphQLArgument.newArgument().name("_page").type(Scalars.GraphQLInt).build())
			.argument(GraphQLArgument.newArgument().name("_pageSize").type(Scalars.GraphQLInt).build())
			.argument(GraphQLArgument.newArgument().name("_sort").type(Scalars.GraphQLString).build())
			.argument(GraphQLArgument.newArgument().name("_desc").type(Scalars.GraphQLBoolean).build());

		// register reference type so that GraphQL can be used to query related nodes
		if (isMultiple(outNode, true)) {

			builder.argument(GraphQLArgument.newArgument().name("_contains").type(typeRef(GraphQLNodeReferenceName)).build());

		} else {

			builder.argument(GraphQLArgument.newArgument().name("_equals").type(typeRef(GraphQLNodeReferenceName)).build());
		}


		// register field
		fields.put(propertyName, builder.build());
	}

	private void registerIncomingGraphQLFields(final Map<String, GraphQLFieldDefinition> fields, final SchemaRelationshipNode inNode, final Set<String> blacklist) {

		final SchemaNode sourceNode = inNode.getSourceNode();
		final String sourceTypeName = sourceNode.getClassName();
		final String propertyName   = inNode.getPropertyName(sourceTypeName, new LinkedHashSet<>(), false);

		if (blacklist.contains(sourceTypeName)) {
			return;
		}

		final GraphQLFieldDefinition.Builder builder = GraphQLFieldDefinition
			.newFieldDefinition()
			.name(propertyName)
			.type(getGraphQLTypeForCardinality(inNode, sourceTypeName, false))
			.argument(GraphQLArgument.newArgument().name("_page").type(Scalars.GraphQLInt).build())
			.argument(GraphQLArgument.newArgument().name("_pageSize").type(Scalars.GraphQLInt).build())
			.argument(GraphQLArgument.newArgument().name("_sort").type(Scalars.GraphQLString).build())
			.argument(GraphQLArgument.newArgument().name("_desc").type(Scalars.GraphQLBoolean).build());

		// register reference type so that GraphQL can be used to query related nodes
		if (isMultiple(inNode, false)) {

			builder.argument(GraphQLArgument.newArgument().name("_contains").type(typeRef(GraphQLNodeReferenceName)).build());

		} else {

			builder.argument(GraphQLArgument.newArgument().name("_equals").type(typeRef(GraphQLNodeReferenceName)).build());
		}


		// register field
		fields.put(propertyName, builder.build());
	}

	private GraphQLOutputType getGraphQLTypeForCardinality(final SchemaRelationshipNode node, final String targetTypeName, final boolean outgoing) {

		if (isMultiple(node, outgoing)) {
			return new GraphQLListType(typeRef(targetTypeName));
		}

		return typeRef(targetTypeName);
	}

	private boolean isMultiple(final SchemaRelationshipNode node, final boolean outgoing) {

		final String multiplicity = node.getMultiplicity(outgoing);
		if (multiplicity != null) {

			switch (multiplicity) {

				case "1":
					return false;
			}
		}

		return true;
	}

	private String addToList(final String source, final String value) {

		final List<String> list = new LinkedList<>();

		if (source != null) {

			list.addAll(Arrays.asList(source.split(",")));
		}

		list.add(value);

		return StringUtils.join(list, ",");
	}


	private String getRelatedType(final SchemaNode schemaNode, final String propertyNameToCheck) {

		final Set<String> existingPropertyNames = new LinkedHashSet<>();
		final String _className                 = schemaNode.getProperty(name);

		for (final SchemaRelationshipNode outRel : schemaNode.getProperty(SchemaNode.relatedTo)) {

			if (propertyNameToCheck.equals(outRel.getPropertyName(_className, existingPropertyNames, true))) {
				return outRel.getSchemaNodeTargetType();
			}
		}

		// output related node definitions, collect property views
		for (final SchemaRelationshipNode inRel : schemaNode.getProperty(SchemaNode.relatedFrom)) {

			if (propertyNameToCheck.equals(inRel.getPropertyName(_className, existingPropertyNames, false))) {
				return inRel.getSchemaNodeSourceType();
			}
		}

		return null;
	}

	public SchemaNode getParentSchemaNode(final Map<String, SchemaNode> schemaNodes) throws FrameworkException {

		final String parentClass = getProperty(SchemaNode.extendsClass);
		if (parentClass != null) {

			// check if property is defined in parent class
			return schemaNodes.get(StringUtils.substringAfterLast(parentClass, "."));
		}

		return null;
	}

	private List<SchemaNode> getInterfaceSchemaNodes(final Map<String, SchemaNode> schemaNodes) throws FrameworkException {

		final List<SchemaNode> interfaces = new LinkedList<>();
		final String inheritsFrom         = getProperty(SchemaNode.implementsInterfaces);

		if (inheritsFrom != null) {

			for (final String iface : inheritsFrom.split("[,]+")) {

				final String trimmed = iface.trim();

				if (trimmed.length() > 0) {

					final SchemaNode node = schemaNodes.get(StringUtils.substringAfterLast(trimmed, "."));
					if (node != null && !node.equals(this)) {

						interfaces.add(node);
					}
				}
			}
		}

		return interfaces;
	}

	/**
	* If the system is fully initialized (and no schema replacement is currently active), we disallow overriding (known) existing types so we can prevent unwanted behavior.
	* If a user were to create a type 'Html', he could cripple Structrs Page rendering completely.
	* This is a fix for all types in the Structr context - this does not help if the user creates a type named 'String' or 'Object'.
	* That could still lead to unexpected behavior.
	*
	* @throws FrameworkException if a pre-existing type is encountered
	*/
	private void throwExceptionIfTypeAlreadyExists() throws FrameworkException {

		if (Services.getInstance().isInitialized() && ! Services.getInstance().isOverridingSchemaTypesAllowed()) {

			final String typeName = getProperty(name);

			// add type names to list of forbidden entity names
			if (EntityNameBlacklist.contains(typeName)) {
				throw new FrameworkException(422, "Type '" + typeName + "' already exists. To prevent unwanted/unexpected behavior this is forbidden.");
			}

			/*
			// add type names to list of forbidden entity names
			if (StructrApp.getConfiguration().getNodeEntities().containsKey(typeName)) {
				throw new FrameworkException(422, "Type '" + typeName + "' already exists. To prevent unwanted/unexpected behavior this is forbidden.");
			}

			// add interfaces to list of forbidden entity names
			if (StructrApp.getConfiguration().getInterfaces().containsKey(typeName)) {
				throw new FrameworkException(422, "Type '" + typeName + "' already exists. To prevent unwanted/unexpected behavior this is forbidden.");
			}
			*/
		}
	}
}
