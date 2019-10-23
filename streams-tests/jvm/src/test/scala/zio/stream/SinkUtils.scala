package zio.stream

import zio.{ Chunk, IO, UIO }
import zio.test.{ assert, Gen, GenZIO, TestResult }
import zio.test.Assertion.{ equalTo, isLeft, isRight, isTrue }

trait SinkUtils {
  def initErrorSink = new ZSink[Any, String, Int, Int, Int] {
    type State = Unit
    val initial                    = IO.fail("Ouch")
    def step(state: State, a: Int) = IO.fail("Ouch")
    def extract(state: State)      = IO.fail("Ouch")
    def cont(state: State)         = false
  }

  def stepErrorSink = new ZSink[Any, String, Int, Int, Int] {
    type State = Unit
    val initial                    = UIO.succeed(())
    def step(state: State, a: Int) = IO.fail("Ouch")
    def extract(state: State)      = IO.fail("Ouch")
    def cont(state: State)         = false
  }

  def extractErrorSink = new ZSink[Any, String, Int, Int, Int] {
    type State = Unit
    val initial                    = UIO.succeed(())
    def step(state: State, a: Int) = UIO.succeed(())
    def extract(state: State)      = IO.fail("Ouch")
    def cont(state: State)         = false
  }

  /** Searches for the `target` element in the stream.
   * When met - accumulates next `accumulateAfterMet` elements and returns as `leftover`
   * If `target` is not met - returns `default` with empty `leftover`
   */
  def sinkWithLeftover[A](target: A, accumulateAfterMet: Int, default: A) = new ZSink[Any, String, A, A, A] {
    type State = (Option[List[A]], Chunk[A])

    def extract(state: State) = UIO.succeed((if (state._1.isEmpty) default else target, state._2))

    def initial = UIO.succeed((None, Chunk.empty))

    def step(state: State, a: A) =
      state match {
        case (None, _) =>
          val st = if (a == target) Some(Nil) else None
          UIO.succeed((st, state._2))
        case (Some(acc), _) =>
          if (acc.length >= accumulateAfterMet)
            UIO.succeed((state._1, Chunk.fromIterable(acc)))
          else
            UIO.succeed((Some(acc :+ a), state._2))
      }

    def cont(state: State) = state._2.isEmpty
  }

  def sinkIteration[R, E, A0, A, B](sink: ZSink[R, E, A0, A, B], a: A) =
    sink.initial >>= (sink.step(_, a)) >>= sink.extract

  object ZipParLaws {
    def coherence[A, B, C](
      s: Stream[String, A],
      sink1: ZSink[Any, String, A, A, B],
      sink2: ZSink[Any, String, A, A, C]
    ) =
      for {
        zb  <- s.run(sink1).either
        zc  <- s.run(sink2).either
        zbc <- s.run(sink1.zipPar(sink2)).either
      } yield {
        zbc match {
          case Left(e)       => assert(zb, isLeft(equalTo(e))) || assert(zc, isLeft(equalTo(e)))
          case Right((b, c)) => assert(zb, isRight(equalTo(b))) && assert(zc, isRight(equalTo(c)))
        }
      }

    def swap[A, B, C](
      s: Stream[String, A],
      sink1: ZSink[Any, String, A, A, B],
      sink2: ZSink[Any, String, A, A, C]
    ): UIO[TestResult] =
      for {
        res     <- s.run(sink1.zipPar(sink2).zip(ZSink.collectAll[A])).either
        swapped <- s.run(sink2.zipPar(sink1).zip(ZSink.collectAll[A])).either
      } yield {
        res match {
          case Right(((b, c), rem)) =>
            assert(swapped, isRight(equalTo(((c, b), rem))))
          case _ => assert(true, isTrue)
        }
      }

    def remainders[A, B, C](
      s: Stream[String, A],
      sink1: ZSink[Any, String, A, A, B],
      sink2: ZSink[Any, String, A, A, C]
    ): UIO[TestResult] = {
      val maybeProp = for {
        rem1 <- s.run(sink1.zipRight(ZSink.collectAll[A]))
        rem2 <- s.run(sink2.zipRight(ZSink.collectAll[A]))
        rem  <- s.run(sink1.zipPar(sink2).zipRight(ZSink.collectAll[A]))
      } yield {
        val (_, shorter) = if (rem1.length <= rem2.length) (rem2, rem1) else (rem1, rem2)
        // assert(longer, equalTo(rem))
        assert(rem.endsWith(shorter), isTrue)
      }
      maybeProp.catchAll(_ => UIO.succeed(assert(true, isTrue)))
    }

    def laws[A, B, C](
      s: Stream[String, A],
      sink1: ZSink[Any, String, A, A, B],
      sink2: ZSink[Any, String, A, A, C]
    ) =
      (coherence(s, sink1, sink2) <*> remainders(s, sink1, sink2) <*> swap(s, sink1, sink2)).map {
        case ((x, y), z) => x && y && z
      }
  }
}

object SinkUtils extends SinkUtils with StreamUtils with GenZIO {
  val zipParLawsStream = Stream(1, 2, 3, 4, 5, 6)

  val weirdStringGenForSplitLines = Gen
    .listOf(Gen.string(Gen.printableChar).map(_.filterNot(c => c == '\n' || c == '\r')))
    .map(l => if (l.nonEmpty && l.last == "") l ++ List("a") else l)
}
