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
package org.structr.files.ssh.filesystem.path.schema;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.structr.common.error.FrameworkException;
import org.structr.core.app.App;
import org.structr.core.app.StructrApp;
import org.structr.core.entity.AbstractNode;
import org.structr.core.entity.SchemaNode;
import org.structr.core.entity.SchemaRelationshipNode;
import org.structr.core.graph.Tx;
import org.structr.files.ssh.filesystem.StructrFilesystem;
import org.structr.files.ssh.filesystem.StructrPath;
import org.structr.files.ssh.filesystem.StructrToplevelAttributes;

/**
 *
 */
public class StructrSchemaPath extends StructrPath {

	private static final Logger logger = LoggerFactory.getLogger(StructrSchemaPath.class.getName());

	public StructrSchemaPath(StructrFilesystem fs, StructrPath parent) {
		super(fs, parent, StructrPath.SCHEMA_DIRECTORY);
	}

	@Override
	public FileChannel newFileChannel(Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
		throw new AccessDeniedException(toString());
	}

	@Override
	public DirectoryStream<Path> getDirectoryStream(final DirectoryStream.Filter<? super Path> filter) {

		return new DirectoryStream() {

			boolean closed = false;

			@Override
			public Iterator iterator() {

				if (!closed) {

					final App app                 = StructrApp.getInstance(fs.getSecurityContext());
					final List<StructrPath> nodes = new LinkedList<>();

					try (final Tx tx = app.tx()) {

						for (final SchemaNode schemaNode : app.nodeQuery(SchemaNode.class).sort(AbstractNode.name).getAsList()) {

							nodes.add(new StructrSchemaNodePath(fs, StructrSchemaPath.this, schemaNode.getName()));
						}

						for (final SchemaRelationshipNode rel : app.nodeQuery(SchemaRelationshipNode.class).sort(AbstractNode.name).getAsList()) {

							nodes.add(new StructrSchemaNodePath(fs, StructrSchemaPath.this, rel.getName()));
						}

						tx.success();

					} catch (FrameworkException fex) {
						logger.warn("", fex);
					}

					return nodes.iterator();

				}

				return Collections.emptyIterator();
			}

			@Override
			public void close() throws IOException {
				closed = true;
			}
		};
	}

	@Override
	public void createDirectory(FileAttribute<?>... attrs) throws IOException {
		throw new FileAlreadyExistsException(this.toString());
	}

	@Override
	public void delete() throws IOException {
		throw new AccessDeniedException(toString());
	}

	@Override
	public StructrPath resolveStructrPath(final String pathComponent) {
		return new StructrSchemaNodePath(fs, this, pathComponent);
	}

	@Override
	public Map<String, Object> getAttributes(final String attributes, final LinkOption... options) {
		return new StructrToplevelAttributes(StructrPath.SCHEMA_DIRECTORY).toMap(attributes);
	}

	@Override
	public <T extends BasicFileAttributes> T getAttributes(Class<T> type, LinkOption... options) {
		return (T)new StructrToplevelAttributes(StructrPath.SCHEMA_DIRECTORY);
	}

	@Override
	public <V extends FileAttributeView> V getFileAttributeView(final Class<V> type, final LinkOption... options) throws IOException {
		return (V)getAttributes((Class)null, options);
	}

	@Override
	public void copy(final Path target, final CopyOption... options) throws IOException {
		throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
	}

	@Override
	public void move(final Path target, final CopyOption... options) throws IOException {
		throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
	}

	@Override
	public void setAttribute(final String attribute, final Object value, final LinkOption... options) throws IOException {
		throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
	}

	@Override
	public boolean isSameFile(final Path path2) throws IOException {
		throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
	}
}
