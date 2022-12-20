package pwmdac

import chisel3._
import chisel3.util._
import chisel3.experimental.{IntParam, BaseModule}
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.subsystem.BaseSubsystem
import freechips.rocketchip.config.{Parameters, Field, Config}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.regmapper.{HasRegMap, RegField}
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util.UIntIsOneOf

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
          // toVariableWidthSlave doesn't use holdFirstDeny, which TLToAXI4() needsx
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
      busy := pwm_dac.module.io.pwm_dac_busy
      Some(busy)
    }
    case None => None
  }
}


class WithPWMDAC(useAXI4: Boolean = false, useBlackBox: Boolean = false) extends Config((site, here, up) => {
  case PWMDACKey => Some(PWMDACParams(useAXI4 = useAXI4, useBlackBox = useBlackBox))
})
