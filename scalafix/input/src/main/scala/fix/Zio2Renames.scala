/*
rule = Zio2Upgrade
 */
package fix

import zio._
import zio.blocking.effectBlockingIO
import zio.blocking._
import zio.console._

object Zio2Renames {

  val flatMap1 = ZIO(1).>>=((x: Int) => ZIO(x + 1))
  val flatMap2 = ZIO(1) >>= { x: Int => ZIO(x + 1) }
  val effect   = ZIO("cool")

  val halt     = ZIO.halt(Cause.fail("fail"))
  val haltWith = ZIO.haltWith(_ => Cause.fail("fail"))

  val toManaged_ = effect.toManaged_
  val toManaged  = effect.toManaged(_ => UIO.unit)
  val bimap      = effect.bimap(_ => UIO.unit, _ => UIO.unit)

  val printline = console.putStrLn("HEY")

  // foreachParN
  val foreachParN = ZIO.foreachParN(4)(List(1,2)) { int =>
    ZIO.succeed(int)
  }

  // foreachParN[Types]
  val foreachParNWithTypes = ZIO.foreachParN[Any, Nothing, Int, Int, List](4)(List(1,2)) { int =>
    ZIO.succeed(int)
  }

  // Blocking
  effectBlockingIO(1)
}
