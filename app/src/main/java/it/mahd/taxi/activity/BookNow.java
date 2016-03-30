package it.mahd.taxi.activity;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Service;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.*;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.Socket;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import it.mahd.taxi.Main;
import it.mahd.taxi.R;
import it.mahd.taxi.database.TaxiPosition;
import it.mahd.taxi.util.Calculator;
import it.mahd.taxi.util.Controllers;
import it.mahd.taxi.util.DirectionMap;
import it.mahd.taxi.util.Encrypt;
import it.mahd.taxi.util.ServerRequest;
import it.mahd.taxi.util.SocketIO;

/**
 * Created by salem on 2/13/16.
 */
public class BookNow extends Fragment implements LocationListener {
    SharedPreferences pref;
    Controllers conf = new Controllers();
    ServerRequest sr = new ServerRequest();
    Socket socket = SocketIO.getInstance();
    ArrayList<TaxiPosition> listTaxi = new ArrayList<>();
    ArrayList<TaxiPosition> driverTaxi = new ArrayList<>();

    MapView mMapView;
    Service service;
    private static Dialog bookDialog;
    private GoogleMap googleMap;
    protected LocationManager locationManager;// Declaring a Location Manager
    Location location; // location
    private CameraPosition cameraPosition;
    private CameraUpdate cameraUpdate;
    private double latitude, longitude;
    private Boolean isStart = false;
    boolean isGPSEnabled = false;// flag for GPS status
    boolean isNetworkEnabled = false;// flag for network status
    boolean canGetLocation = false;// flag for GPS status
    private Boolean ioTaxi = false;
    private Boolean ioValid = false;
    private Boolean ioPostBook = false;
    private String tokenOfDriver;
    private String usernameOfDriver;
    private LatLng ptOfDriver;
    private static final long MIN_DISTANCE_CHANGE_FOR_UPDATES = 3;// The minimum distance to change Updates in meters // 3 meters
    private static final long MIN_TIME_BW_UPDATES = 1000 * 3 * 1;// The minimum time between updates in milliseconds // 3 seconds
    private TextView DistanceDuration_txt;
    private FloatingActionButton Valid_btn;

    public BookNow() {}

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.booknow, container, false);
        pref = getActivity().getSharedPreferences(conf.app, Context.MODE_PRIVATE);
        socket.connect();
        ioTaxi = true;
        socket.on(conf.io_gps, handleIncomingTaxis);//listen in taxi driver
        socket.on(conf.io_validBook, handleIncomingValidBook);//listen in driver valid book
        socket.on(conf.io_postBook, handleIncomingPostBook);
        socket.on(conf.io_drawRoute, handleIncomingDrawRoute);

        DistanceDuration_txt = (TextView) v.findViewById(R.id.distance_time_txt);
        mMapView = (MapView) v.findViewById(R.id.mapView);
        mMapView.onCreate(savedInstanceState);
        mMapView.onResume();

        try {
            MapsInitializer.initialize(getActivity());
        } catch (Exception e) {
            e.printStackTrace();
        }

        googleMap = mMapView.getMap();
        googleMap.setMyLocationEnabled(true);
        googleMap.getUiSettings().setZoomControlsEnabled(true);
        getLocation();
        if(canGetLocation()){
            latitude = getLatitude();
            longitude = getLongitude();
        }else{
            showSettingsAlert();
            latitude = 0;
            longitude = 0;
        }
        cameraPosition = new CameraPosition.Builder().target(new LatLng(latitude, longitude)).zoom(15).build();
        googleMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
        Valid_btn = (FloatingActionButton) v.findViewById(R.id.valid_btn);
        Valid_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                JSONObject jsonx = new JSONObject();
                try{
                    jsonx.put(conf.tag_validRoute, true);
                    jsonx.put(conf.tag_tokenClient, pref.getString(conf.tag_token, ""));
                    socket.emit(conf.io_validRoute, jsonx);
                    Valid_btn.setVisibility(View.GONE);
                }catch(JSONException e){ }
            }
        });

        googleMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
            public boolean onMarkerClick(Marker arg0) {
                tokenOfDriver = arg0.getTitle();
                ptOfDriver = arg0.getPosition();
                bookDialog = new Dialog(getActivity(), R.style.FullHeightDialog);
                bookDialog.setContentView(R.layout.booknow_dialog);
                bookDialog.setCancelable(true);
                ImageView Picture_iv, Color_iv;
                TextView Username_txt, Age_txt, Model_txt, Serial_txt, Places_txt, Luggages_txt;
                Button Book_btn, Cancel_btn;
                Picture_iv = (ImageView) bookDialog.findViewById(R.id.picture_iv);
                Username_txt = (TextView) bookDialog.findViewById(R.id.username_txt);
                Age_txt = (TextView) bookDialog.findViewById(R.id.age_txt);
                Color_iv = (ImageView) bookDialog.findViewById(R.id.color_iv);
                Model_txt = (TextView) bookDialog.findViewById(R.id.model_txt);
                Serial_txt = (TextView) bookDialog.findViewById(R.id.serial_txt);
                Places_txt = (TextView) bookDialog.findViewById(R.id.places_txt);
                Luggages_txt = (TextView) bookDialog.findViewById(R.id.luggages_txt);
                Book_btn = (Button) bookDialog.findViewById(R.id.book_btn);
                Cancel_btn = (Button) bookDialog.findViewById(R.id.cancel_btn);
                List<NameValuePair> params = new ArrayList<NameValuePair>();
                params.add(new BasicNameValuePair(conf.tag_token, tokenOfDriver));
                JSONObject json = sr.getJSON(conf.url_getDriver, params);
                if(json != null){
                    try{
                        if(json.getBoolean(conf.res)) {
                            Encrypt algo = new Encrypt();
                            int keyVirtual = Integer.parseInt(json.getString(conf.tag_key));
                            String newKey = algo.key(keyVirtual);
                            byte[] imageAsBytes = Base64.decode(json.getString(conf.tag_picture).getBytes(), Base64.DEFAULT);
                            Picture_iv.setImageBitmap(BitmapFactory.decodeByteArray(imageAsBytes, 0, imageAsBytes.length));
                            String color = algo.enc2dec(json.getString(conf.tag_color), newKey);
                            Username_txt.setTextColor(Color.parseColor(color));
                            usernameOfDriver = algo.enc2dec(json.getString(conf.tag_fname), newKey)
                                    + " " + algo.enc2dec(json.getString(conf.tag_lname), newKey);
                            Username_txt.setText(usernameOfDriver);
                            int[] tab = new Calculator().getAge(algo.enc2dec(json.getString(conf.tag_dateN), newKey));
                            Age_txt.setTextColor(Color.parseColor(color));
                            Age_txt.setText(tab[0] + "years, " + tab[1] + "month, " + tab[2] + "day");
                            Color_iv.setBackgroundColor(Color.parseColor(color));
                            Model_txt.setTextColor(Color.parseColor(color));
                            Model_txt.setText(algo.enc2dec(json.getString(conf.tag_model), newKey));
                            Serial_txt.setTextColor(Color.parseColor(color));
                            Serial_txt.setText(algo.enc2dec(json.getString(conf.tag_serial), newKey));
                            Places_txt.setTextColor(Color.parseColor(color));
                            Places_txt.setText(algo.enc2dec(json.getString(conf.tag_places), newKey) + " Places,");
                            Luggages_txt.setTextColor(Color.parseColor(color));
                            Luggages_txt.setText(algo.enc2dec(json.getString(conf.tag_luggages), newKey) + " Kg Luggages");
                            Cancel_btn.setOnClickListener(new View.OnClickListener() {
                                public void onClick(View v) {
                                    bookDialog.dismiss();
                                }
                            });
                            Book_btn.setOnClickListener(new View.OnClickListener() {
                                public void onClick(View v) {
                                    ioTaxi = false;
                                    JSONObject jsonx = new JSONObject();
                                    try{
                                        jsonx.put(conf.tag_latitude,latitude);
                                        jsonx.put(conf.tag_longitude, longitude);
                                        jsonx.put(conf.tag_token, tokenOfDriver);
                                        jsonx.put(conf.tag_fname, pref.getString(conf.tag_fname, ""));
                                        socket.emit(conf.io_preBook, jsonx);
                                    }catch(JSONException e){ }
                                    ioValid = true;
                                    bookDialog.dismiss();
                                    googleMap.clear();
                                }
                            });
                        }
                    }catch (JSONException e) {
                        e.printStackTrace();
                    }
                } else {
                    Toast.makeText(getActivity(), R.string.serverunvalid, Toast.LENGTH_SHORT).show();
                }
                bookDialog.show();

                return true;
            }
        });
        return v;
    }

    private Emitter.Listener handleIncomingDrawRoute = new Emitter.Listener(){
        public void call(final Object... args){
            getActivity().runOnUiThread(new Runnable() {
                public void run() {
                    if (ioPostBook) {
                        JSONObject data = (JSONObject) args[0];
                        Double origLat, origLon, desLat, desLon;
                        String token;
                        try {
                            origLat = data.getDouble(conf.tag_originLatitude);
                            origLon = data.getDouble(conf.tag_originLongitude);
                            desLat = data.getDouble(conf.tag_desLatitude);
                            desLon = data.getDouble(conf.tag_desLongitude);
                            token = data.getString(conf.tag_token);
                            if (token.equals(tokenOfDriver)) {
                                googleMap.clear();
                                Valid_btn.setVisibility(View.VISIBLE);
                                MarkerOptions options = new MarkerOptions();
                                LatLng origin = new LatLng(origLat,origLon);
                                LatLng dest = new LatLng(desLat,desLon);
                                options.position(origin);
                                options.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)).title("Start");
                                googleMap.addMarker(options);
                                options.position(dest);
                                options.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)).title("End");
                                googleMap.addMarker(options);
                                String url = getDirectionsUrl(origin, dest);
                                DownloadTask downloadTask = new DownloadTask();
                                downloadTask.execute(url);
                            }
                        } catch (JSONException e) { }
                    }
                }
            });
        }
    };

    private Emitter.Listener handleIncomingPostBook = new Emitter.Listener(){
        public void call(final Object... args){
            getActivity().runOnUiThread(new Runnable() {
                public void run() {
                    if (ioPostBook) {
                        JSONObject data = (JSONObject) args[0];
                        Double lat, lon;
                        String token;
                        try {
                            lat = data.getDouble(conf.tag_latitude);
                            lon = data.getDouble(conf.tag_longitude);
                            token = data.getString(conf.tag_token);
                            if (token.equals(tokenOfDriver)) {
                                if (driverTaxi.isEmpty()) {
                                    MarkerOptions a = new MarkerOptions().position(new LatLng(lat, lon))
                                            .title(usernameOfDriver)
                                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED));
                                    Marker m = googleMap.addMarker(a);
                                    TaxiPosition t = new TaxiPosition(token, "", lat, lon, m);
                                    driverTaxi.add(t);
                                } else {
                                    driverTaxi.get(0).getMarker().setPosition(new LatLng(lat, lon));
                                }
                            }
                        } catch (JSONException e) { }
                    }
                }
            });
        }
    };

    private Emitter.Listener handleIncomingValidBook = new Emitter.Listener(){
        public void call(final Object... args){
            getActivity().runOnUiThread(new Runnable() {
                public void run() {
                    if (ioValid) {
                        JSONObject data = (JSONObject) args[0];
                        Double lat, lon;
                        String token;
                        try {
                            lat = data.getDouble(conf.tag_latitude);
                            lon = data.getDouble(conf.tag_longitude);
                            token = data.getString(conf.tag_token);
                            if (token.equals(tokenOfDriver)) {
                                MarkerOptions options = new MarkerOptions();
                                LatLng origin = new LatLng(lat,lon);
                                LatLng dest = new LatLng(latitude,longitude);
                                options.position(dest);
                                options.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)).title("Me");
                                googleMap.addMarker(options);
                                options.position(origin);
                                options.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)).title(usernameOfDriver);
                                googleMap.addMarker(options);
                                String url = getDirectionsUrl(origin, dest);
                                DownloadTask downloadTask = new DownloadTask();
                                downloadTask.execute(url);
                                ioPostBook = true;
                            }
                        } catch (JSONException e) { }
                    }
                }
            });
        }
    };

    private Emitter.Listener handleIncomingTaxis = new Emitter.Listener(){
        public void call(final Object... args){
            getActivity().runOnUiThread(new Runnable() {
                public void run() {
                    if (ioTaxi) {
                        JSONObject data = (JSONObject) args[0];
                        Double lat, lon;
                        String token, socket;
                        Boolean working;
                        try {
                            lat = data.getDouble(conf.tag_latitude);
                            lon = data.getDouble(conf.tag_longitude);
                            token = data.getString(conf.tag_token);
                            socket = data.getString(conf.tag_socket);
                            working = data.getBoolean(conf.tag_working);
                            if (working) {
                                if (listTaxi.isEmpty()) {
                                    MarkerOptions a = new MarkerOptions().position(new LatLng(lat, lon))
                                            .title(token)
                                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED));
                                    Marker m = googleMap.addMarker(a);
                                    TaxiPosition t = new TaxiPosition(token, socket, lat, lon, m);
                                    listTaxi.add(t);
                                } else {
                                    boolean existTaxi = false;
                                    int position = 0;
                                    for (int i = 0; i < listTaxi.size(); i++) {
                                        if (socket.equals(listTaxi.get(i).getSocket())) {
                                            existTaxi = true;
                                            position = i;
                                            break;
                                        } else {
                                            existTaxi = false;
                                        }
                                    }
                                    if (existTaxi) {
                                        listTaxi.get(position).getMarker().setPosition(new LatLng(lat, lon));
                                        listTaxi.get(position).setLatitude(lat);
                                        listTaxi.get(position).setLongitude(lon);
                                    } else {
                                        MarkerOptions a = new MarkerOptions().position(new LatLng(lat, lon))
                                                .title(token)
                                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED));
                                        Marker m = googleMap.addMarker(a);
                                        TaxiPosition t = new TaxiPosition(token, socket, lat, lon, m);
                                        listTaxi.add(t);
                                    }
                                }
                            } else {
                                if (!listTaxi.isEmpty()) {
                                    for (int i = 0; i < listTaxi.size(); i++) {
                                        if (socket.equals(listTaxi.get(i).getSocket())) {
                                            listTaxi.get(i).getMarker().remove();
                                            listTaxi.remove(i);
                                            break;
                                        }
                                    }
                                }
                            }
                        } catch (JSONException e) { }
                    }
                }
            });
        }
    };

    private String getDirectionsUrl(LatLng origin,LatLng dest){
        // Origin of route
        String str_origin = "origin="+origin.latitude+","+origin.longitude;
        // Destination of route
        String str_dest = "destination="+dest.latitude+","+dest.longitude;
        // Sensor enabled
        String sensor = "sensor=false";
        // Building the parameters to the web service
        String parameters = str_origin+"&"+str_dest+"&"+sensor;
        // Output format
        String output = "json";
        // Building the url to the web service
        String url = "https://maps.googleapis.com/maps/api/directions/"+output+"?"+parameters;
        return url;
    }
    /** A method to download json data from url */
    private String downloadUrl(String strUrl) throws IOException {
        String data = "";
        InputStream iStream = null;
        HttpURLConnection urlConnection = null;
        try{
            URL url = new URL(strUrl);
            // Creating an http connection to communicate with url
            urlConnection = (HttpURLConnection) url.openConnection();
            // Connecting to url
            urlConnection.connect();
            // Reading data from url
            iStream = urlConnection.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(iStream));
            StringBuffer sb  = new StringBuffer();
            String line = "";
            while( ( line = br.readLine())  != null){
                sb.append(line);
            }
            data = sb.toString();
            br.close();
        }catch(Exception e){
        }finally{
            iStream.close();
            urlConnection.disconnect();
        }
        return data;
    }
    // Fetches data from url passed
    private class DownloadTask extends AsyncTask<String, Void, String> {
        // Downloading data in non-ui thread
        @Override
        protected String doInBackground(String... url) {
            // For storing data from web service
            String data = "";
            try{
                // Fetching the data from web service
                data = downloadUrl(url[0]);
            }catch(Exception e){
            }
            return data;
        }
        // Executes in UI thread, after the execution of doInBackground()
        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            ParserTask parserTask = new ParserTask();
            // Invokes the thread for parsing the JSON data
            parserTask.execute(result);
        }
    }
    /** A class to parse the Google Places in JSON format */
    private class ParserTask extends AsyncTask<String, Integer, List<List<HashMap<String,String>>> >{
        // Parsing the data in non-ui thread
        @Override
        protected List<List<HashMap<String, String>>> doInBackground(String... jsonData) {
            JSONObject jObject;
            List<List<HashMap<String, String>>> routes = null;
            try{
                jObject = new JSONObject(jsonData[0]);
                DirectionMap parser = new DirectionMap();
                // Starts parsing data
                routes = parser.parse(jObject);
            }catch(Exception e){
                e.printStackTrace();
            }
            return routes;
        }
        // Executes in UI thread, after the parsing process
        @Override
        protected void onPostExecute(List<List<HashMap<String, String>>> result) {
            ArrayList<LatLng> points = null;
            PolylineOptions lineOptions = null;
            String distance = "";
            String duration = "";
            if(result.size()<1){
                Toast.makeText(getActivity(), "No Points", Toast.LENGTH_SHORT).show();
                return;
            }
            // Traversing through all the routes
            for(int i=0;i<result.size();i++){
                points = new ArrayList<LatLng>();
                lineOptions = new PolylineOptions();
                // Fetching i-th route
                List<HashMap<String, String>> path = result.get(i);
                // Fetching all the points in i-th route
                for(int j=0;j<path.size();j++){
                    HashMap<String,String> point = path.get(j);
                    if(j==0){	// Get distance from the list
                        distance = (String)point.get("distance");
                        continue;
                    }else if(j==1){ // Get duration from the list
                        duration = (String)point.get("duration");
                        continue;
                    }
                    double lat = Double.parseDouble(point.get("lat"));
                    double lng = Double.parseDouble(point.get("lng"));
                    LatLng position = new LatLng(lat, lng);
                    points.add(position);
                }
                // Adding all the points in the route to LineOptions
                lineOptions.addAll(points);
                lineOptions.width(2);
                lineOptions.color(Color.RED);
            }
            DistanceDuration_txt.setText("Distance:"+distance + ", Duration:"+duration);
            // Drawing polyline in the Google Map for the i-th route
            googleMap.addPolyline(lineOptions);
        }
    }

    public Location getLocation() {
        try {
            locationManager = (LocationManager) getActivity().getSystemService(service.LOCATION_SERVICE);
            isGPSEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);// getting GPS status
            isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);// getting network status

            if (!isGPSEnabled) {// no GPS provider is enabled
                showSettingsAlert();
            } else {
                this.canGetLocation = true;
                if (isNetworkEnabled) {// First get location from Network Provider
                    locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
                            MIN_TIME_BW_UPDATES,
                            MIN_DISTANCE_CHANGE_FOR_UPDATES, this);
                    if (locationManager != null) {
                        location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                        if (location != null) {
                            latitude = location.getLatitude();
                            longitude = location.getLongitude();
                        }
                    }
                }
                if (isGPSEnabled) {// if GPS Enabled get lat/long using GPS Services
                    if (location == null) {
                        locationManager.requestLocationUpdates(
                                LocationManager.GPS_PROVIDER,
                                MIN_TIME_BW_UPDATES,
                                MIN_DISTANCE_CHANGE_FOR_UPDATES, this);
                        if (locationManager != null) {
                            location = locationManager
                                    .getLastKnownLocation(LocationManager.GPS_PROVIDER);
                            if (location != null) {
                                latitude = location.getLatitude();
                                longitude = location.getLongitude();
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return location;
    }

    public void stopUsingGPS(){//Stop using GPS listener & Calling this function will stop using GPS in your app
        if(locationManager != null){
            locationManager.removeUpdates(this);
        }
    }

    public double getLatitude(){//Function to get latitude
        if(location != null){
            latitude = location.getLatitude();
        }
        return latitude;
    }

    public double getLongitude(){//Function to get longitude
        if(location != null){
            longitude = location.getLongitude();
        }
        return longitude;
    }

    //Function to check GPS/wifi enabled
    public boolean canGetLocation() {
        return this.canGetLocation;
    }

    public void showSettingsAlert(){//Function to show settings alert dialog & On pressing Settings button will lauch Settings Options
        final AlertDialog.Builder builder =  new AlertDialog.Builder(getActivity());
        final String action = android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS;
        final String title = "GPS is settings";// Setting Dialog Title
        final String message = "GPS is not enabled. Do you want open GPS setting?";// Setting Dialog Message
        builder.setTitle(title).setMessage(message)
                .setPositiveButton("OK",
                        new DialogInterface.OnClickListener() {// On pressing Settings button
                            public void onClick(DialogInterface d, int id) {
                                getActivity().startActivity(new Intent(action));
                                d.dismiss();
                            }
                        })
                .setNegativeButton("Cancel",
                        new DialogInterface.OnClickListener() {// on pressing cancel button
                            public void onClick(DialogInterface d, int id) {
                                d.cancel();
                            }
                        });
        builder.create().show();// Showing Alert Message
    }

    @Override
    public void onLocationChanged(Location location) {
        latitude = location.getLatitude();
        longitude = location.getLongitude();
        cameraUpdate = CameraUpdateFactory.newLatLng(new LatLng(latitude, longitude));
        googleMap.moveCamera(cameraUpdate);
        changeLocation();
    }
    private void changeLocation() {
        if (isStart) {
            JSONObject json = new JSONObject();
            try{
                json.put(conf.tag_latitude,latitude);
                json.put(conf.tag_longitude, longitude);
                json.put(conf.tag_token, pref.getString(conf.tag_token, ""));
                socket.emit(conf.io_gps, json);
            }catch(JSONException e){ }
        }
    }

    @Override
    public void onProviderDisabled(String provider) {}

    @Override
    public void onProviderEnabled(String provider) {}

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {}


    public IBinder onBind(Intent arg0) {
        return null;
    }

    @Override
    public void onResume() {
        super.onResume();
        mMapView.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        mMapView.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopUsingGPS();
        socket.disconnect();
        mMapView.onDestroy();
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        ft.replace(R.id.container_body, new Home());
        ft.addToBackStack(null);
        ft.commit();
        ((Main) getActivity()).getSupportActionBar().setTitle(getString(R.string.home));
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mMapView.onLowMemory();
    }
}
