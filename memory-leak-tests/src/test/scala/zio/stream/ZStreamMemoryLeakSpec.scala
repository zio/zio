package zio.stream

import zio._

object ZStreamMemoryLeakSpec extends MemoryLeakSpec {
  def spec = suite("ZStream Memory Leak spec")(
    leakTest("mapZIOParUnordered(1) doesn't leak") {
      ZStream
        .iterate(1)(_ + 1)
        .mapZIOParUnordered(1)(i => ZIO.when(i % 1000 == 0)(ZIO.logDebug(s"$i")))
        .runDrain
    },
    leakTest("flatMapPar(1) doesn't leak") {
      ZStream
        .iterate(MemoryHolder())(_ => MemoryHolder())
        .flatMapPar(1) { i =>
          ZStream.fromZIO(ZIO.logDebug(s"$i"))
        }
        .runDrain
    }
  )
}
