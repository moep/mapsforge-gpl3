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
package org.mapsforge.preprocessing.osmosis.poi;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mapsforge.poi.PoiCategory;
import org.mapsforge.poi.persistence.CategoryBuilder;

class PoiCategoryMatcher {

	private HashMap<String, PoiCategoryTree> osmCategoryMap = new HashMap<String, PoiCategoryTree>();
	Collection<PoiCategoryTree> rootCategories = new ArrayList<PoiCategoryTree>();

	private class PoiCategoryTree implements OsmPoiCategory {
		OsmPoiCategory root;
		Collection<PoiCategoryTree> children;

		public PoiCategoryTree(OsmPoiCategory root) {
			this.root = root;
			this.children = new ArrayList<PoiCategoryTree>();
		}

		public void addChild(PoiCategoryTree child) {
			children.add(child);
		}

		public List<OsmPoiCategory> traverse() {
			ArrayList<OsmPoiCategory> list = new ArrayList<OsmPoiCategory>();
			list.add(root);
			for (PoiCategoryTree child : children) {
				list.addAll(child.traverse());
			}
			return list;
		}

		public OsmPoiCategory findBestMatch(Map<String, String> tagList) {
			if (root.matchesTags(tagList)) {
				OsmPoiCategory result = null;
				for (PoiCategoryTree child : children) {
					result = child.findBestMatch(tagList);
					if (result != null) {
						return result;
					}
				}
				if (!root.emtpyTaglist()) {
					return root;
				}
			}
			return null;
		}

		@Override
		public Map<String, String> keyValueList() {
			return root.keyValueList();
		}

		@Override
		public String parentUniqueTitle() {
			return root.parentUniqueTitle();
		}

		@Override
		public String uniqueTitle() {
			return root.uniqueTitle();
		}

		@Override
		public boolean matchesTags(Map<String, String> tagList) {
			return root.matchesTags(tagList);
		}

		@Override
		public boolean emtpyTaglist() {
			return root.emtpyTaglist();
		}
	}

	public PoiCategoryMatcher(Collection<OsmPoiCategory> categories) {
		osmCategoryMap =
				new HashMap<String, PoiCategoryTree>(categories.size());

		for (OsmPoiCategory cat : categories) {
			osmCategoryMap.put(cat.uniqueTitle(), new PoiCategoryTree(cat));
		}

		for (PoiCategoryTree catTree : osmCategoryMap.values()) {
			if (catTree.parentUniqueTitle() == null) {
				rootCategories.add(catTree);
			} else {
				osmCategoryMap.get(catTree.parentUniqueTitle()).addChild(catTree);
			}
		}
	}

	public PoiCategory findMatch(Map<String, String> tagList) {
		for (PoiCategoryTree catTree : rootCategories) {
			OsmPoiCategory result = catTree.findBestMatch(tagList);
			if (result != null) {
				return transformToPoiCategory(result);
			}
		}
		return null;
	}

	public List<PoiCategory> traverseTree() {
		ArrayList<OsmPoiCategory> osmList = new ArrayList<OsmPoiCategory>();
		ArrayList<PoiCategory> list = new ArrayList<PoiCategory>();
		for (PoiCategoryTree tree : rootCategories) {
			osmList.addAll(tree.traverse());
		}

		for (OsmPoiCategory osmCat : osmList) {
			list.add(transformToPoiCategory(osmCat));
		}

		return list;
	}

	private PoiCategory transformToPoiCategory(OsmPoiCategory category) {
		if (category == null)
			return null;
		PoiCategory parent = category.parentUniqueTitle() == null ? null
				: transformToPoiCategory(osmCategoryMap.get(category.parentUniqueTitle()).root);
		return new CategoryBuilder(category.uniqueTitle(), parent).build();
	}

}
