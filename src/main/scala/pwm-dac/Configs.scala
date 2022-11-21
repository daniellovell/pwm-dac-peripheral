package pwmdac

import chisel3._
import chisel3.{withClock => withNewClock}
import chisel3.util._
import chisel3.experimental._
import chisel3.experimental.{withClock => withExperimentalClock}
import freechips.rocketchip.config.Config
import freechips.rocketchip.diplomacy.{InModuleBody, LazyModule}
import freechips.rocketchip.subsystem.BaseSubsystem

trait CanHavePeripheryPWMDAC { this: BaseSubsystem =>

  val dac_clock = p(PWMDACKey).map {_ =>
    val dac_clock_io = InModuleBody {
      val dac_clock_io = IO(Input(Clock())).suggestName("dac_clock")
      dac_clock_io
    }
    dac_clock_io
  }

  val pwmdac = p(PWMDACKey).map { params =>
    val pwmdac = LazyModule(new PWMDAC(params, fbus.beatBytes))

    pbus.toVariableWidthSlave(Some("pwmdac")) { pwmdac.mmio }
    fbus.fromPort(Some("pwmdac"))() := pwmdac.mem
    ibus.fromSync := pwmdac.intnode

    val io = InModuleBody {
      dac_clock.map({ a =>
        pwmdac.module.clock := a
      })

      val io = IO(new PWMDACAnalogIO(params)).suggestName("pwmdac")
      io <> pwmdac.module.io
      io
    }
    io
  }
}

class WithPWMDAC(params: PWMDACParams = PWMDACParams()) extends Config((site, here, up) => {
  case PWMDACKey => Some(params)
})

/* Note: The following are commented out as they rely on importing chipyard, which no
         generator can do without having a circular import. They should  be added to
         files in: <chipyard root>/generators/chipyard/src/main/scala/<file>

         To use, you should then add the following to your config:
           new pwmdac.WithPWMDAC() ++
           new chipyard.iobinders.WithPWMDACPunchthrough() ++
           new chipyard.harness.WithPWMDACTiedOff ++

         Finally add the following to DigitalTop.scala:
           with pwmdac.CanHavePeripheryPWMDAC
           with sifive.blocks.devices.timer.HasPeripheryTimer
*/

/* Place this in IOBinders.scala for use
import pwmdac.{CanHavePeripheryPWMDAC, PWMDACAnalogIO, PWMDACParams}

class WithPWMDACPunchthrough(params: PWMDACParams = PWMDACParams()) extends OverrideIOBinder({
  (system: CanHavePeripheryPWMDAC) => {
    val ports: Seq[PWMDACAnalogIO] = system.pwmdac.map({ a =>
      val analog = IO(new PWMDACAnalogIO(params)).suggestName("pwmdac")
      analog <> a
      analog
    }).toSeq
    (ports, Nil)
  }
})
*/

/* Note: Place this in HarnessBinders.scala for use
import pwmdac.{CanHavePeripheryPWMDAC, PWMDACAnalogIO}

class WithPWMDACTiedOff extends OverrideHarnessBinder({
  (system: CanHavePeripheryPWMDAC, th: HasHarnessSignalReferences, ports: Seq[PWMDACAnalogIO]) => {
    ports.map { p => {
      p.data.rx.i.data := 0.U
      p.data.rx.q.data := 0.U
    }}
  }
})
 */
