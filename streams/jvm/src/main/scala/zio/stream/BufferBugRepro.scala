package zio.stream

import zio._

object BufferBugRepro extends ZIOAppDefault {

  def fakeNetworkCall(n: Int): ZIO[Any, Throwable, String] = {
  for {
    _ <- Console.printLine(s"Starting request $n")
    _ <- ZIO.sleep(1.second)
    _ <- Console.printLine(s"Completed request $n")
  } yield s"Response for $n"
}

val program: ZIO[Any, Throwable, Unit] =
  ZStream
    .fromIterator(Iterator.from(1))
    .mapZIO(fakeNetworkCall)
    .buffer(1)
    .runForeach { response =>
      for {
        _ <- Console.printLine(s"Press Enter to process $response...")
        _ <- ZIO.sleep(10.seconds)
        _ <- Console.printLine(s"Processing response $response")
        _ <- ZIO.sleep(1.second)
        _ <- Console.printLine(s"Done processing $response")
      } yield ()
    }

  def run = program
}
