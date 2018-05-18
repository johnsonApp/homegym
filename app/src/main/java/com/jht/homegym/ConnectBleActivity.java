package com.jht.homegym;

import android.Manifest;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.jht.homegym.ble.MultipleBleService;
import com.jht.homegym.ble.Constants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class ConnectBleActivity extends AppCompatActivity implements View.OnClickListener{

    private static final String TAG = "ConnectBleActivity";

    private ImageView mClosePage;
    private Button mConnect;
    private TextView mConnectLable;

    //Constant
    public static final int SERVICE_BIND = 1;
    public static final int CONNECT_CHANGE = 2;
    public static final int REQUEST_CODE_ACCESS_COARSE_LOCATION = 1;

    //Member fields
    private boolean mIsBind;
    private MultipleBleService mBleService;
    //private CommonAdapter<Map<String, Object>> deviceAdapter;
    private List<Map<String, Object>> mDeviceList;
    private String mConnDeviceName;
    private String mConnDeviceAddress;

    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mBleService = ((MultipleBleService.LocalBinder) service).getService();
            if (mBleService != null) mHandler.sendEmptyMessage(SERVICE_BIND);
            if (mBleService.initialize()) {
                if (mBleService.enableBluetooth(true)) {
                    handleVersionPermission();
                    Toast.makeText(ConnectBleActivity.this, "Bluetooth was opened", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(ConnectBleActivity.this, "not support Bluetooth", Toast.LENGTH_SHORT).show();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBleService = null;
            mIsBind = false;
        }
    };


    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case SERVICE_BIND:
                    setBleServiceListener();
                    break;
                case CONNECT_CHANGE:
                    //deviceAdapter.notifyDataSetChanged();
                    mConnectLable.setText(getString(R.string.dev_conn_number) +
                            mBleService.getConnectDevices().size());
                    Log.i(TAG, "handleMessage: " + mBleService.getConnectDevices().toString());
                    break;
            }
        }
    };

    protected BroadcastReceiver mBleReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Constants.ACTION_BLUETOOTH_DEVICE)) {
                String tmpDevName = intent.getStringExtra("name");
                String tmpDevAddress = intent.getStringExtra("address");
                Log.i(TAG, "name: " + tmpDevName + ", address: " + tmpDevAddress);
                HashMap<String, Object> deviceMap = new HashMap<>();
                deviceMap.put("name", tmpDevName);
                deviceMap.put("address", tmpDevAddress);
                deviceMap.put("isConnect", false);
                mDeviceList.add(deviceMap);
                //mDeviceAdapter.notifyDataSetChanged();
            } else if (intent.getAction().equals(Constants.ACTION_GATT_CONNECTED)) {
                Log.i(TAG, "onReceive: CONNECTED: " + mBleService.getConnectDevices().size());
                mHandler.sendEmptyMessage(CONNECT_CHANGE);
                dismissDialog();
            } else if (intent.getAction().equals(Constants.ACTION_GATT_DISCONNECTED)) {
                Log.i(TAG, "onReceive: DISCONNECTED: " + mBleService.getConnectDevices().size());
                mHandler.sendEmptyMessage(CONNECT_CHANGE);
                dismissDialog();
            } else if (intent.getAction().equals(Constants.ACTION_SCAN_FINISHED)) {
                //btn_scanBle.setEnabled(true);
                Log.i(TAG,"ACTION_SCAN_FINISHED");
                mConnect.setEnabled(true);
                dismissDialog();
                connectDevice();
            }
        }
    };

    private static IntentFilter makeIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Constants.ACTION_BLUETOOTH_DEVICE);
        intentFilter.addAction(Constants.ACTION_GATT_CONNECTED);
        intentFilter.addAction(Constants.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(Constants.ACTION_SCAN_FINISHED);
        return intentFilter;
    }



    @Override
    @SuppressWarnings("unchecked")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connect_ble);
        mClosePage = findViewById(R.id.close_page);
        mClosePage.setOnClickListener(this);
        mConnect = findViewById(R.id.button_connect_ble);
        mConnect.setOnClickListener(this);
        mConnectLable = findViewById(R.id.connect_lable);


        mDeviceList = new ArrayList<Map<String, Object>>();
        registerReceiver(mBleReceiver, makeIntentFilter());
        doBindService();
    }

    @Override
    public void onBackPressed() {
        if (mBleService.isScanning()) {
            mBleService.scanLeDevice(false);
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mBleService.isScanning()) {
            mBleService.scanLeDevice(false);
        }
        doUnBindService();
        unregisterReceiver(mBleReceiver);
    }

    private void doBindService() {
        Intent serviceIntent = new Intent(this, MultipleBleService.class);
        bindService(serviceIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
    }

    private void doUnBindService() {
        if (mIsBind) {
            unbindService(mServiceConnection);
            mBleService = null;
            mIsBind = false;
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()){
            case R.id.close_page:
                finish();
                break;
            case R.id.button_connect_ble:
                if(mBleService.isScanning()){
                    mBleService.scanLeDevice(false);
                }
                setScan(true);
                //connectDevice();
                break;
        }
    }

    private void connectDevice() {
        showDialog(getResources().getString(R.string.connecting));
        int find = 0;
        for (int i = 0; i < mDeviceList.size(); i++) {
            HashMap<String, Object> devMap = (HashMap<String, Object>) mDeviceList.get(i);
            String name = (String)devMap.get("name");
            if (null != name && (name.equals("QN-Scale") || name.equals("CSC Sensor"))) {
                Log.i(TAG,"connectDevice " + devMap.get("address").toString());
                mBleService.connect(devMap.get("address").toString());
                find ++;
            }
        }
        Log.i(TAG,"connectDevice " + find);
        if(0 == find){
            dismissDialog();
            Log.i(TAG,"connectDevice make Toast");
            Toast.makeText(this,"Can not find any device ,please retry",Toast.LENGTH_LONG);
        }
    }
    private void setBleServiceListener() {
        mBleService.setOnConnectListener(new MultipleBleService.OnConnectionStateChangeListener() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    for (int i = 0; i < mDeviceList.size(); i++) {
                        HashMap<String, Object> devMap = (HashMap<String, Object>) mDeviceList.get(i);
                        if (devMap.get("address").toString().equals(gatt.getDevice().getAddress())) {
                            ((HashMap) mDeviceList.get(i)).put("isConnect", false);
                            return;
                        }
                    }
                } else if (newState == BluetoothProfile.STATE_CONNECTING) {

                } else if (newState == BluetoothProfile.STATE_CONNECTED) {
                    for (int i = 0; i < mDeviceList.size(); i++) {
                        HashMap<String, Object> devMap = (HashMap<String, Object>) mDeviceList.get(i);
                        if (devMap.get("address").toString().equals(gatt.getDevice().getAddress())) {
                            ((HashMap) mDeviceList.get(i)).put("isConnect", true);
                            return;
                        }
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTING) {

                }
            }
        });
        mBleService.setOnServicesDiscoveredListener(new MultipleBleService.OnServicesDiscoveredListener() {
            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status){

            }
        });
        mBleService.setOnDataAvailableListener(new MultipleBleService.OnDataAvailableListener() {
            @Override
            public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {

            }

            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {

            }

            @Override
            public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {

            }
        });
    }

    private void handleVersionPermission() {
        if (Build.VERSION.SDK_INT >= 23) {
            Log.i(TAG, "onCreate: checkSelfPermission");

            int checkSelfPermission = ContextCompat.checkSelfPermission(ConnectBleActivity.this,
                    Manifest.permission.ACCESS_COARSE_LOCATION);
            Log.i(TAG, "handleVersionPermission: checkSelfPermission = " + checkSelfPermission);
            if (checkSelfPermission != PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "onCreate: Android 6.0 动态申请权限");

                boolean showRequestPermissionRationale = ActivityCompat.shouldShowRequestPermissionRationale(ConnectBleActivity.this,
                        Manifest.permission.ACCESS_COARSE_LOCATION);
                Log.i(TAG, "handleVersionPermission: showRequestPermissionRationale = " + showRequestPermissionRationale);
                if (showRequestPermissionRationale) {
                    Log.i(TAG, "*********onCreate: shouldShowRequestPermissionRationale**********");
                    Toast.makeText(ConnectBleActivity.this, "只有允许访问位置才能搜索到蓝牙设备", Toast.LENGTH_SHORT).show();
                }
                ActivityCompat.requestPermissions(ConnectBleActivity.this,
                        new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                        REQUEST_CODE_ACCESS_COARSE_LOCATION);
            } else {
                Log.i(TAG, "handleVersionPermission: scanning...");
                //showDialog(getResources().getString(R.string.scanning));
                setScan(true);
                //mBleService.scanLeDevice(true);
            }
        } else {
            Log.i(TAG, "handleVersionPermission: scanning...");
            //showDialog(getResources().getString(R.string.scanning));
            setScan(true);
            //mBleService.scanLeDevice(true);
        }
    }

    private void setScan(boolean scan){
        showDialog(getResources().getString(R.string.scanning));
        mBleService.scanLeDevice(scan);
        mConnect.setEnabled(!scan);
    }

    private ProgressDialog mProgressDialog;

    private void showDialog(String message) {
        mProgressDialog = new ProgressDialog(this);
        mProgressDialog.setProgressStyle(mProgressDialog.STYLE_SPINNER);
        mProgressDialog.setCanceledOnTouchOutside(false);
        mProgressDialog.setMessage(message);
        mProgressDialog.show();
    }

    private void dismissDialog() {
        if (mProgressDialog == null) return;
        mProgressDialog.dismiss();
        mProgressDialog = null;
    }
}
