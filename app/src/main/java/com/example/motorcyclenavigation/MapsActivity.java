package com.example.motorcyclenavigation;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.maps.android.PolyUtil;
import com.google.maps.android.SphericalUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {
    private final int REQUEST_ACCESS_FINE_LOCATION = 0;
    private final int REQUEST_CHECK_SETTINGS = 1;
    private final int MIN_UPDATE_GPS_DISTANCE = 1; // 最小更新GPS距離(公尺)
    private final int MIN_UPDATE_PATH_DISTANCE = 8; // 最小更新GPS距離(公尺)
    private final int UPDATE_GPS_INTERVAL = 1000; // 更新GPS間距(毫秒)
    private final int UPDATE_GPS_FASTEST_INTERVAL = 500; // 最快更新GPS間距(毫秒)

    private GoogleMap mMap;
    private LocationRequest mLocationRequest;
    private FusedLocationProviderClient mFusedLocationClient; // 定期更新GPS Provider
    private LocationCallback mLocationCallback; // 取回GPS的回調函式
    private LinearLayout locationContainer; // 途經地點文字框容器
    private Button btnNavigate; // 導航按鈕
    private ImageButton btnAddLocation; // 加入途經地點按鈕
    private ArrayList<EditText> edtWayPoints = new ArrayList<>(); //途經地點
    private EditText edtEnd; // 目的地
    // TODO:測試完要刪掉
    private TextView txtInstruction; // 測試用TextView

    private EditText onFocusEditText;
    private LatLng prevLocation; // 上回取回的經緯度
    private LatLng endLocation; // 目標地點
    private boolean request = false; // 是否送出規劃要求
    private List<LatLng> path; // 規劃路徑點
    private ArrayList<Instruction> instructions = new ArrayList<>(); // 需要轉彎處

    BluetoothSocket myBluetoothSocket;
    BluetoothDevice myBluetoothDevice;
    BluetoothAdapter myBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    private UUID mUUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
    private String deviceAddress = "";
    OutputStream myOutputStream;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        locationContainer = (LinearLayout) findViewById(R.id.locationContainer);
        btnNavigate = (Button)findViewById(R.id.btnNavigate);
        btnAddLocation = (ImageButton)findViewById(R.id.imageButtonAddLocation);
        edtEnd = (EditText)findViewById(R.id.edtEnd);
        edtEnd.setOnFocusChangeListener(onEditTextFocus);
        txtInstruction = (TextView)findViewById(R.id.txtInstruction);

        edtWayPoints.add(edtEnd);

        onFocusEditText = edtEnd;

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // 設定GPS更新條件
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(UPDATE_GPS_INTERVAL); // 設定更新間距
        mLocationRequest.setFastestInterval(UPDATE_GPS_FASTEST_INTERVAL); // 設定最快更新間距
        mLocationRequest.setPriority(LocationRequest.PRIORITY_LOW_POWER); // 設定GPS精確度

        deviceAddress = getIntent().getStringExtra(MainActivity.DEVICE_ADDRESS);
        try{
            myBluetoothDevice = myBluetoothAdapter.getRemoteDevice(deviceAddress);
            myBluetoothSocket = myBluetoothDevice.createRfcommSocketToServiceRecord(mUUID);
            myBluetoothSocket.connect();
            if(!myBluetoothSocket.isConnected()){
                Toast.makeText(this, "你沒連到藍芽喔~", Toast.LENGTH_LONG).show();
            }
            else{
                Toast.makeText(this, "連線成功", Toast.LENGTH_LONG).show();
            }
        }catch (Exception e){
            e.printStackTrace();
            finish();
        }

        // 處理定期(UPDATE_GPS_INTERVAL)回傳的GPS
        mLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                LatLng location = new LatLng(locationResult.getLastLocation().getLatitude(), locationResult.getLastLocation().getLongitude());
                StringBuilder user_location = new StringBuilder(location.latitude + "," + location.longitude);
                double move_distance = prevLocation != null ? SphericalUtil.computeDistanceBetween(location, prevLocation) : Integer.MAX_VALUE;

                Log.d("user_location", user_location.toString());
                Log.d("move_distance", String.valueOf(move_distance));

                // 如果第一次取回GPS、沒有送出要求、大於最小更新GPS距離
                // 則送出規畫路徑要求
                if (move_distance > MIN_UPDATE_GPS_DISTANCE && !request) {

                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(location, 18));

                    //TODO:如何更精確
                    // 判斷使用者是否在規劃路徑上
                    if (PolyUtil.isLocationOnPath(location, path, true, MIN_UPDATE_PATH_DISTANCE)) {
                        int i = 0;
                        double distance = 0;
                        String instruction = "";

                        path.remove(0);
                        path.add(0, location);

                        if (SphericalUtil.computeDistanceBetween(location, path.get(1)) < MIN_UPDATE_PATH_DISTANCE) {
                            if (instructions.size() != 0 && instructions.get(0).getTurnLocation().equals(path.get(1))) {
                                instructions.remove(0);
                            }

                            path.remove(1);
                            if (path.size() == 1) {
                                mFusedLocationClient.removeLocationUpdates(mLocationCallback);
                            }
                        }

                        while ((i + 1) < path.size() && (instructions.size() == 0 || !instructions.get(0).getTurnLocation().equals(path.get(i)))) {
                            i++;
                            distance += SphericalUtil.computeDistanceBetween(path.get(i - 1), path.get(i));
                        }

                        instruction = instructions.size() == 0 ? "arrive" : instructions.get(0).getInstruction();
                        txtInstruction.setText(String.format("在%d公尺%s", (int)distance, instruction));
                        sendMessage(instruction, (int)distance);

                        drawPolyLine(mMap, path);
                    } else {
                        ArrayList<String> aryWayPoints = new ArrayList<>();
                        aryWayPoints.add(location.latitude + "," + location.longitude);

                        for (int i = 0; i < edtWayPoints.size(); i++) {
                            aryWayPoints.add(edtWayPoints.get(i).getText().toString());
                        }

                        requestDirection(aryWayPoints);
                    }

                }

                // 紀錄這次位置
                prevLocation = location;
            }
        };

        btnNavigate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String[] command = edtEnd.getText().toString().split(" ");

                if (command[0].equals("test")) {
                    sendMessage(command[1], Integer.valueOf(command[2]));
                } else {
                    if (checkPermission(Manifest.permission.ACCESS_FINE_LOCATION, REQUEST_ACCESS_FINE_LOCATION)) {
                        mFusedLocationClient.getLastLocation()
                                .addOnSuccessListener(MapsActivity.this, new OnSuccessListener<Location>() {
                                    @Override
                                    public void onSuccess(Location location) {
                                        // Got last known location. In some rare situations this can be null.
                                        if (location != null) {
                                            ArrayList<String> aryWayPoints = new ArrayList<>();
                                            aryWayPoints.add(location.getLatitude() + "," + location.getLongitude());
                                            
                                            for (int i = 0; i < edtWayPoints.size(); i++) {
                                                aryWayPoints.add(edtWayPoints.get(i).getText().toString());
                                            }

                                            requestDirection(aryWayPoints);
                                        }
                                    }
                                });
                    }
                }
            }
        });

        btnNavigate.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                return false;
            }
        });

        btnAddLocation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EditText location = new EditText(MapsActivity.this);
                location.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
                location.setText("112台北市北投區明德路365號");
                location.setTextSize(12);
                location.setOnFocusChangeListener(onEditTextFocus);
                locationContainer.addView(location);
                edtWayPoints.add(location);
            }
        });
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // 判斷使用者是否已允許存取位置
        if (checkPermission(Manifest.permission.ACCESS_FINE_LOCATION, REQUEST_ACCESS_FINE_LOCATION)) {
            mMap.setMyLocationEnabled(true); //  啟用我的位置按鈕
        }

        mMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
            @Override
            public void onMapClick(LatLng latLng) {
                onFocusEditText.setText(latLng.latitude + "," + latLng.longitude);
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case REQUEST_ACCESS_FINE_LOCATION:
                // 如果拒絕授權grantResults會是空的
                if (grantResults.length > 0 &&
                        grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                                PackageManager.PERMISSION_GRANTED) {
                        mMap.setMyLocationEnabled(true);
                } else {
                    Toast.makeText(this, getResources().getText(R.string.permission_denied), Toast.LENGTH_LONG).show();
                }

                break;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mFusedLocationClient.removeLocationUpdates(mLocationCallback);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }

    // 檢查權限
    private boolean checkPermission(String permission, int key) {
        // 判斷使用者是否已允許權限
        if (ContextCompat.checkSelfPermission(this, permission) ==
                PackageManager.PERMISSION_GRANTED) {
            return true;
        } else {
            // 要求權限
            ActivityCompat.requestPermissions(this,
                    new String[]{permission},
                    key);
            return false;
        }
    }

    // 送出規劃要求
    private void requestDirection(ArrayList<String> aryWayPoints) {
        // 送出路線規畫要求
        try {
            String strWayPoints = "";

            for (int i = 1; i < aryWayPoints.size() - 2; i++) {
                strWayPoints += "via:" + aryWayPoints.get(i) + "|";
            }
            strWayPoints += "via:" + aryWayPoints.get(aryWayPoints.size() - 2);

            URI uri = new URI(
                "https",
                "maps.googleapis.com",
                "/maps/api/directions/json",
                String.format("origin=%s&destination=%s&waypoints=%s&key=%s&avoid=highways",
                        aryWayPoints.get(0), aryWayPoints.get(aryWayPoints.size() - 1), strWayPoints, getResources().getString(R.string.google_maps_direction_key)),
                null
            );
            // 使要求在背景執行
            new Request().execute(new URL(uri.toASCIIString()));

            request = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 處理回傳資料
    private void handleResponse(JSONObject response) {
        try {
            String status = response.getString("status"); // 規劃狀態碼
            String message = ""; // 錯誤訊息

            Log.d("status", status);
            switch (status) {
                case "OK":
                    JSONObject routes = response.getJSONArray("routes").getJSONObject(0);
                    JSONObject legs = routes.getJSONArray("legs").getJSONObject(0);
                    JSONArray steps = legs.getJSONArray("steps");
                    String str_points = routes.getJSONObject("overview_polyline").getString("points");
                    int j = 0, index = 0;
                    endLocation = new LatLng(legs.getJSONObject("end_location").getDouble("lat"),
                            legs.getJSONObject("end_location").getDouble("lng"));
                    this.path = PolyUtil.decode(str_points);

                    drawPolyLine(mMap, path);
                    Log.d("endLocation", endLocation.toString());

                    for (int i = 0; i < steps.length(); i++) {
                        if (!steps.getJSONObject(i).isNull("maneuver")) {
                            String maneuver = steps.getJSONObject(i).getString("maneuver");
                            LatLng turnLocation = new LatLng(steps.getJSONObject(i).getJSONObject("start_location").getDouble("lat"),
                                    steps.getJSONObject(i).getJSONObject("start_location").getDouble("lng"));
                            instructions.add(new Instruction(maneuver, turnLocation));
                        }
                    }

                    // TODO:可以再想想如何優化
                    for (int i = 0; i < instructions.size(); i++) {
                        double minDistance = Double.MAX_VALUE;
//                        ArrayList<Double> tempDistance = new ArrayList<>();

                        for (int k = index; k < path.size(); k++) {
//                            tempDistance.add(SphericalUtil.computeDistanceBetween(path.get(k), instructions.get(i).getTurnLocation()));
                            double temp = SphericalUtil.computeDistanceBetween(path.get(k), instructions.get(i).getTurnLocation());

                            if (minDistance > temp) {
                                minDistance = temp;
                                index = k;
                            }
                        }

                        instructions.get(i).setTurnLocation(path.get(index));
                    }

                    // 設定初值
                    prevLocation = null;

                    // 確認手機設定符合GPS精確度條件
                    LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                            .addLocationRequest(mLocationRequest);
                    SettingsClient client = LocationServices.getSettingsClient(MapsActivity.this);
                    Task<LocationSettingsResponse> task = client.checkLocationSettings(builder.build());
                    task.addOnSuccessListener(MapsActivity.this, new OnSuccessListener<LocationSettingsResponse>() {
                        @Override
                        public void onSuccess(LocationSettingsResponse locationSettingsResponse) {
                            if (checkPermission(Manifest.permission.ACCESS_FINE_LOCATION, REQUEST_ACCESS_FINE_LOCATION)) {

                                // 要求定期更新GPS
                                mFusedLocationClient.requestLocationUpdates(mLocationRequest,
                                        mLocationCallback,
                                        null /* Looper */);
                            }
                        }
                    });

                    task.addOnFailureListener(MapsActivity.this, new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            int statusCode = ((ApiException) e).getStatusCode();
                            switch (statusCode) {
                                case CommonStatusCodes.RESOLUTION_REQUIRED:
                                    // Location settings are not satisfied, but this can be fixed
                                    // by showing the user a dialog.
                                    try {
                                        // Show the dialog by calling startResolutionForResult(),
                                        // and check the result in onActivityResult().
                                        ResolvableApiException resolvable = (ResolvableApiException) e;
                                        resolvable.startResolutionForResult(MapsActivity.this,
                                                REQUEST_CHECK_SETTINGS);
                                    } catch (IntentSender.SendIntentException sendEx) {
                                        // Ignore the error.
                                    }
                                    break;
                                case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                                    // Location settings are not satisfied. However, we have no way
                                    // to fix the settings so we won't show the dialog.
                                    break;
                            }
                        }
                    });

                    break;
                case "NOT_FOUND": // 找不到地點
                    message += getString(R.string.not_found);
                    break;
                case "ZERO_RESULTS": // 找不到路徑
                    message += getString(R.string.zero_results);
                    break;
                case "INVALID_REQUEST": // 錯誤參數
                    message += getString(R.string.invalid_request);
                    break;
                case "OVER_QUERY_LIMIT": // 送出過多要求
                    message += getString(R.string.over_query_limit);
                    break;
                case "REQUEST_DENIED": // 拒絕規劃服務
                    message += getString(R.string.request_denied);
                    break;
            }

            if (!message.isEmpty()) {
                message += response.isNull("error_message") ? "" : "\nerror_message:" + response.getString("error_message"); // 如果有額外錯誤訊息，則一併顯示

                // 顯示對話框
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage(message)
                        .setCancelable(false)
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        }).show();
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void drawPolyLine(GoogleMap map, List<LatLng> points){
        map.clear();

        if (points.size() > 1) {
            final PolylineOptions line = new PolylineOptions();
            // Instantiates a new Polyline object and adds points to define a rectangle
            for (int i = 0; i < points.size(); i++) {
                line.add(points.get(i));
            }

            map.addPolyline(line);
            map.addMarker(new MarkerOptions().position(endLocation));
        }

    }

    // 傳送轉彎及距離訊息給arduino
    private void sendMessage(String instruction, int distance) {
        try{
            myOutputStream = myBluetoothSocket.getOutputStream();
            //myOutputStream.write("Success".getBytes());
            String dis = String.format("%03d", distance);
            if(instruction.equals("turn-right")) {
                myOutputStream.write("R".getBytes());
                myOutputStream.write(dis.getBytes());
            }
            else if(instruction.equals("turn-left")){
                myOutputStream.write("L".getBytes());
                myOutputStream.write(dis.getBytes());
            }
            else if(instruction.equals("uturn-left")){
                myOutputStream.write("u".getBytes());
                myOutputStream.write(dis.getBytes());
            }
            else if(instruction.equals("uturn-right")){
                myOutputStream.write("U".getBytes());
                myOutputStream.write(dis.getBytes());
            }
            Log.d("Success", "Send Success");
        }catch(IOException io){
            try{
            myOutputStream = myBluetoothSocket.getOutputStream();
            myOutputStream.write("ERROR".getBytes());

        }catch(IOException io2) {}
            Log.d("ERROR", "Send Error");
       }
    }

    private View.OnFocusChangeListener onEditTextFocus = new View.OnFocusChangeListener() {
        @Override
        public void onFocusChange(View v, boolean hasFocus) {
            if (hasFocus) {
                onFocusEditText = (EditText)v;
            }
        }
    };

    private class Request extends AsyncTask<URL, Integer, JSONObject> {

        @Override
        protected JSONObject doInBackground(URL... params) {
            try {
                String output = "";
                int temp;
                request = false;

                Log.d("request_url", params[0].toString());
                URLConnection urlConnection = params[0].openConnection();
                InputStream in = urlConnection.getInputStream();
                while ((temp = in.read()) != -1) {
                    output += (char)temp;
                }
                in.close();
                Log.d("direction_response", output);

                return new JSONObject(output);
            } catch (Exception e) {
                e.printStackTrace();
            }

            return null;
        }

        @Override
        protected void onPostExecute(JSONObject response) {
            handleResponse(response);
        }
    }
}
