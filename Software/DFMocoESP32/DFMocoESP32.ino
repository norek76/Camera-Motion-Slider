#define DFMOCO_VERSION 1
#define DFMOCO_VERSION_STRING "1.5.2"

#define ESP32
/*
  DFMoco version 1.5.2
  
  Multi-axis motion control.
  For use with the Arc motion control system in Dragonframe 4.
  Generates step and direction signals, which can be sent to stepper motor drivers.
   
  Control up to four axes with an Uno, Duemilanove or 101 board.
  Control up to eight axes with a Mega or Mega 2560.

  Version History
  Version 1.5.2 Add standalone panorama mode
  Version 1.5.1 Add standalone timelapse mode
  Version 1.5.0 Add standalone timelapse mode
  Version 1.4.1 Support acceleration
  Version 1.4.0 ESP32-WROOM-32 support with Serial Bluetooth Classic
  Version 1.3.1 Report if go-motion speed cannot be reached.
  Version 1.3.0 Arduino 101 support. Remove non-Arduino support (chipKit, Maple).
  Version 1.2.7 Direction setup time.
  Version 1.2.6 Add PINOUT_VERSION option to use older pinout.
  Version 1.2.5 Fix jogging with low pulse rate.
  Version 1.2.4 Fix pin assignments
  Version 1.2.3 New Position command
  Version 1.2.2 Jog and Inch commands
  Version 1.2.1 Moved step/direction pins for motions 5-8.
                Detects board type automatically.
  Version 1.2.0 Basic go-motion capabilities
  Version 1.1.2 Smooth transitions when changing direction
  Version 1.1.1 Save/restore motor position
  Version 1.1.0 Major rework 
  Version 1.0.2 Moved pulses into interrupt handler
  Version 1.0.1 Added delay for pulse widths  
  Version 1.0.0 Initial public release.

  Getting Started:
  
   1. Install IDE (Integrated Development Environment):
      Go to https://www.arduino.cc/en/Main/Software and download the Arduino Software for your OS.
   2. Run the IDE you installed.
   3. Open this file in the IDE.
   4. Go to the Tools menu of the IDE and choose the Board type you are using.
   5. Verify/Compile the sketch. (Command-R on Mac, Control-R on Windows.)
   6. After this finishes, Upload the code to the board. (Command-U on Mac, Control-U on Windows.)
   

  Pin configuration (ESP32):
  
  channel 1
        PIN  16   step
        PIN  17   direction
  channel 2
        PIN  18   step
        PIN  19   direction
  channel 3
        PIN  22   step
        PIN  23   direction
  channel 4
        PIN  32   step
        PIN  33   direction

  END Switch (GND)
        PIN  34
  Serial Bluetooth ON (High)/OFF (Low)
        PIN  35


  Pin configuration:
  
  channel 1
        PIN   4   step
        PIN   5   direction
  channel 2
        PIN   6   step
        PIN   7   direction
  channel 3
        PIN   8   step
        PIN   9   direction
  channel 4
        PIN  10   step
        PIN  11   direction

  channel 5
        PIN  28   step
        PIN  29   direction
  channel 6
        PIN  30   step
        PIN  31   direction
  channel 7
        PIN  32   step
        PIN  33   direction
  channel 8
        PIN  34   step
        PIN  35   direction
 */

// change this to 1 if you want original pinout for channels 5-8
#define PINOUT_VERSION 2

/*
  This is PINOUT_VERSION 1
  
  channel 5
        PIN  22   step
        PIN  23   direction
  channel 6
        PIN  24   step
        PIN  25   direction
  channel 7
        PIN  26   step
        PIN  27   direction
  channel 8
        PIN  28   step
        PIN  29   direction
*/

// detect board type
#if defined(__AVR_ATmega1280__) || defined(__AVR_ATmega2560__)
  #define BOARD_MEGA 1
#elif defined(__AVR_ATmega328P__) || defined(__AVR_ATmega328__)  || defined(__AVR_ATmega168__)
  #define BOARD_UNO 1
#elif defined(ARDUINO_ARCH_ARC32) // Intel Curie/101
  #define BOARD_101 1
  #include "CurieTimerOne.h"
#elif defined(ESP32)
  #define BOARD_ESP32 1
  #include "BluetoothSerial.h"
#else
  #error Cannot identify board
#endif

// USER: if you want a kill switch, uncomment out the next line by removing the // characters
//#define KILL_SWITCH_INTERRUPT 0

#define SERIAL_DEVICE Serial
#if defined(BOARD_ESP32)
  #define SERIAL_DEVICE_BT SerialBT
  #define BLUETOOTH_LED_PIN 26
#endif

#define FOCUS_PIN 14
#define SHUTTER_PIN 27
#define ENABLE_PIN 25

#if defined(BOARD_101) || defined(BOARD_ESP32)
  #define PIN_ON(port, pin)  { digitalWrite(pin, 1); }
  #define PIN_OFF(port, pin) { digitalWrite(pin, 0); }
#else
  #define PIN_ON(port, pin)  { port |= pin; }
  #define PIN_OFF(port, pin) { port &= ~pin; }
#endif

// Arduino Uno/Duemilanove  -> 4 MOTORS MAX
// Arduino Mega 2560 / Mega -> 8 MOTORS MAX
#if defined(BOARD_UNO) || defined(BOARD_101) || defined(BOARD_ESP32)
#define MOTOR_COUNT 4
#else
#define MOTOR_COUNT 8
#endif

#define TIME_CHUNK 50
#define SEND_POSITION_COUNT 20000

// update velocities 20 x second
#define VELOCITY_UPDATE_RATE (50000 / TIME_CHUNK)
#define VELOCITY_INC(maxrate) (max(1.0f, maxrate / 70.0f))
#define VELOCITY_CONVERSION_FACTOR 0.30517578125f /* 20 / 65.536f */

#define MAX_VELOCITY 12000
#define DEFAUL_VELOCITY 600
#define MIN_VELOCITY 100
#define MAX_ACCELERATION 2 * MAX_VELOCITY
#define MIN_ACCELERATION 0.1f * MAX_VELOCITY

// setup step and direction pins
#if defined(BOARD_101)

  #define MOTOR0_STEP_PORT 0
  #define MOTOR0_STEP_PIN  4
  
  #define MOTOR1_STEP_PORT 0
  #define MOTOR1_STEP_PIN  6

  #define MOTOR2_STEP_PORT 0
  #define MOTOR2_STEP_PIN  8

  #define MOTOR3_STEP_PORT 0
  #define MOTOR3_STEP_PIN  10

#elif defined(__AVR_ATmega1280__) || defined(__AVR_ATmega2560__)

  #define MOTOR0_STEP_PORT PORTG
  #define MOTOR0_STEP_PIN  B00100000
  
  #define MOTOR1_STEP_PORT PORTH
  #define MOTOR1_STEP_PIN  B00001000

  #define MOTOR2_STEP_PORT PORTH
  #define MOTOR2_STEP_PIN  B00100000

  #define MOTOR3_STEP_PORT PORTB
  #define MOTOR3_STEP_PIN  B00010000

  #if ( PINOUT_VERSION == 2 )
  
    #define MOTOR4_STEP_PORT PORTA
    #define MOTOR4_STEP_PIN  B01000000
  
    #define MOTOR5_STEP_PORT PORTC
    #define MOTOR5_STEP_PIN  B10000000
  
    #define MOTOR6_STEP_PORT PORTC
    #define MOTOR6_STEP_PIN  B00100000
  
    #define MOTOR7_STEP_PORT PORTC
    #define MOTOR7_STEP_PIN  B00001000

  #elif ( PINOUT_VERSION == 1 )
  
    #define MOTOR4_STEP_PORT PORTA
    #define MOTOR4_STEP_PIN  B00000001
  
    #define MOTOR5_STEP_PORT PORTA
    #define MOTOR5_STEP_PIN  B00000100
  
    #define MOTOR6_STEP_PORT PORTA
    #define MOTOR6_STEP_PIN  B00010000
  
    #define MOTOR7_STEP_PORT PORTA
    #define MOTOR7_STEP_PIN  B01000000

  #endif

#elif defined(BOARD_UNO)

  #define MOTOR0_STEP_PORT PORTD
  #define MOTOR0_STEP_PIN  B00010000
  
  #define MOTOR1_STEP_PORT PORTD
  #define MOTOR1_STEP_PIN  B01000000

  #define MOTOR2_STEP_PORT PORTB
  #define MOTOR2_STEP_PIN  B00000001

  #define MOTOR3_STEP_PORT PORTB
  #define MOTOR3_STEP_PIN  B00000100

#elif defined(BOARD_ESP32)

  #define MOTOR0_STEP_PORT 0
  #define MOTOR0_STEP_PIN  16
  
  #define MOTOR1_STEP_PORT 0
  #define MOTOR1_STEP_PIN  18

  #define MOTOR2_STEP_PORT 0
  #define MOTOR2_STEP_PIN  22

  #define MOTOR3_STEP_PORT 0
  #define MOTOR3_STEP_PIN  32

#endif



/**
 * Serial output specialization
 */
#if defined(UBRRH)
#define TX_UCSRA UCSRA
#define TX_UDRE  UDRE
#define TX_UDR   UDR
#else
#define TX_UCSRA UCSR0A
#define TX_UDRE  UDRE0
#define TX_UDR   UDR0
#endif
 
char txBuf[32];
char *txBufPtr;

#define TX_MSG_BUF_SIZE 16

#define MSG_STATE_START 0
#define MSG_STATE_CMD   1
#define MSG_STATE_DATA  2
#define MSG_STATE_ERR   3

#define MSG_STATE_DONE  100

/*
 * Command codes from user
 */
#define USER_CMD_ARGS 40

#define CMD_NONE       0
#define CMD_HI         10
#define CMD_MS         30
#define CMD_NP         31
#define CMD_MM         40 // move motor
#define CMD_PR         41 // pulse rate
#define CMD_SM         42 // stop motor
#define CMD_MP         43 // motor position
#define CMD_ZM         44 // zero motor
#define CMD_SA         50 // stop all (hard)
#define CMD_BF         60 // blur frame
#define CMD_GO         61 // go!

#define CMD_JM         70 // jog motor
#define CMD_IM         71 // inch motor

#define CMD_VE         80 // velocity 
#define CMD_AC         81 // acceleration

#define CMD_CF         90 // camera focus 
#define CMD_CS         91 // camera shutter
#define CMD_CI         92 // camera take image

#define CMD_CM         100 // camera mode
#define CMD_CR         101 // reset camera mode
#define CMD_TL         102 // timelapse
#define CMD_PA         103 // panorama

// pa 1 10 160 2 3 200 5000 1000

#define MSG_HI 01
#define MSG_MM 02
#define MSG_MP 03
#define MSG_MS 04
#define MSG_PR 05
#define MSG_SM 06
#define MSG_SA 07
#define MSG_BF 10
#define MSG_GO 11
#define MSG_JM 12
#define MSG_IM 13
#define MSG_AC 14
#define MSG_VE 15
#define MSG_CM 16
#define MSG_CR 17

#define CAM_MODE_NONE      0
#define CAM_MODE_TIMELAPSE 1
#define CAM_MODE_PANORAMA  2

// Timelapse Status
#define TIMELAPSE_SETUP                    0
#define TIMELAPSE_RUNNING                  1
#define TIMELAPSE_DONE                     2
// Timelapse EXECUTION
#define TIMELAPSE_INIT_MOVEMENT            0
#define TIMELAPSE_START_EXECUTION          1
#define TIMELAPSE_EXECUTING                2
// Timelapse Errors
#define TIMELAPSE_ERROR_ARG1_GT_1          10
#define TIMELAPSE_ERROR_ARG2_GT_0          11
#define TIMELAPSE_ERROR_ARG3_GT_0          12
#define TIMELAPSE_ERROR_ARG4_GE_0          13
#define TIMELAPSE_ERROR_MOVE_SHOOT_TIME    14
#define TIMELAPSE_ERROR_POSITION           15
// Panorama Status
#define PANORAMA_SETUP                     0
#define PANORAMA_RUNNING                   1
#define PANORAMA_DONE                      2
// Panorama Execution Status
#define PANORAMA_REST                      0
#define PANORAMA_REST_RUNNING              1
#define PANORAMA_IMAGE                     2
#define PANORAMA_READY_FOR_MOVE            3
#define PANORAMA_MOVE                      4
#define PANORAMA_MOVE_END                  5
// Panorama Errors
#define PANORAMA_ERROR_MTR_OUT             10
#define PANORAMA_ERROR_IMG_LE_1            11
#define PANORAMA_ERROR_STP_EQ_0            12
#define PANORAMA_ERROR_EXP_LT_50           13
#define PANORAMA_ERROR_RST_LT_1            14

void stopMotor(int motorIndex, boolean hardStop = false);
void setPulsesPerSecond(int motorIndex, uint16_t pulsesPerSecond, boolean setRamp = false);

struct TimelapseMotor {
  boolean enable;
  uint32_t steps;
  uint32_t velocity;
  uint32_t acceleration;
  uint32_t startPosition;
  uint32_t endPosition;
  uint32_t lastPosition;
};

struct Timelapse
{
  TimelapseMotor motors[MOTOR_COUNT];
  uint32_t intervalSeconds;
  uint32_t imagesCount;
  uint32_t exposureTimeMillis;
  uint32_t restMoveTime;
  uint32_t currentImageCounter;
  byte status;
  byte executionStatus;
};

Timelapse timelapseData;

struct Panorma
{  
  uint32_t motorRow;
  uint32_t imagesRow;
  int32_t stepsRow;
  uint32_t motorColumn;
  uint32_t imagesColumn;
  int32_t stepsColumn;
  uint32_t exposureTimeMillis;
  uint32_t restMoveTime;
  uint32_t restStartMillis;
  uint32_t currentRowCounter;
  uint32_t currentColumnCounter;
  byte status;
  byte executionStatus;
};

Panorma panoramaData;

struct UserCmd
{
  byte command;
  byte argCount;
  int32_t args[USER_CMD_ARGS];
};

/*
 * Message state machine variables.
 */
byte lastUserData;
int  msgState;
int  msgNumberSign;
UserCmd userCmd;


struct txMsg
{
  byte msg;
  byte motor;
};

struct TxMsgBuffer
{
  txMsg buffer[TX_MSG_BUF_SIZE];
  byte head;
  byte tail;
};

TxMsgBuffer txMsgBuffer;

#if defined(BOARD_ESP32)
BluetoothSerial SerialBT;
#endif

/*
 Motor data.
 */

uint16_t           motorAccumulator0;
uint16_t           motorAccumulator1;
uint16_t           motorAccumulator2;
uint16_t           motorAccumulator3;
#if MOTOR_COUNT > 4
uint16_t           motorAccumulator4;
uint16_t           motorAccumulator5;
uint16_t           motorAccumulator6;
uint16_t           motorAccumulator7;
#endif
uint16_t*          motorAccumulator[MOTOR_COUNT] =
{
  &motorAccumulator0, &motorAccumulator1, &motorAccumulator2, &motorAccumulator3, 
#if MOTOR_COUNT > 4
  &motorAccumulator4, &motorAccumulator5, &motorAccumulator6, &motorAccumulator7 
#endif
};

uint16_t           motorMoveSteps0;
uint16_t           motorMoveSteps1;
uint16_t           motorMoveSteps2;
uint16_t           motorMoveSteps3;
#if MOTOR_COUNT > 4
uint16_t           motorMoveSteps4;
uint16_t           motorMoveSteps5;
uint16_t           motorMoveSteps6;
uint16_t           motorMoveSteps7;
#endif
uint16_t*          motorMoveSteps[MOTOR_COUNT] =
{
  &motorMoveSteps0, &motorMoveSteps1, &motorMoveSteps2, &motorMoveSteps3,
#if MOTOR_COUNT > 4
  &motorMoveSteps4, &motorMoveSteps5, &motorMoveSteps6, &motorMoveSteps7
#endif
};


uint16_t           motorMoveSpeed0;
uint16_t           motorMoveSpeed1;
uint16_t           motorMoveSpeed2;
uint16_t           motorMoveSpeed3;
#if MOTOR_COUNT > 4
uint16_t           motorMoveSpeed4;
uint16_t           motorMoveSpeed5;
uint16_t           motorMoveSpeed6;
uint16_t           motorMoveSpeed7;
#endif
uint16_t         * motorMoveSpeed[MOTOR_COUNT] =
{
  &motorMoveSpeed0, &motorMoveSpeed1, &motorMoveSpeed2, &motorMoveSpeed3,
#if MOTOR_COUNT > 4
  &motorMoveSpeed4, &motorMoveSpeed5, &motorMoveSpeed6, &motorMoveSpeed7
#endif
};

volatile boolean nextMoveLoaded;


unsigned int   velocityUpdateCounter;
byte           sendPositionCounter;
boolean        hardStopRequested;

byte sendPosition = 0;
byte motorMoving = 0;
byte toggleStep = 0;


#define P2P_MOVE_COUNT 7

struct Motor
{
  byte   stepPin;
  byte   dirPin;

  // pre-computed move
  float   moveTime[P2P_MOVE_COUNT];
  int32_t movePosition[P2P_MOVE_COUNT];
  float   moveVelocity[P2P_MOVE_COUNT];
  float   moveAcceleration[P2P_MOVE_COUNT];

  float   gomoMoveTime[P2P_MOVE_COUNT];
  int32_t gomoMovePosition[P2P_MOVE_COUNT];
  float   gomoMoveVelocity[P2P_MOVE_COUNT];
  float   gomoMoveAcceleration[P2P_MOVE_COUNT];

  int       currentMove;
  float     currentMoveTime;
  
  volatile  boolean   positionReached;
  volatile  boolean   dir;

  int32_t   position;
  int32_t   destination;
  float     maxVelocity;
  float     maxAcceleration;
  
  uint16_t  nextMotorMoveSteps;
  float     nextMotorMoveSpeed;

};

boolean goMoReady;
int     goMoDelayTime;

Motor motors[MOTOR_COUNT];
      
#if defined(BOARD_ESP32)
unsigned long lastBluetoothLedMillis = millis();
boolean lastBluetoothLedState = true;
hw_timer_t * timerMotion = NULL;
portMUX_TYPE timerMotionMux = portMUX_INITIALIZER_UNLOCKED;
boolean serialBluetoothEnabled = false;
#endif

hw_timer_t * timerCameraMode = NULL;
hw_timer_t * timerCameraModeMotion = NULL;

byte cameraMode = CAM_MODE_NONE;

#ifdef KILL_SWITCH_INTERRUPT && !defined(BOARD_ESP32)
void killSwitch()
{
    hardStopRequested = true;
}
#endif

class DualPrint : public Print
{
public:
    DualPrint() : serialBluetoothEnabled(false) {}
    virtual size_t write(uint8_t c) {
      #if defined(BOARD_ESP32)
      if (serialBluetoothEnabled) 
        SERIAL_DEVICE_BT.write(c);
      else
      #endif
        SERIAL_DEVICE.write(c);
      return 1;
    }

    boolean serialBluetoothEnabled;
} dualSerial;

boolean availableSerial() {
#if defined(BOARD_ESP32)
  if (serialBluetoothEnabled) {
    return SERIAL_DEVICE_BT.available();
  }
  return SERIAL_DEVICE.available();
#else
  return SERIAL_DEVICE.available();
#endif 
}

int readSerial() {
#if defined(BOARD_ESP32)
  if (serialBluetoothEnabled) {
    return SERIAL_DEVICE_BT.read();
  }
  return SERIAL_DEVICE.read();
#else
  return SERIAL_DEVICE.read();
#endif 
}

#if defined(BOARD_ESP32)
void bluetoothCallback(esp_spp_cb_event_t event, esp_spp_cb_param_t *param){
  if(event == ESP_SPP_SRV_OPEN_EVT) {
    PIN_ON(0, BLUETOOTH_LED_PIN);
    dualSerial.serialBluetoothEnabled = true;
    serialBluetoothEnabled = true;
  }
  if (event == ESP_SPP_CLOSE_EVT) {
    dualSerial.serialBluetoothEnabled = false;
    serialBluetoothEnabled = false;
    lastBluetoothLedState = false;
    lastBluetoothLedMillis = millis();
    PIN_OFF(0, BLUETOOTH_LED_PIN);
  }
}
#endif


#if defined(BOARD_101)
void updateStepDirection(void)
{
#elif defined(BOARD_ESP32)
void IRAM_ATTR updateStepDirection(void)
{
  portENTER_CRITICAL_ISR(&timerMotionMux);
#else
ISR(TIMER1_OVF_vect)
{
#endif
  toggleStep = !toggleStep;
  
  if (toggleStep)
  {
    // MOTOR 1
    if (motorMoveSteps0)
    {
      uint16_t a = motorAccumulator0;
      motorAccumulator0 += motorMoveSpeed0;
      if (motorAccumulator0 < a)
      {
        motorMoveSteps0--;
        
        PIN_ON(MOTOR0_STEP_PORT, MOTOR0_STEP_PIN);
      }
    }

    // MOTOR 2
    if (motorMoveSteps1)
    {
      uint16_t a = motorAccumulator1;
      motorAccumulator1 += motorMoveSpeed1;
      if (motorAccumulator1 < a)
      {
        motorMoveSteps1--;
        
        PIN_ON(MOTOR1_STEP_PORT, MOTOR1_STEP_PIN);
      }
    }

    // MOTOR 3
    if (motorMoveSteps2)
    {
      uint16_t a = motorAccumulator2;
      motorAccumulator2 += motorMoveSpeed2;
      if (motorAccumulator2 < a)
      {
        motorMoveSteps2--;
        
        PIN_ON(MOTOR2_STEP_PORT, MOTOR2_STEP_PIN);
      }
    }

    // MOTOR 4
    if (motorMoveSteps3)
    {
      uint16_t a = motorAccumulator3;
      motorAccumulator3 += motorMoveSpeed3;
      if (motorAccumulator3 < a)
      {
        motorMoveSteps3--;
        
        PIN_ON(MOTOR3_STEP_PORT, MOTOR3_STEP_PIN);
      }
    }

  #if MOTOR_COUNT > 4

    // MOTOR 5
    if (motorMoveSteps4)
    {
      uint16_t a = motorAccumulator4;
      motorAccumulator4 += motorMoveSpeed4;
      if (motorAccumulator4 < a)
      {
        motorMoveSteps4--;
        
        PIN_ON(MOTOR4_STEP_PORT, MOTOR4_STEP_PIN);
      }
    }

    // MOTOR 6
    if (motorMoveSteps5)
    {
      uint16_t a = motorAccumulator5;
      motorAccumulator5 += motorMoveSpeed5;
      if (motorAccumulator5 < a)
      {
        motorMoveSteps5--;
        
        PIN_ON(MOTOR5_STEP_PORT, MOTOR5_STEP_PIN);
      }
    }

    // MOTOR 7
    if (motorMoveSteps6)
    {
      uint16_t a = motorAccumulator6;
      motorAccumulator6 += motorMoveSpeed6;
      if (motorAccumulator6 < a)
      {
        motorMoveSteps6--;
        
        PIN_ON(MOTOR6_STEP_PORT, MOTOR6_STEP_PIN);
      }
    }

    // MOTOR 8
    if (motorMoveSteps7)
    {
      uint16_t a = motorAccumulator7;
      motorAccumulator7 += motorMoveSpeed7;
      if (motorAccumulator7 < a)
      {
        motorMoveSteps7--;
        
        PIN_ON(MOTOR7_STEP_PORT, MOTOR7_STEP_PIN);
      }
    }

  #endif

  }
  else
  {
    velocityUpdateCounter++;
    if (velocityUpdateCounter == VELOCITY_UPDATE_RATE)
    {
      velocityUpdateCounter = 0;
      
      if (sendPositionCounter)
      {
        sendPositionCounter--;
      }
      
      for (int i = 0; i < MOTOR_COUNT; i++)
      {
        if (*motorMoveSpeed[i] && !motors[i].nextMotorMoveSpeed)
        {
          bitSet(sendPosition, i);
        }

        *motorMoveSteps[i] = motors[i].nextMotorMoveSteps;
        *motorMoveSpeed[i] = motors[i].nextMotorMoveSpeed;
        digitalWrite(motors[i].dirPin, motors[i].dir);

        *motorAccumulator[i] = 65535;
      }
      nextMoveLoaded = false; // ready for new move
    }
    
    PIN_OFF(MOTOR0_STEP_PORT, MOTOR0_STEP_PIN);
    PIN_OFF(MOTOR1_STEP_PORT, MOTOR1_STEP_PIN);
    PIN_OFF(MOTOR2_STEP_PORT, MOTOR2_STEP_PIN);
    PIN_OFF(MOTOR3_STEP_PORT, MOTOR3_STEP_PIN);

    #if MOTOR_COUNT > 4
      PIN_OFF(MOTOR4_STEP_PORT, MOTOR4_STEP_PIN);
      PIN_OFF(MOTOR5_STEP_PORT, MOTOR5_STEP_PIN);
      PIN_OFF(MOTOR6_STEP_PORT, MOTOR6_STEP_PIN);
      PIN_OFF(MOTOR7_STEP_PORT, MOTOR7_STEP_PIN);
    #endif
  }
#if defined(BOARD_ESP32)
  portEXIT_CRITICAL_ISR(&timerMotionMux);
#endif
}


/*
 * setup() gets called once, at the start of the program.
 */
void setup()
{  
  // setup serial connection
  SERIAL_DEVICE.begin(57600);

  #if defined(BOARD_ESP32)
    #ifdef KILL_SWITCH_INTERRUPT
      pinMode(KILL_SWITCH_INTERRUPT, INPUT_PULLUP);
    #endif
    
    if(SERIAL_DEVICE_BT.begin("DFMoCo")){
      SERIAL_DEVICE_BT.register_callback(bluetoothCallback);
    }
  #endif

  #if defined(FOCUS_PIN)
  pinMode(FOCUS_PIN, OUTPUT);
  PIN_OFF(0, FOCUS_PIN);
  #endif
  
  pinMode(SHUTTER_PIN, OUTPUT);
  PIN_OFF(0, SHUTTER_PIN);
  
  pinMode(ENABLE_PIN, OUTPUT);
  PIN_OFF(0, ENABLE_PIN);
  
  pinMode(BLUETOOTH_LED_PIN, OUTPUT);  
  PIN_ON(0, BLUETOOTH_LED_PIN);

  
  goMoReady = false;
  lastUserData = 0;
  msgState = MSG_STATE_START;
  velocityUpdateCounter = 0;
  sendPositionCounter = 10;
  nextMoveLoaded = false;
  hardStopRequested = false;
  
  for (int i = 0; i < 32; i++)
    txBuf[i] = 0;
  
  txBufPtr = txBuf;
  
  #ifdef KILL_SWITCH_INTERRUPT
    #if !defined(BOARD_ESP32)
    attachInterrupt(KILL_SWITCH_INTERRUPT, killSwitch, CHANGE);
    #endif
  #endif

  
  // initialize motor structures
  for (int i = 0; i < MOTOR_COUNT; i++)
  {
    // setup motor pins - you can customize/modify these after loop
    // default sets step/dir pairs together, with first four motors at 4/5, 6/7, 8/9, 10/11
    // then, for the Mega boards, it jumps to 28/29, 30/31, 32/33, 34/35
    #if defined(BOARD_ESP32)      
      switch(i) {
        case 0:
          motors[i].stepPin = MOTOR0_STEP_PIN;
          break;
        case 1:
          motors[i].stepPin = MOTOR1_STEP_PIN;
          break;
        case 2:
          motors[i].stepPin = MOTOR2_STEP_PIN;
          break;
        case 3:
          motors[i].stepPin = MOTOR3_STEP_PIN;
          break;
      }
    #elif ( PINOUT_VERSION == 2 )
      motors[i].stepPin = (i * 2) + ( (i < 4) ? 4 : 20 );
    #elif ( PINOUT_VERSION == 1 )
      motors[i].stepPin = (i * 2) + ( (i < 4) ? 4 : 14 );
    #endif
    
    motors[i].dirPin = motors[i].stepPin + 1;
    motors[i].dir = true; // forward
    motors[i].positionReached = true;
    motors[i].position = 0L;
    motors[i].destination = 0L;

    motors[i].nextMotorMoveSteps = 0;
    motors[i].nextMotorMoveSpeed = 0;
    
    setPulsesPerSecond(i, DEFAUL_VELOCITY, true);
  }


  // set output pins
  for (int i = 0; i < MOTOR_COUNT; i++)
  {
    pinMode(motors[i].stepPin, OUTPUT);
    pinMode(motors[i].dirPin, OUTPUT);
    
#if defined(__AVR_ATmega1280__) || defined(__AVR_ATmega2560__)

    // disable PWM
    switch (motors[i].stepPin)
    {
      #if defined(TCCR3A) && defined(COM3B1)
      case 4:
        TCCR3A &= ~COM3B1;
        break;
      #endif

      #if defined(TCCR4A) && defined(COM4A1)
      case 6:
        TCCR4A &= ~COM4A1;
        break;
      #endif

      #if defined(TCCR4A) && defined(COM4C1)
      case 8:
        TCCR4A &= ~COM4C1;
        break;
      #endif

      #if defined(TCCR2A) && defined(COM2A1)
      case 10:
        TCCR2A &= ~COM2A1;
        break;
      #endif
    }
    
#elif !defined(BOARD_ESP32)
    
    switch (motors[i].stepPin)
    {
      #if defined(TCCR1A) && defined(COM1B1)
      case 10:
        TCCR1A &= ~COM1B1;
        break;
      #endif

    }

#endif
  }

  // set initial direction
  for (int i = 0; i < MOTOR_COUNT; i++)
  {
    digitalWrite( motors[i].dirPin, motors[i].dir ? HIGH : LOW );
  }

  sendMessage(MSG_HI, 0);
   
 // SET UP interrupt timer  
 #if defined(BOARD_UNO) || defined(BOARD_MEGA)

   TCCR1A = 0;
   TCCR1B = _BV(WGM13);
 
   ICR1 = (F_CPU / 4000000) * TIME_CHUNK; // goes twice as often as time chunk, but every other event turns off pins
   TCCR1B &= ~(_BV(CS10) | _BV(CS11) | _BV(CS12));
   TIMSK1 = _BV(TOIE1);
   TCCR1B |= _BV(CS10);

 #elif defined(BOARD_101)

   CurieTimerOne.start(25, &updateStepDirection);

 #elif defined(BOARD_ESP32)
   timerMotion = timerBegin(0, 80, true);                
   timerAttachInterrupt(timerMotion, &updateStepDirection, true);
   timerAlarmWrite(timerMotion, 25, true);           
   timerAlarmEnable(timerMotion);
 #endif
  
}


/*
 * For stepper-motor timing, every clock cycle counts.
 */
void loop()
{
  int32_t *ramValues = (int32_t *)malloc(sizeof(int32_t) * MOTOR_COUNT);
  int32_t *ramNotValues = (int32_t *)malloc(sizeof(int32_t) * MOTOR_COUNT);

  for (int i = 0; i < MOTOR_COUNT; i++)
  { 
    if (ramValues[i] == ~ramNotValues[i])
    {
      motors[i].position = motors[i].destination = ramValues[i];
    }
  }  

  while (true)
  {
    if (!serialBluetoothEnabled && ((millis() - lastBluetoothLedMillis) > 600)) {
      if (lastBluetoothLedState) {
        PIN_OFF(0, BLUETOOTH_LED_PIN);
      } else {      
        PIN_ON(0, BLUETOOTH_LED_PIN);
      }
      lastBluetoothLedState = !lastBluetoothLedState;
      lastBluetoothLedMillis = millis();
    }
    if (!nextMoveLoaded)
      updateMotorVelocities();
    
    processSerialCommand();
    if (cameraMode != CAM_MODE_NONE) {
      processCameraMode();
    }
    
    // check if we have serial output
    #if defined(BOARD_UNO) || defined(BOARD_MEGA)
    if (*txBufPtr)
    {
      if ((TX_UCSRA) & (1 << TX_UDRE))
      {
        TX_UDR = *txBufPtr++;
  
        // we are done with this msg, get the next one
        if (!*txBufPtr)
          nextMessage();
      }
    }
    #endif

    if (!sendPositionCounter)
    {
      sendPositionCounter = 20;

      byte i;
      for (i = 0; i < MOTOR_COUNT; i++)
      {
        if (bitRead(motorMoving, i) || bitRead(sendPosition, i))
        {
          sendMessage(MSG_MP, i);
          ramValues[i] = motors[i].position;
          ramNotValues[i] = ~motors[i].position;
        }
        if (motors[i].position == motors[i].destination) {
          motors[i].positionReached = true;
        }
      }

      sendPosition = 0;
    }
  }
}

/**
 * Update velocities.
 */

void updateMotorVelocities()
{
  // process hard stop interrupt request
#if defined(BOARD_ESP32) && KILL_SWITCH_INTERRUPT
  if (!digitalRead(KILL_SWITCH_INTERRUPT))
  {
#else
  if (hardStopRequested)
  {
    hardStopRequested = 0;
#endif
    hardStop();
  }
  
  for (int m = 0; m < MOTOR_COUNT; m++)
  {
    Motor *motor = &motors[m];
    motor->nextMotorMoveSteps = 0;
    motor->nextMotorMoveSpeed = 0;

    if (bitRead(motorMoving, m))
    {
      int seg = motor->currentMove;
      
      if (motor->moveTime[seg] == 0)
      {
        bitClear(motorMoving, m);
      }
      else
      {
        float originalMoveTime = motor->currentMoveTime;
        int originalMove = motor->currentMove;
        
        motor->currentMoveTime += 0.05f;
        
        if (motor->currentMoveTime >= motor->moveTime[seg])
        {
          motor->currentMoveTime -= motor->moveTime[seg];
          motor->currentMove++;
          seg++;
        }
        float t = motor->currentMoveTime;
        int32_t xn = (int32_t)(motor->movePosition[seg] + motor->moveVelocity[seg] * t + motor->moveAcceleration[seg] * t * t); // accel was already multiplied * 0.5

        int32_t dx = abs(xn - motor->position);

        if (!dx) // don't change direction flag unless we are actually stepping in new direction
          continue;
          
        boolean forward = xn > motor->position;

        if (forward != motor->dir) // direction setup time 1/20th second should be plenty
        {
          // revert everything except for dir flag
          motor->currentMoveTime = originalMoveTime;
          motor->currentMove = originalMove;
        }
        else
        {
#if defined(BOARD_ESP32)
          motor->nextMotorMoveSpeed = _max(1, _min(65535, dx * 65.6f));
#else
          motor->nextMotorMoveSpeed = max(1, min(65535, dx * 65.6f));
#endif
          motor->nextMotorMoveSteps = dx;
          motor->position = xn;
        }
        
        motor->dir = forward;
      }      
    }
  }
  nextMoveLoaded = true;
}

/*
 * Set up the axis for pulses per second (approximate)
 */
void setMaxAccelerationPerSecond(int motorIndex, uint16_t accelerationPerSecond)
{
  if (accelerationPerSecond > MAX_ACCELERATION)
    accelerationPerSecond = MAX_ACCELERATION;
  if (accelerationPerSecond < MIN_ACCELERATION)
    accelerationPerSecond = MIN_ACCELERATION;
    
  motors[motorIndex].maxAcceleration = accelerationPerSecond;
}

/*
 * Set up the axis for pulses per second (approximate)
 */
void setPulsesPerSecond(int motorIndex, uint16_t pulsesPerSecond, boolean setRamp)
{
  if (pulsesPerSecond > MAX_VELOCITY)
    pulsesPerSecond = MAX_VELOCITY;
  if (pulsesPerSecond < MIN_VELOCITY)
    pulsesPerSecond = MIN_VELOCITY;
    
  motors[motorIndex].maxVelocity = pulsesPerSecond;
  if (setRamp) {
    motors[motorIndex].maxAcceleration = pulsesPerSecond * 0.5f;
  }
}


void setupMotorMove(int motorIndex, int32_t destination)
{
  motors[motorIndex].destination = destination;

  if (destination != motors[motorIndex].position)
  {
    calculatePointToPoint(motorIndex, destination);
    bitSet(motorMoving, motorIndex);
  }

}


void hardStop()
{
  // set the destination to the current location, so they won't move any more
  for (int i = 0; i < MOTOR_COUNT; i++)
  {
    stopMotor(i, true);
  }
}

void stopMotor(int motorIndex, boolean hardStop)
{
  int32_t delta = (motors[motorIndex].destination - motors[motorIndex].position);
  if (!delta)
    return;

  Motor *motor = &motors[motorIndex];
  int i;

  for (i = 0; i < P2P_MOVE_COUNT; i++)
  {
    motor->moveTime[i] = 0;
    motor->moveVelocity[i] = 0;
    motor->movePosition[i] = 0;
  }

  float v = VELOCITY_CONVERSION_FACTOR * motors[motorIndex].nextMotorMoveSpeed;
  float maxA = motor->maxAcceleration;
  if (hardStop) {
    maxA = MAX_ACCELERATION * 3;
  }
  float maxV = motor->maxVelocity;

  if (v > maxV)
    v = maxV;

  if (!motor->dir)
    v = -v;

  float t = fabs(v / maxA);

  motor->moveTime[0] = t;
  motor->movePosition[0] = motor->position;
  motor->moveVelocity[0] = v;
  motor->moveAcceleration[0] = (v > 0) ? -maxA : maxA;
 
  motor->moveTime[1] = 0;
  motor->movePosition[1] = (int32_t)(motor->movePosition[0] + motor->moveVelocity[0] * t + 0.5f * motor->moveAcceleration[0] * t * t);
  motor->moveVelocity[1] = 0;
  motor->moveAcceleration[1] = 0;

  motor->moveAcceleration[0] *= 0.5f;

  motor->destination = motor->movePosition[1];
  
  motor->currentMoveTime = 0;
  motor->currentMove = 0;  
}

boolean isValidMotor(int motorIndex)
{
  return (motorIndex >=0 && motorIndex < MOTOR_COUNT);
}


void processGoPosition(int motorIndex, int32_t pos)
{
  if (motors[motorIndex].position != pos)
  {
    motors[motorIndex].positionReached = false;
    setupMotorMove(motorIndex, pos);
    sendMessage(MSG_MM, motorIndex);
  }
  else
  {
    motors[motorIndex].positionReached = true;
    sendMessage(MSG_MP, motorIndex);
  }
}

/*

Command format

ASCII
[command two bytes]

Version
"hi"
-> "hi 1"

zero motor
"zm 1"
-> "z 1"

move motor
"mm 1 +1111111111

motor position?
mp 1

MOTOR STATUS
"ms"
-> "ms [busy motor count]"

SET PULSE PER SECOND
pr 1 200

STOP MOTOR
sm 1

STOP ALL
sa

*/

/*
 * int processUserMessage(char data)
 *
 * Read user data (from virtual com port), processing one byte at a time.
 * Implemented with a state machine to reduce memory overhead.
 *
 * Returns command code for completed command.
 */
byte processUserMessage(char data)
{
  byte cmd = CMD_NONE;

  switch (msgState)
  {
  case MSG_STATE_START:
    if (data != '\r' && data != '\n')
    {
      msgState = MSG_STATE_CMD;
      msgNumberSign = 1;
      userCmd.command = CMD_NONE;
      userCmd.argCount = 0;
      userCmd.args[0] = 0;
    }
    break;

  case MSG_STATE_CMD:
    if (lastUserData == 'h' && data == 'i')
    {
      userCmd.command = CMD_HI;
      msgState = MSG_STATE_DONE;
    }
    else if (lastUserData == 'm' && data == 's')
    {
      userCmd.command = CMD_MS;
      msgState = MSG_STATE_DONE;
    }
    else if (lastUserData == 's' && data == 'a')
    {
      userCmd.command = CMD_SA;
      msgState = MSG_STATE_DONE;
    }
    else if (lastUserData == 'm' && data == 'm')
    {
      userCmd.command = CMD_MM;
      msgState = MSG_STATE_DATA;
    }
    else if (lastUserData == 'n' && data == 'p')
    {
      userCmd.command = CMD_NP;
      msgState = MSG_STATE_DATA;
    }
    else if (lastUserData == 'm' && data == 'p')
    {
      userCmd.command = CMD_MP;
      msgState = MSG_STATE_DATA;
    }
    else if (lastUserData == 'z' && data == 'm')
    {
      userCmd.command = CMD_ZM;
      msgState = MSG_STATE_DATA;
    }
    else if (lastUserData == 's' && data == 'm')
    {
      userCmd.command = CMD_SM;
      msgState = MSG_STATE_DATA;
    }
    else if (lastUserData == 'p' && data == 'r')
    {
      userCmd.command = CMD_PR;
      msgState = MSG_STATE_DATA;
    }
    else if (lastUserData == 'v' && data == 'e')
    {
      userCmd.command = CMD_VE;
      msgState = MSG_STATE_DATA;
    }
    else if (lastUserData == 'a' && data == 'c')
    {
      userCmd.command = CMD_AC;
      msgState = MSG_STATE_DATA;
    }
    else if (lastUserData == 'b' && data == 'f')
    {
      userCmd.command = CMD_BF;
      msgState = MSG_STATE_DATA;
    }
    else if (lastUserData == 'g' && data == 'o')
    {
      userCmd.command = CMD_GO;
      msgState = MSG_STATE_DONE;
    }
    else if (lastUserData == 'j' && data == 'm') // jm [motor] [destination position]
    {
      userCmd.command = CMD_JM;
      msgState = MSG_STATE_DATA;
    }
    else if (lastUserData == 'i' && data == 'm') // im [motor] [destination position]
    {
      userCmd.command = CMD_IM;
      msgState = MSG_STATE_DATA;
    }
    else if (lastUserData == 'c' && data == 'f') // cf camera focus [value]
    {
      userCmd.command = CMD_CF;
      msgState = MSG_STATE_DATA;
    }
    else if (lastUserData == 'c' && data == 's') // cf camera shutter [value]
    {
      userCmd.command = CMD_CS;
      msgState = MSG_STATE_DATA;
    }
    else if (lastUserData == 'c' && data == 'i') // ci camera image
    {
      userCmd.command = CMD_CI;
      msgState = MSG_STATE_DATA;
    }
    else if (lastUserData == 'c' & data == 'm') // camera mode
    {
      userCmd.command = CMD_CM;
      msgState = MSG_STATE_DONE;
    }
    else if (lastUserData == 'c' && data == 'r') // reset camera mode
    {
      userCmd.command = CMD_CR;
      msgState = MSG_STATE_DONE;
    }
    else if (lastUserData == 't' && data == 'l') // timelapse
    {
      userCmd.command = CMD_TL;
      msgState = MSG_STATE_DATA;
    }
    else if (lastUserData == 'p' && data == 'a') // timelapse
    {
      userCmd.command = CMD_PA;
      msgState = MSG_STATE_DATA;
    }
    else
    {
      // error msg? unknown command?
      msgState = MSG_STATE_START;
    }
    break;

  case MSG_STATE_DATA:
    if (((data >= '0' && data <= '9') || data == '-') && lastUserData == ' ')
    {
      userCmd.argCount++;
      if (userCmd.argCount >= USER_CMD_ARGS)
      {
        dualSerial.print("error: too many args\r\n");
        msgState = MSG_STATE_ERR;
      }
      else
      {
        userCmd.args[userCmd.argCount - 1] = 0;
        if (data == '-')
        {
          msgNumberSign = -1;
        }
        else
        {
          msgNumberSign = 1;
          userCmd.args[userCmd.argCount - 1] = (data - '0');
        }
      }
    }
    else if (data >= '0' && data <= '9')
    {
      userCmd.args[userCmd.argCount - 1] = userCmd.args[userCmd.argCount - 1] * 10 + (data - '0');
    }
    else if (data == ' ' || data == '\r')
    {
      if (lastUserData  >= '0' && lastUserData <= '9')
      {
        if (userCmd.argCount > 0)
          userCmd.args[userCmd.argCount - 1] *= msgNumberSign;
      }
      if (data == '\r')
      {
        msgState = MSG_STATE_DONE;
      }
    }
    break;


  case MSG_STATE_ERR:
    userCmd.command = CMD_NONE;
    msgState = MSG_STATE_DONE;
    break;

  case MSG_STATE_DONE:
    // wait for newline, then reset
    if (data == '\n' && lastUserData == '\r')
    {
      cmd = userCmd.command;
      msgState = MSG_STATE_START;
      lastUserData = 0;
    }
    break;

  default: // unknown state -> revert to begin
    msgState = MSG_STATE_START;
    lastUserData = 0;
  }

  lastUserData = data;

  return cmd;
}

void processSerialCommand()
{
  byte avail = availableSerial();
  byte motor;
  int m;

  for (int i = 0; i < avail; i++)
  {
    int cmd = processUserMessage(readSerial());
    
    if (cmd != CMD_NONE)
    {
      boolean parseError = false;

      motor = userCmd.args[0] - 1;
      
      switch (cmd)
      {
        case CMD_HI:
          sendMessage(MSG_HI, 0);
          break;
        
        case CMD_ZM:
          parseError = (userCmd.argCount != 1 || !isValidMotor(motor));
          if (!parseError)
          {
            motors[motor].position = 0;
            setupMotorMove(motor, 0);
            processGoPosition(motor, 0);
            bitSet(sendPosition, motor);
          }
          break;

        case CMD_MM:
          parseError = (userCmd.argCount != 2 || !isValidMotor(motor));
          if (!parseError)
          {
            processGoPosition(motor, (int32_t)userCmd.args[1]);
          }
          break;

        case CMD_NP:
          parseError = (userCmd.argCount != 2 || !isValidMotor(motor));
          if (!parseError)
          {
            motors[motor].position = userCmd.args[1];
            sendMessage(MSG_MP, motor);
          }
          break;


        case CMD_MP:
          parseError = (userCmd.argCount != 1 || !isValidMotor(motor));
          if (!parseError)
          {
            sendMessage(MSG_MP, motor);
          }
          break;

        case CMD_MS:
          parseError = (userCmd.argCount != 0);
          if (!parseError)
          {
            sendMessage(MSG_MS, 0);
          }
          break;

        case CMD_SM:
          parseError = (userCmd.argCount != 1 || !isValidMotor(motor));
          if (!parseError)
          {
            stopMotor(motor, true);
            sendMessage(MSG_SM, motor);
            sendMessage(MSG_MP, motor);
          }
          break;

        case CMD_SA:
          parseError = (userCmd.argCount != 0);
          if (!parseError)
          {
            hardStop();
            sendMessage(MSG_SA, 0);
          }
          break;

        case CMD_PR:
          parseError = (userCmd.argCount != 2 || !isValidMotor(motor));
          if (!parseError)
          {
            setPulsesPerSecond(motor, (uint16_t)userCmd.args[1], true);
            sendMessage(MSG_PR, motor);
          }
          break;

        case CMD_VE:
          parseError = (userCmd.argCount != 2 || !isValidMotor(motor));
          if (!parseError)
          {
            setPulsesPerSecond(motor, (uint16_t)userCmd.args[1]);
            sendMessage(MSG_VE, motor);
          }
          break;

        case CMD_AC:
          parseError = (userCmd.argCount != 2 || !isValidMotor(motor));
          if (!parseError)
          {
            setMaxAccelerationPerSecond(motor, (uint16_t)userCmd.args[1]);
            sendMessage(MSG_AC, motor);
          }
          break;

        case CMD_BF:
          parseError = motorMoving || userCmd.argCount < 5 || ((userCmd.argCount - 2) % 4) != 0;
          if (!parseError)
          {
            goMoDelayTime = 1000;
            
            int motorCount = (userCmd.argCount - 2) / 4;
            
            for (m = 0; m < MOTOR_COUNT; m++)
            {
              motors[m].gomoMoveTime[0] = 0.0f;
            }

            int32_t destPositions[MOTOR_COUNT];
            for (m = 0; m < motorCount; m++)
            {
              int offset = 2 + m * 4;
              motor = userCmd.args[offset] - 1;
              if (!isValidMotor(motor))
              {
                parseError = true;
                break;
              }
              destPositions[m] = setupBlur(motor, userCmd.args[0], userCmd.args[1], userCmd.args[offset + 1], userCmd.args[offset + 2], userCmd.args[offset + 3]);
            }
            goMoReady = (goMoDelayTime >= 0);
            if (goMoReady)
            {
              for (m = 0; m < motorCount; m++)
              {
                int offset = 2 + m * 4;
                motor = userCmd.args[offset] - 1;
                setupMotorMove(motor, destPositions[m]);
              }
            }
            sendMessage(MSG_BF, 0);

          }
          break;

        case CMD_GO:
          parseError = motorMoving || (userCmd.argCount > 0) || !goMoReady;
          if (!parseError)
          {
            for (m = 0; m < MOTOR_COUNT; m++)
            {
               if (motors[m].gomoMoveTime[0] != 0)
               {
                 int j;
                 for (j = 0; j < P2P_MOVE_COUNT; j++)
                 {
                     motors[m].moveTime[j] = motors[m].gomoMoveTime[j];
                     motors[m].movePosition[j] = motors[m].gomoMovePosition[j];
                     motors[m].moveVelocity[j] = motors[m].gomoMoveVelocity[j];
                     motors[m].moveAcceleration[j] = motors[m].gomoMoveAcceleration[j];
                 }
                 motors[m].destination = motors[m].gomoMovePosition[4]; // TODO change this!
                 motors[m].currentMove = 0;
                 bitSet(motorMoving, m);
               }
            }
            updateMotorVelocities();
            noInterrupts();
            velocityUpdateCounter = VELOCITY_UPDATE_RATE - 1;
            interrupts();
            sendMessage(MSG_GO, 0);            
          }
          break;
          
        case CMD_JM:
          parseError = (userCmd.argCount != 2 || !isValidMotor(motor));
          if (!parseError)
          {
            int32_t destination = 0;
            if (jogMotor(motor, userCmd.args[1], &destination))
            {
              if (!bitRead(motorMoving, motor) || destination != motors[motor].destination)
              {
                setupMotorMove(motor, destination);
              }
            }
            sendMessage(MSG_JM, motor);
          }
          break;

        case CMD_IM:
          parseError = (userCmd.argCount != 2 || !isValidMotor(motor));
          if (!parseError)
          {
            inchMotor(motor, userCmd.args[1]);
            sendMessage(MSG_IM, motor);
          }
          break;

        case CMD_CF:
          parseError = (userCmd.argCount != 1);
          if (!parseError)
            setCameraFocus(userCmd.args[0] == 1);
          break;

        case CMD_CS:
          parseError = (userCmd.argCount != 1);
          if (!parseError)
            setCameraShutter(userCmd.args[0] == 1);
          break;

        case CMD_CI:
          if (userCmd.argCount == 2)
            takeCameraImage(userCmd.args[0], userCmd.args[1]);
          else
            takeCameraImage(3000, 50);
          break;

        case CMD_CM:
          sendMessage(MSG_CM, cameraMode);
          break;

        case CMD_CR:
          resetCameraMode();
          sendMessage(MSG_CR, motor);
          break;
        case CMD_TL:
          parseError = (userCmd.argCount < 7 || (userCmd.argCount - 4) % 3 != 0);
          if (!parseError && cameraMode == CAM_MODE_NONE)
            setupTimelapse(userCmd);
          break;
        case CMD_PA:
          parseError = (userCmd.argCount != 8);
          if (!parseError && cameraMode == CAM_MODE_NONE)
            setupPanorama(userCmd);
          break;
          
        default:
          parseError = true;
          break;
      }
      
      if (parseError)
      {
        dualSerial.print("parse error\r\n");
      }
    }
  }
}


/*
 *
 * Serial transmission.
 *
 */
void sendMessage(byte msg, byte motorIndex)
{
#if defined(BOARD_UNO) || defined(BOARD_MEGA)

  int i = (unsigned int)(txMsgBuffer.head + 1) % TX_MSG_BUF_SIZE;

  if (i != txMsgBuffer.tail)
  {
    txMsgBuffer.buffer[txMsgBuffer.head].msg = msg;
    txMsgBuffer.buffer[txMsgBuffer.head].motor = motorIndex;
    txMsgBuffer.head = i;
    
    if (!*txBufPtr)
      nextMessage();
  }

#else
  int i;
  
  switch (msg)
  {
    case MSG_HI:
      dualSerial.print("hi ");
      dualSerial.print(DFMOCO_VERSION);
      dualSerial.print(" ");
      dualSerial.print(MOTOR_COUNT);
      dualSerial.print(" ");
      dualSerial.print(DFMOCO_VERSION_STRING);
      dualSerial.print("\r\n");
      break;
    case MSG_MM:
      dualSerial.print("mm ");
      dualSerial.print(motorIndex + 1);
      dualSerial.print(" ");
      dualSerial.print(motors[motorIndex].destination);
      dualSerial.print("\r\n");
      break;
    case MSG_MP:
      dualSerial.print("mp ");
      dualSerial.print(motorIndex + 1);
      dualSerial.print(" ");
      dualSerial.print(motors[motorIndex].position);
      dualSerial.print("\r\n");
      break;
    case MSG_MS:
      dualSerial.print("ms ");
      for (i = 0; i < MOTOR_COUNT; i++)
        dualSerial.print(bitRead(motorMoving, i) ? '1' : '0');
      dualSerial.print("\r\n");
      break;
    case MSG_PR:
    case MSG_VE:
      dualSerial.print("pr ");
      dualSerial.print(motorIndex + 1);
      dualSerial.print(" ");
      dualSerial.print((uint16_t)motors[motorIndex].maxVelocity);
      dualSerial.print("\r\n");
      break;
    case MSG_AC:
      dualSerial.print("ac ");
      dualSerial.print(motorIndex + 1);
      dualSerial.print(" ");
      dualSerial.print((uint16_t)motors[motorIndex].maxAcceleration);
      dualSerial.print("\r\n");
      break;
    case MSG_SM:
      dualSerial.print("sm ");
      dualSerial.print(motorIndex + 1);
      dualSerial.print("\r\n");
      break;
    case MSG_SA:
      dualSerial.print("sa\r\n");
      break;
    case MSG_BF:
      dualSerial.print("bf ");
      dualSerial.print(goMoDelayTime);
      dualSerial.print("\r\n");
    case MSG_GO:
      dualSerial.print("go\r\n");
      break;
    case MSG_JM:
      dualSerial.print("jm ");
      dualSerial.print(motorIndex + 1);
      dualSerial.print("\r\n");
      break;
    case MSG_IM:
      dualSerial.print("im ");
      dualSerial.print(motorIndex + 1);
      dualSerial.print("\r\n");
      break;
    case MSG_CM:
      dualSerial.print("cm ");
      dualSerial.print(cameraMode);
      switch (cameraMode)
      {
        case CAM_MODE_TIMELAPSE:
          dualSerial.print(" ");
          dualSerial.print(timelapseData.status);
          dualSerial.print(" ");
          dualSerial.print(timelapseData.executionStatus);
          dualSerial.print(" ");
          dualSerial.print(timelapseData.currentImageCounter);
          dualSerial.print(" ");
          dualSerial.print(timelapseData.imagesCount);
          break;
        case CAM_MODE_PANORAMA:
          dualSerial.print(" ");
          dualSerial.print(panoramaData.status);
          dualSerial.print(" ");
          dualSerial.print(panoramaData.executionStatus);
          dualSerial.print(" ");
          dualSerial.print(panoramaData.currentRowCounter);
          dualSerial.print(" ");
          dualSerial.print(panoramaData.currentColumnCounter);
          break;
      }      
      dualSerial.print("\r\n");
      break;
    case MSG_CR:
      dualSerial.print("cr\r\n");
      break;    
  }
#endif
}

#if defined(BOARD_UNO) || defined(BOARD_MEGA)
void nextMessage()
{
  char *bufPtr;
  int i;
  
  if ((TX_MSG_BUF_SIZE + txMsgBuffer.head - txMsgBuffer.tail) % TX_MSG_BUF_SIZE)
  {
    byte msg = txMsgBuffer.buffer[txMsgBuffer.tail].msg;
    byte motorIndex = txMsgBuffer.buffer[txMsgBuffer.tail].motor;
    txMsgBuffer.tail = (unsigned int)(txMsgBuffer.tail + 1) % TX_MSG_BUF_SIZE;

    switch (msg)
    {
      case MSG_HI:
        sprintf(txBuf, "hi %d %d %s\r\n", DFMOCO_VERSION, MOTOR_COUNT, DFMOCO_VERSION_STRING);
        break;
      case MSG_MM:
        sprintf(txBuf, "mm %d %ld\r\n", motorIndex + 1, motors[motorIndex].destination);
        break;
      case MSG_MP:
        sprintf(txBuf, "mp %d %ld\r\n", motorIndex + 1, motors[motorIndex].position);
        break;
      case MSG_MS:
        sprintf(txBuf, "ms ");
        bufPtr = txBuf + 3;
        for (i = 0; i < MOTOR_COUNT; i++)
          *bufPtr++ = bitRead(motorMoving, i) ? '1' : '0';
        *bufPtr++ = '\r';
        *bufPtr++ = '\n';
        *bufPtr = 0;
        break;
      case MSG_PR:
      case MSG_VE:
        sprintf(txBuf, "pr %d %u\r\n", motorIndex + 1, (uint16_t)motors[motorIndex].maxVelocity);
      case MSG_AC:
        sprintf(txBuf, "ac %d %u\r\n", motorIndex + 1, (uint16_t)motors[motorIndex].maxAcceleration);
        break;
      case MSG_SM:
        sprintf(txBuf, "sm %d\r\n", motorIndex + 1);
        break;
      case MSG_SA:
        sprintf(txBuf, "sa\r\n");
        break;
      case MSG_BF:
        sprintf(txBuf, "bf %d\r\n", goMoDelayTime);
        break;
      case MSG_GO:
        sprintf(txBuf, "go\r\n");
        break;
      case MSG_JM:
        sprintf(txBuf, "jm %d\r\n", motorIndex + 1);
        break;
      case MSG_IM:
        sprintf(txBuf, "im %d\r\n", motorIndex + 1);
        break;
      case MSG_CM:
        sprintf(txBuf, "cm %d\r\n", motorIndex);
        break;
      case MSG_CR:
        sprintf(txBuf, "cr\r\n");
    }
    
    txBufPtr = txBuf;
  }
}
#endif

boolean jogMotor(int motorIndex, int32_t target, int32_t * destination)
{
  Motor *motor = &motors[motorIndex];
  // ideally send motor to distance where decel happens after 2 seconds
  float vi = (motor->dir ? 1 : -1) * VELOCITY_CONVERSION_FACTOR * motor->nextMotorMoveSpeed;
  
  int dir = (target > motor->position) ? 1 : -1;
  // if switching direction, just stop
  if (motor->nextMotorMoveSpeed && motor->dir * dir < 0)
  {
    stopMotor(motorIndex);
    return false;
  }
  if (target == motor->position)
  {
    return false;
  }
  
  float maxVelocity = motor->maxVelocity;
  float maxAcceleration = motor->maxAcceleration;
  
  
  // given current velocity vi
  // compute distance so that decel starts after 0.5 seconds
  // time to accel
  // time at maxvelocity
  // time to decel
  float accelTime = 0, atMaxVelocityTime = 0;
  if (fabs(vi) < maxVelocity)
  {
    accelTime = (maxVelocity - fabs(vi)) / maxAcceleration;
    if (accelTime < 0.5f)
    {
      atMaxVelocityTime = 0.5f - accelTime;
    }
    else
    {
      accelTime = 0.5f;
    }
  }
  else
  {
    atMaxVelocityTime = 0.5f;
  }
  float maxVelocityReached = fabs(vi) + maxAcceleration * accelTime;

  int32_t delta = fabs(vi) * accelTime + (0.5f * maxAcceleration * accelTime * accelTime);
  delta += atMaxVelocityTime * maxVelocityReached;
  delta += 0.5f * (maxVelocityReached * maxVelocityReached) / maxAcceleration; // = 0.5 * a * t^2 -> t = (v/a)
  
  int32_t dest = motor->position + dir * delta;
  
  // now clamp to target
  if ( (dir == 1 && dest > target) || (dir == -1 && dest < target) )
  {
    dest = target;
  }
  *destination = dest;
  return true;
}

void inchMotor(int motorIndex, int32_t target)
{
  Motor *motor = &motors[motorIndex];
  // ideally send motor to distance where decel happens after 2 seconds
  
  // if switching direction, just stop
  int dir = (target > motor->destination) ? 1 : -1;
  
  if (motor->nextMotorMoveSpeed)// && motor->dir * dir < 0)
  {
    stopMotor(motorIndex);
    return;
  }

  int32_t dest = motor->destination + dir * 2;
  
  // now clamp to target
  if ( (dir == 1 && dest > target) || (dir == -1 && dest < target) )
  {
    dest = target;
  }
  //setupMotorMove(motorIndex, dest);
  
  int i, moveCount;
  moveCount = 0;

  for (i = 0; i < P2P_MOVE_COUNT; i++)
  {
    motor->moveTime[i] = 0;
    motor->moveVelocity[i] = 0;
    motor->moveAcceleration[i] = 0;
  }
  motor->currentMoveTime = 0;
  motor->moveTime[0] = 0.01f;
  motor->movePosition[0] = motor->position;
  motor->movePosition[1] = motor->position + dir * 2;
  motor->currentMove = 0;
 
  motor->destination = dest;

  if ( dest != motor->position )
  {
    bitSet(motorMoving, motorIndex);
  }
}

void calculatePointToPoint(int motorIndex, int32_t destination)
{
  Motor *motor = &motors[motorIndex];
  
  int i, moveCount;
  moveCount = 0;

  for (i = 0; i < P2P_MOVE_COUNT; i++)
  {
    motor->moveTime[i] = 0;
    motor->moveVelocity[i] = 0;
    motor->moveAcceleration[i] = 0;
  }
  motor->currentMoveTime = 0;
  motor->movePosition[0] = motor->position;

  float tmax = motor->maxVelocity / motor->maxAcceleration;
  float dmax = motor->maxVelocity * tmax;
  
  float dist = abs(destination - motor->position);
  int dir = destination > motor->position ? 1 : -1;
  
  if (motor->nextMotorMoveSpeed > 5) // we need to account for existing velocity
  {
    float vi = (motor->dir ? 1 : -1) * VELOCITY_CONVERSION_FACTOR * motor->nextMotorMoveSpeed;
    float ti = fabs(vi / motor->maxAcceleration);
    float di = 0.5f * motor->maxAcceleration * ti * ti;
    
    if (vi * dir < 0) // switching directions
    {
      motor->moveTime[moveCount] = ti;
      motor->moveAcceleration[moveCount] = dir * motor->maxAcceleration;
      motor->moveVelocity[moveCount] = vi;
      moveCount++;
      
      dist += di;
    }
    else if (dist < di) // must decelerate and switch directions
    {
      motor->moveTime[moveCount] = ti;
      motor->moveAcceleration[moveCount] = -dir * motor->maxAcceleration;
      motor->moveVelocity[moveCount] = vi;
      moveCount++;

      dist = (di - dist);
      dir = -dir;
    }
    else // further on in same direction
    {
      dist += di;
      motor->movePosition[0] -= dir * di;

      motor->currentMoveTime = ti;
    }
  }

  float t = tmax;
  if (dist <= dmax)
  {
    t = sqrt(dist / motor->maxAcceleration);
  }
    
  motor->moveTime[moveCount] = t;
  motor->moveAcceleration[moveCount] = dir * motor->maxAcceleration;
  
  if (dist > dmax)
  {
    moveCount++;
    dist -= dmax;
    float tconst = dist / motor->maxVelocity;
    motor->moveTime[moveCount] = tconst;
    motor->moveAcceleration[moveCount] = 0;
  }

  moveCount++;
  motor->moveTime[moveCount] = t;
  motor->moveAcceleration[moveCount] = dir * -motor->maxAcceleration;


  for (i = 1; i <= moveCount; i++)
  {
    float t = motor->moveTime[i - 1];
    motor->movePosition[i] = (int32_t)(motor->movePosition[i - 1] + motor->moveVelocity[i - 1] * t + 0.5f * motor->moveAcceleration[i - 1] * t * t);
    motor->moveVelocity[i] = motor->moveVelocity[i - 1] + motor->moveAcceleration[i - 1] * t;
  }
  motor->movePosition[moveCount + 1] = destination;
  for (i = 0; i <= moveCount; i++)
  {
    motor->moveAcceleration[i] *= 0.5f; // pre-multiply here for later position calculation
  }
  motor->currentMove = 0;
  
  return;

}

int32_t setupBlur(int motorIndex, int exposure, int blur, int32_t p0, int32_t p1, int32_t p2)
{
  Motor *motor = &motors[motorIndex];
  int i;
  
  float b = blur * 0.001f;
  float expTime = exposure * 0.001f;
  
  p0 = p1 + b * (p0 - p1);
  p2 = p1 + b * (p2 - p1);

  for (i = 0; i < P2P_MOVE_COUNT; i++)
  {
    motor->gomoMoveTime[i] = 0;
    motor->gomoMoveVelocity[i] = 0;
    motor->gomoMoveAcceleration[i] = 0;
  }
  
  motor->gomoMovePosition[1] = p0;
  motor->gomoMoveTime[1] = expTime * 0.5f;
  motor->gomoMoveVelocity[1] = (float)(p1 - p0) / (expTime * 0.5f);

  motor->gomoMovePosition[2] = p1;
  motor->gomoMoveTime[2] = expTime * 0.5f;
  motor->gomoMoveVelocity[2] = (float)(p2 - p1) / (expTime * 0.5f);

  if (fabs(motor->gomoMoveVelocity[1]) > MAX_VELOCITY || fabs(motor->gomoMoveVelocity[2]) > MAX_VELOCITY)
    goMoDelayTime = -1; // can not reach this speed

  // v = a*t -> a = v / t
  float accelTime = 1.0f;
  float a = motor->gomoMoveVelocity[1] / accelTime;
  float dp = 0.5f * a * accelTime * accelTime;
  float sp = p0 - dp; // starting position

  motor->gomoMovePosition[0] = sp;
  motor->gomoMoveTime[0] = accelTime;
  motor->gomoMoveAcceleration[0] = 0.5f * a; // pre-multiplied

  a = motor->gomoMoveVelocity[2] / accelTime;
  dp = 0.5f * a * accelTime * accelTime;
  float fp = p2 + dp;

  motor->gomoMovePosition[3] = p2;
  motor->gomoMoveTime[3] = accelTime;
  motor->gomoMoveVelocity[3] = motor->gomoMoveVelocity[2];
  motor->gomoMoveAcceleration[3] = -0.5f * a; // pre-multiplied

  motor->gomoMovePosition[4] = fp;

  return (int32_t)sp;
}

void IRAM_ATTR delayMilliseconds(uint32_t us)
{
    uint32_t m = millis();
    if(us){
        uint32_t e = (m + us);
        if(m > e){ //overflow
            while(millis() > e){
                NOP();
            }
        }
        while(millis() < e){
            NOP();
        }
    }
}

void takeCameraImage(int delayFocus, int delayShutter) {
  setCameraFocus(false);
  setCameraShutter(false);
  setCameraFocus(true);
  if (delayFocus > 0) {
    delayMilliseconds(delayFocus);
  }
  setCameraShutter(true);
  delayMilliseconds(delayShutter);
  setCameraFocus(false);
  setCameraShutter(false);
}

void setCameraFocus(boolean value) {
  if (value) {
    PIN_ON(0, FOCUS_PIN);
  } else {
    PIN_OFF(0, FOCUS_PIN);    
  }
}

void setCameraShutter(boolean value) {
  if (value) {
    PIN_ON(0, SHUTTER_PIN);
  } else {
    PIN_OFF(0, SHUTTER_PIN);    
  }
}

void resetCameraMode() {
  cameraMode = CAM_MODE_NONE;
  resetCameraModeSettings();
}

void resetCameraModeSettings() {
  if (timerCameraMode != NULL) {
    timerEnd(timerCameraMode);
    timerCameraMode = NULL;
  }
  if (timerCameraModeMotion != NULL) {
    timerEnd(timerCameraModeMotion);
    timerCameraModeMotion = NULL;
  }
  
  setCameraShutter(false);
  setCameraFocus(false);  
}

void processCameraMode() {
  switch(cameraMode) {
    case CAM_MODE_TIMELAPSE:
      if (timelapseData.status == TIMELAPSE_RUNNING)
        processTimelapse();
      break;
    case CAM_MODE_PANORAMA:
      if (panoramaData.status == PANORAMA_RUNNING)
        processPanorama();
      break;
  }
}

void IRAM_ATTR executeTimelaseMotion(void) {
  setCameraShutter(false);
  setCameraFocus(false);

  if (timelapseData.currentImageCounter >= timelapseData.imagesCount) {
    timelapseData.status = TIMELAPSE_DONE;
    resetCameraModeSettings();
    return;
  }

  for (int i = 0; i < MOTOR_COUNT; i++) {
    TimelapseMotor motor = timelapseData.motors[i];
    if (motor.enable) {
      uint32_t newPosition = motor.startPosition + (timelapseData.currentImageCounter * motor.steps);
      timelapseData.motors[i].lastPosition = newPosition;
      processGoPosition(i, newPosition);
    }
  }
}

void IRAM_ATTR executeTimelapseImage() {
  for (int i = 0; i < MOTOR_COUNT; i++) {
    TimelapseMotor motor = timelapseData.motors[i];
    if (motor.enable && motor.lastPosition != motors[i].position) {
      timelapseData.status = TIMELAPSE_ERROR_POSITION;
      resetCameraModeSettings();
      return;
    }
  }

  timelapseData.currentImageCounter++;

  setCameraShutter(true);
  setCameraFocus(true);  

  timerCameraModeMotion = timerBegin(2, 80, true);                
  timerAttachInterrupt(timerCameraModeMotion, &executeTimelaseMotion, true);
  timerAlarmWrite(timerCameraModeMotion, timelapseData.exposureTimeMillis * 1000, false);
  timerAlarmEnable(timerCameraModeMotion);
}

void setupTimelapse(UserCmd userCmd) {  
  cameraMode = CAM_MODE_TIMELAPSE;
  timelapseData.status = TIMELAPSE_SETUP;

  timelapseData.currentImageCounter = 0;
  timelapseData.imagesCount = userCmd.args[0];
  if (timelapseData.imagesCount <= 1) {
    timelapseData.status = TIMELAPSE_ERROR_ARG1_GT_1;
    return;
  }
  timelapseData.intervalSeconds = userCmd.args[1];
  if (timelapseData.intervalSeconds <= 0) {
    timelapseData.status = TIMELAPSE_ERROR_ARG2_GT_0;
    return;
  }
  timelapseData.exposureTimeMillis = userCmd.args[2];
  if (timelapseData.exposureTimeMillis <= 0) {
    timelapseData.status = TIMELAPSE_ERROR_ARG3_GT_0;
    return;
  }
  timelapseData.restMoveTime = userCmd.args[3];
  if (timelapseData.restMoveTime < 0) {
    timelapseData.status = TIMELAPSE_ERROR_ARG4_GE_0;
    return;
  }

  for (int i = 0; i < MOTOR_COUNT; i++) {
    timelapseData.motors[i].enable = false;
    timelapseData.motors[i].steps = 0;
    timelapseData.motors[i].startPosition = 0;
    timelapseData.motors[i].endPosition = 0;
  }
  for (int j = 4; j < userCmd.argCount && j < (4 + (MOTOR_COUNT * 3)); j+=3) {
    uint32_t motorNumber = userCmd.args[j] - 1;
    uint32_t startPosition = userCmd.args[j + 1];
    uint32_t endPosition = userCmd.args[j + 2];
    uint32_t distance = endPosition - startPosition;
    uint32_t steps = distance / (timelapseData.imagesCount - 1);
    uint32_t newEndPosition = startPosition + ((timelapseData.imagesCount - 1) * steps);
    timelapseData.motors[motorNumber].enable = true;
    timelapseData.motors[motorNumber].steps = steps;
    timelapseData.motors[motorNumber].lastPosition = startPosition;
    timelapseData.motors[motorNumber].startPosition = startPosition;
    timelapseData.motors[motorNumber].endPosition = newEndPosition;
    
    Motor *motor = &motors[motorNumber];

    float moveTime = 0.0f;
    calculatePointToPoint(motorNumber, motor->position + steps);
    for (int k = 0; k < P2P_MOVE_COUNT; k++)
    {
      if (motor->moveTime[k] == 0)
        break;
      
      moveTime += motor->moveTime[k];
    }

    if ( (timelapseData.exposureTimeMillis + ( 1.05 * 1000 * moveTime ) + timelapseData.restMoveTime) > (timelapseData.intervalSeconds * 1000) ) {
      timelapseData.status = TIMELAPSE_ERROR_MOVE_SHOOT_TIME;
      return;
    }
  }


  sendMessage(MSG_GO, 0);
  timelapseData.status = TIMELAPSE_RUNNING;

  for (int i = 0; i < MOTOR_COUNT; i++) {
    TimelapseMotor motor = timelapseData.motors[i];
    if (motor.enable) {
      processGoPosition(i, motor.startPosition);
    }
  }

  timelapseData.executionStatus = TIMELAPSE_INIT_MOVEMENT;  
}


void processTimelapse() {
  boolean movementReady = true;

  switch(timelapseData.executionStatus) {
    case TIMELAPSE_INIT_MOVEMENT:
      for (int i = 0; i < MOTOR_COUNT; i++) {
        TimelapseMotor motor = timelapseData.motors[i];
        if (motor.enable && movementReady) {
          movementReady = motors[i].positionReached;
        }
      }

      if (movementReady)
        timelapseData.executionStatus = TIMELAPSE_START_EXECUTION;

      break;
    
    case TIMELAPSE_START_EXECUTION:
      timerCameraMode = timerBegin(1, 80, true);                
      timerAttachInterrupt(timerCameraMode, &executeTimelapseImage, true);
      timerAlarmWrite(timerCameraMode, 1000000 * timelapseData.intervalSeconds, true);
        
      timelapseData.executionStatus = TIMELAPSE_EXECUTING;
      timerAlarmEnable(timerCameraMode);
      break;
  }
}

void setupPanorama(UserCmd userCmd) {
  cameraMode = CAM_MODE_PANORAMA;
  panoramaData.status = PANORAMA_SETUP;

  panoramaData.currentRowCounter = 1;
  panoramaData.currentColumnCounter = 1;
  
  panoramaData.motorRow = userCmd.args[0];
  if (panoramaData.motorRow < 1 || panoramaData.motorRow > MOTOR_COUNT) {
    panoramaData.status = PANORAMA_ERROR_MTR_OUT;
    return;
  }
  panoramaData.motorRow--;

  panoramaData.imagesRow = userCmd.args[1];
  if (panoramaData.imagesRow <= 1) {
    panoramaData.status = PANORAMA_ERROR_IMG_LE_1;
    return;
  }
  panoramaData.stepsRow = userCmd.args[2];
  if (panoramaData.imagesRow == 0) {
    panoramaData.status = PANORAMA_ERROR_STP_EQ_0;
    return;
  }

  panoramaData.motorColumn = userCmd.args[3];
  if (panoramaData.motorColumn < 1 || panoramaData.motorColumn > MOTOR_COUNT) {
    panoramaData.status = PANORAMA_ERROR_MTR_OUT;
    return;
  }
  panoramaData.motorColumn--;

  panoramaData.imagesColumn = userCmd.args[4];
  if (panoramaData.imagesColumn <= 1) {
    panoramaData.status = PANORAMA_ERROR_IMG_LE_1;
    return;
  }
  panoramaData.stepsColumn = userCmd.args[5];
  if (panoramaData.imagesColumn == 0) {
    panoramaData.status = PANORAMA_ERROR_STP_EQ_0;
    return;
  }
  
  panoramaData.exposureTimeMillis = userCmd.args[6];
  if (panoramaData.exposureTimeMillis < 50) {
    panoramaData.status = PANORAMA_ERROR_EXP_LT_50;
    return;
  }
  
  panoramaData.restMoveTime = userCmd.args[7];
  if (panoramaData.restMoveTime < 1) {
    panoramaData.status = PANORAMA_ERROR_RST_LT_1;
    return;
  }
  
  panoramaData.status = PANORAMA_RUNNING;
  panoramaData.executionStatus = PANORAMA_REST;
  sendMessage(MSG_GO, 0);
}

void processPanorama() {
  int32_t newPositionRow;
  int32_t newPositionColumn;

  switch(panoramaData.executionStatus) {
    case PANORAMA_REST:
      panoramaData.restStartMillis = millis();
      panoramaData.executionStatus = PANORAMA_REST_RUNNING;
      break;
    case PANORAMA_REST_RUNNING:
      if ((millis() - panoramaData.restStartMillis) > (panoramaData.restMoveTime))
        panoramaData.executionStatus = PANORAMA_IMAGE;
      break;
    case PANORAMA_IMAGE:
      takeCameraImage(0, panoramaData.exposureTimeMillis);
      panoramaData.executionStatus = PANORAMA_READY_FOR_MOVE;
      break;
    case PANORAMA_READY_FOR_MOVE:
      newPositionRow = motors[panoramaData.motorRow].position;
      newPositionColumn = motors[panoramaData.motorColumn].position;
  
      panoramaData.executionStatus = PANORAMA_MOVE;

      panoramaData.currentRowCounter++;
      if (panoramaData.currentRowCounter > panoramaData.imagesRow) {
        newPositionRow -= panoramaData.stepsRow * (panoramaData.imagesRow - 1);
        panoramaData.currentRowCounter = 1;
        panoramaData.currentColumnCounter++;

        if (panoramaData.currentColumnCounter > panoramaData.imagesColumn) {
          newPositionColumn -= panoramaData.stepsColumn * (panoramaData.imagesColumn - 1); 
          panoramaData.currentRowCounter = 1;
          panoramaData.currentColumnCounter = 1;
          panoramaData.executionStatus = PANORAMA_MOVE_END;
        } else {
          newPositionColumn += panoramaData.stepsColumn;
        }
      } else {
        newPositionRow += panoramaData.stepsRow;
      }

      processGoPosition(panoramaData.motorRow, newPositionRow);
      processGoPosition(panoramaData.motorColumn, newPositionColumn);    
      break;
    case PANORAMA_MOVE:      
      if (motors[panoramaData.motorRow].positionReached && motors[panoramaData.motorColumn].positionReached)
        panoramaData.executionStatus = PANORAMA_REST;
      break;
    case PANORAMA_MOVE_END:      
      if (motors[panoramaData.motorRow].positionReached && motors[panoramaData.motorColumn].positionReached)
        panoramaData.status = PANORAMA_DONE;
      break;
  }
}
