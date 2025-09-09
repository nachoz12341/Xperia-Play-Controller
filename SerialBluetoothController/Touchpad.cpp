#include<Arduino.h>
#include "Touchpad.h"

Touchpad::Touchpad(int SDA, int SDL, int INT, int RST)
{
  uint16_t xpos = 0;
  uint16_t ypos = 0;
  bool touch = false;

  mxt_hard_reset(RST);
  delay(1000);
  Wire.begin(SDA, SCL, 400000);
  
  MXT_INT = INT;
  pinMode(MXT_INT, INPUT_PULLUP);
  delay(1000);
  mxt_identify();  
}

uint16_t Touchpad::GetTouchX()
{
  return xpos;
} 

uint16_t Touchpad::GetTouchY()
{
  return ypos;
} 

bool Touchpad::GetTouch()
{
  mxt_report_t9();
  return touch;
} 

void Touchpad::mxt_identify() 
{
  mxt_read(0x0000, MXT_BUF, MXT_ID_BLK_SIZ);

  num_obj = MXT_BUF[6];// save the object table elements count.

  /*Serial.print("Family ID 0x");
  Serial.println(MXT_BUF[0], HEX);
  Serial.print("Variant ID 0x");
  Serial.println(MXT_BUF[1], HEX);
  Serial.print("Version ");
  Serial.print((uint8_t)((MXT_BUF[2] & 0xF0) >> 4), DEC);
  Serial.print(".");
  Serial.println((uint8_t)(MXT_BUF[2] & 0x0F), DEC);
  Serial.print("Build Number 0x");
  Serial.println(MXT_BUF[3], HEX);

  Serial.print("X channels :");
  Serial.println(MXT_BUF[4], DEC);
  Serial.print("Y channels :");
  Serial.println(MXT_BUF[5], DEC);

  Serial.print("OJB table elements count");
  Serial.println(num_obj, DEC);*/

  // maXTouch can have many object. But we specifically looking for T5 A.K.A Message Processor
  // and T100 A.K.A Multitouch
  //
  // maXTouch can have many object. But we specifically looking for T5 A.K.A Message Processor
  // and T9 Multitouch object
 
  for (uint8_t i = 0; i < num_obj; i ++) 
  {
    mxt_read(obj_addr_cnt, MXT_BUF, MXT_ID_BLK_SIZ - 1);

    ///Serial.printf("OBJ: %u Case:%u \n",i, MXT_BUF[0]);

    switch (MXT_BUF[0]) {
      case 5:// T5 message processor.
        ///Serial.print("Found T5 object\n");
        T5_addr = (MXT_BUF[2] << 8) | MXT_BUF[1];
        ///Serial.print("T5 object address : 0x");
        ///Serial.println(T5_addr, HEX);
        break;

      case 7:// T7 power config stuffs.
        ///Serial.print("Found T7 Object\n");
        T7_addr = (MXT_BUF[2] << 8) | MXT_BUF[1];
        break;

      case 9:// T9 Multi touch (Assuming the maXTouch S series have this instead of T100 from U series).
        ///Serial.print("Found T9 object\n");
        T9_addr = (MXT_BUF[2] << 8) | MXT_BUF[1];
        ///Serial.print("T9 object address : 0x");
        ///Serial.println(T9_addr, HEX);

        // TODO : properly build report ID map.
        //T9_report_id_min = MXT_BUF[5];
        //T9_report_id_max = MXT_BUF[5] + MXT_BUF[4]; 
        ///Serial.printf("T9 MIN: %u T9 MAX: %u\n",MXT_BUF[5], MXT_BUF[5]+MXT_BUF[4]);
        break;

      case 44:
        ///Serial.print("Found T44 object\n");
        T44_addr = (MXT_BUF[2] << 8) | MXT_BUF[1];
        ///Serial.print("T44 object address : 0x");
        ///Serial.println(T44_addr, HEX);
        break;

    }
    obj_addr_cnt += 6;// move on to next object.
  }

  // Dummy read CRC. We don't actually use it.
  mxt_read(obj_addr_cnt, MXT_BUF, 3);

  // Get X,Y range
  mxt_read(T9_addr + 18, MXT_BUF, 4);
  Serial.print("X range : ");
  Serial.println((MXT_BUF[1] << 8) | MXT_BUF[0], DEC);
  Serial.print("Y range : ");
  Serial.println((MXT_BUF[3] << 8) | MXT_BUF[2], DEC);

  // Set T7 into Free-running mode
  mxt_read(T7_addr, MXT_BUF, 3);
  MXT_BUF[0] = 255;
  MXT_BUF[1] = 255;
  mxt_write(T7_addr, MXT_BUF, 3);
}

void Touchpad::mxt_hard_reset(int RST)
{
  pinMode(RST, OUTPUT);
  digitalWrite(RST, HIGH); delay(5);
  digitalWrite(RST, LOW);  delay(10);
  digitalWrite(RST, HIGH); delay(200);
}

void Touchpad::mxt_read(uint16_t addr, uint8_t *data, uint16_t len) 
{

  Wire.beginTransmission(MXT_ADDR);// Begin TX
  Wire.write((uint8_t)addr);// Write LSB byte address to MXT112S.
  Wire.write((uint8_t)(addr >> 8));// Write MSB byte address to MXT112S.

  Wire.endTransmission();

  Wire.requestFrom(MXT_ADDR, len);// Begin RX
  while (Wire.available() && len--) { //
    *data++ = Wire.read();
  }

}

// Write data to MXT112S
void Touchpad::mxt_write(uint16_t addr, uint8_t *data, uint8_t len) 
{

  Wire.beginTransmission(MXT_ADDR);// Begin tx
  Wire.write((uint8_t)addr);// Write LSB byte address to MXT112S.
  Wire.write((uint8_t)(addr >> 8));// Write MSB byte address to MXT112S.

  while (len--)
    Wire.write(*data++);

  Wire.endTransmission();// End tx

}

// Report Multi touch from T9 object.
// But data is read from T5 message object.
void Touchpad::mxt_report_t9()
{
  touch = false;
  if(digitalRead(MXT_INT) == HIGH) return;

  mxt_read(T5_addr, MXT_BUF, 16);
  //Serial.write(0x0C);// clear terminal
  uint8_t rid  = MXT_BUF[0];

  if (rid == 0xFF ) return;
  uint8_t amplitude =  MXT_BUF[6];
  // Convert 2 bytes to 16bit uint X and Y position 
  xpos = (MXT_BUF[2] << 4) | ((MXT_BUF[4] >> 4) & 0x0F);
  ypos = (MXT_BUF[3] << 2) | (MXT_BUF[4] & 0x0F);

  

  uint8_t tid   = MXT_BUF[8] & 0x0F;   // tracking id (which finger)
  uint8_t stat = MXT_BUF[1];
  bool detect  = stat & 0x80;
  bool press   = stat & 0x40;
  bool release = stat & 0x20;
  bool move    = stat & 0x10;
  uint8_t slot = rid - T9_report_id_min;

 
  touch = true;

    Serial.print("X pos : ");
    Serial.print(xpos, DEC);
    Serial.print(" Y pos : ");
    Serial.print(ypos, DEC);
    Serial.print(" Pressure : ");
    Serial.print(amplitude, DEC); 
    Serial.print(" Touch id : ");
    Serial.println(tid, DEC);

    /*Serial.printf("slot=%u tid=%u %s%s%s x=%u y=%u amp=%u\n",
        slot, tid,
        press ? "PRESS " : "",
        move  ? "MOVE "  : "",
        release ? "RELEASE " : "",
        xpos, ypos, amplitude);

    if(!press && !move && !release)
    {
      Serial.print(stat);
      Serial.println("!");
    }*/
}
