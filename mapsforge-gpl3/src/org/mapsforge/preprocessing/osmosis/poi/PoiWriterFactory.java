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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

import org.mapsforge.poi.persistence.IPersistenceManager;
import org.mapsforge.poi.persistence.PersistenceManagerFactory;
import org.openstreetmap.osmosis.core.pipeline.common.TaskConfiguration;
import org.openstreetmap.osmosis.core.pipeline.common.TaskManager;
import org.openstreetmap.osmosis.core.pipeline.common.TaskManagerFactory;
import org.openstreetmap.osmosis.core.pipeline.v0_6.SinkManager;

class PoiWriterFactory extends TaskManagerFactory {

	private static final String DRIVER = "org.postgresql.Driver";
	private static final String DEFAULT_DB_SERVER = "localhost";
	private static final String DEFAULT_PORT = "5432";
	private static final String DEFAULT_DATABASE = "mapsforge_pois";
	private static final String DEFAULT_USER = "postgres";
	private static final String DEFAULT_PASSWORD = "postgres";
	private static final String DEFAULT_PERST_FILE = "perstPoi.dbs";

	protected List<OsmPoiCategory> categories;
	protected IPersistenceManager persistenceManager;

	private String modus;
	private String database;
	private String username;
	private String password;
	private String protocol;
	private String perstFile;

	protected void handleArguments(TaskConfiguration taskConfig) {
		// Get the task arguments.
		String categoryFileName = getStringArgument(taskConfig, "poiWriter-categoryFile");
		perstFile = getStringArgument(taskConfig, "poiWriter-perstFile", DEFAULT_PERST_FILE);
		database = getStringArgument(taskConfig, "poiWriter-db", DEFAULT_DATABASE);
		username = getStringArgument(taskConfig, "poiWriter-username", DEFAULT_USER);
		password = getStringArgument(taskConfig, "poiWriter-password", DEFAULT_PASSWORD);
		String port = getStringArgument(taskConfig, "poiWriter-port", DEFAULT_PORT);
		String dbServer = getStringArgument(taskConfig, "poiWriter-server", DEFAULT_DB_SERVER);
		modus = getStringArgument(taskConfig, "poiWriter-modus", "perst");
		protocol = "jdbc:postgresql://" + dbServer + ":" + port + "/";

		if (categoryFileName == null) {
			throw new IllegalArgumentException("NO FILE ARGUMENT");
		}

		categories = new PoiCategoryParser().parseFile(categoryFileName);

		if (modus.equalsIgnoreCase("postGis")) {
			persistenceManager = PersistenceManagerFactory
					.getPostGisPersistenceManager(establishDBConnection());
		} else if (modus.equals("perst")) {
			persistenceManager = PersistenceManagerFactory
					.getPerstPersistenceManager(perstFile);
		} else {
			persistenceManager = PersistenceManagerFactory.getDualPersistenceManager(
					establishDBConnection(), perstFile);
		}
	}

	@Override
	protected TaskManager createTaskManagerImpl(TaskConfiguration taskConfig) {
		PoiWriter task;

		handleArguments(taskConfig);
		task = new PoiWriter(persistenceManager, categories);

		return new SinkManager(taskConfig.getId(), task, taskConfig.getPipeArgs());
	}

	private Connection establishDBConnection() {

		Connection connection = null;

		try {
			Class.forName(DRIVER);
			connection = DriverManager.getConnection(protocol + database, username, password);
			System.out.println("DB connection established");
		} catch (ClassNotFoundException e1) {
			e1.printStackTrace();
			throw new RuntimeException("Unble to connect to Database");
		} catch (SQLException e) {
			e.printStackTrace();
			throw new IllegalArgumentException();
		}

		return connection;
	}

}
