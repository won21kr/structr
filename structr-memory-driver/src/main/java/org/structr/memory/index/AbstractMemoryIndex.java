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
package org.structr.memory.index;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.structr.api.DatabaseService;
import org.structr.api.graph.PropertyContainer;
import org.structr.api.index.AbstractIndex;
import org.structr.api.index.QueryFactory;
import org.structr.api.search.ArrayQuery;
import org.structr.api.search.ComparisonQuery;
import org.structr.api.search.EmptyQuery;
import org.structr.api.search.ExactQuery;
import org.structr.api.search.FulltextQuery;
import org.structr.api.search.GraphQuery;
import org.structr.api.search.GroupQuery;
import org.structr.api.search.NotEmptyQuery;
import org.structr.api.search.QueryContext;
import org.structr.api.search.RangeQuery;
import org.structr.api.search.RelationshipQuery;
import org.structr.api.search.SpatialQuery;
import org.structr.api.search.TypeConverter;
import org.structr.api.search.TypeQuery;
import org.structr.api.search.UuidQuery;
import org.structr.memory.MemoryDatabaseService;
import org.structr.memory.index.converter.BooleanTypeConverter;
import org.structr.memory.index.converter.ByteTypeConverter;
import org.structr.memory.index.converter.DateTypeConverter;
import org.structr.memory.index.converter.DoubleTypeConverter;
import org.structr.memory.index.converter.FloatTypeConverter;
import org.structr.memory.index.converter.IntTypeConverter;
import org.structr.memory.index.converter.LongTypeConverter;
import org.structr.memory.index.converter.ShortTypeConverter;
import org.structr.memory.index.converter.StringTypeConverter;
import org.structr.memory.index.factory.ArrayQueryFactory;
import org.structr.memory.index.factory.ComparisonQueryFactory;
import org.structr.memory.index.factory.EmptyQueryFactory;
import org.structr.memory.index.factory.GraphQueryFactory;
import org.structr.memory.index.factory.GroupQueryFactory;
import org.structr.memory.index.factory.KeywordQueryFactory;
import org.structr.memory.index.factory.NotEmptyQueryFactory;
import org.structr.memory.index.factory.RangeQueryFactory;
import org.structr.memory.index.factory.RelationshipQueryFactory;
import org.structr.memory.index.factory.SpatialQueryFactory;
import org.structr.memory.index.factory.TypeQueryFactory;
import org.structr.memory.index.factory.UuidQueryFactory;

/**
 *
 */
public abstract class AbstractMemoryIndex<T extends PropertyContainer> extends AbstractIndex<MemoryQuery, T> {

	private final Map<Class, TypeConverter> converters = new HashMap<>();
	private final Map<Class, QueryFactory> factories   = new HashMap<>();
	protected MemoryDatabaseService db                 = null;

	public AbstractMemoryIndex(final MemoryDatabaseService db) {

		super();

		this.db = db;

		init();
	}

	@Override
	public MemoryQuery createQuery(final QueryContext context, final int pageSize, final int page) {
		return new MemoryQuery(context);
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

	@Override
	public boolean supports(final Class type) {
		return converters.containsKey(type);
	}

	// ----- private methods -----
	private void init() {

		factories.put(NotEmptyQuery.class,     new NotEmptyQueryFactory(this));
		factories.put(FulltextQuery.class,     new KeywordQueryFactory(this));
		factories.put(SpatialQuery.class,      new SpatialQueryFactory(this));
		factories.put(GraphQuery.class,        new GraphQueryFactory(this));
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
