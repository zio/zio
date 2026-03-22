package zio.stream

import zio._
import java.util.concurrent.TimeUnit

object Repro extends ZIOAppDefault {
  def run = for {
    _     <- ZIO.debug("--- TESTING FIX (PARALLELISM IS DECOUPLED FROM BUFFER SIZE) ---")
    start <- Clock.currentTime(TimeUnit.SECONDS)
    _ <- ZStream
           .fromIterable(1 to 32)
           .mapZIOPar(n = 32, bufferSize = 2)(_ => ZIO.sleep(1.second))
           .runDrain
    end <- Clock.currentTime(TimeUnit.SECONDS)
    _   <- ZIO.debug(s"FINISHED IN: ${end - start} seconds (should be ~1, not ~16)")
    _   <- ZIO.debug("---------------------------------------------------------------")
  } yield ()
}
