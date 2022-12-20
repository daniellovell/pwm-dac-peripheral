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

// Analog IO bundle
class PWMDACAnalogIO(val w: Int) extends Bundle {
  val dac_out = Output(Bool())
}

// Trait for modules that have PWMDAC IO
trait HasPWMDACIO extends BaseModule {
  val w: Int
  val io = IO(new PWMDACIO(w))
}

class PWMDACMMIOModule(val w: Int) extends Module
  with HasPWMDACIO 
{
    // States for the state machine
    val s_idle :: s_run :: Nil = Enum(2)
    val state = RegInit(s_idle)
    // Counter to width
    val counter = RegInit(0.U(w.W))
    // Counter max as (2^w)-1
    val counter_max = (1 << w) - 1

    io.input_ready := (state === s_idle)
    when (state === s_idle && io.input_valid) {
      state := s_run
    }.elsewhen (state === s_run && counter === w.U) {
      state := s_idle
    }


}

trait PWMDACModule extends HasRegMap {
  val io: PWMDACTopIO

  implicit val p: Parameters
  def params: PWMDACParams
  val clock: Clock
  val reset: Reset


  // How many clock cycles in a PWM cycle?
  val sample = Reg(new DecoupledIO(UInt(params.width.W)))
  val dac_out = Wire(Bool())
  val status = Wire(UInt(2.W))

  val impl = Module(new PWMDACMMIOModule(params.width))

  impl.io.clock := clock
  impl.io.reset := reset.asBool

  impl.io.input_valid := sample.valid
  sample.ready := impl.io.input_ready

  status := Cat(impl.io.input_ready, impl.io.busy)
  io.dac_busy := impl.io.busy

  regmap(
    0x00 -> Seq(
      RegField.r(2, status)),               // a read-only register capturing current status
    0x04 -> Seq(
      RegField.w(params.width, sample))),   // write-only, sample.valid is set on write
}