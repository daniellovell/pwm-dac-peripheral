package pwmdac

import chisel3._
import chisel3.experimental._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.subsystem.BaseSubsystem
import freechips.rocketchip.config.{Config, Field, Parameters}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._

trait CanHavePeripheryPWMDAC { this: BaseSubsystem =>
  private val portName = "pwm_dac"

  // Only build if we are using the TL (nonAXI4) version
  val pwm_dac = p(PWMDACKey) match {
    case Some(params) => {
      if (params.useAXI4) {
        val pwm_dac = LazyModule(new PWMDACAXI4(params, pbus.beatBytes)(p))
        pbus.toSlave(Some(portName)) {
          pwm_dac.node :=
          AXI4Buffer () :=
          TLToAXI4 () :=
          TLFragmenter(pbus.beatBytes, pbus.blockBytes, holdFirstDeny = true)
        }
        Some(pwm_dac)
      } else {
        val pwm_dac = LazyModule(new PWMDACTL(params, pbus.beatBytes)(p))
        pbus.toVariableWidthSlave(Some(portName)) { pwm_dac.node }
        Some(pwm_dac)
      }
    }
    case None => None
  }
}

trait CanHavePeripheryPWMDACModuleImp extends LazyModuleImp {
  val outer: CanHavePeripheryPWMDAC
  val pwm_dac_busy = outer.pwm_dac match {
    case Some(pwm_dac) => {
      val busy = IO(Output(Bool()))
      busy := pwm_dac.module.io.dac_busy
      Some(busy)
    }
    case None => None
  }
}


class WithPWMDAC(useAXI4: Boolean = false) extends Config((site, here, up) => {
  case PWMDACKey => Some(PWMDACParams(useAXI4 = useAXI4))
})
