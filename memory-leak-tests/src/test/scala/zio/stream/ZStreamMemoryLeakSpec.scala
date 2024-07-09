package zio.stream

import zio._

object ZStreamMemoryLeakSpec extends MemoryLeakSpec {
  def spec = suite("ZStream Memory Leak spec")(
    leakTest("mapZIOParUnordered not leak") {
      ZStream
        .iterate(1)(_ + 1)
        .mapZIOParUnordered(1)(i => ZIO.when(i % 1000 == 0)(ZIO.logDebug(s"$i")))
        .runDrain
    },
    leakTest("flatMapPar with Int.MaxValue not leak") {
      ZStream
        .iterate(MemoryHolder())(_ => MemoryHolder())
        .flatMapPar(Int.MaxValue) { i =>
          ZStream.fromZIO(ZIO.logDebug(s"$i"))
        }
        .runDrain
    }
  )
}
