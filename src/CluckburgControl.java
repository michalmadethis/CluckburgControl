
import com.pi4j.component.motor.impl.GpioStepperMotorComponent;
import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPinDigitalOutput;
import com.pi4j.io.gpio.PinState;
import com.pi4j.io.gpio.RaspiPin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;

import java.util.Calendar;
 
public class CluckburgControl
{
    private static byte[] weak_but_fast_sequence = new byte[4];
    private static byte[] slow_but_strong_sequence = new byte[4];
    private static byte[] very_strong_but_very_slow_sequence = new byte[8];
    private static GpioStepperMotorComponent motor;
    private static int[] average_minute_for_sunrise_by_month = new int[12];
    private static int[] average_minute_for_sunset_by_month = new int[12];
    
    //TODO move out to config.
    private static boolean IS_DOOR_OPEN = false;
    private static int tickIntervalInSeconds = 1800; //30 minutes
    private static int revolutions_to_open = 2;
    private static int revolutions_to_close = -2;
    
    
    public static void main(String[] args) throws InterruptedException, IOException
    {
        boolean exitRequested = false;
        boolean DEBUGGING = false;
        boolean HARDWARE_TEST = false;
     
        if(args != null && args.length > 0){
            if(args[0].equalsIgnoreCase("debug_mode")){
                DEBUGGING = true;
            }
            else if(args[0].equalsIgnoreCase("hardware_test")){
                HARDWARE_TEST = true;
            }
        }
        
        
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        
        SetupMotor();
        SetupSequences();
        SetupDaylightValues();
        
        //Run application loop.
        while(!exitRequested){
                
            if(DEBUGGING){
                System.out.println("Ready for input:");
                String s = br.readLine();
        
                if(s.equalsIgnoreCase("q")){
                    exitRequested = true;
                }
                else if(s.equalsIgnoreCase("open")){
                    OpenCoopDoor();
                }
                else if(s.equalsIgnoreCase("close")){
                    CloseCoopDoor();
                }
                else if(s.equalsIgnoreCase("lightcheck")){
                    System.out.println(IsThereLightOut());
                }
            }
            else if (HARDWARE_TEST) {
                
                //Every 15 seconds, open/close the coop door to test gearing/winch/mounting etc of drawbridge.
                
                
                    OpenCoopDoor();
                
                    Thread.sleep(15000); //15 seconds
                
                    CloseCoopDoor();
                    
                    Thread.sleep(15000); //15 seconds
                            
            }
            else {
                
                boolean isThereLightOut = IsThereLightOut();
                
                //If it's daytime and the door has not opened, open it.
                if(isThereLightOut && !IS_DOOR_OPEN){
                    OpenCoopDoor();
                }
                //IF it's nightime and the door is still open, close it.
                else if(!isThereLightOut && IS_DOOR_OPEN){
                    CloseCoopDoor();
                }
            
                Thread.sleep(tickIntervalInSeconds * 1000);
            }
        }
        
        System.out.println("Exited.");  
    }
    
    
    private static void OpenCoopDoor()  throws InterruptedException {
        System.out.println("Opening Coop Door");
        
        // define stepper parameters before attempting to control motor
        // anything lower than 2 ms does not work for my sample motor using single step sequence
        motor.setStepInterval(2);  
        motor.setStepSequence(weak_but_fast_sequence);

        System.out.println("   Motor FORWARD with faster speed and lower torque for " + revolutions_to_open + " revolutions.");
        motor.rotate(revolutions_to_open);
        System.out.println("   Motor STOPPED for 2 seconds.");
        Thread.sleep(2000);
        
        // final stop to ensure no motor activity
        motor.stop();
        IS_DOOR_OPEN = true;
        System.out.println("Door should be OPEN");
    }
    
    
    private static void CloseCoopDoor() throws InterruptedException{
        System.out.println("Closing Coop Door");

        motor.setStepInterval(10);
        motor.setStepSequence(slow_but_strong_sequence);
        
        System.out.println("   Motor BACKWARDS with slower speed and higher torque for " + revolutions_to_close +" revolution.");
        motor.rotate(revolutions_to_close);
        System.out.println("   Motor STOPPED for 2 seconds.");
        Thread.sleep(2000);

        // final stop to ensure no motor activity
        motor.stop();
        IS_DOOR_OPEN = false;
        System.out.println("Door should be CLOSED");
    }
    
    
    private static void SetupMotor(){
        
        // create gpio controller
        final GpioController gpio = GpioFactory.getInstance();
        
        // provision gpio pins #00 to #03 as output pins and ensure in LOW state
        final GpioPinDigitalOutput[] pins = {
                gpio.provisionDigitalOutputPin(RaspiPin.GPIO_00, PinState.LOW),
                gpio.provisionDigitalOutputPin(RaspiPin.GPIO_01, PinState.LOW),
                gpio.provisionDigitalOutputPin(RaspiPin.GPIO_02, PinState.LOW),
                gpio.provisionDigitalOutputPin(RaspiPin.GPIO_03, PinState.LOW)};

        // this will ensure that the motor is stopped when the program terminates
        gpio.setShutdownOptions(true, PinState.LOW, pins);
        
        // create motor component
        motor = new GpioStepperMotorComponent(pins);
        
        // There are 32 steps per revolution on my sample motor, 
        // and inside is a ~1/64 reduction gear set.
        // Gear reduction is actually: (32/9)/(22/11)x(26/9)x(31/10)=63.683950617
        // This means is that there are really 32*63.683950617 steps per revolution 
        // =  2037.88641975 ~ 2038 steps! 
        motor.setStepsPerRevolution(2038);
       
    }
    
    private static void SetupSequences(){
        
        // create byte array to demonstrate a single-step sequencing
        // (This is the most basic method, turning on a single electromagnet every time.
        //  This sequence requires the least amount of energy and generates the smoothest movement.)
        weak_but_fast_sequence[0] = (byte) 0b0001;  
        weak_but_fast_sequence[1] = (byte) 0b0010;
        weak_but_fast_sequence[2] = (byte) 0b0100;
        weak_but_fast_sequence[3] = (byte) 0b1000;
        
        // create byte array to demonstrate a double-step sequencing
        // (In this method two coils are turned on simultaneously.  This method does not generate 
        //  a smooth movement as the previous method, and it requires double the current, but as 
        //  return it generates double the torque.) 
        slow_but_strong_sequence[0] = (byte) 0b0011;  
        slow_but_strong_sequence[1] = (byte) 0b0110;
        slow_but_strong_sequence[2] = (byte) 0b1100;
        slow_but_strong_sequence[3] = (byte) 0b1001;
        
         // create byte array to demonstrate a half-step sequencing
        // (In this method two coils are turned on simultaneously.  This method does not generate 
        //  a smooth movement as the previous method, and it requires double the current, but as 
        //  return it generates double the torque.)
        very_strong_but_very_slow_sequence[0] = (byte) 0b0001;  
        very_strong_but_very_slow_sequence[1] = (byte) 0b0011;
        very_strong_but_very_slow_sequence[2] = (byte) 0b0010;
        very_strong_but_very_slow_sequence[3] = (byte) 0b0110;
        very_strong_but_very_slow_sequence[4] = (byte) 0b0100;
        very_strong_but_very_slow_sequence[5] = (byte) 0b1100;
        very_strong_but_very_slow_sequence[6] = (byte) 0b1000;
        very_strong_but_very_slow_sequence[7] = (byte) 0b1001;
    }
    
    private static void SetupDaylightValues() {
        //Array of months, average sun set in minutes elapsed hour sun rises
        average_minute_for_sunrise_by_month[0] = (int) 410;
        average_minute_for_sunrise_by_month[1] = (int) 450;
        average_minute_for_sunrise_by_month[2] = (int) 450;
        average_minute_for_sunrise_by_month[3] = (int) 480;
        average_minute_for_sunrise_by_month[4] = (int) 450;
        average_minute_for_sunrise_by_month[5] = (int) 480;
        average_minute_for_sunrise_by_month[6] = (int) 480;
        average_minute_for_sunrise_by_month[7] = (int) 450;
        average_minute_for_sunrise_by_month[8] = (int) 420;
        average_minute_for_sunrise_by_month[9] = (int) 420;
        average_minute_for_sunrise_by_month[10] = (int) 380;
        average_minute_for_sunrise_by_month[11] = (int) 370;
        
        average_minute_for_sunset_by_month[0] = (int) 1260;
        average_minute_for_sunset_by_month[1] = (int) 1240;
        average_minute_for_sunset_by_month[2] = (int) 1230;
        average_minute_for_sunset_by_month[3] = (int) 1100;
        average_minute_for_sunset_by_month[4] = (int) 1060;
        average_minute_for_sunset_by_month[5] = (int) 1040;
        average_minute_for_sunset_by_month[6] = (int) 1060;
        average_minute_for_sunset_by_month[7] = (int) 1090;
        average_minute_for_sunset_by_month[8] = (int) 1110;
        average_minute_for_sunset_by_month[9] = (int) 1200;
        average_minute_for_sunset_by_month[10] = (int) 1240;
        average_minute_for_sunset_by_month[11] = (int) 1260;
        
    }
    
    private static boolean IsThereLightOut() {
        
        Calendar now = Calendar.getInstance();
        
        int month = now.get(Calendar.MONTH);
        
        int hour = now.get(Calendar.HOUR_OF_DAY);
        int minute = now.get(Calendar.MINUTE);
        
        int minute_of_day = ((hour * 60) + minute);
        
        if(minute_of_day >= average_minute_for_sunrise_by_month[month -1] && minute_of_day <= average_minute_for_sunset_by_month[month-1]){
            return true;
        }
        
        return false;
    } 
    
}