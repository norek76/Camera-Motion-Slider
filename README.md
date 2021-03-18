# Camera Motion Slider with Pan Tilt Module

Hi,
since long time I was looking for a stable slider, that can handle the load of a system camera like the A7 together with heavy lenses. Second requirement was to keep the price low.

![alt text](https://github.com/JoJ123/Camera-Motion-Slider/blob/master/Hardware/Base%20with%20Pan/images/BaseWithPan.jpg?raw=true)

## Hardware Components
So here we are after 4 months of drawing, sketching and development here is the slider with pan and tilt module. It's a modular system with the following components:

 - Base with Pan
 - Tilt Module
 - Pan Standalone Module

You can connect the three modules in the following configurations:
 - Base with Pan (f.e. Timelapse)
 - Base with Pan + Tilt Module (f.e. Timelapse)
 - Pan Standalone Module + Tilt Module (f.e. 360° Pano)

## Controller & Software
Last but not least there must be a controller. I started first to develope my "own" motion control library, after few weeks I stopped. And search for existing libs out there and there is a really powerfull open source library called "DFMoCo". It's the protocoll that is used by the software Dragonframe.

So I've started to check the source code of this library, that was build for Arduino Board with just USB Serial support. I've migrated the library to support ESP32 boards together with Bluetooth 4.0 that can be used also with a Serial Bluetooth Application from Android/iOS. The ESP32 can controll up to 4 stepper motors with this library. The library is command based.
In addition to the functionalities of DFMoCo I've added the possibility to focus / shutter an image of the camera with a headphone jack. Most camera manufactors support this way.

To be able to controll the slider also outside of my home I found a really good powerbank from the company XTPower. Product Name: [XT-20000QC3](https://www.xtpower.de/XT-20000Q3-Powerbank-mobiler-externer-USB-Akku-mit-20100mAh)
With this PowerBank I'm was able to reach the following operation times:
- 1 motor:  7.5h
- 2 motors: 5h
- 3 motors: 2.5h

You can easily connect two of them to increase the time. There is also a newer version of the PowerBank [XT-20000QC3-AO-PA](https://www.xtpower.de/XT-20000Q3-AO-PA-Powerbank-ohne-Abschaltung) without the automatic turnoff, when the power consumption is below 4-5W. If you build your controller wihtout a fan f.e. it would be better to choose this model, to keep the microcontroller running.
 
Possible commands:
- Move Motor 1-4 to position x
- Set Speed / Acc Ramp
- Focus / Shutter Camera Controll
- Timelapse with start / end position, timer, image shutter
- Panorama (full squere possible)

The controller for sure still supports the Dragonframe software.

To control the Slider / Camera Module easily also outside of the home, I've written a small Android application.

[Hardware - Base With Pan](https://github.com/JoJ123/Camera-Motion-Slider/blob/master/Hardware/Base%20with%20Pan/README.md)

[Hardware - Tilt Module](https://github.com/JoJ123/Camera-Motion-Slider/blob/master/Hardware/Tilt%20Module/README.md)

[Hardware - Pan Standalone Module](https://github.com/JoJ123/Camera-Motion-Slider/blob/master/Hardware/Pan%20Standalone%20Module/README.md)

[Hardware - Controller](https://github.com/JoJ123/Camera-Motion-Slider/blob/master/Hardware/Controller/README.md)

[Software - ESP32](https://github.com/JoJ123/Camera-Motion-Slider/blob/master/Software/README.md)

[Software - Android](https://github.com/JoJ123/Camera-Motion-Slider/blob/master/Software/AndroidApp/README.md)
