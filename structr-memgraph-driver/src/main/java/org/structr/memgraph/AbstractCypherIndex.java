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
package org.structr.memgraph;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.structr.api.DatabaseService;
import org.structr.api.graph.PropertyContainer;
import org.structr.api.index.AbstractIndex;
import org.structr.api.index.QueryFactory;
import org.structr.api.search.*;
import org.structr.memgraph.converter.BooleanTypeConverter;
import org.structr.memgraph.converter.ByteTypeConverter;
import org.structr.memgraph.converter.DateTypeConverter;
import org.structr.memgraph.converter.DoubleTypeConverter;
import org.structr.memgraph.converter.FloatTypeConverter;
import org.structr.memgraph.converter.IntTypeConverter;
import org.structr.memgraph.converter.LongTypeConverter;
import org.structr.memgraph.converter.ShortTypeConverter;
import org.structr.memgraph.converter.StringTypeConverter;
import org.structr.memgraph.factory.ArrayQueryFactory;
import org.structr.memgraph.factory.ComparisonQueryFactory;
import org.structr.memgraph.factory.EmptyQueryFactory;
import org.structr.memgraph.factory.GroupQueryFactory;
import org.structr.memgraph.factory.KeywordQueryFactory;
import org.structr.memgraph.factory.NotEmptyQueryFactory;
import org.structr.memgraph.factory.RangeQueryFactory;
import org.structr.memgraph.factory.RelationshipQueryFactory;
import org.structr.memgraph.factory.SpatialQueryFactory;
import org.structr.memgraph.factory.TypeQueryFactory;
import org.structr.memgraph.factory.UuidQueryFactory;

/**
 *
 */
abstract class AbstractCypherIndex<T extends PropertyContainer> extends AbstractIndex<AdvancedCypherQuery, T> {

	private static final Logger logger = LoggerFactory.getLogger(AbstractCypherIndex.class.getName());

	public static final Set<Class> INDEXABLE = new HashSet<>(Arrays.asList(new Class[] {
		String.class,   Boolean.class,   Short.class,   Integer.class,   Long.class,   Character.class,   Float.class,   Double.class,   byte.class,
		String[].class, Boolean[].class, Short[].class, Integer[].class, Long[].class, Character[].class, Float[].class, Double[].class, byte[].class
	}));

	private final Map<Class, TypeConverter> converters = new HashMap<>();
	private final Map<Class, QueryFactory> factories   = new HashMap<>();
	protected MemgraphDatabaseService db                   = null;

	public abstract String getQueryPrefix(final String mainType, final String sourceTypeLabel, final String targetTypeLabel, final boolean hasPredicates);
	public abstract String getQuerySuffix(final AdvancedCypherQuery query);

	public AbstractCypherIndex(final MemgraphDatabaseService db) {

		this.db = db;

		init();
	}

	@Override
	public AdvancedCypherQuery createQuery(final QueryContext context, final int requestedPageSize, final int requestedPage) {
		return new AdvancedCypherQuery(context, this, requestedPageSize, requestedPage);
	}

	@Override
	public QueryFactory getFactoryForType(final Class type) {
		return factories.get(type);
	}

	@Override
	public TypeConverter getConverterForType(final Class type) {
		return converters.get(type);
	}

	@Override
	public DatabaseService getDatabaseService() {
		return db;
	}

	public boolean supports(final Class type) {
		return INDEXABLE.contains(type);
	}

	public String anyOrSingleFunction() {
		return db.anyOrSingleFunction();
	}

	// ----- private methods -----
	private void init() {

		factories.put(NotEmptyQuery.class,     new NotEmptyQueryFactory(this));
		factories.put(FulltextQuery.class,     new KeywordQueryFactory(this));
		factories.put(SpatialQuery.class,      new SpatialQueryFactory(this));
		factories.put(GroupQuery.class,        new GroupQueryFactory(this));
		factories.put(RangeQuery.class,        new RangeQueryFactory(this));
		factories.put(ExactQuery.class,        new KeywordQueryFactory(this));
		factories.put(ArrayQuery.class,        new ArrayQueryFactory(this));
		factories.put(EmptyQuery.class,        new EmptyQueryFactory(this));
		factories.put(TypeQuery.class,         new TypeQueryFactory(this));
		factories.put(UuidQuery.class,         new UuidQueryFactory(this));
		factories.put(RelationshipQuery.class, new RelationshipQueryFactory(this));
		factories.put(ComparisonQuery.class,   new ComparisonQueryFactory(this));

		converters.put(Boolean.class, new BooleanTypeConverter());
		converters.put(String.class,  new StringTypeConverter());
		converters.put(Date.class,    new DateTypeConverter());
		converters.put(Long.class,    new LongTypeConverter());
		converters.put(Short.class,   new ShortTypeConverter());
		converters.put(Integer.class, new IntTypeConverter());
		converters.put(Float.class,   new FloatTypeConverter());
		converters.put(Double.class,  new DoubleTypeConverter());
		converters.put(byte.class,    new ByteTypeConverter());
	}
}
