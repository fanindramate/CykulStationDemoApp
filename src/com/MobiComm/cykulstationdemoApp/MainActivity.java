/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.MobiComm.cykulstationdemoApp;

import java.text.DateFormat;
import java.util.Date;
import java.util.LinkedList;
import com.MobiComm.cykulstationdemoApp.gui.CheckButton;
import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.mtp.MtpConstants;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.lang.String;

public class MainActivity extends Activity implements RadioGroup.OnCheckedChangeListener {
    private static final int REQUEST_SELECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    private static final int REQUEST_EDIT_SETTINGS = 3;
    private static final int REQUEST_ENABLE_LOCATION = 4;

    final private static int REQUEST_CODE_ASK_PERMISSIONS = 123;

    private static final int ERL_PROFILE_READY = 10;
    public static final String TAG = "lbs_tag";
    private static final int ERL_PROFILE_CONNECTED = 20;
    private static final int ERL_PROFILE_DISCONNECTED = 21;
    private static final int STATE_OFF = 10;
    private static final int SETTINGS_ACTIVITY = 100;

    TextView mRemoteRssiVal;
    RadioGroup mRg;
    private int mState = ERL_PROFILE_DISCONNECTED;
    private eRLBikeLockService mService = null;
    private BluetoothDevice mDevice = null;
    private BluetoothAdapter mBtAdapter = null;
    private Button btnConnectLock;
    private CheckButton cbtnSlide, cbtnButton;
    private ProgressDialog barProgressDialog;

    private static final int CONNECT_NONE = 1;
    private static final int CONNECT_LOCK = 2;

    private int mConnectTo = CONNECT_NONE;
    private Boolean mWaitingWriteChar;
    private LinkedList<byte[]> mWriteQueue = new LinkedList<byte[]>();

    private String mPassKey;
    private int mPassKeyInt;
    private int mNumberOfPasskeys;
    private int mOTPasskeyNr;
    private String mPasskeyType;
    private String[] mOTPKeyparts;
   // private Switch sb;
    private SeekBar mSeekbar;
    private TextView liverLockText;
    // private GoogleApiClient client;
    byte[] txValue;
    private int lockState;


    private Boolean handleConnectClick(int connectBtn) {

        // show enable BT box if not already
        if (!mBtAdapter.isEnabled()) {
            Log.i(TAG, "onClick - BT not enabled yet");
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
            return true;
        }
      //  53456 66235
        if (connectBtn == CONNECT_LOCK) {
            if (btnConnectLock.getText().equals("Disconnect")) {
                if (mDevice != null) {
                    mService.disconnect();
                }
            } else {
                //Connect button pressed, open DeviceListActivity class, with popup windows that scan for devices
                mConnectTo = CONNECT_LOCK; // not actually connected yet
                Intent newIntent = new Intent(MainActivity.this, DeviceListActivity.class);
                startActivityForResult(newIntent, REQUEST_SELECT_DEVICE);
            }
        }

        return true;
    }

    @TargetApi(Build.VERSION_CODES.M)
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        mBtAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBtAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        btnConnectLock = (Button) findViewById(R.id.btn_connect_lock);
//        sb = (Switch)findViewById(R.id.switchButton);
//        sb.setTextOn("<< Slide to\n Lock");
//        sb.setTextOff("Slide to\nUnlock >>");



        // PUT YOUR API TEST KEY HERE !!

        mWaitingWriteChar = false;


        service_init();

        if (handleConnectClick(CONNECT_LOCK) == true) {
        }
        //
        // wfr Do the lock/unlock
        //
        liverLockText = (TextView)findViewById(R.id.liverText);
//        if(txValue[0]== 1){
//            liverLockText.setVisibility(View.GONE);
//        }else {
//            liverLockText.setVisibility(View.VISIBLE);
//        }
        SharedPreferences sharedPreferences = getSharedPreferences("lockstate",Context.MODE_PRIVATE) ;
        lockState = sharedPreferences.getInt("lockstate",0);

        mSeekbar = (SeekBar) findViewById(R.id.seekbar);
        mSeekbar.setClickable(false);
        mSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

                // Perform your animation of the thumb icon if any, here I will progressively make the thumb icon transparent
                int alpha = (int)(progress * (255/80));
                seekBar.getThumb().setAlpha(255 - alpha);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // This will be called when user starts to touch the icon
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                byte[] bleData = {0};
                // When user lift up the touch, we will check if it is at the end of the bar. If it is not the end, then we will set the progress status to 0 so it jumps back to the origin point
                if (seekBar.getProgress() < 100){
                  //  seekBar.setThumb(MainActivity.this.getResources().getDrawable(R.drawable.slider2));
                    seekBar.setProgress(0);
                } else {
                    // Put all the logic we want to proceed after unlock here
                    if (mService != null && mService.isConnected()) {
                        if (mSeekbar.getProgress()== 0) {
                            if(mPasskeyType == "otp") {
                                if(mOTPasskeyNr < mOTPKeyparts.length) {
                                    // process each mOTPKeyparts[p]
                                    bleData = Utils.hexStringToByteArray( mOTPKeyparts[mOTPasskeyNr++] );
                                }
                            }
                            else bleData[0] = 1;
                            mService.writeRXCharacteristicCommand(bleData);
                            Log.d(TAG, "eRL Close CMD");
                        } else {
                            if(mPasskeyType == "otp") {
                                if(mOTPasskeyNr < mOTPKeyparts.length) {
                                    // process each mOTPKeyparts[p]
                                    bleData = Utils.hexStringToByteArray( mOTPKeyparts[mOTPasskeyNr++] );
                                }
                            }
                            else bleData[0] = 0;
                            mService.writeRXCharacteristicCommand(bleData);
                            Log.d(TAG, "eRL Open CMD");
                        }
                    }
                }
            }
        });

//        sb.setOnClickListener(new View.OnClickListener() {
//            byte[] bleData = {0};
//            @Override
//            public void onClick(View v) {
//                if (mService != null && mService.isConnected()) {
//                    if (!sb.isChecked()) {
//                        if(mPasskeyType == "otp") {
//                            if(mOTPasskeyNr < mOTPKeyparts.length) {
//                                 // process each mOTPKeyparts[p]
//                                bleData = Utils.hexStringToByteArray( mOTPKeyparts[mOTPasskeyNr++] );
//                            }
//                        }
//                        else bleData[0] = 1;
//                        mService.writeRXCharacteristicCommand(bleData);
//                        Log.d(TAG, "eRL Close CMD");
//                    } else {
//                        if(mPasskeyType == "otp") {
//                            if(mOTPasskeyNr < mOTPKeyparts.length) {
//                                // process each mOTPKeyparts[p]
//                                bleData = Utils.hexStringToByteArray( mOTPKeyparts[mOTPasskeyNr++] );
//                            }
//                        }
//                        else bleData[0] = 0;
//                        mService.writeRXCharacteristicCommand(bleData);
//                        Log.d(TAG, "eRL Open CMD");
//                    }
//                }
//            }
//        });

        // Set initial UI state

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        // client = new GoogleApiClient.Builder(this).addApi(AppIndex.API).build();
    }


//    public static boolean isLocationEnabled(Context context) {
//        int locationMode = 0;
//        String locationProviders;
//
//         try {
//            locationMode = Settings.Secure.getInt(context.getContentResolver(), Settings.Secure.LOCATION_MODE);
//
//        } catch (Settings.SettingNotFoundException e) {
//            e.printStackTrace();
//        }
//        return locationMode != Settings.Secure.LOCATION_MODE_OFF;
//    }

//    @Override
//    // for support android 6+
//    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
//        switch (requestCode) {
//            case REQUEST_CODE_ASK_PERMISSIONS:
//                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                    Toast.makeText(this, "Loation permission allowed", Toast.LENGTH_SHORT)
//                            .show();
//                } else {
//                    // Permission Denied
//                    Toast.makeText(this, "Loation permission denied", Toast.LENGTH_SHORT)
//                            .show();
//                }
//                break;
//            default:
//                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
//        }
//    }

    //ERL service connected/disconnected
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder rawBinder) {
            mService = ((eRLBikeLockService.LocalBinder) rawBinder).getService();
            Log.d(TAG, "onServiceConnected mService= " + mService);
            if (!mService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }

        }

        public void onServiceDisconnected(ComponentName classname) {
            ////     mService.disconnect(mDevice);
            mService = null;
        }
    };

    private Handler mHandler = new Handler() {
        @Override

        //Handler events that received from ERL service
        public void handleMessage(Message msg) {

        }
    };

    /* Handle events from bluetooth service. Must be added to intent filter */
    private final BroadcastReceiver ERLStatusChangeReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            final boolean mLockState;
            final Intent mIntent = intent;

            //*********************//
            if ( action.equals(eRLBikeLockService.ACTION_GATT_CONNECTED)) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
                        Log.d(TAG, "ERL_CONNECT_MSG");

                        //btnConnectLock.setText("Disconnect");
                        if (mConnectTo == CONNECT_LOCK) {
                            btnConnectLock.setText("Disconnect");
                        }
                        if(mDevice.getName().equals("AXA:77DC66F211D8639801ED")) {
                            ((TextView) findViewById(R.id.deviceName)).setText("Cycle 53456" + " - Paired");
                        }else if(mDevice.getName().equals("AXA:52C1952E2190F6897134")){
                            ((TextView) findViewById(R.id.deviceName)).setText("Cycle 66235" + " - Paired");
                        }

                        // ((TextView) findViewById(R.id.deviceName)).setText(mDevice.getName() + " - ready");

                        mState = ERL_PROFILE_CONNECTED;
                        //sb.setEnabled(false);
                    }
                });
            }

            //*********************//
            if (action.equals(eRLBikeLockService.ACTION_GATT_DISCONNECTED)) {
               // sb.setVisibility(View.INVISIBLE);
                mSeekbar.setProgress(0);
                runOnUiThread(new Runnable() {
                    public void run() {
                        String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
                        Log.d(TAG, "ERL_DISCONNECT_MSG");

                        if (mConnectTo == CONNECT_LOCK) {
                            btnConnectLock.setText(getString(R.string.connect_to_lock));
                        }

                        ((TextView) findViewById(R.id.deviceName)).setText("please try again");
                        mState = ERL_PROFILE_DISCONNECTED;
                        mService.close();
                    }
                });
            }

            //*********************//
            if (action.equals(eRLBikeLockService.ACTION_GATT_SERVICES_DISCOVERED)) {
                mService.enableTXNotification();
//                sb.setVisibility(View.VISIBLE);
//                sb.setEnabled(true);
            }
            //*********************//
            if (action.equals(eRLBikeLockService.ACTION_DATA_AVAILABLE)) {

                txValue = intent.getByteArrayExtra(eRLBikeLockService.EXTRA_DATA);
                Log.d(TAG, "got " + txValue[0]); //wfr
                if((txValue[0]&0x01)==0x00) mSeekbar.setProgress(0);
                else mSeekbar.setProgress(100);
                runOnUiThread(new Runnable() {
                    public void run() {
                        try {
                            String text = new String(txValue, "UTF-8");
                            String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
                            if((txValue[0]&0x08)==0x00) mSeekbar.setProgress(0);
                             mSeekbar.setProgress(0);

                        } catch (Exception e) {
                            Log.e(TAG, e.toString());
                        }

                        SharedPreferences preferences = getSharedPreferences("lockstate", Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = preferences.edit();
                        editor.putInt("lockstate",txValue[0]);
                        editor.commit();

                        if(txValue[0]== 0){
                            liverLockText.setVisibility(View.VISIBLE);
                        }else {
                            liverLockText.setVisibility(View.GONE);
                        }

                    }
                });
            }
            //*********************//
            if (action.equals(eRLBikeLockService.DEVICE_DOES_NOT_SUPPORT_ERL)) {
                showMessage("Device doesn't support eRL. Disconnecting");
                mService.disconnect();
            }

            // wfr - indicates data write to chaacteristic has been sent
            //  to the stack and it can now accept more. If you don't wait for this
            //  an immdiate subsequent write may fail, if the "BLE buffer" is full.
            else if (eRLBikeLockService.ACTION_ON_WRITE_CHAR.equals(action)) {
                // NOTE: we are running on the main thread so no need for synchronization
                //  but don't spend too much time.
                Log.d(TAG, "Received broadcast ON_WRITE_CHAR");
                dequeCommand(); // kickoff next send  if any buffered
                mWaitingWriteChar = false; // clearing the flag indicates more

            } else if (eRLBikeLockService.ACTION_BOND_STATE_CHANGED.equals(action)) {
                final int bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, 0);
                if (bondState == BluetoothDevice.BOND_BONDED) {
                    Log.d(TAG, "Bond state changed: BONDED");
                } else if (bondState == BluetoothDevice.BOND_BONDING) {
                    Log.d(TAG, "Bond state changed: BONDING");
                } else if (bondState == BluetoothDevice.BOND_NONE) {
                    Log.d(TAG, "Bond state changed: NOT BONDED");

                }
            }
        }
    };

    private final BroadcastReceiver mPairingRequestReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (action.equals(BluetoothDevice.ACTION_PAIRING_REQUEST)) {
                try {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    int pin=intent.getIntExtra("android.bluetooth.device.extra.PAIRING_KEY", mPassKeyInt);
                    //the pin in case you need to accept for an specific pin
                    //                  Log.d(TAG, "Start Auto Pairing. PIN = " + intent.getIntExtra("android.bluetooth.device.extra.PAIRING_KEY",mPassKeyInt));
                    byte[] pinBytes;
                    pinBytes = (""+pin).getBytes("UTF-8");
                    device.setPin(pinBytes);
                    //setPairing confirmation if neeeded
                    //                   device.setPairingConfirmation(true);
                    showMessage("ACTION_PAIRING_REQUEST received " + pin);

                } catch (Exception e) {
                    Log.e(TAG, "Error occurs when trying to auto pair");
                    e.printStackTrace();
                }
            }
            if (action.equals(BluetoothDevice.ACTION_BOND_STATE_CHANGED)) {
                try {
                    showMessage("ACTION_BOND_STATE_CHANGED"+mDevice.getBondState());

                } catch (Exception e) {
                    Log.e(TAG, "Error occurs when trying to auto pair");
                    e.printStackTrace();
                }
            }
        }
    };

    private void service_init() {
        Intent bindIntent = new Intent(this, eRLBikeLockService.class);
        bindService(bindIntent, mServiceConnection, Context.BIND_AUTO_CREATE);

        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_PAIRING_REQUEST);
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        registerReceiver(mPairingRequestReceiver, filter);

        LocalBroadcastManager.getInstance(this).registerReceiver(ERLStatusChangeReceiver, makeGattUpdateIntentFilter());
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(eRLBikeLockService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(eRLBikeLockService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(eRLBikeLockService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(eRLBikeLockService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(eRLBikeLockService.DEVICE_DOES_NOT_SUPPORT_ERL);

        // allow these events to be sent to BroadcastReceiver
        intentFilter.addAction(eRLBikeLockService.ACTION_ON_WRITE_CHAR); // this indicates a write has "completed", such that you can write more without error.
        intentFilter.addAction(eRLBikeLockService.ACTION_BOND_STATE_CHANGED); // be notified of bond state change. Shouldn't need this.

        return intentFilter;
    }
//    @Override
//    public void onStart() {
//        super.onStart();
//        // ATTENTION: This was auto-generated to implement the App Indexing API.
//        // See https://g.co/AppIndexing/AndroidStudio for more information.
//        client.connect();
//        // ATTENTION: This was auto-generated to implement the App Indexing API.
//        // See https://g.co/AppIndexing/AndroidStudio for more information.
//        Action viewAction = Action.newAction(
//                Action.TYPE_VIEW, // TODO: choose an action type.
//                "Main Page", // TODO: Define a title for the content shown.
//                // TODO: If you have web page content that matches this app activity's content,
//                // make sure this auto-generated web page URL is correct.
//                // Otherwise, set the URL to null.
//                Uri.parse("http://host/path"),
//                // TODO: Make sure this auto-generated app URL is correct.
//                Uri.parse("android-app://com.MobiComm.Axa_eRL_Demo/http/host/path")
//        );
//        AppIndex.AppIndexApi.start(client, viewAction);
//    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy()");

        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(ERLStatusChangeReceiver);
        } catch (Exception ignore) {
            Log.e(TAG, ignore.toString());
        }
        unbindService(mServiceConnection);
        mService.stopSelf();
        mService = null;

    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        if (!mBtAdapter.isEnabled()) {
            Log.i(TAG, "onResume - BT not enabled yet");
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        }

//        if (!isLocationEnabled(this)) {
//            Intent enableLocationIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
//            startActivityForResult(enableLocationIntent, REQUEST_ENABLE_LOCATION);
//        }

        // apparently related to new permissions for android 6+
        // Using ActivityCompat means it will work with eaerlier android without crashing
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                    REQUEST_CODE_ASK_PERMISSIONS);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    private void handleBonding() {
        int bondState = mDevice.getBondState();
        if (bondState == BluetoothDevice.BOND_BONDED) {
            Log.d(TAG, "device is BONDED");
            return;
        } else if (bondState == BluetoothDevice.BOND_BONDING) {
            Log.d(TAG, "device is BONDING");
            return;
        } else {
            Log.d(TAG, "device is NOT BONDED");
        }

    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_EDIT_SETTINGS: // from settings activity
                // Make sure the request was successful
                if (resultCode == RESULT_OK) {
                    // do something
                }
                break;
            case REQUEST_SELECT_DEVICE:
                //When the DeviceListActivity return, with the selected device address
                if (resultCode == Activity.RESULT_OK && data != null) {
                    String deviceAddress = data.getStringExtra(BluetoothDevice.EXTRA_DEVICE);
                    mDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(deviceAddress);

                    handleBonding();

                    Log.d(TAG, "... onActivityResultdevice.address==" + mDevice + "mserviceValue" + mService);
                    if(mDevice.getName().equals("AXA:77DC66F211D8639801ED")) {
                        ((TextView) findViewById(R.id.deviceName)).setText("Cycle 53456" + " - connecting...");
                    }else if(mDevice.getName().equals("AXA:52C1952E2190F6897134")){
                        ((TextView) findViewById(R.id.deviceName)).setText("Cycle 66235" + " - connecting...");
                    }
                    //((TextView) findViewById(R.id.deviceName)).setText(mDevice.getName() + " - connecting");
                    mService.connect(deviceAddress);
                }
                break;
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    Toast.makeText(this, "Bluetooth has turned on ", Toast.LENGTH_SHORT).show();

                } else {
                    // User did not enable Bluetooth or an error occurred
                    Log.d(TAG, "BT not enabled");
                    Toast.makeText(this, "Problem in BT Turning ON ", Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
            default:
                Log.e(TAG, "wrong request code");
                break;
        }
    }

    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId) {

    }


    private void showMessage(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();

    }


    @Override
    public void onBackPressed() {
//        if (mState == ERL_PROFILE_CONNECTED) {
//            Intent startMain = new Intent(Intent.ACTION_MAIN);
//            startMain.addCategory(Intent.CATEGORY_HOME);
//            startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//            startActivity(startMain);
//            showMessage("CYKUL SMART LOCK DEMO's running in background.\nDisconnect to exit");
//        } else {
//            finish();
//        }
        finish();
    }

    private void pairDevice(BluetoothDevice device) {
        try {
            byte[] pinBytes;
            pinBytes = (""+mPassKeyInt).getBytes("UTF-8");
            device.setPin(pinBytes);

        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
    }


    /**
     * Send the contents of mEKeyAscii (converted to binary) to the RX characteristic which should
     * trigger bonding (if hasn't been done already) and enable lock/unlock
     * command access.
     * You should press "get ekey" first before sending ekey. To test don't forget to delete
     * the phone's bonding each time.
     *
     * @param view
     */




    /**
     * see https://github.com/koush/ion#get-ion
     * Use keysafe cloud api to retrieve ekey, etc
     *
     * @param view
     */


//    public void onSettingsPressed(View view) {
//        Intent activity = new Intent(getBaseContext(), SettingsActivity.class);
//        // @todo should get from persistent setting - will go back to default when app closes
//        activity.putExtra(SettingsActivity.SETTINGS_ERL_ID, mERLId);
//        activity.putExtra(SettingsActivity.SETTINGS_API_KEY, mAPIKey);
//        startActivityForResult(activity, REQUEST_EDIT_SETTINGS);
//    }

    /**
     * wfr queued writing for sending eKey, which is 110 bytes, 20 at a time (5-20 bytes, 1-10 byte)
     *
     * @param data
     */
    private void queueData(byte[] data) {
        mWriteQueue.add(data);
        String.format("%02x", data[0]);
        Log.d(TAG, "queuing: " + data.length + " bytes: " +
                String.format("%02x", data[0]) + "," +
                String.format("%02x", data[1]) + "," +
                String.format("%02x", data[2]) + "," +
                String.format("%02x", data[3]) + "...");
        if (!mWaitingWriteChar)
            dequeCommand();
    }

    private void dequeCommand() {
        if (mWriteQueue.size() > 0) {
            byte[] item = mWriteQueue.remove(0);
            if (item == null) {
                Log.d(TAG, "error item was null, not writing characteristic");
                return;
            }
            mWaitingWriteChar = true;  // indicate waiting for write done callback
            mService.writeRXCharacteristicCommand(item);
        }
    }
}
