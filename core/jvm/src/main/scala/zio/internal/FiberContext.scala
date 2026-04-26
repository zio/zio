package zio.internal

import zio.{ Cause, Fiber, FiberContext, FiberId, UIO, ZIO, ZScheduler }
import zio.stacktracer.TracingImplicits.disableAutoTrace
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.duration.FiniteDuration

private[zio] abstract class FiberContext[-R, +E, +A](
  val id: FiberId,
  runtimeFlags: RuntimeFlags,
  override val scheduler: ZScheduler,
  metricsEnabled: Boolean
) extends Fiber.Runtime[E, A]
    with Runnable {

  def getFiberRef[A](ref: FiberRef[A]): A

  def setFiberRef[A](ref: FiberRef[A], value: A): Unit

  def updateFiberRef[A](ref: FiberRef[A])(f: A => A): Unit

  def overrideFiberRef[A](ref: FiberRef[A], value: A): Unit

  def overrideFiberRefWith[A](ref: FiberRef[A])(f: A => A): Unit

  def evaluateNow[R, E1 >: E, A1 >: A](zio: ZIO[R, E1, A1]): Unit

  def interruptAsFork(id: FiberId): UIO[Unit]

  def interruptAs(id: FiberId): UIO[Nothing]

  def await(timeout: Option[FiniteDuration]): UIO[Option[Either[Cause[E], A]]]

  def children: UIO[Chunk[Fiber.Runtime[Any, Any]]]

  def poll: UIO[Option[Either[Cause[E], A]]]

  def getRef[A](ref: FiberRef[A]): UIO[A]

  def setRef[A](ref: FiberRef[A], value: A): UIO[Unit]

  def updateRef[A](ref: FiberRef[A])(f: A => A): UIO[Unit]

  def overrideRef[A](ref: FiberRef[A], value: A): UIO[Unit]

  def overrideRefWith[A](ref: FiberRef[A])(f: A => A): UIO[Unit]

  def withRuntimeFlags(update: RuntimeFlags => RuntimeFlags): UIO[RuntimeFlags]

  def locally[R, E1 >: E, A1 >: A](zio: ZIO[R, E1, A1]): ZIO[R, E1, A1]

  def locallyWith[R, E1 >: E, A1 >: A](f: FiberRefs => FiberRefs)(zio: ZIO[R, E1, A1]): ZIO[R, E1, A1]

  def locallyScoped[R, E1 >: E, A1 >: A](zio: ZIO[R, E1, A1]): ZIO[R with Scope, E1, A1]

  def run(): Unit

  def resume(): Unit

  def unsafeAttachFiber(fiber: Fiber.Runtime[Any, Any]): Unit

  def unsafeRemoveFiber(fiber: Fiber.Runtime[Any, Any]): Unit

  def unsafeRemoveAll(): Chunk[Fiber.Runtime[Any, Any]]

  def unsafeGetFiber(id: FiberId): Option[Fiber.Runtime[Any, Any]]

  def unsafeGetAllFibers(): Chunk[Fiber.Runtime[Any, Any]]

  def unsafeGetRuntimeFlags(): RuntimeFlags

  def unsafeSetRuntimeFlags(flags: RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith0(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith1(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith2(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith3(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith4(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith5(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith6(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith7(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith8(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith9(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith10(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith11(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith12(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith13(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith14(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith15(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith16(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith17(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith18(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith19(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith20(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith21(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith22(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith23(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith24(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith25(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith26(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith27(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith28(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith29(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith30(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith31(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith32(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith33(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith34(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith35(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith36(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith37(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith38(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith39(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith40(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith41(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith42(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith43(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith44(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith45(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith46(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith47(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith48(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith49(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith50(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith51(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith52(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith53(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith54(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith55(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith56(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith57(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith58(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith59(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith60(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith61(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith62(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith63(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith64(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith65(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith66(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith67(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith68(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith69(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith70(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith71(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith72(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith73(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith74(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith75(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith76(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith77(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith78(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith79(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith80(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith81(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith82(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith83(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith84(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith85(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith86(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith87(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith88(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith89(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith90(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith91(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith92(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith93(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith94(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith95(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith96(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith97(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith98(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith99(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith100(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith101(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith102(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith103(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith104(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith105(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith106(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith107(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith108(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith109(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith110(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith111(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith112(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith113(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith114(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith115(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith116(update: RuntimeFlags => RuntimeFlags): RuntimeFlags

  def unsafeSetRuntimeFlagsWith117(update: RuntimeFlags => RuntimeFlags): Runtime