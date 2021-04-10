package zio.stream.experimental

import zio.random.Random
import zio.stream.experimental.ZChannelSimulatedChecks.Simulation.{opsToDoneChannel, opsToEffect, opsToOutChannel}
import zio.test.Assertion._
import zio.test._
import zio.{Chunk, IO, ZIO, ZIOBaseSpec}

object ZChannelSimulatedChecks extends ZIOBaseSpec {
  override def spec =
    suite("ZChannel simulated checks")(
      testM("done channel")(
        checkM(gen) { sim =>
          for {
            channelResult <- sim.asDoneChannel.run.run
            effectResult  <- sim.asEffect.run
          } yield assert(channelResult)(equalTo(effectResult))
        }
      ),
      testM("out channel")(
        checkM(gen) { sim =>
          for {
            channelResult <- sim.asOutChannel.runCollect.map(_._1).run
            effectResult  <- sim.asEffect.run.map(_.map(Chunk.single))
          } yield assert(channelResult)(equalTo(effectResult))
        }
      )
    )

  type Err = String
  type Res = Int

  private val genErr: Gen[Sized with Random, Err] = Gen.oneOf(Gen.const("err1"), Gen.const("err2"), Gen.const("err3"))
  private val genRes: Gen[Sized with Random, Res] = Gen.int(0, 100)

  private def cutAtFailure(ops: List[Op]): List[Op] =
    ops.reverse.dropWhile {
      case Fail(_) => false
      case _       => true
    }.reverse

  private def genOps(currentDepth: Int = 1): Gen[Sized with Random, Op] =
    Gen.double(0.0, 1.0).flatMap { n =>
      val r = (1.0 / currentDepth)

      val nonRecursive = Seq(
        Gen.const(Succeed),
        genErr.map(Fail),
        genRes.map(value => Map(value))
      )

      val recursive = Seq(
        Gen.listOf1(genOps(currentDepth + 1)).map(ops => FlatMap(cutAtFailure(ops))),
        Gen.listOf1(genOps(currentDepth + 1)).map(ops => Bracket(cutAtFailure(ops))),
        Gen.listOf1(genOps(currentDepth + 1)).map(ops => CatchAll(cutAtFailure(ops)))
      )

      if (n < r) {
        Gen.oneOf(nonRecursive ++ recursive: _*)
      } else {
        Gen.oneOf(nonRecursive: _*)
      }
    }

  val gen: Gen[Sized with Random, Simulation] =
    for {
      first <- genRes
      rest  <- Gen.listOf1(genOps()).map(cutAtFailure)
    } yield Simulation(first, rest)

  case class Simulation(start: Res, ops: List[Op]) {
    val asDoneChannel: ZChannel[Any, Err, Any, Res, Err, Nothing, Res] =
      opsToDoneChannel(ZChannel.succeed(start), ops)
    val asOutChannel: ZChannel[Any, Err, Any, Res, Err, Res, Any] =
      opsToOutChannel(ZChannel.write(start), ops)
    val asEffect: IO[Err, Res] =
      opsToEffect(ZIO.succeed(start), ops)

    override def toString: String = {
      val sb = new StringBuilder()
      sb.append("{ ")
      Simulation.writeOutChannelString(sb, s"ZChannel.write($start)", ops)
      sb.append("\n}")
      sb.toString()
    }
  }

  object Simulation {
    def opsToDoneChannel(
      start: ZChannel[Any, Err, Any, Res, Err, Nothing, Res],
      ops: List[Op]
    ): ZChannel[Any, Err, Any, Res, Err, Nothing, Res] =
      ops.foldLeft[ZChannel[Any, Err, Any, Res, Err, Nothing, Res]](start) { case (ch, op) =>
        op.asChannel(ch)
      }

    def opsToOutChannel(
      start: ZChannel[Any, Err, Any, Res, Err, Res, Any],
      ops: List[Op]
    ): ZChannel[Any, Err, Any, Res, Err, Res, Any] =
      ops.foldLeft[ZChannel[Any, Err, Any, Res, Err, Res, Any]](start) { case (ch, op) =>
        op.asOutChannel(ch)
      }

    def opsToEffect(start: IO[Err, Res], ops: List[Op]): IO[Err, Res] =
      ops.foldLeft[IO[Err, Res]](start) { case (effect, op) =>
        op.asEffect(effect)
      }

    def writeOutChannelString(sb: StringBuilder, start: String, ops: List[Op]): Unit = {
      sb.append(start)
      ops.foreach(op => op.writeOutChannelString(sb))
    }

  }

  sealed trait Op {
    def asChannel(ch: ZChannel[Any, Err, Any, Res, Err, Nothing, Res]): ZChannel[Any, Err, Any, Res, Err, Nothing, Res]
    def asOutChannel(ch: ZChannel[Any, Err, Any, Res, Err, Res, Any]): ZChannel[Any, Err, Any, Res, Err, Res, Any]
    def asEffect(f: IO[Err, Res]): IO[Err, Res]
    def writeOutChannelString(sb: StringBuilder): Unit
  }
  final case object Succeed extends Op {
    override def asChannel(
      ch: ZChannel[Any, Err, Any, Res, Err, Nothing, Res]
    ): ZChannel[Any, Err, Any, Res, Err, Nothing, Res] = ch
    override def asOutChannel(
      ch: ZChannel[Any, Err, Any, Res, Err, Res, Any]
    ): ZChannel[Any, Err, Any, Res, Err, Res, Any]       = ch
    override def asEffect(f: IO[Err, Res]): IO[Err, Res] = f

    override def writeOutChannelString(sb: StringBuilder): Unit = {}
  }
  final case class Fail(error: Err) extends Op {
    override def asChannel(
      ch: ZChannel[Any, Err, Any, Res, Err, Nothing, Res]
    ): ZChannel[Any, Err, Any, Res, Err, Nothing, Res] = ch.flatMap(_ => ZChannel.fail(error))
    override def asOutChannel(
      ch: ZChannel[Any, Err, Any, Res, Err, Res, Any]
    ): ZChannel[Any, Err, Any, Res, Err, Res, Any] =
      ch.concatMap(_ => ZChannel.fail(error))
    override def asEffect(f: IO[Err, Res]): IO[Err, Nothing] = f.flatMap(_ => IO.fail(error))

    override def writeOutChannelString(sb: StringBuilder): Unit = {
      sb.append(s""".concatMap(_ => ZChannel.fail("${error}"))"""); ()
    }
  }
  final case class Map(addN: Int) extends Op {
    override def asChannel(
      ch: ZChannel[Any, Err, Any, Res, Err, Nothing, Res]
    ): ZChannel[Any, Err, Any, Res, Err, Nothing, Res] =
      ch.map(_ + addN)

    override def asOutChannel(
      ch: ZChannel[Any, Err, Any, Res, Err, Res, Any]
    ): ZChannel[Any, Err, Any, Res, Err, Res, Any] =
      ch.mapOut(_ + addN)

    override def asEffect(f: IO[Err, Res]): IO[Err, Res] =
      f.map(_ + addN)

    override def writeOutChannelString(sb: StringBuilder): Unit = { sb.append(s".mapOut(_ + $addN)"); () }
  }
  final case class FlatMap(ops: List[Op]) extends Op {
    override def asChannel(
      ch: ZChannel[Any, Err, Any, Res, Err, Nothing, Res]
    ): ZChannel[Any, Err, Any, Res, Err, Nothing, Res] =
      ch.flatMap(value => Simulation.opsToDoneChannel(ZChannel.succeed(value), ops))

    override def asOutChannel(
      ch: ZChannel[Any, Err, Any, Res, Err, Res, Any]
    ): ZChannel[Any, Err, Any, Res, Err, Res, Any] =
      ch.concatMap(value => Simulation.opsToOutChannel(ZChannel.write(value), ops))

    override def asEffect(f: IO[Err, Res]): IO[Err, Res] =
      f.flatMap(value => Simulation.opsToEffect(ZIO.succeed(value), ops))

    override def writeOutChannelString(sb: StringBuilder): Unit = {
      sb.append(".concatMap { value => \n")
      Simulation.writeOutChannelString(sb, "ZChannel.write(value)", ops)
      sb.append("\n}")
      ()
    }
  }
  final case class Bracket(ops: List[Op]) extends Op {
    override def asChannel(
      ch: ZChannel[Any, Err, Any, Res, Err, Nothing, Res]
    ): ZChannel[Any, Err, Any, Res, Err, Nothing, Res] =
      ch.flatMap { value =>
        ZChannel.bracket(ZIO.succeed(value))(_ => ZIO.unit)(value =>
          Simulation.opsToDoneChannel(ZChannel.succeed(value), ops)
        )
      }

    override def asOutChannel(
      ch: ZChannel[Any, Err, Any, Res, Err, Res, Any]
    ): ZChannel[Any, Err, Any, Res, Err, Res, Any] =
      ch.concatMap { value =>
        ZChannel.bracketOut(ZIO.succeed(value))(_ => ZIO.unit).concatMap { value =>
          Simulation.opsToOutChannel(ZChannel.write(value), ops)
        }
      }

    override def asEffect(f: IO[Err, Res]): IO[Err, Res] =
      f.flatMap { value =>
        ZIO.bracket(ZIO.succeed(value))(_ => ZIO.unit)(value => Simulation.opsToEffect(ZIO.succeed(value), ops))
      }

    override def writeOutChannelString(sb: StringBuilder): Unit = {
      sb.append(".concatMap { value => \nZChannel.bracketOut(ZIO.succeed(value))(_ => ZIO.unit).concatMap { value =>\n")
      Simulation.writeOutChannelString(sb, "ZChannel.write(value)", ops)
      sb.append("\n}\n}")
      ()
    }
  }
  final case class CatchAll(ops: List[Op]) extends Op {
    override def asChannel(
      ch: ZChannel[Any, Err, Any, Res, Err, Nothing, Res]
    ): ZChannel[Any, Err, Any, Res, Err, Nothing, Res] =
      ch.catchAll(_ => Simulation.opsToDoneChannel(ZChannel.succeed(0), ops))

    override def asOutChannel(
      ch: ZChannel[Any, Err, Any, Res, Err, Res, Any]
    ): ZChannel[Any, Err, Any, Res, Err, Res, Any] =
      ch.catchAll(_ => Simulation.opsToOutChannel(ZChannel.write(0), ops))

    override def asEffect(f: IO[Err, Res]): IO[Err, Res] =
      f.catchAll(_ => Simulation.opsToEffect(ZIO.succeed(0), ops))

    override def writeOutChannelString(sb: StringBuilder): Unit = {
      sb.append(".catchAll { _ =>\n")
      Simulation.writeOutChannelString(sb, "ZChannel.write(0)", ops)
      sb.append("\n}")
      ()
    }
  }
}
