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

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

class PoiCategoryParser {

	public List<OsmPoiCategory> parseFile(String filename) {
		ArrayList<OsmPoiCategory> categories = new ArrayList<OsmPoiCategory>();
		FileReader reader;

		try {
			reader = new FileReader(filename);
			BufferedReader br = new BufferedReader(reader);
			String lineString = br.readLine();
			while (lineString != null) {
				System.out.println("parsing: " + lineString);
				OsmPoiCategory category = parseCategoryLine(lineString);
				if (category != null) {
					categories.add(category);
				}
				lineString = br.readLine();
			}
		} catch (FileNotFoundException e) {
			throw new IllegalArgumentException("No such file");
		} catch (IOException e) {
			e.printStackTrace();
			throw new RuntimeException(e.getMessage());
		}

		return categories;
	}

	private OsmPoiCategory parseCategoryLine(String line) {
		if (line.trim().equals("") || line.trim().startsWith("#")) {
			return null;
		}

		String[] attr = line.trim().split(";");
		String title = attr[0].trim();
		String parentTitle = attr[1].trim();

		String[] tagList;
		TagListBuilder builder = new TagListBuilder();
		if (attr.length > 2) {
			tagList = attr[2].trim().split(",");
			for (String tagString : tagList) {
				String[] kvPair = tagString.trim().split("\\.");
				builder.addTag(kvPair[0], kvPair[1]);
			}
		}

		if (title.equals(""))
			return null;

		return new BasePoiCategory(title,
									builder.tagList(),
									parentTitle.equals("") ? null : parentTitle);
	}

}
