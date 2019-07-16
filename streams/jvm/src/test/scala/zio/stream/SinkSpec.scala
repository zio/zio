package zio.stream

import org.scalacheck.Arbitrary
import org.specs2.ScalaCheck
import scala.{ Stream => _ }
import zio._
import zio.clock.Clock
import zio.duration._
import zio.testkit.TestClock
import java.util.concurrent.TimeUnit

class SinkSpec(implicit ee: org.specs2.concurrent.ExecutionEnv)
    extends TestRuntime
    with StreamTestUtils
    with GenIO
    with ScalaCheck {
  import ArbitraryStream._, ZSink.Step

  def is = "SinkSpec".title ^ s2"""
  Constructors
    Sink.foldLeft               $foldLeft
    Sink.fold                   $fold
    Sink.fold short circuits    $foldShortCircuits
    Sink.foldM                  $foldM
    Sink.foldM short circuits   $foldMShortCircuits
    Sink.collectAllWhile        $collectAllWhile
    ZSink.fromOutputStream      $sinkFromOutputStream
    ZSink.throttleEnforce       $throttleEnforce
    ZSink.throttleShape         $throttleShape

  Usecases
    Number array parsing with Sink.foldM  $jsonNumArrayParsingSinkFoldM
    Number array parsing with combinators $jsonNumArrayParsingSinkWithCombinators
  """

  private def foldLeft =
    prop { (s: Stream[String, Int], f: (String, Int) => String, z: String) =>
      unsafeRunSync(s.run(ZSink.foldLeft(z)(f))) must_=== slurp(s).map(_.foldLeft(z)(f))
    }

  private def fold =
    prop { (s: Stream[String, Int], f: (String, Int) => String, z: String) =>
      val ff = (acc: String, el: Int) => Step.more(f(acc, el))

      unsafeRunSync(s.run(ZSink.fold(z)(ff))) must_=== slurp(s).map(_.foldLeft(z)(f))
    }

  private def foldShortCircuits = {
    val empty: Stream[Nothing, Int]     = ZStream.empty
    val single: Stream[Nothing, Int]    = ZStream.succeed(1)
    val double: Stream[Nothing, Int]    = ZStream(1, 2)
    val failed: Stream[String, Nothing] = ZStream.fail("Ouch")

    def run[E](stream: Stream[E, Int]) = {
      var effects: List[Int] = Nil
      val sink = ZSink.fold[Any, Int, Int](0) { (_, a) =>
        effects ::= a
        Step.done(30, Chunk.empty)
      }

      val exit = unsafeRunSync(stream.run(sink))

      (exit, effects)
    }

    run(empty) must_=== ((Exit.succeed(0), Nil))
    run(single) must_=== ((Exit.succeed(30), List(1)))
    run(double) must_=== ((Exit.succeed(30), List(1)))
    run(failed) must_=== ((Exit.fail("Ouch"), Nil))
  }

  private def foldM = {
    implicit val ioArb: Arbitrary[IO[String, String]] = Arbitrary(genSuccess[String, String])

    prop { (s: Stream[String, Int], f: (String, Int) => IO[String, String], z: IO[String, String]) =>
      val ff         = (acc: String, el: Int) => f(acc, el).map(Step.more)
      val sinkResult = unsafeRunSync(z.flatMap(z => s.run(ZSink.foldM(z)(ff))))
      val foldResult = unsafeRunSync {
        s.foldLeft(List[Int]())((acc, el) => el :: acc)
          .use(IO.succeed)
          .map(_.reverse)
          .flatMap(_.foldLeft(z)((acc, el) => acc.flatMap(f(_, el))))
      }

      foldResult.succeeded ==> (sinkResult must_=== foldResult)
    }
  }

  private def foldMShortCircuits = {
    val empty: Stream[Nothing, Int]     = ZStream.empty
    val single: Stream[Nothing, Int]    = ZStream.succeed(1)
    val double: Stream[Nothing, Int]    = ZStream(1, 2)
    val failed: Stream[String, Nothing] = ZStream.fail("Ouch")

    def run[E](stream: Stream[E, Int]) = {
      var effects: List[Int] = Nil
      val sink = ZSink.foldM[Any, E, Int, Int, Int](0) { (_, a) =>
        effects ::= a
        IO.succeed(Step.done(30, Chunk.empty))
      }

      val exit = unsafeRunSync(stream.run(sink))

      (exit, effects)
    }

    run(empty) must_=== ((Exit.succeed(0), Nil))
    run(single) must_=== ((Exit.succeed(30), List(1)))
    run(double) must_=== ((Exit.succeed(30), List(1)))
    run(failed) must_=== ((Exit.fail("Ouch"), Nil))
  }

  private def collectAllWhile =
    prop { (s: Stream[String, String], f: String => Boolean) =>
      val sinkResult = unsafeRunSync(s.run(ZSink.collectAllWhile(f)))
      val listResult = slurp(s).map(_.takeWhile(f))

      listResult.succeeded ==> (sinkResult must_=== listResult)
    }

  private def jsonNumArrayParsingSinkFoldM = {
    sealed trait ParserState
    object ParserState {
      case object Start               extends ParserState
      case class Element(acc: String) extends ParserState
      case object Done                extends ParserState
    }

    val numArrayParser =
      ZSink
        .foldM((ParserState.Start: ParserState, List.empty[Int])) { (s, a: Char) =>
          s match {
            case (ParserState.Start, acc) =>
              a match {
                case a if a.isWhitespace => IO.succeed(ZSink.Step.more((ParserState.Start, acc)))
                case '['                 => IO.succeed(ZSink.Step.more((ParserState.Element(""), acc)))
                case _                   => IO.fail("Expected '['")
              }

            case (ParserState.Element(el), acc) =>
              a match {
                case a if a.isDigit => IO.succeed(ZSink.Step.more((ParserState.Element(el + a), acc)))
                case ','            => IO.succeed(ZSink.Step.more((ParserState.Element(""), acc :+ el.toInt)))
                case ']'            => IO.succeed(ZSink.Step.done((ParserState.Done, acc :+ el.toInt), Chunk.empty))
                case _              => IO.fail("Expected a digit or ,")
              }

            case (ParserState.Done, acc) =>
              IO.succeed(ZSink.Step.done((ParserState.Done, acc), Chunk.empty))
          }
        }
        .map(_._2)
        .chunked

    val src1         = ZStreamChunk.succeedLazy(Chunk.fromArray(Array('[', '1', '2')))
    val src2         = ZStreamChunk.succeedLazy(Chunk.fromArray(Array('3', ',', '4', ']')))
    val partialParse = unsafeRunSync(src1.run(numArrayParser))
    val fullParse    = unsafeRunSync((src1 ++ src2).run(numArrayParser))

    (partialParse must_=== (Exit.Success(List()))) and
      (fullParse must_=== (Exit.Success(List(123, 4))))
  }

  private def jsonNumArrayParsingSinkWithCombinators = {
    val comma: ZSink[Any, Nothing, Char, Char, List[Char]] = ZSink.collectAllWhile[Char](_ == ',')
    val brace: ZSink[Any, String, Char, Char, Char] =
      ZSink.read1[String, Char](a => s"Expected closing brace; instead: $a")((_: Char) == ']')
    val number: ZSink[Any, String, Char, Char, Int] =
      ZSink.collectAllWhile[Char](_.isDigit).map(_.mkString.toInt)
    val numbers = (number <*> (comma *> number).collectAllWhile[Char, Char](_ != ']'))
      .map(tp => tp._1 :: tp._2)

    val elements = numbers <* brace

    lazy val start: ZSink[Any, String, Char, Char, List[Int]] =
      ZSink.pull1(IO.fail("Input was empty")) {
        case a if a.isWhitespace => start
        case '['                 => elements
        case _                   => ZSink.fail("Expected '['")
      }

    val src1         = ZStreamChunk.succeedLazy(Chunk.fromArray(Array('[', '1', '2')))
    val src2         = ZStreamChunk.succeedLazy(Chunk.fromArray(Array('3', ',', '4', ']')))
    val partialParse = unsafeRunSync(src1.run(start.chunked))
    val fullParse    = unsafeRunSync((src1 ++ src2).run(start.chunked))

    (partialParse must_=== (Exit.fail("Expected closing brace; instead: None"))) and
      (fullParse must_=== (Exit.Success(List(123, 4))))
  }

  private def sinkFromOutputStream = unsafeRun {
    import java.io.ByteArrayOutputStream

    val output = new ByteArrayOutputStream()
    val data   = "0123456789"
    val stream = Stream(Chunk.fromArray(data.take(5).getBytes), Chunk.fromArray(data.drop(5).getBytes))

    stream.run(ZSink.fromOutputStream(output)) map { bytesWritten =>
      (bytesWritten must_=== 10) and (new String(output.toByteArray, "UTF-8") must_=== data)
    }
  }

  private def throttleEnforce = {

    def sinkTest(sink: ZSink[Clock, Nothing, Nothing, Int, Option[Int]]) =
      for {
        init1 <- sink.initial
        step1 <- sink.step(Step.state(init1), 1)
        res1  <- sink.extract(Step.state(step1))
        init2 <- sink.initial
        _     <- clock.sleep(7.milliseconds)
        step2 <- sink.step(Step.state(init2), 2)
        res2  <- sink.extract(Step.state(step2))
        init3 <- sink.initial
        _     <- clock.sleep(7.milliseconds)
        step3 <- sink.step(Step.state(init3), 3)
        res3  <- sink.extract(Step.state(step3))
      } yield (res1 must_=== Some(1)) and (res2 must_=== None) and (res3 must_=== Some(3))

    unsafeRun {
      for {
        clock <- Ref.make(TestClock.Zero).map(ref => new Clock { val clock = TestClock(ref) })
        test <- ZSink
                 .throttleEnforce[Int](1, 10.milliseconds)(_ => 1)
                 .use(sinkTest)
                 .provide(clock)
      } yield test
    }
  }

  private def throttleShape = {

    def sinkTest(sink: ZSink[Clock, Nothing, Nothing, Int, Int]) =
      for {
        init1   <- sink.initial
        step1   <- sink.step(Step.state(init1), 1)
        res1    <- sink.extract(Step.state(step1))
        init2   <- sink.initial
        step2   <- sink.step(Step.state(init2), 2)
        res2    <- sink.extract(Step.state(step2))
        init3   <- sink.initial
        step3   <- sink.step(Step.state(init3), 3)
        res3    <- sink.extract(Step.state(step3))
        elapsed <- clock.currentTime(TimeUnit.SECONDS)
      } yield (elapsed must_=== 5) and (List(res1, res2, res3) must_=== List(1, 2, 3))

    unsafeRun {
      for {
        clock <- Ref.make(TestClock.Zero).map(ref => new Clock { val clock = TestClock(ref) })
        test <- ZSink
                 .throttleShapeM[Any, Nothing, Int](1, 1.second)(n => UIO.succeed(n.toLong))
                 .use(sinkTest)
                 .provide(clock)
      } yield test
    }
  }
}
