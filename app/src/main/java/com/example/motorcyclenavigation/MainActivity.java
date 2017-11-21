package com.example.motorcyclenavigation;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.icu.util.Output;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOError;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends Activity {
    public static final String DEVICE_ADDRESS = "deviceAddress";
    private Button btnMap, btnBluetooth, btnSetting, btnAboutus;

    private final int CODE = 1;
    private int tmp = -1, i = 0;
    BluetoothAdapter myBluetoothAdapter;
    BluetoothSocket myBluetoothSocket;
    BluetoothDevice myBluetoothDevice;
    private UUID mUUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
    String deviceName = "DuhWell", deviceAddress = "";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_layout);

        btnMap = (Button)findViewById(R.id.btnMap);
        btnBluetooth = (Button)findViewById(R.id.btnBluetooth);
        btnSetting = (Button)findViewById(R.id.btnSetting);
        btnAboutus = (Button)findViewById(R.id.btnAboutus);

        myBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if(myBluetoothAdapter == null)  finish();
        if(!myBluetoothAdapter.isEnabled()){
            Intent en_BT = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(en_BT, CODE);
        }
        Set<BluetoothDevice> pairedDevices = myBluetoothAdapter.getBondedDevices();
        // There are paired devices. Get the name and address of each paired device.
        for (BluetoothDevice device : pairedDevices){
            char [] d = deviceName.toCharArray();
            for (char c : device.getName().toCharArray()) {
                if(c == d[i]){
                    i++;
                    if(i==7){
                        deviceAddress = device.getAddress().toString();
                        break;
                    }
                }
            }
            i=0;
        }

        btnMap.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, MapsActivity.class);
                intent.putExtra(DEVICE_ADDRESS, deviceAddress);
                startActivity(intent);
            }
        });

        btnBluetooth.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, BluetoothActivity.class);
                try{
                    myBluetoothDevice = myBluetoothAdapter.getRemoteDevice(deviceAddress);
                    myBluetoothSocket = myBluetoothDevice.createRfcommSocketToServiceRecord(mUUID);
//                    myBluetoothSocket.connect();
//                    OutputStream myOutputStream = myBluetoothSocket.getOutputStream();
//                    if(myBluetoothSocket.isConnected()){
//                        Toast.makeText(MainActivity.this, "連線成功", Toast.LENGTH_LONG).show();
//                        myOutputStream.write("Success".getBytes());
//                        myOutputStream.write("Success01".getBytes());
//                    } else {
//                        Toast.makeText(MainActivity.this, "連線失敗", Toast.LENGTH_LONG).show();
//                    }
                }catch(Exception io){}
//                try{
//                    myBluetoothSocket.close();
//                }catch(IOException io){}
//                startActivity(intent);
            }
        });

        btnSetting.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, SettingActivity.class);
                startActivity(intent);
            }
        });

        btnAboutus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, AboutusActivity.class);
                startActivity(intent);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data){
        if(requestCode == CODE){
            if(resultCode == RESULT_OK){
                tmp = RESULT_OK;
            }
            else{
                finish();
            }
        }
    }
}
