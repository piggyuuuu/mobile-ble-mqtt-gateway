package com.have_no_eyes_deer.bleawsgateway.ble;

import com.have_no_eyes_deer.bleawsgateway.model.BleDataModel;

/**
 * BLE Data Listener
 */
public interface BleDataListener {
    
    /**
     * when new BLE data is received
     * @param data BLE data model
     */
    void onDataReceived(BleDataModel data);
    
    /**
     * when BLE device connection state changes
     * @param deviceAddress 
     * @param isConnected 
     * @param deviceName (optional)
     */
    void onConnectionStateChanged(String deviceAddress, boolean isConnected, String deviceName);
    
    /**
     * when BLE operation fails
     * @param error
     * @param deviceAddress related device address(optional)
     */
    void onError(String error, String deviceAddress);
    
    /**
     * when data is sent to BLE device successfully
     * @param deviceAddress
     * @param data
     */
    void onDataSent(String deviceAddress, byte[] data);
} 