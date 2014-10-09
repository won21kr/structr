/**
 * Copyright (C) 2010-2014 Morgner UG (haftungsbeschränkt)
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
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Structr.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.structr.cloud.transmission;

import java.io.IOException;
import java.util.Set;
import org.structr.cloud.CloudConnection;
import org.structr.cloud.CloudService;
import org.structr.cloud.ExportSet;
import org.structr.cloud.message.FileNodeChunk;
import org.structr.cloud.message.FileNodeDataContainer;
import org.structr.cloud.message.FileNodeEndChunk;
import org.structr.cloud.message.NodeDataContainer;
import org.structr.cloud.message.PushNodeRequestContainer;
import org.structr.cloud.message.RelationshipDataContainer;
import org.structr.common.Syncable;
import org.structr.common.error.FrameworkException;
import org.structr.core.graph.NodeInterface;
import org.structr.core.graph.RelationshipInterface;
import org.structr.dynamic.File;

/**
 *
 * @author Christian Morgner
 */
public class PushTransmission extends AbstractTransmission<Boolean> {

	private ExportSet exportSet = null;
	private int sequenceNumber  = 0;

	public PushTransmission(final Syncable sourceNode, final boolean recursive, final String userName, final String password, final String remoteHost, final int port) {

		super(userName, password, remoteHost, port);

		// create export set before first progress callback is called
		// so the client gets the correct total from the beginning
		exportSet = ExportSet.getInstance(sourceNode, recursive);
	}

	public PushTransmission(final String userName, final String password, final String remoteHost, final int port) {

		super(userName, password, remoteHost, port);

		exportSet = ExportSet.getInstance();
	}

	public ExportSet getExportSet() {
		return exportSet;
	}

	@Override
	public int getTotalSize() {
		return exportSet.getTotalSize() + 1;
	}

	@Override
	public Boolean doRemote(final CloudConnection client) throws IOException, FrameworkException {

		// send type of request
		client.send(new PushNodeRequestContainer());

		// reset sequence number
		sequenceNumber = 0;

		// send child nodes when recursive sending is requested
		final Set<NodeInterface> nodes = exportSet.getNodes();
		for (final NodeInterface n : nodes) {

			if (n instanceof File) {
				sendFile(client, (File)n, CloudService.CHUNK_SIZE);

			} else {

				client.send(new NodeDataContainer(n, sequenceNumber++));
			}
		}

		// send relationships
		Set<RelationshipInterface> rels = exportSet.getRelationships();
		for (RelationshipInterface r : rels) {

			if (nodes.contains(r.getSourceNode()) && nodes.contains(r.getTargetNode())) {
				client.send(new RelationshipDataContainer(r, sequenceNumber++));
			}
		}

		// wait for end of transmission
		client.waitForTransmission();

		return true;
	}

	/**
	 * Splits the given file and sends it over the client connection. This method first creates a <code>FileNodeDataContainer</code> and sends it to the remote end. The file from disk is then
	 * split into multiple instances of <code>FileChunkContainer</code> while being sent. To finalize the transfer, a <code>FileNodeEndChunk</code> is sent to notify the receiving end of the
	 * successful transfer.
	 *
	 * @param client the client to send over
	 * @param file the file to split and send
	 * @param chunkSize the chunk size for a single chunk
	 * @throws org.structr.common.error.FrameworkException
	 * @throws java.io.IOException
	 */
	public static void sendFile(final CloudConnection client, final File file, final int chunkSize) throws FrameworkException, IOException {

		// send file container first
		FileNodeDataContainer container = new FileNodeDataContainer(file);
		client.send(container);

		// send chunks
		for (FileNodeChunk chunk : FileNodeDataContainer.getChunks(file, chunkSize)) {
			client.send(chunk);
		}

		// mark end of file with special chunk
		client.send(new FileNodeEndChunk(container.getSourceNodeId(), container.getFileSize()));
	}
}
