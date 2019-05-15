package com.mad.poleato.Rides;


import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.StrictMode;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.widget.CardView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.navigation.Navigation;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.mad.poleato.R;
import com.onesignal.OneSignal;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import static android.app.Activity.RESULT_OK;
import static com.mad.poleato.Rides.DeliveringActivity.RESULT_PERMISSION_DENIED;


/**
 * A simple {@link Fragment} subclass.
 */
public class RidesFragment extends Fragment {

    private Toast myToast;

    private final static int MAPS_ACTIVITY_CODE = 1; //code for startActivityForResult

    private Activity hostActivity;
    private View fragView;

    private Map<String, TextView> tv_Fields;
    private Button status_button;
    private FloatingActionButton map_button;
    private ImageButton show_more_button;

    //to set the visibility to gone when there are no ride available
    private ImageView emptyView;
    private CardView rideCardView;
    private FrameLayout rideFrameLayout;

    //the key for that order at rider side
    private String reservationKey;
    //the ride status:if is delivering or if the order is still at the restaurant
    private Boolean delivering;

    //this flag is to avoid multiple order that will override the maps
    private boolean isRunning;

    //auth
    private String currentUserID;
    private FirebaseAuth mAuth;

    private Ride ride;


    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        this.hostActivity = this.getActivity();

        if (hostActivity != null) {
            myToast = Toast.makeText(hostActivity, "", Toast.LENGTH_LONG);
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        //in order to create the logout menu (don't move!)
        setHasOptionsMenu(true);
        super.onCreate(savedInstanceState);

        //initially the rider is free from work
        isRunning = false;

        tv_Fields = new HashMap<>();

        //authenticate the user
        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        currentUserID = currentUser.getUid();

        OneSignal.startInit(getContext())
                .inFocusDisplaying(OneSignal.OSInFocusDisplayOption.Notification)
                .unsubscribeWhenNotificationsAreDisabled(true)
                .init();

        OneSignal.setSubscription(true);
        OneSignal.sendTag("User_ID", currentUserID);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.clear();
        // Inflate the menu; this adds items to the action bar if it is present.
        inflater.inflate(R.menu.popup_account_settings, menu);
        menu.findItem(R.id.logout).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                //logout
                Log.d("matte", "Logout");
                FirebaseAuth.getInstance().signOut();
                //                OneSignal.sendTag("User_ID", "");
                OneSignal.setSubscription(false);

                /**
                 *  GO TO LOGIN ****
                 */
                Navigation.findNavController(fragView).navigate(R.id.action_rides_id_to_signInActivity);
                getActivity().finish();
                return true;
            }
        });
        super.onCreateOptionsMenu(menu,inflater);
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        fragView = inflater.inflate(R.layout.ride_layout, container, false);

        //collect the TextView inside the map, collect the button and attach the listeners
        collectFields();

        //download the ride data from firebase
        retrieveOrderInfo();

        return fragView;
    }


    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

    }


    private void collectFields() {

        tv_Fields.put("address", (TextView) fragView.findViewById(R.id.deliveryAddress_tv));
        tv_Fields.put("name", (TextView) fragView.findViewById(R.id.customerName_tv));
        tv_Fields.put("restaurant", (TextView) fragView.findViewById(R.id.restaurant_tv));
        tv_Fields.put("phone", (TextView) fragView.findViewById(R.id.phone_tv));
        tv_Fields.put("dishes", (TextView) fragView.findViewById(R.id.dishes_tv));
        tv_Fields.put("hour", (TextView) fragView.findViewById(R.id.time_tv));
        tv_Fields.put("price", (TextView) fragView.findViewById(R.id.cost_tv));
        tv_Fields.put("status", (TextView) fragView.findViewById(R.id.status_tv));

        rideCardView = (CardView) fragView.findViewById(R.id.rideCardView);
        rideFrameLayout = (FrameLayout) fragView.findViewById(R.id.rideFrameLayout);
        emptyView = (ImageView) fragView.findViewById(R.id.ride_empty_view);

        map_button = (FloatingActionButton) fragView.findViewById(R.id.map_button);
        map_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getActivity(), DeliveringActivity.class);
                intent.putExtra("ride", ride);
                intent.putExtra("order_key", reservationKey);
                intent.putExtra("delivering", delivering);
                startActivityForResult(intent, MAPS_ACTIVITY_CODE);
            }
        });

        status_button = (Button) fragView.findViewById(R.id.status_button);
        status_button.setOnClickListener(new OnClickRideStatus());

        show_more_button = (ImageButton) fragView.findViewById(R.id.showMoreButton);
        show_more_button.setOnClickListener(new OnClickShowMore());

        //initialize visibility
        show_empty_view();

    }

    private void show_empty_view(){

        rideCardView.setVisibility(View.GONE);
        rideFrameLayout.setVisibility(View.GONE);
        emptyView.setVisibility(View.VISIBLE);
    }

    private void show_view(){

        emptyView.setVisibility(View.GONE);
        rideCardView.setVisibility(View.VISIBLE);
        rideFrameLayout.setVisibility(View.VISIBLE);
    }

    private class OnClickShowMore implements View.OnClickListener{

        @Override
        public void onClick(View v) {
            FragmentTransaction ft = getFragmentManager().beginTransaction();
            Fragment prev = getFragmentManager().findFragmentByTag("show_more_fragment");
            if (prev != null) {
                ft.remove(prev);
            }
            ft.addToBackStack(null);
            ShowMoreFragment showMoreFrag = new ShowMoreFragment();
            Bundle bundle = new Bundle();
            //pass the restaurant info
            bundle.putString("name", ride.getNameRestaurant());
            bundle.putString("address", ride.getAddressRestaurant());
            bundle.putString("phone", ride.getPhoneRestaurant());
            showMoreFrag.setArguments(bundle);
            showMoreFrag.show(ft, "show_more_fragment");
        }
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if(requestCode == MAPS_ACTIVITY_CODE){
            if(resultCode == RESULT_OK){
                //retrieve the delivered hour
                Bundle b = data.getExtras();
                String deliveredHour = b.get("deliveryHour").toString();

                //terminate the ride
                terminateRide(deliveredHour);

            }
            else if(resultCode == RESULT_PERMISSION_DENIED){
                myToast.setText(hostActivity.getString(R.string.permission_denied));
                myToast.show();
            }
        }
    }


    private void retrieveOrderInfo() {

        DatabaseReference reference = FirebaseDatabase.getInstance()
                .getReference("deliveryman/" + currentUserID + "/reservations");

        reference.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {
                if (dataSnapshot.exists() &&
                        getContext() != null &&
                        !isRunning &&
                        dataSnapshot.hasChild("addressCustomer") &&
                        dataSnapshot.hasChild("addressRestaurant") &&
                        dataSnapshot.hasChild("CustomerID") &&
                        dataSnapshot.hasChild("delivering") &&
                        dataSnapshot.hasChild("nameRestaurant") &&
                        dataSnapshot.hasChild("numberOfDishes") &&
                        dataSnapshot.hasChild("orderID") &&
                        dataSnapshot.hasChild("restaurantID") &&
                        dataSnapshot.hasChild("nameCustomer") &&
                        dataSnapshot.hasChild("totalPrice") &&
                        dataSnapshot.hasChild("phoneCustomer") &&
                        dataSnapshot.hasChild("phoneRestaurant") &&
                        dataSnapshot.hasChild("time")) {

                    //retrieve order infos from DB
                    reservationKey = dataSnapshot.getKey();
                    String customerAddress = dataSnapshot.child("addressCustomer").getValue().toString();
                    String nameRestaurant = dataSnapshot.child("nameRestaurant").getValue().toString();
                    String numDishes = dataSnapshot.child("numberOfDishes").getValue().toString();
                    String orderID = dataSnapshot.child("orderID").getValue().toString();
                    String nameCustomer = dataSnapshot.child("nameCustomer").getValue().toString();
                    String customerID = dataSnapshot.child("CustomerID").getValue().toString();
                    String restaurantID = dataSnapshot.child("restaurantID").getValue().toString();
                    //replace comma with dot before change it in double
                    String priceStr = dataSnapshot.child("totalPrice").getValue()
                            .toString().replace(",", ".");

                    //get the price with at leat 2 decimal digit
                    DecimalFormat decimalFormat = new DecimalFormat("#0.00"); //two decimal
                    double d = Double.parseDouble(priceStr);
                    priceStr = decimalFormat.format(d);

                    String restaurantAddress = dataSnapshot.child("addressRestaurant").getValue().toString();
                    String deliveryTime = dataSnapshot.child("time").getValue().toString();
                    String customerPhone = dataSnapshot.child("phoneCustomer").getValue().toString();
                    String restaurantPhone = dataSnapshot.child("phoneRestaurant").getValue().toString();
                    delivering = (Boolean) dataSnapshot.child("delivering").getValue();

                    //set the button and status text based on the rider status
                    String buttonText;
                    String statusText;
                    int textColor;
                    if(delivering){
                        buttonText = getString(R.string.maps_button_order_delivered);
                        statusText = getString(R.string.delivering);
                        textColor = getResources().getColor(R.color.colorPrimary);
                    }
                    else{
                        buttonText = getString(R.string.maps_button_to_restaurant);
                        statusText = getString(R.string.not_delivering);
                        textColor = Color.RED;
                    }
                    status_button.setText(buttonText);
                    tv_Fields.get("status").setText(statusText);
                    tv_Fields.get("status").setTextColor(textColor);



                    //fill the fields
                    tv_Fields.get("address").setText(customerAddress);
                    tv_Fields.get("name").setText(nameCustomer);
                    tv_Fields.get("restaurant").setText(nameRestaurant);
                    tv_Fields.get("phone").setText(customerPhone);
                    tv_Fields.get("dishes").setText(numDishes);
                    tv_Fields.get("hour").setText(deliveryTime);
                    tv_Fields.get("price").setText(priceStr+"€");

                    ride = new Ride(orderID, customerAddress, restaurantAddress,
                            nameCustomer, nameRestaurant, priceStr,
                            numDishes, customerPhone, restaurantPhone,
                            deliveryTime, customerID, restaurantID);
                    //lock the rider
                    isRunning = true;

                    //hide the empty view
                    show_view();
                }
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {
                if (dataSnapshot.exists() &&
                        getContext() != null &&
                        dataSnapshot.hasChild("addressCustomer") &&
                        dataSnapshot.hasChild("addressRestaurant") &&
                        dataSnapshot.hasChild("CustomerID") &&
                        dataSnapshot.hasChild("delivering") &&
                        dataSnapshot.hasChild("nameRestaurant") &&
                        dataSnapshot.hasChild("numberOfDishes") &&
                        dataSnapshot.hasChild("orderID") &&
                        dataSnapshot.hasChild("restaurantID") &&
                        dataSnapshot.hasChild("nameCustomer") &&
                        dataSnapshot.hasChild("totalPrice") &&
                        dataSnapshot.hasChild("phoneCustomer") &&
                        dataSnapshot.hasChild("phoneRestaurant") &&
                        dataSnapshot.hasChild("time")) {

                    //a new reservation can be created only if the rider is not busy
                    if(!isRunning){
                        //retrieve order infos from DB
                        reservationKey = dataSnapshot.getKey();
                        String customerAddress = dataSnapshot.child("addressCustomer").getValue().toString();
                        String nameRestaurant = dataSnapshot.child("nameRestaurant").getValue().toString();
                        String numDishes = dataSnapshot.child("numberOfDishes").getValue().toString();
                        String orderID = dataSnapshot.child("orderID").getValue().toString();
                        String nameCustomer = dataSnapshot.child("nameCustomer").getValue().toString();
                        String customerID = dataSnapshot.child("CustomerID").getValue().toString();
                        String restaurantID = dataSnapshot.child("restaurantID").getValue().toString();
                        //replace comma with dot before change it in double
                        String priceStr = dataSnapshot.child("totalPrice").getValue()
                                .toString().replace(",", ".");

                        //get the price with at leat 2 decimal digit
                        DecimalFormat decimalFormat = new DecimalFormat("#0.00"); //two decimal
                        double d = Double.parseDouble(priceStr);
                        priceStr = decimalFormat.format(d);

                        String restaurantAddress = dataSnapshot.child("addressRestaurant").getValue().toString();
                        String deliveryTime = dataSnapshot.child("time").getValue().toString();
                        String customerPhone = dataSnapshot.child("phoneCustomer").getValue().toString();
                        String restaurantPhone = dataSnapshot.child("phoneRestaurant").getValue().toString();

                        //fill the fields
                        tv_Fields.get("address").setText(customerAddress);
                        tv_Fields.get("name").setText(nameCustomer);
                        tv_Fields.get("restaurant").setText(nameRestaurant);
                        tv_Fields.get("phone").setText(customerPhone);
                        tv_Fields.get("dishes").setText(numDishes);
                        tv_Fields.get("hour").setText(deliveryTime);
                        tv_Fields.get("price").setText(priceStr+"€");

                        ride = new Ride(orderID, customerAddress, restaurantAddress,
                                nameCustomer, nameRestaurant, priceStr,
                                numDishes, customerPhone, restaurantPhone,
                                deliveryTime, customerID, restaurantID);

                        //lock the rider
                        isRunning = true;

                        //set the visibility
                        show_view();
                    }

                    //delivering field instead can be changed only if the rider is busy
                    delivering = (Boolean) dataSnapshot.child("delivering").getValue();
                    //set the button and status text based on the rider status
                    String buttonText;
                    String statusText;
                    int textColor;
                    if(delivering){
                        buttonText = getString(R.string.maps_button_order_delivered);
                        statusText = getString(R.string.delivering);
                        textColor = getResources().getColor(R.color.colorPrimary);
                    }
                    else{
                        buttonText = getString(R.string.maps_button_to_restaurant);
                        statusText = getString(R.string.not_delivering);
                        textColor = Color.RED;
                    }
                    status_button.setText(buttonText);
                    tv_Fields.get("status").setText(statusText);
                    tv_Fields.get("status").setTextColor(textColor);

                }
            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot dataSnapshot) {
                //the order can be removed after its completion
                ride = null;
                isRunning = false;
                show_empty_view();
            }

            @Override
            public void onChildMoved(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {
                //an order cannot be moved
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });

    }


    private void terminateRide(String deliveredHour){

        //save the completed ride into the history
        DatabaseReference historyReference = FirebaseDatabase.getInstance().getReference("deliveryman/"+currentUserID+"/history").push();
        historyReference.child("orderID").setValue(ride.getOrderID());
        historyReference.child("addressRestaurant").setValue(ride.getAddressRestaurant());
        historyReference.child("nameRestaurant").setValue(ride.getNameRestaurant());
        historyReference.child("totalPrice").setValue(ride.getTotalPrice());
        historyReference.child("numberOfDishes").setValue(ride.getNumberOfDishes());
        historyReference.child("expectedTime").setValue(ride.getTime());
        historyReference.child("deliveredTime").setValue(deliveredHour);

        //remove the ride
        DatabaseReference reservationReference = FirebaseDatabase.getInstance().getReference("deliveryman/"+currentUserID);
        reservationReference.child("reservations/"+reservationKey).removeValue();

        //set this ride to free
        DatabaseReference deliverymanReference = FirebaseDatabase.getInstance().getReference("deliveryman/"+currentUserID);
        deliverymanReference.child("Busy").setValue(false);
        isRunning = false;

        sendNotificationToRestaurant();

        FirebaseDatabase.getInstance().getReference("restaurants/" + ride.getRestaurantID()
                                                           + "/reservations/" + ride.getOrderID()
                                                           + "/status/it/").setValue("Consegnato");
        FirebaseDatabase.getInstance().getReference("restaurants/" + ride.getRestaurantID()
                                                            + "/reservations/" + ride.getOrderID()
                                                            + "/status/en/").setValue("Delivered");

    }


    private class OnClickRideStatus implements View.OnClickListener {

        @Override
        public void onClick(View v) {
            String title,
                    message;
            //towards restaurant
            if(!delivering){
                title = getString(R.string.go_to_customer);
                message = getString(R.string.dialog_go_to_customer);
            }
            else{
                //toward customer
                title = getString(R.string.maps_button_order_delivered);
                message = getString(R.string.dialog_message_order_completed);
            }

            new AlertDialog.Builder(v.getContext())
                    .setTitle(title)
                    .setMessage(message)

                    // Specifying a listener allows you to take an action before dismissing the dialog.
                    // The dialog is automatically dismissed when a dialog button is clicked.
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            // If the rider arrives to the restaurant
                            if(!delivering)
                            {
                                delivering = true;
                                status_button.setText(getString(R.string.maps_button_order_delivered));
                                //update the delivery status on FireBase
                                DatabaseReference reference = FirebaseDatabase.getInstance().getReference("deliveryman/"+currentUserID+"/reservations/"
                                        +reservationKey);
                                reference.child("delivering").setValue(true);
                                sendNotificationToCustomer();
                            }
                            else{
                                //here the order is completed
                                myToast.setText(R.string.message_order_completed);
                                myToast.show();

                                //retrieve actual time and terminate the order
                                Date currentDate = Calendar.getInstance().getTime();
                                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
                                String currentTime = sdf.format(currentDate);
                                terminateRide(currentTime);
                            }


                        }
                    })

                    // A null listener allows the button to dismiss the dialog and take no further action.
                    .setNegativeButton(android.R.string.no, null)
                    .show();
        }
    }


    private void sendNotificationToCustomer() {
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                int SDK_INT = android.os.Build.VERSION.SDK_INT;
                if (SDK_INT > 8) {
                    StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
                            .permitAll().build();
                    StrictMode.setThreadPolicy(policy);
                    String send_email;

                    //This is a Simple Logic to Send Notification different Device Programmatically....
                    send_email= ride.getCustomerID();

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

                                + "\"data\": {\"Order\": \"PolEATo\"},"
                                + "\"contents\": {\"it\": \"Il fattorino ha lasciato il ristorante\"}"
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
    }

    private void sendNotificationToRestaurant() {
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                int SDK_INT = android.os.Build.VERSION.SDK_INT;
                if (SDK_INT > 8) {
                    StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
                            .permitAll().build();
                    StrictMode.setThreadPolicy(policy);
                    String send_email;

                    //This is a Simple Logic to Send Notification different Device Programmatically....
                    send_email= ride.getRestaurantID();

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

                                + "\"data\": {\"Order\": \"PolEATo\"},"
                                + "\"contents\": {\"it\": \"Ordine " + ride.getOrderID() + " consegnato!\"}"
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
    }
}
