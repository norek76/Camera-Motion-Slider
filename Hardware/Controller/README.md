# Controller
The controller is based on a ESP32 with an CNC Shield V3 board. It looks really like a prototype :D It is a prototype.

If you have any interest in building a similar controller I would be happy to work together on a version with a PCB design. 

Contact me! :)

To be able to use the controller also outside of my home I found a really good powerbank from the company XTPower. Product Name: [XT-20000QC3](https://www.xtpower.de/XT-20000Q3-Powerbank-mobiler-externer-USB-Akku-mit-20100mAh)
With this PowerBank I'm was able to reach the following operation times:
- 1 motor:  7.5h
- 2 motors: 5h
- 3 motors: 2.5h

To connect the Powerbank to the controller you can use an XT60 to DC 5.5 2.1mm adapter, you will find it on one of the big electronic online stores or build it yourself.

You can easily connect two of them to increase the time. There is also a newer version of the PowerBank [XT-20000QC3-AO-PA](https://www.xtpower.de/XT-20000Q3-AO-PA-Powerbank-ohne-Abschaltung) without the automatic turnoff, when the power consumption is below 4-5W. If you build your controller wihtout a fan f.e. it would be better to choose this model, to keep the microcontroller running.

![alt text](https://github.com/JoJ123/Camera-Motion-Slider/blob/master/Hardware/Controller/images/Controller1.jpg?raw=true)

Components:
- Hoisting (Proma 131030) - 168 x 103 x 56 Aluminium
- Lochrasterplatine 160mm x 100mm 
- ESP32 Dev Kit C V4
- CNC Shield v3 Board
- StepDown Board to 5V (PowerSupply ESP and fan)
- XT60 Connector
- 5V Mini Fan
- 3.5mm Audio Jack 
- PNP Transistor (for the camera focus/shutter)
- M12 Connector
- few Resistors, LEDs & Switches

![alt text](https://github.com/JoJ123/Camera-Motion-Slider/blob/master/Hardware/Controller/images/Controller2.jpg?raw=true)
![alt text](https://github.com/JoJ123/Camera-Motion-Slider/blob/master/Hardware/Controller/images/Controller3.jpg?raw=true)
