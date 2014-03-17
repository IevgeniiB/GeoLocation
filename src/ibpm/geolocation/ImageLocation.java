package ibpm.geolocation;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.lang.GeoLocation;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.GpsDirectory;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;

public class ImageLocation {
	private static final Logger log = Logger.getLogger(ImageLocation.class.getName());
	private GeoLocation location;
	private String language;
	private Map<String, Map<String, String>> address_components;
	private String formatted_address;
	private Map<String, Map<String, String>> nearby_places;
	private boolean needUpdate; 
	
	public ImageLocation(File jpegFile) throws ImageProcessingException, IOException, ParseException{
		Metadata metadata = ImageMetadataReader.readMetadata(jpegFile);
		this.initMetadata(metadata);
	}
	
	public ImageLocation(URL url) throws ImageProcessingException, IOException, ParseException {
		Metadata metadata = ImageMetadataReader.readMetadata(new BufferedInputStream(url.openStream()), true);
		this.initMetadata(metadata);
	}
	
	private void initMetadata(Metadata metadata) throws IOException, ParseException {
		try {
			GpsDirectory directory = metadata.getDirectory(GpsDirectory.class);
			this.location = directory.getGeoLocation();
		}
		catch(java.lang.NullPointerException ex) {
			log.log(Level.SEVERE, "This image file has no gps data");
			throw ex;
		}
		this.language = "en";
		this.needUpdate = true;
				
		log.info("Coordinates:\n\t"
				+ "Latitude: " + this.location.getLatitude() 
				+ "\n\tLongitude: " + this.location.getLongitude());
	}
	
 	private void initLocation() throws IOException, ParseException {
 		if(this.needUpdate == true){
 			this.parseResponse(this.getResponse());
 			this.needUpdate = false;
 		}
	}
	
	public ArrayList<String> getAddressComponents() throws IOException, ParseException {
		this.initLocation();
		ArrayList<String> result = new ArrayList<String>();
		Iterator<Entry<String, Map<String, String>>> iter = this.address_components.entrySet().iterator();
		while(iter.hasNext()) {
			Map.Entry<String, Map<String, String>> item = iter.next();
			result.add(item.getKey() + ": " + item.getValue().get("long_name"));
		}
		return result;
	}

	public Map<String, Map<String, String>> getAddressComponentsMap() {
		return address_components;
	}

	public double getLatitude(){
		return this.location.getLatitude();
	}
	
	public double getLongitude(){
		return this.location.getLongitude();
	}
	
	public String getLanguage() {
		return language;
	}

	public void setLanguage(String language) {
		this.language = language;
		this.needUpdate = true;
	}
	
	private void parseResponse(String response) throws IOException, ParseException{
		JSONParser parser = new JSONParser();
        JSONObject rsp = (JSONObject) parser.parse(response);
        if (rsp.containsKey("results")) {
        	JSONArray matches = (JSONArray) rsp.get("results");
            JSONObject data = (JSONObject) matches.get(0);
            this.formatted_address = (String) data.get("formatted_address");
            JSONArray address = (JSONArray) data.get("address_components");
            this.address_components = new HashMap<String, Map<String,String>>();
            for(int count = 0; count < address.size(); count++) {
            	JSONObject item = (JSONObject) address.get(count);
            	Map<String, String> names = new HashMap<String, String>();
            	names.put("long_name", (String) item.get("long_name"));
            	names.put("short_name", (String) item.get("short_name"));
            	this.address_components.put((String) ((JSONArray) item.get("types")).get(0), names);
            }
        }
	}
	
	public String getFormattedAddress() throws IOException, ParseException {
		this.initLocation();
		return formatted_address;
	}

	private String getResponse() throws IOException{
		final String baseUrl = "http://maps.googleapis.com/maps/api/geocode/json";
		final Map<String, String> params = Maps.newHashMap();
		params.put("language", this.language);
		params.put("sensor", "false");
		params.put("latlng", this.location.getLatitude() + "," + this.location.getLongitude());
		URL url = new URL(baseUrl + '?' + encodeParams(params));
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
	    String result = "";
		try {
			InputStream in = url.openStream();
	        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
	        String line = reader.readLine();
	        result = line;
	        while ((line = reader.readLine()) != null) {
	            result += line;
	        }
		}
		finally {
	        connection.disconnect();   
		}
		return result;
	}
	
	private String encodeParams(final Map<String, String> params) {
		final String paramsUrl = Joiner.on('&').join(
				Iterables.transform(params.entrySet(), new Function<Entry<String, String>, String>() {

					@Override
					public String apply(final Entry<String, String> input) {
						try {
							final StringBuffer buffer = new StringBuffer();
							buffer.append(input.getKey());
							buffer.append('=');
							buffer.append(URLEncoder.encode(input.getValue(), "utf-8"));
							return buffer.toString();
						} catch (final UnsupportedEncodingException e) {
							throw new RuntimeException(e);
						}
					}
				}));
		return paramsUrl;
	}
	
	public String getProperty(AdressType type, Mode mode) throws IOException, ParseException {
		this.initLocation();
		Map<String, String> result = null;
		result = this.address_components.get(type.toString().toLowerCase());
		if(result == null) {
			log.log(Level.SEVERE, "No such type of component");
			return null;
		}
		if(mode == Mode.LONG_NAME) {
			return result.get("long_name");
		}
		return result.get("short_name");
	}
	
	private String getResponsePlaces(LocationType type, int radius) throws IOException {
		final String baseUrl = "https://maps.googleapis.com/maps/api/place/search/json";
		final Map<String, String> params = Maps.newHashMap();
		params.put("location", this.location.getLatitude() + "," + this.location.getLongitude());
		params.put("radius", Integer.toString(radius));
		params.put("types", type.toString().toLowerCase());
		params.put("sensor", "false");
		params.put("key", "AIzaSyA3e8q1ovoilnEBUHDCJxj7uIPyl971ogw");
	    URL url = new URL(baseUrl + '?' + encodeParams(params));
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
	    String result = "";
		try {
			InputStream in = url.openStream();
	        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
	        String line = reader.readLine();
	        result = line;
	        while ((line = reader.readLine()) != null) {
	            result += line;
	        }
		}
		finally {
	        connection.disconnect();   
		}
		
		return result;
	}
	
	private void parseResponsePlaces(String responsePlaces) throws ParseException {
		JSONParser parser = new JSONParser();
        JSONObject rsp = (JSONObject) parser.parse(responsePlaces);
        if (rsp.containsKey("results")) {
        	JSONArray matches = (JSONArray) rsp.get("results");
        	this.nearby_places = new HashMap<String, Map<String,String>>();
        	for(int count = 0; count < matches.size(); count++) {
        		JSONObject place = (JSONObject) matches.get(count);
        		Map<String, String> place_prop = new HashMap<String, String>();
        		if(place.containsKey("geometry")) {
        			JSONObject geometry = (JSONObject) place.get("geometry");
        			JSONObject location = (JSONObject) geometry.get("location");
        			place_prop.put("latitude", Double.toString((Double) location.get("lat")));
        			place_prop.put("longitude", Double.toString((Double) location.get("lng")));
        		}
        		Number num = (Number) place.get("rating");
        		if(place.containsKey("name")) place_prop.put("name", (String) place.get("name"));
        		if(place.containsKey("vicinity")) place_prop.put("formatted_address", (String) place.get("vicinity"));
        		if(place.containsKey("rating")) place_prop.put("rating", num.toString());
        		if(place.containsKey("price_level")) place_prop.put("price_level", Long.toString((Long) place.get("price_level")));
        		this.nearby_places.put((String) place.get("id"), place_prop);
        	}
        }
	}
	
	public void queryNearbyPlaces(LocationType type, int radius) throws ParseException, IOException {
		this.parseResponsePlaces(this.getResponsePlaces(type, radius));
	}
	
	public Map<String, String> getPlaceByID(String id) {
		if(this.nearby_places.containsKey(id)) {
			return this.nearby_places.get(id);
		}
		log.log(Level.SEVERE, "This ID is not exists in scope of this place");
		return null;
	}
	
	public Map<String, Map<String, String>> getNearbyPlacesMap() {
		if(this.nearby_places != null) {
			return this.nearby_places;
		}
		log.log(Level.SEVERE, "There is no places near");
		return null;
	}
	
	public enum AdressType {
		STREET_ADDRESS,
		ROUTE,
		INTERSECTION,
		POLITICAL,
		COUNTRY,
		ADMINISTRATIVE_AREA_LEVEL_1,
		ADMINISTRATIVE_AREA_LEVEL_2,
		ADMINISTRATIVE_AREA_LEVEL_3,
		COLLOQUIAL_AREA,
		LOCALITY,
		SUBLOCALITY,
		NEIGHBORHOOD,
		PREMISE,
		SUBPREMISE,
		POSTAL_CODE,
		NATURAL_FEATURE,
		AIRPORT,
		PARK,
		POINT_OF_INTEREST,
		POST_BOX,
	    STREET_NUMBER,
	    FLOOR,
	    ROOM
	}
	
	public enum Mode {
		LONG_NAME,
		SHORT_NAME
	}
	
	public enum LocationType {
	    ACCOUNTING,
	    AIRPORT,
	    AMUSEMENT_PARK,
	    AQUARIUM,
	    ART_GALLERY,
	    ATM,
	    BAKERY,
	    BANK,
	    BAR,
	    BEAUTY_SALON,
	    BICYCLE_STORE,
	    BOOK_STORE,
	    BOWLING_ALLEY,
	    BUS_STATION,
	    CAFE,
	    CAMPGROUND,
	    CAR_DEALER,
	    CAR_RENTAL,
	    CAR_REPAIR,
	    CAR_WASH,
	    CASINO,
	    CEMETERY,
	    CHURCH,
	    CITY_HALL,
	    CLOTHING_STORE,
	    CONVENIENCE_STORE,
	    COURTHOUSE,
	    DENTIST,
	    DEPARTMENT_STORE,
	    DOCTOR,
	    ELECTRICIAN,
	    ELECTRONICS_STORE,
	    EMBASSY,
	    ESTABLISHMENT,
	    FINANCE,
	    FIRE_STATION,
	    FLORIST,
	    FOOD,
	    FUNERAL_HOME,
	    FURNITURE_STORE,
	    GAS_STATION,
	    GENERAL_CONTRACTOR,
	    GROCERY_OR_SUPERMARKET,
	    GYM,
	    HAIR_CARE,
	    HARDWARE_STORE,
	    HEALTH,
	    HINDU_TEMPLE,
	    HOME_GOODS_STORE,
	    HOSPITAL,
	    INSURANCE_AGENCY,
	    JEWELRY_STORE,
	    LAUNDRY,
	    LAWYER,
	    LIBRARY,
	    LIQUOR_STORE,
	    LOCAL_GOVERNMENT_OFFICE,
	    LOCKSMITH,
	    LODGING,
	    MEAL_DELIVERY,
	    MEAL_TAKEAWAY,
	    MOSQUE,
	    MOVIE_RENTAL,
	    MOVIE_THEATER,
	    MOVING_COMPANY,
	    MUSEUM,
	    NIGHT_CLUB,
	    PAINTER,
	    PARK,
	    PARKING,
	    PET_STORE,
	    PHARMACY,
	    PHYSIOTHERAPIST,
	    PLACE_OF_WORSHIP,
	    PLUMBER,
	    POLICE,
	    POST_OFFICE,
	    REAL_ESTATE_AGENCY,
	    RESTAURANT,
	    ROOFING_CONTRACTOR,
	    RV_PARK,
	    SCHOOL,
	    SHOE_STORE,
	    SHOPPING_MALL,
	    SPA,
	    STADIUM,
	    STORAGE,
	    STORE,
	    SUBWAY_STATION,
	    SYNAGOGUE,
	    TAXI_STAND,
	    TRAIN_STATION,
	    TRAVEL_AGENCY,
	    UNIVERSITY,
	    VETERINARY_CARE,
	    ZOO
	}
}
