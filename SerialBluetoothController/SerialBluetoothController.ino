#include "BluetoothSerial.h"
#include "Switchable.h"
#include "VibrationMotor.h"
#include "Touchpad.h"

#include <Adafruit_MCP23X08.h>
#include <Wire.h>
#define SDA_PIN   21
#define SCL_PIN   22

#define MCP_PIN_SEARCH 0
#define MCP_PIN_CIRCLE 1
#define MCP_PIN_CROSS 4
#define MCP_PIN_TRIANGLE 3
#define MCP_PIN_RIGHT 2
#define MCP_PIN_SQUARE 5
#define MCP_PIN_START 6
#define MCP_PIN_MENU 7

#define MCP_PIN_BACK 0
#define MCP_PIN_DPAD_LEFT 1
#define MCP_PIN_LEFT 2
#define MCP_PIN_DPAD_DOWN 3
#define MCP_PIN_DPAD_UP 4
#define MCP_PIN_DPAD_RIGHT 5
#define MCP_PIN_SELECT 6
#define MCP_PIN_HOME 7

#define BUFFER_PIN_DPAD_UP 0
#define BUFFER_PIN_DPAD_DOWN 1
#define BUFFER_PIN_DPAD_LEFT 2
#define BUFFER_PIN_DPAD_RIGHT 3
#define BUFFER_PIN_BUTTON_CROSS 4
#define BUFFER_PIN_BUTTON_SQUARE 5
#define BUFFER_PIN_BUTTON_TRIANGLE 6
#define BUFFER_PIN_BUTTON_CIRCLE 7
#define BUFFER_PIN_BUTTON_START 8
#define BUFFER_PIN_BUTTON_SELECT 9
#define BUFFER_PIN_SHOULDER_LEFT 10
#define BUFFER_PIN_SHOULDER_RIGHT 11
#define BUFFER_PIN_NAVIGATE_BACK 12
#define BUFFER_PIN_NAVIGATE_HOME 13
#define BUFFER_PIN_NAVIGATE_MENU 14
#define BUFFER_PIN_NAVIGATE_SEARCH 15


const int LED_PIN = 2; // built in LED is GPIO 2
const int VIBRATIONMOTOR_PIN = 15;

const unsigned long tickRate = 1000/120;  //100hz target

BluetoothSerial SerialBT;  // Create Bluetooth Serial object
VibrationMotor vibrationMotor(VIBRATIONMOTOR_PIN);
Touchpad* touchpad;
Adafruit_MCP23X08 mcpRight;
Adafruit_MCP23X08 mcpLeft;

unsigned long startTime = 0;
byte buffer[32] = {};
byte prevBuffer[32] = {};
bool connected = false;

bool rumble = false;
unsigned long rumbleTime = 0;
unsigned long rumbleDuration = 0;
unsigned long touchTime = 0;

void setup() {
  Serial.begin(115200);  // Optional: for debugging over USB
  SerialBT.begin("Xperia Play Controller");  // Bluetooth device name
  SerialBT.setTimeout(tickRate);
  Serial.println("Bluetooth Serial started. Waiting for connection...");
  pinMode(LED_PIN, OUTPUT); // Set LED pin as output

  //touchpad = new Touchpad(21, 22, 18, 19);

  int rightStatus = mcpRight.begin_I2C(0x20);
  Serial.printf("MCP right started status: %d\n", rightStatus);
  mcpRight.pinMode(MCP_PIN_SEARCH, INPUT_PULLUP);
  mcpRight.pinMode(MCP_PIN_CIRCLE, INPUT_PULLUP);
  mcpRight.pinMode(MCP_PIN_CROSS, INPUT_PULLUP);
  mcpRight.pinMode(MCP_PIN_TRIANGLE, INPUT_PULLUP);
  mcpRight.pinMode(MCP_PIN_RIGHT, INPUT_PULLUP);
  mcpRight.pinMode(MCP_PIN_SQUARE, INPUT_PULLUP);
  mcpRight.pinMode(MCP_PIN_START, INPUT_PULLUP);
  mcpRight.pinMode(MCP_PIN_MENU, INPUT_PULLUP);

  int leftStatus = mcpLeft.begin_I2C(0x27);
  Serial.printf("MCP left started status: %d\n", leftStatus);
  mcpLeft.pinMode(MCP_PIN_BACK, INPUT_PULLUP);
  mcpLeft.pinMode(MCP_PIN_DPAD_LEFT, INPUT_PULLUP);
  mcpLeft.pinMode(MCP_PIN_LEFT, INPUT_PULLUP);
  mcpLeft.pinMode(MCP_PIN_DPAD_DOWN, INPUT_PULLUP);
  mcpLeft.pinMode(MCP_PIN_DPAD_UP, INPUT_PULLUP);
  mcpLeft.pinMode(MCP_PIN_DPAD_RIGHT, INPUT_PULLUP);
  mcpLeft.pinMode(MCP_PIN_SELECT, INPUT_PULLUP);
  mcpLeft.pinMode(MCP_PIN_HOME, INPUT_PULLUP);

  for(int i=0;i<32;i++)
  {
    buffer[i] = 0;
    prevBuffer[i] = 0;
  }
}



void loop() {
  unsigned long currentTime = millis();

  if(currentTime-startTime>=tickRate)
  {
    startTime = currentTime;

    if (SerialBT.hasClient()) {
      if(!connected)  //Rumble on connect
      {
        connected = true;
        vibrationMotor.on();     // 1. turns on
        delay(250);           // 2. waits 500ms (0.5 sec). change the value in the brackets (500) for a longer or shorter delay in milliseconds.
        vibrationMotor.off();    // 3. turns off
        delay(125);           // 2. waits 500ms (0.5 sec). change the value in the brackets (500) for a longer or shorter delay in milliseconds.
        vibrationMotor.on();     // 1. turns on
        delay(250);           // 2. waits 500ms (0.5 sec). change the value in the brackets (500) for a longer or shorter delay in milliseconds.
        vibrationMotor.off();    // 3. turns off
        Serial.println("Connected!");
      }

      //Received packet
      if(SerialBT.available())
      {
        byte msg[512];
        SerialBT.readBytes(msg, 512);

        rumble = true;
        rumbleTime = millis();
        
        rumbleDuration = ((unsigned long)msg[0] << 24) | 
         ((unsigned long)msg[1] << 16) |
         ((unsigned long)msg[2] << 8) |
         (unsigned long)msg[3]; 
        

        vibrationMotor.on();
        String string_to_print = String(rumbleDuration);
        Serial.println("Received message, turned on rumble for: "+string_to_print);
        Serial.println(rumbleTime);
      }

      if(rumble && ((millis()-rumbleTime)>=rumbleDuration))
      {
        rumble = false;
        rumbleTime = 0;
        rumbleDuration = 0;
        vibrationMotor.off();
        Serial.println("Rumble time expired");
      }

      buffer[BUFFER_PIN_NAVIGATE_SEARCH] = !mcpRight.digitalRead(MCP_PIN_SEARCH);
      buffer[BUFFER_PIN_BUTTON_CIRCLE]   = !mcpRight.digitalRead(MCP_PIN_CIRCLE);
      buffer[BUFFER_PIN_BUTTON_CROSS]    = !mcpRight.digitalRead(MCP_PIN_CROSS);
      buffer[BUFFER_PIN_BUTTON_TRIANGLE] = !mcpRight.digitalRead(MCP_PIN_TRIANGLE);
      buffer[BUFFER_PIN_SHOULDER_RIGHT]  = !mcpRight.digitalRead(MCP_PIN_RIGHT);
      buffer[BUFFER_PIN_BUTTON_SQUARE]   = !mcpRight.digitalRead(MCP_PIN_SQUARE);
      buffer[BUFFER_PIN_BUTTON_START]    = !mcpRight.digitalRead(MCP_PIN_START);
      buffer[BUFFER_PIN_NAVIGATE_MENU]   = !mcpRight.digitalRead(MCP_PIN_MENU);

      buffer[BUFFER_PIN_NAVIGATE_BACK] = !mcpLeft.digitalRead(MCP_PIN_BACK);
      buffer[BUFFER_PIN_DPAD_LEFT]     = !mcpLeft.digitalRead(MCP_PIN_DPAD_LEFT);
      buffer[BUFFER_PIN_SHOULDER_LEFT] = !mcpLeft.digitalRead(MCP_PIN_LEFT);
      buffer[BUFFER_PIN_DPAD_DOWN]     = !mcpLeft.digitalRead(MCP_PIN_DPAD_DOWN);
      buffer[BUFFER_PIN_DPAD_UP]       = !mcpLeft.digitalRead(MCP_PIN_DPAD_UP);
      buffer[BUFFER_PIN_DPAD_RIGHT]    = !mcpLeft.digitalRead(MCP_PIN_DPAD_RIGHT);
      buffer[BUFFER_PIN_BUTTON_SELECT] = !mcpLeft.digitalRead(MCP_PIN_SELECT);
      buffer[BUFFER_PIN_NAVIGATE_HOME] = !mcpLeft.digitalRead(MCP_PIN_HOME);

      for(int i=0;i<32;i++)
      {
        if(buffer[i] && buffer[i]!=prevBuffer[i])
          Serial.println("Button down: "+String(i));

         prevBuffer[i] = buffer[i]; 
      }

      /*if(touchpad->GetTouch())
      {
        buffer[16] = 1;
        buffer[17] = touchpad->GetTouchX()&0xFF;
        buffer[18] = (touchpad->GetTouchX()&0xFF00)>>8;
        buffer[19] = touchpad->GetTouchY()&0xFF;
        buffer[20] = (touchpad->GetTouchY()&0xFF00)>>8;

        Serial.printf("Touchpad X: %d Y: %d\n", touchpad->GetTouchX(), touchpad->GetTouchY());
      }
      else
      {
        buffer[16] = 0;
      }*/

      /*if(currentTime-touchTime>=1000)
      {
        touchTime = currentTime;
        uint16_t xt = 480;
        uint16_t yt = 470;

        buffer[16] = 1;
        buffer[17] = xt&0xFF;
        buffer[18] = (xt&0xFF00)>>8;
        buffer[19] = yt&0xFF;
        buffer[20] = (yt&0xFF00)>>8;
      }*/
      
      
      SerialBT.write(buffer, sizeof(buffer));
    }
    else
    {
      connected = false;  //Reset connection flag
      vibrationMotor.off();
      digitalWrite(LED_PIN, LOW);  // Turn LED off
      delay(500);  // Wait 100 millisecond (10hz)
      digitalWrite(LED_PIN, HIGH); // Turn LED on
      delay(500);                  // Wait 500ms
    }
  }
}