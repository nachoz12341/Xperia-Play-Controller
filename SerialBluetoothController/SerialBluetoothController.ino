#include "BluetoothSerial.h"

BluetoothSerial SerialBT;  // Create Bluetooth Serial object
byte buffer[18];
const int LED_PIN = 2; // On most ESP32 dev boards, built-in LED is on GPIO 2

void setup() {
  Serial.begin(115200);  // Optional: for debugging over USB
  SerialBT.begin("ESP32_BT_SENDER");  // Bluetooth device name
  Serial.println("Bluetooth Serial started. Waiting for connection...");
  pinMode(LED_PIN, OUTPUT); // Set LED pin as output
  
  for(int i=0;i<18;i++)
    buffer[i] = 0;
}

void loop() {
  
  if (SerialBT.hasClient()) {
    buffer[0] = !buffer[0];
    buffer[4]= !buffer[4];
    SerialBT.write(buffer, sizeof(buffer));
    //Serial.println("Sent over BT");
    delay(1000);  // Wait 100 millisecond (10hz)
  }
  else
  {
    digitalWrite(LED_PIN, LOW);  // Turn LED off
    delay(500);  // Wait 100 millisecond (10hz)
    digitalWrite(LED_PIN, HIGH); // Turn LED on
    delay(500);                  // Wait 500ms
  }
}
