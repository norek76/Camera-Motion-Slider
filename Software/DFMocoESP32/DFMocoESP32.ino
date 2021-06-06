#include <Arduino.h>
#include <math.h>
#include <BluetoothSerial.h>

#define DFMOCO_VERSION 1
#define DFMOCO_VERSION_STRING "1.6.0"

#define USER_CMD_ARGS 40
struct UserCmd
{
  byte command;
  byte argCount;
  int32_t args[USER_CMD_ARGS];
};

boolean availableSerial();
int readSerial();
void bluetoothCallback(esp_spp_cb_event_t event, esp_spp_cb_param_t *param);
void IRAM_ATTR updateStepDirection(void);
void hardStop();
void updateMotorVelocities();
void setMaxAccelerationPerSecond(int motorIndex, uint16_t accelerationPerSecond);
void setPulsesPerSecond(int motorIndex, uint16_t pulsesPerSecond, boolean setRamp = false);
void setupMotorMove(int motorIndex, int32_t destination);
void stopMotor(int motorIndex, boolean fastStop = false, boolean hardStop = false);
boolean isValidMotor(int motorIndex);
void processGoPosition(int motorIndex, int32_t pos);
byte processUserMessage(char data);
void processSerialCommand();
void sendMessage(byte msg, byte motorIndex);
void sendMessageQueue(byte msg, byte motorIndex);
boolean jogMotor(int motorIndex, int32_t target, int32_t *destination);
void inchMotor(int motorIndex, int32_t target);
void calculatePointToPoint(int motorIndex, int32_t destination);
int32_t setupBlur(int motorIndex, int exposure, int blur, int32_t p0, int32_t p1, int32_t p2);
void IRAM_ATTR delayMilliseconds(uint32_t us);
void takeCameraImage(int delayFocus, int delayShutter);
void setCameraFocus(boolean value);
void setCameraShutter(boolean value);
void resetCameraMode();
void resetCameraModeSettings();
void processCameraMode();
void IRAM_ATTR executeTimelaseMotion(void);
void IRAM_ATTR executeTimelapseImage();
void setupTimelapse(UserCmd userCmd);
void processTimelapse();
void setupPanorama(UserCmd userCmd);
void processPanorama();

/*
  DFMoco version 1.5.2
  
  Multi-axis motion control.
  For use with the Arc motion control system in Dragonframe 4.
  Generates step and direction signals, which can be sent to stepper motor drivers.

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
*/

#define SERIAL_DEVICE Serial
#define SERIAL_DEVICE_BT SerialBT
#define BLUETOOTH_LED_PIN 26

#define FOCUS_PIN 14
#define SHUTTER_PIN 27
#define ENABLE_PIN 25

#define PIN_ON(port, pin) \
  {                       \
    digitalWrite(pin, 1); \
  }
#define PIN_OFF(port, pin) \
  {                        \
    digitalWrite(pin, 0);  \
  }

#define MOTOR_COUNT 4
#define TIME_CHUNK 50
#define SEND_POSITION_COUNT 20000

// update velocities 20 x second
#define VELOCITY_UPDATE_RATE (50000 / TIME_CHUNK)
#define VELOCITY_INC(maxrate) (max(1.0f, maxrate / 70.0f))
#define VELOCITY_CONVERSION_FACTOR 0.30517578125f /* 20 / 65.536f */

#define MAX_VELOCITY 12000
#define DEFAUL_VELOCITY 1500
#define MIN_VELOCITY 100
#define MAX_ACCELERATION 2 * MAX_VELOCITY
#define MIN_ACCELERATION 2 * MIN_VELOCITY

#define MOTOR0_STEP_PORT 0
#define MOTOR0_STEP_PIN 16

#define MOTOR1_STEP_PORT 0
#define MOTOR1_STEP_PIN 18

#define MOTOR2_STEP_PORT 0
#define MOTOR2_STEP_PIN 22

#define MOTOR3_STEP_PORT 0
#define MOTOR3_STEP_PIN 32

#define TX_UCSRA UCSR0A
#define TX_UDRE UDRE0
#define TX_UDR UDR0
char txBuf[32];
char *txBufPtr;

#define TX_MSG_BUF_SIZE 16

#define MSG_STATE_START 0
#define MSG_STATE_CMD 1
#define MSG_STATE_DATA 2
#define MSG_STATE_ERR 3

#define MSG_STATE_DONE 100

/*
 * Command codes from user
 */
#define CMD_NONE 0
#define CMD_HI 10
#define CMD_MS 30
#define CMD_NP 31
#define CMD_MM 40 // move motor
#define CMD_PR 41 // pulse rate
#define CMD_SM 42 // stop motor
#define CMD_MP 43 // motor position
#define CMD_ZM 44 // zero motor
#define CMD_SA 50 // stop all (hard)
#define CMD_BF 60 // blur frame
#define CMD_GO 61 // go!

#define CMD_JM 70 // jog motor
#define CMD_IM 71 // inch motor

#define CMD_VE 80 // velocity
#define CMD_AC 81 // acceleration

#define CMD_CF 90 // camera focus
#define CMD_CS 91 // camera shutter
#define CMD_CI 92 // camera take image

#define CMD_CM 100 // camera mode
#define CMD_CR 101 // reset camera mode
#define CMD_TL 102 // timelapse
#define CMD_PA 103 // panorama

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

#define CAM_MODE_NONE 0
#define CAM_MODE_TIMELAPSE 1
#define CAM_MODE_PANORAMA 2
#define CAM_MODE_RESETTING 10

// Timelapse Status
#define TIMELAPSE_SETUP 0
#define TIMELAPSE_RUNNING 1
#define TIMELAPSE_DONE 2
// Timelapse EXECUTION
#define TIMELAPSE_INIT_MOVEMENT 0
#define TIMELAPSE_START_EXECUTION 1
#define TIMELAPSE_REST 2
#define TIMELAPSE_IMAGE 3
#define TIMELAPSE_MOVE 4
// Timelapse Errors
#define TIMELAPSE_ERROR_INTERVAL_LT_1 10
#define TIMELAPSE_ERROR_EXPOSURE_LT_1 11
#define TIMELAPSE_ERROR_REST_LT_0 12
#define TIMELAPSE_ERROR_POSITION_COUNT_BETWEEN_2_4 13
#define TIMELAPSE_ERROR_IMAGES_LE_1 14
#define TIMELAPSE_ERROR_IMAGES_NOT_IN_ROW 15
#define TIMELAPSE_ERROR_RAMP_BETWEEN_0_40 16
#define TIMELAPSE_ERROR_ACC_RAMP 17
#define TIMELAPSE_ERROR_MOVE_SHOOT_TIME 18
#define TIMELAPSE_ERROR_POSITION 19
// Panorama Status
#define PANORAMA_SETUP 0
#define PANORAMA_RUNNING 1
#define PANORAMA_DONE 2
// Panorama Execution Status
#define PANORAMA_REST 0
#define PANORAMA_REST_RUNNING 1
#define PANORAMA_IMAGE 2
#define PANORAMA_READY_FOR_MOVE 3
#define PANORAMA_MOVE 4
#define PANORAMA_MOVE_END 5
// Panorama Errors
#define PANORAMA_ERROR_MTR_OUT 10
#define PANORAMA_ERROR_IMG_LE_1 11
#define PANORAMA_ERROR_STP_EQ_0 12
#define PANORAMA_ERROR_EXP_LT_50 13
#define PANORAMA_ERROR_RST_LT_1 14

#define MAX_POSITION_COUNT 4
#define MAX_ITERATION_COUNT MAX_POSITION_COUNT - 1

struct TimelapseMotor
{
  boolean enable;
  int32_t time[MAX_ITERATION_COUNT][4];
  double acc[MAX_ITERATION_COUNT];
  double dec[MAX_ITERATION_COUNT];
  double v0[MAX_ITERATION_COUNT];
  double v1[MAX_ITERATION_COUNT];
  uint32_t velocity;
  uint32_t acceleration;
  int32_t positions[MAX_POSITION_COUNT];
  int32_t lastPosition;
};

struct Timelapse
{
  TimelapseMotor motors[MOTOR_COUNT];
  uint32_t intervalSeconds;
  uint32_t imagesCount[3];
  uint32_t exposureTimeMillis;
  uint32_t restMoveTime;
  uint32_t ramp;
  uint32_t positionCount;
  uint32_t currentIteration;
  uint32_t currentIterationImageCounter;
  uint32_t totalImageCounter;
  uint32_t totalImages;
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
/*
 * Message state machine variables.
 */
byte lastUserData;
int msgState;
int msgNumberSign;
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
BluetoothSerial SerialBT;

/*
 Motor data.
 */
uint16_t motorAccumulator0;
uint16_t motorAccumulator1;
uint16_t motorAccumulator2;
uint16_t motorAccumulator3;
uint16_t *motorAccumulator[MOTOR_COUNT] =
    {
        &motorAccumulator0,
        &motorAccumulator1,
        &motorAccumulator2,
        &motorAccumulator3,
};

uint16_t motorMoveSteps0;
uint16_t motorMoveSteps1;
uint16_t motorMoveSteps2;
uint16_t motorMoveSteps3;
uint16_t *motorMoveSteps[MOTOR_COUNT] =
    {
        &motorMoveSteps0,
        &motorMoveSteps1,
        &motorMoveSteps2,
        &motorMoveSteps3,
};

uint16_t motorMoveSpeed0;
uint16_t motorMoveSpeed1;
uint16_t motorMoveSpeed2;
uint16_t motorMoveSpeed3;
uint16_t *motorMoveSpeed[MOTOR_COUNT] =
    {
        &motorMoveSpeed0,
        &motorMoveSpeed1,
        &motorMoveSpeed2,
        &motorMoveSpeed3,
};

volatile boolean nextMoveLoaded;

unsigned int velocityUpdateCounter;
byte sendPositionCounter;

byte sendPosition = 0;
byte motorMoving = 0;
byte toggleStep = 0;

#define P2P_MOVE_COUNT 7
struct Motor
{
  byte stepPin;
  byte dirPin;

  // pre-computed move
  float moveTime[P2P_MOVE_COUNT];
  int32_t movePosition[P2P_MOVE_COUNT];
  float moveVelocity[P2P_MOVE_COUNT];
  float moveAcceleration[P2P_MOVE_COUNT];

  float gomoMoveTime[P2P_MOVE_COUNT];
  int32_t gomoMovePosition[P2P_MOVE_COUNT];
  float gomoMoveVelocity[P2P_MOVE_COUNT];
  float gomoMoveAcceleration[P2P_MOVE_COUNT];

  int currentMove;
  float currentMoveTime;

  volatile boolean positionReached;
  volatile boolean dir;

  int32_t position;
  int32_t destination;
  float maxVelocity;
  float maxAcceleration;

  uint16_t nextMotorMoveSteps;
  float nextMotorMoveSpeed;
};

boolean goMoReady;
int goMoDelayTime;

Motor motors[MOTOR_COUNT];

unsigned long lastBluetoothLedMillis = millis();
boolean serialBluetoothEnabled = false;

boolean lastBluetoothLedState = true;
hw_timer_t *timerMotion = NULL;
portMUX_TYPE timerMotionMux = portMUX_INITIALIZER_UNLOCKED;

hw_timer_t *timerCameraMode = NULL;
hw_timer_t *timerCameraModeMotion = NULL;

byte cameraMode = CAM_MODE_NONE;
QueueHandle_t messageQueue;
int messageCounter = 0;

class DualPrint : public Print
{
public:
  DualPrint() : serialBluetoothEnabled(false) {}
  virtual size_t write(uint8_t c)
  {
    if (serialBluetoothEnabled)
      SERIAL_DEVICE_BT.write(c);
    else
      SERIAL_DEVICE.write(c);
    return 1;
  }

  boolean serialBluetoothEnabled;
} dualSerial;

boolean availableSerial()
{
  if (serialBluetoothEnabled)
  {
    return SERIAL_DEVICE_BT.available();
  }
  return SERIAL_DEVICE.available();
}

int readSerial()
{
  if (serialBluetoothEnabled)
  {
    return SERIAL_DEVICE_BT.read();
  }
  return SERIAL_DEVICE.read();
}

void bluetoothCallback(esp_spp_cb_event_t event, esp_spp_cb_param_t *param)
{
  if (event == ESP_SPP_SRV_OPEN_EVT)
  {
    PIN_ON(0, BLUETOOTH_LED_PIN);
    dualSerial.serialBluetoothEnabled = true;
    serialBluetoothEnabled = true;
  }
  if (event == ESP_SPP_CLOSE_EVT)
  {
    dualSerial.serialBluetoothEnabled = false;
    serialBluetoothEnabled = false;
    lastBluetoothLedState = false;
    lastBluetoothLedMillis = millis();
    PIN_OFF(0, BLUETOOTH_LED_PIN);
  }
}

void IRAM_ATTR updateStepDirection(void)
{
  portENTER_CRITICAL_ISR(&timerMotionMux);
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
  }
  portEXIT_CRITICAL_ISR(&timerMotionMux);
}

void setup()
{
  // setup serial connection
  SERIAL_DEVICE.begin(57600);

  messageQueue = xQueueCreate(200, sizeof(word));
  if (messageQueue == NULL)
  {
    Serial.println("Error creating the queue");
  }

  if (SERIAL_DEVICE_BT.begin("DFMoCo"))
  {
    SERIAL_DEVICE_BT.register_callback(bluetoothCallback);
  }

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

  for (int i = 0; i < 32; i++)
    txBuf[i] = 0;

  txBufPtr = txBuf;

  // initialize motor structures
  for (int i = 0; i < MOTOR_COUNT; i++)
  {
    // setup motor pins - you can customize/modify these after loop
    // default sets step/dir pairs together, with first four motors at 4/5, 6/7, 8/9, 10/11
    // then, for the Mega boards, it jumps to 28/29, 30/31, 32/33, 34/35
    switch (i)
    {
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

    motors[i].dirPin = motors[i].stepPin + 1;
    motors[i].dir = true; // forward
    motors[i].positionReached = true;
    motors[i].position = 0L;
    motors[i].destination = 0L;

    motors[i].nextMotorMoveSteps = 0;
    motors[i].nextMotorMoveSpeed = 0;

    setPulsesPerSecond(i, DEFAUL_VELOCITY, true);

    for (int i = 0; i < MOTOR_COUNT; i++)
    {
      pinMode(motors[i].stepPin, OUTPUT);
      pinMode(motors[i].dirPin, OUTPUT);
    }
    // set initial direction
    for (int i = 0; i < MOTOR_COUNT; i++)
    {
      digitalWrite(motors[i].dirPin, motors[i].dir ? HIGH : LOW);
    }
  }

  sendMessage(MSG_HI, 0);

  timerMotion = timerBegin(0, 80, true);
  timerAttachInterrupt(timerMotion, &updateStepDirection, true);
  timerAlarmWrite(timerMotion, 25, true);
  timerAlarmEnable(timerMotion);
}

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
    unsigned int element;
    messageCounter++;
    if (messageCounter % 3 == 0 && xQueueReceive(messageQueue, &element, 0) == pdTRUE)
    {
      sendMessageQueue(highByte(element), lowByte(element));
    }

    if (!serialBluetoothEnabled && ((millis() - lastBluetoothLedMillis) > 600))
    {
      if (lastBluetoothLedState)
      {
        PIN_OFF(0, BLUETOOTH_LED_PIN);
      }
      else
      {
        PIN_ON(0, BLUETOOTH_LED_PIN);
      }
      lastBluetoothLedState = !lastBluetoothLedState;
      lastBluetoothLedMillis = millis();
    }
    if (!nextMoveLoaded)
      updateMotorVelocities();

    processSerialCommand();
    if (cameraMode != CAM_MODE_NONE)
    {
      processCameraMode();
    }

    if (!sendPositionCounter)
    {
      sendPositionCounter = 20;

      byte i;
      for (i = 0; i < MOTOR_COUNT; i++)
      {
        if (bitRead(motorMoving, i) || bitRead(sendPosition, i))
        {
          if (cameraMode == CAM_MODE_NONE || bitRead(sendPosition, i))
          {
            sendMessage(MSG_MP, i);
          }
          ramValues[i] = motors[i].position;
          ramNotValues[i] = ~motors[i].position;
        }
        if (motors[i].position == motors[i].destination)
        {
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

void IRAM_ATTR updateMotorVelocities()
{
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
          motor->nextMotorMoveSpeed = _max(1, _min(65535, dx * 65.6f));
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
  if (setRamp)
  {
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
    stopMotor(i, false, true);
  }
}

void normalStop()
{
  // set the destination to the current location, so they won't move any more
  for (int i = 0; i < MOTOR_COUNT; i++)
  {
    stopMotor(i, true, false);
  }
}

void stopMotor(int motorIndex, boolean fastStop, boolean hardStop)
{
  int32_t delta = (motors[motorIndex].destination - motors[motorIndex].position);
  if (!delta)
  {
    // motors[motorIndex].positionReached = true;
    // motors[motorIndex].nextMotorMoveSpeed = 0;
    // bitClear(motorMoving, motorIndex);
    return;
  }

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
  if (hardStop)
  {
    maxA = MAX_ACCELERATION * 3;
  }
  else if (fastStop && maxA < motors[motorIndex].maxVelocity)
  {
    maxA = motors[motorIndex].maxVelocity;
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
  return (motorIndex >= 0 && motorIndex < MOTOR_COUNT);
}

void IRAM_ATTR processGoPositionNoSend(int motorIndex, int32_t pos)
{
  if (motors[motorIndex].position != pos)
  {
    motors[motorIndex].positionReached = false;
    setupMotorMove(motorIndex, pos);
  }
  else
  {
    motors[motorIndex].positionReached = true;
  }
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
    else if ((lastUserData == 'c') & (data == 'm')) // camera mode
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
      if (lastUserData >= '0' && lastUserData <= '9')
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
        if (!parseError && cameraMode == CAM_MODE_NONE)
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
        else
        {
          parseError = (userCmd.argCount != 1 || !isValidMotor(motor));
          if (!parseError)
          {
            sendMessage(MSG_PR, motor);
          }
        }
        break;

      case CMD_VE:
        parseError = (userCmd.argCount != 2 || !isValidMotor(motor));
        if (!parseError)
        {
          setPulsesPerSecond(motor, (uint16_t)userCmd.args[1]);
          sendMessage(MSG_VE, motor);
        }
        else
        {
          parseError = (userCmd.argCount != 1 || !isValidMotor(motor));
          if (!parseError)
          {
            sendMessage(MSG_VE, motor);
          }
        }
        break;

      case CMD_AC:
        parseError = (userCmd.argCount != 2 || !isValidMotor(motor));
        if (!parseError)
        {
          setMaxAccelerationPerSecond(motor, (uint16_t)userCmd.args[1]);
          sendMessage(MSG_AC, motor);
        }
        else
        {
          parseError = (userCmd.argCount != 1 || !isValidMotor(motor));
          if (!parseError)
          {
            sendMessage(MSG_AC, motor);
          }
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
        if (!parseError && cameraMode == CAM_MODE_NONE)
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
        cameraMode = CAM_MODE_RESETTING;
        sendMessage(MSG_CM, cameraMode);
        normalStop();
        break;
      case CMD_TL:
        parseError = (userCmd.argCount < 11);
        if (!parseError && cameraMode == CAM_MODE_NONE)
        {
          setupTimelapse(userCmd);
        }

        sendMessage(MSG_CM, cameraMode);
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

void sendMessage(byte msg, byte motorIndex)
{
  unsigned int data = (msg << 8) | motorIndex;
  xQueueSend(messageQueue, &data, 0);
}

void sendMessageQueue(byte msg, byte motorIndex)
{
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
      dualSerial.print(timelapseData.totalImageCounter);
      dualSerial.print(" ");
      dualSerial.print(timelapseData.totalImages);
      dualSerial.print(" ");
      dualSerial.print(timelapseData.intervalSeconds);
      dualSerial.print(" ");
      dualSerial.print(timelapseData.exposureTimeMillis);
      dualSerial.print(" ");
      dualSerial.print(timelapseData.restMoveTime);
      dualSerial.print(" ");
      dualSerial.print(timelapseData.ramp);
      break;
    case CAM_MODE_PANORAMA:
      dualSerial.print(" ");
      dualSerial.print(panoramaData.status);
      dualSerial.print(" ");
      dualSerial.print(panoramaData.executionStatus);
      dualSerial.print(" ");
      dualSerial.print(panoramaData.currentRowCounter);
      dualSerial.print(" ");
      dualSerial.print(panoramaData.imagesRow);
      dualSerial.print(" ");
      dualSerial.print(panoramaData.currentColumnCounter);
      dualSerial.print(" ");
      dualSerial.print(panoramaData.imagesColumn);
      dualSerial.print(" ");
      dualSerial.print(panoramaData.exposureTimeMillis);
      dualSerial.print(" ");
      dualSerial.print(panoramaData.restMoveTime);
      break;
    }

    dualSerial.print("\r\n");
    break;
  case MSG_CR:
    dualSerial.print("cr\r\n");
    break;
  }
}

boolean jogMotor(int motorIndex, int32_t target, int32_t *destination)
{
  if (cameraMode != CAM_MODE_NONE)
  {
    return false;
  }

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
  if ((dir == 1 && dest > target) || (dir == -1 && dest < target))
  {
    dest = target;
  }
  *destination = dest;
  return true;
}

void inchMotor(int motorIndex, int32_t target)
{
  if (cameraMode != CAM_MODE_NONE)
  {
    return;
  }

  Motor *motor = &motors[motorIndex];
  // ideally send motor to distance where decel happens after 2 seconds

  // if switching direction, just stop
  int dir = (target > motor->destination) ? 1 : -1;

  if (motor->nextMotorMoveSpeed) // && motor->dir * dir < 0)
  {
    stopMotor(motorIndex);
    return;
  }

  int32_t dest = motor->destination + dir * 2;

  // now clamp to target
  if ((dir == 1 && dest > target) || (dir == -1 && dest < target))
  {
    dest = target;
  }
  //setupMotorMove(motorIndex, dest);

  int i;

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

  if (dest != motor->position)
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
  if (us)
  {
    uint32_t e = (m + us);
    if (m > e)
    { //overflow
      while (millis() > e)
      {
        NOP();
      }
    }
    while (millis() < e)
    {
      NOP();
    }
  }
}

void takeCameraImage(int delayFocus, int delayShutter)
{
  setCameraFocus(false);
  setCameraShutter(false);
  setCameraFocus(true);
  if (delayFocus > 0)
  {
    delayMilliseconds(delayFocus);
  }
  setCameraShutter(true);
  delayMilliseconds(delayShutter);
  setCameraFocus(false);
  setCameraShutter(false);
}

void IRAM_ATTR setCameraFocus(boolean value)
{
  if (value)
  {
    PIN_ON(0, FOCUS_PIN);
  }
  else
  {
    PIN_OFF(0, FOCUS_PIN);
  }
}

void IRAM_ATTR setCameraShutter(boolean value)
{
  if (value)
  {
    PIN_ON(0, SHUTTER_PIN);
  }
  else
  {
    PIN_OFF(0, SHUTTER_PIN);
  }
}

void resetCameraMode()
{
  cameraMode = CAM_MODE_NONE;
  resetCameraModeSettings();
}

void IRAM_ATTR resetCameraModeSettings()
{
  if (timerCameraMode != NULL)
  {
    timerEnd(timerCameraMode);
    timerCameraMode = NULL;
  }
  if (timerCameraModeMotion != NULL)
  {
    timerEnd(timerCameraModeMotion);
    timerCameraModeMotion = NULL;
  }

  setCameraShutter(false);
  setCameraFocus(false);

  sendMessage(MSG_CM, cameraMode);
}

void processCameraMode()
{
  switch (cameraMode)
  {
  case CAM_MODE_TIMELAPSE:
    if (timelapseData.status == TIMELAPSE_RUNNING)
      processTimelapse();
    else if (timelapseData.status >= 10 && timelapseData.executionStatus > 0)
    {
      timelapseData.executionStatus = 0;
      sendMessage(MSG_CM, cameraMode);
    }
    break;
  case CAM_MODE_PANORAMA:
    if (panoramaData.status == PANORAMA_RUNNING)
      processPanorama();
    break;
  case CAM_MODE_RESETTING:
    boolean movementReady = true;
    for (int i = 0; i < MOTOR_COUNT; i++)
    {
      if (!motors[i].positionReached)
      {
        movementReady = false;
      }
    }

    if (movementReady)
    {
      resetCameraMode();
    }

    break;
  }
}

void IRAM_ATTR executeTimelaseMotion(void)
{
  timelapseData.executionStatus = TIMELAPSE_MOVE;

  if (timelapseData.currentIterationImageCounter > timelapseData.imagesCount[timelapseData.currentIteration])
  {
    timelapseData.currentIteration++;

    if (timelapseData.currentIteration >= timelapseData.positionCount - 1)
    {
      timelapseData.status = TIMELAPSE_DONE;
      resetCameraModeSettings();
      return;
    }
    else
    {
      timelapseData.currentIterationImageCounter = 1;
    }
  }

  for (int i = 0; i < MOTOR_COUNT; i++)
  {
    TimelapseMotor motor = timelapseData.motors[i];
    if (motor.enable)
    {
      int32_t newPosition = motor.positions[timelapseData.currentIteration] + getNewTimelapsePosition(i, timelapseData.currentIteration, timelapseData.currentIterationImageCounter);
      timelapseData.motors[i].lastPosition = newPosition;
      processGoPositionNoSend(i, newPosition);
    }
  }
}

void IRAM_ATTR executeTimelapseImage()
{
  for (int i = 0; i < MOTOR_COUNT; i++)
  {
    TimelapseMotor motor = timelapseData.motors[i];
    if (motor.enable && motor.lastPosition != motors[i].position)
    {
      timelapseData.status = TIMELAPSE_ERROR_POSITION;
      resetCameraModeSettings();
      return;
    }
  }

  timelapseData.executionStatus = TIMELAPSE_IMAGE;
  timelapseData.currentIterationImageCounter++;
  timelapseData.totalImageCounter++;

  setCameraShutter(true);
  setCameraFocus(true);

  timerCameraModeMotion = timerBegin(2, 80, true);
  timerAttachInterrupt(timerCameraModeMotion, &executeTimelaseMotion, true);
  timerAlarmWrite(timerCameraModeMotion, timelapseData.exposureTimeMillis * 1000, false);
  timerAlarmEnable(timerCameraModeMotion);

  delayMilliseconds(100);

  setCameraShutter(false);
  setCameraFocus(false);
}

void setupTimelapse(UserCmd userCmd)
{
  cameraMode = CAM_MODE_TIMELAPSE;
  timelapseData.status = TIMELAPSE_SETUP;

  timelapseData.currentIterationImageCounter = 0;
  timelapseData.totalImageCounter = 0;
  timelapseData.totalImages = 0;
  timelapseData.currentIteration = 0;
  timelapseData.intervalSeconds = userCmd.args[0];
  if (timelapseData.intervalSeconds < 1)
  {
    timelapseData.status = TIMELAPSE_ERROR_INTERVAL_LT_1;
    return;
  }
  timelapseData.exposureTimeMillis = userCmd.args[1];
  if (timelapseData.exposureTimeMillis < 1)
  {
    timelapseData.status = TIMELAPSE_ERROR_EXPOSURE_LT_1;
    return;
  }
  timelapseData.restMoveTime = userCmd.args[2];
  if (timelapseData.restMoveTime < 0)
  {
    timelapseData.status = TIMELAPSE_ERROR_REST_LT_0;
    return;
  }
  timelapseData.ramp = userCmd.args[3];
  if (timelapseData.ramp < 0 || timelapseData.ramp > 40)
  {
    timelapseData.status = TIMELAPSE_ERROR_RAMP_BETWEEN_0_40;
    return;
  }
  timelapseData.positionCount = userCmd.args[4];
  if (timelapseData.positionCount < 2 || timelapseData.positionCount > 4)
  {
    timelapseData.status = TIMELAPSE_ERROR_POSITION_COUNT_BETWEEN_2_4;
    return;
  }

  timelapseData.totalImages = userCmd.args[4 + timelapseData.positionCount - 1];
  if (timelapseData.totalImages <= 1)
  {
    timelapseData.status = TIMELAPSE_ERROR_IMAGES_LE_1;
    return;
  }
  timelapseData.imagesCount[timelapseData.positionCount - 2] = timelapseData.totalImages;

  for (int i = timelapseData.positionCount - 2; i > 0; i--)
  {
    uint32_t imageCount = userCmd.args[4 + i];
    if (imageCount <= 1)
    {
      timelapseData.status = TIMELAPSE_ERROR_IMAGES_LE_1;
      return;
    }
    if (timelapseData.imagesCount[i] < imageCount)
    {
      timelapseData.status = TIMELAPSE_ERROR_IMAGES_NOT_IN_ROW;
      return;
    }

    timelapseData.imagesCount[i] = timelapseData.imagesCount[i] - imageCount;
    timelapseData.imagesCount[i - 1] = imageCount;
  }

  timelapseData.imagesCount[0] = timelapseData.imagesCount[0] - 1;

  for (int i = 0; i < MOTOR_COUNT; i++)
  {
    timelapseData.motors[i].enable = false;
    for (int j = 0; j < timelapseData.positionCount; j++)
    {
      timelapseData.motors[i].positions[j] = 0;
    }
  }

  for (int i = 4 + timelapseData.positionCount; i < userCmd.argCount; i += (1 + timelapseData.positionCount))
  {
    int32_t motorNumber = userCmd.args[i] - 1;
    timelapseData.motors[motorNumber].enable = true;

    int32_t positions[timelapseData.positionCount];
    for (int j = 0; j < timelapseData.positionCount; j++)
    {
      positions[j] = userCmd.args[i + 1 + j];
    }

    for (int j = 1; j < timelapseData.positionCount; j++)
    {
      int32_t sGes = positions[j] - positions[j - 1];
      double ramp1 = (double)timelapseData.ramp * 0.01;
      double ramp2 = 0;
      if (j == timelapseData.positionCount - 1)
      {
        ramp2 = (double)timelapseData.ramp * 0.01;
      }
      else
      {
        int32_t sGesNext = positions[j + 1] - positions[j];
        if ((sGes > 0 && sGesNext < 0) || (sGes < 0 && sGesNext > 0))
        {
          ramp2 = (double)timelapseData.ramp * 0.01;
        }
      }
      int32_t v0 = 0;
      if (j > 1 && (((sGes > 0) && timelapseData.motors[motorNumber].v1[j - 2] > 0) || ((sGes < 0) && timelapseData.motors[motorNumber].v1[j - 2] < 0)))
      {
        v0 = timelapseData.motors[motorNumber].v1[j - 2];
      }
      int32_t v2 = 0;
      int32_t iterations = timelapseData.imagesCount[j - 1];
      int32_t t0 = 0;
      int32_t t1 = (int32_t)(round((double)(iterations - t0) * ramp1));
      int32_t t3 = iterations;
      int32_t t2 = iterations - (int32_t)(round((iterations - t0) * ramp2));
      double v1 = (2 * sGes - (v0 * t1) + (v0 * t0) - (v2 * t3) + (v2 * t2)) / (t2 + t3 - t0 - t1);
      if ((sGes > 0 && v1 < 0) || (sGes < 0 && v1 > 0))
      {
        timelapseData.status = TIMELAPSE_ERROR_ACC_RAMP;
        return;
      }
      double a1 = 0;
      double a2 = 0;
      int32_t deltaT1 = t1 - t0;
      int32_t deltaT3 = t3 - t2;
      if (deltaT1 > 0)
      {
        a1 = (v1 - v0) / deltaT1;
      }
      if (deltaT3 > 0)
      {
        a2 = (v2 - v1) / deltaT3;
      }

      timelapseData.motors[motorNumber].time[j - 1][0] = t0;
      timelapseData.motors[motorNumber].time[j - 1][1] = t1;
      timelapseData.motors[motorNumber].time[j - 1][2] = t2;
      timelapseData.motors[motorNumber].time[j - 1][3] = t3;
      timelapseData.motors[motorNumber].acc[j - 1] = a1;
      timelapseData.motors[motorNumber].dec[j - 1] = a2;
      timelapseData.motors[motorNumber].v0[j - 1] = v0;
      timelapseData.motors[motorNumber].v1[j - 1] = v1;

      int32_t newEndPosition = positions[j - 1] + getNewTimelapsePosition(motorNumber, j - 1, iterations);
      positions[j] = newEndPosition;
      timelapseData.motors[motorNumber].positions[j] = positions[j];
    }

    timelapseData.motors[motorNumber].lastPosition = positions[0];

    Motor *motor = &motors[motorNumber];
    for (int j = 0; j < timelapseData.positionCount - 1; j++)
    {
      float moveTime = 0.0f;
      calculatePointToPoint(motorNumber, motor->position + (int32_t)(round(timelapseData.motors[motorNumber].v1[j - 1])));
      for (int k = 0; k < P2P_MOVE_COUNT; k++)
      {
        if (motor->moveTime[k] == 0)
          break;

        moveTime += motor->moveTime[k];
      }

      if ((timelapseData.exposureTimeMillis + (1.07 * 1000 * moveTime) + timelapseData.restMoveTime) > (timelapseData.intervalSeconds * 1000))
      {
        timelapseData.status = TIMELAPSE_ERROR_MOVE_SHOOT_TIME;
        return;
      }
    }
  }

  timelapseData.status = TIMELAPSE_RUNNING;
  timelapseData.executionStatus = TIMELAPSE_INIT_MOVEMENT;
  sendMessage(MSG_CM, cameraMode);

  for (int i = 0; i < MOTOR_COUNT; i++)
  {
    TimelapseMotor motor = timelapseData.motors[i];
    if (motor.enable)
    {
      int32_t newPosition = motor.positions[0] + getNewTimelapsePosition(i, timelapseData.currentIteration, timelapseData.currentIterationImageCounter);
      processGoPositionNoSend(i, newPosition);
    }
  }
}

int32_t getNewTimelapsePosition(int motorNumber, int32_t it, int32_t itImage)
{
  int32_t t1 = timelapseData.motors[motorNumber].time[it][1];
  if (itImage < t1)
    t1 = itImage;
  double s0 = 0.5 * timelapseData.motors[motorNumber].acc[it] * pow(t1, 2) + timelapseData.motors[motorNumber].v0[it] * t1;
  if (itImage <= t1)
    return (int32_t)(round(s0));

  int32_t t2 = timelapseData.motors[motorNumber].time[it][2];
  if (itImage < t2)
    t2 = itImage;
  double s1 = timelapseData.motors[motorNumber].v1[it] * (t2 - t1);
  if (itImage <= t2)
    return (int32_t)(round(s0 + s1));

  int32_t t3 = timelapseData.motors[motorNumber].time[it][3];
  if (itImage < t3)
    t3 = itImage;
  double s2 = 0.5 * timelapseData.motors[motorNumber].dec[it] * pow((t3 - t2), 2) + timelapseData.motors[motorNumber].v1[it] * (t3 - t2);

  return (int32_t)(round(s0 + s1 + s2));
}

void processTimelapse()
{
  boolean movementReady = true;

  switch (timelapseData.executionStatus)
  {
  case TIMELAPSE_INIT_MOVEMENT:
    for (int i = 0; i < MOTOR_COUNT; i++)
    {
      TimelapseMotor motor = timelapseData.motors[i];
      if (motor.enable && !motors[i].positionReached)
      {
        movementReady = false;
      }
    }

    if (movementReady)
    {
      timelapseData.executionStatus = TIMELAPSE_START_EXECUTION;
    }

    break;

  case TIMELAPSE_START_EXECUTION:
    timerCameraMode = timerBegin(1, 80, true);
    timerAttachInterrupt(timerCameraMode, &executeTimelapseImage, true);
    timerAlarmWrite(timerCameraMode, 1000000 * timelapseData.intervalSeconds, true);

    timelapseData.executionStatus = TIMELAPSE_REST;
    timerAlarmEnable(timerCameraMode);
    break;

  case TIMELAPSE_MOVE:
    for (int i = 0; i < MOTOR_COUNT; i++)
    {
      TimelapseMotor motor = timelapseData.motors[i];
      if (motor.enable && !motors[i].positionReached)
      {
        movementReady = false;
      }
    }

    if (movementReady)
    {
      timelapseData.executionStatus = TIMELAPSE_REST;
      sendMessage(MSG_CM, cameraMode);
    }

    break;
  }
}

void setupPanorama(UserCmd userCmd)
{
  cameraMode = CAM_MODE_PANORAMA;
  panoramaData.status = PANORAMA_SETUP;

  panoramaData.currentRowCounter = 1;
  panoramaData.currentColumnCounter = 1;

  panoramaData.motorRow = userCmd.args[0];
  if (panoramaData.motorRow < 1 || panoramaData.motorRow > MOTOR_COUNT)
  {
    panoramaData.status = PANORAMA_ERROR_MTR_OUT;
    return;
  }
  panoramaData.motorRow--;

  panoramaData.imagesRow = userCmd.args[1];
  if (panoramaData.imagesRow <= 1)
  {
    panoramaData.status = PANORAMA_ERROR_IMG_LE_1;
    return;
  }
  panoramaData.stepsRow = userCmd.args[2];
  if (panoramaData.imagesRow == 0)
  {
    panoramaData.status = PANORAMA_ERROR_STP_EQ_0;
    return;
  }

  panoramaData.motorColumn = userCmd.args[3];
  if (panoramaData.motorColumn < 1 || panoramaData.motorColumn > MOTOR_COUNT)
  {
    panoramaData.status = PANORAMA_ERROR_MTR_OUT;
    return;
  }
  panoramaData.motorColumn--;

  panoramaData.imagesColumn = userCmd.args[4];
  if (panoramaData.imagesColumn <= 1)
  {
    panoramaData.status = PANORAMA_ERROR_IMG_LE_1;
    return;
  }
  panoramaData.stepsColumn = userCmd.args[5];
  if (panoramaData.imagesColumn == 0)
  {
    panoramaData.status = PANORAMA_ERROR_STP_EQ_0;
    return;
  }

  panoramaData.exposureTimeMillis = userCmd.args[6];
  if (panoramaData.exposureTimeMillis < 50)
  {
    panoramaData.status = PANORAMA_ERROR_EXP_LT_50;
    return;
  }

  panoramaData.restMoveTime = userCmd.args[7];
  if (panoramaData.restMoveTime < 1)
  {
    panoramaData.status = PANORAMA_ERROR_RST_LT_1;
    return;
  }

  panoramaData.currentRowCounter = panoramaData.imagesRow;

  panoramaData.status = PANORAMA_RUNNING;
  panoramaData.executionStatus = PANORAMA_REST;
  sendMessage(MSG_CM, cameraMode);
}

void processPanorama()
{
  int32_t newPositionRow;
  int32_t newPositionColumn;

  switch (panoramaData.executionStatus)
  {
  case PANORAMA_REST:
    panoramaData.restStartMillis = millis();
    panoramaData.executionStatus = PANORAMA_REST_RUNNING;
    break;
  case PANORAMA_REST_RUNNING:
    if ((millis() - panoramaData.restStartMillis) > (panoramaData.restMoveTime))
    {
      panoramaData.executionStatus = PANORAMA_IMAGE;
      sendMessage(MSG_CM, cameraMode);
    }
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
    if (panoramaData.currentRowCounter > panoramaData.imagesRow)
    {
      if (panoramaData.currentColumnCounter != 1)
      {
        newPositionRow -= panoramaData.stepsRow * (panoramaData.imagesRow - 1);
      }
      panoramaData.currentRowCounter = 1;
      panoramaData.currentColumnCounter++;

      if (panoramaData.currentColumnCounter > panoramaData.imagesColumn)
      {
        newPositionColumn -= panoramaData.stepsColumn * (panoramaData.imagesColumn - 1);
        panoramaData.currentRowCounter = 1;
        panoramaData.currentColumnCounter = 1;
        panoramaData.executionStatus = PANORAMA_MOVE_END;
      }
      else
      {
        newPositionColumn += panoramaData.stepsColumn;
      }
    }
    else
    {
      newPositionRow += panoramaData.stepsRow;
    }

    sendMessage(MSG_CM, cameraMode);

    processGoPosition(panoramaData.motorRow, newPositionRow);
    processGoPosition(panoramaData.motorColumn, newPositionColumn);
    break;
  case PANORAMA_MOVE:
    if (motors[panoramaData.motorRow].positionReached && motors[panoramaData.motorColumn].positionReached)
    {
      panoramaData.executionStatus = PANORAMA_REST;
      sendMessage(MSG_CM, cameraMode);
    }
    break;
  case PANORAMA_MOVE_END:
    if (motors[panoramaData.motorRow].positionReached && motors[panoramaData.motorColumn].positionReached)
    {
      panoramaData.status = PANORAMA_DONE;
      sendMessage(MSG_CM, cameraMode);
    }
    break;
  }
}
