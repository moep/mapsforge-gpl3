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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import org.garret.perst.FieldIndex;
import org.garret.perst.GenericIndex;
import org.garret.perst.IterableIterator;
import org.garret.perst.Key;
import org.garret.perst.Storage;
import org.mapsforge.core.GeoCoordinate;
import org.mapsforge.poi.PoiCategory;
import org.mapsforge.poi.PointOfInterest;

/**
 * {@link IPersistenceManager} for Perst which uses multiple rtrees to index the pois.
 * 
 * @author weise
 * 
 */
public class MultiRtreePersistenceManager extends
		AbstractPerstPersistenceManager<PoiRootElement> {

	private class PackEntryIterator implements Iterator<PackEntry<Rect, PerstPoi>> {

		private final Iterator<ClusterEntry> iterator;

		public PackEntryIterator(Iterator<ClusterEntry> iterator) {
			super();
			this.iterator = iterator;
		}

		@Override
		public boolean hasNext() {
			return iterator.hasNext();
		}

		@Override
		public PackEntry<Rect, PerstPoi> next() {
			if (!iterator.hasNext()) {
				return null;
			}

			PerstPoi poi = root.poiIntegerIdPKIndex.get(new Key(iterator.next().poiId));

			return new PackEntry<Rect, PerstPoi>(new Rect(poi.latitude, poi.longitude,
					poi.latitude,
					poi.longitude), poi);
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}

	}

	/**
	 * @param storageFileName
	 *            filename of the perst storage file that should be used.
	 */
	public MultiRtreePersistenceManager(String storageFileName) {
		super(storageFileName);
	}

	@Override
	public boolean insertCategory(PoiCategory category) {
		if (!categoryManager.contains(category.getTitle())) {
			if (super.insertCategory(category)) {
				root.addSpatialIndex(db, category.getTitle());
				return true;
			}
		}
		return false;
	}

	@Override
	public void insertPointOfInterest(PointOfInterest poi) {
		if (poi == null)
			throw new NullPointerException();

		PerstCategory category = categoryManager.get(poi.getCategory().getTitle());

		if (category == null) {
			throw new IllegalArgumentException("POI of unknown category, insert category first");
		}

		PerstPoi perstPoi = new PerstPoi(poi, category);
		root.poiIntegerIdPKIndex.put(perstPoi);
		root.poiCategoryFkIndex.put(perstPoi);

		Collection<PoiCategory> categories = categoryManager.ancestors(perstPoi.category.title);

		for (PoiCategory cat : categories) {
			root.getSpatialIndex(cat.getTitle()).put(
					perstPoi,
					new Rect(perstPoi.latitude, perstPoi.longitude, perstPoi.latitude,
							perstPoi.longitude));
		}
		db.store(perstPoi);
	}

	@Override
	protected Collection<PointOfInterest> find(Rect rect, String category, int limit) {
		if (!categoryManager.contains(category)) {
			return new ArrayList<PointOfInterest>(0);
		}

		ArrayList<PointOfInterest> result = new ArrayList<PointOfInterest>();

		ArrayList<PerstPoi> pois = root.getSpatialIndex(category).getList(rect);

		// TODO: order pois by distance asc;

		int max = limit <= 0 ? Integer.MAX_VALUE : limit;

		if (max >= pois.size()) {
			result.addAll(pois);
		} else {
			result.addAll(pois.subList(0, max));
		}

		return result;
	}

	@Override
	public void removeCategory(PoiCategory category) {
		Collection<PoiCategory> descendants = categoryManager.descendants(category.getTitle());

		IterableIterator<PerstPoi> pois = null;
		Key categoryFK = null;
		ArrayList<Integer> deleteQueue = new ArrayList<Integer>();

		for (PoiCategory descendant : descendants) {
			categoryFK = new Key(categoryManager.get(descendant.getTitle()));
			pois = root.poiCategoryFkIndex.iterator(categoryFK, categoryFK,
					GenericIndex.ASCENT_ORDER);

			while (pois.hasNext()) {
				deleteQueue.add(pois.next().getOid());
			}

			Iterator<Integer> queueIterator = deleteQueue.iterator();
			while (queueIterator.hasNext()) {
				removePointOfInterest((PerstPoi) db.getObjectByOID(queueIterator.next()));
			}
			deleteQueue.clear();

			root.removeSpatialIndex(descendant.getTitle());
			root.categoryTitlePkIndex.remove(descendant);
			db.deallocate(descendant);
		}

		categoryManager = PerstPoiCategoryManager.getInstance(root.categoryTitlePkIndex);
	}

	@Override
	public void removePointOfInterest(PointOfInterest poi) {
		removePointOfInterest(root.poiIntegerIdPKIndex.get(poi.getId()));
	}

	private void removePointOfInterest(PerstPoi perstPoi) {
		if (perstPoi == null)
			return;

		root.poiIntegerIdPKIndex.remove(perstPoi);

		Collection<PoiCategory> categories = categoryManager.ancestors(perstPoi.category.title);
		for (PoiCategory category : categories) {
			root.getSpatialIndex(category.getTitle()).remove(
					perstPoi,
					new Rect(perstPoi.latitude, perstPoi.longitude, perstPoi.latitude,
							perstPoi.longitude));
		}

		root.poiCategoryFkIndex.remove(perstPoi);
		db.deallocate(perstPoi);
	}

	@Override
	public Collection<PoiCategory> allCategories() {
		return categoryManager.allCategories();
	}

	@Override
	public void clusterStorage() {
		if (categoryManager.get("Root") == null) {
			throw new UnsupportedOperationException(
					"This only works for PersistenceManager that have a root Category 'Root'");
		}

		ClusterStorage clusterStorage = new ClusterStorage(this.fileName + ".clusterStorage",
				true);

		IPersistenceManager destinationManager = new MultiRtreePersistenceManager(
				fileName + ".clustered");

		// create temporary index for cluster value
		FieldIndex<ClusterEntry> clusterIndex = clusterStorage.createClusterIndex(
				root.poiIntegerIdPKIndex.iterator(),
				"Root",
				categoryManager,
				CATEGORY_CLUSTER_SPREAD_FACTOR);

		Collection<PoiCategory> categories = categoryManager.allCategories();

		for (PoiCategory category : categories) {
			destinationManager.insertCategory(category);
		}

		// reopen
		destinationManager.close();
		destinationManager = new MultiRtreePersistenceManager(fileName + ".clustered");

		Iterator<ClusterEntry> clusterIterator = clusterIndex.iterator();
		PerstPoi perstPoi = null;
		while (clusterIterator.hasNext()) {
			perstPoi = root.poiIntegerIdPKIndex.get(new Key(clusterIterator.next().poiId));
			destinationManager.insertPointOfInterest(perstPoi);
		}

		destinationManager.close();
		clusterStorage.destroy();
	}

	@Override
	public void packIndex() {
		ClusterStorage clusterStorage = new ClusterStorage(this.fileName + ".clusterStorage",
				false);

		Rtree2DIndex<PerstPoi> oldIndex = null;
		Rtree2DIndex<PerstPoi> newIndex = null;
		FieldIndex<ClusterEntry> clusterIndex = null;

		Collection<PoiCategory> categories = categoryManager.allCategories();
		for (PoiCategory category : categories) {
			oldIndex = root.getSpatialIndex(category.getTitle());
			if (oldIndex.size() > 0) {
				clusterIndex = clusterStorage.createClusterIndex(oldIndex.iterator(),
													category.getTitle(),
													categoryManager,
													CATEGORY_CLUSTER_SPREAD_FACTOR);

				root.removeSpatialIndex(category.getTitle()); // remove old
				newIndex = new Rtree2DIndex<PerstPoi>();
				newIndex.packInsert(new PackEntryIterator(clusterIndex.iterator()), root
						.getStorage());
				root.addSpatialIndex(new NamedSpatialIndex(category.getTitle(), newIndex)); // add
				// new
			}
		}
		clusterStorage.destroy();
	}

	@Override
	protected PoiRootElement initRootElement(Storage database) {
		return new PoiRootElement(database);
	}

	@Override
	public Iterator<PointOfInterest> neighborIterator(GeoCoordinate geoCoordinate,
			String category) {
		if (!categoryManager.contains(category)) {
			return new ArrayList<PointOfInterest>(0).iterator();
		}

		return new PoiNeighborIterator(root.getSpatialIndex(category).
				neighborIterator(geoCoordinate.getLongitudeE6(),
						geoCoordinate.getLatitudeE6()));
	}
}
