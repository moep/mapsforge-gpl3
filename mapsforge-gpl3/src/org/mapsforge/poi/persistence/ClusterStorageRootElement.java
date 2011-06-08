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

import org.garret.perst.FieldIndex;
import org.garret.perst.Persistent;
import org.garret.perst.Storage;

class ClusterStorageRootElement extends Persistent {

	FieldIndex<NamedClusterIndex> clusterIndexIndex;

	public ClusterStorageRootElement() {
		// required by perst
	}

	public ClusterStorageRootElement(Storage db) {
		super(db);
		clusterIndexIndex = db.<NamedClusterIndex> createFieldIndex(NamedClusterIndex.class,
				"name", true);
	}

	public void addClusterIndex(FieldIndex<ClusterEntry> clusterIndex, String name) {
		clusterIndexIndex.add(new NamedClusterIndex(name, clusterIndex));
		modify();
	}

	public void removeClusterIndex(String clusterName) {
		NamedClusterIndex namedIndex = clusterIndexIndex.get(clusterName);
		clusterIndexIndex.remove(namedIndex);
		namedIndex.index.clear();
		namedIndex.index.deallocate();
		namedIndex.deallocate();
	}

	public FieldIndex<ClusterEntry> getClusterIndex(String clusterName) {
		NamedClusterIndex namedIndex = clusterIndexIndex.get(clusterName);
		return namedIndex == null ? null : namedIndex.index;
	}

}
