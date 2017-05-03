package com.example.rc.samples;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.util.Pair;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import org.apache.commons.io.IOUtils;
import org.json.JSONException;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener {

    private static final int LOCATION_PERMISSION_REQUEST = 0;

    private static final int SMALLEST_DISPLACEMENT = 20;

    private static final int MAX_REFRESH_INTERVAL = 10000;

    private static final int MIN_REFRESH_INTERVAL = 1000;

    private static final String URL = "http://api.openweathermap.org/data/2.5/weather";

    private LocationRequest locationRequest;

    private GoogleApiClient mGoogleApiClient;

    private ProgressDialog loader;

    private EditText cityName;

    private View outBox;
    private Location location;
    private String lastRequestUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        cityName = (EditText) findViewById(R.id.city);
        outBox = findViewById(R.id.outputBox);
        findViewById(R.id.refresh).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                loadAndBind();
            }
        });
        findViewById(R.id.submit).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (cityName.getText().length() == 0) {
                    cityName.setError("To pole jest wymagane!");
                    return;
                }

                generateQueryByCityParameters();
                loadAndBind();
            }
        });

        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
        }
        createLocationRequest();
        loader = ProgressDialog.show(this, "Czekaj...", "", true);
    }

    private void loadAndBind() {
        loader = ProgressDialog.show(this, "Czekaj...", "", true);
        new AsyncTask<Void, Void, Pair<CurrentWeatherModel, String>>() {

            @Override
            protected Pair<CurrentWeatherModel, String> doInBackground(Void... args) {
                return loadData();
            }

            @Override
            protected void onPostExecute(Pair<CurrentWeatherModel, String> result) {
                switch (result.second) {
                    case "OK":
                        bindOutBox(result.first);
                        break;
                    case "CITY_NOT_FOUND":
                        showCityNotFoud();
                        break;
                    case "WRONG_KEY":
                        Toast.makeText(MainActivity.this, "Autoryzacja się nie powiodła...", Toast.LENGTH_LONG).show();
                        break;
                    default:
                        showConnectionProblemsAlert();
                }
                loader.dismiss();
            }
        }.execute();
    }

    private void bindOutBox(CurrentWeatherModel model) {
        outBox.setVisibility(View.VISIBLE);

        ((TextView) findViewById(R.id.placeName)).setText(model.getPlaceName());
        ((TextView) findViewById(R.id.desc)).setText(model.getDesc());

        TextView temp = ((TextView) findViewById(R.id.temp));
        temp.setText(String.format("%d °C", model.getTemperature()));

        if (model.getTemperature() < 8) {
            temp.setTextColor(ContextCompat.getColor(this, R.color.cold));
        } else if (model.getTemperature() < 24) {
            temp.setTextColor(ContextCompat.getColor(this, R.color.normal));
        } else {
            temp.setTextColor(ContextCompat.getColor(this, R.color.hot));
        }

        if (model.getClouds() < 20) {
            ((ImageView) findViewById(R.id.weather)).setImageResource(R.drawable.white_balance_sunny);
        } else if (model.getClouds() < 40) {
            ((ImageView) findViewById(R.id.weather)).setImageResource(R.drawable.weather_partlycloudy);
        } else if (model.getClouds() < 60) {
            ((ImageView) findViewById(R.id.weather)).setImageResource(R.drawable.cloud_outline);
        } else {
            ((ImageView) findViewById(R.id.weather)).setImageResource(R.drawable.weather_lightning_rainy);
        }
    }

    private void showCityNotFoud() {
        new AlertDialog.Builder(this)
                .setTitle("Pusto!")
                .setMessage("Nie znaleziono takiego miasta.")
                .setCancelable(true)
                .setPositiveButton("Rozumiem", null)
                .show();
    }

    private void showConnectionProblemsAlert() {
        new AlertDialog.Builder(this)
                .setTitle("Ojojoj")
                .setMessage("Problem z połączeniem?")
                .setCancelable(true)
                .setNegativeButton("Ustawienia", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
                    }
                })
                .setPositiveButton("Rozumiem", null)
                .show();
    }

    private Pair<CurrentWeatherModel, String> loadData() {
        Pair<CurrentWeatherModel, String> result = new Pair(null, "ERROR");
        InputStream in = null;
        try {
            URL url = new URL(lastRequestUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setReadTimeout(10000);
            conn.setConnectTimeout(15000);
            conn.setRequestMethod("GET");
            conn.setDoInput(true);

            conn.connect();
            switch (conn.getResponseCode()) {
                case HttpURLConnection.HTTP_OK:
                    in = new BufferedInputStream(conn.getInputStream());
                    result = new Pair(CurrentWeatherModel.serialize(IOUtils.toString(in, "UTF-8")), "OK");
                    break;
                case HttpURLConnection.HTTP_NOT_FOUND:
                    result = new Pair(null, "CITY_NOT_FOUND");
                    break;
                case HttpURLConnection.HTTP_UNAUTHORIZED:
                    result = new Pair(null, "WRONG_KEY");
                    break;
            }

        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return result;
    }

    private void generateQueryByCityParameters() {
        lastRequestUrl = URL + "?q=" + cityName.getText() + "&lang=pl&units=metric&appid=" + getString(R.string.API_KEY);
    }

    private void generateQueryByLocationParameters() {
        lastRequestUrl = URL + "?lat=" + location.getLatitude() + "&lon=" + location.getLongitude() + "&lang=pl&units=metric&appid=" + getString(R.string.API_KEY);
    }

    private void createLocationRequest() {
        locationRequest = new LocationRequest();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(MAX_REFRESH_INTERVAL);
        locationRequest.setFastestInterval(MIN_REFRESH_INTERVAL);
        locationRequest.setSmallestDisplacement(SMALLEST_DISPLACEMENT);
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Toast.makeText(this, "Waiting on GPS connection...", Toast.LENGTH_LONG).show();
        startLocationRequest();
    }

    private void startLocationRequest() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_PERMISSION_REQUEST);
            return;
        }

        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, locationRequest, this);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (LOCATION_PERMISSION_REQUEST == requestCode) {
            startLocationRequest();
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
    }

    @Override
    public void onLocationChanged(Location location) {
        this.location = location;
        loader.dismiss();
        generateQueryByLocationParameters();
        loadAndBind();
        Toast.makeText(this, location.getLatitude() + "", Toast.LENGTH_LONG).show();
    }

    protected void onStart() {
        mGoogleApiClient.connect();
        super.onStart();
    }

    protected void onStop() {
        mGoogleApiClient.disconnect();
        super.onStop();
    }
}
