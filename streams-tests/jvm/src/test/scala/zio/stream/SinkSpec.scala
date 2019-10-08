package zio.stream

import org.scalacheck.{ Arbitrary, Gen }
import org.specs2.ScalaCheck
import scala.{ Stream => _ }
import zio._
import zio.clock.Clock
import zio.duration._
import zio.test.environment.TestClock
import java.util.concurrent.TimeUnit
import org.specs2.matcher.MatchResult
import org.specs2.matcher.describe.Diffable

class SinkSpec(implicit ee: org.specs2.concurrent.ExecutionEnv) extends TestRuntime with GenIO with ScalaCheck {
  import ArbitraryStream._

  def is = "SinkSpec".title ^ s2"""
  Combinators
    as
      happy path    $asHappyPath
      init error    $asInitError
      step error    $asStepError
      extract error $asExtractError

    asError
      init error    $asErrorInitError
      step error    $asErrorStepError
      extract error $asErrorExtractError

    chunked
      happy path      $chunkedHappyPath
      empty           $chunkedEmpty
      init error      $chunkedInitError
      step error      $chunkedStepError
      extract error   $chunkedExtractError
      leftover        $chunkedLeftover
      leftover append $chunkedLeftoverAppend

    collectAll
      happy path                   $collectAllHappyPath
      init error                   $collectAllInitError
      step error                   $collectAllStepError
      extract error                $collectAllExtractError
      interaction with succeed     $collectAllSucceed
      interaction with ignoreWhile $collectAllIgnoreWhile

    collectAllN
      happy path          $collectAllNHappyPath
      empty list          $collectAllNEmptyList
      init error          $collectAllNInitError
      step error          $collectAllNStepError
      extract error       $collectAllNExtractError

    collectAllWhile
      happy path             $collectAllWhileHappyPath
      false predicate        $collectAllWhileFalsePredicate
      init error             $collectAllWhileInitError
      step error             $collectAllWhileStepError
      extract error          $collectAllWhileExtractError

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

    flatMap
      happy path    $flatMapHappyPath
      init error    $flatMapInitError
      step error    $flatMapStepError
      extract error $flatMapExtractError
      self done     $flatMapSelfDone
      self more     $flatMapSelfMore
      pass leftover $flatMapPassLeftover
      end leftover  $flatMapEndLeftover

    filter
      happy path      $filterHappyPath
      false predicate $filterFalsePredicate
      init error      $filterInitError
      step error      $filterStepError
      extractError    $filterExtractError

    filterM
      happy path      $filterMHappyPath
      false predicate $filterMFalsePredicate
      init error      $filterMInitError
      step error      $filterMStepError
      extractError    $filterMExtractError

    keyed
      happy path    $keyedHappyPath
      init error    $keyedInitError
      step error    $keyedStepError
      extract error $keyedExtractError

    map
      happy path    $mapHappyPath
      init error    $mapInitError
      step error    $mapStepError
      extract error $mapExtractError

    mapError
      init error    $mapErrorInitError
      step error    $mapErrorStepError
      extract error $mapErrorExtractError

    mapM
      happy path    $mapMHappyPath
      init error    $mapMInitError
      step error    $mapMStepError
      extract error $mapMExtractError

    mapRemainder
      init error    $mapRemainderInitError
      step error    $mapRemainderStepError
      extract error $mapRemainderExtractError

    optional
      happy path    $optionalHappyPath
      init error    $optionalInitError
      step error    $optionalStepError
      extract error $optionalExtractError
      with leftover $optionalWithLeftover

    orElse
      left                       $orElseLeft
      right                      $orElseRight
      init error left            $orElseInitErrorLeft
      init error right           $orElseInitErrorRight
      init error both            $orElseInitErrorBoth
      step error left            $orElseStepErrorLeft
      step error right           $orElseStepErrorRight
      step error both            $orElseStepErrorBoth
      extract error left         $orElseExtractErrorLeft
      extract error right        $orElseExtractErrorRight
      extract error both         $orElseExtractErrorBoth
      left short right long      $orElseLeftShortRightLong
      left long right short      $orElseLeftLongRightShort
      left long fail right short $orElseLeftLongFailRightShort

    raceBoth
      left                $raceBothLeft
      init error left     $raceBothInitErrorLeft
      init error right    $raceBothInitErrorRight
      init error both     $raceBothInitErrorBoth
      step error left     $raceBothStepErrorLeft
      step error right    $raceBothStepErrorRight
      step error both     $raceBothStepErrorBoth
      extract error left  $raceBothExtractErrorLeft
      extract error right $raceBothExtractErrorRight
      extract error both  $raceBothExtractErrorBoth
      left wins           $raceBothLeftWins
      right wins          $raceBothRightWins

    takeWhile
      happy path          $takeWhileHappyPath
      false predicate     $takeWhileFalsePredicate
      init error          $takeWhileInitError
      step error          $takeWhileStepError
      extract error       $takeWhileExtractError

    untilOutput
      happy path      $untilOutputHappyPath
      false predicate $untilOutputFalsePredicate
      init error      $untilOutputInitError
      step error      $untilOutputStepError
      extract error   $untilOutputExtractError

    zip (<*>)
      happy path          $zipHappyPath
      init error left     $zipInitErrorLeft
      init error right    $zipInitErrorRight
      init error both     $zipInitErrorBoth
      step error left     $zipStepErrorLeft
      step error right    $zipStepErrorRight
      step error both     $zipStepErrorBoth
      extract error left  $zipExtractErrorLeft
      extract error right $zipExtractErrorRight
      extract error both  $zipExtractErrorBoth

    zipLeft (<*)
      happy path $zipLeftHappyPath

    zipPar
       happy path 1 $zipParHappyPathBothDone
       happy path 2 $zipParHappyPathOneNonterm
       happy path 3 $zipParHappyPathBothNonterm
       extract error $zipParErrorExtract
       step error $zipParErrorStep
       init error $zipParErrorInit
       both error $zipParErrorBoth
       remainder corner case 1 $zipParRemainderWhenCompleteSeparately
       remainder corner case 2 $zipParRemainderWhenCompleteTogether

    zipRight (*>)
      happy path $zipRightHappyPath

    zipWith
      happy path $zipWithHappyPath

  Constructors
    foldLeft $foldLeft

    fold             $fold
      short circuits $foldShortCircuits

    foldM            $foldM
      short circuits $foldMShortCircuits

    collectAllN $collectAllN

    collectAllToSet $collectAllToSet

    collectAllToSetN $collectAllToSetN

    collectAllToMap $collectAllToMap

    collectAllToMapN $collectAllToMapN

    collectAllWhile $collectAllWhile

    foldWeighted           $foldWeighted
    foldWeightedDecompose  $foldWeightedDecompose

    foldWeightedM          $foldWeightedM
    foldWeightedDecomposeM $foldWeightedDecomposeM

    foldUntil $foldUntil

    foldUntilM $foldUntilM

    fromFunction $fromFunction

    fromOutputStream $fromOutputStream

    pull1 $pull1

    splitLines
      preserves data          $splitLines
      handles leftovers       $splitLinesLeftovers
      transduces              $splitLinesTransduce
      single newline edgecase $splitLinesEdgecase
      no newlines in data     $splitLinesNoNewlines
      \r\n on the boundary    $splitLinesBoundary

    throttleEnforce $throttleEnforce
      with burst    $throttleEnforceWithBurst

    throttleShape        $throttleShape
      infinite bandwidth $throttleShapeInfiniteBandwidth
      with burst         $throttleShapeWithBurst

    utf8Decode $utf8Decode

    utf8DecodeChunk
      regular strings     $utf8DecodeChunk
      incomplete chunk 1  $utf8DecodeChunkIncomplete1
      incomplete chunk 2  $utf8DecodeChunkIncomplete2
      incomplete chunk 3  $utf8DecodeChunkIncomplete3
      chunk with leftover $utf8DecodeChunkWithLeftover

  Usecases
    Number array parsing with Sink.foldM  $jsonNumArrayParsingSinkFoldM
    Number array parsing with combinators $jsonNumArrayParsingSinkWithCombinators
  """

  private def initErrorSink = new ZSink[Any, String, Int, Int, Int] {
    type State = Unit
    val initial                    = IO.fail("Ouch")
    def step(state: State, a: Int) = IO.fail("Ouch")
    def extract(state: State)      = IO.fail("Ouch")
    def cont(state: State)         = false
  }

  private def stepErrorSink = new ZSink[Any, String, Int, Int, Int] {
    type State = Unit
    val initial                    = UIO.succeed(())
    def step(state: State, a: Int) = IO.fail("Ouch")
    def extract(state: State)      = IO.fail("Ouch")
    def cont(state: State)         = false
  }

  private def extractErrorSink = new ZSink[Any, String, Int, Int, Int] {
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
  private def sinkWithLeftover[A](target: A, accumulateAfterMet: Int, default: A) = new ZSink[Any, String, A, A, A] {
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

  private def sinkIteration[R, E, A0, A, B](sink: ZSink[R, E, A0, A, B], a: A) =
    for {
      init   <- sink.initial
      step   <- sink.step(init, a)
      result <- sink.extract(step)
    } yield result

  private def asHappyPath = {
    val sink = ZSink.identity[Int].as("const")
    unsafeRun(sinkIteration(sink, 1).map(_ must_=== (("const", Chunk.empty))))
  }

  private def asInitError = {
    val sink = initErrorSink.as("const")
    unsafeRun(sinkIteration(sink, 1).either.map(_ must_=== Left("Ouch")))
  }

  private def asStepError = {
    val sink = stepErrorSink.as("const")
    unsafeRun(sinkIteration(sink, 1).either.map(_ must_=== Left("Ouch")))
  }

  private def asErrorInitError = {
    val sink = initErrorSink.asError("Error")
    unsafeRun(sinkIteration(sink, 1).either.map(_ must_=== Left("Error")))
  }

  private def asErrorStepError = {
    val sink = stepErrorSink.asError("Error")
    unsafeRun(sinkIteration(sink, 1).either.map(_ must_=== Left("Error")))
  }

  private def asErrorExtractError = {
    val sink = extractErrorSink.asError("Error")
    unsafeRun(sinkIteration(sink, 1).either.map(_ must_=== Left("Error")))
  }

  private def asExtractError = {
    val sink = extractErrorSink.as("const")
    unsafeRun(sinkIteration(sink, 1).either.map(_ must_=== Left("Ouch")))
  }

  private def chunkedHappyPath = {
    val sink = ZSink.collectAll[Int].chunked
    unsafeRun(sinkIteration(sink, Chunk(1, 2, 3, 4, 5)).map {
      case (b, leftovers) => (b must_=== List(1, 2, 3, 4, 5)) and (leftovers must_=== Chunk.empty)
    })
  }

  private def chunkedEmpty = {
    val sink = ZSink.collectAll[Int].chunked
    unsafeRun(sinkIteration(sink, Chunk.empty).map {
      case (b, leftovers) => (b must_=== Nil) and (leftovers must_=== Chunk.empty)
    })
  }

  private def chunkedInitError = {
    val sink = initErrorSink.chunked
    unsafeRun(sinkIteration(sink, Chunk.single(1)).either.map(_ must_=== Left("Ouch")))
  }

  private def chunkedStepError = {
    val sink = stepErrorSink.chunked
    unsafeRun(sinkIteration(sink, Chunk.single(1)).either.map(_ must_=== Left("Ouch")))
  }

  private def chunkedExtractError = {
    val sink = extractErrorSink.chunked
    unsafeRun(sinkIteration(sink, Chunk.single(1)).either.map(_ must_=== Left("Ouch")))
  }

  private def chunkedLeftover = {
    val sink = ZSink.collectAllN[Int](2).chunked
    val test = for {
      init           <- sink.initial
      step           <- sink.step(init, Chunk(1, 2, 3, 4, 5))
      result         <- sink.extract(step)
      (b, leftovers) = result
    } yield (b must_=== List(1, 2)) and (leftovers must_=== Chunk(3, 4, 5))
    unsafeRun(test)
  }

  private def chunkedLeftoverAppend = {
    val sink = ZSink.ignoreWhile[Int](_ < 0).chunked
    val test = for {
      init   <- sink.initial
      step   <- sink.step(init, Chunk(1, 2, 3, 4, 5))
      result <- sink.extract(step)
    } yield result._2 must_=== Chunk(1, 2, 3, 4, 5)
    unsafeRun(test)
  }

  private def collectAllHappyPath = {
    val sink = ZSink.identity[Int].collectAll
    unsafeRun(sinkIteration(sink, 1).map(_ must_=== ((List(1), Chunk.empty))))
  }

  private def collectAllInitError = {
    val sink = initErrorSink.collectAll
    unsafeRun(sinkIteration(sink, 1).either.map(_ must_=== Left("Ouch")))
  }

  private def collectAllStepError = {
    val sink = stepErrorSink.collectAll
    unsafeRun(sinkIteration(sink, 1).either.map(_ must_=== Left("Ouch")))
  }

  private def collectAllExtractError = {
    val sink = extractErrorSink.collectAll
    unsafeRun(sinkIteration(sink, 1).either.map(_ must_=== Left("Ouch")))
  }

  private def collectAllSucceed = {
    val sink = ZSink.succeed[Int, Int](5).collectAll
    val test = for {
      init <- sink.initial
      s <- sink
            .step(init, 1)
            .flatMap(sink.step(_, 2))
            .flatMap(sink.step(_, 3))
      result <- sink.extract(s)
    } yield result must_=== (List(5, 5, 5, 5) -> Chunk(1, 2, 3))

    unsafeRun(test)
  }

  private def collectAllIgnoreWhile = {
    val sink = ZSink.ignoreWhile[Int](_ < 5).collectAll
    val test = for {
      result <- sink.initial
                 .flatMap(sink.step(_, 1))
                 .flatMap(sink.step(_, 2))
                 .flatMap(sink.step(_, 3))
                 .flatMap(sink.step(_, 5))
                 .flatMap(sink.step(_, 6))
                 .flatMap(sink.extract)
    } yield result must_=== (List((), ()) -> Chunk(5, 6))

    unsafeRun(test)
  }

  private def collectAllNHappyPath = {
    val sink = ZSink.identity[Int].collectAllN(3)
    val test = for {
      result <- sink.initial
                 .flatMap(sink.step(_, 1))
                 .flatMap(sink.step(_, 2))
                 .flatMap(sink.step(_, 3))
                 .flatMap(sink.step(_, 4))
                 .flatMap(sink.extract)
    } yield result must_=== ((List(1, 2, 3), Chunk(4)))
    unsafeRun(test)
  }

  private def collectAllNEmptyList = {
    val sink = ZSink.identity[Int].collectAllN(0)
    val test = for {
      init   <- sink.initial
      result <- sink.extract(init)
    } yield result must_=== ((Nil, Chunk.empty))
    unsafeRun(test)
  }

  private def collectAllNInitError = {
    val sink = initErrorSink.collectAllN(1)
    unsafeRun(sinkIteration(sink, 1).either.map(_ must_=== Left("Ouch")))
  }

  private def collectAllNStepError = {
    val sink = stepErrorSink.collectAllN(1)
    unsafeRun(sinkIteration(sink, 1).either.map(_ must_=== Left("Ouch")))
  }

  private def collectAllNExtractError = {
    val sink = extractErrorSink.collectAllN(1)
    unsafeRun(sinkIteration(sink, 1).either.map(_ must_=== Left("Ouch")))
  }

  private def collectAllWhileHappyPath = {
    val sink = ZSink.collectAll[Int].collectAllWhile(_ < 4)
    val test = for {
      init   <- sink.initial
      step1  <- sink.step(init, 1)
      step2  <- sink.step(step1, 2)
      step3  <- sink.step(step2, 3)
      step4  <- sink.step(step3, 4)
      result <- sink.extract(step4)
    } yield result must_=== ((List(List(1, 2, 3)), Chunk.single(4)))
    unsafeRun(test)
  }

  private def collectAllWhileFalsePredicate = {
    val sink = ZSink.identity[Int].collectAllWhile(_ < 0)
    unsafeRun(sinkIteration(sink, 1).map(_ must_=== (List() -> Chunk.single(1))))
  }

  private def collectAllWhileInitError = {
    val sink = initErrorSink.collectAllWhile(_ < 4)
    unsafeRun(sinkIteration(sink, 1).either.map(_ must_=== Left("Ouch")))
  }

  private def collectAllWhileStepError = {
    val sink = stepErrorSink.collectAllWhile(_ < 4)
    unsafeRun(sinkIteration(sink, 1).either.map(_ must_=== Left("Ouch")))
  }

  private def collectAllWhileExtractError = {
    val sink = extractErrorSink.collectAllWhile(_ < 4)
    unsafeRun(sinkIteration(sink, 1).either.map(_ must_=== Left("Ouch")))
  }

  private def contramapHappyPath = {
    val sink = ZSink.identity[Int].contramap[String](_.toInt)
    unsafeRun(sinkIteration(sink, "1").map(_ must_=== ((1, Chunk.empty))))
  }

  private def contramapInitError = {
    val sink = initErrorSink.contramap[String](_.toInt)
    unsafeRun(sinkIteration(sink, "1").either.map(_ must_=== Left("Ouch")))
  }

  private def contramapStepError = {
    val sink = stepErrorSink.contramap[String](_.toInt)
    unsafeRun(sinkIteration(sink, "1").either.map(_ must_=== Left("Ouch")))
  }

  private def contramapExtractError = {
    val sink = extractErrorSink.contramap[String](_.toInt)
    unsafeRun(sinkIteration(sink, "1").either.map(_ must_=== Left("Ouch")))
  }

  private def contramapMHappyPath = {
    val sink = ZSink.identity[Int].contramapM[Any, Unit, String](s => UIO.succeed(s.toInt))
    unsafeRun(sinkIteration(sink, "1").map(_ must_=== ((1, Chunk.empty))))
  }

  private def contramapMInitError = {
    val sink = initErrorSink.contramapM[Any, String, String](s => UIO.succeed(s.toInt))
    unsafeRun(sinkIteration(sink, "1").either.map(_ must_=== Left("Ouch")))
  }

  private def contramapMStepError = {
    val sink = stepErrorSink.contramapM[Any, String, String](s => UIO.succeed(s.toInt))
    unsafeRun(sinkIteration(sink, "1").either.map(_ must_=== Left("Ouch")))
  }

  private def contramapMExtractError = {
    val sink = extractErrorSink.contramapM[Any, String, String](s => UIO.succeed(s.toInt))
    unsafeRun(sinkIteration(sink, "1").either.map(_ must_=== Left("Ouch")))
  }

  private def dimapHappyPath = {
    val sink = ZSink.identity[Int].dimap[String, String](_.toInt)(_.toString.reverse)
    unsafeRun(sinkIteration(sink, "123").map(_ must_=== (("321", Chunk.empty))))
  }

  private def dimapInitError = {
    val sink = initErrorSink.dimap[String, String](_.toInt)(_.toString.reverse)
    unsafeRun(sinkIteration(sink, "123").either.map(_ must_=== Left("Ouch")))
  }

  private def dimapStepError = {
    val sink = stepErrorSink.dimap[String, String](_.toInt)(_.toString.reverse)
    unsafeRun(sinkIteration(sink, "123").either.map(_ must_=== Left("Ouch")))
  }

  private def dimapExtractError = {
    val sink = extractErrorSink.dimap[String, String](_.toInt)(_.toString.reverse)
    unsafeRun(sinkIteration(sink, "123").either.map(_ must_=== Left("Ouch")))
  }

  private def dropWhileHappyPath = {
    val sink = ZSink.identity[Int].dropWhile(_ < 5)
    unsafeRun(sinkIteration(sink, 1).either.map(_ must_=== Left(())))
  }

  private def dropWhileFalsePredicate = {
    val sink = ZSink.identity[Int].dropWhile(_ > 5)
    unsafeRun(sinkIteration(sink, 1).map(_ must_=== ((1, Chunk.empty))))
  }

  private def dropWhileInitError = {
    val sink = initErrorSink.dropWhile(_ < 5)
    unsafeRun(sinkIteration(sink, 1).either.map(_ must_=== Left("Ouch")))
  }

  private def dropWhileStepError = {
    val sink = stepErrorSink.dropWhile(_ < 5)
    unsafeRun(sinkIteration(sink, 1).either.map(_ must_=== Left("Ouch")))
  }

  private def dropWhileExtractError = {
    val sink = extractErrorSink.dropWhile(_ < 5)
    unsafeRun(sinkIteration(sink, 1).either.map(_ must_=== Left("Ouch")))
  }

  private def flatMapHappyPath = {
    val sink = ZSink.identity[Int].flatMap(n => ZSink.succeed[Int, String](n.toString))
    unsafeRun(sinkIteration(sink, 1).map(_ must_=== (("1", Chunk.empty))))
  }

  private def flatMapInitError = {
    val sink = initErrorSink.flatMap(n => ZSink.succeed[Int, String](n.toString))
    unsafeRun(sinkIteration(sink, 1).either.map(_ must_=== Left("Ouch")))
  }

  private def flatMapStepError = {
    val sink = stepErrorSink.flatMap(n => ZSink.succeed[Int, String](n.toString))
    unsafeRun(sinkIteration(sink, 1).either.map(_ must_=== Left("Ouch")))
  }

  private def flatMapExtractError = {
    val sink = extractErrorSink.flatMap(n => ZSink.succeed[Int, String](n.toString))
    unsafeRun(sinkIteration(sink, 1).either.map(_ must_=== Left("Ouch")))
  }

  private def flatMapSelfDone = {
    val sink = ZSink.succeed(3).flatMap(n => ZSink.collectAllN[Int](n.toLong))
    val test = for {
      init   <- sink.initial
      step1  <- sink.step(init, 1)
      step2  <- sink.step(step1, 2)
      step3  <- sink.step(step2, 3)
      step4  <- sink.step(step3, 4)
      step5  <- sink.step(step4, 5)
      result <- sink.extract(step5)
    } yield result must_=== ((List(1, 2, 3), Chunk(4, 5)))
    unsafeRun(test)
  }

  private def flatMapSelfMore = {
    val sink = ZSink.collectAll[Int].flatMap(list => ZSink.succeed[Int, Int](list.headOption.getOrElse(0)))
    val test = for {
      init   <- sink.initial
      step1  <- sink.step(init, 1)
      step2  <- sink.step(step1, 2)
      step3  <- sink.step(step2, 3)
      result <- sink.extract(step3)
    } yield result must_=== ((1, Chunk.empty))
    unsafeRun(test)
  }

  private def flatMapPassLeftover = {
    val sink = ZSink.ignoreWhile[Int](_ < 3).flatMap(_ => ZSink.identity[Int])
    val test = for {
      init   <- sink.initial
      step1  <- sink.step(init, 1)
      step2  <- sink.step(step1, 2)
      step3  <- sink.step(step2, 3)
      result <- sink.extract(step3)
    } yield result must_=== ((3, Chunk.empty))
    unsafeRun(test)
  }

  private def flatMapEndLeftover = {
    val sink = ZSink.ignoreWhile[Int](_ < 3).flatMap(_ => ZSink.ignoreWhile[Int](_ < 3))
    val test = for {
      init   <- sink.initial
      step1  <- sink.step(init, 1)
      step2  <- sink.step(step1, 2)
      step3  <- sink.step(step2, 3)
      result <- sink.extract(step3)
    } yield result must_=== (((), Chunk.single(3)))
    unsafeRun(test)
  }

  private def filterHappyPath = {
    val sink = ZSink.identity[Int].filter(_ < 5)
    unsafeRun(sinkIteration(sink, 1).map(_ must_=== ((1, Chunk.empty))))
  }

  private def filterFalsePredicate = {
    val sink = ZSink.identity[Int].filter(_ > 5)
    unsafeRun(sinkIteration(sink, 1).either.map(_ must_=== Left(())))
  }

  private def filterInitError = {
    val sink = initErrorSink.filter(_ < 5)
    unsafeRun(sinkIteration(sink, 1).either.map(_ must_=== Left("Ouch")))
  }

  private def filterStepError = {
    val sink = stepErrorSink.filter(_ < 5)
    unsafeRun(sinkIteration(sink, 1).either.map(_ must_=== Left("Ouch")))
  }

  private def filterExtractError = {
    val sink = extractErrorSink.filter(_ < 5)
    unsafeRun(sinkIteration(sink, 1).either.map(_ must_=== Left("Ouch")))
  }

  private def filterMHappyPath = {
    val sink = ZSink.identity[Int].filterM[Any, Unit](n => UIO.succeed(n < 5))
    unsafeRun(sinkIteration(sink, 1).map(_ must_=== ((1, Chunk.empty))))
  }

  private def filterMFalsePredicate = {
    val sink = ZSink.identity[Int].filterM[Any, Unit](n => UIO.succeed(n > 5))
    unsafeRun(sinkIteration(sink, 1).either.map(_ must_=== Left(())))
  }

  private def filterMInitError = {
    val sink = initErrorSink.filterM[Any, String](n => UIO.succeed(n < 5))
    unsafeRun(sinkIteration(sink, 1).either.map(_ must_=== Left("Ouch")))
  }

  private def filterMStepError = {
    val sink = stepErrorSink.filterM[Any, String](n => UIO.succeed(n < 5))
    unsafeRun(sinkIteration(sink, 1).either.map(_ must_=== Left("Ouch")))
  }

  private def filterMExtractError = {
    val sink = extractErrorSink.filterM[Any, String](n => UIO.succeed(n < 5))
    unsafeRun(sinkIteration(sink, 1).either.map(_ must_=== Left("Ouch")))
  }

  private def keyedHappyPath = {
    val sink = ZSink.identity[Int].keyed(_ + 1)
    unsafeRun(sinkIteration(sink, 1).map(_ must_=== ((Map(2 -> 1), Chunk.empty))))
  }

  private def keyedInitError = {
    val sink = initErrorSink.keyed(_ + 1)
    unsafeRun(sinkIteration(sink, 1).either.map(_ must_=== Left("Ouch")))
  }

  private def keyedStepError = {
    val sink = stepErrorSink.keyed(_ + 1)
    unsafeRun(sinkIteration(sink, 1).either.map(_ must_=== Left("Ouch")))
  }

  private def keyedExtractError = {
    val sink = extractErrorSink.keyed(_ + 1)
    unsafeRun(sinkIteration(sink, 1).either.map(_ must_=== Left("Ouch")))
  }

  private def mapHappyPath = {
    val sink = ZSink.identity[Int].map(_.toString)
    unsafeRun(sinkIteration(sink, 1).map(_ must_=== (("1", Chunk.empty))))
  }

  private def mapInitError = {
    val sink = initErrorSink.map(_.toString)
    unsafeRun(sinkIteration(sink, 1).either.map(_ must_=== Left("Ouch")))
  }

  private def mapStepError = {
    val sink = stepErrorSink.map(_.toString)
    unsafeRun(sinkIteration(sink, 1).either.map(_ must_=== Left("Ouch")))
  }

  private def mapExtractError = {
    val sink = extractErrorSink.map(_.toString)
    unsafeRun(sinkIteration(sink, 1).either.map(_ must_=== Left("Ouch")))
  }

  private def mapErrorInitError = {
    val sink = initErrorSink.mapError(_ => "Error")
    unsafeRun(sinkIteration(sink, 1).either.map(_ must_=== Left("Error")))
  }

  private def mapErrorStepError = {
    val sink = stepErrorSink.mapError(_ => "Error")
    unsafeRun(sinkIteration(sink, 1).either.map(_ must_=== Left("Error")))
  }

  private def mapErrorExtractError = {
    val sink = extractErrorSink.mapError(_ => "Error")
    unsafeRun(sinkIteration(sink, 1).either.map(_ must_=== Left("Error")))
  }

  private def mapMHappyPath = {
    val sink = ZSink.identity[Int].mapM[Any, Unit, String](n => UIO.succeed(n.toString))
    unsafeRun(sinkIteration(sink, 1).map(_ must_=== (("1", Chunk.empty))))
  }

  private def mapMInitError = {
    val sink = initErrorSink.mapM[Any, String, String](n => UIO.succeed(n.toString))
    unsafeRun(sinkIteration(sink, 1).either.map(_ must_=== Left("Ouch")))
  }

  private def mapMStepError = {
    val sink = stepErrorSink.mapM[Any, String, String](n => UIO.succeed(n.toString))
    unsafeRun(sinkIteration(sink, 1).either.map(_ must_=== Left("Ouch")))
  }

  private def mapMExtractError = {
    val sink = extractErrorSink.mapM[Any, String, String](n => UIO.succeed(n.toString))
    unsafeRun(sinkIteration(sink, 1).either.map(_ must_=== Left("Ouch")))
  }

  private def mapRemainderInitError = {
    val sink = initErrorSink.mapRemainder(_.toLong)
    unsafeRun(sinkIteration(sink, 1).either.map(_ must_=== Left("Ouch")))
  }

  private def mapRemainderStepError = {
    val sink = stepErrorSink.mapRemainder(_.toLong)
    unsafeRun(sinkIteration(sink, 1).either.map(_ must_=== Left("Ouch")))
  }

  private def mapRemainderExtractError = {
    val sink = extractErrorSink.mapRemainder(_.toLong)
    unsafeRun(sinkIteration(sink, 1).either.map(_ must_=== Left("Ouch")))
  }

  private def optionalHappyPath = {
    val sink = ZSink.identity[Int].optional
    unsafeRun(sinkIteration(sink, 1).map(_ must_=== ((Some(1), Chunk.empty))))
  }

  private def optionalInitError = {
    val sink = initErrorSink.optional
    val test = for {
      init   <- sink.initial
      result <- sink.extract(init)
    } yield result must_=== ((None, Chunk.empty))
    unsafeRun(test)
  }

  private def optionalStepError = {
    val s = new ZSink[Any, String, Nothing, Any, Nothing] {
      type State = Unit
      val initial                    = UIO.succeed(())
      def step(state: State, a: Any) = IO.fail("Ouch")
      def extract(state: State)      = IO.fail("Ouch")
      def cont(state: State)         = true
    }
    val sink = s.optional
    unsafeRun(sinkIteration(sink, 1).map(_ must_=== ((None, Chunk.single(1)))))
  }

  private def optionalExtractError = {
    val sink = extractErrorSink.optional
    unsafeRun(sinkIteration(sink, 1).map(_ must_=== ((None, Chunk.empty))))
  }

  private def optionalWithLeftover = {
    val sink = Sink.ignoreWhile[Int](_ < 0).optional
    unsafeRun(sinkIteration(sink, 1).map(_ must_=== ((Some(()), Chunk.single(1)))))
  }

  private def orElseLeft = {
    val sink = ZSink.identity[Int] orElse ZSink.fail("Ouch")
    unsafeRun(sinkIteration(sink, 1).map(_ must_=== ((Left(1), Chunk.empty))))
  }

  private def orElseRight = {
    val sink = ZSink.fail("Ouch") orElse ZSink.identity[Int]
    unsafeRun(sinkIteration(sink, 1).map(_ must_=== ((Right(1), Chunk.empty))))
  }

  private def orElseInitErrorLeft = {
    val sink = initErrorSink orElse ZSink.identity[Int]
    unsafeRun(sinkIteration(sink, 1).map(_ must_=== ((Right(1), Chunk.empty))))
  }

  private def orElseInitErrorRight = {
    val sink = ZSink.identity[Int] orElse initErrorSink
    unsafeRun(sinkIteration(sink, 1).map(_ must_=== ((Left(1), Chunk.empty))))
  }

  private def orElseInitErrorBoth = {
    val sink = initErrorSink orElse initErrorSink
    unsafeRun(sinkIteration(sink, 1).either.map(_ must_=== Left("Ouch")))
  }

  private def orElseStepErrorLeft = {
    val sink = stepErrorSink orElse ZSink.identity[Int]
    unsafeRun(sinkIteration(sink, 1).map(_ must_=== ((Right(1), Chunk.empty))))
  }

  private def orElseStepErrorRight = {
    val sink = ZSink.identity[Int] orElse stepErrorSink
    unsafeRun(sinkIteration(sink, 1).map(_ must_=== ((Left(1), Chunk.empty))))
  }

  private def orElseStepErrorBoth = {
    val sink = stepErrorSink orElse stepErrorSink
    unsafeRun(sinkIteration(sink, 1).either.map(_ must_=== Left("Ouch")))
  }

  private def orElseExtractErrorLeft = {
    val sink = extractErrorSink orElse ZSink.identity[Int]
    unsafeRun(sinkIteration(sink, 1).map(_ must_=== ((Right(1), Chunk.empty))))
  }

  private def orElseExtractErrorRight = {
    val sink = ZSink.identity[Int] orElse extractErrorSink
    unsafeRun(sinkIteration(sink, 1).map(_ must_=== ((Left(1), Chunk.empty))))
  }

  private def orElseExtractErrorBoth = {
    val sink = extractErrorSink orElse extractErrorSink
    unsafeRun(sinkIteration(sink, 1).either.map(_ must_=== Left("Ouch")))
  }

  private def orElseLeftShortRightLong = {
    val sink = ZSink.collectAllN[Int](2) orElse ZSink.collectAll[Int]
    val test = for {
      init   <- sink.initial
      step1  <- sink.step(init, 1)
      step2  <- sink.step(step1, 2)
      result <- sink.extract(step2)
    } yield result must_=== ((Left(List(1, 2)), Chunk.empty))
    unsafeRun(test)
  }

  private def orElseLeftLongRightShort = {
    val sink = ZSink.collectAll[Int] orElse ZSink.collectAllN[Int](2)
    val test = for {
      init   <- sink.initial
      step1  <- sink.step(init, 1)
      step2  <- sink.step(step1, 2)
      step3  <- sink.step(step2, 3)
      step4  <- sink.step(step3, 4)
      result <- sink.extract(step4)
    } yield result must_=== ((Left(List(1, 2, 3, 4)), Chunk.empty))
    unsafeRun(test)
  }

  private def orElseLeftLongFailRightShort = {
    val sink = (ZSink.collectAll[Int] <* ZSink.fail("Ouch")) orElse ZSink.collectAllN[Int](2)
    val test = for {
      init   <- sink.initial
      step1  <- sink.step(init, 1)
      step2  <- sink.step(step1, 2)
      step3  <- sink.step(step2, 3)
      step4  <- sink.step(step3, 4)
      step5  <- sink.step(step4, 5)
      result <- sink.extract(step5)
    } yield result must_=== ((Right(List(1, 2)), Chunk(3, 4, 5)))
    unsafeRun(test)
  }

  private def pull1 = unsafeRun {
    val stream = Stream.fromIterable(List(1))
    val sink   = Sink.pull1(IO.succeed(None: Option[Int]))((i: Int) => Sink.succeed[Int, Option[Int]](Some(i)))

    stream.run(sink).map(_ must_=== Some(1))
  }

  private def raceBothLeft = {
    val sink = ZSink.identity[Int] raceBoth ZSink.succeed[Int, String]("Hello")
    unsafeRun(sinkIteration(sink, 1).map(_ must_=== ((Left(1), Chunk.empty))))
  }

  private def raceBothInitErrorLeft = {
    val sink = initErrorSink raceBoth ZSink.identity[Int]
    unsafeRun(sinkIteration(sink, 1).map(_ must_=== ((Right(1), Chunk.empty))))
  }

  private def raceBothInitErrorRight = {
    val sink = ZSink.identity[Int] raceBoth initErrorSink
    unsafeRun(sinkIteration(sink, 1).map(_ must_=== ((Left(1), Chunk.empty))))
  }

  private def raceBothInitErrorBoth = {
    val sink = initErrorSink raceBoth initErrorSink
    unsafeRun(
      sinkIteration(sink, 1).foldCause(_.failures, _ => List.empty[String]).map(_ must_=== List("Ouch", "Ouch"))
    )
  }

  private def raceBothStepErrorLeft = {
    val sink = stepErrorSink raceBoth ZSink.identity[Int]
    unsafeRun(sinkIteration(sink, 1).map(_ must_=== ((Right(1), Chunk.empty))))
  }

  private def raceBothStepErrorRight = {
    val sink = ZSink.identity[Int] raceBoth stepErrorSink
    unsafeRun(sinkIteration(sink, 1).map(_ must_=== ((Left(1), Chunk.empty))))
  }

  private def raceBothStepErrorBoth = {
    val sink = stepErrorSink raceBoth stepErrorSink
    unsafeRun(
      sinkIteration(sink, 1).foldCause(_.failures, _ => List.empty[String]).map(_ must_=== List("Ouch", "Ouch"))
    )
  }

  private def raceBothExtractErrorLeft = {
    val sink = extractErrorSink raceBoth ZSink.identity[Int]
    unsafeRun(sinkIteration(sink, 1).map(_ must_=== ((Right(1), Chunk.empty))))
  }

  private def raceBothExtractErrorRight = {
    val sink = ZSink.identity[Int] raceBoth extractErrorSink
    unsafeRun(sinkIteration(sink, 1).map(_ must_=== ((Left(1), Chunk.empty))))
  }

  private def raceBothExtractErrorBoth = {
    val sink = extractErrorSink raceBoth extractErrorSink
    unsafeRun(
      sinkIteration(sink, 1).foldCause(_.failures, _ => List.empty[String]).map(_ must_=== List("Ouch", "Ouch"))
    )
  }

  private def raceBothLeftWins = {
    val sink = ZSink.collectAllN[Int](2) raceBoth ZSink.collectAll[Int]
    val test = for {
      init   <- sink.initial
      step1  <- sink.step(init, 1)
      step2  <- sink.step(step1, 2)
      result <- sink.extract(step2)
    } yield result must_=== ((Left(List(1, 2)), Chunk.empty))
    unsafeRun(test)
  }

  private def raceBothRightWins = {
    val sink = ZSink.collectAll[Int] raceBoth ZSink.collectAllN[Int](2)
    val test = for {
      init   <- sink.initial
      step1  <- sink.step(init, 1)
      step2  <- sink.step(step1, 2)
      result <- sink.extract(step2)
    } yield result must_=== ((Right(List(1, 2)), Chunk.empty))
    unsafeRun(test)
  }

  private def takeWhileHappyPath = {
    val sink = Sink.collectAll[Int].takeWhile(_ < 5)
    val test = for {
      init   <- sink.initial
      step1  <- sink.step(init, 1)
      step2  <- sink.step(step1, 2)
      step3  <- sink.step(step2, 3)
      step4  <- sink.step(step3, 4)
      step5  <- sink.step(step4, 5)
      cont   = sink.cont(step5)
      result <- sink.extract(step5)
    } yield (cont must_=== false) and (result must_=== ((List(1, 2, 3, 4), Chunk.single(5))))
    unsafeRun(test)
  }

  private def takeWhileFalsePredicate = {
    val sink = ZSink.identity[Int].takeWhile(_ > 5)
    unsafeRun(sinkIteration(sink, 1).either.map(_ must_=== Left(())))
  }

  private def takeWhileInitError = {
    val sink = initErrorSink.takeWhile(_ < 5)
    unsafeRun(sinkIteration(sink, 1).either.map(_ must_=== Left("Ouch")))
  }

  private def takeWhileStepError = {
    val sink = stepErrorSink.takeWhile(_ < 5)
    unsafeRun(sinkIteration(sink, 1).either.map(_ must_=== Left("Ouch")))
  }

  private def takeWhileExtractError = {
    val sink = extractErrorSink.takeWhile(_ < 5)
    unsafeRun(sinkIteration(sink, 1).either.map(_ must_=== Left("Ouch")))
  }

  private def untilOutputHappyPath = {
    val sink = ZSink.collectAllN[Int](3).untilOutput(_.sum > 3)
    val test = for {
      under <- sink.initial
                .flatMap(sink.stepChunk(_, Chunk(1, 2)).map(_._1))
                .flatMap(sink.extract)
      over <- sink.initial
               .flatMap(sink.stepChunk(_, Chunk(1, 2)).map(_._1))
               .flatMap(sink.stepChunk(_, Chunk(2, 2)).map(_._1))
               .flatMap(sink.extract)
    } yield (under must_=== (None -> Chunk.empty)) and (over must_=== (Some(List(1, 2, 2)) -> Chunk(2)))

    unsafeRun(test)
  }

  private def untilOutputFalsePredicate = {
    val sink = ZSink.identity[Int].untilOutput(_ < 0)
    unsafeRun(sinkIteration(sink, 1).map(_ must_=== ((None, Chunk.empty))))
  }

  private def untilOutputInitError = {
    val sink = initErrorSink.untilOutput(_ == 0)
    unsafeRun(sinkIteration(sink, 1).either.map(_ must_=== Left("Ouch")))
  }

  private def untilOutputStepError = {
    val sink = stepErrorSink.untilOutput(_ == 0)
    unsafeRun(sinkIteration(sink, 1).either.map(_ must_=== Left("Ouch")))
  }

  private def untilOutputExtractError = {
    val sink = extractErrorSink.untilOutput(_ == 0)
    unsafeRun(sinkIteration(sink, 1).either.map(_ must_=== Left("Ouch")))
  }

  private def zipHappyPath = {
    val sink = ZSink.identity[Int] <*> ZSink.succeed[Int, String]("Hello")
    unsafeRun(sinkIteration(sink, 1).map(_ must_=== (((1, "Hello"), Chunk.empty))))
  }

  private def zipInitErrorLeft = {
    val sink = initErrorSink <*> ZSink.identity[Int]
    unsafeRun(sinkIteration(sink, 1).either.map(_ must_=== Left("Ouch")))
  }

  private def zipInitErrorRight = {
    val sink = ZSink.identity[Int] <*> initErrorSink
    unsafeRun(sinkIteration(sink, 1).either.map(_ must_=== Left("Ouch")))
  }

  private def zipInitErrorBoth = {
    val sink = initErrorSink <*> initErrorSink
    unsafeRun(sinkIteration(sink, 1).either.map(_ must_=== Left("Ouch")))
  }

  private def zipStepErrorLeft = {
    val sink = stepErrorSink <*> ZSink.identity[Int]
    unsafeRun(sinkIteration(sink, 1).either.map(_ must_=== Left("Ouch")))
  }

  private def zipStepErrorRight = {
    val sink = ZSink.identity[Int] <*> stepErrorSink
    unsafeRun(sinkIteration(sink, 1).either.map(_ must_=== Left("Ouch")))
  }

  private def zipStepErrorBoth = {
    val sink = stepErrorSink <*> stepErrorSink
    unsafeRun(sinkIteration(sink, 1).either.map(_ must_=== Left("Ouch")))
  }

  private def zipExtractErrorLeft = {
    val sink = extractErrorSink <*> ZSink.identity[Int]
    unsafeRun(sinkIteration(sink, 1).either.map(_ must_=== Left("Ouch")))
  }

  private def zipExtractErrorRight = {
    val sink = ZSink.identity[Int] <*> extractErrorSink
    unsafeRun(sinkIteration(sink, 1).either.map(_ must_=== Left("Ouch")))
  }

  private def zipExtractErrorBoth = {
    val sink = extractErrorSink <*> extractErrorSink
    unsafeRun(sinkIteration(sink, 1).either.map(_ must_=== Left("Ouch")))
  }

  private def zipLeftHappyPath = {
    val sink = ZSink.identity[Int].zipLeft(ZSink.succeed[Int, String]("Hello"))
    unsafeRun(sinkIteration(sink, 1).map(_ must_=== ((1, Chunk.empty))))
  }

  private def zipRightHappyPath = {
    val sink = ZSink.identity[Int].zipRight(ZSink.succeed[Int, String]("Hello"))
    unsafeRun(sinkIteration(sink, 1).map(_ must_=== (("Hello", Chunk.empty))))
  }

  private def zipWithHappyPath = {
    val sink = ZSink.identity[Int].zipWith(ZSink.succeed[Int, String]("Hello"))((x, y) => x.toString + y.toString)
    unsafeRun(sinkIteration(sink, 1).map(_ must_=== (("1Hello", Chunk.empty))))
  }

  private object ZipParLaws {
    def coherence[A, B: Diffable, C: Diffable](
      s: Stream[String, A],
      sink1: ZSink[Any, String, A, A, B],
      sink2: ZSink[Any, String, A, A, C]
    ): MatchResult[Either[String, Any]] =
      unsafeRun {
        for {
          zb  <- s.run(sink1).either
          zc  <- s.run(sink2).either
          zbc <- s.run(sink1.zipPar(sink2)).either
        } yield {
          zbc match {
            case Left(e)       => (zb must beLeft(e)) or (zc must beLeft(e))
            case Right((b, c)) => (zb must beRight(b)) and (zc must beRight(c))
          }
        }
      }

    def swap[A, B: Diffable, C: Diffable](
      s: Stream[String, A],
      sink1: ZSink[Any, String, A, A, B],
      sink2: ZSink[Any, String, A, A, C]
    ) =
      unsafeRun {
        for {
          res     <- s.run(sink1.zipPar(sink2).zip(ZSink.collectAll[A])).either
          swapped <- s.run(sink2.zipPar(sink1).zip(ZSink.collectAll[A])).either
        } yield {
          swapped must_=== res.map {
            case ((b, c), rem) => ((c, b), rem)
          }
        }
      }

    def remainders[A, B: Diffable, C: Diffable](
      s: Stream[String, A],
      sink1: ZSink[Any, String, A, A, B],
      sink2: ZSink[Any, String, A, A, C]
    ): MatchResult[AnyVal] =
      unsafeRun {
        val maybeProp = for {
          rem1 <- s.run(sink1.zipRight(ZSink.collectAll[A]))
          rem2 <- s.run(sink2.zipRight(ZSink.collectAll[A]))
          rem  <- s.run(sink1.zipPar(sink2).zipRight(ZSink.collectAll[A]))
        } yield {
          val (longer, shorter) = if (rem1.length <= rem2.length) (rem2, rem1) else (rem1, rem2)
          longer must_=== rem
          rem.endsWith(shorter) must_=== true
        }
        //irrelevant if an error occurred
        maybeProp.catchAll(_ => UIO.succeed(1 must_=== 1))
      }

    def laws[A, B: Diffable, C: Diffable](
      s: Stream[String, A],
      sink1: ZSink[Any, String, A, A, B],
      sink2: ZSink[Any, String, A, A, C]
    ): MatchResult[Any] =
      coherence(s, sink1, sink2) and remainders(s, sink1, sink2) and swap(s, sink1, sink2)
  }

  private def zipParHappyPathBothDone = {
    val sink1 = ZSink.collectAllWhile[Int](_ < 5)
    val sink2 = ZSink.collectAllWhile[Int](_ < 3)
    ZipParLaws.laws(Stream(1, 2, 3, 4, 5, 6), sink1, sink2)
  }

  private def zipParHappyPathOneNonterm = {
    val sink1 = ZSink.collectAllWhile[Int](_ < 5)
    val sink2 = ZSink.collectAllWhile[Int](_ < 30)
    ZipParLaws.laws(Stream(1, 2, 3, 4, 5, 6), sink1, sink2)
  }

  private def zipParHappyPathBothNonterm = {
    val sink1 = ZSink.collectAllWhile[Int](_ < 50)
    val sink2 = ZSink.collectAllWhile[Int](_ < 30)
    ZipParLaws.laws(Stream(1, 2, 3, 4, 5, 6), sink1, sink2)
  }

  private def zipParErrorExtract = {
    val sink1 = ZSink.collectAllWhile[Int](_ < 5)
    ZipParLaws.laws(Stream(1, 2, 3, 4, 5, 6), sink1, extractErrorSink)
  }

  private def zipParErrorStep = {
    val sink1 = ZSink.collectAllWhile[Int](_ < 5)
    ZipParLaws.laws(Stream(1, 2, 3, 4, 5, 6), sink1, stepErrorSink)
  }

  private def zipParErrorInit = {
    val sink1 = ZSink.collectAllWhile[Int](_ < 5)
    ZipParLaws.laws(Stream(1, 2, 3, 4, 5, 6), sink1, initErrorSink)
  }

  private def zipParErrorBoth =
    ZipParLaws.laws(Stream(1, 2, 3, 4, 5, 6), stepErrorSink, initErrorSink)

  private def zipParRemainderWhenCompleteTogether = {
    val sink1 = sinkWithLeftover(2, 3, -42)
    val sink2 = sinkWithLeftover(2, 4, -42)
    ZipParLaws.laws(Stream(1, 2, 3, 4, 5, 6), sink1, sink2)
  }

  private def zipParRemainderWhenCompleteSeparately = {
    val sink1 = sinkWithLeftover(3, 1, -42)
    val sink2 = sinkWithLeftover(2, 4, -42)
    ZipParLaws.laws(Stream(1, 2, 3, 4, 5, 6), sink1, sink2)
  }

  private def foldLeft =
    prop { (s: Stream[String, Int], f: (String, Int) => String, z: String) =>
      unsafeRunSync(s.run(ZSink.foldLeft(z)(f))) must_=== unsafeRunSync(s.runCollect.map(_.foldLeft(z)(f)))
    }

  private def fold =
    prop { (s: Stream[String, Int], f: (String, Int) => String, z: String) =>
      unsafeRunSync(s.run(ZSink.foldLeft(z)(f))) must_=== unsafeRunSync(
        s.runCollect.map(_.foldLeft(z)(f))
      )
    }

  private def foldShortCircuits = {
    val empty: Stream[Nothing, Int]     = ZStream.empty
    val single: Stream[Nothing, Int]    = ZStream.succeed(1)
    val double: Stream[Nothing, Int]    = ZStream(1, 2)
    val failed: Stream[String, Nothing] = ZStream.fail("Ouch")

    def run[E](stream: Stream[E, Int]) = {
      var effects: List[Int] = Nil
      val sink = ZSink.fold[Any, Int, Int](0)(_ => true) { (_, a) =>
        effects ::= a
        (30, Chunk.empty)
      }

      val exit = unsafeRunSync(stream.run(sink))

      (exit, effects)
    }

    (run(empty) must_=== ((Exit.succeed(0), Nil))) &&
    (run(single) must_=== ((Exit.succeed(30), List(1)))) &&
    (run(double) must_=== ((Exit.succeed(30), List(2, 1)))) &&
    (run(failed) must_=== ((Exit.fail("Ouch"), Nil)))
  }

  private def foldM = {
    implicit val ioArb: Arbitrary[IO[String, String]] = Arbitrary(genSuccess[String, String])

    prop { (s: Stream[String, Int], f: (String, Int) => IO[String, String], z: IO[String, String]) =>
      val sinkResult = unsafeRunSync(z.flatMap(z => s.run(ZSink.foldLeftM(z)(f))))
      val foldResult = unsafeRunSync {
        s.fold(List[Int]())((acc, el) => el :: acc)
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
      val sink = ZSink.foldM[Any, E, Int, Int, Int](0)(_ => true) { (_, a) =>
        effects ::= a
        UIO.succeed((30, Chunk.empty))
      }

      val exit = unsafeRunSync(stream.run(sink))

      (exit, effects)
    }

    (run(empty) must_=== ((Exit.succeed(0), Nil))) &&
    (run(single) must_=== ((Exit.succeed(30), List(1)))) &&
    (run(double) must_=== ((Exit.succeed(30), List(2, 1)))) &&
    (run(failed) must_=== ((Exit.fail("Ouch"), Nil)))
  }

  private def collectAllWhile =
    prop { (s: Stream[String, String], f: String => Boolean) =>
      val sinkResult = unsafeRunSync(s.run(ZSink.collectAllWhile(f)))
      val listResult = unsafeRunSync(s.runCollect.map(_.takeWhile(f)))

      listResult.succeeded ==> (sinkResult must_=== listResult)
    }

  private def collectAllN = unsafeRun {
    Stream[Int](1, 2, 3)
      .run(Sink.collectAllN[Int](2))
      .map(_ must_=== List(1, 2))
  }

  private def collectAllToSet = unsafeRun {
    Stream[Int](1, 2, 3, 3, 4)
      .run(Sink.collectAllToSet[Int])
      .map(_ must_=== Set(1, 2, 3, 4))
  }

  private def collectAllToSetN = unsafeRun {
    Stream[Int](1, 2, 1, 2, 3, 3, 4)
      .run(Sink.collectAllToSetN[Int](3))
      .map(_ must_=== Set(1, 2, 3))
  }

  private def collectAllToMap = unsafeRun {
    Stream[Int](1, 2, 3)
      .run(Sink.collectAllToMap[Int, Int](value => value))
      .map(_ must_=== Map[Int, Int](1 -> 1, 2 -> 2, 3 -> 3))
  }

  private def collectAllToMapN = unsafeRun {
    Stream[Int](1, 2, 3, 4, 5, 6)
      .run(Sink.collectAllToMapN[Int, Int](2)(value => value % 2))
      .map(_ must_=== Map[Int, Int](1 -> 1, 0 -> 2))
  }

  private def foldWeighted = unsafeRun {
    Stream[Long](1, 5, 2, 3)
      .transduce(Sink.foldWeighted[Long, List[Long]](List())(_ * 2, 12)((acc, el) => el :: acc).map(_.reverse))
      .runCollect
      .map(_ must_=== List(List(1, 5), List(2, 3)))
  }

  private def foldWeightedDecompose = unsafeRun {
    Stream(1, 5, 1)
      .transduce(
        Sink
          .foldWeightedDecompose(List[Int]())((i: Int) => i.toLong, 4, (i: Int) => Chunk(i - 1, 1)) { (acc, el) =>
            el :: acc
          }
          .map(_.reverse)
      )
      .runCollect
      .map(_ must_=== List(List(1), List(4), List(1, 1)))
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

  private def foldWeightedDecomposeM = unsafeRun {
    Stream(1, 5, 1)
      .transduce(
        Sink
          .foldWeightedDecomposeM(List[Int]())(
            (i: Int) => UIO.succeed(i.toLong),
            4,
            (i: Int) => UIO.succeed(Chunk(i - 1, 1))
          ) { (acc, el) =>
            UIO.succeed(el :: acc)
          }
          .map(_.reverse)
      )
      .runCollect
      .map(_ must_=== List(List(1), List(4), List(1, 1)))
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

  private def fromFunction = unsafeRun {
    Stream(1, 2, 3, 4, 5)
      .transduce(Sink.fromFunction[Int, String](_.toString))
      .runCollect
      .map(_ must_=== List("1", "2", "3", "4", "5"))
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
        .foldM((ParserState.Start: ParserState, List.empty[Int], true))(_._3) { (s, a: Char) =>
          s match {
            case (ParserState.Start, acc, _) =>
              a match {
                case a if a.isWhitespace => UIO.succeed(((ParserState.Start, acc, true), Chunk.empty))
                case '['                 => UIO.succeed(((ParserState.Element(""), acc, true), Chunk.empty))
                case _                   => IO.fail("Expected '['")
              }

            case (ParserState.Element(el), acc, _) =>
              a match {
                case a if a.isDigit => UIO.succeed(((ParserState.Element(el + a), acc, true), Chunk.empty))
                case ','            => UIO.succeed(((ParserState.Element(""), acc :+ el.toInt, true), Chunk.empty))
                case ']'            => UIO.succeed(((ParserState.Done, acc :+ el.toInt, false), Chunk.empty))
                case _              => IO.fail("Expected a digit or ,")
              }

            case (ParserState.Done, acc, _) => UIO.succeed(((ParserState.Done, acc, false), Chunk.empty))
          }
        }
        .map(_._2)
        .chunked

    val src1         = ZStreamChunk.succeed(Chunk.fromArray(Array('[', '1', '2')))
    val src2         = ZStreamChunk.succeed(Chunk.fromArray(Array('3', ',', '4', ']')))
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
    val numbers = (number <*> (comma *> number).collectAllWhile(_ != ']'))
      .map(tp => tp._1 :: tp._2)

    val elements = numbers <* brace

    lazy val start: ZSink[Any, String, Char, Char, List[Int]] =
      ZSink.pull1(IO.fail("Input was empty")) {
        case a if a.isWhitespace => start
        case '['                 => elements
        case _                   => ZSink.fail("Expected '['")
      }

    val src1         = ZStreamChunk.succeed(Chunk.fromArray(Array('[', '1', '2')))
    val src2         = ZStreamChunk.succeed(Chunk.fromArray(Array('3', ',', '4', ']')))
    val partialParse = unsafeRunSync(src1.run(start.chunked))
    val fullParse    = unsafeRunSync((src1 ++ src2).run(start.chunked))

    (partialParse must_=== (Exit.fail("Expected closing brace; instead: None"))) and
      (fullParse must_=== (Exit.Success(List(123, 4))))
  }

  private def fromOutputStream = unsafeRun {
    import java.io.ByteArrayOutputStream

    val output = new ByteArrayOutputStream()
    val data   = "0123456789"
    val stream = Stream(Chunk.fromArray(data.take(5).getBytes), Chunk.fromArray(data.drop(5).getBytes))

    stream.run(ZSink.fromOutputStream(output)) map { bytesWritten =>
      (bytesWritten must_=== 10) and (new String(output.toByteArray, "UTF-8") must_=== data)
    }
  }

  private def splitLines =
    prop { (lines: List[String]) =>
      val data = lines.mkString("\n")

      unsafeRun {
        for {
          initial            <- ZSink.splitLines.initial
          middle             <- ZSink.splitLines.step(initial, data)
          res                <- ZSink.splitLines.extract(middle)
          (result, leftover) = res
        } yield ((result ++ leftover).toArray[String].mkString("\n") must_=== lines.mkString("\n"))
      }
    }.setGen(
      Gen
        .listOf(Gen.asciiStr.map(_.filterNot(c => c == '\n' || c == '\r')))
        .map(l => if (l.nonEmpty && l.last == "") l ++ List("a") else l)
    )

  private def splitLinesLeftovers = unsafeRun {
    for {
      initial            <- ZSink.splitLines.initial
      middle             <- ZSink.splitLines.step(initial, "abc\nbc")
      res                <- ZSink.splitLines.extract(middle)
      (result, leftover) = res
    } yield (result.toArray[String].mkString("\n") must_=== "abc") and (leftover
      .toArray[String]
      .mkString must_=== "bc")
  }

  private def splitLinesTransduce = unsafeRun {
    Stream("abc", "\n", "bc", "\n", "bcd", "bcd")
      .transduce(ZSink.splitLines)
      .runCollect
      .map {
        _ must_=== List(Chunk("abc"), Chunk("bc"), Chunk("bcdbcd"))
      }
  }

  private def splitLinesEdgecase = unsafeRun {
    Stream("\n")
      .transduce(ZSink.splitLines)
      .mapConcat(identity)
      .runCollect
      .map {
        _ must_=== List("")
      }
  }

  private def splitLinesNoNewlines = unsafeRun {
    Stream("abc", "abc", "abc")
      .transduce(ZSink.splitLines)
      .mapConcat(identity)
      .runCollect
      .map {
        _ must_=== List("abcabcabc")
      }
  }

  private def splitLinesBoundary = unsafeRun {
    Stream("abc\r", "\nabc")
      .transduce(ZSink.splitLines)
      .mapConcat(identity)
      .runCollect
      .map {
        _ must_=== List("abc", "abc")
      }
  }

  private def throttleEnforce = {

    def sinkTest(sink: ZSink[Clock, Nothing, Nothing, Int, Option[Int]]) =
      for {
        init1 <- sink.initial
        step1 <- sink.step(init1, 1)
        res1  <- sink.extract(step1).map(_._1)
        init2 <- sink.initial
        _     <- TestClock.adjust(23.milliseconds)
        step2 <- sink.step(init2, 2)
        res2  <- sink.extract(step2).map(_._1)
        init3 <- sink.initial
        step3 <- sink.step(init3, 3)
        res3  <- sink.extract(step3).map(_._1)
        init4 <- sink.initial
        step4 <- sink.step(init4, 4)
        res4  <- sink.extract(step4).map(_._1)
        _     <- TestClock.adjust(11.milliseconds)
        init5 <- sink.initial
        step5 <- sink.step(init5, 5)
        res5  <- sink.extract(step5).map(_._1)
      } yield (List(res1, res2, res3, res4, res5) must_=== List(Some(1), Some(2), None, None, Some(5)))

    unsafeRun {
      for {
        clock <- TestClock.make(TestClock.DefaultData)
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
        step1 <- sink.step(init1, 1)
        res1  <- sink.extract(step1).map(_._1)
        init2 <- sink.initial
        _     <- TestClock.adjust(23.milliseconds)
        step2 <- sink.step(init2, 2)
        res2  <- sink.extract(step2).map(_._1)
        init3 <- sink.initial
        step3 <- sink.step(init3, 3)
        res3  <- sink.extract(step3).map(_._1)
        init4 <- sink.initial
        step4 <- sink.step(init4, 4)
        res4  <- sink.extract(step4).map(_._1)
        _     <- TestClock.adjust(11.milliseconds)
        init5 <- sink.initial
        step5 <- sink.step(init5, 5)
        res5  <- sink.extract(step5).map(_._1)
      } yield (List(res1, res2, res3, res4, res5) must_=== List(Some(1), Some(2), Some(3), None, Some(5)))

    unsafeRun {
      for {
        clock <- TestClock.make(TestClock.DefaultData)
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
        init1 <- sink.initial
        step1 <- sink.step(init1, 1)
        res1  <- sink.extract(step1).map(_._1)
        init2 <- sink.initial
        step2 <- sink.step(init2, 2)
        res2  <- sink.extract(step2).map(_._1)
        init3 <- sink.initial
        _     <- clock.sleep(4.seconds)
        step3 <- sink.step(init3, 3)
        res3  <- sink.extract(step3).map(_._1)
      } yield List(res1, res2, res3) must_=== List(1, 2, 3)

    unsafeRun {
      for {
        clock <- TestClock.make(TestClock.DefaultData)
        fiber <- ZSink
                  .throttleShape[Int](1, 1.second)(_.toLong)
                  .use(sinkTest)
                  .provide(clock)
                  .fork
        _    <- clock.clock.adjust(8.seconds)
        test <- fiber.join
      } yield test
    }
  }

  private def throttleShapeInfiniteBandwidth = {

    def sinkTest(sink: ZSink[Clock, Nothing, Nothing, Int, Int]) =
      for {
        init1   <- sink.initial
        step1   <- sink.step(init1, 1)
        res1    <- sink.extract(step1).map(_._1)
        init2   <- sink.initial
        step2   <- sink.step(init2, 2)
        res2    <- sink.extract(step2).map(_._1)
        elapsed <- clock.currentTime(TimeUnit.SECONDS)
      } yield (elapsed must_=== 0) and (List(res1, res2) must_=== List(1, 2))

    unsafeRun {
      for {
        clock <- TestClock.make(TestClock.DefaultData)
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
        init1 <- sink.initial
        step1 <- sink.step(init1, 1)
        res1  <- sink.extract(step1).map(_._1)
        init2 <- sink.initial
        _     <- TestClock.adjust(2.seconds)
        step2 <- sink.step(init2, 2)
        res2  <- sink.extract(step2).map(_._1)
        init3 <- sink.initial
        _     <- TestClock.adjust(4.seconds)
        _     <- clock.sleep(4.seconds)
        step3 <- sink.step(init3, 3)
        res3  <- sink.extract(step3).map(_._1)
      } yield List(res1, res2, res3) must_=== List(1, 2, 3)

    unsafeRun {
      for {
        clock <- TestClock.make(TestClock.DefaultData)
        fiber <- ZSink
                  .throttleShape[Int](1, 1.second, 2)(_.toLong)
                  .use(sinkTest)
                  .provide(clock)
                  .fork
        test <- fiber.join
      } yield test
    }
  }

  private def utf8Decode = prop { (s: String) =>
    unsafeRun {
      Stream
        .fromIterable(s.getBytes("UTF-8"))
        .transduce(ZSink.utf8Decode())
        .runCollect
        .map(_.mkString must_=== s)
    }
  }

  private def utf8DecodeChunk = prop { (s: String) =>
    unsafeRun {
      Stream(Chunk.fromArray(s.getBytes("UTF-8")))
        .transduce(ZSink.utf8DecodeChunk)
        .runCollect
        .map(_.mkString must_=== s)
    }
  }

  private def utf8DecodeChunkIncomplete1 = unsafeRun {
    for {
      init        <- ZSink.utf8DecodeChunk.initial
      state1      <- ZSink.utf8DecodeChunk.step(init, Chunk(0xC2.toByte))
      state2      <- ZSink.utf8DecodeChunk.step(state1, Chunk(0xA2.toByte))
      result      <- ZSink.utf8DecodeChunk.extract(state2)
      (string, _) = result
    } yield (ZSink.utf8DecodeChunk.cont(state1) must_=== true) and
      (ZSink.utf8DecodeChunk.cont(state2) must_=== false) and
      (string.getBytes("UTF-8") must_=== Array(0xC2.toByte, 0xA2.toByte))
  }

  private def utf8DecodeChunkIncomplete2 = unsafeRun {
    for {
      init        <- ZSink.utf8DecodeChunk.initial
      state1      <- ZSink.utf8DecodeChunk.step(init, Chunk(0xE0.toByte, 0xA4.toByte))
      state2      <- ZSink.utf8DecodeChunk.step(state1, Chunk(0xB9.toByte))
      result      <- ZSink.utf8DecodeChunk.extract(state2)
      (string, _) = result
    } yield (ZSink.utf8DecodeChunk.cont(state1) must_=== true) and
      (ZSink.utf8DecodeChunk.cont(state2) must_=== false) and
      (string.getBytes("UTF-8") must_=== Array(0xE0.toByte, 0xA4.toByte, 0xB9.toByte))
  }

  private def utf8DecodeChunkIncomplete3 = unsafeRun {
    for {
      init        <- ZSink.utf8DecodeChunk.initial
      state1      <- ZSink.utf8DecodeChunk.step(init, Chunk(0xF0.toByte, 0x90.toByte, 0x8D.toByte))
      state2      <- ZSink.utf8DecodeChunk.step(state1, Chunk(0x88.toByte))
      result      <- ZSink.utf8DecodeChunk.extract(state2)
      (string, _) = result
    } yield (ZSink.utf8DecodeChunk.cont(state1) must_=== true) and
      (ZSink.utf8DecodeChunk.cont(state2) must_=== false) and
      (string.getBytes("UTF-8") must_=== Array(0xF0.toByte, 0x90.toByte, 0x8D.toByte, 0x88.toByte))
  }

  private def utf8DecodeChunkWithLeftover = unsafeRun {
    for {
      init <- ZSink.utf8DecodeChunk.initial
      state1 <- ZSink.utf8DecodeChunk
                 .step(init, Chunk(0xF0.toByte, 0x90.toByte, 0x8D.toByte, 0x88.toByte, 0xF0.toByte, 0x90.toByte))
    } yield (ZSink.utf8DecodeChunk.cont(state1) must_=== false) and
      (unsafeRun(ZSink.utf8DecodeChunk.extract(state1).map(_._2)).flatMap(identity).toArray[Byte] must_=== Array(
        0xF0.toByte,
        0x90.toByte
      ))
  }
}
