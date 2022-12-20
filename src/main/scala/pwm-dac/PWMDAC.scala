package pwmdac

import chisel3._
import chisel3.experimental._
import chisel3.util._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.subsystem.BaseSubsystem
import freechips.rocketchip.config.{Config, Field, Parameters}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.regmapper.{HasRegMap, RegField}
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util.UIntIsOneOf

case class PWMDACParams(
  address: BigInt = 0x1000,
  width: Int = 32,
  useAXI4: Boolean = false)

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
      
class PWMDACAXI4(params: PWMDACParams, beatBytes: Int)(implicit p: Parameters)
  extends AXI4RegisterRouter(
    params.address,
    beatBytes=beatBytes)(
      new AXI4RegBundle(params, _) with PWMDACTopIO)(
      new AXI4RegModule(params, _, _) with PWMDACModule)


// Top-level IO bundle, including both digital and analog IO
class PWMDACIO(val w: Int) extends Bundle {
  val digital = new PWMDACDigitalIO(w)
  val analog = new PWMDACAnalogIO()
}

// Digital IO bundle
class PWMDACDigitalIO(val w: Int) extends Bundle {
  //val clock = Input(Clock())
  //val reset = Input(Bool())
  val sample = Input(UInt(w.W))
  val input_ready = Output(Bool())
  val input_valid = Input(Bool())
  val busy = Output(Bool())
}

// Analog IO bundle
class PWMDACAnalogIO() extends Bundle {
  val dac_out = Output(Bool())
}

trait HasPWMDACIO extends BaseModule {
  val w: Int
  val io = IO(new PWMDACIO(w))
}


class PWMDACImp(val w: Int) extends Module
  with HasPWMDACIO
{
    val dig_io: PWMDACDigitalIO = io.digital
    val ana_io: PWMDACAnalogIO = io.analog
    // States for the state machine
    val s_idle :: s_run :: Nil = Enum(2)
    val state = RegInit(s_idle)
    // Counter to width
    val counter = RegInit(0.U(w.W))
    // Counter max as (2^w)-1
    val counter_max = (1 << w) - 1

    dig_io.input_ready := (state === s_idle)
    when (state === s_idle && dig_io.input_valid) {
      state := s_run
    }.elsewhen (state === s_run && counter >= counter_max.U) {
      state := s_idle
    }

    when (state === s_idle) {
      counter := 0.U
    }.elsewhen (state === s_run) {
      counter := counter + 1.U
    }
    // PWM output
    ana_io.dac_out := (counter >= counter_max.U)
}

trait PWMDACModule extends HasRegMap {
  val params: PWMDACParams
  val io: PWMDACTopIO

  //val clock: Clock
  //val reset: Reset


  // How many clock cycles in a PWM cycle?
  val sample = Reg(new DecoupledIO(UInt(params.width.W)))
  val dac_out = Wire(Bool())
  val status = Wire(UInt(2.W))

  lazy val impl = new PWMDACImp(params.width)

  //impl.io.digital.clock := clock
  //impl.io.digital.reset := reset.asBool

  impl.io.digital.input_valid := sample.valid
  sample.ready := impl.io.digital.input_ready

  status := Cat(impl.io.digital.input_ready, impl.io.digital.busy)
  io.dac_busy := impl.io.digital.busy

  regmap(
    0x00 -> Seq(
      RegField.r(2, status)),               // a read-only register capturing current status
    0x04 -> Seq(
      RegField.w(params.width, sample)))   // write-only, sample.valid is set on write
}