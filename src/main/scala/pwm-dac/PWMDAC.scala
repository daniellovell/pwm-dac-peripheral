package pwmdac

import chisel3._
import chisel3.{withClock => withNewClock}
import chisel3.util._
import chisel3.experimental._
import freechips.rocketchip.config.{Field, Parameters, Config}
import freechips.rocketchip.diplomacy.{LazyModule, LazyModuleImp}
import freechips.rocketchip.regmapper.{HasRegMap, RegField, RegisterWriteIO}
import freechips.rocketchip.tilelink._

case class PWMDACParams(
  address: BigInt = 0x1000,
  width: Int = 32)

case object PWMDACKey extends Field[Option[PWMDACParams]](None)

trait PWMDACTopIO extends Bundle {
  val dac_busy = Output(Bool())
}

class PWMDACTL(params: PWMDACParams, beatBytes: Int)(implicit p: Parameters)
  extends TLRegisterRouter(
    params.address, "pwmdac", Seq("ucbbar,pwmdac"),
    beatBytes = beatBytes)(
      new TLRegBundle(params, _) with PWMDACTopIO)(
      new TLRegModule(params, _, _) with PWMDACModule)

// IO bundle to define 
class PWMDACIO(val w: Int) extends Bundle {
  val clock = Input(Clock())
  val reset = Input(Bool())
  val sample = Input(UInt(w.W))
  val input_ready = Output(Bool())
  val input_valid = Input(Bool())
  val busy = Output(Bool())
}

// Trait for modules that have PWMDAC IO
trait HasPWMDACIO extends BaseModule {
  val w: Int
  val io = IO(new PWMDACIO(w))
}

class PWMDACModule(val w: Int) extends Module
  with HasPWMDACIO 
  {
    // States for the state machine
    val s_idle :: s_run :: s_done :: Nil = Enum(3)
    val state = RegInit(s_idle)


  }