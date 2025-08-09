# BLE-AWS-Gateway

A comprehensive Android application that serves as a bridge between Bluetooth Low Energy (BLE) devices and AWS IoT, enabling real-time sensor data collection, processing, and cloud transmission.

## 🚀 Features

- **BLE Device Scanning & Connection**: Discover and connect to nearby BLE devices
- **Real-time Data Reception**: Receive sensor data with multiple format support (UTF-8, Hexadecimal, Byte Array)
- **AWS IoT Integration**: Seamless data forwarding to AWS IoT Core with X.509 certificate authentication
- **Temperature Data Processing**: Automatic recognition and parsing of temperature sensor data
- **Flexible Logging**: Toggle between detailed and simple logging modes
- **Data Testing**: Built-in BLE data sending capabilities for testing purposes
- **Modular Architecture**: Separated BLE and MQTT modules for team collaboration

## 🏗️ Architecture

### System Overview
```
BLE Device ←→ Android Gateway ←→ AWS IoT Core ←→ Cloud Services
```

### Component Architecture
```
┌─────────────────────────────────────────┐
│              MainActivity               │
│         (Main Coordinator)              │
└─────────────┬───────────────────────────┘
              │
    ┌─────────┴─────────┐
    │                   │
┌───▼────┐         ┌────▼────┐
│   BLE  │         │  MQTT   │
│ Module │         │ Module  │
└────────┘         └─────────┘
```

### Data Flow
```
BLE Device → BleManager → BleDataListener → MqttDataSender → AWS IoT
     ↑                                                           ↓
Temperature Sensor                                        Cloud Analytics
```

## 🛠️ Technology Stack

### Android Development
- **Language**: Java
- **Minimum SDK**: Android 7.0 (API 24)
- **Target SDK**: Android 14 (API 34)
- **Build Tool**: Gradle 8.7

### Bluetooth Technologies
- **BLE (Bluetooth Low Energy)**: Core communication protocol
- **GATT (Generic Attribute Profile)**: Data exchange protocol
- **Android Bluetooth API**: Native Android BLE implementation

### AWS Technologies
- **AWS IoT Core**: Cloud messaging and device management
- **AWS IoT SDK for Android**: MQTT client implementation
- **X.509 Certificates**: Device authentication
- **MQTT Protocol**: Message transmission (QoS 0)

### Key Libraries
```gradle
dependencies {
    implementation 'com.amazonaws:aws-android-sdk-iot:2.46.0'
    implementation 'com.amazonaws:aws-android-sdk-mobile-client:2.16.12'
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'com.google.android.material:material:1.8.0'
}
```

## 📱 Application Structure

### Core Components

#### 1. MainActivity
- **Purpose**: Main coordinator managing UI and component interactions
- **Responsibilities**:
  - BLE device scanning and connection management
  - AWS IoT connection handling
  - UI state management and user interactions
  - Data format conversion and validation

#### 2. BLE Module
- **BleManager.java**: Handles BLE operations (scanning, connecting, data exchange)
- **BleDataListener.java**: Interface for BLE data events
- **BleDataModel.java**: Standardized data model for BLE information

#### 3. MQTT Module
- **MqttDataSender.java**: Interface for AWS IoT data transmission
- **AWSIotMqttManager**: AWS SDK implementation for MQTT operations

#### 4. UI Components
- **Device List**: Displays discovered BLE devices
- **Connection Status**: Shows BLE and AWS connection states
- **Data Input/Output**: Controls for data transmission and logging
- **Settings**: AWS IoT configuration interface

## 🔧 Setup and Installation

### Prerequisites
1. **Android Studio**: Latest stable version
2. **Android SDK**: API level 24 or higher
3. **AWS Account**: For IoT Core services
4. **BLE Device**: Compatible sensor device (e.g., temperature sensor)

### AWS IoT Setup

#### 1. Create IoT Thing
```bash
aws iot create-thing --thing-name "BLE-Gateway-Device"
```

#### 2. Generate Certificates
```bash
aws iot create-keys-and-certificate \
    --set-as-active \
    --certificate-pem-outfile certificate.pem.crt \
    --private-key-outfile private.pem.key \
    --public-key-outfile public.pem.key
```

#### 3. Create IoT Policy
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": ["iot:Connect"],
      "Resource": "*"
    },
    {
      "Effect": "Allow",
      "Action": ["iot:Publish"],
      "Resource": [
        "arn:aws:iot:*:*:topic/test/*",
        "arn:aws:iot:*:*:topic/devices/*"
      ]
    }
  ]
}
```

#### 4. Attach Policy to Certificate
```bash
aws iot attach-policy \
    --policy-name "BLE-Gateway-Policy" \
    --target "CERTIFICATE_ARN"
```

### Application Setup

#### 1. Clone Repository
```bash
git clone <repository-url>
cd mobile-ble-mqtt-gateway
```

#### 2. Configure AWS Credentials
1. Open the app and navigate to "AWS IoT Settings"
2. Enter your AWS IoT Endpoint
3. Select your certificate files:
   - **Key File**: `private.pem.key`
   - **Credentials File**: `certificate.pem.crt`

#### 3. Build and Install
```bash
cd BleAwsGateway
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

## 📖 Usage Guide

### Basic Workflow

#### 1. Device Setup
1. Launch the application
2. Configure AWS IoT settings with your credentials
3. Connect to AWS IoT (verify "Connected" status)

#### 2. BLE Connection
1. Tap "Scan BLE Devices" to discover nearby devices
2. Select your target device from the list
3. Device will automatically connect and enable notifications

#### 3. Data Collection
1. Toggle "Start Receiving" to enable data reception
2. Send commands to BLE device (e.g., 's' for temperature reading)
3. Monitor received data in the log area
4. Verify data forwarding to AWS IoT

### Data Formats

#### Supported Input/Output Formats
- **UTF-8 Text**: Human-readable string data
- **Hexadecimal**: Binary data as hex string (e.g., "41424344")
- **Byte Array**: Comma-separated bytes (e.g., "65,66,67,68")

#### Temperature Data Format
```
Input:  T1:23.5C
Output: {
  "device": "AA:BB:CC:DD:EE:FF",
  "deviceName": "Nano tron",
  "timestamp": "2025-01-19T06:22:25.332Z",
  "type": "temperature",
  "sampleNumber": 1,
  "temperature": 23.5,
  "unit": "C",
  "rawData": "T1:23.5C"
}
```

### MQTT Topics

#### Connection Messages
- **Topic**: `test/bleawsgateway`
- **Purpose**: Gateway connection status

#### Device Data
- **Topic**: `devices/{DEVICE_ADDRESS}/data`
- **Purpose**: Sensor data transmission
- **Example**: `devices/AABBCCDDEEFF/data`

### Logging Features

#### Simple Logging (Default)
- Shows essential events only
- Connection status updates
- Data transmission confirmations
- Error messages

#### Detailed Logging
- Complete debugging information
- Raw data inspection
- MQTT message content
- Processing step details

## 🔍 Troubleshooting

### Common Issues

#### BLE Connection Problems
- **Symptom**: Cannot discover or connect to devices
- **Solution**: 
  - Ensure Bluetooth and location permissions are granted
  - Verify target device is in pairing/advertising mode
  - Check device compatibility with BLE GATT

#### AWS IoT Connection Issues
- **Symptom**: "Reconnecting" status, authentication failures
- **Solutions**:
  - Verify certificate files are correctly formatted
  - Check endpoint URL format (must include `-ats`)
  - Ensure IoT Policy includes required permissions
  - Confirm certificates are active in AWS console

#### Data Forwarding Problems
- **Symptom**: BLE data received but not appearing in AWS
- **Solutions**:
  - Subscribe to `devices/#` topic in AWS IoT console
  - Verify IoT Policy includes `devices/*` publish permissions
  - Check detailed logs for MQTT send errors
  - Ensure network connectivity

### Diagnostic Tools

#### Built-in Diagnostics
1. **Diagnose Button**: Comprehensive connection testing
2. **Detailed Logging**: Step-by-step operation tracking
3. **Copy Log**: Share debugging information

#### AWS IoT Console Testing
1. Navigate to AWS IoT Console → Test
2. Subscribe to topics: `test/#` and `devices/#`
3. Verify message reception during app operation

## 🔒 Security Considerations

### Certificate Management
- Store certificates securely on device
- Use Android Keystore for certificate protection
- Regularly rotate certificates as per AWS recommendations

### Network Security
- All communication encrypted via TLS
- Certificate-based device authentication
- No hardcoded credentials in application

### Permissions
```xml
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

## 🚀 Future Enhancements

### Planned Features
- [ ] Multiple device connection support
- [ ] Data caching for offline scenarios
- [ ] Advanced data visualization
- [ ] Custom BLE service support
- [ ] Real-time dashboard integration

### Scalability Considerations
- Modular architecture supports easy feature additions
- Interface-based design enables component swapping
- Configurable data processing pipeline
- Cloud-native architecture for high availability

## 🤝 Contributing

### Development Guidelines
1. Follow modular architecture principles
2. Maintain interface separation between BLE and MQTT modules
3. Use centralized logging system
4. Implement comprehensive error handling
5. Document API changes and additions

### Code Style
- Java coding standards
- Meaningful variable and method names
- Comprehensive inline documentation
- Unit tests for critical functions

## 📄 License

This project is licensed under the MIT License - see the LICENSE file for details.

## 📞 Support

For technical support and questions:
- Create an issue in the repository
- Use the diagnostic tools for troubleshooting
- Refer to AWS IoT documentation for cloud-side issues

---

**Built with ❤️ for IoT and BLE enthusiasts**
