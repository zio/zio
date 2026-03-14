package features.error_handling

import zio.{UIO, ZIO}

object ErrorHandling {

  def handleDefects(): ZIO[Any, Nothing, String] = {
    val faultyEffect: ZIO[Any, Nothing, String] = ZIO.fail(new RuntimeException("Test error"))

    faultyEffect.catchAll(_ => UIO.succeed("Recovered"))
  }

  def foldWithDefects(): String = {
    val faultyEffect: ZIO[Any, Nothing, String] = ZIO.fail(new RuntimeException("Test error"))

    faultyEffect.fold(_ => "Recovered", identity)
  }
}