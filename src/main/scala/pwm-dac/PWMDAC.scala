package pwmdac

import chisel3._
import chisel3.{withClock => withNewClock}
import chisel3.util._
import chisel3.experimental._
import freechips.rocketchip.config.{Field, Parameters}
import freechips.rocketchip.diplomacy.{LazyModule, LazyModuleImp}
import freechips.rocketchip.regmapper.{HasRegMap, RegField, RegisterWriteIO}
import freechips.rocketchip.tilelink.{TLIdentityNode, TLRegBundle, TLRegModule, TLRegisterRouter}

case class PWMDACParams (
  address: BigInt = 0x8000,
)

case object PWMDACKey extends Field[Option[PWMDACParams]](None)

class PWMDACAnalogIO(params: PWMDACParams) extends Bundle {
  val dac_out = new Bool()
}

class PWMDACStatus extends Bundle {
  val status0 = UInt(32.W)
}

class PWMDACInterrupts extends Bundle {
  val sampleFinished = Bool()
}


trait PWMDACFrontendBundle extends Bundle {
  val params: PWMDACParams

  val back = new PWMDACBackendIO
  val tuning = new ModemTuningIO
  val tuningControl = Output(new ModemTuningControl(params))
}

trait PWMDACFrontendModule extends HasRegMap {
  val params: PWMDACParams

  val io: PWMDACFrontendBundle


  // Interrupts
  interrupts(0) := io.back.interrupt.rxError

  regmap(
    0x00 -> Seq(RegField.w(32, inst)),         // Command start
    0x04 -> Seq(RegField.w(32, additionalData)),
    0x08 -> Seq(RegField.r(32, io.back.status.status0)), // Status start
    0x0C -> Seq(RegField.r(32, io.back.status.status1)),
    0x10 -> Seq(RegField.r(32, io.back.status.status2)),
    0x14 -> Seq(RegField.r(32, io.back.status.status3)),
  )
}

class PWMDACFrontend(params: PWMDACParams)(implicit p: Parameters)
  extends TLRegisterRouter(
    params.address, "pwmdac", Seq("ucbbar, riscv"),
    beatBytes = beatBytes, interrupts = 1)( 
      new TLRegBundle(params, _) with PWMDACFrontendBundle)(
      new TLRegModule(params, _, _) with PWMDACFrontendModule)

class PWMDAC(params: PWMDACParams, beatBytes: Int)(implicit p: Parameters) extends LazyModule {

  val mmio = TLIdentityNode()

  val pwmdacFrontend = LazyModule(new PWMDACFrontend(params, beatBytes))
  val intnode = pwmdacFrontend.intnode

  pwmdacFrontend.node := mmio

  lazy val module = new PWMDACImp(params, beatBytes,this)
}

class PWMDACImp(params: PWMDACParams, beatBytes: Int, outer: PWMDAC)(implicit p: Parameters) extends LazyModuleImp(outer) {
  val io = dontTouch(IO(new PWMDACAnalogIO(params)))

  val pwmdacFrontend = outer.pwmdacFrontend.module
  val dma = outer.dma.module

  // Interrupt Message Store
  val messageStore = Module(new MessageStore(params))
  messageStore.io.in <> bmc.io.messages
  pwmdacFrontend.io.back.messages <> messageStore.io.out

  // Interrupts
  pwmdacFrontend.io.back.interrupt.rxError  := bmc.io.interrupt.rxError
  // LO/32 Counter
  val lo_counter = withNewClock(io.tuning.trim.g1(0).asClock) {
    val ctr = Reg(UInt(32.W))
    ctr := ctr + 1.U
    ctr
  }

  // Status
  pwmdacFrontend.io.back.status.status0 := Cat(bmc.io.state.assemblerState,
                                                 bmc.io.state.disassemblerState,
                                                 bmc.io.state.txState,
                                                 bmc.io.state.rxControllerState,
                                                 bmc.io.state.txControllerState,
                                                 bmc.io.state.mainControllerState,
                                                 io.data.rx.i.data,
                                                 io.data.rx.q.data)

}
