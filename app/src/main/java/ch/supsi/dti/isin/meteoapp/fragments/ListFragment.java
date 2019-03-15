package ch.supsi.dti.isin.meteoapp.fragments;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.toolbox.Volley;

import java.util.List;

import ch.supsi.dti.isin.meteoapp.R;
import ch.supsi.dti.isin.meteoapp.activities.DetailActivity;
import ch.supsi.dti.isin.meteoapp.activities.MainActivity;
import ch.supsi.dti.isin.meteoapp.db.DatabaseHelper;
import ch.supsi.dti.isin.meteoapp.db.DatabaseSchema;
import ch.supsi.dti.isin.meteoapp.model.LocationsHolder;
import ch.supsi.dti.isin.meteoapp.model.apirequest.Coordinate;
import ch.supsi.dti.isin.meteoapp.model.apirequest.CurrentWeather;
import ch.supsi.dti.isin.meteoapp.model.apirequest.Location;
import ch.supsi.dti.isin.meteoapp.model.apirequest.WeatherHttpClient;
import io.nlopez.smartlocation.OnLocationUpdatedListener;
import io.nlopez.smartlocation.SmartLocation;
import io.nlopez.smartlocation.location.config.LocationAccuracy;
import io.nlopez.smartlocation.location.config.LocationParams;

import static android.content.ContentValues.TAG;

public class ListFragment extends Fragment implements Updateable{
    private static final int REQ_CODE = 648;
    private RecyclerView mLocationRecyclerView; //Lista contenente le città
    private LocationAdapter mAdapter; //Responsabile di creare i ViewHolders necessari
    private SQLiteDatabase mDatabase;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDatabase = new DatabaseHelper(this.getContext()).getWritableDatabase();

        setHasOptionsMenu(true); //Informa al fragment che c'è un menu

        if (ContextCompat.checkSelfPermission(this.getContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this.getActivity(), new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_CODE);
        } else {
            // ho già i permessi
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case REQ_CODE: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startLocationListener(this.getContext());
                }
                return;
            }
        }
    }

    private void startLocationListener(final Context context) {

        LocationParams.Builder builder = new LocationParams.Builder()
                .setAccuracy(LocationAccuracy.HIGH)
                .setDistance(0)
                .setInterval(5000); // 5 sec

        SmartLocation.with(context)
                .location()
                .continuous()
                .config(builder.build())
                .start(
                        new OnLocationUpdatedListener() {
                            @Override
                            public void onLocationUpdated(android.location.Location location) {
                                Log.i(TAG, "Location" + location);

                                WeatherHttpClient weatherHttpClient = new WeatherHttpClient(ListFragment.this);
                                weatherHttpClient.getCurrentWeatherDataByLatLon(location.getLatitude(), location.getLongitude());
                            }
                        });

    }

    public void update(CurrentWeather currentWeather){
        if(!mAdapter.mLocations.get(0).getName().equals(currentWeather.getName())){
            mAdapter.mLocations.get(0).setName(currentWeather.getName());
            mAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_list, container, false);
        mLocationRecyclerView = view.findViewById(R.id.recycler_view); //Seleziono la zona in cui voglio inserire il RecyclerView
        mLocationRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));

        List<Location> locations = LocationsHolder.get(getActivity()).getLocations();
        mAdapter = new LocationAdapter(locations);
        mLocationRecyclerView.setAdapter(mAdapter);

        return view;
    }

    /************************** MENU **************************
     Menu, richiamato automaticamente
     ***********************************************************/
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.fragment_list, menu);
    }

    // Azione di riposta al menu
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_add:
                final EditText editText = new EditText(getContext());

                new AlertDialog.Builder(getContext())
                        .setTitle("Add Location")
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                String locationName = editText.getText().toString();
                                mAdapter.addLocationToList(new Location(locationName));
                                ContentValues values = new ContentValues();
                                values.put(DatabaseSchema.Table.Cols.LOCATION, locationName);
                                mDatabase.insert("meteoapp", null, values);
                            }
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .setView(editText)
                        .show();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /************************** HOLDER **************************
     Holder crea la lista con le view
     ************************************************************/
    private class LocationHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        private TextView mNameTextView;
        private Location mLocation;

        public LocationHolder(LayoutInflater inflater, ViewGroup parent) {
            super(inflater.inflate(R.layout.list_item, parent, false)); //Seleziono il layout
            itemView.setOnClickListener(this);
            mNameTextView = itemView.findViewById(R.id.name);
        }

        @Override
        public void onClick(View view) {
            Intent intent = DetailActivity.newIntent(getActivity(), mLocation);
            startActivity(intent);
        }

        public void bind(Location location) {
            mLocation = location;
            mNameTextView.setText(mLocation.getName());
        }
    }

    /************************** ADAPTER **************************
     Adapter crea gli elementi (view) della lista
     ************************************************************/
    private class LocationAdapter extends RecyclerView.Adapter<LocationHolder> {
        private List<Location> mLocations;

        public LocationAdapter(List<Location> locations) {
            mLocations = locations;
        }

        @Override
        public LocationHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LayoutInflater layoutInflater = LayoutInflater.from(getActivity());
            return new LocationHolder(layoutInflater, parent);
        }

        @Override
        public void onBindViewHolder(LocationHolder holder, int position) {
            Location location = mLocations.get(position);
            holder.bind(location);
        }

        @Override
        public int getItemCount() {
            return mLocations.size();
        }

        public void addLocationToList(Location l){
            mLocations.add(l);
            mAdapter.notifyDataSetChanged();
        }
    }
}
