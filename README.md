# pwm-dac-peripheral
An example of a PWM DAC peripheral for use with Chipyard SoC generator. Intended for use alongside the "Mixed-signal verification with Chisel" tutorial. 

The accompanying Chipyard fork which integrates this peripheral as a submodule can be found at:
 - https://github.com/daniellovell/chipyard-mixed-signal 

## Project Structure
- src/main/scala/pwm-dac
    - Configs.scala - Configurations and traits for integration with Chipyard Rocket Chip
    - PWMDAC.scala - RTL for the peripheral and memory-mapped IO
- src/test/scala/pwm-dac
    - PWMDACTest.scala - Chisel verification tests

- build.sbt - Project build definition file