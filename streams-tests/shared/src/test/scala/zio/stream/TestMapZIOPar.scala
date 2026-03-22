import zio._
import zio.stream._
import java.util.concurrent.TimeUnit

object Test extends ZIOAppDefault {
  def run = for {
    _ <- ZIO.debug("Starting test...")
    start <- Clock.currentTime(TimeUnit.MILLISECONDS)
    _ <- ZStream.fromIterable(1 to 32)
      .mapZIOPar(32, bufferSize = 2) { i =>
        ZIO.sleep(1.second) *> ZIO.debug(s"Processed $i")
      }
      .runDrain
    end <- Clock.currentTime(TimeUnit.MILLISECONDS)
    duration = end - start
    _ <- ZIO.debug(s"Completed in ${duration}ms")
    _ <- if (duration < 2000) ZIO.debug("SUCCESS: Ran concurrently!")
         else ZIO.debug("FAILURE: Took too long, did not run with full concurrency.")
  } yield ()
}
