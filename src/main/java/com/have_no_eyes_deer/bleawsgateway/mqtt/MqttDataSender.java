package com.have_no_eyes_deer.bleawsgateway.mqtt;

import com.have_no_eyes_deer.bleawsgateway.model.BleDataModel;

/**
 * MQTT data sender interface
 */
public interface MqttDataSender {
    
    /**
     * send BLE data to MQTT topic
     * @param data BLE data model
     * @param topic MQTT topic
     * @return 
     */
    boolean sendData(BleDataModel data, String topic);
    
    /**
     * send raw string data
     * @param message message content
     * @param topic MQTT topic
     * @return 
     */
    boolean sendMessage(String message, String topic);
    
    /**
     * batch send data
     * @param dataList data list
     * @param topic MQTT topic
     * @return number of successful sends
     */
    int sendBatchData(java.util.List<BleDataModel> dataList, String topic);
    
    /**
     * check MQTT connection status
     * @return 
     */
    boolean isConnected();
    
    /**
     * connect to MQTT server
     * @return 
     */
    boolean connect();
    
    /**
     * disconnect from MQTT
     */
    void disconnect();
    
    /**
     * set MQTT status listener
     * @param listener status listener
     */
    void setStatusListener(MqttStatusListener listener);
    
    /**
     * MQTT status listener interface
     */
    interface MqttStatusListener {
        void onConnected();
        void onDisconnected();
        void onMessageSent(String topic, String message);
        void onError(String error);
    }
} 