package com.mad.poleato;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class RiderListAdapter extends ArrayAdapter<Rider>
{
    private List<Rider> ridersList;
    private final Context mContext;
    private final LayoutInflater inflater;

    public RiderListAdapter(Context context, int resourceId)
    {
        super(context, resourceId);

        mContext = context;
        ridersList = new ArrayList<>();
        inflater = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent)
    {
        final ViewHolder holder;
        if (convertView == null)
        {
            convertView = inflater.inflate(R.layout.rider_layout, null);
            holder = new ViewHolder();
            holder.riderID_tv= (TextView) convertView.findViewById(R.id.rider_tv);
            holder.distance_tv= (TextView) convertView.findViewById(R.id.distance_tv);
            holder.selectButton= (Button) convertView.findViewById(R.id.myButton);
            convertView.setTag(holder);
        }
        else
        {
            holder = (ViewHolder)convertView.getTag();
        }

        Rider item = getItem(position);
        if (item != null)
        {
            // Set up the views.
            holder.riderID_tv.setText(ridersList.get(position).getId());

            /*
             * setup the distance format:
             *  e.g 120.4 m or 1.3 Km
             */
            String distance_text;
            if(ridersList.get(position).getDistance() < 1)
                // distance consists in meters
                distance_text= new DecimalFormat("##.##").format(1000*ridersList.get(position).getDistance()) + " m";
            else
                // distance consists in kilometers
                distance_text= new DecimalFormat("##.##").format(ridersList.get(position).getDistance()) + " Km";
            holder.distance_tv.setText(distance_text);

            /*
             * Listener of button 'select rider'
             */
            holder.selectButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    String riderID= holder.riderID_tv.getText().toString();
                    //TODO return riderID to reservation expandable list adapter
                    ((Activity) mContext).finish();
                }
            });

        }

        return convertView;
    }

    @Override
    public int getCount()
    {
        return ridersList.size();
    }

    @Override
    public Rider getItem(int position)
    {
        return ridersList.get(position);
    }

    public void addRider(Rider rider){
        ridersList.add(rider);
        Collections.sort(ridersList, Rider.distanceComparator);
    }

    @Override
    public void notifyDataSetChanged() {
        super.notifyDataSetChanged();
    }

    public class ViewHolder
    {
        TextView riderID_tv;
        TextView distance_tv;
        Button selectButton;
    }
}
