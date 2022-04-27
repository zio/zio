package fix

import zio._



import zio.Duration
import zio.internal.Platform
import zio.stream.ZStream
import zio.test.Gen
import zio.{ Console, FiberId, Random }
import zio.Console._
import zio.ZIO.attemptBlockingIO
import zio.test.Gen

object Zio2Renames {

  val flatMap1 = ZIO(1).flatMap((x: Int) => ZIO(x + 1))
  val flatMap2 = ZIO(1) flatMap { x: Int => ZIO(x + 1) }
  val effect   = ZIO("cool")

  val halt     = ZIO.failCause(Cause.fail("fail"))
  val haltWith = ZIO.failCauseWith(_ => Cause.fail("fail"))

  val toManaged_ = effect.toManaged
  val toManaged  = effect.toManagedWith(_ => ZIO.unit)
  val bimap      = effect.mapBoth(_ => ZIO.unit, _ => ZIO.unit)

  val printline = Console.printLine("HEY")

  // foreachParN
  val foreachParN = ZIO.foreachParN(4)(List(1,2)) { int =>
    ZIO.succeed(int)
  }

  // foreachParN[Types]
  val foreachParNWithTypes = ZIO.foreachParN[Any, Nothing, Int, Int, List](4)(List(1,2)) { int =>
    ZIO.succeed(int)
  }

  // Generators
  Gen.int
  Gen.string
  Gen.unicodeChar
  Gen.asciiChar
  Gen.byte
  Gen.char
  Gen.double
  Gen.float
  Gen.hexChar
  Gen.long
  Gen.hexCharLower
  Gen.short
  Gen.hexCharUpper
  Gen.asciiString
  Gen.dayOfWeek
  Gen.finiteDuration
  Gen.uuid
  Gen.localDate
  Gen.localTime
  Gen.localDateTime
  Gen.month
  Gen.monthDay
  Gen.offsetDateTime
  Gen.offsetTime
  Gen.period
  Gen.year
  Gen.yearMonth
  Gen.zonedDateTime
  Gen.zoneOffset
  Gen.zoneId

  // Blocking
  attemptBlockingIO(1)

  ZIO.succeed(1).onExecutionContext _

  Cause.fail("Die").isInterrupted

  FiberId

  zio.Duration
  
  val x: Layer[Nothing, Random] = zio.Random.live

  zio.Executor

  RuntimeConfig.fromExecutor(???)
  
  zio.RuntimeConfig
    .fromExecutor(???)

  Chunk.succeed(1).mapZIO(???)
  ZStream.succeed("hi") flatMap (x => ZStream.succeed(x))

  ZIO.executor.map(_.asExecutionContext)
  
  ZManaged.environmentWith( (x: Int) => x)
  ZManaged.environmentWithManaged( (x: Int) => ZManaged.succeed(x))
  ZManaged.environmentWithManaged( (x: Int) => ZManaged.succeed(x))

  ZIO.serviceWithZIO
}
