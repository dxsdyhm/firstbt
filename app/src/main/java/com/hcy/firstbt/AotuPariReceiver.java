package com.hcy.firstbt;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.Set;

public class AotuPariReceiver extends BroadcastReceiver {
    private static String ACTION_TEST="com.hcy.test";
    //每次启动时检查是否已经存在配对遥控
    @Override
    public void onReceive(Context context, Intent intent) {
        if(Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())){
            checkBoundDevice(context);
        }else if(ACTION_TEST.equals(intent.getAction())){
            Log.w("dxsTest","onReceive:"+ACTION_TEST);
            checkBoundDevice(context);
        }else if(BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(intent.getAction())){
            int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1);
            int previousState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, -1);
            if(previousState==BluetoothDevice.BOND_BONDED&&state==BluetoothDevice.BOND_NONE){
                //有设备解绑，检查是否需要重新配对
                checkBoundDevice(context);
            }
        }
    }

    @SuppressLint("MissingPermission")
    private void checkBoundDevice(Context context){
        BluetoothAdapter defaultAdapter = BluetoothAdapter.getDefaultAdapter();
        if(defaultAdapter!=null){
            if(!defaultAdapter.isEnabled()){
                defaultAdapter.enable();
            }
            Set<BluetoothDevice> devices = defaultAdapter.getBondedDevices();
            boolean hasBounded=false;
            for(BluetoothDevice bt:devices){
                Log.e("dxsTest","bt:"+bt.getName()+" bound:"+bt.getBondState()+"  type:"+bt.getType());
                if(MainActivity.BT_NAME_DEFAULT.equals(bt.getName())&&bt.getBondState()==BluetoothDevice.BOND_BONDED){
                    hasBounded=true;
                    break;
                }
            }
            if(!hasBounded){
                Intent intent=new Intent();
                intent.setClass(context,MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            }
        }
    }
}
