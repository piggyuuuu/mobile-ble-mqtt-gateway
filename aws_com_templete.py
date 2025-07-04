import time as t
import json
import AWSIoTPythonSDK.MQTTLib as AWSIoTpyMQTT
# Client configuration with endpoint and credentials
myClient = AWSIoTpyMQTT.AWSIoTMQTTClient("testDevice")
myClient.configureEndpoint('a64x80liluglf-ats.iot.ap-southeast-2.amazonaws.com',8883)
myClient.configureCredentials("AmazonRootCA1.pem","3fe167be7a5bc017f4e7ecaf73b845337dc8997a6249b71cb44c11bc84f4fada-private.pem.key",
                              "3fe167be7a5bc017f4e7ecaf73b845337dc8997a6249b71cb44c11bc84f4fada-certificate.pem.crt")
myClient.connect()
for i in range(10):
    message = str(i+1)
    myClient.publish("test/comp6733",message,1)
    print("Published: '"+ message + " to test/comp6733")
    t.sleep(0.5)
myClient.disconnect()