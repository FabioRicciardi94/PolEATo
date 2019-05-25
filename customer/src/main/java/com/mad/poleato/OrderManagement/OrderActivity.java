package com.mad.poleato.OrderManagement;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.support.annotation.NonNull;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.mad.poleato.ConnectionManager;
import com.mad.poleato.FavoritePlates.FavoriteMenuFragment;
import com.mad.poleato.Interface;
import com.mad.poleato.MyDatabaseReference;
import com.mad.poleato.OrderManagement.CartManagement.CartActivity;
import com.mad.poleato.OrderManagement.ReviewManagement.RestaurantReviewsFragment;
import com.mad.poleato.PageAdapter;
import com.mad.poleato.R;
import com.mad.poleato.Classes.Restaurant;
import com.onesignal.OneSignal;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


public class OrderActivity extends AppCompatActivity implements Interface {

    private ViewPager onViewPager;
    private PageAdapter adapter;
    private Order order;
    private DatabaseReference dbReferece;
    private ConnectionManager connectionManager;

    private List<MyDatabaseReference> dbReferenceList;
    int indexReference;

    private String currentUserID;
    private FirebaseAuth mAuth;

    /* *************************
     ********* FRAGMENTS ********
     *************************** */

    private MenuFragment menuFragment;
    private InfoFragment infoFragment;
    private FavoriteMenuFragment favoriteMenuFragment;
    private RestaurantReviewsFragment restaurantReviewsFragment;

    private String localeShort;

    BroadcastReceiver networkReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            if(!connectionManager.haveNetworkConnection(context))
                connectionManager.showDialog(context);
        }
    };
    private void addFragmentToAdapter(Bundle bundle) {
        infoFragment = null;
        menuFragment = null;
        favoriteMenuFragment= null;
        restaurantReviewsFragment = null;

        FragmentManager fm = getSupportFragmentManager();
        adapter = new PageAdapter(fm);
        List<Fragment> fragmentList = fm.getFragments();
        if (fragmentList != null) {
            for (int idx = 0; idx < fragmentList.size(); idx++) {

                if (fragmentList.get(idx) instanceof MenuFragment)
                    menuFragment = (MenuFragment) fragmentList.get(idx);
                if (fragmentList.get(idx) instanceof InfoFragment)
                    infoFragment = (InfoFragment) fragmentList.get(idx);
                if (fragmentList.get(idx) instanceof FavoriteMenuFragment)
                    favoriteMenuFragment = (FavoriteMenuFragment) fragmentList.get(idx);
                if (fragmentList.get(idx) instanceof RestaurantReviewsFragment)
                    restaurantReviewsFragment = (RestaurantReviewsFragment) fragmentList.get(idx);
            }
        }
        if (menuFragment == null) {
            menuFragment = new MenuFragment();
            menuFragment.setArguments(bundle);
        }
        if (infoFragment == null) {
            infoFragment = new InfoFragment();
            infoFragment.setArguments(bundle);
        }
        if(favoriteMenuFragment == null){
            favoriteMenuFragment = new FavoriteMenuFragment();
            favoriteMenuFragment.setArguments(bundle);
        }

        if(restaurantReviewsFragment == null){
            restaurantReviewsFragment = new RestaurantReviewsFragment();
            restaurantReviewsFragment.setArguments(bundle);
        }

        adapter.addFragment(menuFragment, "Menu");

        if(localeShort.equals("en"))
            adapter.addFragment(favoriteMenuFragment, "Favorite");
        else
            adapter.addFragment(favoriteMenuFragment, "Preferiti");
        adapter.addFragment(infoFragment, "Info");

        adapter.addFragment(restaurantReviewsFragment,"Reviews");
        onViewPager.setAdapter(adapter);

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_layout);
        connectionManager = new ConnectionManager();

        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        currentUserID = currentUser.getUid();

// OneSignal is used to send notifications between applications

        OneSignal.startInit(this)
                .inFocusDisplaying(OneSignal.OSInFocusDisplayOption.Notification)
                .unsubscribeWhenNotificationsAreDisabled(true)
                .init();

        OneSignal.setSubscription(true);

        OneSignal.sendTag("User_ID", currentUserID);

        dbReferenceList= new ArrayList<>();

        order = new Order(currentUserID);

        Locale locale= Locale.getDefault();
        localeShort = locale.toString().substring(0, 2);

        //gettin id of restaurant selected by user and name
        Bundle bundle = getIntent().getExtras();


        order.setRestaurantID(bundle.getString("id"));
        dbReferece = FirebaseDatabase.getInstance().getReference("restaurants").child(order.getRestaurantID());
        dbReferenceList.add(new MyDatabaseReference(dbReferece));
        int indexReference= dbReferenceList.size()-1;
        ValueEventListener valueEventListener;

        dbReferenceList.get(indexReference).getReference().addValueEventListener(valueEventListener= new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                String id = dataSnapshot.getKey();
                if (dataSnapshot.hasChild("Name") &&
                        dataSnapshot.hasChild("Type") &&
                        dataSnapshot.child("Type").hasChild("it") &&
                        dataSnapshot.child("Type").hasChild("en") &&
                        dataSnapshot.hasChild("IsActive") &&
                        //dataSnapshot.hasChild("PriceRange") &&
                        dataSnapshot.hasChild("DeliveryCost")
                ) {
                    String name = dataSnapshot.child("Name").getValue().toString();
                    String type = dataSnapshot.child("Type").child(localeShort).getValue().toString();
                    Boolean isOpen = (Boolean) dataSnapshot.child("IsActive").getValue();
                    int priceRange = Integer.parseInt(dataSnapshot.child("PriceRange").getValue().toString());
                    double deliveryCost = Double.parseDouble(dataSnapshot.child("DeliveryCost").getValue().toString().replace(",", "."));

                    Restaurant resObj = new Restaurant(id, "", name, type, isOpen, priceRange, deliveryCost);

                    order.setR(resObj);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
        dbReferenceList.get(indexReference).setValueListener(valueEventListener);

        android.support.v7.widget.Toolbar toolbar = findViewById(R.id.toolbar_order);
        TabLayout tabLayout = findViewById(R.id.tabs);

        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("");
        } else {
            Log.d("Error", "getSupportActionBar is null");
            finish();
        }

        onViewPager = findViewById(R.id.container);
        addFragmentToAdapter(bundle);

        tabLayout.setupWithViewPager(onViewPager);
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(networkReceiver,filter);
    }

    @Override
    protected void onStop() {
        unregisterReceiver(networkReceiver);
        super.onStop();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                /**CONTROLLARE NEL CASO DI PERDITA DI DATI*/
                onBackPressed();
                finish();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu items for use in the action bar
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.cart_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    public void goToCart(MenuItem item) {
        Intent intent = new Intent(this, CartActivity.class);
        intent.putExtra("order", order);

        startActivityForResult(intent, 1);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 1) {
            if (resultCode == Activity.RESULT_OK)
                finish();
            else if (resultCode == Activity.RESULT_CANCELED) {
                order = (Order) data.getExtras().getSerializable("old_order");
            }
        }
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    @Override
    public Order getOrder() {
        return order;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        for (int i=0; i < dbReferenceList.size(); i++)
            dbReferenceList.get(i).removeAllListener();
    }
}
