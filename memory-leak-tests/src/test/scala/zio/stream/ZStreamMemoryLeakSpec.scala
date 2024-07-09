package zio.stream

import zio._

class ZStreamMemoryLeakSpec extends MemoryLeakSpec {

  leakTest("mapZIOParUnordered(8) doesn't leak") {
    ZStream
      .iterate(MemoryHolder())(_ => MemoryHolder())
      .mapZIOParUnordered(1)(i => ZIO.logDebug(s"$i"))
      .runDrain
  }

  leakTest("flatMapPar(1) doesn't leak") {
    ZStream
      .iterate(MemoryHolder())(_ => MemoryHolder())
      .flatMapPar(1) { i =>
        ZStream.fromZIO(ZIO.logDebug(s"$i").delay(5.millis))
      }
      .runDrain
  }

  leakTest("flatMapPar(Int.MaxValue) doesn't leak") {
    ZStream
      .iterate(1)(_ + 1)
      .flatMapPar(Int.MaxValue) { i =>
        ZStream.fromZIO(ZIO.logDebug(s"$i").delay(30.millis))
      }
      .runDrain
  }
}
