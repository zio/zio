package zio.stream

import zio.{ Chunk, IO, UIO, ZIO }
import zio.test.{ assert, TestResult }
import zio.test.Assertion.{ equalTo, isLeft, isRight, isTrue }

trait SinkUtils {
  import ZSink.Step

  def initErrorSink = new ZSink[Any, String, Int, Int, Int] {
    type State = Unit
    val initial                    = IO.fail("Ouch")
    def step(state: State, a: Int) = IO.fail("Ouch")
    def extract(state: State)      = IO.fail("Ouch")
  }

  def stepErrorSink = new ZSink[Any, String, Int, Int, Int] {
    type State = Unit
    val initial                    = UIO.succeed(Step.more(()))
    def step(state: State, a: Int) = IO.fail("Ouch")
    def extract(state: State)      = IO.fail("Ouch")
  }

  def extractErrorSink = new ZSink[Any, String, Int, Int, Int] {
    type State = Unit
    val initial                    = UIO.succeed(Step.more(()))
    def step(state: State, a: Int) = UIO.succeed(Step.done((), Chunk.empty))
    def extract(state: State)      = IO.fail("Ouch")
  }

  /** Searches for the `target` element in the stream.
   * When met - accumulates next `accumulateAfterMet` elements and returns as `leftover`
   * If `target` is not met - returns `default` with empty `leftover`
   */
  def sinkWithLeftover[A](target: A, accumulateAfterMet: Int, default: A) = new ZSink[Any, String, A, A, A] {
    override type State = Option[List[A]]

    override def extract(state: Option[List[A]]): ZIO[Any, String, A] =
      UIO.succeed(if (state.isEmpty) default else target)

    override def initial: ZIO[Any, String, Step[Option[List[A]], Nothing]] = UIO.succeed(Step.more(None))

    override def step(state: Option[List[A]], a: A): ZIO[Any, String, Step[Option[List[A]], A]] =
      state match {
        case None =>
          val st = if (a == target) Some(Nil) else None
          UIO.succeed(Step.more(st))
        case Some(acc) =>
          if (acc.length >= accumulateAfterMet)
            UIO.succeed(Step.done(state, Chunk.fromIterable(acc)))
          else
            UIO.succeed(Step.more(Some(acc :+ a)))
      }
  }

  def sinkIteration[R, E, A0, A, B](sink: ZSink[R, E, A0, A, B], a: A): ZIO[R, E, B] =
    for {
      init   <- sink.initial
      step   <- sink.step(Step.state(init), a)
      result <- sink.extract(Step.state(step))
    } yield result

  object ZipParLaws {
    def coherence[A, B, C](
      s: Stream[String, A],
      sink1: ZSink[Any, String, A, A, B],
      sink2: ZSink[Any, String, A, A, C]
    ): ZIO[Any, Nothing, TestResult] =
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
    ): ZIO[Any, Nothing, TestResult] =
      for {
        res     <- s.run(sink1.zipPar(sink2).zip(ZSink.collectAll[A])).either
        swapped <- s.run(sink2.zipPar(sink1).zip(ZSink.collectAll[A])).either
      } yield {
        assert(swapped, equalTo(res.map {
          case ((b, c), rem) => ((c, b), rem)
        }))
      }

    def remainders[A, B, C](
      s: Stream[String, A],
      sink1: ZSink[Any, String, A, A, B],
      sink2: ZSink[Any, String, A, A, C]
    ): ZIO[Any, Nothing, TestResult] = {
      val maybeProp = for {
        rem1 <- s.run(sink1.zipRight(ZSink.collectAll[A]))
        rem2 <- s.run(sink2.zipRight(ZSink.collectAll[A]))
        rem  <- s.run(sink1.zipPar(sink2).zipRight(ZSink.collectAll[A]))
      } yield {
        val (longer, shorter) = if (rem1.length <= rem2.length) (rem2, rem1) else (rem1, rem2)
        assert(longer, equalTo(rem)) && assert(rem.endsWith(shorter), isTrue)
      }
      //irrelevant if an error occurred
      maybeProp.catchAll(_ => UIO.succeed(assert(true, isTrue)))
    }

    def laws[A, B, C](
      s: Stream[String, A],
      sink1: ZSink[Any, String, A, A, B],
      sink2: ZSink[Any, String, A, A, C]
    ): ZIO[Any, Nothing, TestResult] =
      (coherence(s, sink1, sink2) <*> remainders(s, sink1, sink2) <*> swap(s, sink1, sink2)).map {
        case ((c, r), s) => c && r && s
      }
  }
}

object SinkUtils extends SinkUtils
