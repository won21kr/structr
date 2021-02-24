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
package org.structr.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.SignatureException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class StructrLicenseVerifier {

	private static final Logger logger         = LoggerFactory.getLogger(StructrLicenseVerifier.class);

	private static final Pattern HostIdPattern = Pattern.compile("[a-f0-9]{32}");
	private static final Pattern DatePattern   = Pattern.compile("[0-9]{4}\\-[0-9]{2}\\-[0-9]{2}");
	private static final Pattern NamePattern   = Pattern.compile("[a-zA-Z0-9 \\-]+");

	private Gson gson           = null;
	private Signature signer    = null;
	private KeyStore keyStore   = null;
	private Cipher streamCipher = null;
	private Cipher blockCipher  = null;
	private Key key             = null;

	public static void main(final String[] args) {

		if (args.length < 2) {

			System.out.println("Parameters: keystoreFileName passwordFileName");
			System.exit(0);
		}

		final String keystoreFileName = args[0];
		final String passwordFileName = args[1];

		new StructrLicenseVerifier(keystoreFileName, passwordFileName).run();
	}

	private StructrLicenseVerifier(final String keystoreFileName, final String passwordFileName) {

		logger.info("Starting license server..");

		try {

			logger.info("Loading key store, initializing ciphers..");

			this.gson         = new GsonBuilder().setPrettyPrinting().create();
			this.keyStore     = KeyStore.getInstance(KeyStore.getDefaultType());
			this.blockCipher  = Cipher.getInstance(StructrLicenseManager.KeyEncryptionAlgorithm);
			this.streamCipher = Cipher.getInstance(StructrLicenseManager.DataEncryptionAlgorithm);
			this.signer       = Signature.getInstance(StructrLicenseManager.SignatureAlgorithm);

			try (final InputStream is = new FileInputStream(keystoreFileName)) {

				final String password = readPasswordFromFile(passwordFileName);
				final char[] pwd      = password.toCharArray();

				keyStore.load(is, pwd);

				this.key = keyStore.getKey("structr", pwd);

				blockCipher.init(Cipher.DECRYPT_MODE, key);
			}

		} catch (Throwable t) {
			logger.warn("Unable to initialize key store or ciphers: {}", t.getMessage());
		}
	}

	private void run() {

		try {

			logger.info("Listening on port {}", StructrLicenseManager.ServerPort);

			final ServerSocket serverSocket = new ServerSocket(StructrLicenseManager.ServerPort);

			serverSocket.setReuseAddress(true);

			// validation loop
			while (true) {

				try (final Socket socket = serverSocket.accept()) {

					logger.info("##### New connection from {}", socket.getInetAddress().getHostAddress());

					final InputStream is = socket.getInputStream();
					final int bufSize    = 4096;

					socket.setSoTimeout(2000);

					// decrypt AES stream key using RSA block cipher
					final byte[] sessionKey = blockCipher.doFinal(IOUtils.readFully(is, 256));
					final byte[] ivSpec     = blockCipher.doFinal(IOUtils.readFully(is, 256));
					final byte[] buf        = new byte[bufSize];
					int count               = 0;

					// initialize cipher using stream key
					streamCipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(sessionKey, "AES"), new IvParameterSpec(ivSpec));

					// we want to be able to control the number of bytes AND the timeout
					// of the underlying socket, so that we read the available amount of
					// data until the socket times out or we have read all the data.
					try {

						count = is.read(buf, 0, bufSize);

					} catch (IOException ioex) { }

					final byte[] decrypted        = streamCipher.doFinal(buf, 0, count);
					final String data             = new String(decrypted, "utf-8");

					// transform decrypted data into a Map<String, String>
					final List<Pair> pairs        = split(data).stream().map(StructrLicenseVerifier::keyValue).collect(Collectors.toList());
					final Map<String, String> map = pairs.stream().filter(Objects::nonNull).collect(Collectors.toMap(Pair::getLeft, Pair::getRight));

					// validate data against customer database
					if (isValid(map)) {

						// send signatur of name field back to client
						final String name     = (String)map.get(StructrLicenseManager.NameKey);
						final byte[] response = name.getBytes("utf-8");

						// respond with the signature of the data sent to us
						socket.getOutputStream().write(sign(response));
						socket.getOutputStream().flush();

					} else {

						logger.info("License verification failed.");
					}

					socket.getOutputStream().close();

				} catch (Throwable t) {
					logger.warn("Unable to verify license: {}", t.getMessage());
				}
			}

		} catch (Throwable t) {
			logger.warn("Unable to verify license: {}", t.getMessage());
		}
	}

	private boolean isValid(final Map<String, String> map) {

		final String toValidate       = StructrLicenseManager.collectLicenseFieldsForSignature(map);
		final String signature        = map.get(StructrLicenseManager.SignatureKey);
		final String startDate        = map.get(StructrLicenseManager.StartKey);
		final String licensee         = map.get(StructrLicenseManager.NameKey);
		final String hostId           = map.get(StructrLicenseManager.HostIdKey);
		boolean valid                 = false;

		if (StringUtils.isNotBlank(toValidate) && StringUtils.isNotBlank(signature)) {

			try {

				final byte[] signedData    = toValidate.getBytes("utf-8");
				final byte[] signatureData = Hex.decodeHex(signature.toCharArray());

				signer.initVerify(keyStore.getCertificate("structr"));
				signer.update(signedData);

				// verify signature of license data sent to us
				if (!signer.verify(signatureData)) {

					logger.info("Client signature not valid.");

					return false;
				}

			} catch (Throwable t) {

				logger.warn("Unable to verify client signature: {}", t.getMessage());

				return false;
			}

			// verify license contents
			if (StringUtils.isNotBlank(startDate) && StringUtils.isNotBlank(licensee) && StringUtils.isNotBlank(hostId)) {

				// make sure that date and licensee do not contain illegal characters
				if (DatePattern.matcher(startDate).matches() && NamePattern.matcher(licensee).matches() && HostIdPattern.matcher(hostId).matches()) {

					logger.info("License request for {}, start date {}, host ID {}", licensee, startDate, hostId);

					final String cleanedName = licensee.replace(" ", "-");
					final String configName  = startDate + "-" + cleanedName + ".json";

					logger.info("Loading license configuration from {}..", configName);

					// load config file or create new one
					Map<String, Object> config = readConfig(configName);
					if (config == null) {

						config = new HashMap<>();
					}

					// load count
					final Map<String, Object> hostIdMapping = getMapValue(config, StructrLicenseManager.HostIdMappingKey);
					final int limit                         = getIntValue(config, StructrLicenseManager.LimitKey, -1);
					final int hostIdCount                   = getIntValue(hostIdMapping, hostId, 0);
					final int count                         = hostIdMapping.size();

					if (limit == -1) {

						// no numerical limit found in config, check for "*" value
						valid = "*".equals(getStringValue(config, StructrLicenseManager.LimitKey, null));

						logger.info("count: {}, unlimited license", count);

					} else {

						valid = count <= limit;

						logger.info("count: {}, limit: {}", count, limit);
					}


					// update host ID count
					hostIdMapping.put(hostId, hostIdCount + 1);

					// store config
					writeConfig(configName, config);

				} else {

					logger.info("Client request malformed: {} {} {}", startDate, licensee, hostId);
				}

			} else {

				logger.info("Client request incomplete, missing startDate, licensee or hostId.");
			}

		} else {

			logger.info("Client request incomplete, missing data or signature.");
		}

		return valid;
	}

	private byte[] sign(final byte[] data) throws SignatureException, InvalidKeyException, NoSuchAlgorithmException {

		signer.initSign((PrivateKey)key);
		signer.update(data);

		return signer.sign();
	}

	private List<String> split(final String src) {
		return Arrays.asList(src.split("\n"));
	}

	private Map<String, Object> readConfig(final String name) {

		try (final FileReader reader = new FileReader(name)) {

			return gson.fromJson(reader, Map.class);

		} catch (IOException ioex) {
			logger.warn("Unable to open license config {}: {}", name, ioex.getMessage());
		}

		return null;
	}

	private void writeConfig(final String name, final Map<String, Object> config) {

		try (final FileWriter writer = new FileWriter(name)) {

			gson.toJson(config, writer);

		} catch (IOException ioex) {
			logger.warn("Unable to store license config {}: {}", name, ioex.getMessage());
		}
	}

	private int getIntValue(final Map<String, Object> data, final String key, final int defaultValue) {

		final Object src = data.get(key);
		if (src instanceof Number) {

			return ((Number)src).intValue();
		}

		return defaultValue;
	}

	private String getStringValue(final Map<String, Object> data, final String key, final String defaultValue) {

		final Object src = data.get(key);
		if (src instanceof String) {

			return (String)src;
		}

		return defaultValue;
	}

	private Map<String, Object> getMapValue(final Map<String, Object> data, final String key) {

		final Object src = data.get(key);
		if (src instanceof Map) {

			return (Map<String, Object>)src;
		}

		// create empty map
		final Map<String, Object> newMap = new HashMap<>();

		// store map in data object
		data.put(key, newMap);

		return newMap;

	}

	private String readPasswordFromFile(final String passwordFileName) throws IOException {

		try (final InputStream is = new FileInputStream(passwordFileName)) {

			final List<String> lines = IOUtils.readLines(is, "utf-8");

			if (!lines.isEmpty()) {

				return lines.get(0);
			}
		}

		return "";
	}

	// ----- private static members -----
	private static Pair keyValue(final String line) {

		final String[] parts = line.split("=", 2);

		if (parts.length == 2) {

			final String key   = parts[0].trim();
			final String value = parts[1].trim();

			return new Pair(key, value);
		}

		// ignore invalid lines
		return null;
	}

	private static class Pair {

		private String left  = null;
		private String right = null;

		public Pair(final String left, final String right) {
			this.left = left;
			this.right = right;
		}

		public String getLeft() {
			return left;
		}

		public String getRight() {
			return right;
		}
	}
}
