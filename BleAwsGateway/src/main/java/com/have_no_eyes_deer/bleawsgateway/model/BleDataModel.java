package com.have_no_eyes_deer.bleawsgateway.model;

import java.util.Date;

/**
 * BLE data model - used for data transfer between BLE module and MQTT module
 * This class defines standard data format to avoid coupling between modules
 */
public class BleDataModel {
    private String deviceAddress;     // BLE device address
    private String deviceName;        // BLE device name
    private String serviceUuid;       // Service UUID
    private String characteristicUuid; // Characteristic UUID
    private byte[] rawData;          // Raw data
    private String dataString;       // String format data
    private Date timestamp;          // Timestamp
    private DataType dataType;       // Data type

    public enum DataType {
        SENSOR_DATA,    // Sensor data
        COMMAND_RESPONSE, // Command response
        STATUS_UPDATE,  // Status update
        UNKNOWN         // Unknown type
    }

    // Constructor
    public BleDataModel(String deviceAddress, String deviceName, 
                       String serviceUuid, String characteristicUuid,
                       byte[] rawData, String dataString) {
        this.deviceAddress = deviceAddress;
        this.deviceName = deviceName;
        this.serviceUuid = serviceUuid;
        this.characteristicUuid = characteristicUuid;
        this.rawData = rawData;
        this.dataString = dataString;
        this.timestamp = new Date();
        this.dataType = detectDataType(dataString);
    }

    // Simplified constructor
    public BleDataModel(String deviceAddress, byte[] rawData, String dataString) {
        this(deviceAddress, "", "", "", rawData, dataString);
    }

    // Auto-detect data type
    private DataType detectDataType(String data) {
        if (data == null) return DataType.UNKNOWN;
        
        // Simple detection logic, can be enhanced
        if (data.contains("temperature") || data.contains("humidity") || 
            data.matches(".*\\d+\\.\\d+.*")) {
            return DataType.SENSOR_DATA;
        } else if (data.startsWith("OK") || data.startsWith("ERROR")) {
            return DataType.COMMAND_RESPONSE;
        } else if (data.contains("status") || data.contains("connected") || 
                  data.contains("disconnected")) {
            return DataType.STATUS_UPDATE;
        }
        return DataType.UNKNOWN;
    }

    // Getters and Setters
    public String getDeviceAddress() { return deviceAddress; }
    public void setDeviceAddress(String deviceAddress) { this.deviceAddress = deviceAddress; }

    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }

    public String getServiceUuid() { return serviceUuid; }
    public void setServiceUuid(String serviceUuid) { this.serviceUuid = serviceUuid; }

    public String getCharacteristicUuid() { return characteristicUuid; }
    public void setCharacteristicUuid(String characteristicUuid) { this.characteristicUuid = characteristicUuid; }

    public byte[] getRawData() { return rawData; }
    public void setRawData(byte[] rawData) { this.rawData = rawData; }

    public String getDataString() { return dataString; }
    public void setDataString(String dataString) { this.dataString = dataString; }

    public Date getTimestamp() { return timestamp; }
    public void setTimestamp(Date timestamp) { this.timestamp = timestamp; }

    public DataType getDataType() { return dataType; }
    public void setDataType(DataType dataType) { this.dataType = dataType; }

    @Override
    public String toString() {
        return String.format("BleDataModel{device='%s', data='%s', type=%s, time=%s}",
                deviceAddress, dataString, dataType, timestamp);
    }
}