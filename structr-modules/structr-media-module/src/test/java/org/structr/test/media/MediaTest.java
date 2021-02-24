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
package org.structr.test.media;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.filter.log.ResponseLoggingFilter;
import java.io.InputStream;
import static org.hamcrest.CoreMatchers.equalTo;
import org.testng.annotations.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.structr.core.app.StructrApp;
import org.structr.core.graph.NodeAttribute;
import org.structr.core.graph.Tx;
import org.structr.media.AVConv;
import org.structr.test.web.StructrUiTest;
import org.structr.web.common.FileHelper;
import org.structr.web.entity.User;

/**
 */
public class MediaTest extends StructrUiTest {

	private static final Logger logger = LoggerFactory.getLogger(MediaTest.class.getName());

	@Test
	public void test01() {

		if (!AVConv.isAVConvInstalled()) {
			logger.info("Not performing test because `avconv` is not installed!");
			return;
		}

		final Class type = StructrApp.getConfiguration().getNodeEntityClass("VideoFile");

		try (final Tx tx = app.tx()) {

			app.create(User.class,
				new NodeAttribute<>(StructrApp.key(User.class, "name"),     "admin"),
				new NodeAttribute<>(StructrApp.key(User.class, "password"), "admin"),
				new NodeAttribute<>(StructrApp.key(User.class, "isAdmin"),  true)
			);

			// create AutoClosable input stream
			try (final InputStream is = MediaTest.class.getResourceAsStream("/test.mp4")) {
				FileHelper.createFile(securityContext, is, null, type, "test.mp4");
			}

			tx.success();

		} catch (Throwable t) {
			t.printStackTrace();
		}


		// use RestAssured to check file
		RestAssured
			.given()
			.filter(ResponseLoggingFilter.logResponseTo(System.out))
			.header("X-User", "admin")
			.header("X-Password", "admin")
			.expect()
			.statusCode(200)
			.body("result[0].type",                  equalTo("VideoFile"))
			.body("result[0].name",                  equalTo("test.mp4"))
			.body("result[0].path",                  equalTo("/test.mp4"))
			.body("result[0].checksum",              equalTo(3346681520328299771L))
			.body("result[0].size",                  equalTo(91960))
			.body("result[0].duration",              equalTo(34.77f))
			.body("result[0].width",                 equalTo(18))
			.body("result[0].height",                equalTo(10))
			.body("result[0].pixelFormat",           equalTo("yuv420p"))
			.body("result[0].videoCodec",            equalTo("h264"))
			.body("result[0].videoCodecName",        equalTo("H.264 / AVC / MPEG-4 AVC / MPEG-4 part 10"))
			.when()
			.get("/VideoFile");

	}
}
