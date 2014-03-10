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
		if(type == AdressType.STREET_ADDRESS) result = this.address_components.get("street_address");
		else if(type == AdressType.ROUTE) result = this.address_components.get("route");
		else if(type == AdressType.INTERSECTION) result = this.address_components.get("intersection");
		else if(type == AdressType.POLITICAL) result = this.address_components.get("political");
		else if(type == AdressType.COUNTRY) result = this.address_components.get("country");
		else if(type == AdressType.ADMINISTRATIVE_AREA_LEVEL_1) result = this.address_components.get("administrative_area_level_1");
		else if(type == AdressType.ADMINISTRATIVE_AREA_LEVEL_2) result = this.address_components.get("administrative_area_level_2");
		else if(type == AdressType.ADMINISTRATIVE_AREA_LEVEL_3) result = this.address_components.get("administrative_area_level_3");
		else if(type == AdressType.COLLOQUIAL_AREA) result = this.address_components.get("colloquial_area");
		else if(type == AdressType.LOCALITY) result = this.address_components.get("locality");
		else if(type == AdressType.SUBLOCALITY) result = this.address_components.get("sublocality");
		else if(type == AdressType.NEIGHBORHOOD) result = this.address_components.get("neighborhood");
		else if(type == AdressType.PREMISE) result = this.address_components.get("premise");
		else if(type == AdressType.SUBPREMISE) result = this.address_components.get("subpremise");
		else if(type == AdressType.POSTAL_CODE) result = this.address_components.get("postal_code");
		else if(type == AdressType.NATURAL_FEATURE) result = this.address_components.get("natural_feature");
		else if(type == AdressType.AIRPORT) result = this.address_components.get("airport");
		else if(type == AdressType.PARK) result = this.address_components.get("park");
		else if(type == AdressType.POINT_OF_INTEREST) result = this.address_components.get("point_of_interest");
		else if(type == AdressType.POST_BOX) result = this.address_components.get("post_box");
		else if(type == AdressType.STREET_NUMBER) result = this.address_components.get("street_number");
		else if(type == AdressType.FLOOR) result = this.address_components.get("floor");
		else if(type == AdressType.ROOM) result = this.address_components.get("room");
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
		if(type == LocationType.AIRPORT) params.put("types", "airport");
		else if(type == LocationType.AMUSEMENT_PARK) params.put("types", "amusement_park");
		else if(type == LocationType.AQUARIUM) params.put("types", "aquarium");
	    else if(type == LocationType.ART_GALLERY) params.put("types", "art_gallery");
	    else if(type == LocationType.ATM) params.put("types", "atm");
	    else if(type == LocationType.BAKERY) params.put("types", "bakery");
	    else if(type == LocationType.BANK) params.put("types", "bank");
	    else if(type == LocationType.BAR) params.put("types", "bar");
	    else if(type == LocationType.BEAUTY_SALON) params.put("types", "beauty_salon");
	    else if(type == LocationType.BICYCLE_STORE) params.put("types", "bicycle_store");
	    else if(type == LocationType.BOOK_STORE) params.put("types", "book_store");
	    else if(type == LocationType.BOWLING_ALLEY) params.put("types", "bowling_alley");
	    else if(type == LocationType.BUS_STATION) params.put("types", "bus_station");
	    else if(type == LocationType.CAFE) params.put("types", "cafe");
	    else if(type == LocationType.CAMPGROUND) params.put("types", "campground");
	    else if(type == LocationType.CAR_DEALER) params.put("types", "car_dealer");
	    else if(type == LocationType.CAR_RENTAL) params.put("types", "car_rental");
	    else if(type == LocationType.CAR_REPAIR) params.put("types", "car_repair");
	    else if(type == LocationType.CAR_WASH) params.put("types", "car_wash");
	    else if(type == LocationType.CASINO) params.put("types", "casino");
	    else if(type == LocationType.CEMETERY) params.put("types", "cemetery");
	    else if(type == LocationType.CHURCH) params.put("types", "church");
	    else if(type == LocationType.CITY_HALL) params.put("types", "city_hall");
	    else if(type == LocationType.CLOTHING_STORE) params.put("types", "clothing_store");
	    else if(type == LocationType.CONVENIENCE_STORE) params.put("types", "convenience_store");
	    else if(type == LocationType.COURTHOUSE) params.put("types", "courthouse");
	    else if(type == LocationType.DENTIST) params.put("types", "dentist");
	    else if(type == LocationType.DEPARTMENT_STORE) params.put("types", "department_store");
	    else if(type == LocationType.DOCTOR) params.put("types", "doctor");
	    else if(type == LocationType.ELECTRICIAN) params.put("types", "electrician");
	    else if(type == LocationType.ELECTRONICS_STORE) params.put("types", "electronics_store");
	    else if(type == LocationType.EMBASSY) params.put("types", "embassy");
	    else if(type == LocationType.ESTABLISHMENT) params.put("types", "establishment");
	    else if(type == LocationType.FINANCE) params.put("types", "finance");
	    else if(type == LocationType.FIRE_STATION) params.put("types", "fire_station");
	    else if(type == LocationType.FLORIST) params.put("types", "florist");
	    else if(type == LocationType.FOOD) params.put("types", "food");
	    else if(type == LocationType.FUNERAL_HOME) params.put("types", "funeral_home");
	    else if(type == LocationType.FURNITURE_STORE) params.put("types", "furniture_store");
	    else if(type == LocationType.GAS_STATION) params.put("types", "gas_station");
	    else if(type == LocationType.GENERAL_CONTRACTOR) params.put("types", "general_contractor");
	    else if(type == LocationType.GROCERY_OR_SUPERMARKET) params.put("types", "grocery_or_supermarket");
	    else if(type == LocationType.GYM) params.put("types", "gym");
	    else if(type == LocationType.HAIR_CARE) params.put("types", "hair_care");
	    else if(type == LocationType.HARDWARE_STORE) params.put("types", "hardware_store");
	    else if(type == LocationType.HEALTH) params.put("types", "health");
	    else if(type == LocationType.HINDU_TEMPLE) params.put("types", "hindu_temple");
	    else if(type == LocationType.HOME_GOODS_STORE) params.put("types", "home_goods_store");
	    else if(type == LocationType.HOSPITAL) params.put("types", "hospital");
	    else if(type == LocationType.INSURANCE_AGENCY) params.put("types", "insurance_agency");
	    else if(type == LocationType.JEWELRY_STORE) params.put("types", "jewelry_store");
	    else if(type == LocationType.LAUNDRY) params.put("types", "laundry");
	    else if(type == LocationType.LAWYER) params.put("types", "lawyer");
	    else if(type == LocationType.LIBRARY) params.put("types", "library");
	    else if(type == LocationType.LIQUOR_STORE) params.put("types", "liquor_store");
	    else if(type == LocationType.LOCAL_GOVERNMENT_OFFICE) params.put("types", "local_government_office");
	    else if(type == LocationType.LOCKSMITH) params.put("types", "locksmith");
	    else if(type == LocationType.LODGING) params.put("types", "lodging");
	    else if(type == LocationType.MEAL_DELIVERY) params.put("types", "meal_delivery");
	    else if(type == LocationType.MEAL_TAKEAWAY) params.put("types", "meal_takeaway");
	    else if(type == LocationType.MOSQUE) params.put("types", "mosque");
	    else if(type == LocationType.MOVIE_RENTAL) params.put("types", "movie_rental");
	    else if(type == LocationType.MOVIE_THEATER) params.put("types", "movie_theater");
	    else if(type == LocationType.MOVING_COMPANY) params.put("types", "moving_company");
	    else if(type == LocationType.MUSEUM) params.put("types", "museum");
	    else if(type == LocationType.NIGHT_CLUB) params.put("types", "night_club");
	    else if(type == LocationType.PAINTER) params.put("types", "painter");
	    else if(type == LocationType.PARK) params.put("types", "park");
	    else if(type == LocationType.PARKING) params.put("types", "parking");
	    else if(type == LocationType.PET_STORE) params.put("types", "pet_store");
	    else if(type == LocationType.PHARMACY) params.put("types", "pharmacy");
	    else if(type == LocationType.PHYSIOTHERAPIST) params.put("types", "physiotherapist");
	    else if(type == LocationType.PLACE_OF_WORSHIP) params.put("types", "place_of_worship");
	    else if(type == LocationType.PLUMBER) params.put("types", "plumber");
	    else if(type == LocationType.POLICE) params.put("types", "police");
	    else if(type == LocationType.POST_OFFICE) params.put("types", "post_office");
	    else if(type == LocationType.REAL_ESTATE_AGENCY) params.put("types", "real_estate_agency");
	    else if(type == LocationType.RESTAURANT) params.put("types", "restaurant");
	    else if(type == LocationType.ROOFING_CONTRACTOR) params.put("types", "roofing_contractor");
	    else if(type == LocationType.RV_PARK) params.put("types", "rv_park");
	    else if(type == LocationType.SCHOOL) params.put("types", "school");
	    else if(type == LocationType.SHOE_STORE) params.put("types", "shoe_store");
	    else if(type == LocationType.SHOPPING_MALL) params.put("types", "shopping_mall");
	    else if(type == LocationType.SPA) params.put("types", "spa");
	    else if(type == LocationType.STADIUM) params.put("types", "stadium");
	    else if(type == LocationType.STORAGE) params.put("types", "storage");
	    else if(type == LocationType.STORE) params.put("types", "store");
	    else if(type == LocationType.SUBWAY_STATION) params.put("types", "subway_station");
	    else if(type == LocationType.SYNAGOGUE) params.put("types", "synagogue");
	    else if(type == LocationType.TAXI_STAND) params.put("types", "taxi_stand");
	    else if(type == LocationType.TRAIN_STATION) params.put("types", "train_station");
	    else if(type == LocationType.TRAVEL_AGENCY) params.put("types", "travel_agency");
	    else if(type == LocationType.UNIVERSITY) params.put("types", "university");
	    else if(type == LocationType.VETERINARY_CARE) params.put("types", "veterinary_care");
	    else if(type == LocationType.ZOO) params.put("types", "zoo");
	    else {
	    	return null;
	    }
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
