package fix

import zio._


import zio.Console
import zio.Console._
import zio.ZIO.attemptBlockingIO

object Zio2Renames {

  val flatMap1 = ZIO(1).flatMap((x: Int) => ZIO(x + 1))
  val flatMap2 = ZIO(1) flatMap { x: Int => ZIO(x + 1) }
  val effect   = ZIO("cool")

  val halt     = ZIO.failCause(Cause.fail("fail"))
  val haltWith = ZIO.failCauseWith(_ => Cause.fail("fail"))

  val toManaged_ = effect.toManaged
  val toManaged  = effect.toManagedWith(_ => UIO.unit)
  val bimap      = effect.mapBoth(_ => UIO.unit, _ => UIO.unit)

  val printline = Console.printLine("HEY")

  // foreachParN
  val foreachParN = ZIO.foreachPar(List(1,2))({ int =>
    ZIO.succeed(int)
  }).withParallelism(4)

  // foreachParN[Types]
  val foreachParNWithTypes = ZIO.foreachPar[Any, Nothing, Int, Int, List](List(1,2))({ int =>
    ZIO.succeed(int)
  }).withParallelism(4)

  // Blocking
  attemptBlockingIO(1)
}
