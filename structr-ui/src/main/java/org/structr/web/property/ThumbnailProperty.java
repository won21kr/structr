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
package org.structr.web.property;

import org.apache.commons.lang3.StringUtils;
import org.structr.api.Predicate;
import org.structr.api.search.SortType;
import org.structr.common.SecurityContext;
import org.structr.core.GraphObject;
import org.structr.core.property.AbstractReadOnlyProperty;
import org.structr.core.property.Property;
import org.structr.web.entity.Image;

/**
 * A property that automatically creates a thumbnail for an image.
 */
public class ThumbnailProperty extends AbstractReadOnlyProperty<Image> {

	private int width    = 0;
	private int height   = 0;
	private boolean crop = false;


	public ThumbnailProperty(final String name) {

		super(name);

		this.unvalidated = true;

	}

	@Override
	public Image getProperty(SecurityContext securityContext, GraphObject obj, boolean applyConverter) {
		return getProperty(securityContext, obj, applyConverter, null);
	}

	@Override
	public Image getProperty(SecurityContext securityContext, GraphObject obj, boolean applyConverter, Predicate<GraphObject> predicate) {

		if (obj instanceof Image && ((Image)obj).isThumbnail()) {
			return null;
		}

		return ((Image)obj).getScaledImage(width, height, crop);
	}

	@Override
	public Class relatedType() {
		return Image.class;
	}

	@Override
	public Class valueType() {
		return relatedType();
	}

	@Override
	public boolean isCollection() {
		return false;
	}

	@Override
	public SortType getSortType() {
		return null;
	}

	@Override
	public Property<Image> format(final String format) {

		if (StringUtils.isNotBlank(format) && format.contains(",")) {

			final String[] parts = format.split("[, ]+");

			if (parts.length >= 1) {

				width    = Integer.parseInt(parts[0].trim());
				height   = Integer.parseInt(parts[1].trim());
			}

			if (parts.length == 3) {

				crop = Boolean.parseBoolean(parts[2].trim());
			}
		}

		return this;
	}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}

	public boolean getCrop() {
		return crop;
	}

	@Override
	public boolean isIndexed() {
		return false;
	}

	@Override
	public boolean isPassivelyIndexed() {
		return false;
	}

	// ----- OpenAPI -----
	@Override
	public Object getExampleValue(final String type, final String viewName) {
		return null;
	}
}
