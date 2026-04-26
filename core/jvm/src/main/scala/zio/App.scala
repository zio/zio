import zio.{App, Fiber, RIO, Runtime, ZIO}
import zio.interop.cats.effect._

object App {
  def run(args: List[String]): ZIO[ZEnv, ExitCode, Unit] = {
    val app = ZIO.succeed(42)
    val runtime = Runtime.default
    val fiber = runtime.unsafe.runApp(app)
    fiber.join
  }
}