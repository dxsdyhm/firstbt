package com.hcy.firstbt;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.input.InputManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;

import com.baoyachi.stepview.HorizontalStepView;
import com.baoyachi.stepview.bean.StepBean;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static String TAG ="MainActivity";
    protected HorizontalStepView stepView;
    //private BlueRemoteReceiver remoteReceiver;
    private BluetoothAdapter mBluetoothAdapter;

    String pin = "0000";  //此处为你要连接的蓝牙设备的初始密钥，一般为1234或0000
    public static String BT_NAME_DEFAULT = "RemoteC01";
    public String BT_NAME=BT_NAME_DEFAULT;
    private BluetoothProfile mService = null;

    private BroadcastReceiver remoteReceiver=new BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction(); //得到action
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {  //发现设备
                BluetoothDevice btDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (btDevice != null && btDevice.getName() != null && btDevice.getName().contains(BT_NAME))//HC-05设备如果有多个，第一个搜到的那个会被尝试。
                {
                    Log.e(TAG, btDevice.toString() + "[" + btDevice.getName() + "]"+" state:"+btDevice.getBondState());
                    //todo 12 已配对，未连接是否需要处理 已配对的也配对（后果未知）
                    if (btDevice.getBondState() == BluetoothDevice.BOND_NONE) {
                        try {
                            //通过工具类ClsUtils,调用createBond方法
                            ClsUtils.createBond(btDevice.getClass(), btDevice);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }else if(btDevice.getBondState() == BluetoothDevice.BOND_BONDED){
                        boolean isConnect=isConnect();
                        if(!isConnect){
                            try {
                                boolean rem=ClsUtils.removeBond(btDevice.getClass(), btDevice);
                                if(rem){
                                    ClsUtils.createBond(btDevice.getClass(), btDevice);
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    } else {
                        Log.i(TAG, "find " + btDevice.getName() + " but state :" + btDevice.getBondState());
                        //connected(btDevice);
                    }
                }
            } else if (action.equals(BluetoothDevice.ACTION_PAIRING_REQUEST)) {
                BluetoothDevice btDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (btDevice != null && btDevice.getName() != null && btDevice.getName().contains(BT_NAME)) {
                    abortBroadcast();
                    try {
                        //1.确认配对
                        ClsUtils.setPairingConfirmation(btDevice.getClass(), btDevice, true);
                        boolean ret = ClsUtils.setPin(btDevice.getClass(), btDevice, pin);
                        Log.e(TAG, "ret:" + ret);
                        if (ret) {
                            BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
                            setStep(2);
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
    };

    private BluetoothProfile.ServiceListener mServiceConnection =
            new BluetoothProfile.ServiceListener() {

                @Override
                public void onServiceDisconnected(int profile) {
                    Log.w(TAG, "Service disconnected, perhaps unexpectedly");
                }

                @Override
                public void onServiceConnected(int profile, BluetoothProfile proxy) {
                    Log.w(TAG, "Service onServiceConnected:"+profile);
                    registerInputMethodMonitor();
                    mService = proxy;
                }
            };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initUI();
    }

    private void initUI() {
        setStep(0);
        if(startScan()){
            setStep(1);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        startScan();
    }

    @Override
    protected void onResume() {
        super.onResume();
        registRe();
        startScan();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(remoteReceiver!=null){
            unregisterReceiver(remoteReceiver);
        }
    }

    private void registRe(){
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothDevice.ACTION_PAIRING_REQUEST);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filter.setPriority(Integer.MAX_VALUE);
        registerReceiver(remoteReceiver, filter);
    }

    @SuppressLint("MissingPermission")
    private boolean startScan() {
        BT_NAME=getBTName();
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            Log.i(TAG,"No bluetooth device!");
            return false;
        } else {
            Log.i(TAG,"Bluetooth device exits!");
            if (!mBluetoothAdapter.isEnabled()) {
                mBluetoothAdapter.enable();
            }
            if (!BluetoothAdapter.getDefaultAdapter().getProfileProxy(this, mServiceConnection,
                    4/*BluetoothProfile.HID_HOST*/)) {
                Log.i(TAG,"Bluetooth getProfileProxy failed!");
            }
        }
        return BluetoothAdapter.getDefaultAdapter().startDiscovery();
    }

    private void connected(BluetoothDevice device){
        if (mService != null && device != null) {
            Log.e(TAG, "Connecting to target: " + device.getAddress());
            try {
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
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            Log.e(TAG, "mService or device no work!");
        }
    }

    private void setStep(int step){
        if(stepView==null){
            stepView = findViewById(R.id.v_state);
        }
        List<StepBean> stepsBeanList = new ArrayList<>();
        if(step==1){
            StepBean stepBean0 = new StepBean("准备", 0);
            StepBean stepBean1 = new StepBean("连接中", -1);
            StepBean stepBean2 = new StepBean("成功", -1);
            stepsBeanList.add(stepBean0);
            stepsBeanList.add(stepBean1);
            stepsBeanList.add(stepBean2);
        }else if(step==2){
            StepBean stepBean0 = new StepBean("准备", 1);
            StepBean stepBean1 = new StepBean("连接中", 0);
            StepBean stepBean2 = new StepBean("成功", -1);
            stepsBeanList.add(stepBean0);
            stepsBeanList.add(stepBean1);
            stepsBeanList.add(stepBean2);
        }else if(step==3){
            StepBean stepBean0 = new StepBean("准备", 1);
            StepBean stepBean1 = new StepBean("连接中", 1);
            StepBean stepBean2 = new StepBean("成功", 0);
            stepsBeanList.add(stepBean0);
            stepsBeanList.add(stepBean1);
            stepsBeanList.add(stepBean2);
        }else {
            StepBean stepBean0 = new StepBean("准备", -1);
            StepBean stepBean1 = new StepBean("连接中", -1);
            StepBean stepBean2 = new StepBean("成功", -1);
            stepsBeanList.add(stepBean0);
            stepsBeanList.add(stepBean1);
            stepsBeanList.add(stepBean2);
        }
        stepView.setStepViewTexts(stepsBeanList)
                .setTextSize(11)
                .setStepsViewIndicatorCompletedLineColor(ContextCompat.getColor(this, R.color.step))//设置StepsViewIndicator完成线的颜色
                .setStepsViewIndicatorUnCompletedLineColor(ContextCompat.getColor(this, R.color.step))//设置StepsViewIndicator未完成线的颜色
                .setStepViewComplectedTextColor(ContextCompat.getColor(this, R.color.black))//设置StepsView text完成线的颜色
                .setStepViewUnComplectedTextColor(ContextCompat.getColor(this, R.color.black))//设置StepsView text未完成线的颜色
                .setStepsViewIndicatorCompleteIcon(ContextCompat.getDrawable(this, R.drawable.step_compli))//设置StepsViewIndicator CompleteIcon
                .setStepsViewIndicatorDefaultIcon(ContextCompat.getDrawable(this, R.drawable.point))//设置StepsViewIndicator DefaultIcon
                .setStepsViewIndicatorAttentionIcon(ContextCompat.getDrawable(this, R.drawable.point));//设置StepsViewIndicator AttentionIcon
    }

    private void registerInputMethodMonitor() {
        InputManager inputManager = (InputManager) getSystemService(Context.INPUT_SERVICE);
        inputManager.registerInputDeviceListener(mInputListener, new Handler());
        int[] inputDevices = inputManager.getInputDeviceIds();
    }

    private InputManager.InputDeviceListener mInputListener =
            new InputManager.InputDeviceListener() {
                @Override
                public void onInputDeviceRemoved(int deviceId) {
                    // ignored
                }

                @Override
                public void onInputDeviceChanged(int deviceId) {
                    // ignored
                }

                @Override
                public void onInputDeviceAdded(int deviceId) {
                    //添加成功
                    setStep(3);
                    stopMyself();
                }
            };

    private void stopMyself(){
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                finishAndRemoveTask();
            }
        },2000);
    }

    private boolean isConnect(){
        try{
            Method method = BluetoothAdapter.getDefaultAdapter().getClass().getDeclaredMethod("getConnectionState", (Class[]) null);
            method.setAccessible(true);
            int state = (int) method.invoke(BluetoothAdapter.getDefaultAdapter(), (Object[]) null);
            return state == BluetoothAdapter.STATE_CONNECTED;
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static String getBTName(){
        String s= ReflectUtils.reflect("android.os.SystemProperties").method("get","persist.sys.remotebt").get();
        if(s!=null&&s.length()>0){
            return s;
        }
        return BT_NAME_DEFAULT;
    }
}