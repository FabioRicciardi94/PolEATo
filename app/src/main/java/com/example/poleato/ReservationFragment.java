package com.example.poleato;

import android.graphics.Point;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ExpandableListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

//TODO status persistency

public class ReservationFragment extends Fragment {
    private ExpandableListView lv;
    private ReservationExpandableListAdapter listAdapter;
    private List<Reservation> reservations;
    private HashMap<String,List<Dish>> listHash = new HashMap<>();
    private Display display;
    private Point size;
    private int width;
    private Reservation c1;
    private Reservation c2;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Calculate position of ExpandableListView indicator.
        display = getActivity().getWindowManager().getDefaultDisplay();
        size = new Point();
        display.getSize(size);
        width = size.x;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.reservation_frag_layout,container,false);
        initData();


        lv = view.findViewById(R.id.reservationslv);

        //fix expandablelistview arrow position
        lv.setIndicatorBounds(width-GetDipsFromPixel(35), width-GetDipsFromPixel(5));

        listAdapter = new ReservationExpandableListAdapter(getActivity(),reservations,listHash);

        lv.setAdapter(listAdapter);
        return view;
    }

    public int GetDipsFromPixel(float pixels){
        // Get the screen's density scale
        final float scale = getResources().getDisplayMetrics().density;
        // Convert the dps to pixels, based on density scale
        return (int) (pixels * scale + 0.5f);
    }

    private void initData(){

        reservations = new ArrayList<>();
        listHash = new HashMap<>();

        c1 = new Reservation("A100","Fabio", "Ricciardi", "Corso duca degli abruzzi, 24","04/04/2019", "20.30", getContext());
        c2 = new Reservation("A101","Michelangelo", "Moncada", "Via rossana, 26", "04/04/2019", "19.20", getContext());

        reservations.add(c1);
        reservations.add(c2);

        Dish d1_c1= new Dish("Pasta carbonara", 1, "no cipolla");
        Dish d2_c1= new Dish("Pizza margherita", 2, "pizze tagliate");
        List<Dish> list1= new ArrayList<>();
        list1.add(d1_c1);
        list1.add(d2_c1);
        List<Dish> list2 = new ArrayList<>();
        list2.add(d1_c1);

        listHash.put(c1.getOrder_id(),list1);
        listHash.put(c2.getOrder_id(),list2);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        ArrayList<String> statusPersistence= new ArrayList<>();
        for(int i=0; i<listAdapter.getGroupCount(); i++){
            View v= listAdapter.getGroupView(i, false, null, lv);
            TextView status= v.findViewById(R.id.tvStatusField);
            statusPersistence.add(i, status.getText().toString());
        }
        outState.putStringArrayList("Status_Persistence", statusPersistence);
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        ArrayList<String> statusPersistence;
        if(savedInstanceState!=null) {
            statusPersistence = savedInstanceState.getStringArrayList("Status_Persistence");
            if (statusPersistence != null)
                if (statusPersistence.size() > 0) {
                    for (int i = 0; i < listAdapter.getGroupCount(); i++) {
                        reservations.get(i).setStat(statusPersistence.get(i), getContext());
                    }
                }
        }
    }
}