package ibpm.geolocation;

import ibpm.geolocation.ImageLocation.LocationType;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.json.simple.parser.ParseException;

import com.drew.imaging.ImageProcessingException;

public class Demo {
	public static void main(String[] args) throws ImageProcessingException, IOException, ParseException, URISyntaxException {
		ImageLocation loc;
		try {
			if(args[0].startsWith("http://") || args[0].startsWith("https://")) {
				URL jpegFile = new URL(args[0]);
				loc = new ImageLocation(jpegFile);
			}
			else {
				File jpegFile = new File(args[0]);
				loc = new ImageLocation(jpegFile);
			}
		}
		catch(java.lang.NullPointerException ex){
			return;
		}		
		
		System.out.println("-------------");
		printLocation(loc);
		loc.setLanguage("uk");
		System.out.println("-------------");
		printLocation(loc);
		System.out.println("-------------");
		
		System.out.println("List of all nearby locations");
		System.out.println("-------------");
		loc.queryNearbyPlaces(LocationType.RESTAURANT, Integer.parseInt(args[1]));
		Map<String, Map<String, String>> nearby = loc.getNearbyPlacesMap();
		Iterator<Entry<String, Map<String, String>>> nearbyIter = nearby.entrySet().iterator();
		while(nearbyIter.hasNext()) {
			Map.Entry<String, Map<String, String>> item = nearbyIter.next();
			System.out.println("ID: " + item.getKey());
			Iterator<Entry<String, String>> iterPlace = item.getValue().entrySet().iterator();
			while(iterPlace.hasNext()) {
				Map.Entry<String, String> placeProp = iterPlace.next();
				System.out.println("\t" + placeProp.getKey() + " : " + placeProp.getValue());
			}
		}
	}
	public static void printLocation(ImageLocation loc) throws IOException, ParseException {
		ArrayList<String> result = loc.getAddressComponents();
		Iterator<String> iter = result.iterator();
		while(iter.hasNext()) {
			System.out.println(iter.next());
		}
		System.out.println("Formatted Adress: " + loc.getFormattedAddress());
	}
}
