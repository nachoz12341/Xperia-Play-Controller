package com.example.injectionapp;

public class Gamepad {
    boolean[] buttons;
    int[] scancode = {
            106,    //Dpad up
            105,    //Dpad down
            103,    //Dpad left
            108,    //Dpad right
            304,    //Cross
            307,    //Square
            308,    //Triangle
            305,    //Circle
             28,    //Start
            314,    //Select
            310,    //Shoulder L
            311,    //Shoulder R
            0,      //Back
            0,      //Home
            0,      //Menu
            0,      //Search
            0,      //Vol +
            0       //Vol -
    };

    final int DPAD_UP = 0;
    final int DPAD_DOWN = 1;
    final int DPAD_LEFT = 2;
    final int DPAD_RIGHT = 3;
    final int BUTTON_CROSS = 4;
    final int BUTTON_SQUARE = 5;
    final int BUTTON_TRIANGLE = 6;
    final int BUTTON_CIRCLE = 7;
    final int BUTTON_START = 8;
    final int BUTTON_SELECT = 9;
    final int SHOULDER_LEFT = 10;
    final int SHOULDER_RIGHT = 11;
    final int NAVIGATE_BACK = 12;
    final int NAVIGATE_HOME = 13;
    final int NAVIGATE_MENU = 14;
    final int NAVIGATE_SEARCH = 15;
    final int NAVIGATE_VOL_PLUS = 16;
    final int NAVIGATE_VOL_MINUS = 17;

    int[] touch_x;
    int[] touch_y;

    public Gamepad()
    {
        buttons = new boolean[18];
        touch_x = new int[2];
        touch_y = new int[2];
    }

    public Gamepad(Gamepad other)
    {
        buttons = new boolean[18];
        touch_x = new int[2];
        touch_y = new int[2];

        System.arraycopy(other.buttons, 0, this.buttons, 0, 18);
        System.arraycopy(other.touch_x, 0, this.touch_x, 0, 2);
        System.arraycopy(other.touch_y, 0, this.touch_y, 0, 2);
    }
}
