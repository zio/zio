package zio.stream

import org.scalacheck.Arbitrary
import org.specs2.ScalaCheck
import scala.{ Stream => _ }
import zio._
import zio.clock.Clock
import zio.duration._
import zio.test.mock.MockClock
import java.util.concurrent.TimeUnit

class SinkSpec(implicit ee: org.specs2.concurrent.ExecutionEnv)
    extends TestRuntime
    with StreamTestUtils
    with GenIO
    with ScalaCheck {
  import ArbitraryStream._, ZSink.Step

  def is = "SinkSpec".title ^ s2"""
  Combinators
    ? (optional)
      happy path    $optionalHappyPath
      init error    $optionalInitError
      step error    $optionalStepError
      extract error $optionalExtractError

    chunked
      happy path    $chunkedHappyPath
      empty         $chunkedEmpty
      init error    $chunkedInitError
      step error    $chunkedStepError
      extract error $chunkedExtractError

    collectAll
      happy path         $collectAllHappyPath
      init error         $collectAllInitError
      step error         $collectAllStepError
      extract error      $collectAllExtractError

    collectAllWhile
      happy path      $collectAllWhileHappyPath
      false predicate $collectAllWhileFalsePredicate
      init error      $collectAllWhileInitError
      step error      $collectAllWhileStepError
      extract error   $collectAllWhileExtractError

    contramap
      happy path    $contramapHappyPath
      init error    $contramapInitError
      step error    $contramapStepError
      extract error $contramapExtractError

    contramapM
      happy path    $contramapMHappyPath
      init error    $contramapMInitError
      step error    $contramapMStepError
      extract error $contramapMExtractError
    
    const
      happy path    $constHappyPath
      init error    $constInitError
      step error    $constStepError
      extract error $constExtractError

    dimap
      happy path    $dimapHappyPath
      init error    $dimapInitError
      step error    $dimapStepError
      extract error $dimapExtractError

    dropWhile
      happy path      $dropWhileHappyPath
      false predicate $dropWhileFalsePredicate
      init error      $dropWhileInitError
      step error      $dropWhileStepError
      extract error   $dropWhileExtractError

  Constructors
    Sink.foldLeft                         $foldLeft
    Sink.fold                             $fold
    Sink.fold short circuits              $foldShortCircuits
    Sink.foldM                            $foldM
    Sink.foldM short circuits             $foldMShortCircuits
    Sink.collectAllWhile                  $collectAllWhile
    Sink.foldWeighted                     $foldWeighted
    Sink.foldWeightedM                    $foldWeightedM
    Sink.foldUntil                        $foldUntil
    Sink.foldUntilM                       $foldUntilM
    Sink.fromOutputStream                 $sinkFromOutputStream
    Sink.throttleEnforce                  $throttleEnforce
    Sink.throttleEnforce with burst       $throttleEnforceWithBurst
    Sink.throttleShape                    $throttleShape
    Sink.throttleShape infinite bandwidth $throttleShapeInfiniteBandwidth
    Sink.throttleShape with burst         $throttleShapeWithBurst

  Usecases
    Number array parsing with Sink.foldM  $jsonNumArrayParsingSinkFoldM
    Number array parsing with combinators $jsonNumArrayParsingSinkWithCombinators
  """

  private def initErrorSink = new ZSink[Any, String, Int, Int, Int] {
    type State = Unit
    val initial                    = IO.fail("Ouch")
    def step(state: State, a: Int) = IO.fail("Ouch")
    def extract(state: State)      = IO.fail("Ouch")
  }

  private def stepErrorSink = new ZSink[Any, String, Int, Int, Int] {
    type State = Unit
    val initial                    = UIO.succeed(Step.more(()))
    def step(state: State, a: Int) = IO.fail("Ouch")
    def extract(state: State)      = IO.fail("Ouch")
  }

  private def extractErrorSink = new ZSink[Any, String, Int, Int, Int] {
    type State = Unit
    val initial                    = UIO.succeed(Step.more(()))
    def step(state: State, a: Int) = UIO.succeed(Step.done((), Chunk.empty))
    def extract(state: State)      = IO.fail("Ouch")
  }

  private def sinkIteration[R, E, A0, A, B](sink: ZSink[R, E, A0, A, B], a: A) =
    for {
      init   <- sink.initial
      step   <- sink.step(Step.state(init), a)
      result <- sink.extract(Step.state(step))
    } yield result

  private def optionalHappyPath = {
    val sink = ZSink.identity[Int].?
    unsafeRun(sinkIteration(sink, 1).map(_ must_=== Some(1)))
  }

  private def optionalInitError = {
    val sink = initErrorSink.?
    unsafeRun(sinkIteration(sink, 1).map(_ must_=== None))
  }

  private def optionalStepError = {
    val sink = stepErrorSink.?
    unsafeRun(sinkIteration(sink, 1).map(_ must_=== None))
  }

  private def optionalExtractError = {
    val sink = extractErrorSink.?
    unsafeRun(sinkIteration(sink, 1).map(_ must_=== None))
  }

  private def chunkedHappyPath = {
    val sink = ZSink.collectAll[Int].chunked
    unsafeRun(sinkIteration(sink, Chunk(1, 2, 3, 4, 5)).map(_ must_=== List(1, 2, 3, 4, 5)))
  }

  private def chunkedEmpty = {
    val sink = ZSink.collectAll[Int].chunked
    unsafeRun(sinkIteration(sink, Chunk.empty).map(_ must_=== Nil))
  }

  private def chunkedInitError = {
    val sink = initErrorSink.chunked
    unsafeRun(sinkIteration(sink, Chunk.single(1)).option.map(_ must_=== None))
  }

  private def chunkedStepError = {
    val sink = stepErrorSink.chunked
    unsafeRun(sinkIteration(sink, Chunk.single(1)).option.map(_ must_=== None))
  }

  private def chunkedExtractError = {
    val sink = extractErrorSink.chunked
    unsafeRun(sinkIteration(sink, Chunk.single(1)).option.map(_ must_=== None))
  }

  private def collectAllHappyPath = {
    val sink = ZSink.identity[Int].collectAll
    unsafeRun(sinkIteration(sink, 1).map(_ must_=== List(1)))
  }

  private def collectAllInitError = {
    val sink = initErrorSink.collectAll
    unsafeRun(sinkIteration(sink, 1).option.map(_ must_=== None))
  }

  private def collectAllStepError = {
    // This test needs to be verified for correctness.
    val sink = stepErrorSink.collectAll
    unsafeRun(sinkIteration(sink, 1).option.map(_ must_=== Some(Nil)))
  }

  private def collectAllExtractError = {
    val sink = extractErrorSink.collectAll
    unsafeRun(sinkIteration(sink, 1).option.map(_ must_=== None))
  }

  private def collectAllWhileHappyPath = {
    val sink = ZSink.identity[Int].collectAllWhile[Int, Int](_ < 10)
    unsafeRun(sinkIteration(sink, 1).map(_ must_=== List(1)))
  }

  private def collectAllWhileFalsePredicate = {
    // This test needs to be verified for correctness.
    // I find this behavior to be surprising.
    val sink = ZSink.identity[Int].collectAllWhile[Int, Int](_ < 0)
    unsafeRun(sinkIteration(sink, 1).option.map(_ must_=== None))
    // Fails instead of returning empty list.
    // I would presume that sinkIteration(sink, 1).map(_ must_=== Nil) is the correct behavior.
  }

  private def collectAllWhileInitError = {
    val sink = initErrorSink.collectAllWhile[Int, Int](_ > 1)
    unsafeRun(sinkIteration(sink, 1).option.map(_ must_=== None))
  }

  private def collectAllWhileStepError = {
    val sink = stepErrorSink.collectAllWhile[Int, Int](_ > 1)
    unsafeRun(sinkIteration(sink, 1).option.map(_ must_=== None))
  }

  private def collectAllWhileExtractError = {
    val sink = extractErrorSink.collectAllWhile[Int, Int](_ > 1)
    unsafeRun(sinkIteration(sink, 1).option.map(_ must_=== None))
  }

  private def contramapHappyPath = {
    val sink = ZSink.identity[Int].contramap[String](_.toInt)
    unsafeRun(sinkIteration(sink, "1").map(_ must_=== 1))
  }

  private def contramapInitError = {
    val sink = initErrorSink.contramap[String](_.toInt)
    unsafeRun(sinkIteration(sink, "1").option.map(_ must_=== None))
  }

  private def contramapStepError = {
    val sink = stepErrorSink.contramap[String](_.toInt)
    unsafeRun(sinkIteration(sink, "1").option.map(_ must_=== None))
  }

  private def contramapExtractError = {
    val sink = extractErrorSink.contramap[String](_.toInt)
    unsafeRun(sinkIteration(sink, "1").option.map(_ must_=== None))
  }

  private def contramapMHappyPath = {
    val sink = ZSink.identity[Int].contramapM[Any, Unit, String](s => UIO.succeed(s.toInt))
    unsafeRun(sinkIteration(sink, "1").map(_ must_=== 1))
  }

  private def contramapMInitError = {
    val sink = initErrorSink.contramapM[Any, String, String](s => UIO.succeed(s.toInt))
    unsafeRun(sinkIteration(sink, "1").option.map(_ must_=== None))
  }

  private def contramapMStepError = {
    val sink = stepErrorSink.contramapM[Any, String, String](s => UIO.succeed(s.toInt))
    unsafeRun(sinkIteration(sink, "1").option.map(_ must_=== None))
  }

  private def contramapMExtractError = {
    val sink = extractErrorSink.contramapM[Any, String, String](s => UIO.succeed(s.toInt))
    unsafeRun(sinkIteration(sink, "1").option.map(_ must_=== None))
  }

  private def constHappyPath = {
    val sink = ZSink.identity[Int].const("const")
    unsafeRun(sinkIteration(sink, 1).map(_ must_=== "const"))
  }

  private def constInitError = {
    val sink = initErrorSink.const("const")
    unsafeRun(sinkIteration(sink, 1).option.map(_ must_=== None))
  }

  private def constStepError = {
    val sink = stepErrorSink.const("const")
    unsafeRun(sinkIteration(sink, 1).option.map(_ must_=== None))
  }

  private def constExtractError = {
    val sink = extractErrorSink.const("const")
    unsafeRun(sinkIteration(sink, 1).option.map(_ must_=== None))
  }

  private def dimapHappyPath = {
    val sink = ZSink.identity[Int].dimap[String, String](_.toInt)(_.toString.reverse)
    unsafeRun(sinkIteration(sink, "123").map(_ must_=== "321"))
  }

  private def dimapInitError = {
    val sink = initErrorSink.dimap[String, String](_.toInt)(_.toString.reverse)
    unsafeRun(sinkIteration(sink, "123").option.map(_ must_=== None))
  }

  private def dimapStepError = {
    val sink = stepErrorSink.dimap[String, String](_.toInt)(_.toString.reverse)
    unsafeRun(sinkIteration(sink, "123").option.map(_ must_=== None))
  }

  private def dimapExtractError = {
    val sink = extractErrorSink.dimap[String, String](_.toInt)(_.toString.reverse)
    unsafeRun(sinkIteration(sink, "123").option.map(_ must_=== None))
  }

  private def dropWhileHappyPath = {
    val sink = ZSink.identity[Int].dropWhile[Int](_ < 5)
    unsafeRun(sinkIteration(sink, 1).option.map(_ must_=== None))
  }

  private def dropWhileFalsePredicate = {
    val sink = ZSink.identity[Int].dropWhile[Int](_ > 5)
    unsafeRun(sinkIteration(sink, 1).map(_ must_=== 1))
  }

  private def dropWhileInitError = {
    val sink = initErrorSink.dropWhile[Int](_ < 5)
    unsafeRun(sinkIteration(sink, 1).option.map(_ must_=== None))
  }

  private def dropWhileStepError = {
    val sink = stepErrorSink.dropWhile[Int](_ < 5)
    unsafeRun(sinkIteration(sink, 1).option.map(_ must_=== None))
  }

  private def dropWhileExtractError = {
    val sink = extractErrorSink.dropWhile[Int](_ < 5)
    unsafeRun(sinkIteration(sink, 1).option.map(_ must_=== None))
  }

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

  private def foldWeighted = unsafeRun {
    Stream[Long](1, 5, 2, 3)
      .transduce(Sink.foldWeighted(List[Long]())((_: Long) * 2, 12)((acc, el) => el :: acc).map(_.reverse))
      .runCollect
      .map(_ must_=== List(List(1, 5), List(2, 3)))
  }

  private def foldWeightedM = unsafeRun {
    Stream[Long](1, 5, 2, 3)
      .transduce(
        Sink
          .foldWeightedM(List[Long]())((a: Long) => UIO.succeed(a * 2), 12)((acc, el) => UIO.succeed(el :: acc))
          .map(_.reverse)
      )
      .runCollect
      .map(_ must_=== List(List(1, 5), List(2, 3)))
  }

  private def foldUntil = unsafeRun {
    Stream[Long](1, 1, 1, 1, 1, 1)
      .transduce(Sink.foldUntil(0L, 3)(_ + (_: Long)))
      .runCollect
      .map(_ must_=== List(3, 3))
  }

  private def foldUntilM = unsafeRun {
    Stream[Long](1, 1, 1, 1, 1, 1)
      .transduce(Sink.foldUntilM(0L, 3)((s, a: Long) => UIO.succeed(s + a)))
      .runCollect
      .map(_ must_=== List(3, 3))
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
        _     <- clock.sleep(23.milliseconds)
        step2 <- sink.step(Step.state(init2), 2)
        res2  <- sink.extract(Step.state(step2))
        init3 <- sink.initial
        step3 <- sink.step(Step.state(init3), 3)
        res3  <- sink.extract(Step.state(step3))
        init4 <- sink.initial
        step4 <- sink.step(Step.state(init4), 4)
        res4  <- sink.extract(Step.state(step4))
        _     <- clock.sleep(11.milliseconds)
        init5 <- sink.initial
        step5 <- sink.step(Step.state(init5), 5)
        res5  <- sink.extract(Step.state(step5))
      } yield (List(res1, res2, res3, res4, res5) must_=== List(Some(1), Some(2), None, None, Some(5)))

    unsafeRun {
      for {
        clock <- MockClock.make(MockClock.DefaultData)
        test <- ZSink
                 .throttleEnforce[Int](1, 10.milliseconds)(_ => 1)
                 .use(sinkTest)
                 .provide(clock)
      } yield test
    }
  }

  private def throttleEnforceWithBurst = {

    def sinkTest(sink: ZSink[Clock, Nothing, Nothing, Int, Option[Int]]) =
      for {
        init1 <- sink.initial
        step1 <- sink.step(Step.state(init1), 1)
        res1  <- sink.extract(Step.state(step1))
        init2 <- sink.initial
        _     <- clock.sleep(23.milliseconds)
        step2 <- sink.step(Step.state(init2), 2)
        res2  <- sink.extract(Step.state(step2))
        init3 <- sink.initial
        step3 <- sink.step(Step.state(init3), 3)
        res3  <- sink.extract(Step.state(step3))
        init4 <- sink.initial
        step4 <- sink.step(Step.state(init4), 4)
        res4  <- sink.extract(Step.state(step4))
        _     <- clock.sleep(11.milliseconds)
        init5 <- sink.initial
        step5 <- sink.step(Step.state(init5), 5)
        res5  <- sink.extract(Step.state(step5))
      } yield (List(res1, res2, res3, res4, res5) must_=== List(Some(1), Some(2), Some(3), None, Some(5)))

    unsafeRun {
      for {
        clock <- MockClock.make(MockClock.DefaultData)
        test <- ZSink
                 .throttleEnforce[Int](1, 10.milliseconds, 1)(_ => 1)
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
        _       <- clock.sleep(4.seconds)
        step3   <- sink.step(Step.state(init3), 3)
        res3    <- sink.extract(Step.state(step3))
        elapsed <- clock.currentTime(TimeUnit.SECONDS)
      } yield (elapsed must_=== 8) and (List(res1, res2, res3) must_=== List(1, 2, 3))

    unsafeRun {
      for {
        clock <- MockClock.make(MockClock.DefaultData)
        test <- ZSink
                 .throttleShape[Int](1, 1.second)(_.toLong)
                 .use(sinkTest)
                 .provide(clock)
      } yield test
    }
  }

  private def throttleShapeInfiniteBandwidth = {

    def sinkTest(sink: ZSink[Clock, Nothing, Nothing, Int, Int]) =
      for {
        init1   <- sink.initial
        step1   <- sink.step(Step.state(init1), 1)
        res1    <- sink.extract(Step.state(step1))
        init2   <- sink.initial
        step2   <- sink.step(Step.state(init2), 2)
        res2    <- sink.extract(Step.state(step2))
        elapsed <- clock.currentTime(TimeUnit.SECONDS)
      } yield (elapsed must_=== 0) and (List(res1, res2) must_=== List(1, 2))

    unsafeRun {
      for {
        clock <- MockClock.make(MockClock.DefaultData)
        test <- ZSink
                 .throttleShape[Int](1, 0.seconds)(_ => 100000L)
                 .use(sinkTest)
                 .provide(clock)
      } yield test
    }
  }

  private def throttleShapeWithBurst = {

    def sinkTest(sink: ZSink[Clock, Nothing, Nothing, Int, Int]) =
      for {
        init1   <- sink.initial
        step1   <- sink.step(Step.state(init1), 1)
        res1    <- sink.extract(Step.state(step1))
        init2   <- sink.initial
        step2   <- sink.step(Step.state(init2), 2)
        res2    <- sink.extract(Step.state(step2))
        init3   <- sink.initial
        _       <- clock.sleep(4.seconds)
        step3   <- sink.step(Step.state(init3), 3)
        res3    <- sink.extract(Step.state(step3))
        elapsed <- clock.currentTime(TimeUnit.SECONDS)
      } yield (elapsed must_=== 6) and (List(res1, res2, res3) must_=== List(1, 2, 3))

    unsafeRun {
      for {
        clock <- MockClock.make(MockClock.DefaultData)
        test <- ZSink
                 .throttleShape[Int](1, 1.second, 2)(_.toLong)
                 .use(sinkTest)
                 .provide(clock)
      } yield test
    }
  }
}
