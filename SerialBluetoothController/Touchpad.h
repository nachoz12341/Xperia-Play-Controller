#ifndef _TOUCHPAD_H
#define _TOUCHPAD_H

#include <Wire.h>
#include <stdint.h>


class Touchpad {

	public:
		Touchpad(int SDA, int SDL, int INT, int RST);
    bool GetTouch();
    uint16_t GetTouchX();
    uint16_t GetTouchY();

  private:
    void mxt_identify();
    void mxt_hard_reset(int RST);
    void mxt_read(uint16_t addr, uint8_t *data, uint16_t len);
    void mxt_write(uint16_t addr, uint8_t *data, uint8_t len);
    void mxt_report_t9();

    /* I2C 7bit address of MXT112S */
    const int MXT_ADDR = 0x4B;
    const int MXT_ID_BLK_SIZ = 7; // Query ID size of 7 bytes
    uint8_t num_obj = 0;  // Number of elements in the Object Table
    uint16_t obj_addr_cnt = 7; // Object query address.

    /* Register map*/
    // Store address of each object.
    uint16_t T5_addr = 0;
    uint16_t T7_addr = 0;
    uint16_t T9_addr = 0;
    uint16_t T44_addr = 0;

    // TODO : properly build report ID map.
    // Store min and max report ID of T9
    uint8_t T9_report_id_min = 0;
    uint8_t T9_report_id_max = 0;

    uint8_t MXT_BUF[256]; // Genral purpose buffer array.
    int MXT_INT;

    uint16_t xpos;
    uint16_t ypos;
    bool touch;
};

#endif
 
