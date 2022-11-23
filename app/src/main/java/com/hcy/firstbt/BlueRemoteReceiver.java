package com.hcy.firstbt;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import java.lang.reflect.Method;

public class BlueRemoteReceiver extends BroadcastReceiver {
    private static String TAG = "BlueRemoteReceiver";
    String pin = "0000";  //此处为你要连接的蓝牙设备的初始密钥，一般为1234或0000
    private BluetoothProfile mService = null;

    private BluetoothProfile.ServiceListener mServiceConnection =
            new BluetoothProfile.ServiceListener() {

                @Override
                public void onServiceDisconnected(int profile) {
                    Log.w(TAG, "Service disconnected, perhaps unexpectedly");
                }

                @Override
                public void onServiceConnected(int profile, BluetoothProfile proxy) {
                    Log.w(TAG, "Service onServiceConnected:"+profile);
                    mService = proxy;
                }
            };

    @SuppressLint("MissingPermission")
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction(); //得到action
        if (BluetoothDevice.ACTION_FOUND.equals(action)) {  //发现设备
            BluetoothDevice btDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (btDevice != null && btDevice.getName() != null && MainActivity.isLegalRemote(btDevice.getName()))//HC-05设备如果有多个，第一个搜到的那个会被尝试。
            {
                Log.e(TAG, btDevice.toString() + "[" + btDevice.getName() + "]");
                //todo 12 已配对，未连接是否需要处理
                if (btDevice.getBondState() == BluetoothDevice.BOND_NONE||btDevice.getBondState() == BluetoothDevice.BOND_BONDING) {
                    try {
                        //通过工具类ClsUtils,调用createBond方法
                        ClsUtils.createBond(btDevice.getClass(), btDevice);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    Log.i(TAG, "find " + btDevice.getName() + "but state :" + btDevice.getBondState());
                    //connected(btDevice);
                }
            }
        } else if (action.equals(BluetoothDevice.ACTION_PAIRING_REQUEST)) {
            BluetoothDevice btDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            Log.i(TAG, "ACTION_PAIRING_REQUEST ：" + btDevice.getName());
            if (btDevice != null && btDevice.getName() != null && MainActivity.isLegalRemote(btDevice.getName())) {
                //abortBroadcast();
                try {
                    Bundle extras = intent.getExtras();
                    Object pairkey = extras.get("android.bluetooth.device.extra.PAIRING_KEY");
                    Log.i(TAG, "pairkey ：" + pairkey);
                    //1.确认配对
                    ClsUtils.setPairingConfirmation(btDevice.getClass(), btDevice, true);
                    //2.终止有序广播
                    Log.i(TAG, "isOrderedBroadcast:" + isOrderedBroadcast() + ",isInitialStickyBroadcast:" + isInitialStickyBroadcast());
                    //如果没有将广播终止，则会出现一个一闪而过的配对框。
                    //3.调用setPin方法进行配对...
                    boolean ret = ClsUtils.setPin(btDevice.getClass(), btDevice, pin);
                    Log.e(TAG, "ret:" + ret);
                    if (ret) {
                        BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
                        connected(btDevice);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                Log.i(TAG, "这个设备不是目标蓝牙设备");
            }
        } else if (action.equals(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)) {
            Log.w(TAG, "ACTION_DISCOVERY_FINISHED");
            BluetoothAdapter.getDefaultAdapter().startDiscovery();
        }
    }


    private void connected(BluetoothDevice device) throws Exception {
        if (mService != null && device != null) {
            Log.e(TAG, "Connecting to target: " + device.getAddress());
            Class clz = Class.forName("android.bluetooth.BluetoothHidHost");
            if (clz.cast(mService) == null) return;
            Method connect = clz.getMethod("connect", BluetoothDevice.class);
            Method setPriority = clz.getMethod("setPriority", BluetoothDevice.class, int.class);
            boolean ret = (boolean) connect.invoke(clz.cast(mService), device);
            if (ret) {
                setPriority.invoke(clz.cast(mService), device, 1000/*BluetoothProfile.PRIORITY_AUTO_CONNECT*/);
                Log.e(TAG, "connect ok and show toast!");
            } else {
                Log.e(TAG, "connect no!");
            }
        } else {
            Log.e(TAG, "mService or device no work!");
        }
    }
}
