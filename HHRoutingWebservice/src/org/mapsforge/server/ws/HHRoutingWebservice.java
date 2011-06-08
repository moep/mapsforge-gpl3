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

/*
 * This WAR requires:
 * - json.jar for JSON writing
 * - xstream-1.3.1.jar for XML writing
 * - mapsforge-routing-X.X.X.jar as created by the ant task
 * - trove-3.0.0.a3.jar or later
 * 
 * *.hh file for which the location is set in th routerFactory.properties
 */
package org.mapsforge.server.ws;

import java.io.FileInputStream;
import java.io.IOException;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Properties;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.mapsforge.core.GeoCoordinate;
import org.mapsforge.server.routing.IEdge;
import org.mapsforge.server.routing.IRouter;
import org.mapsforge.server.routing.IVertex;
import org.mapsforge.server.routing.highwayHierarchies.HHRouterServerside;

import org.mapsforge.directions.LandmarksFromPerst;
import org.mapsforge.directions.TurnByTurnDescription;
import org.mapsforge.directions.TurnByTurnDescriptionToString;

/**
 * Servlet implementation class HHRoutingWebservice
 */
public class HHRoutingWebservice extends HttpServlet {
	private static final long serialVersionUID = 1L;
	
	private static IRouter router;

	private LandmarksFromPerst landmarkService; 
	private String propertiesURI;
	
	public void init() {
		try {
			propertiesURI = getServletContext().getRealPath("/WEB-INF/routerSettings.properties");
			FileInputStream fis = new FileInputStream(propertiesURI);
			Properties props = new Properties();
			props.load(fis);
			fis.close();
			String hhFilename = props.getProperty("hh.file");
			System.out.print("Loaded Router from " + hhFilename);
			long t = System.currentTimeMillis();
			FileInputStream iStream = new FileInputStream(hhFilename);
			router = HHRouterServerside.deserialize(iStream);
			iStream.close();
			t = System.currentTimeMillis() - t;
			System.out.println(" in " + t + " milliseconds");
			
			t = System.currentTimeMillis();
			String landmarksFilename = props.getProperty("landmarksPerstDBFile");
			System.out.print("Loaded landmarks from " + landmarksFilename );
			landmarkService = new LandmarksFromPerst(landmarksFilename );
			t = System.currentTimeMillis() - t;
			System.out.println(" in " + t + " milliseconds");
		} catch (Exception e) {
			System.err.print(e.toString());
		}
	}

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setCharacterEncoding("UTF-8");
		PrintWriter out = response.getWriter();
		try {
			ArrayList<Integer> pointIds = parseInputString(request.getParameter("points"));
			// ToDo: handle any number of stops along the way
			// for now its just source and destination

			boolean use_landmarks = request.getParameter("landmarks") != null;

			IEdge[] routeEdges = router.getShortestPath(pointIds.get(0), pointIds.get(1));
			if (routeEdges == null || routeEdges.length == 0) {
				response.setStatus(500);
				out.print("<error>It seems like I was not able to find a route. Sorry about that!</error>");
				return;
			}
			if (use_landmarks) {
				TurnByTurnDescription.landmarkService = landmarkService;
			}
			TurnByTurnDescription turnByTurn = new TurnByTurnDescription(routeEdges);
			TurnByTurnDescriptionToString converter = new TurnByTurnDescriptionToString(turnByTurn);
			
			String format = request.getParameter("format");
			if (format.equalsIgnoreCase("json")) {
				response.setContentType("application/json; charset=UTF-8");
				out.write(converter.toJSONString());
			} else if (format.equalsIgnoreCase("gpx")) {
				response.setHeader("Content-Disposition", "attachment; filename=route.gpx");
				response.setContentType("application/gpx+xml");
				out.write(converter.toGPX());
			} else if (format.equalsIgnoreCase("kml")) {
				response.setContentType("application/vnd.google-earth.kml+xml");
				out.write(converter.toKML());
			} else if (format.equalsIgnoreCase("txt")) {
				response.setContentType("text/plain");
				out.write(turnByTurn.toString());
			}
		} catch (Exception e) {
			e.printStackTrace();
			out.print("<error>"+e.toString()+"</error>");
		}
	}
	
	/**
	 * Turn a String of coordinates into a List of Points
	 * 
	 * @param points is a serialized String of Coordinates
	 * @return ArrayList of ints representing the IDs 
	 *         of the next vertex in the routing graph  
	 */
	private ArrayList<Integer> parseInputString(String points) {
		String[] alternatingCoordinates = points.split("[;,]");
		ArrayList<Integer> pp = new ArrayList<Integer>();
		for (int i = 0; i < alternatingCoordinates.length - (alternatingCoordinates.length%2); i += 2) {
			double lon = Double.valueOf(alternatingCoordinates[i]);
			double lat = Double.valueOf(alternatingCoordinates[i+1]);
			IVertex nv = router.getNearestVertex(new GeoCoordinate(lat, lon));
			if (nv != null) {
				int id = nv.getId();
				pp.add(id);
			}
		}
		return pp;
	}	
	
}
