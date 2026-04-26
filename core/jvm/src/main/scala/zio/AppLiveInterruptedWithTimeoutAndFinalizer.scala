import zio.{App, Fiber, RIO, Runtime, ZIO}
import zio.interop.cats.effect._

object AppLiveInterruptedWithTimeoutAndFinalizer extends App {
  override def run(args: List[String]): ZIO[ZEnv, ExitCode, Unit] = {
    val app = ZIO.succeed(42).interrupt.finally(ZIO.succeed(Console.writeLine("Finalizer ran")))
    val runtime = Runtime.default
    val fiber = runtime.unsafe.runApp(app, 1)
    fiber.join
  }
}