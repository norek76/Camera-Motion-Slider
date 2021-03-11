# DFMocoESP32
Forked from Dragonframe DFMoCo v.1.3.1

## Open Points
- Panorama Delay 100 after image shot

## Migration for ESP32-WROOM-32 Board (ESP32-DevKitC V4)
- channel 1
  - PIN  16   step
  - PIN  17   direction
- channel 2
  - PIN  18   step
  - PIN  19   direction
- channel 3
  - PIN  22   step
  - PIN  23   direction
- channel 4
  - PIN  32   step
  - PIN  33   direction
  
## Serial over USB / Bluetooth
If you want to use Bluetooth Classic, you have to set Pin35 to HIGH(Vcc), after that you can connect any Serial Bluetooth Application to the Board. The name of the bluetooth device is _DFMoCo_BT_.

## Camera Mode
You can check the different status codes at the code (~line 320)
```
cm --> Current camera mode status
- Timelapse: cm 1 [status] [executionStatus] [currentImageCounter] [imagesCount] 
- Panorama: cm 2 [status] [executionStatus] [currentRowCounter] [imagesRow] [currentColumnCounter] [imagesColumn] [exposureTimeMillis] [restMoveTime]

cr --> Reset camera mode
```

## Timelapse
Shoot a standalone timelapse, after the setup you can disconnect the phone.
```
tl [imageCount] [intervalSeconds] [exposureTimeMilliSeconds] [motionRestTimeMilliSeconds] [[motorNumber] [startPosition] [endPosition]]*
```
Example:
100 images, interval 5 seconds, exposure 150ms, restTimeAfterMovement 50ms, Motor1: 0 --> 1000, Motor2: 0 --> 500
```
tl 100 5 150 50 1 0 1000 2 0 500
```
## Panorama
Shoot a standalone timelapse, after the setup you can disconnect the phone.
```
pa [motorNumber] [imagesRow] [stepsRow] [motorNumber] [imagesColumn] [stepsColumn] [exposureTimeMilliSeconds] [motionRestTimeMilliSeconds]
```
Example:
30° Row, 30° Column --> 360°/30° = 12 Images, 200Row, 400 Column, Motor1: 0, Motor2: 0
```
pa 1 12 200 2 12 400 1000 5000
```

## Version History
- Version 1.5.2 Add standalone panorama mode
- Version 1.5.1 Add standalone timelapse mode
- Version 1.5.0 Add Camera Focus / Shutter
- Version 1.4.1 Support acceleration
- Version 1.4.0 ESP32-WROOM-32 support with Serial Bluetooth Classic
- Version 1.3.1 Report if go-motion speed cannot be reached.
- Version 1.3.0 Arduino 101 support. Remove non-Arduino support (chipKit, Maple).
- Version 1.2.7 Direction setup time.
- Version 1.2.6 Add PINOUT_VERSION option to use older pinout.
- Version 1.2.5 Fix jogging with low pulse rate.
- Version 1.2.4 Fix pin assignments
- Version 1.2.3 New Position command
- Version 1.2.2 Jog and Inch commands
- Version 1.2.1 Moved step/direction pins for motions 5-8. Detects board type automatically.
- Version 1.2.0 Basic go-motion capabilities
- Version 1.1.2 Smooth transitions when changing direction
- Version 1.1.1 Save/restore motor position
- Version 1.1.0 Major rework 
- Version 1.0.2 Moved pulses into interrupt handler
- Version 1.0.1 Added delay for pulse widths  
- Version 1.0.0 Initial public release.
