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
package org.structr.pdf.function;

import com.github.jhonnymertz.wkhtmltopdf.wrapper.PDFExportException;
import com.github.jhonnymertz.wkhtmltopdf.wrapper.Pdf;
import com.github.jhonnymertz.wkhtmltopdf.wrapper.configurations.WrapperConfig;
import com.github.jhonnymertz.wkhtmltopdf.wrapper.configurations.XvfbConfig;
import com.github.jhonnymertz.wkhtmltopdf.wrapper.params.Param;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.structr.api.config.Settings;
import org.structr.common.error.FrameworkException;
import org.structr.core.entity.Principal;
import org.structr.core.entity.SuperUser;
import org.structr.schema.action.ActionContext;
import org.structr.schema.action.Function;

public class PDFFunction extends Function<Object, Object> {

	public static final String ERROR_MESSAGE_PDF = "Usage: ${ pdf(page [, wkhtmltopdfParameter, baseUrl, runWithXServer, xServerSettings]) }";
	public static final String ERROR_MESSAGE_PDF_JS = "Usage: ${{ Structr.pdf(page [, wkhtmltopdfParameter, baseUrl, runWithXServer, xServerSettings]); }}";

	@Override
	public String getName() {
		return "pdf";
	}

	@Override
	public String getSignature() {
		return "pageName [, wkthtmlParams, baseUrl, runWithX, xSettings ]";
	}

	@Override
	public String getRequiredModule() {
		return "pdf";
	}

	@Override
	public Object apply(ActionContext ctx, Object caller, Object[] sources) throws FrameworkException {

		assertArrayHasMinLengthAndAllElementsNotNull(sources, 1);

		String baseUrl = null;
		String userParamter = null;

		Boolean runWithXserver = false;
		String xServerSettings = null;

		final String page = sources[0].toString();

		if (sources.length >= 2) {

			userParamter = sources[1].toString();
		}

		if (sources.length >= 3) {

			 baseUrl = sources[2].toString();
		}

		if (sources.length >= 4) {

			runWithXserver = (Boolean) sources[3];
		}

		if (sources.length >= 5) {

			xServerSettings = sources[4].toString();
		}

		if (baseUrl == null || baseUrl.length() == 0) {

			baseUrl = ActionContext.getBaseUrl(ctx.getSecurityContext().getRequest()) + "/";
		}

		Principal currentUser = ctx.getSecurityContext().getUser(false);

		List<Param> parameterList = new ArrayList<Param>();

		if (currentUser instanceof SuperUser) {
			parameterList.add(new Param("--custom-header X-User superadmin --custom-header X-Password " + Settings.SuperUserPassword.getValue()));
			parameterList.add(new Param("--custom-header-propagation"));

		} else {
			parameterList.add(new Param("--cookie JSESSIONID " + ctx.getSecurityContext().getSessionId()));

		}

		if (userParamter != null) {
			parameterList.add(new Param(userParamter));
		}

		final ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {

			if (!runWithXserver) {
				return convertPageToPdfWithoutXServer(baseUrl, page, parameterList, baos);
			} else {
				return convertPageToPdfWithXServer(baseUrl, page, parameterList, baos, xServerSettings);
			}

		} catch (PDFExportException e) {

			logger.warn("Could not convert page {}{} to pdf... retrying with xvfb...", baseUrl, page);

			return convertPageToPdfWithXServer(baseUrl, page, parameterList, baos, xServerSettings);
		}
	}

	private  String convertPageToPdfWithoutXServer (String baseUrl, String page, List<Param> parameterList, ByteArrayOutputStream baos) {
		Pdf pdf = new Pdf();
		pdf.addPageFromUrl(baseUrl + page);
		addParametersToPdf(pdf, parameterList);

		return convertPageToPdf(pdf, baos);
	}

	private String convertPageToPdfWithXServer (String baseUrl, String page, List<Param> parameterList, ByteArrayOutputStream baos, String xServerSettings) {
		XvfbConfig xc = new XvfbConfig();

		if (xServerSettings == null || xServerSettings.length() == 0) {

			xc.addParams(new Param("--auto-servernum"), new Param("--server-num=1"));
		} else {

			xc.addParams(new Param(xServerSettings));
		}

		WrapperConfig wc = new WrapperConfig();
		wc.setXvfbConfig(xc);

		Pdf pdf = new Pdf(wc);
		pdf.addPageFromUrl(baseUrl + page);
		addParametersToPdf(pdf, parameterList);

		return convertPageToPdf(pdf, baos);
	}

	private String convertPageToPdf (Pdf pdf, ByteArrayOutputStream baos) {
		try {
			baos.write(pdf.getPDF());
			return baos.toString("ISO-8859-1");
		} catch (IOException e) {

			logger.warn("pdf(): IOException", e);

		} catch (InterruptedException e) {

			logger.warn("pdf(): InterruptedException", e);

		}
		return "";
	}

	private void addParametersToPdf (Pdf pdf, List<Param> paramList) {
		for (Param param : paramList) {
			pdf.addParam(param);
		}
	}

	@Override
	public String usage(boolean inJavaScriptContext) {

		return (inJavaScriptContext ? ERROR_MESSAGE_PDF_JS : ERROR_MESSAGE_PDF);
	}

	@Override
	public String shortDescription() {
		return "Creates the PDF representation of a given page.";
	}
}
