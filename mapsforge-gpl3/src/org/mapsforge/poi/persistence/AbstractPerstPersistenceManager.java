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

import org.garret.perst.FieldIndex;
import org.garret.perst.IterableIterator;
import org.garret.perst.Key;
import org.garret.perst.Storage;
import org.garret.perst.StorageFactory;
import org.mapsforge.core.GeoCoordinate;
import org.mapsforge.poi.PoiCategory;
import org.mapsforge.poi.PointOfInterest;

abstract class AbstractPerstPersistenceManager<T extends BasicRootElement> implements
		IPersistenceManager {

	static class PoiNeighborIterator implements Iterator<PointOfInterest> {

		private IterableIterator<PerstPoi> iterator;

		public PoiNeighborIterator(IterableIterator<PerstPoi> iterator) {
			super();
			this.iterator = iterator;
		}

		@Override
		public boolean hasNext() {
			return iterator.hasNext();
		}

		@Override
		public PointOfInterest next() {
			return iterator.next();
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}
	}

	protected static final int CATEGORY_CLUSTER_SPREAD_FACTOR = 1000000;
	protected static final double SPACE_BETWEEN_LATS_IN_KM = 111.32;
	protected static final long PAGE_POOL_SIZE = 3 * 1024 * 1024;
	protected static final int MAX_POIS = 20000;

	protected T root;
	protected Storage db;
	protected String fileName;
	protected PerstPoiCategoryManager categoryManager;

	public AbstractPerstPersistenceManager(String storageFileName) {
		this.fileName = storageFileName;
		open();
	}

	protected void open() {
		db = StorageFactory.getInstance().createStorage();
		// db.setProperty("perst.string.encoding", "UTF-8");
		db.open(this.fileName, PAGE_POOL_SIZE);

		/* db.getRoot() returns object. Must be cast to root object T */
		T tmpRoot = (T) db.getRoot();

		if (tmpRoot == null) {
			root = initRootElement(db);
			db.setRoot(root);
		} else {
			root = tmpRoot;
		}

		categoryManager = PerstPoiCategoryManager.getInstance(root.categoryTitlePkIndex);
	}

	protected abstract T initRootElement(Storage storage);

	@Override
	public Collection<PoiCategory> allCategories() {
		return categoryManager.allCategories();
	}

	@Override
	public PointOfInterest getPointById(long poiId) {
		return root.poiIntegerIdPKIndex.get(new Key(poiId));
	}

	@Override
	public boolean insertCategory(PoiCategory category) {
		if (category == null)
			throw new NullPointerException();

		// check if already exists
		if (root.categoryTitlePkIndex.get(category.getTitle()) == null) {

			PerstCategory parent = null;

			if (category.getParent() != null) {
				// check if parent already exists
				if (root.categoryTitlePkIndex.get(category.getParent().getTitle()) == null) {
					// insert parent category
					insertCategory(category.getParent());
				}
				parent = root.categoryTitlePkIndex.get(category.getParent().getTitle());
			}

			PerstCategory perstCategory = new PerstCategory(category.getTitle(), parent);
			root.categoryTitlePkIndex.put(perstCategory);
			db.store(perstCategory);

			// reload catergoryManager
			categoryManager = PerstPoiCategoryManager.getInstance(root.categoryTitlePkIndex);
			return true;
		}
		return false;
	}

	@Override
	public void close() {
		db.close();
	}

	protected Rect computeBoundingBox(GeoCoordinate coordinate, int radius) {
		double deltaLat = deltaLatitudeForRadius(radius);
		double deltaLng = deltaLongitudeForRadius(radius, coordinate.getLatitude());

		double lat1 = coordinate.getLatitude() - deltaLat;
		double lng1 = coordinate.getLongitude() - deltaLng;
		double lat2 = coordinate.getLatitude() + deltaLat;
		double lng2 = coordinate.getLongitude() + deltaLng;

		GeoCoordinate c1 = new GeoCoordinate(lat1, lng1);
		GeoCoordinate c2 = new GeoCoordinate(lat2, lng2);

		return new Rect(Math.min(c1.getLatitudeE6(), c2.getLatitudeE6()), Math.min(c1
				.getLongitudeE6(), c2.getLongitudeE6()), Math.max(c1.getLatitudeE6(), c2
				.getLatitudeE6()), Math.max(c1.getLongitudeE6(), c2.getLongitudeE6()));
	}

	/**
	 * @param radius
	 *            in meters.
	 * @param latitude
	 *            in degrees.
	 * @return delta longitude for given radius.
	 */
	protected double deltaLongitudeForRadius(int radius, double latitude) {
		return Math.abs((new Double(radius) / 1000)
				/ (SPACE_BETWEEN_LATS_IN_KM * Math.cos(Math.toRadians(latitude))));
	}

	protected double deltaLatitudeForRadius(int radius) {
		return Math.abs((new Double(radius) / 1000) / SPACE_BETWEEN_LATS_IN_KM);
	}

	@Override
	public Collection<PointOfInterest> findNearPosition(GeoCoordinate point, int radius,
			String category, int limit) {
		Collection<PointOfInterest> result = find(computeBoundingBox(point, radius),
				category,
				limit);

		// delete pois from result that are in the bounding box but not within the radius;
		// for (PointOfInterest poi : result) {
		// if (point.sphericalDistance(poi.getGeoCoordinate()) <= radius) {
		// filtered.add(poi);
		// }
		// }

		return result;
	}

	@Override
	public Collection<PointOfInterest> findInRect(GeoCoordinate p1, GeoCoordinate p2,
			String categoryName) {
		Rect rect = new Rect(Math.min(p1.getLatitudeE6(), p2.getLatitudeE6()), Math.min(p1
				.getLongitudeE6(), p2.getLongitudeE6()), Math.max(p1.getLatitudeE6(), p2
				.getLatitudeE6()), Math.max(p1.getLongitudeE6(), p2.getLongitudeE6()));
		return find(rect, categoryName, 0);
	}

	protected FieldIndex<ClusterEntry> createClusterIndex(Iterator<PerstPoi> iterator) {
		FieldIndex<ClusterEntry> clusterIndex = db.<ClusterEntry> createFieldIndex(
				ClusterEntry.class, "value", false);

		while (iterator.hasNext()) {
			clusterIndex.put(generateClusterEntry(iterator.next()));
		}

		return clusterIndex;
	}

	@Override
	public void insertPointsOfInterest(Collection<PointOfInterest> pois) {
		for (PointOfInterest poi : pois) {
			insertPointOfInterest(poi);
		}
	}

	@Override
	public Collection<PoiCategory> descendants(String category) {
		return categoryManager.descendants(category);
	}

	private ClusterEntry generateClusterEntry(PerstPoi poi) {
		int z = categoryManager.getOrderNumber(poi.category.title)
				* CATEGORY_CLUSTER_SPREAD_FACTOR;
		return new ClusterEntry(poi.id, Hilbert.computeValue3D(poi.longitude,
				poi.latitude, z));
	}

	abstract protected Collection<PointOfInterest> find(Rect rect, String category, int limit);

}
