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

import org.mapsforge.poi.PoiCategory;
import org.mapsforge.poi.PointOfInterest;
import org.mapsforge.poi.persistence.IPersistenceManager;
import org.mapsforge.poi.persistence.PoiBuilder;
import org.openstreetmap.osmosis.core.container.v0_6.EntityContainer;
import org.openstreetmap.osmosis.core.domain.v0_6.Entity;
import org.openstreetmap.osmosis.core.domain.v0_6.Node;
import org.openstreetmap.osmosis.core.domain.v0_6.Tag;
import org.openstreetmap.osmosis.core.task.v0_6.DatasetSink;
import org.openstreetmap.osmosis.core.task.v0_6.Sink;

/**
 * Implements {@link Sink} and {@link DatasetSink} interface from osmosis. Used to store points of
 * interest in either a perst or postgis database or both. Uses mapsforge {@link IPersistenceManager}
 * interface to store points of interest.
 * 
 * @author weise
 * 
 */
public class PoiWriter implements Sink {

	static final String REPLACE_REGEX = "\\Q{{?}}\\E";
	static final String INSERT_POI = "INSERT INTO pois (\"location\", category_id, name, url) VALUES "
									+ "(ST_GeographyFromText('SRID=4326;POINT({{?}} {{?}})'), "
									+ "(SELECT id FROM categories WHERE title = '{{?}}'), {{?}}, {{?}});";
	static final String INSERT_CATEGORY = "INSERT INTO categories(title, parent_id) VALUES "
											+ "('{{?}}', (SELECT id FROM categories WHERE title = '{{?}}'));";

	static final int DEFAULT_BATCH_SIZE = 5000;

	final PoiCategoryMatcher categoryMatcher;
	final HashMap<PoiCategory, Integer> categoryCountMap;
	final ArrayList<PointOfInterest> poiBatchList;
	final IPersistenceManager persistenceManager;

	/**
	 * @param persistenceManager
	 *            the {@link IPersistenceManager} that will be used to store the points of interest.
	 * @param categories
	 *            List of {@link OsmPoiCategory} containing all categories that shall be inserted into
	 *            the {@link IPersistenceManager}.
	 */
	public PoiWriter(IPersistenceManager persistenceManager, List<OsmPoiCategory> categories) {
		this.persistenceManager = persistenceManager;
		this.categoryMatcher = new PoiCategoryMatcher(categories);
		this.categoryCountMap = new HashMap<PoiCategory, Integer>(categories.size());
		this.poiBatchList = new ArrayList<PointOfInterest>(DEFAULT_BATCH_SIZE);
		init();
	}

	private void init() {
		List<PoiCategory> categories = categoryMatcher.traverseTree();

		for (PoiCategory category : categories) {
			persistenceManager.insertCategory(category);
			categoryCountMap.put(category, 0);
		}

		System.out.println("Inserted categories into DB!");
	}

	@Override
	// this is the entry point for osmosis when using xml as source.
	public void process(EntityContainer container) {
		Entity entity = container.getEntity();
		// if entity is a Node handle it.
		if (entity instanceof Node) {
			handleNode((Node) entity);
		}
	}

	void handleNode(Node node) {
		insertNode(node);
		if (poiBatchList.size() == DEFAULT_BATCH_SIZE) {
			executeBatch();
		}
	}

	/**
	 * Inserts the given node into the {@link IPersistenceManager} if and only if it tags matches one of
	 * the specified categories.
	 * 
	 * @param node
	 *            {@link Node} to be inserted into {@link IPersistenceManager}.
	 */
	void insertNode(Node node) {
		TagListBuilder tagListBuilder = new TagListBuilder();
		Collection<Tag> nodeTags = node.getTags();
		for (Tag tag : nodeTags) {
			tagListBuilder.addTag(tag.getKey(), tag.getValue());
		}
		PoiCategory category = categoryMatcher.findMatch(tagListBuilder.tagList());

		if (category != null) {
			String name = null;
			String url = null;

			Collection<Tag> tags = node.getTags();
			for (Tag tag : tags) {
				if (tag.getKey().equalsIgnoreCase("name")) {
					name = tag.getValue();
				} else if (tag.getKey().equalsIgnoreCase("url")) {
					url = tag.getValue();
				}
			}

			poiBatchList.add(new PoiBuilder(node.getId(), node.getLatitude(), node
					.getLongitude(), name, url, category).build());
			categoryCountMap.put(category, categoryCountMap.get(category) + 1);
		}
	}

	/**
	 * Executes the insert statements as batch.
	 */
	void executeBatch() {
		System.out.println("Writing " + poiBatchList.size() + " to DB!");
		persistenceManager.insertPointsOfInterest(poiBatchList);
		poiBatchList.clear();
	}

	@Override
	public void complete() {
		executeBatch();
		System.out.println("=========================================================");
		System.out.println("COMPLETE!");
		for (PoiCategory category : categoryCountMap.keySet()) {
			System.out.println(category.getTitle() + ": "
					+ categoryCountMap.get(category).toString());
		}
		System.out.println("=========================================================");
		System.out.println("DONE!");
	}

	@Override
	public void release() {
		persistenceManager.close();
	}

}
