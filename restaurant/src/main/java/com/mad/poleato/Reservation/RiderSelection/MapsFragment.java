package com.mad.poleato.Reservation.RiderSelection;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.location.Location;

import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryEventListener;
import com.google.android.gms.location.LocationListener;

import android.os.AsyncTask;
import android.os.StrictMode;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.Toast;

import androidx.navigation.Navigation;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;
import com.mad.poleato.MyDatabaseReference;
import com.mad.poleato.R;
import com.mad.poleato.Reservation.Reservation;
import com.mad.poleato.Reservation.Status;
import com.mad.poleato.Rider;
import com.onesignal.OneSignal;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

/**
 * A simple {@link Fragment} subclass.
 */
public class MapsFragment extends Fragment implements OnMapReadyCallback,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener,
        GoogleMap.OnMarkerClickListener {

    private GoogleMap mMap;
    private GeoQuery geoQuery;

    //Play Service Location
    private static final int MY_PERMISSION_REQUEST_CODE = 0001;
    private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 000001;

    private static final int EARTH_RADIUS = 6371; // Approx Earth radius in KM

    private LocationRequest mLocationRequest;
    private GoogleApiClient mGoogleApiClient;
    private Location mLastLocation;

    private static int UPDATE_INTERVAL = 5000; // 5 seconds
    private static int FASTEST_INTERVAL = 3000; // 3 seconds
    private static int DISPLACEMENT = 10;

    DatabaseReference referenceDB;
    GeoFire geoFire;
    private String restaurant_name;
    private double latitudeRest;
    private double longitudeRest;
    private Marker restaurantMarker;
    private HashMap<String, Rider> riders;
    private String currentUserID;
    private FirebaseAuth mAuth;
    private View fragView;
    private Reservation reservation;

    private String loggedID;

    private String localeShort;

    private ListView listView;
    private RiderListAdapter listAdapter;

    private SupportMapFragment mapFragment;

    private List<MyDatabaseReference> dbReferenceList;

    public MapsFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        //in order to create the logout menu (don't move!)
        setHasOptionsMenu(true);
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        fragView = inflater.inflate(R.layout.activity_maps, container, false);

        /**
         * Value of Order FROM RESERVATION FRAGMENT
         */

        loggedID = MapsFragmentArgs.fromBundle(getArguments()).getLoggedId();
        reservation = MapsFragmentArgs.fromBundle(getArguments()).getReservation();

        Locale locale = Locale.getDefault();
        localeShort = locale.toString().substring(0, 2);

        /**
         *
         */
        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        currentUserID = currentUser.getUid();


        OneSignal.startInit(getContext())
                .inFocusDisplaying(OneSignal.OSInFocusDisplayOption.Notification)
                .unsubscribeWhenNotificationsAreDisabled(true)
                .init();

        OneSignal.setSubscription(true);
        OneSignal.sendTag("User_ID", currentUserID);
        dbReferenceList = new ArrayList<>();

        /** Obtain the SupportMapFragment and get notified when the map is ready to be used.*/
        mapFragment = (SupportMapFragment) getChildFragmentManager()
                .findFragmentById(R.id.map_view);
        mapFragment.getMapAsync(this);
        mapFragment.getView().setVisibility(View.GONE);


        referenceDB = FirebaseDatabase.getInstance().getReference("Map");
        //ref = FirebaseDatabase.getInstance().getReference("restaurants").child(currentUserID);
        //referenceRiders = FirebaseDatabase.getInstance().getReference("deliveryman");
        geoFire = new GeoFire(referenceDB);

        /**
         * setup listview and adapter that will contain the list of riders;
         */
        riders = new HashMap<>();
        listView = (ListView) fragView.findViewById(R.id.rider_listview);
        listAdapter = new RiderListAdapter(getContext(), 0, reservation, loggedID);

        listView.setAdapter(listAdapter);

        setUpLocation();

        return fragView;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.clear();
        /** Inflate the menu; this adds items to the action bar if it is present.*/
        inflater.inflate(R.menu.map_menu, menu);

        /** Button to show map */
        menu.findItem(R.id.map_id).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if (mapFragment.getView().getVisibility() == View.VISIBLE) {
                    mapFragment.getView().setVisibility(View.GONE);
                    item.setIcon(R.mipmap.map_icon_round);
                } else {
                    mapFragment.getView().setVisibility(View.VISIBLE);
                    item.setIcon(R.mipmap.list_icon_round);
                }

                return true;
            }
        });

        super.onCreateOptionsMenu(menu, inflater);
    }


    /**
     * Functions related to location and google play services
     */

    private void setUpLocation() {
        if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Request routine permission
            ActivityCompat.requestPermissions(getActivity(), new String[]{
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
            }, MY_PERMISSION_REQUEST_CODE);
        } else {
            if (checkPlayServices()) {
                buildGoogleApiClient();
                createLocationRequest();
                displayLocation();
            }
        }
    }

    private void createLocationRequest() {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(UPDATE_INTERVAL);
        mLocationRequest.setFastestInterval(FASTEST_INTERVAL);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mLocationRequest.setSmallestDisplacement(DISPLACEMENT);

    }

    private void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(getContext())
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        mGoogleApiClient.connect();
    }

    private boolean checkPlayServices() {
        int resultCode = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(getContext());
        if (resultCode != ConnectionResult.SUCCESS) {
            if (GoogleApiAvailability.getInstance().isUserResolvableError(resultCode))
                GoogleApiAvailability.getInstance().getErrorDialog(getActivity(), resultCode, PLAY_SERVICES_RESOLUTION_REQUEST).show();
            else {
                Toast.makeText(getContext(), "This device is not suppoted", Toast.LENGTH_SHORT).show();

                /**
                 * GO FROM MAPSFRAGMENT to RESERVATION
                 */
                Navigation.findNavController(fragView).navigate(R.id.action_mapsFragment_id_to_reservation_id);
            }
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSION_REQUEST_CODE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (checkPlayServices()) {
                        buildGoogleApiClient();
                        createLocationRequest();
                        displayLocation();
                    }
                }
                break;
        }
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setOnMarkerClickListener(this);
    }

    @Override
    public void onLocationChanged(Location location) {
        mLastLocation = location;
        displayLocation();
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        displayLocation();
    }

    @Override
    public void onConnectionSuspended(int i) {
        mGoogleApiClient.connect();
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    /**
     * ********************************************************************************
     * body of activity
     * ********************************************************************************
     */

    private void displayLocation() {
        if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        /*
         * retrieve the name of current restaurant to put the title to the marker in its map
         */
        final DatabaseReference referenceRestaurant = FirebaseDatabase.getInstance().getReference("restaurants").child(currentUserID);
        dbReferenceList.add(new MyDatabaseReference(referenceRestaurant));
        int indexReference = dbReferenceList.size() - 1;
        ChildEventListener childEventListener;

        dbReferenceList.get(indexReference).getReference().child("Name").addChildEventListener(childEventListener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {
                restaurant_name = dataSnapshot.getValue().toString();
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {
                restaurant_name = dataSnapshot.getValue().toString();
            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot dataSnapshot) {
                //the restaurant must have the name
            }

            @Override
            public void onChildMoved(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {
                restaurant_name = dataSnapshot.getValue().toString();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });

        dbReferenceList.get(indexReference).setChildListener(childEventListener);

        /**
         * retrieve coordinates of restaurant to put the marker in the map
         */

        dbReferenceList.get(indexReference).getReference().child("Coordinates").addChildEventListener(childEventListener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {
                if (reservation.getStatus().equals(Status.COOKING) && getContext() != null) {
                    latitudeRest = Double.parseDouble(dataSnapshot.child("Latitude").getValue().toString());
                    longitudeRest = Double.parseDouble(dataSnapshot.child("Longitude").getValue().toString());
                    Log.d("Valerio", String.format("Restaurant location was changed: %f / %f", latitudeRest, longitudeRest));
                    //Update to firebase
                    geoFire.setLocation(currentUserID, new GeoLocation(latitudeRest, longitudeRest), new GeoFire.CompletionListener() {
                        @Override
                        public void onComplete(String key, DatabaseError error) {
                            if (reservation.getStatus().equals(Status.COOKING) && getContext() != null) {
                                //Add marker
                                Drawable icon = ContextCompat.getDrawable(getContext(), R.drawable.restaurant_icon);
                                BitmapDescriptor markerIcon = getMarkerIconFromDrawable(icon);
                                if (restaurantMarker != null)
                                    restaurantMarker = null;

                                restaurantMarker = mMap.addMarker(new MarkerOptions()
                                        .position(new LatLng(latitudeRest, longitudeRest))
                                        .title(restaurant_name)
                                        .icon(markerIcon)
                                );

                                /**Move camera to this position*/
                                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(latitudeRest, longitudeRest), 15.0f));
                            }
                        }
                    });
                }
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {
                if (reservation.getStatus().equals(Status.COOKING) && getContext() != null) {
                    latitudeRest = Double.parseDouble(dataSnapshot.child("Latitude").getValue().toString());
                    longitudeRest = Double.parseDouble(dataSnapshot.child("Longitude").getValue().toString());
                    Log.d("Valerio", String.format("Restaurant location was changed: %f / %f", latitudeRest, longitudeRest));
                    //Update to firebase
                    geoFire.setLocation(currentUserID, new GeoLocation(latitudeRest, longitudeRest), new GeoFire.CompletionListener() {
                        @Override
                        public void onComplete(String key, DatabaseError error) {
                            if (reservation.getStatus().equals(Status.COOKING) && getContext() != null) {
                                //Add marker
                                Drawable icon = ContextCompat.getDrawable(getContext(), R.drawable.restaurant_icon);
                                BitmapDescriptor markerIcon = getMarkerIconFromDrawable(icon);
                                if (restaurantMarker != null)
                                    restaurantMarker = null;

                                restaurantMarker = mMap.addMarker(new MarkerOptions()
                                        .position(new LatLng(latitudeRest, longitudeRest))
                                        .title(restaurant_name)
                                        .icon(markerIcon)
                                );

                                /**Move camera to this position*/
                                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(latitudeRest, longitudeRest), 15.0f));
                            }
                        }
                    });
                }
            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot dataSnapshot) {
                // the restaurant must have an address and coordinates
            }

            @Override
            public void onChildMoved(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {
                if (reservation.getStatus().equals(Status.COOKING) && getContext() != null) {
                    latitudeRest = Double.parseDouble(dataSnapshot.child("Latitude").getValue().toString());
                    longitudeRest = Double.parseDouble(dataSnapshot.child("Longitude").getValue().toString());
                    Log.d("Valerio", String.format("Restaurant location was changed: %f / %f", latitudeRest, longitudeRest));
                    //Update to firebase
                    geoFire.setLocation(currentUserID, new GeoLocation(latitudeRest, longitudeRest), new GeoFire.CompletionListener() {
                        @Override
                        public void onComplete(String key, DatabaseError error) {
                            if (reservation.getStatus().equals(Status.COOKING) && getContext() != null) {
                                //Add marker
                                Drawable icon = ContextCompat.getDrawable(getContext(), R.drawable.restaurant_icon);
                                BitmapDescriptor markerIcon = getMarkerIconFromDrawable(icon);
                                if (restaurantMarker != null)
                                    restaurantMarker = null;

                                restaurantMarker = mMap.addMarker(new MarkerOptions()
                                        .position(new LatLng(latitudeRest, longitudeRest))
                                        .title(restaurant_name)
                                        .icon(markerIcon)
                                );

                                /**Move camera to this position*/
                                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(latitudeRest, longitudeRest), 15.0f));
                            }
                        }
                    });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
        dbReferenceList.get(indexReference).setChildListener(childEventListener);

        /**
         * retrieve rider coordinates and update the list and the map
         */


        geoQuery = geoFire.queryAtLocation(new GeoLocation(latitudeRest, longitudeRest), 2);

        geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
            @Override
            public void onKeyEntered(String key, final GeoLocation location) {
                final String riderID = key;

                if (!riderID.equals(currentUserID)) {
                    DatabaseReference referenceRider = FirebaseDatabase.getInstance().getReference("deliveryman/" + riderID);
                    dbReferenceList.add(new MyDatabaseReference(referenceRider));
                    int indexReference = dbReferenceList.size() - 1;
                    ValueEventListener valueEventListener;

                    dbReferenceList.get(indexReference).getReference().addValueEventListener(valueEventListener = new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull final DataSnapshot dataSnapshot) {
                            if (dataSnapshot.hasChild("Busy") &&
                                    dataSnapshot.hasChild("IsActive") &&
                                    dataSnapshot.child("IsActive").getValue().toString().equals("true") &&
                                    reservation.getStatus().equals(Status.COOKING) &&
                                    getContext() != null) {

                                final double latRider = location.latitude;
                                final double longRider = location.longitude;
                                String status;
                                int numberOfOrder= 0;
                                if(dataSnapshot.child("Busy").getValue().toString().equals("true"))
                                    status = localeShort.equals("en") ? "Busy" : "Occupato";
                                else
                                    status = localeShort.equals("en") ? "Free" : "Libero";

                                String timeCurrentOrder= reservation.getTime();

                                for(DataSnapshot dataSnapshotRequests : dataSnapshot.child("requests").getChildren()){
                                    if(dataSnapshotRequests.exists()) {
                                        String timeRequest = dataSnapshotRequests
                                                .child("deliveryTime").getValue().toString().split(" ")[1];
                                        if(timeRequest.compareTo(timeCurrentOrder) < 0)
                                            numberOfOrder++;
                                    }
                                }

                                if (!riders.containsKey(riderID)) {
                                    Rider rider = new Rider(riderID, latRider, longRider, latitudeRest, longitudeRest, status, numberOfOrder);
                                    riders.put(riderID, rider);
                                    listAdapter.addRider(riders.get(riderID));
                                } else {
                                    riders.get(riderID).setLatitude(latRider);
                                    riders.get(riderID).setLongitude(longRider);
                                    riders.get(riderID).setDistance(latitudeRest, longitudeRest);
                                    for (int i = 0; i < listAdapter.getCount(); i++) {
                                        if (listAdapter.getItem(i).getId().equals(riderID)) {
                                            listAdapter.getItem(i).setLatitude(latRider);
                                            listAdapter.getItem(i).setLongitude(longRider);
                                            listAdapter.getItem(i).setDistance(latitudeRest, longitudeRest);
                                        }
                                    }
                                }

                                listAdapter.notifyDataSetChanged();


                                /**Update restaurant map*/
                                geoFire.setLocation(riderID,
                                        new GeoLocation(latRider, longRider), new GeoFire.CompletionListener() {
                                            @Override
                                            public void onComplete(String key, DatabaseError error) {
                                                if (reservation.getStatus().equals(Status.COOKING) &&
                                                        getContext() != null) {

                                                    //Add marker
                                                    if (riders.get(riderID).getMarker() != null)
                                                        riders.get(riderID).getMarker().remove();
                                                    Drawable icon = ContextCompat.getDrawable(getContext(), R.drawable.ic_baseline_directions_bike_24px);
                                                    BitmapDescriptor markerIcon = getMarkerIconFromDrawable(icon);
                                                    riders.get(riderID).setMarker(mMap.addMarker(new MarkerOptions()
                                                            .position(new LatLng(latRider, longRider))
                                                            .title(riderID)
                                                            .icon(markerIcon)
                                                    ));
                                                }
                                            }
                                        });
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError databaseError) {
                            Log.d("ValerioMap", "onCancelled referenceRider -> " + databaseError.getMessage());
                        }
                    });

                    dbReferenceList.get(indexReference).setValueListener(valueEventListener);
                }
                Log.d("ValerioMap", String.format("Key %s entered the search area at [%f,%f]", key, location.latitude, location.longitude));
            }

            @Override
            public void onKeyExited(String key) {
                String riderID = key;
                if (!riderID.equals(currentUserID)) {
                    /**
                     * remove busy rider if is in the adapter
                     */
                    if (riders.containsKey(riderID)) {
                        riders.get(riderID).getMarker().remove();
                        riders.remove(riderID);
                        listAdapter.removeRider(riderID);
                        listAdapter.notifyDataSetChanged();
                    }
                }
                Log.d("ValerioMap", String.format("Key %s is no longer in the search area", key));
            }

            @Override
            public void onKeyMoved(String key, final GeoLocation location) {
                final String riderID = key;

                if (!riderID.equals(currentUserID)) {
                    DatabaseReference referenceRider = FirebaseDatabase.getInstance().getReference("deliveryman/" + riderID);
                    dbReferenceList.add(new MyDatabaseReference(referenceRider));
                    int indexReference = dbReferenceList.size() - 1;
                    ValueEventListener valueEventListener;

                    dbReferenceList.get(indexReference).getReference().addValueEventListener(valueEventListener = new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull final DataSnapshot dataSnapshot) {
                            if (dataSnapshot.hasChild("Busy") &&
                                    dataSnapshot.hasChild("IsActive") &&
                                    dataSnapshot.child("IsActive").getValue().toString().equals("true") &&
                                    reservation.getStatus().equals(Status.COOKING) &&
                                    getContext() != null) {
                                final double latRider = location.latitude;
                                final double longRider = location.longitude;
                                String status;
                                int numberOfOrder= 0;
                                if(dataSnapshot.child("Busy").getValue().toString().equals("true"))
                                    status = localeShort.equals("en") ? "Busy" : "Occupato";
                                else
                                    status = localeShort.equals("en") ? "Free" : "Libero";

                                String timeCurrentOrder= reservation.getTime();

                                for(DataSnapshot dataSnapshotRequests : dataSnapshot.child("requests").getChildren()){
                                    if(dataSnapshotRequests.exists()) {
                                        String timeRequest = dataSnapshotRequests
                                                .child("deliveryTime").getValue().toString().split(" ")[1];
                                        if(timeRequest.compareTo(timeCurrentOrder) < 0)
                                            numberOfOrder++;
                                    }
                                }

                                final double distance = computeDistance(latitudeRest, longitudeRest, latRider, longRider);

                                if (distance <= 2) {
                                    if (!riders.containsKey(riderID)) {
                                        Rider rider = new Rider(riderID, latRider, longRider, latitudeRest, longitudeRest, status, numberOfOrder);
                                        riders.put(riderID, rider);
                                        listAdapter.addRider(riders.get(riderID));
                                    } else {
                                        riders.get(riderID).setLatitude(latRider);
                                        riders.get(riderID).setLongitude(longRider);
                                        riders.get(riderID).setDistance(latitudeRest, longitudeRest);
                                        for (int i = 0; i < listAdapter.getCount(); i++) {
                                            if (listAdapter.getItem(i).getId().equals(riderID)) {
                                                listAdapter.getItem(i).setLatitude(latRider);
                                                listAdapter.getItem(i).setLongitude(longRider);
                                                listAdapter.getItem(i).setDistance(latitudeRest, longitudeRest);
                                            }
                                        }
                                    }

                                    listAdapter.notifyDataSetChanged();


                                    /**Update restaurant map*/
                                    geoFire.setLocation(riderID,
                                            new GeoLocation(latRider, longRider), new GeoFire.CompletionListener() {
                                                @Override
                                                public void onComplete(String key, DatabaseError error) {
                                                    if (reservation.getStatus().equals(Status.COOKING) &&
                                                            getContext() != null) {

                                                        //Add marker
                                                        if (riders.get(riderID).getMarker() != null)
                                                            riders.get(riderID).getMarker().remove();
                                                        Drawable icon = ContextCompat.getDrawable(getContext(), R.drawable.ic_baseline_directions_bike_24px);
                                                        BitmapDescriptor markerIcon = getMarkerIconFromDrawable(icon);
                                                        riders.get(riderID).setMarker(mMap.addMarker(new MarkerOptions()
                                                                .position(new LatLng(latRider, longRider))
                                                                .title(riderID)
                                                                .icon(markerIcon)
                                                        ));
                                                    }
                                                }
                                            });
                                }
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError databaseError) {
                            Log.d("ValerioMap", "onCancelled referenceRider -> " + databaseError.getMessage());
                        }
                    });
                    dbReferenceList.get(indexReference).setValueListener(valueEventListener);
                }
                Log.d("ValerioMap", String.format("Key %s moved within the search area to [%f,%f]", key, location.latitude, location.longitude));
            }

            @Override
            public void onGeoQueryReady() {
                DatabaseReference referenceMap = FirebaseDatabase.getInstance().getReference("Map");
                dbReferenceList.add(new MyDatabaseReference(referenceMap));
                int indexReference = dbReferenceList.size() - 1;
                ValueEventListener valueEventListener;

                dbReferenceList.get(indexReference).getReference().addValueEventListener(valueEventListener = new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull final DataSnapshot dataSnapshot) {
                        for (DataSnapshot currentPosition : dataSnapshot.getChildren()) {
                            if (!currentPosition.getKey().equals(currentUserID)) {
                                final String riderID = currentPosition.getKey();
                                final double latRider = Double.parseDouble(currentPosition.child("l/0").getValue().toString());
                                final double longRider = Double.parseDouble(currentPosition.child("l/1").getValue().toString());

                                final double distance = computeDistance(latitudeRest, longitudeRest, latRider, longRider);
                                if (distance <= 2) {
                                    DatabaseReference referenceRider = FirebaseDatabase.getInstance()
                                            .getReference("deliveryman/" + riderID);
                                    dbReferenceList.add(new MyDatabaseReference(referenceRider));
                                    int indexReference = dbReferenceList.size() - 1;
                                    ValueEventListener valueEventListener;

                                    dbReferenceList.get(indexReference).getReference()
                                            .addValueEventListener(valueEventListener = new ValueEventListener() {
                                        @Override
                                        public void onDataChange(@NonNull final DataSnapshot dataSnapshotRider) {
                                            if (dataSnapshotRider.hasChild("Busy") &&
                                                    dataSnapshotRider.hasChild("IsActive") &&
                                                    dataSnapshotRider.child("IsActive").getValue().toString().equals("true") &&
                                                    reservation.getStatus().equals(Status.COOKING) &&
                                                    getContext() != null) {
                                                if (!riders.containsKey(riderID)) {
                                                    String status;
                                                    int numberOfOrder= 0;
                                                    if(dataSnapshotRider.child("Busy").getValue().toString().equals("true"))
                                                        status = localeShort.equals("en") ? "Busy" : "Occupato";
                                                    else
                                                        status = localeShort.equals("en") ? "Free" : "Libero";

                                                    String timeCurrentOrder= reservation.getTime();

                                                    for(DataSnapshot dataSnapshotRequests : dataSnapshotRider.child("requests")
                                                                                                                .getChildren()){
                                                        if(dataSnapshotRequests.exists()) {
                                                            String timeRequest = dataSnapshotRequests
                                                                    .child("deliveryTime").getValue().toString().split(" ")[1];
                                                            if(timeRequest.compareTo(timeCurrentOrder) < 0)
                                                                numberOfOrder++;
                                                        }
                                                    }

                                                    Rider rider = new Rider(riderID, latRider, longRider, latitudeRest,
                                                                                    longitudeRest, status, numberOfOrder);
                                                    riders.put(riderID, rider);
                                                    listAdapter.addRider(riders.get(riderID));
                                                } else {
                                                    riders.get(riderID).setLatitude(latRider);
                                                    riders.get(riderID).setLongitude(longRider);
                                                    riders.get(riderID).setDistance(latitudeRest, longitudeRest);
                                                    for (int i = 0; i < listAdapter.getCount(); i++) {
                                                        if (listAdapter.getItem(i).getId().equals(riderID)) {
                                                            listAdapter.getItem(i).setLatitude(latRider);
                                                            listAdapter.getItem(i).setLongitude(longRider);
                                                            listAdapter.getItem(i).setDistance(latitudeRest, longitudeRest);
                                                        }
                                                    }
                                                }

                                                listAdapter.notifyDataSetChanged();


                                                //Update restaurant map
                                                geoFire.setLocation(riderID,
                                                        new GeoLocation(latRider, longRider), new GeoFire.CompletionListener() {
                                                            @Override
                                                            public void onComplete(String key, DatabaseError error) {
                                                                if (reservation.getStatus().equals(Status.COOKING) &&
                                                                        getContext() != null) {

                                                                    //Add marker
                                                                    if (riders.get(riderID).getMarker() != null)
                                                                        riders.get(riderID).getMarker().remove();
                                                                    Drawable icon = ContextCompat.getDrawable(getContext(),
                                                                                            R.drawable.ic_baseline_directions_bike_24px);
                                                                    BitmapDescriptor markerIcon = getMarkerIconFromDrawable(icon);
                                                                    riders.get(riderID).setMarker(mMap.addMarker(new MarkerOptions()
                                                                            .position(new LatLng(latRider, longRider))
                                                                            .title(riderID)
                                                                            .icon(markerIcon)
                                                                    ));
                                                                }
                                                            }
                                                        }); // end geofire set location
                                            }// end if check isActive, busy presence and status cooking
                                        }

                                        @Override
                                        public void onCancelled(@NonNull DatabaseError databaseError) {

                                        }
                                    });
                                    dbReferenceList.get(indexReference).setValueListener(valueEventListener);
                                }// end if check distance
                            } // end if check currentID
                        } // end for children
                    }// end onDataChange

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {

                    }
                });

                dbReferenceList.get(indexReference).setValueListener(valueEventListener);
                Log.d("ValerioMap", "onGeoQueryReady -> AllReady");
            }

            @Override
            public void onGeoQueryError(DatabaseError error) {
                Log.d("ValerioMap", "onGeoQueryError -> " + error.getMessage());
            }
        });
    }

    private BitmapDescriptor getMarkerIconFromDrawable(Drawable drawable) {
        Canvas canvas = new Canvas();
        Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        canvas.setBitmap(bitmap);
        drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
        drawable.draw(canvas);
        return BitmapDescriptorFactory.fromBitmap(bitmap);
    }

    @Override
    public boolean onMarkerClick(Marker marker) {
        if (!marker.getTitle().equals(restaurant_name)) {
            final String riderID = marker.getTitle();
            final AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
            builder.setTitle(this.getString(R.string.rider_selected) + ": " + riderID);

            builder.setMessage(this.getString(R.string.msg_rider_selected));
            builder.setPositiveButton(this.getString(R.string.choice_confirm), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    reservation.setStat(getContext().getString(R.string.delivery));
                    reservation.setStatus(Status.DELIVERY);
                    FirebaseDatabase.getInstance().getReference("customers/"+reservation.getCustomerID()
                                                        +"/reservations/"+reservation.getOrder_id()+"/status/en").setValue("Delivering");
                    FirebaseDatabase.getInstance().getReference("customers/"+reservation.getCustomerID()
                                                        +"/reservations/"+reservation.getOrder_id()+"/status/it").setValue("In Consegna");
                    notifyRider(riderID);
                }
            });
            builder.setNegativeButton(this.getString(R.string.choice_cancel), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    dialogInterface.cancel();
                }
            });

            AlertDialog dialog = builder.create();
            dialog.show();

            return true;
        } else
            return false;
    }

    private void notifyRider(final String riderID) {

        /** retrieve the restaurant information */
        final DatabaseReference referenceRestaurant = FirebaseDatabase.getInstance().getReference("restaurants").child(loggedID);
        referenceRestaurant.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshotRestaurant) {
                if (getContext() != null &&
                        dataSnapshotRestaurant.exists() &&
                        dataSnapshotRestaurant.hasChild("Address") &&
                        dataSnapshotRestaurant.hasChild("Name") &&
                        dataSnapshotRestaurant.hasChild("Phone") &&
                        dataSnapshotRestaurant.hasChild("reservations") &&
                        dataSnapshotRestaurant.child("reservations").hasChild(reservation.getOrder_id()) &&
                        dataSnapshotRestaurant.child("reservations/" + reservation.getOrder_id()).hasChild("status") &&
                        dataSnapshotRestaurant.child("reservations/" + reservation.getOrder_id() + "/status").hasChild("it") &&
                        dataSnapshotRestaurant.child("reservations/" + reservation.getOrder_id() + "/status").hasChild("en") &&
                        dataSnapshotRestaurant.child("reservations/" + reservation.getOrder_id()
                                + "/status/it").getValue().toString().equals("Preparazione") &&
                        dataSnapshotRestaurant.child("reservations/" + reservation.getOrder_id()
                                + "/status/en").getValue().toString().equals("Cooking")) {

                    DatabaseReference referenceRider = FirebaseDatabase.getInstance().getReference("deliveryman").child(riderID);
                    DatabaseReference reservationRider = referenceRider.child("requests").push();
                    final String addressRestaurant = dataSnapshotRestaurant.child("Address").getValue().toString();
                    final String nameRestaurant = dataSnapshotRestaurant.child("Name").getValue().toString();
                    final String phoneRestaurant = dataSnapshotRestaurant.child("Phone").getValue().toString();
                    reservationRider.child("addressCustomer").setValue(reservation.getAddress());
                    reservationRider.child("addressRestaurant").setValue(addressRestaurant);
                    reservationRider.child("CustomerID").setValue(reservation.getCustomerID());

                    //update the delivery status
                    reservationRider.child("nameRestaurant").setValue(nameRestaurant);
                    reservationRider.child("numberOfDishes").setValue(reservation.getNumberOfDishes());
                    reservationRider.child("orderID").setValue(reservation.getOrder_id());
                    reservationRider.child("restaurantID").setValue(loggedID);
                    reservationRider.child("nameCustomer").setValue(reservation.getName() + " " + reservation.getSurname());
                    reservationRider.child("totalPrice").setValue(reservation.getTotalPrice());
                    reservationRider.child("phoneCustomer").setValue(reservation.getPhone());
                    reservationRider.child("phoneRestaurant").setValue(phoneRestaurant);

                    /** compose the date in the format YYYY/MM/DD HH:mm */
                    String[] date_components = reservation.getDate().split("/"); //format: dd/mm/yyyy
                    String timeStr = date_components[2] + "/" + date_components[1] + "/" + date_components[0] + " " +
                            reservation.getTime();
                    reservationRider.child("deliveryTime").setValue(timeStr);

                    FirebaseDatabase.getInstance().getReference("restaurants/"+
                            loggedID+"/reservations/"+reservation.getOrder_id()+"/status/en").setValue("Delivering");
                    FirebaseDatabase.getInstance().getReference("restaurants/"+
                            loggedID+"/reservations/"+reservation.getOrder_id()+"/status/it").setValue("In consegna");
                    sendNotification(riderID);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.d("Valerio", "NotifyRandomRider -> retrieve restaurant info: " + databaseError.getMessage());
            }
        });
    }

    private void sendNotification(final String childID) {
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                int SDK_INT = android.os.Build.VERSION.SDK_INT;
                if (SDK_INT > 8) {
                    StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
                            .permitAll().build();
                    StrictMode.setThreadPolicy(policy);
                    String send_email;

                    /** This is a Simple Logic to Send Notification different Device Programmatically.... */
                    send_email = childID;

                    try {
                        String jsonResponse;

                        URL url = new URL("https://onesignal.com/api/v1/notifications");
                        HttpURLConnection con = (HttpURLConnection) url.openConnection();
                        con.setUseCaches(false);
                        con.setDoOutput(true);
                        con.setDoInput(true);

                        con.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                        con.setRequestProperty("Authorization", "Basic YjdkNzQzZWQtYTlkYy00MmIzLTg0NDUtZmQ3MDg0ODc4YmQ1");
                        con.setRequestMethod("POST");

                        String strJsonBody = "{"
                                + "\"app_id\": \"a2d0eb0d-4b93-4b96-853e-dcfe6c34778e\","

                                + "\"filters\": [{\"field\": \"tag\", \"key\": \"User_ID\", \"relation\": \"=\", \"value\": \"" + send_email + "\"}],"

                                + "\"data\": {\"Delivery\": \"New order\"},"
                                + "\"contents\": {\"en\": \"New order to deliver\"}"
                                + "}";


                        System.out.println("strJsonBody:\n" + strJsonBody);

                        byte[] sendBytes = strJsonBody.getBytes("UTF-8");
                        con.setFixedLengthStreamingMode(sendBytes.length);

                        OutputStream outputStream = con.getOutputStream();
                        outputStream.write(sendBytes);

                        int httpResponse = con.getResponseCode();
                        System.out.println("httpResponse: " + httpResponse);

                        if (httpResponse >= HttpURLConnection.HTTP_OK
                                && httpResponse < HttpURLConnection.HTTP_BAD_REQUEST) {
                            Scanner scanner = new Scanner(con.getInputStream(), "UTF-8");
                            jsonResponse = scanner.useDelimiter("\\A").hasNext() ? scanner.next() : "";
                            scanner.close();
                        } else {
                            Scanner scanner = new Scanner(con.getErrorStream(), "UTF-8");
                            jsonResponse = scanner.useDelimiter("\\A").hasNext() ? scanner.next() : "";
                            scanner.close();
                        }
                        System.out.println("jsonResponse:\n" + jsonResponse);

                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                }
            }
        });

        /**
         * GO FROM MAPSFRAGMENT to RESERVATION
         */
        Navigation.findNavController(fragView).navigate(R.id.action_mapsFragment_id_to_reservation_id);
    }

    public double computeDistance(double latitudeRest, double longitudeRest, double latitudeRider, double longitudeRider) {
        double startLat = latitudeRest;
        double endLat = latitudeRider;

        double startLong = longitudeRest;
        double endLong = longitudeRest;

        double dLat = Math.toRadians((endLat - startLat));
        double dLong = Math.toRadians((endLong - startLong));

        startLat = Math.toRadians(startLat);
        endLat = Math.toRadians(endLat);

        double a = haversin(dLat) + Math.cos(startLat) * Math.cos(endLat) * haversin(dLong);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS * c; // <-- d
    }

    ;

    public static double haversin(double val) {
        return Math.pow(Math.sin(val / 2), 2);
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        for (int i = 0; i < dbReferenceList.size(); i++) {
            dbReferenceList.get(i).removeAllListener();
        }
        if (geoQuery != null)
            geoQuery.removeAllListeners();
    }
}
