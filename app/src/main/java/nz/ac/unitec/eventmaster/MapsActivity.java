package nz.ac.unitec.eventmaster;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.sothree.slidinguppanel.SlidingUpPanelLayout;
import com.ticketmaster.api.discovery.operation.SearchEventsOperation;
import com.ticketmaster.api.discovery.response.PagedResponse;
import com.ticketmaster.discovery.model.Events;


import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import nz.ac.unitec.eventmaster.model.Event;


public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private ArrayList<Event> events = new ArrayList<>();


    private class MyArrayAdapter extends ArrayAdapter<String> {


        HashMap<String, Integer> mIdMap = new HashMap<String, Integer>();


        public MyArrayAdapter(Context context, int textViewResourceId,
                              List<String> objects) {
            super(context, textViewResourceId, objects);
            for (int i = 0; i < objects.size(); ++i) {
                mIdMap.put(objects.get(i), i);
            }
        }

        @Override
        public long getItemId(int position) {
            String item = getItem(position);
            return mIdMap.get(item);
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

    }

    private GoogleMap mMap;
    private double lat;
    private double lng;
    private String city;

    ///--- View components ---///
    ProgressBar progressBar;
    FloatingActionButton button;
    ListView listView;

    ///--- App logic variables ---///
    ArrayList<String> list;
    FusedLocationProviderClient client;
    Geocoder gcd;
    RequestQueue queue;
    ArrayList<Marker> markers;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }


    @Override
    public void onMapReady(GoogleMap googleMap) {

        mMap = googleMap;

        progressBar = findViewById(R.id.progressBar);
        button = findViewById(R.id.floatingActionButton);
        listView = findViewById(R.id.listView);

        list = new ArrayList<>();
        markers = new ArrayList<>();

        /// Set info box view on list item click
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                Marker marker = markers.get(i);
                marker.showInfoWindow();
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(marker.getPosition(), 15));
            }
        });

        /// Set ScrollableView property to simplify list navigation
        ((SlidingUpPanelLayout)findViewById(R.id.slMap)).setScrollableView(listView);

        /// Instantiate location services (LocationProvider to get current location,
        /// Geocoder to convert location to geographical name, Volley to get data from service provider)
        client = LocationServices.getFusedLocationProviderClient(getApplicationContext());
        gcd = new Geocoder(getApplicationContext(), Locale.getDefault());
        queue = Volley.newRequestQueue(getApplicationContext());

        /// Initiate map update on start
        updateView();

        /// Initiate map update on button click
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                updateView();
            }
        });
    }

    private void updateView() {


        /// Run current location request
        LocationRequest req = new LocationRequest();
        req.setInterval(10000);
        req.setFastestInterval(5000);
        req.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        client.requestLocationUpdates(req,new LocationCallback(){
            @Override
            public void onLocationResult(LocationResult locationResult) {
                Log.e("location:",locationResult.getLastLocation().toString());
            }
        },null);

        try {
            final Task<Location> location = client.getLastLocation();
            list.clear();
            markers.clear();
            progressBar.setVisibility(View.VISIBLE);

            location.addOnCompleteListener(new OnCompleteListener<Location>() {
                @Override
                public void onComplete(@NonNull Task<Location> task) {
                    Location res = task.getResult();
                    if (task.isSuccessful() && (res != null)) {

                        /// Save new coordinates, create a new marker
                        lat = res.getLatitude();
                        lng = res.getLongitude();
                        LatLng coordinates = new LatLng(lat, lng);
                        Marker marker = mMap.addMarker(new MarkerOptions().position(coordinates).title("I'm here!").icon(BitmapDescriptorFactory.fromResource(R.mipmap.ic_map_pin_dark_red)));
                        marker.showInfoWindow();
                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(coordinates, 10));
                        System.out.println("lat = " + lat + ", lng = " + lng);

                        /// Use Geocoder to get the city
                        List<Address> addresses = null;
                        try {
                            addresses = gcd.getFromLocation(lat, lng, 1);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                        /// Use city to get current events
                        assert addresses != null;
                        if (addresses.size() > 0) {
                            city = addresses.get(0).getLocality();
                            System.out.println(city);
                            Date c = Calendar.getInstance().getTime();
                            System.out.println("Current time => " + c);
                            SimpleDateFormat df = new SimpleDateFormat("dd-MMM-yyyy");
                            String startDateTime = df.format(c);
                            //String url = "https://app.ticketmaster.com/discovery/v2/events.json?city=" + city + "&apikey=mzOuM4tYy3IrWOM3sOHsGaABAsHWNCo3";
                            String url = "https://app.ticketmaster.com/discovery/v2/events.json?countryCode=IE&size=133&startDateTime=2018-03-19T17:52:00Z&endDateTime=2018-03-26T17:53:00Z&apikey=mzOuM4tYy3IrWOM3sOHsGaABAsHWNCo3";
                            JsonObjectRequest jsObjRequest = new JsonObjectRequest(Request.Method.GET, url, (JSONObject) null, new Response.Listener<JSONObject>() {
                                        @Override
                                        public void onResponse(JSONObject response) {
                                            progressBar.setVisibility(View.INVISIBLE);
                                            /// Parse JSON
                                            try {
                                                JSONObject embedded = response.getJSONObject("_embedded");
                                                JSONArray eventsRequested = embedded.getJSONArray("events");
                                                int length = eventsRequested.length();
                                                for (int index = 0; index < length; index++) {
                                                    JSONObject Tevent = eventsRequested.getJSONObject(index);
                                                    String eventName = Tevent.getString("name");
                                                    System.out.println("eventName:" + eventName);
                                                    JSONObject dates = Tevent.getJSONObject("dates");
                                                    JSONObject start = dates.getJSONObject("start");
                                                    String startDateTime = start.getString("dateTime");
                                                    JSONObject venueEvent = Tevent.getJSONObject("_embedded");
                                                    JSONArray venues = venueEvent.getJSONArray("venues");



                                                    int venuesLength = venues.length();
                                                    for (int venuesIndex = 0; venuesIndex < venuesLength; venuesIndex++) {

                                                        if(venues.getJSONObject(venuesIndex).has("location")){
                                                            JSONObject venue = venues.getJSONObject(venuesIndex);
                                                            JSONObject venueLocation = venue.getJSONObject("location");
                                                            double venueLng = Double.parseDouble(venueLocation.get("longitude").toString());
                                                            double venueLat = Double.parseDouble(venueLocation.get("latitude").toString());
                                                            LatLng venueCoordinates = new LatLng(venueLat, venueLng);
                                                            Marker marker = mMap.addMarker(new MarkerOptions().position(venueCoordinates).title(eventName).snippet(startDateTime).icon(BitmapDescriptorFactory.fromResource(R.mipmap.ic_map_pin_azure)));
                                                            markers.add(marker);
                                                            //mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(venueCoordinates, 10));
                                                            list.add(eventName + " (start at " + startDateTime + ")");
                                                        }
                                                        else{
                                                            System.out.println("no location");
                                                        }



                                                    }
                                                }

                                                /// When the list is filled, set list view adapter
                                                final MyArrayAdapter adapter = new MyArrayAdapter(getApplicationContext(), android.R.layout.simple_list_item_1, list);
                                                listView.setAdapter(adapter);

                                            } catch (JSONException e) {
                                                e.printStackTrace();
                                            } finally {
                                                Event event = new Event();
                                                events.add(event);
                                                progressBar.setVisibility(View.INVISIBLE);
                                            }
                                        }
                                    }, new Response.ErrorListener() {
                                        @Override
                                        public void onErrorResponse(VolleyError error) {

                                        }
                                    });

                            queue.add(jsObjRequest);
                        }
                    } else {
                        /// @todo For some reasons after the first start, App fails to
                        /// @todo get the current location
                        progressBar.setVisibility(View.INVISIBLE);
                        Toast.makeText(getApplicationContext(),
                                "Failed to get location",
                                Toast.LENGTH_LONG).show();
                        System.err.println("Failed to get location");
                    }
                }
            });
        } catch(SecurityException ex) {
            ex.printStackTrace();
        }
    }
}
