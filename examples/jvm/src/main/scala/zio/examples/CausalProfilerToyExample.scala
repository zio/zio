package zio.examples

import zio._
import zio.profiling._

object CausalProfilerToyExample extends ZIOAppDefault {

  def run =
    CausalProfiler
      .profile(ProfilerConfig.Default.copy(iterations = 100)) {
        val io = for {
          _    <- CausalProfiler.progressPoint("iteration start")
          short = ZIO.succeed(Thread.sleep(80))
          long  = ZIO.succeed(Thread.sleep(100))
          _    <- short.zipPar(long)
        } yield ()
        io.forever
      }
      .flatMap(_.writeToFile("profile.coz"))

}
