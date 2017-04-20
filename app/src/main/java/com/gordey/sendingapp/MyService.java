package com.gordey.sendingapp;

import android.app.Service;
import android.content.Intent;
import android.os.Debug;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import com.libelium.mysignalsconnectkit.BluetoothManagerHelper;
import com.libelium.mysignalsconnectkit.BluetoothManagerService;
import com.libelium.mysignalsconnectkit.callbacks.BluetoothManagerCharacteristicsCallback;
import com.libelium.mysignalsconnectkit.callbacks.BluetoothManagerHelperCallback;
import com.libelium.mysignalsconnectkit.callbacks.BluetoothManagerQueueCallback;
import com.libelium.mysignalsconnectkit.callbacks.BluetoothManagerServicesCallback;
import com.libelium.mysignalsconnectkit.pojo.LBSensorObject;
import com.libelium.mysignalsconnectkit.utils.BitManager;
import com.libelium.mysignalsconnectkit.utils.BitManager.BLUETOOTH_DISPLAY_MODE;
import com.libelium.mysignalsconnectkit.utils.LBValueConverter;
import com.libelium.mysignalsconnectkit.utils.StringConstants;
import com.libelium.mysignalsconnectkit.utils.Utils;

public class MyService extends Service implements BluetoothManagerHelperCallback, BluetoothManagerServicesCallback, BluetoothManagerCharacteristicsCallback, BluetoothManagerQueueCallback  {

    static String kMySignalsId = "mysignals h00100"; // MySignals advertising name

    private static BluetoothManagerService mService = null;

    private BluetoothGattService selectedService;
    private BluetoothDevice selectedDevice;
    private ArrayList<LBSensorObject> selectedSensors;
    private ArrayList<BluetoothGattCharacteristic> notifyCharacteristics;
    private boolean writtenService;
    private BluetoothGattCharacteristic characteristicSensorList;

    private BluetoothManagerHelper bluetoothManager;
    private Timer timerRssi;

    private final Handler handler = new Handler();

    private int numIntent;
    private String airflowData = "null";

    // It's the code we want our Handler to execute to send data
    private Runnable sendData = new Runnable() {
        // the specific method which will be executed by the handler
        public void run() {
            Log.d("DEBUG", "Service Started : " + numIntent);
            numIntent++;

            // sendIntent is the object that will be broadcast outside our app
            Intent sendIntent = new Intent();

            // We add flags for example to work from background
            sendIntent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION|Intent.FLAG_FROM_BACKGROUND|Intent.FLAG_INCLUDE_STOPPED_PACKAGES	);

            // SetAction uses a string which is an important name as it identifies the sender of the itent and that we will give to the receiver to know what to listen.
            // By convention, it's suggested to use the current package name
            sendIntent.setAction("com.test.sendintent.IntentToUnity");

            // Here we fill the Intent with our data, here just a string with an incremented number in it.
            sendIntent.putExtra(Intent.EXTRA_TEXT, airflowData);
            // And here it goes ! our message is send to any other app that want to listen to it.
            sendBroadcast(sendIntent);

            // In our case we run this method each second with postDelayed
            handler.removeCallbacks(this);
            handler.postDelayed(this, 20);
        }
    };



    // When service is started
    @Override
    public void onStart(Intent intent, int startid) {
        numIntent = 0;
        // We first start the Handle
        Log.d("DEBUG", "onStart : Service Started");
        handler.removeCallbacks(sendData);
        handler.postDelayed(sendData, 20);

        try {

            mService = BluetoothManagerService.getInstance();

            mService.initialize(this);
            mService.setServicesCallback(MyService.this);
            mService.setCharacteristicsCallback(MyService.this);
            mService.setQueueCallback(MyService.this);

        } catch (Exception e) {

        }

        scanBluetoothDevices();
        scheduleRSSIReader();

        createInterface();
    }

    private void createInterface() {

        if (BluetoothManagerHelper.hasBluetooth(this)) {

            writtenService = false;
            notifyCharacteristics = new ArrayList<BluetoothGattCharacteristic>();
            selectedSensors = createSensorsDisplay();
            selectedDevice = null;
        } else {

            Log.d("DEBUG", "The device does not have BLE technology, please use an Android device with BLE technology.");
        }
    }

    /**
     * Method to create a list of sensors to notify.
     *
     * @return List of sensors
     */
    private ArrayList<LBSensorObject> createSensorsDisplay() {

        int maxNotifications = Utils.getMaxNotificationNumber();

        ArrayList<LBSensorObject> sensors = new ArrayList<LBSensorObject>();

        LBSensorObject object = LBSensorObject.newInstance();

        object.tag = 1;
        object.tickStatus = true;
        object.uuidString = StringConstants.kUUIDBodyPositionSensor;

        LBSensorObject.preloadValues(object);
        sensors.add(object);

        object = LBSensorObject.newInstance();

        object.tag = 2;
        object.tickStatus = true;
        object.uuidString = StringConstants.kUUIDTemperatureSensor;

        LBSensorObject.preloadValues(object);
        sensors.add(object);

        object = LBSensorObject.newInstance();

        object.tag = 3;
        object.tickStatus = true;
        object.uuidString = StringConstants.kUUIDEMGSensor;

        LBSensorObject.preloadValues(object);
        sensors.add(object);

        object = LBSensorObject.newInstance();

        object.tag = 4;
        object.tickStatus = true;
        object.uuidString = StringConstants.kUUIDECGSensor;

        LBSensorObject.preloadValues(object);
        sensors.add(object);

        object = LBSensorObject.newInstance();

        object.tag = 5;
        object.tickStatus = (maxNotifications > 4) ? true : false;
        object.uuidString = StringConstants.kUUIDAirflowSensor;

        LBSensorObject.preloadValues(object);
        sensors.add(object);

        object = LBSensorObject.newInstance();

        object.tag = 6;
        object.tickStatus = (maxNotifications > 4) ? true : false;
        object.uuidString = StringConstants.kUUIDGSRSensor;

        LBSensorObject.preloadValues(object);
        sensors.add(object);

        object = LBSensorObject.newInstance();

        object.tag = 7;
        object.tickStatus = (maxNotifications > 4) ? true : false;
        object.uuidString = StringConstants.kUUIDBloodPressureSensor;

        LBSensorObject.preloadValues(object);
        sensors.add(object);

        object = LBSensorObject.newInstance();

        object.tag = 8;
        object.tickStatus = (maxNotifications > 7) ? true : false;
        object.uuidString = StringConstants.kUUIDPulsiOximeterSensor;

        LBSensorObject.preloadValues(object);
        sensors.add(object);

        object = LBSensorObject.newInstance();

        object.tag = 9;
        object.tickStatus = (maxNotifications > 7) ? true : false;
        object.uuidString = StringConstants.kUUIDGlucometerSensor;

        LBSensorObject.preloadValues(object);
        sensors.add(object);

        object = LBSensorObject.newInstance();

        object.tag = 10;
        object.tickStatus = (maxNotifications > 7) ? true : false;
        object.uuidString = StringConstants.kUUIDSpirometerSensor;

        LBSensorObject.preloadValues(object);
        sensors.add(object);

        object = LBSensorObject.newInstance();

        object.tag = 11;
        object.tickStatus = (maxNotifications > 7) ? true : false;
        object.uuidString = StringConstants.kUUIDSnoreSensor;

        LBSensorObject.preloadValues(object);
        sensors.add(object);

        return sensors;
    }

    /**
     * Scan method to find new devices
     */
    private void scanBluetoothDevices() {

        bluetoothManager = BluetoothManagerHelper.getInstance();

        bluetoothManager.setInitParameters(this, this);

        List<BluetoothDevice> devicesBonded = bluetoothManager.getBondedDevices();

        if (devicesBonded.size() > 0) {

            selectedDevice = null;

            for (BluetoothDevice deviceItem : devicesBonded) {

                String name = deviceItem.getName();

                if (name != null) {

                    if (name.toLowerCase().contains(kMySignalsId)) {

                        Log.d("DEBUG", "Address: " + name);

                        this.selectedDevice = deviceItem;

                        break;
                    }
                }
            }

            if (selectedDevice != null) {

                performConnection();
            } else {
                bluetoothManager.startLEScan(true);
            }

        } else {

            bluetoothManager.startLEScan(true);
        }
    }

    /**
     * Scheduler method to update and query RSSI value
     */
    private void scheduleRSSIReader() {

        if (timerRssi != null) {

            timerRssi.cancel();
            timerRssi.purge();
            timerRssi = null;
        }

        timerRssi = new Timer();
        timerRssi.schedule(new TimerTask() {

            @Override
            public void run() {

                if (mService != null) {

                    mService.readRemoteRSSI();
                }
            }
        }, 1000, 1000);
    }

    /**
     * Callback method that gives a list of available devices, the user can manage the connection here.
     *
     * @param devices List of available devices
     */
    @Override
    public void onListDevicesFound(ArrayList<BluetoothDevice> devices) {

        for (BluetoothDevice deviceItem : devices) {

            String name = deviceItem.getName();

            if (name != null) {

                if (name.toLowerCase().contains(kMySignalsId)) {

                    Log.d("DEBUG", "Address: " + name);

                    this.selectedDevice = deviceItem;

                    break;
                }
            }
        }

        if (selectedDevice != null) {

            bluetoothManager.stopLeScan();

            boolean bonded = mService.startBonding(selectedDevice);

            if (bonded) {

                Log.d("DEBUG", "Bonding starting...");
            }
        }
    }

    /**
     * Creates a new connection for current device
     */
    private void performConnection() {

        final Handler handler = new Handler();

        final Runnable postExecution = new Runnable() {

            @Override
            public void run() {

                try {

                    if (mService != null) {

                        if (mService.discoverServices()) {

                            Log.d("DEBUG", "Device discoverServices: " + selectedDevice.getAddress());
                        }
                    }
                } catch (Exception e) {

                }
            }
        };

        if (mService.connectToDevice(selectedDevice, MyService.this)) {

            Log.d("DEBUG", "Device connected!!");

            handler.postDelayed(postExecution, 2000);
        }
    }

    /**
     * Callback method when the mobile phone does not find devices to connect
     */
    @Override
    public void onManagerDidNotFoundDevices() {

        Log.d("DEBUG", "Device MySignals not found!!!");
    }

    /**
     * Callback method to controls boning authentication errors.
     *
     * @param gatt Bluetooth manager
     */
    @Override
    public void onBondAuthenticationError(BluetoothGatt gatt) {

        Log.d("DEBUG", "Bonding authentication error!!!");
    }

    /**
     * Callback for successfully bonded device
     */
    @Override
    public void onBonded() {

        performConnection();
    }

    /**
     * Callback for failed bonding.
     */
    @Override
    public void onBondedFailed() {

        Log.d("DEBUG", "Bonded failed!!!");
    }

    /**
     * This method handle the callback for a connection between mobile phone and MySignals.
     *
     * @param device Device connected
     * @param status Connection status
     */
    @Override
    public void onConnectedToDevice(BluetoothDevice device, int status) {

        Log.d("DEBUG", "Device connected!!");
    }

    /**
     * Callback method for discovered services, this method gives an array of available services.
     *
     * @param services
     */
    @Override
    public void onServicesFound(List<BluetoothGattService> services) {

        if (services != null) {

            selectedService = null;

            for (BluetoothGattService service : services) {

                String uuidService = service.getUuid().toString().toUpperCase();

                if (uuidService.equals(StringConstants.kServiceMainUUID)) {

                    selectedService = service;
                    break;
                }
            }

            if (selectedService != null) {

                writtenService = false;
                mService.readCharacteristicsForService(selectedService);
            }
        }
    }

    /**
     * Disconnection callback method, this method tracks disconnection process.
     *
     * @param device Disconnected device
     * @param newState Connection state
     */
    @Override
    public void onDisconnectFromDevice(BluetoothDevice device, int newState) {

        Log.d("DEBUG", "Device disconnected!!");
    }

    /**
     * Read remote RSSI power, the measures is given in dBm.
     *
     * @param rssi Power signal value
     * @param status Connection status
     */
    @Override
    public void onReadRemoteRssi(int rssi, int status) {

        Log.d("DEBUG", "RSSI: " + rssi + " dBm - Status: " + status);
    }

    /**
     * Callback method to retrieve characteristics from discovered service.
     *
     * @param characteristics Array of characteristics discovered
     * @param service Service used to discover characteristics
     */
    @Override
    public void onCharacteristicsFound(List<BluetoothGattCharacteristic> characteristics, BluetoothGattService service) {

        if (service.getUuid().toString().toUpperCase().equals(StringConstants.kServiceMainUUID)) {

            if (!writtenService) {

                characteristicSensorList = null;
                writtenService = true;

                for (BluetoothGattCharacteristic characteristic : characteristics) {

                    String uuid = characteristic.getUuid().toString().toUpperCase();

                    if (characteristic.getUuid().toString().toUpperCase().equals(StringConstants.kSensorList)) {

                        Log.d("DEBUG", "characteristic: " + uuid);
                        Log.d("DEBUG", "characteristic uuid: " + characteristic.getUuid().toString().toUpperCase());
                        Log.d("DEBUG", "characteristic getWriteType: " + characteristic.getWriteType());

                        characteristicSensorList = characteristic;

                        break;
                    }
                }

                if (characteristicSensorList != null) {

                    BitManager bitManager = BitManager.newObject();
                    bitManager.objectByte = BitManager.createByteObjectFromSensors(selectedSensors, BLUETOOTH_DISPLAY_MODE.BLUETOOTH_DISPLAY_MODE_GENERAL, this);

                    byte[] data = BitManager.convertToData(bitManager.objectByte);

                    String dataString = data.toString();
                    String hexByte = Utils.toHexString(data);

                    Log.d("DEBUG", "hex dataString value: " + hexByte);
                    Log.d("DEBUG", "dataString: " + dataString);

                    mService.writeCharacteristicQueue(characteristicSensorList, data);

                    Log.d("DEBUG", "Writting characteristic: " + characteristicSensorList.getUuid().toString().toUpperCase());
                }
            }
        }
    }

    /**
     * Callback result for write operation.
     *
     * @param characteristic Characteristic to write onto
     * @param status Write operation status
     */
    @Override
    public void onCharacteristicWritten(BluetoothGattCharacteristic characteristic, int status) {

    }

    /**
     * Track changes on characteristics when subscribe for notification. This method gives the notified characteristic, the user can get new value for this notification.
     *
     * @param characteristic Notified characteristic
     */
    @Override
    public void onCharacteristicChanged(BluetoothGattCharacteristic characteristic) {

        readCharacteristic(characteristic);
    }

    /**
     * Callback result for subscription operation.
     *
     * @param characteristic Characteristic for notification
     * @param isUnsubscribed Subscription operation status
     */
    @Override
    public void onCharacteristicSubscribed(BluetoothGattCharacteristic characteristic, boolean isUnsubscribed) {

        if (isUnsubscribed) {

            Log.d("DEBUG", "unsubscribed from characteristic!!");
        } else {

            Log.d("DEBUG", "subscribed to characteristic!!");
        }
    }

    /**
     * Callback method to notify the end of writing operations.
     */
    @Override
    public void onFinishWriteAllCharacteristics() {

    }

    /**
     * Callback method to notify the start of writing operations.
     *
     * @param characteristic Characteristics written
     * @param status Writing status
     */
    @Override
    public void onStartWriteCharacteristic(BluetoothGattCharacteristic characteristic, int status) {

        if (status != BluetoothGatt.GATT_SUCCESS) {

            Log.d("DEBUG", "writing characteristic error: " + status + " - " + characteristic.getUuid().toString().toUpperCase());
        } else {

            String uuid = characteristic.getService().getUuid().toString().toUpperCase();

            if (uuid.equals(StringConstants.kServiceMainUUID)) {

                Log.d("DEBUG", "pasa aquiasdadds");

                for (BluetoothGattCharacteristic charac : notifyCharacteristics) {

                    mService.writeCharacteristicSubscription(charac, false);
                }

                notifyCharacteristics.clear();

                for (BluetoothGattCharacteristic charac : selectedService.getCharacteristics()) {

                    for (LBSensorObject sensor : selectedSensors) {

                        if (sensor.uuidString.toUpperCase().equals(charac.getUuid().toString().toUpperCase()) && sensor.tickStatus) {

                            notifyCharacteristics.add(charac);

                            mService.writeCharacteristicSubscription(charac, true);
                        }
                    }
                }
            }
        }
    }

    /**
     * Callback method to notify the start of reading operations.
     * @param characteristic
     */
    @Override
    public void onStartReadCharacteristic(BluetoothGattCharacteristic characteristic) {

    }

    /**
     * Callback method to notify the use when the writing operations begin over descriptors.
     *
     * @param descriptor Description written
     */
    @Override
    public void onStartWriteQueueDescriptor(BluetoothGattDescriptor descriptor) {

    }

    /**
     * Callback to let the user know that writing operations have finished.
     */
    @Override
    public void onFinishWriteAllDescriptors() {

        if (characteristicSensorList != null) {

            BitManager bitManager = BitManager.newObject();
            bitManager.objectByte = BitManager.createByteObjectFromSensors(selectedSensors, BLUETOOTH_DISPLAY_MODE.BLUETOOTH_DISPLAY_MODE_GENERAL, this);

            byte[] data = BitManager.convertToData(bitManager.objectByte);

            String dataString = data.toString();
            String hexByte = Utils.toHexString(data);

            Log.d("DEBUG", "hex dataString value: " + hexByte);
            Log.d("DEBUG", "dataString: " + dataString);

            mService.writeCharacteristicQueue(characteristicSensorList, data);

            Log.d("DEBUG", "Writting characteristic: " + characteristicSensorList.getUuid().toString().toUpperCase());
        }
    }

    /**
     * Callback to let the user know that reading operations have finished.
     */
    @Override
    public void onFinishReadAllCharacteristics() {

    }

    /**
     * Method that gives the most recent notified characteristic.
     *
     * @param characteristic Characteristic changed
     */
    @Override
    public void onCharacteristicChangedQueue(BluetoothGattCharacteristic characteristic) {

    }

    /**
     * Manages and parse values from notified characteristic.
     *
     * @param characteristic Notified characteristic
     */
    private void readCharacteristic(BluetoothGattCharacteristic characteristic) {

        try {

            String uuid = characteristic.getUuid().toString().toUpperCase();

            byte[] value = characteristic.getValue();

            if (value == null) {

                return;
            }

            if (uuid.equals(StringConstants.kUUIDBodyPositionSensor)) {

                HashMap<String, String> dataDict = LBValueConverter.manageValuePosition(value);

                Log.d("DEBUG", "kUUIDBodyPositionSensor dict: " + dataDict);
            }

            if (uuid.equals(StringConstants.kUUIDTemperatureSensor)) {

                HashMap<String, String> dataDict = LBValueConverter.manageValueTemperature(value);

                Log.d("DEBUG", "kUUIDTemperatureSensor dict: " + dataDict);
            }

            if (uuid.equals(StringConstants.kUUIDEMGSensor)) {

                HashMap<String, String> dataDict = LBValueConverter.manageValueElectromyography(value);

                Log.d("DEBUG", "kUUIDEMGSensor dict: " + dataDict);
            }

            if (uuid.equals(StringConstants.kUUIDECGSensor)) {

                HashMap<String, String> dataDict = LBValueConverter.manageValueElectrocardiography(value);

                Log.d("DEBUG", "kUUIDECGSensor dict: " + dataDict);
            }

            if (uuid.equals(StringConstants.kUUIDAirflowSensor)) {

                HashMap<String, String> dataDict = LBValueConverter.manageValueAirflow(value);

                Log.d("DEBUG", "kUUIDAirflowSensor dict: " + dataDict);
                airflowData = dataDict.get("1");
                Log.d("DEBUG", "Air Flow Data:  " + dataDict.get("1"));
            }

            if (uuid.equals(StringConstants.kUUIDGSRSensor)) {

                HashMap<String, String> dataDict = LBValueConverter.manageValueGSR(value);

                Log.d("DEBUG", "kUUIDGSRSensor dict: " + dataDict);
            }

            if (uuid.equals(StringConstants.kUUIDBloodPressureSensor) || uuid.equals(StringConstants.kUUIDBloodPressureBLESensor)) {

                HashMap<String, String> dataDict = LBValueConverter.manageValueBloodPressure(value);

                if (uuid.equals(StringConstants.kUUIDBloodPressureSensor)) {

                    Log.d("DEBUG", "kUUIDBloodPressureSensor dict: " + dataDict);
                }

                if (uuid.equals(StringConstants.kUUIDBloodPressureBLESensor)) {

                    Log.d("DEBUG", "kUUIDBloodPressureBLESensor dict: " + dataDict);
                }
            }

            if (uuid.equals(StringConstants.kUUIDPulsiOximeterSensor) || uuid.equals(StringConstants.kUUIDPulsiOximeterBLESensor)) {

                HashMap<String, String> dataDict = LBValueConverter.manageValuePulsiOximeter(value);

                if (uuid.equals(StringConstants.kUUIDPulsiOximeterSensor)) {

                    Log.d("DEBUG", "kUUIDPulsiOximeterSensor dict: " + dataDict);
                }

                if (uuid.equals(StringConstants.kUUIDPulsiOximeterBLESensor)) {

                    Log.d("DEBUG", "kUUIDPulsiOximeterBLESensor dict: " + dataDict);
                }
            }

            if (uuid.equals(StringConstants.kUUIDGlucometerSensor) || uuid.equals(StringConstants.kUUIDGlucometerBLESensor)) {

                HashMap<String, String> dataDict = LBValueConverter.manageValueGlucometer(value);

                if (uuid.equals(StringConstants.kUUIDGlucometerSensor)) {

                    Log.d("DEBUG", "kUUIDGlucometerSensor dict: " + dataDict);
                } else {

                    Log.d("DEBUG", "kUUIDGlucometerBLESensor dict: " + dataDict);
                }
            }

            if (uuid.equals(StringConstants.kUUIDSpirometerSensor)) {

                HashMap<String, String> dataDict = LBValueConverter.manageValueSpirometer(value);

                Log.d("DEBUG", "kUUIDSpirometerSensor dict: " + dataDict);
            }

            if (uuid.equals(StringConstants.kUUIDSnoreSensor)) {

                HashMap<String, String> dataDict = LBValueConverter.manageValueSnore(value);

                Log.d("DEBUG", "kUUIDSnoreSensor dict: " + dataDict);
            }

            if (uuid.equals(StringConstants.kUUIDScaleBLESensor)) {

                HashMap<String, String> dataDict = LBValueConverter.manageValueScale(value);

                Log.d("DEBUG", "kUUIDScaleBLESensor dict: " + dataDict);
            }

            if (uuid.equals(StringConstants.kUUIDEEGSensor)) {

                HashMap<String, String> dataDict = LBValueConverter.manageValueElectroencephalography(value);

                Log.d("DEBUG", "kUUIDEEGSensor dict: " + dataDict);
            }

        } catch (Exception e) {

        }
    }

    @Override
    public void onDestroy() {

        if (timerRssi != null) {

            timerRssi.cancel();
            timerRssi.purge();
            timerRssi = null;
        }

        try {

            mService.unregisterBondNotification();
        } catch (Exception e1) {

        }

        try {

            if (mService != null) {

                mService.disconnectDevice();
                mService.serviceDestroy();
                mService.close();
                mService = null;
            }
        } catch (Exception e) {

        }
        handler.removeCallbacks(sendData);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}