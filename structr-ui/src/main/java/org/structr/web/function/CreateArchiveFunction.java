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
package org.structr.web.function;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.structr.common.error.FrameworkException;
import org.structr.core.app.StructrApp;
import org.structr.schema.ConfigurationProvider;
import org.structr.schema.action.ActionContext;
import org.structr.web.common.FileHelper;
import org.structr.web.entity.AbstractFile;
import org.structr.web.entity.File;
import org.structr.web.entity.Folder;

public class CreateArchiveFunction extends UiAdvancedFunction {

	public static final String ERROR_MESSAGE_CREATE_ARCHIVE    = "Usage: ${create_archive(archiveFileName, files [, CustomFileType])}. Example: ${create_archive(\"archive\", find(\"File\"))}";
	public static final String ERROR_MESSAGE_CREATE_ARCHIVE_JS = "Usage: ${{Structr.createArchive(archiveFileName, files [, CustomFileType])}}. Example: ${{Structr.createArchive(\"archive\", Structr.find(\"File\"))}}";

	@Override
	public String getName() {
		return "create_archive";
	}

	@Override
	public String getSignature() {
		return "fileName, files [, customFileTypeName ]";
	}

	@Override
	public Object apply(ActionContext ctx, Object caller, Object[] sources) throws FrameworkException {

		if (!(sources[1] instanceof File || sources[1] instanceof Folder || sources[1] instanceof Collection || sources.length < 2)) {

			logParameterError(caller, sources, ctx.isJavaScriptContext());

			return usage(ctx.isJavaScriptContext());
		}

		final ConfigurationProvider config    = StructrApp.getConfiguration();

		try {

			java.io.File newArchive = java.io.File.createTempFile(sources[0].toString(), "zip");

			ZipArchiveOutputStream zaps = new ZipArchiveOutputStream(newArchive);
			zaps.setEncoding("UTF8");
			zaps.setUseLanguageEncodingFlag(true);
			zaps.setCreateUnicodeExtraFields(ZipArchiveOutputStream.UnicodeExtraFieldPolicy.ALWAYS);
			zaps.setFallbackToUTF8(true);

			if (sources[1] instanceof File) {

				File file = (File) sources[1];
				addFileToZipArchive(file.getProperty(AbstractFile.name), file, zaps);

			} else if (sources[1] instanceof Folder) {

				Folder folder = (Folder) sources[1];
				addFilesToArchive(folder.getProperty(Folder.name) + "/", folder.getFiles(), zaps);
				addFoldersToArchive(folder.getProperty(Folder.name) + "/", folder.getFolders(), zaps);

			} else 	if (sources[1] instanceof Collection) {

				for (Object fileOrFolder : (Collection) sources[1]) {

					if (fileOrFolder instanceof File) {

						File file = (File) fileOrFolder;
						addFileToZipArchive(file.getProperty(AbstractFile.name), file, zaps);
					} else if (fileOrFolder instanceof Folder) {

						Folder folder = (Folder) fileOrFolder;
						addFilesToArchive(folder.getProperty(Folder.name) + "/", folder.getFiles(), zaps);
						addFoldersToArchive(folder.getProperty(Folder.name) + "/", folder.getFolders(), zaps);
					} else {

						logParameterError(caller, sources, ctx.isJavaScriptContext());
						return usage(ctx.isJavaScriptContext());
					}
				}
			} else {

				logParameterError(caller, sources, ctx.isJavaScriptContext());
				return usage(ctx.isJavaScriptContext());
			}

			zaps.close();

			Class archiveClass = null;

			if (sources.length > 2) {

				archiveClass = config.getNodeEntityClass(sources[2].toString());

			}

			if(archiveClass == null) {

				archiveClass = org.structr.web.entity.File.class;
			}

			try (final FileInputStream fis = new FileInputStream(newArchive)) {
				return FileHelper.createFile(ctx.getSecurityContext(), fis, "application/zip", archiveClass, sources[0].toString() + ".zip");
			}

		} catch (IOException e) {

			logException(caller, e, sources);
		}
		return null;
	}

	@Override
	public String usage(boolean inJavaScriptContext) {

		return (inJavaScriptContext ? ERROR_MESSAGE_CREATE_ARCHIVE_JS : ERROR_MESSAGE_CREATE_ARCHIVE);
	}

	@Override
	public String shortDescription() {

		return "Packs the given files and folders into zipped archive.";
	}

	private void addFileToZipArchive(String path, File file, ArchiveOutputStream aps) throws IOException {

		logger.info("Adding File \"{}\" to new archive...", path);

		ZipArchiveEntry entry = new ZipArchiveEntry(path);
		aps.putArchiveEntry(entry);

		try (final InputStream in = file.getInputStream()) {

			IOUtils.copy(in, aps);
		}

		aps.closeArchiveEntry();
	}

	private void addFilesToArchive(String path, Iterable<File> list, ArchiveOutputStream aps) throws IOException {

		for(File fileForArchive : list) {

			addFileToZipArchive(path + fileForArchive.getProperty(AbstractFile.name), fileForArchive,  aps);
		}
	}

	private void addFoldersToArchive(String path, Iterable<Folder> list, ArchiveOutputStream aps) throws IOException {

		for(Folder folder : list) {

			addFilesToArchive(path + folder.getProperty(Folder.name) + "/", folder.getFiles(), aps);
			addFoldersToArchive(path + folder.getProperty(Folder.name) + "/", folder.getFolders(), aps);
		}
	}
}