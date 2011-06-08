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

import java.io.File;
import java.util.Iterator;

import org.garret.perst.FieldIndex;
import org.garret.perst.Storage;
import org.garret.perst.StorageFactory;

class ClusterStorage {

	protected ClusterStorageRootElement root;
	protected Storage db;
	protected String fileName;
	protected boolean threeDim;

	public ClusterStorage(String fileName, boolean threeDim) {
		this.fileName = fileName;
		this.threeDim = threeDim;
		db = StorageFactory.getInstance().createStorage();
		db.open(fileName, 1024 * 1024);

		/* db.getRoot() returns object. Must be cast to root object T */
		ClusterStorageRootElement tmpRoot = (ClusterStorageRootElement) db.getRoot();

		if (tmpRoot == null) {
			root = new ClusterStorageRootElement(db);
			db.setRoot(root);
		} else {
			root = tmpRoot;
		}
	}

	public FieldIndex<ClusterEntry> getClusterIndex(String name) {
		return root.getClusterIndex(name);
	}

	public FieldIndex<ClusterEntry> createClusterIndex(Iterator<PerstPoi> iterator,
			String name, PerstPoiCategoryManager categoryManager, int spreadFactor) {
		FieldIndex<ClusterEntry> clusterIndex = db.<ClusterEntry> createFieldIndex(
				ClusterEntry.class, "value", false);
		root.addClusterIndex(clusterIndex, name);

		while (iterator.hasNext()) {
			clusterIndex.put(generateClusterEntry(iterator.next(), categoryManager,
					spreadFactor));
		}

		return clusterIndex;
	}

	public void close() {
		db.close();
	}

	public void destroy() {
		db.close();
		new File(fileName).delete();
	}

	private ClusterEntry generateClusterEntry(PerstPoi poi,
			PerstPoiCategoryManager categoryManager, int spreadFactor) {
		if (threeDim) {
			int z = categoryManager.getOrderNumber(poi.category.title)
					* spreadFactor;
			return new ClusterEntry(poi.getId(), Hilbert.computeValue3D(poi.longitude,
					poi.latitude, z));
		}
		return new ClusterEntry(poi.getId(), Hilbert.computeValue(poi.latitude,
				poi.longitude));

	}
}
