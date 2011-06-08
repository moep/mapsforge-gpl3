/*
 * Copyright 2010, 2011 mapsforge.org
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.mapsforge.poi.persistence;

/**
 * Used when packing indexes implementing {@link RtreeIndex}.
 * 
 * @author weise
 * 
 * @param <S>
 *            shape class implementing {@link SpatialShape}.
 * @param <T>
 *            indexed item.
 */
class PackEntry<S extends SpatialShape<S>, T> {
	public S shape;
	public T obj;

	public PackEntry(S shape, T obj) {
		super();
		this.shape = shape;
		this.obj = obj;
	}
}
