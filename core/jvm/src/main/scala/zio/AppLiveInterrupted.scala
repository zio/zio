import zio.{App, Fiber, RIO, Runtime, ZIO}
import zio.interop.cats.effect._

object AppLiveInterrupted extends App {
  override def run(args: List[String]): ZIO[ZEnv, ExitCode, Unit] = {
    val app = ZIO.succeed(42).interrupt
    val runtime = Runtime.default
    val fiber = runtime.unsafe.runApp(app)
    fiber.join
  }
}