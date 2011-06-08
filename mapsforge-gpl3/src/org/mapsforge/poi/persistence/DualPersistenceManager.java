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

import java.util.Collection;
import java.util.Iterator;

import org.mapsforge.core.GeoCoordinate;
import org.mapsforge.poi.PoiCategory;
import org.mapsforge.poi.PointOfInterest;

class DualPersistenceManager implements IPersistenceManager {

	private final PostGisPersistenceManager pgManager;
	private final MultiRtreePersistenceManager perstManager;

	public DualPersistenceManager(PostGisPersistenceManager pgManager,
			MultiRtreePersistenceManager perstManager) {
		this.pgManager = pgManager;
		this.perstManager = perstManager;
	}

	@Override
	public Collection<PoiCategory> allCategories() {
		return perstManager.allCategories();
	}

	@Override
	public void close() {
		pgManager.close();
		perstManager.close();
	}

	@Override
	public Collection<PoiCategory> descendants(String category) {
		return perstManager.descendants(category);
	}

	@Override
	public boolean insertCategory(PoiCategory category) {
		pgManager.insertCategory(category);
		perstManager.insertCategory(category);
		return true;
	}

	@Override
	public void insertPointOfInterest(PointOfInterest poi) {
		perstManager.insertPointOfInterest(poi);
		pgManager.insertPointOfInterest(poi);
	}

	@Override
	public void insertPointsOfInterest(Collection<PointOfInterest> pois) {
		perstManager.insertPointsOfInterest(pois);
		pgManager.insertPointsOfInterest(pois);
	}

	@Override
	public void removeCategory(PoiCategory category) {
		perstManager.removeCategory(category);
		pgManager.removeCategory(category);
	}

	@Override
	public void removePointOfInterest(PointOfInterest poi) {
		perstManager.removePointOfInterest(poi);
		pgManager.removePointOfInterest(poi);
	}

	@Override
	public Collection<PointOfInterest> findInRect(GeoCoordinate p1, GeoCoordinate p2,
			String categoryName) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Collection<PointOfInterest> findNearPosition(GeoCoordinate point, int distance,
			String categoryName, int limit) {
		return perstManager.findNearPosition(point, distance, categoryName, limit);
	}

	@Override
	public PointOfInterest getPointById(long poiId) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void clusterStorage() {
		perstManager.clusterStorage();
	}

	@Override
	public void packIndex() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Iterator<PointOfInterest> neighborIterator(GeoCoordinate geoCoordinate,
			String category) {
		return perstManager.neighborIterator(geoCoordinate, category);
	}

}
