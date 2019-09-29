package zio.stream

import com.github.ghik.silencer.silent
import org.scalacheck.{ Arbitrary, Gen }
import org.specs2.ScalaCheck
import zio.ZQueueSpecUtil.waitForSize
import zio._
import zio.duration._

import scala.{ Stream => _ }

class StreamSpec(implicit ee: org.specs2.concurrent.ExecutionEnv) extends TestRuntime with GenIO with ScalaCheck {

  import ArbitraryChunk._
  import ArbitraryStream._
  import Exit.{ Cause => _, _ }
  import zio.Cause

  def is = "StreamSpec".title ^ s2"""
  Stream.aggregate
    aggregate                            $aggregate
    error propagation                    $aggregateErrorPropagation1
    error propagation                    $aggregateErrorPropagation2
    interruption propagation             $aggregateInterruptionPropagation
    interruption propagation             $aggregateInterruptionPropagation2
    leftover handling                    $aggregateLeftoverHandling

  Stream.aggregateWithinEither
    aggregateWithinEither                 $aggregateWithinEither
    error propagation                     $aggregateWithinEitherErrorPropagation1
    error propagation                     $aggregateWithinEitherErrorPropagation2
    interruption propagation              $aggregateWithinEitherInterruptionPropagation
    interruption propagation              $aggregateWithinEitherInterruptionPropagation2
    aggregateWithinEitherLeftoverHandling $aggregateWithinEitherLeftoverHandling

  Stream.aggregateWithin
    aggregateWithin                      $aggregateWithin

  Stream.bracket
    bracket                              $bracket
    bracket short circuits               $bracketShortCircuits
    no acquisition when short circuiting $bracketNoAcquisition
    releases when there are defects      $bracketWithDefects

  Stream.broadcast
    Values       $broadcastValues
    Errors       $broadcastErrors
    BackPressure $broadcastBackPressure
    Unsubscribe  $broadcastUnsubscribe

  Stream.buffer
    buffer the Stream                      $bufferStream
    buffer the Stream with Error           $bufferStreamError
    fast producer progress independently   $bufferFastProducerSlowConsumer

  Stream.catchAllCause
    recovery from errors                 $catchAllCauseErrors
    recovery from defects                $catchAllCauseDefects
    happy path                           $catchAllCauseHappyPath
    executes finalizers                  $catchAllCauseFinalizers
    failures on the scope                $catchAllCauseScopeErrors

  Stream.collect            $collect
  Stream.collectWhile
    collectWhile                $collectWhile
    collectWhile short circuits $collectWhileShortCircuits

  Stream.concat
    concat                  $concat
    finalizer order         $concatFinalizerOrder

  Stream.drain              $drain

  Stream.dropUntil
    dropUntil         $dropUntil

  Stream.dropWhile
    dropWhile         $dropWhile
    short circuits    $dropWhileShortCircuiting

  Stream.effectAsync
    effectAsync                 $effectAsync

  Stream.effectAsyncMaybe
    effectAsyncMaybe signal end stream $effectAsyncMaybeSignalEndStream
    effectAsyncMaybe Some              $effectAsyncMaybeSome
    effectAsyncMaybe None              $effectAsyncMaybeNone

  Stream.effectAsyncM
    effectAsyncM                   $effectAsyncM
    effectAsyncM signal end stream $effectAsyncMSignalEndStream

  Stream.effectAsyncInterrupt
    effectAsyncInterrupt Left              $effectAsyncInterruptLeft
    effectAsyncInterrupt Right             $effectAsyncInterruptRight
    effectAsyncInterrupt signal end stream $effectAsyncInterruptSignalEndStream

  Stream.ensuring      $ensuring
  Stream.ensuringFirst $ensuringFirst
  Stream.finalizer     $finalizer
  Stream.filter        $filter
  Stream.filterM       $filterM

  Stream.flatMap
    deep flatMap stack safety $flatMapStackSafety
    left identity             $flatMapLeftIdentity
    right identity            $flatMapRightIdentity
    associativity             $flatMapAssociativity
    inner finalizers          $flatMapInnerFinalizers
    finalizer ordering        $flatMapFinalizerOrdering

  Stream.flatMapPar/flattenPar/mergeAll
    guarantee ordering                 $flatMapParGuaranteeOrdering
    consistent with flatMap            $flatMapParConsistency
    short circuiting                   $flatMapParShortCircuiting
    interruption propagation           $flatMapParInterruptionPropagation
    inner errors interrupt all fibers  $flatMapParInnerErrorsInterruptAllFibers
    outer errors interrupt all fibers  $flatMapParOuterErrorsInterruptAllFibers
    inner defects interrupt all fibers $flatMapParInnerDefectsInterruptAllFibers
    outer defects interrupt all fibers $flatMapParOuterDefectsInterruptAllFibers
    finalizer ordering                 $flatMapParFinalizerOrdering

  Stream.flatMapParSwitch
    guarantee ordering no parallelism   $flatMapParSwitchGuaranteeOrderingNoParallelism
    guarantee ordering with parallelism $flatMapParSwitchGuaranteeOrderingWithParallelism
    short circuiting                    $flatMapParSwitchShortCircuiting
    interruption propagation            $flatMapParSwitchInterruptionPropagation
    inner errors interrupt all fibers   $flatMapParSwitchInnerErrorsInterruptAllFibers
    outer errors interrupt all fibers   $flatMapParSwitchOuterErrorsInterruptAllFibers
    inner defects interrupt all fibers  $flatMapParSwitchInnerDefectsInterruptAllFibers
    outer defects interrupt all fibers  $flatMapParSwitchOuterDefectsInterruptAllFibers
    finalizer ordering                  $flatMapParSwitchFinalizerOrdering

  Stream.foreach/foreachWhile
    foreach                     $foreach
    foreachWhile                $foreachWhile
    foreachWhile short circuits $foreachWhileShortCircuits

  Stream.forever            $forever
  Stream.fromChunk          $fromChunk
  Stream.fromInputStream    $fromInputStream
  Stream.fromIterable       $fromIterable
  Stream.fromIterator       $fromIterator
  Stream.fromQueue          $fromQueue

  Stream.groupBy
    values                                                $groupByValues
    first                                                 $groupByFirst
    filter                                                $groupByFilter
    outer errors                                          $groupByErrorsOuter

  Stream interleaving
    interleave              $interleave
    interleaveWith          $interleaveWith

  Stream.map                $map
  Stream.mapAccum           $mapAccum
  Stream.mapAccumM          $mapAccumM
  Stream.mapConcat          $mapConcat
  Stream.mapM               $mapM

  Stream.repeatEffect       $repeatEffect
  Stream.repeatEffectWith   $repeatEffectWith

  Stream.mapMPar
    foreachParN equivalence  $mapMPar
    order when n = 1         $mapMParOrder
    interruption propagation $mapMParInterruptionPropagation
    guarantee ordering       $mapMParGuaranteeOrdering

  Stream merging
    merge                         $merge
    mergeEither                   $mergeEither
    mergeWith                     $mergeWith
    mergeWith short circuit       $mergeWithShortCircuit
    mergeWith prioritizes failure $mergeWithPrioritizesFailure

  Stream.paginate            $paginate

  Stream.partitionEither
    values        $partitionEitherValues
    errors        $partitionEitherErrors
    backpressure  $partitionEitherBackPressure

  Stream.peel               $peel
  Stream.range              $range

  Stream.repeat
    repeat                  $repeat
    short circuits          $repeatShortCircuits

  Stream.repeatEither
    emits schedule output   $repeatEither
    short circuits          $repeatEitherShortCircuits

  Stream.schedule
    scheduleElementsWith          $scheduleElementsWith
    scheduleElementsEither        $scheduleElementsEither
    scheduleWith                  $scheduleWith
    scheduleEither                $scheduleEither
    repeated and spaced           $repeatedAndSpaced
    short circuits in schedule    $spacedShortCircuitsWhileInSchedule
    short circuits after schedule $spacedShortCircuitsAfterScheduleFinished

  Stream.take
    take                     $take
    take short circuits      $takeShortCircuits
    take(0) short circuits   $take0ShortCircuitsStreamNever
    take(1) short circuits   $take1ShortCircuitsStreamNever
    takeUntil                $takeUntil
    takeWhile                $takeWhile
    takeWhile short circuits $takeWhileShortCircuits

  Stream.tap                $tap

  Stream.timeout
    succeed                 $timeoutSucceed
    should interrupt stream $timeoutInterrupt

  Stream.throttleEnforce
    free elements                   $throttleEnforceFreeElements
    no bandwidth                    $throttleEnforceNoBandwidth

  Stream.throttleShape
    free elements                 $throttleShapeFreeElements

  Stream.toQueue            $toQueue

  Stream.transduce
    transduce                            $transduce
    no remainder                         $transduceNoRemainder
    with remainder                       $transduceWithRemainder
    with a sink that always signals more $transduceSinkMore
    managed                              $transduceManaged
    propagate managed error              $transduceManagedError

  Stream.unfold             $unfold
  Stream.unfoldM            $unfoldM

  Stream.unNone             $unNone

  Stream.unTake
    unTake happy path       $unTake
    unTake with error       $unTakeError

  Stream zipping
    zipWith                     $zipWith
    zipWithIndex                $zipWithIndex
    zipWith ignore RHS          $zipWithIgnoreRhs
    zipWith prioritizes failure $zipWithPrioritizesFailure
    zipWithLatest               $zipWithLatest
  
  Stream.toInputStream      $toInputStream
  """

  def aggregate = unsafeRun {
    Stream(1, 1, 1, 1)
      .aggregate(ZSink.foldUntil(List[Int](), 3)((acc, el: Int) => el :: acc).map(_.reverse))
      .runCollect
      .map { result =>
        (result.flatten must_=== List(1, 1, 1, 1)) and
          (result.forall(_.length <= 3) must_=== true)
      }
  }

  def aggregateErrorPropagation1 =
    unsafeRun {
      val e = new RuntimeException("Boom")
      Stream(1, 1, 1, 1)
        .aggregate(ZSink.die(e))
        .runCollect
        .run
        .map(_ must_=== Exit.Failure(Cause.Die(e)))
    }

  def aggregateErrorPropagation2 = unsafeRun {
    val e = new RuntimeException("Boom")
    val sink = Sink.foldM[Nothing, Int, Int, List[Int]](List[Int]())(_ => true) { (_, _) =>
      ZIO.die(e)
    }

    Stream(1, 1)
      .aggregate(sink)
      .runCollect
      .run
      .map(_ must_=== Exit.Failure(Cause.Die(e)))
  }

  def aggregateInterruptionPropagation = unsafeRun {
    for {
      latch     <- Promise.make[Nothing, Unit]
      cancelled <- Ref.make(false)
      sink = Sink.foldM(List[Int]())(_ => true) { (acc, el: Int) =>
        if (el == 1) UIO.succeed((el :: acc, Chunk[Int]()))
        else
          (latch.succeed(()) *> UIO.never)
            .onInterrupt(cancelled.set(true))
      }
      fiber  <- Stream(1, 1, 2).aggregate(sink).runCollect.untraced.fork
      _      <- latch.await
      _      <- fiber.interrupt
      result <- cancelled.get
    } yield result must_=== true
  }

  def aggregateInterruptionPropagation2 = unsafeRun {
    for {
      latch     <- Promise.make[Nothing, Unit]
      cancelled <- Ref.make(false)
      sink = Sink.fromEffect {
        (latch.succeed(()) *> UIO.never)
          .onInterrupt(cancelled.set(true))
      }
      fiber  <- Stream(1, 1, 2).aggregate(sink).runCollect.untraced.fork
      _      <- latch.await
      _      <- fiber.interrupt
      result <- cancelled.get
    } yield result must_=== true
  }

  private def aggregateLeftoverHandling = unsafeRun {
    val data = List(1, 2, 2, 3, 2, 3)
    Stream(data: _*)
      .aggregate(
        Sink
          .foldWeighted(List[Int]())((i: Int) => i.toLong, 4) { (acc, el) =>
            el :: acc
          }
          .map(_.reverse)
      )
      .runCollect
      .map { result =>
        result.flatten must_=== data
      }
  }

  def aggregateWithinEither = unsafeRun {
    for {
      result <- Stream(1, 1, 1, 1, 2)
                 .aggregateWithinEither(
                   Sink
                     .fold((List[Int](), true))(_._2) { (acc, el: Int) =>
                       if (el == 1) ((el :: acc._1, true), Chunk.empty)
                       else if (el == 2 && acc._1.isEmpty) ((el :: acc._1, false), Chunk.empty)
                       else ((acc._1, false), Chunk.single(el))
                     }
                     .map(_._1),
                   ZSchedule.spaced(30.minutes)
                 )
                 .runCollect
    } yield result must_=== List(Right(List(1, 1, 1, 1)), Right(List(2)))
  }

  private def aggregateWithinEitherErrorPropagation1 =
    unsafeRun {
      val e = new RuntimeException("Boom")
      Stream(1, 1, 1, 1)
        .aggregateWithinEither(ZSink.die(e), Schedule.spaced(30.minutes))
        .runCollect
        .run
        .map(_ must_=== Exit.Failure(Cause.Die(e)))
    }

  private def aggregateWithinEitherErrorPropagation2 = unsafeRun {
    val e = new RuntimeException("Boom")
    val sink = Sink.foldM[Nothing, Int, Int, List[Int]](List[Int]())(_ => true) { (_, _) =>
      ZIO.die(e)
    }

    Stream(1, 1)
      .aggregateWithinEither(sink, Schedule.spaced(30.minutes))
      .runCollect
      .run
      .map(_ must_=== Exit.Failure(Cause.Die(e)))
  }

  private def aggregateWithinEitherInterruptionPropagation = unsafeRun {
    for {
      latch     <- Promise.make[Nothing, Unit]
      cancelled <- Ref.make(false)
      sink = Sink.foldM(List[Int]())(_ => true) { (acc, el: Int) =>
        if (el == 1) UIO.succeed((el :: acc, Chunk[Int]()))
        else
          (latch.succeed(()) *> UIO.never)
            .onInterrupt(cancelled.set(true))
      }
      fiber  <- Stream(1, 1, 2).aggregateWithinEither(sink, Schedule.spaced(30.minutes)).runCollect.untraced.fork
      _      <- latch.await
      _      <- fiber.interrupt
      result <- cancelled.get
    } yield result must_=== true
  }

  private def aggregateWithinEitherInterruptionPropagation2 = unsafeRun {
    for {
      latch     <- Promise.make[Nothing, Unit]
      cancelled <- Ref.make(false)
      sink = Sink.fromEffect {
        (latch.succeed(()) *> UIO.never)
          .onInterrupt(cancelled.set(true))
      }
      fiber  <- Stream(1, 1, 2).aggregateWithinEither(sink, Schedule.spaced(30.minutes)).runCollect.untraced.fork
      _      <- latch.await
      _      <- fiber.interrupt
      result <- cancelled.get
    } yield result must_=== true
  }

  private def aggregateWithinEitherLeftoverHandling = unsafeRun {
    val data = List(1, 2, 2, 3, 2, 3)
    Stream(data: _*)
      .aggregateWithinEither(
        Sink
          .foldWeighted(List[Int]())((i: Int) => i.toLong, 4) { (acc, el) =>
            el :: acc
          }
          .map(_.reverse),
        Schedule.spaced(100.millis)
      )
      .collect {
        case Right(v) => v
      }
      .runCollect
      .map { result =>
        result.flatten must_=== data
      }
  }

  def aggregateWithin = unsafeRun {
    for {
      result <- Stream(1, 1, 1, 1, 2)
                 .aggregateWithin(
                   Sink
                     .fold((List[Int](), true))(_._2) { (acc, el: Int) =>
                       if (el == 1) ((el :: acc._1, true), Chunk.empty)
                       else if (el == 2 && acc._1.isEmpty) ((el :: acc._1, false), Chunk.empty)
                       else ((acc._1, false), Chunk.single(el))
                     }
                     .map(_._1),
                   ZSchedule.spaced(30.minutes)
                 )
                 .runCollect
    } yield result must_=== List(List(1, 1, 1, 1), List(2))
  }

  private def bracket =
    unsafeRun(
      for {
        done           <- Ref.make(false)
        iteratorStream = Stream.bracket(UIO(0 to 2))(_ => done.set(true)).flatMap(Stream.fromIterable)
        result         <- iteratorStream.run(Sink.collectAll[Int])
        released       <- done.get
      } yield (result must_=== List(0, 1, 2)) and (released must_=== true)
    )

  private def bracketShortCircuits =
    unsafeRun(
      for {
        done <- Ref.make(false)
        iteratorStream = Stream
          .bracket(UIO(0 to 3))(_ => done.set(true))
          .flatMap(Stream.fromIterable)
          .take(2)
        result   <- iteratorStream.run(Sink.collectAll[Int])
        released <- done.get
      } yield (result must_=== List(0, 1)) and (released must_=== true)
    )

  private def bracketNoAcquisition =
    unsafeRun(
      for {
        acquired       <- Ref.make(false)
        iteratorStream = (Stream(1) ++ Stream.bracket(acquired.set(true))(_ => UIO.unit)).take(0)
        _              <- iteratorStream.run(Sink.drain)
        result         <- acquired.get
      } yield result must_=== false
    )

  private def bracketWithDefects = unsafeRun {
    for {
      ref <- Ref.make(false)
      _ <- Stream
            .bracket(ZIO.unit)(_ => ref.set(true))
            .flatMap(_ => Stream.fromEffect(ZIO.dieMessage("boom")))
            .run(Sink.drain)
            .run
      released <- ref.get
    } yield released must_=== true
  }

  private def broadcastValues =
    unsafeRun {
      Stream.range(0, 5).broadcast(2, 12).use {
        case s1 :: s2 :: Nil =>
          for {
            out1     <- s1.runCollect
            out2     <- s2.runCollect
            expected = List(0, 1, 2, 3, 4, 5)
          } yield (out1 must_=== expected) && (out2 must_=== expected)
        case _ =>
          ZIO.fail("Wrong number of streams produced")
      }
    }

  private def broadcastErrors =
    unsafeRun {
      (Stream.range(0, 1) ++ Stream.fail("Boom")).broadcast(2, 12).use {
        case s1 :: s2 :: Nil =>
          for {
            out1     <- s1.runCollect.either
            out2     <- s2.runCollect.either
            expected = Left("Boom")
          } yield (out1 must_=== expected) && (out2 must_=== expected)
        case _ =>
          ZIO.fail("Wrong number of streams produced")
      }
    }

  private def broadcastBackPressure =
    unsafeRun {
      Stream
        .range(0, 5)
        .broadcast(2, 2)
        .use {
          case s1 :: s2 :: Nil =>
            for {
              ref       <- Ref.make[List[Int]](Nil)
              latch1    <- Promise.make[Nothing, Unit]
              fib       <- s1.tap(i => ref.update(i :: _) *> latch1.succeed(()).when(i == 2)).runDrain.fork
              _         <- latch1.await
              snapshot1 <- ref.get
              _         <- s2.runDrain
              _         <- fib.await
              snapshot2 <- ref.get
            } yield (snapshot1 must_=== List(2, 1, 0)) && (snapshot2 must_=== List(5, 4, 3, 2, 1, 0))
          case _ =>
            ZIO.fail("Wrong number of streams produced")
        }
    }

  private def broadcastUnsubscribe =
    flaky {
      Stream.range(0, 5).broadcast(2, 2).use {
        case s1 :: s2 :: Nil =>
          for {
            _    <- s1.process.use_(ZIO.unit).ignore
            out2 <- s2.runCollect
          } yield out2 must_=== List(0, 1, 2, 3, 4, 5)
        case _ =>
          ZIO.fail("Wrong number of streams produced")
      }
    }

  private def bufferStream = prop { list: List[Int] =>
    unsafeRunSync(
      Stream
        .fromIterable(list)
        .buffer(2)
        .run(Sink.collectAll[Int])
    ) must_== (Success(list))
  }

  private def bufferStreamError = {
    val e = new RuntimeException("boom")
    unsafeRunSync(
      (Stream.range(0, 10) ++ Stream.fail(e))
        .buffer(2)
        .run(Sink.collectAll[Int])
    ) must_== Failure(Cause.Fail(e))
  }

  private def bufferFastProducerSlowConsumer =
    unsafeRun(
      for {
        ref   <- Ref.make(List[Int]())
        latch <- Promise.make[Nothing, Unit]
        s     = Stream.range(1, 5).tap(i => ref.update(i :: _) *> latch.succeed(()).when(i == 4)).buffer(2)
        l <- s.process.use { as =>
              for {
                _ <- as
                _ <- latch.await
                l <- ref.get
              } yield l
            }
      } yield l.reverse must_=== (1 to 4).toList
    )

  private def catchAllCauseErrors =
    unsafeRun {
      val s1 = Stream(1, 2) ++ Stream.fail("Boom")
      val s2 = Stream(3, 4)

      s1.catchAllCause(_ => s2).runCollect.map(_ must_=== List(1, 2, 3, 4))
    }

  private def catchAllCauseDefects =
    unsafeRun {
      val s1 = Stream(1, 2) ++ Stream.dieMessage("Boom")
      val s2 = Stream(3, 4)

      s1.catchAllCause(_ => s2).runCollect.map(_ must_=== List(1, 2, 3, 4))
    }

  private def catchAllCauseHappyPath =
    unsafeRun {
      val s1 = Stream(1, 2)
      val s2 = Stream(3, 4)

      s1.catchAllCause(_ => s2).runCollect.map(_ must_=== List(1, 2))
    }

  private def catchAllCauseFinalizers =
    unsafeRun {
      for {
        fins   <- Ref.make(List[String]())
        s1     = (Stream(1, 2) ++ Stream.fail("Boom")).ensuring(fins.update("s1" :: _))
        s2     = (Stream(3, 4) ++ Stream.fail("Boom")).ensuring(fins.update("s2" :: _))
        _      <- s1.catchAllCause(_ => s2).runCollect.run
        result <- fins.get
      } yield result must_=== List("s2", "s1")
    }

  private def catchAllCauseScopeErrors =
    unsafeRun {
      val s1 = Stream(1, 2) ++ ZStream(ZManaged.fail("Boom"))
      val s2 = Stream(3, 4)

      s1.catchAllCause(_ => s2).runCollect.map(_ must_=== List(1, 2, 3, 4))
    }

  private def collect = unsafeRun {
    Stream(Left(1), Right(2), Left(3)).collect {
      case Right(n) => n
    }.runCollect.map(_ must_=== List(2))
  }

  private def collectWhile = unsafeRun {
    Stream(Some(1), Some(2), Some(3), None, Some(4)).collectWhile { case Some(v) => v }.runCollect
      .map(_ must_=== List(1, 2, 3))
  }

  private def collectWhileShortCircuits =
    unsafeRun {
      (Stream(Option(1)) ++ Stream.fail("Ouch")).collectWhile {
        case None => 1
      }.runDrain.either
        .map(_ must beRight(()))
    }

  private def concat =
    prop { (s1: Stream[String, Byte], s2: Stream[String, Byte]) =>
      val listConcat   = unsafeRunSync(s1.runCollect.zipWith(s2.runCollect)(_ ++ _))
      val streamConcat = unsafeRunSync((s1 ++ s2).runCollect)
      (streamConcat.succeeded && listConcat.succeeded) ==> (streamConcat must_=== listConcat)
    }

  private def concatFinalizerOrder =
    unsafeRun {
      for {
        log       <- Ref.make[List[String]](Nil)
        _         <- (Stream.finalizer(log.update("Second" :: _)) ++ Stream.finalizer(log.update("First" :: _))).runDrain
        execution <- log.get
      } yield execution must_=== List("First", "Second")
    }

  private def drain =
    unsafeRun(
      for {
        ref <- Ref.make(List[Int]())
        _   <- Stream.range(0, 10).mapM(i => ref.update(i :: _)).drain.run(Sink.drain)
        l   <- ref.get
      } yield l.reverse must_=== (0 to 10).toList
    )

  private def dropUntil = {
    def dropUntil[A](as: List[A])(f: A => Boolean): List[A] =
      as.dropWhile(!f(_)).drop(1)
    prop { (s: Stream[String, Byte], p: Byte => Boolean) =>
      unsafeRunSync(s.dropUntil(p).runCollect) must_=== unsafeRunSync(s.runCollect.map(dropUntil(_)(p)))
    }
  }

  private def dropWhile =
    prop { (s: Stream[String, Byte], p: Byte => Boolean) =>
      unsafeRunSync(s.dropWhile(p).runCollect) must_=== unsafeRunSync(s.runCollect.map(_.dropWhile(p)))
    }

  private def dropWhileShortCircuiting =
    unsafeRun {
      (Stream(1) ++ Stream.fail("Ouch"))
        .take(1)
        .dropWhile(_ => true)
        .runDrain
        .either
        .map(_ must beRight(()))
    }

  private def effectAsync =
    prop { list: List[Int] =>
      val s = Stream.effectAsync[Throwable, Int] { k =>
        list.foreach(a => k(Task.succeed(a)))
      }

      unsafeRunSync(s.take(list.size).runCollect) must_=== Success(list)
    }

  private def effectAsyncM = {
    val list = List(1, 2, 3)
    unsafeRun {
      for {
        latch <- Promise.make[Nothing, Unit]
        fiber <- ZStream
                  .effectAsyncM[Any, Throwable, Int] { k =>
                    latch.succeed(()) *>
                      Task.succeed {
                        list.foreach(a => k(Task.succeed(a)))
                      }
                  }
                  .take(list.size)
                  .run(Sink.collectAll[Int])
                  .fork
        _ <- latch.await
        s <- fiber.join
      } yield s must_=== list
    }
  }

  private def effectAsyncMSignalEndStream = unsafeRun {
    for {
      result <- Stream
                 .effectAsyncM[Nothing, Int] { k =>
                   k(IO.fail(None))
                   UIO.succeed(())
                 }
                 .runCollect
    } yield result must_=== List()
  }

  private def effectAsyncMaybeSignalEndStream = unsafeRun {
    for {
      result <- Stream
                 .effectAsyncMaybe[Nothing, Int] { k =>
                   k(IO.fail(None))
                   None
                 }
                 .runCollect
    } yield result must_=== List()
  }

  private def effectAsyncMaybeSome =
    prop { list: List[Int] =>
      val s = Stream.effectAsyncMaybe[Throwable, Int] { _ =>
        Some(Stream.fromIterable(list))
      }

      unsafeRunSync(s.runCollect.map(_.take(list.size))) must_=== Success(list)
    }

  private def effectAsyncMaybeNone =
    prop { list: List[Int] =>
      val s = Stream.effectAsyncMaybe[Throwable, Int] { k =>
        list.foreach(a => k(Task.succeed(a)))
        None
      }

      unsafeRunSync(s.take(list.size).runCollect) must_=== Success(list)
    }

  private def effectAsyncInterruptLeft = unsafeRun {
    for {
      cancelled <- Ref.make(false)
      latch     <- Promise.make[Nothing, Unit]
      fiber <- Stream
                .effectAsyncInterrupt[Nothing, Unit] { offer =>
                  offer(ZIO.succeed(())); Left(cancelled.set(true))
                }
                .tap(_ => latch.succeed(()))
                .run(Sink.collectAll[Unit])
                .fork
      _      <- latch.await
      _      <- fiber.interrupt
      result <- cancelled.get
    } yield result must_=== true
  }

  private def effectAsyncInterruptRight =
    prop { list: List[Int] =>
      val s = Stream.effectAsyncInterrupt[Throwable, Int] { _ =>
        Right(Stream.fromIterable(list))
      }

      unsafeRunSync(s.take(list.size).runCollect) must_=== Success(list)
    }

  private def effectAsyncInterruptSignalEndStream = unsafeRun {
    for {
      result <- Stream
                 .effectAsyncInterrupt[Nothing, Int] { k =>
                   k(IO.fail(None))
                   Left(UIO.succeed(()))
                 }
                 .runCollect
    } yield result must_=== List()
  }

  private def ensuring =
    unsafeRun {
      for {
        log <- Ref.make[List[String]](Nil)
        _ <- (for {
              _ <- Stream.bracket(log.update("Acquire" :: _))(_ => log.update("Release" :: _))
              _ <- Stream.fromEffect(log.update("Use" :: _))
            } yield ()).ensuring(log.update("Ensuring" :: _)).runDrain
        execution <- log.get
      } yield execution must_=== List("Ensuring", "Release", "Use", "Acquire")
    }

  private def ensuringFirst =
    unsafeRun {
      for {
        log <- Ref.make[List[String]](Nil)
        _ <- (for {
              _ <- Stream.bracket(log.update("Acquire" :: _))(_ => log.update("Release" :: _))
              _ <- Stream.fromEffect(log.update("Use" :: _))
            } yield ()).ensuringFirst(log.update("Ensuring" :: _)).runDrain
        execution <- log.get
      } yield execution must_=== List("Release", "Ensuring", "Use", "Acquire")
    }

  private def finalizer =
    unsafeRun {
      for {
        log <- Ref.make[List[String]](Nil)
        _ <- (for {
              _ <- Stream.bracket(log.update("Acquire" :: _))(_ => log.update("Release" :: _))
              _ <- Stream.finalizer(log.update("Use" :: _))
            } yield ()).ensuring(log.update("Ensuring" :: _)).runDrain
        execution <- log.get
      } yield execution must_=== List("Ensuring", "Release", "Use", "Acquire")
    }

  private def filter =
    prop { (s: Stream[String, Byte], p: Byte => Boolean) =>
      unsafeRunSync(s.filter(p).runCollect) must_=== unsafeRunSync(s.runCollect.map(_.filter(p)))
    }

  private def filterM =
    prop { (s: Stream[String, Byte], p: Byte => Boolean) =>
      unsafeRunSync(s.filterM(s => IO.succeed(p(s))).runCollect) must_=== unsafeRunSync(s.runCollect.map(_.filter(p)))
    }

  private def flatMapStackSafety = {
    def fib(n: Int): Stream[Nothing, Int] =
      if (n <= 1) Stream.succeed(n)
      else
        fib(n - 1).flatMap { a =>
          fib(n - 2).flatMap { b =>
            Stream.succeed(a + b)
          }
        }

    val stream   = fib(20)
    val expected = 6765

    unsafeRunSync(stream.runCollect).toEither must beRight(List(expected))
  }

  private def flatMapLeftIdentity =
    prop(
      (x: Int, f: Int => Stream[String, Int]) =>
        unsafeRunSync(Stream(x).flatMap(f).runCollect) must_=== unsafeRunSync(f(x).runCollect)
    )

  private def flatMapRightIdentity =
    prop(
      (m: Stream[String, Int]) =>
        unsafeRunSync(m.flatMap(i => Stream(i)).runCollect) must_=== unsafeRunSync(m.runCollect)
    )

  private def flatMapAssociativity =
    prop { (m: Stream[String, Int], f: Int => Stream[String, Int], g: Int => Stream[String, Int]) =>
      val leftStream  = m.flatMap(f).flatMap(g)
      val rightStream = m.flatMap(x => f(x).flatMap(g))
      unsafeRunSync(leftStream.runCollect) must_=== unsafeRunSync(rightStream.runCollect)
    }

  private def flatMapInnerFinalizers = {
    val test =
      for {
        effects <- Ref.make(List[Int]())
        push    = (i: Int) => effects.update(i :: _)
        latch   <- Promise.make[Nothing, Unit]
        fiber <- Stream(
                  Stream.bracket(push(1))(_ => push(1)),
                  Stream.fromEffect(push(2)),
                  Stream.bracket(push(3))(_ => push(3)) *> Stream.fromEffect(
                    latch.succeed(()) *> ZIO.never
                  )
                ).flatMap(identity).runDrain.fork
        _      <- latch.await
        _      <- fiber.interrupt
        result <- effects.get
      } yield result must_=== List(3, 3, 2, 1, 1)

    unsafeRun(test)
  }

  private def flatMapFinalizerOrdering = {
    val test =
      for {
        effects <- Ref.make(List[Int]())
        push    = (i: Int) => effects.update(i :: _)
        stream = for {
          _ <- Stream.bracket(push(1))(_ => push(1))
          _ <- Stream((), ()).tap(_ => push(2)).ensuring(push(2))
          _ <- Stream.bracket(push(3))(_ => push(3))
          _ <- Stream((), ()).tap(_ => push(4)).ensuring(push(4))
        } yield ()
        _      <- stream.runDrain
        result <- effects.get
      } yield result must_=== List(1, 2, 3, 4, 4, 4, 3, 2, 3, 4, 4, 4, 3, 2, 1).reverse

    unsafeRun(test)
  }

  private def flatMapParGuaranteeOrdering = prop { m: List[Int] =>
    val flatMap    = Stream.fromIterable(m).flatMap(i => Stream(i, i)).runCollect
    val flatMapPar = Stream.fromIterable(m).flatMapPar(1)(i => Stream(i, i)).runCollect
    unsafeRunSync(flatMap) must_=== unsafeRunSync(flatMapPar)
  }

  private def flatMapParConsistency = prop { (n: Int, m: List[Int]) =>
    val flatMap    = Stream.fromIterable(m).flatMap(i => Stream(i, i)).runCollect.map(_.toSet)
    val flatMapPar = Stream.fromIterable(m).flatMapPar(n)(i => Stream(i, i)).runCollect.map(_.toSet)

    (n > 0) ==> (unsafeRunSync(flatMap) must_=== unsafeRunSync(flatMapPar))
  }

  private def flatMapParShortCircuiting = unsafeRun {
    Stream
      .mergeAll(2)(
        Stream.never,
        Stream(1)
      )
      .take(1)
      .run(Sink.collectAll[Int])
      .map(_ must_=== List(1))
  }

  private def flatMapParInterruptionPropagation = unsafeRun {
    for {
      substreamCancelled <- Ref.make[Boolean](false)
      latch              <- Promise.make[Nothing, Unit]
      fiber <- Stream(())
                .flatMapPar(1)(
                  _ => Stream.fromEffect((latch.succeed(()) *> ZIO.never).onInterrupt(substreamCancelled.set(true)))
                )
                .run(Sink.collectAll[Unit])
                .fork
      _         <- latch.await
      _         <- fiber.interrupt
      cancelled <- substreamCancelled.get
    } yield cancelled must_=== true
  }

  private def flatMapParInnerErrorsInterruptAllFibers = unsafeRun {
    for {
      substreamCancelled <- Ref.make[Boolean](false)
      latch              <- Promise.make[Nothing, Unit]
      result <- Stream(
                 Stream.fromEffect((latch.succeed(()) *> ZIO.never).onInterrupt(substreamCancelled.set(true))),
                 Stream.fromEffect(latch.await *> ZIO.fail("Ouch"))
               ).flatMapPar(2)(identity)
                 .run(Sink.drain)
                 .either
      cancelled <- substreamCancelled.get
    } yield (cancelled must_=== true) and (result must beLeft("Ouch"))
  }

  private def flatMapParFinalizerOrdering = unsafeRun {
    for {
      execution <- Ref.make[List[String]](Nil)
      inner = Stream
        .bracket(execution.update("InnerAcquire" :: _))(_ => execution.update("InnerRelease" :: _))
      _ <- Stream
            .bracket(execution.update("OuterAcquire" :: _).as(inner))(_ => execution.update("OuterRelease" :: _))
            .flatMapPar(2)(identity)
            .runDrain
      results <- execution.get
    } yield results must_=== List("OuterRelease", "InnerRelease", "InnerAcquire", "OuterAcquire")
  }

  private def flatMapParOuterErrorsInterruptAllFibers = unsafeRun {
    for {
      substreamCancelled <- Ref.make[Boolean](false)
      latch              <- Promise.make[Nothing, Unit]
      result <- (Stream(()) ++ Stream.fromEffect(latch.await *> ZIO.fail("Ouch")))
                 .flatMapPar(2) { _ =>
                   Stream.fromEffect((latch.succeed(()) *> ZIO.never).onInterrupt(substreamCancelled.set(true)))
                 }
                 .run(Sink.drain)
                 .either
      cancelled <- substreamCancelled.get
    } yield (cancelled must_=== true) and (result must beLeft("Ouch"))
  }

  private def flatMapParInnerDefectsInterruptAllFibers = unsafeRun {
    val ex = new RuntimeException("Ouch")

    for {
      substreamCancelled <- Ref.make[Boolean](false)
      latch              <- Promise.make[Nothing, Unit]
      result <- Stream(
                 Stream.fromEffect((latch.succeed(()) *> ZIO.never).onInterrupt(substreamCancelled.set(true))),
                 Stream.fromEffect(latch.await *> ZIO.die(ex))
               ).flatMapPar(2)(identity)
                 .run(Sink.drain)
                 .run
      cancelled <- substreamCancelled.get
    } yield (cancelled must_=== true) and (result must_=== Exit.die(ex))
  }

  private def flatMapParOuterDefectsInterruptAllFibers = unsafeRun {
    val ex = new RuntimeException()

    for {
      substreamCancelled <- Ref.make[Boolean](false)
      latch              <- Promise.make[Nothing, Unit]
      result <- (Stream(()) ++ Stream.fromEffect(latch.await *> ZIO.die(ex)))
                 .flatMapPar(2) { _ =>
                   Stream.fromEffect((latch.succeed(()) *> ZIO.never).onInterrupt(substreamCancelled.set(true)))
                 }
                 .run(Sink.drain)
                 .run
      cancelled <- substreamCancelled.get
    } yield (cancelled must_=== true) and (result must_=== Exit.die(ex))
  }

  private def flatMapParSwitchGuaranteeOrderingNoParallelism = unsafeRun {
    for {
      lastExecuted <- Ref.make(false)
      semaphore    <- Semaphore.make(1)
      _ <- Stream(1, 2, 3, 4)
            .flatMapParSwitch(1) { i =>
              if (i > 3) Stream.bracket(UIO.unit)(_ => lastExecuted.set(true)).flatMap(_ => Stream.empty)
              else Stream.bracket(semaphore.acquire)(_ => semaphore.release).flatMap(_ => Stream.never)
            }
            .runDrain
      result <- semaphore.withPermit(lastExecuted.get)
    } yield result must_=== true
  }

  private def flatMapParSwitchGuaranteeOrderingWithParallelism = unsafeRun {
    for {
      lastExecuted <- Ref.make(0)
      semaphore    <- Semaphore.make(4)
      _ <- Stream(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)
            .flatMapParSwitch(4) { i =>
              if (i > 8) Stream.bracket(UIO.unit)(_ => lastExecuted.update(_ + 1)).flatMap(_ => Stream.empty)
              else Stream.bracket(semaphore.acquire)(_ => semaphore.release).flatMap(_ => Stream.never)
            }
            .runDrain
      result <- semaphore.withPermits(4)(lastExecuted.get)
    } yield result must_=== 4
  }

  private def flatMapParSwitchShortCircuiting = unsafeRun {
    Stream(Stream.never, Stream(1))
      .flatMapParSwitch(2)(identity)
      .take(1)
      .runCollect
      .map(_ must_=== List(1))
  }

  private def flatMapParSwitchInterruptionPropagation = unsafeRun {
    for {
      substreamCancelled <- Ref.make[Boolean](false)
      latch              <- Promise.make[Nothing, Unit]
      fiber <- Stream(())
                .flatMapParSwitch(1)(
                  _ => Stream.fromEffect((latch.succeed(()) *> UIO.never).onInterrupt(substreamCancelled.set(true)))
                )
                .runCollect
                .fork
      _         <- latch.await
      _         <- fiber.interrupt
      cancelled <- substreamCancelled.get
    } yield cancelled must_=== true
  }

  private def flatMapParSwitchInnerErrorsInterruptAllFibers = unsafeRun {
    for {
      substreamCancelled <- Ref.make[Boolean](false)
      latch              <- Promise.make[Nothing, Unit]
      result <- Stream(
                 Stream.fromEffect((latch.succeed(()) *> UIO.never).onInterrupt(substreamCancelled.set(true))),
                 Stream.fromEffect(latch.await *> IO.fail("Ouch"))
               ).flatMapParSwitch(2)(identity).runDrain.either
      cancelled <- substreamCancelled.get
    } yield (cancelled must_=== true) and (result must beLeft("Ouch"))
  }

  private def flatMapParSwitchFinalizerOrdering = unsafeRun {
    for {
      execution <- Ref.make(List.empty[String])
      inner     = Stream.bracket(execution.update("InnerAcquire" :: _))(_ => execution.update("InnerRelease" :: _))
      _ <- Stream
            .bracket(execution.update("OuterAcquire" :: _).as(inner))(_ => execution.update("OuterRelease" :: _))
            .flatMapParSwitch(2)(identity)
            .runDrain
      results <- execution.get
    } yield results must_=== List("OuterRelease", "InnerRelease", "InnerAcquire", "OuterAcquire")
  }

  private def flatMapParSwitchOuterErrorsInterruptAllFibers = unsafeRun {
    for {
      substreamCancelled <- Ref.make[Boolean](false)
      latch              <- Promise.make[Nothing, Unit]
      result <- (Stream(()) ++ Stream.fromEffect(latch.await *> IO.fail("Ouch")))
                 .flatMapParSwitch(2) { _ =>
                   Stream.fromEffect((latch.succeed(()) *> UIO.never).onInterrupt(substreamCancelled.set(true)))
                 }
                 .runDrain
                 .either
      cancelled <- substreamCancelled.get
    } yield (cancelled must_=== true) and (result must beLeft("Ouch"))
  }

  private def flatMapParSwitchInnerDefectsInterruptAllFibers = unsafeRun {
    val ex = new RuntimeException("Ouch")

    for {
      substreamCancelled <- Ref.make[Boolean](false)
      latch              <- Promise.make[Nothing, Unit]
      result <- Stream(
                 Stream.fromEffect((latch.succeed(()) *> ZIO.never).onInterrupt(substreamCancelled.set(true))),
                 Stream.fromEffect(latch.await *> ZIO.die(ex))
               ).flatMapParSwitch(2)(identity)
                 .run(Sink.drain)
                 .run
      cancelled <- substreamCancelled.get
    } yield (cancelled must_=== true) and (result must_=== Exit.die(ex))
  }

  private def flatMapParSwitchOuterDefectsInterruptAllFibers = unsafeRun {
    val ex = new RuntimeException()

    for {
      substreamCancelled <- Ref.make[Boolean](false)
      latch              <- Promise.make[Nothing, Unit]
      result <- (Stream(()) ++ Stream.fromEffect(latch.await *> ZIO.die(ex)))
                 .flatMapParSwitch(2) { _ =>
                   Stream.fromEffect((latch.succeed(()) *> ZIO.never).onInterrupt(substreamCancelled.set(true)))
                 }
                 .run(Sink.drain)
                 .run
      cancelled <- substreamCancelled.get
    } yield (cancelled must_=== true) and (result must_=== Exit.die(ex))
  }

  private def foreach = {
    var sum = 0
    val s   = Stream(1, 1, 1, 1, 1)

    unsafeRun(s.foreach[Any, Nothing](a => IO.effectTotal(sum += a)))
    sum must_=== 5
  }

  private def foreachWhile = {
    var sum = 0
    val s   = Stream(1, 1, 1, 1, 1, 1)

    unsafeRun(
      s.foreachWhile[Any, Nothing](
        a =>
          IO.effectTotal(
            if (sum >= 3) false
            else {
              sum += a;
              true
            }
          )
      )
    )
    sum must_=== 3
  }

  private def foreachWhileShortCircuits = unsafeRun {
    for {
      flag    <- Ref.make(true)
      _       <- (Stream(true, true, false) ++ Stream.fromEffect(flag.set(false)).drain).foreachWhile(ZIO.succeed)
      skipped <- flag.get
    } yield skipped must_=== true
  }

  private def forever = {
    var sum = 0
    val s = Stream(1).forever.foreachWhile[Any, Nothing](
      a =>
        IO.effectTotal {
          sum += a;
          if (sum >= 9) false else true
        }
    )

    unsafeRun(s)
    sum must_=== 9
  }

  private def fromChunk = prop { c: Chunk[Int] =>
    unsafeRunSync(Stream.fromChunk(c).runCollect) must_=== Success(c.toSeq.toList)
  }

  private def fromInputStream = unsafeRun {
    import java.io.ByteArrayInputStream
    val chunkSize = ZStreamChunk.DefaultChunkSize
    val data      = Array.tabulate[Byte](chunkSize * 5 / 2)(_.toByte)
    val is        = new ByteArrayInputStream(data)
    ZStream.fromInputStream(is, chunkSize).run(Sink.collectAll[Chunk[Byte]]) map { chunks =>
      chunks.flatMap(_.toArray[Byte]).toArray must_=== data
    }
  }

  private def fromIterable = prop { l: List[Int] =>
    unsafeRunSync(Stream.fromIterable(l).runCollect) must_=== Success(l)
  }

  private def fromIterator = prop { l: List[Int] =>
    unsafeRunSync(Stream.fromIterator(UIO.effectTotal(l.iterator)).runCollect) must_=== Success(l)
  }

  private def fromQueue = prop { c: Chunk[Int] =>
    val result = unsafeRunSync {
      for {
        queue <- Queue.unbounded[Int]
        _     <- queue.offerAll(c.toSeq)
        fiber <- Stream
                  .fromQueue(queue)
                  .foldWhileM[Any, Nothing, Int, List[Int]](List[Int]())(_ => true)((acc, el) => IO.succeed(el :: acc))
                  .map(_.reverse)
                  .fork
        _     <- waitForSize(queue, -1)
        _     <- queue.shutdown
        items <- fiber.join
      } yield items
    }
    result must_=== Success(c.toSeq.toList)
  }

  private def groupByValues =
    unsafeRun {
      val words = List.fill(1000)(0 to 100).flatten.map(_.toString())
      Stream
        .fromIterable(words)
        .groupByKey(identity, 8192) {
          case (k, s) =>
            s.transduce(Sink.foldLeft[String, Int](0) { case (acc: Int, _: String) => acc + 1 })
              .take(1)
              .map((k -> _))
        }
        .runCollect
        .map(_.toMap must_=== (0 to 100).map((_.toString -> 1000)).toMap)
    }

  private def groupByFirst =
    unsafeRun {
      val words = List.fill(1000)(0 to 100).flatten.map(_.toString())
      Stream
        .fromIterable(words)
        .groupByKey(identity, 1050)
        .first(2) {
          case (k, s) =>
            s.transduce(Sink.foldLeft[String, Int](0) { case (acc: Int, _: String) => acc + 1 })
              .take(1)
              .map((k -> _))
        }
        .runCollect
        .map(_.toMap must_=== (0 to 1).map((_.toString -> 1000)).toMap)
    }

  private def groupByFilter =
    unsafeRun {
      val words = List.fill(1000)(0 to 100).flatten
      Stream
        .fromIterable(words)
        .groupByKey(identity, 1050)
        .filter(_ <= 5) {
          case (k, s) =>
            s.transduce(Sink.foldLeft[Int, Int](0) { case (acc, _) => acc + 1 })
              .take(1)
              .map((k -> _))
        }
        .runCollect
        .map(_.toMap must_=== (0 to 5).map((_ -> 1000)).toMap)
    }

  private def groupByErrorsOuter =
    unsafeRun {
      val words = List("abc", "test", "test", "foo")
      (Stream.fromIterable(words) ++ Stream.fail("Boom"))
        .groupByKey(identity) { case (_, s) => s.drain }
        .runCollect
        .either
        .map(_ must_=== Left("Boom"))
    }

  private def map =
    prop { (s: Stream[String, Byte], f: Byte => Int) =>
      unsafeRunSync(s.map(f).runCollect) must_=== unsafeRunSync(s.runCollect.map(_.map(f)))
    }

  private def mapAccum = unsafeRun {
    Stream(1, 1, 1).mapAccum(0)((acc, el) => (acc + el, acc + el)).runCollect.map(_ must_=== List(1, 2, 3))
  }

  private def mapAccumM = unsafeRun {
    Stream(1, 1, 1)
      .mapAccumM[Any, Nothing, Int, Int](0)((acc, el) => IO.succeed((acc + el, acc + el)))
      .runCollect
      .map(_ must_=== List(1, 2, 3))
  }

  private def mapConcat =
    prop { (s: Stream[String, Byte], f: Byte => Chunk[Int]) =>
      unsafeRunSync(s.mapConcat(f).runCollect) must_=== unsafeRunSync(s.runCollect.map(_.flatMap(v => f(v).toSeq)))
    }

  private def mapM = {
    implicit val arb: Arbitrary[IO[String, Byte]] = Arbitrary(genIO[String, Byte])

    prop { (data: List[Byte], f: Byte => IO[String, Byte]) =>
      unsafeRun {
        val s = Stream.fromIterable(data)

        for {
          l <- s.mapM(f).runCollect.either
          r <- IO.foreach(data)(f).either
        } yield l must_=== r
      }
    }
  }

  private def mapMPar = {
    implicit val arb: Arbitrary[IO[Unit, Byte]] = Arbitrary(genIO[Unit, Byte])

    prop { (data: List[Byte], f: Byte => IO[Unit, Byte]) =>
      unsafeRun {
        val s = Stream.fromIterable(data)

        for {
          l <- s.mapMPar(8)(f).runCollect.either
          r <- IO.foreachParN(8)(data)(f).either
        } yield l must_=== r
      }
    }
  }

  private def mapMParOrder =
    nonFlaky {
      for {
        queue  <- Queue.unbounded[Int]
        _      <- Stream.range(0, 9).mapMPar(1)(queue.offer).runDrain
        result <- queue.takeAll
      } yield result must_== result.sorted
    }

  private def mapMParInterruptionPropagation = unsafeRun {
    for {
      interrupted <- Ref.make(false)
      latch       <- Promise.make[Nothing, Unit]
      fib <- Stream(())
              .mapMPar(1) { _ =>
                (latch.succeed(()) *> ZIO.never).onInterrupt(interrupted.set(true))
              }
              .runDrain
              .fork
      _      <- latch.await
      _      <- fib.interrupt
      result <- interrupted.get
    } yield result must_=== true
  }

  private def mapMParGuaranteeOrdering =
    prop { (n: Int, m: List[Int]) =>
      val mapM    = Stream.fromIterable(m).mapM(UIO.succeed).runCollect
      val mapMPar = Stream.fromIterable(m).mapMPar(n)(UIO.succeed).runCollect
      (n > 0) ==> (unsafeRun(mapM) must_=== unsafeRun(mapMPar))
    }.setGens(Gen.posNum[Int], Gen.listOf(Arbitrary.arbitrary[Int]))

  private def merge =
    prop { (s1: Stream[String, Int], s2: Stream[String, Int]) =>
      val mergedStream = unsafeRunSync((s1 merge s2).runCollect.map(_.toSet))
      val mergedLists = unsafeRunSync(
        s1.runCollect
          .zipWith(s2.runCollect) { (left, right) =>
            left ++ right
          }
          .map(_.toSet)
      )

      (!mergedStream.succeeded && !mergedLists.succeeded) || (mergedStream must_=== mergedLists)
    }

  private def mergeEither = {
    val s1 = Stream(1, 2)
    val s2 = Stream(1, 2)

    val merge = s1.mergeEither(s2)
    val list: List[Either[Int, Int]] = unsafeRunSync(merge.runCollect).toEither.fold(
      _ => List.empty,
      identity
    )

    list must containTheSameElementsAs(List(Left(1), Left(2), Right(1), Right(2)))
  }

  private def mergeWith = {
    val s1 = Stream(1, 2)
    val s2 = Stream(1, 2)

    val merge = s1.mergeWith(s2)(_.toString, _.toString)
    val list: List[String] = unsafeRunSync(merge.runCollect).toEither.fold(
      _ => List.empty,
      identity
    )

    list must containTheSameElementsAs(List("1", "2", "1", "2"))
  }

  private def mergeWithShortCircuit = unsafeRun {
    val s1 = Stream(1, 2)
    val s2 = Stream(1, 2)

    s1.mergeWith(s2)(_.toString, _.toString)
      .run(Sink.succeed[String, String]("done"))
      .map(_ must_=== "done")
  }

  private def mergeWithPrioritizesFailure = unsafeRun {
    val s1 = Stream.never
    val s2 = Stream.fail("Ouch")

    s1.mergeWith(s2)(_ => (), _ => ())
      .runCollect
      .either
      .map(_ must_=== Left("Ouch"))
  }

  private def paginate = unsafeRun {
    val s = (0, List(1, 2, 3))

    ZStream
      .paginate(s) {
        case (x, Nil)      => ZIO.succeed(x -> None)
        case (x, x0 :: xs) => ZIO.succeed(x -> Some(x0 -> xs))
      }
      .runCollect
      .map(_ must_=== List(0, 1, 2, 3))
  }

  private def partitionEitherValues =
    unsafeRun {
      Stream
        .range(0, 5)
        .partitionEither { i =>
          if (i % 2 == 0) ZIO.succeed(Left(i))
          else ZIO.succeed(Right(i))
        }
        .use {
          case (s1, s2) =>
            for {
              out1 <- s1.runCollect
              out2 <- s2.runCollect
            } yield (out1 must_=== List(0, 2, 4)) && (out2 must_=== List(1, 3, 5))
        }
    }

  private def partitionEitherErrors =
    unsafeRun {
      (Stream.range(0, 1) ++ Stream.fail("Boom")).partitionEither { i =>
        if (i % 2 == 0) ZIO.succeed(Left(i))
        else ZIO.succeed(Right(i))
      }.use {
        case (s1, s2) =>
          for {
            out1 <- s1.runCollect.either
            out2 <- s2.runCollect.either
          } yield (out1 must_=== Left("Boom")) && (out2 must_=== Left("Boom"))
      }
    }

  private def partitionEitherBackPressure =
    unsafeRun {
      Stream
        .range(0, 5)
        .partitionEither({ i =>
          if (i % 2 == 0) ZIO.succeed(Left(i))
          else ZIO.succeed(Right(i))
        }, 1)
        .use {
          case (s1, s2) =>
            for {
              ref       <- Ref.make[List[Int]](Nil)
              latch1    <- Promise.make[Nothing, Unit]
              fib       <- s1.tap(i => ref.update(i :: _) *> latch1.succeed(()).when(i == 2)).runDrain.fork
              _         <- latch1.await
              snapshot1 <- ref.get
              other     <- s2.runCollect
              _         <- fib.await
              snapshot2 <- ref.get
            } yield (snapshot1 must_=== List(2, 0)) && (snapshot2 must_=== List(4, 2, 0)) && (other must_=== List(
              1,
              3,
              5
            ))
        }
    }

  private def peel = unsafeRun {
    val s      = Stream('1', '2', ',', '3', '4')
    val parser = ZSink.collectAllWhile[Char](_.isDigit).map(_.mkString.toInt) <* ZSink.collectAllWhile[Char](_ == ',')
    val peeled = s.peel(parser).use[Any, Int, (Int, Exit[Nothing, List[Char]])] {
      case (n, rest) =>
        IO.succeed((n, unsafeRunSync(rest.runCollect)))
    }

    peeled.map(_ must_=== ((12, Success(List('3', '4')))))
  }

  private def range = unsafeRun {
    Stream.range(0, 9).runCollect.map(_ must_=== (0 to 9).toList)
  }

  private def repeat =
    unsafeRun(
      Stream(1)
        .repeat(Schedule.recurs(4))
        .run(Sink.collectAll[Int])
        .map(_ must_=== List(1, 1, 1, 1, 1))
    )

  private def repeatShortCircuits =
    unsafeRun(
      for {
        ref <- Ref.make[List[Int]](Nil)
        _ <- Stream
              .fromEffect(ref.update(1 :: _))
              .repeat(Schedule.spaced(10.millis))
              .take(2)
              .run(Sink.drain)
        result <- ref.get
      } yield result must_=== List(1, 1)
    )

  private def repeatEither =
    unsafeRun(
      Stream(1)
        .repeatEither(Schedule.recurs(4))
        .run(Sink.collectAll[Either[Int, Int]])
        .map(_ must_=== List(Right(1), Right(1), Left(1), Right(1), Left(2), Right(1), Left(3), Right(1), Left(4)))
    )

  private def repeatEitherShortCircuits =
    unsafeRun(
      for {
        ref <- Ref.make[List[Int]](Nil)
        _ <- Stream
              .fromEffect(ref.update(1 :: _))
              .repeatEither(Schedule.spaced(10.millis))
              .take(3) // take one schedule output
              .run(Sink.drain)
        result <- ref.get
      } yield result must_=== List(1, 1)
    )

  private def repeatEffect =
    unsafeRun(
      Stream
        .repeatEffect(IO.succeed(1))
        .take(2)
        .run(Sink.collectAll[Int])
        .map(_ must_=== List(1, 1))
    )

  private def repeatEffectWith =
    unsafeRun(
      for {
        ref <- Ref.make[List[Int]](Nil)
        _ <- Stream
              .repeatEffectWith(ref.update(1 :: _), Schedule.spaced(10.millis))
              .take(2)
              .run(Sink.drain)
        result <- ref.get
      } yield result must_=== List(1, 1)
    )

  private def scheduleWith =
    unsafeRun(
      Stream("A", "B", "C", "A", "B", "C")
        .scheduleWith(Schedule.recurs(2) *> Schedule.fromFunction((_) => "Done"))(_.toLowerCase, identity)
        .run(Sink.collectAll[String])
        .map(_ must_=== List("a", "b", "c", "Done", "a", "b", "c", "Done"))
    )

  private def scheduleEither =
    unsafeRun(
      Stream("A", "B", "C")
        .scheduleEither(Schedule.recurs(2) *> Schedule.fromFunction((_) => "!"))
        .run(Sink.collectAll[Either[String, String]])
        .map(_ must_=== List(Right("A"), Right("B"), Right("C"), Left("!")))
    )

  private def scheduleElementsWith =
    unsafeRun(
      Stream("A", "B", "C")
        .scheduleElementsWith(Schedule.recurs(0) *> Schedule.fromFunction((_) => 123))(identity, _.toString)
        .run(Sink.collectAll[String])
        .map(_ must_=== List("A", "123", "B", "123", "C", "123"))
    )

  private def scheduleElementsEither =
    unsafeRun(
      Stream("A", "B", "C")
        .scheduleElementsEither(Schedule.recurs(0) *> Schedule.fromFunction((_) => 123))
        .run(Sink.collectAll[Either[Int, String]])
        .map(_ must_=== List(Right("A"), Left(123), Right("B"), Left(123), Right("C"), Left(123)))
    )

  private def repeatedAndSpaced =
    unsafeRun(
      Stream("A", "B", "C")
        .scheduleElements(Schedule.once)
        .run(Sink.collectAll[String])
        .map(_ must_=== List("A", "A", "B", "B", "C", "C"))
    )

  private def spacedShortCircuitsAfterScheduleFinished =
    unsafeRun(
      Stream("A", "B", "C")
        .scheduleElements(Schedule.once)
        .take(3)
        .run(Sink.collectAll[String])
        .map(_ must_=== List("A", "A", "B"))
    )

  private def spacedShortCircuitsWhileInSchedule =
    unsafeRun(
      Stream("A", "B", "C")
        .scheduleElements(Schedule.once)
        .take(4)
        .run(Sink.collectAll[String])
        .map(_ must_=== List("A", "A", "B", "B"))
    )

  private def take =
    prop { (s: Stream[String, Byte], n: Int) =>
      val takeStreamResult = unsafeRunSync(s.take(n).runCollect)
      val takeListResult   = unsafeRunSync(s.runCollect.map(_.take(n)))
      (takeListResult.succeeded ==> (takeStreamResult must_=== takeListResult))
    }

  private def takeShortCircuits =
    unsafeRun(
      for {
        ran    <- Ref.make(false)
        stream = (Stream(1) ++ Stream.fromEffect(ran.set(true)).drain).take(0)
        _      <- stream.run(Sink.drain)
        result <- ran.get
      } yield result must_=== false
    )

  private def take0ShortCircuitsStreamNever =
    unsafeRun(
      for {
        units <- Stream.never.take(0).run(Sink.collectAll[Unit])
      } yield units must_=== List()
    )

  private def take1ShortCircuitsStreamNever =
    unsafeRun(
      for {
        ints <- (Stream(1) ++ Stream.never).take(1).run(Sink.collectAll[Int])
      } yield ints must_=== List(1)
    )

  private def takeUntil = {
    def takeUntil[A](as: List[A])(f: A => Boolean): List[A] =
      as.takeWhile(!f(_)) ++ as.dropWhile(!f(_)).take(1)
    prop { (s: Stream[String, Byte], p: Byte => Boolean) =>
      val streamTakeWhile = unsafeRunSync(s.takeUntil(p).runCollect)
      val listTakeWhile   = unsafeRunSync(s.runCollect.map(takeUntil(_)(p)))
      listTakeWhile.succeeded ==> (streamTakeWhile must_=== listTakeWhile)
    }
  }

  private def takeWhile =
    prop { (s: Stream[String, Byte], p: Byte => Boolean) =>
      val streamTakeWhile = unsafeRunSync(s.takeWhile(p).runCollect)
      val listTakeWhile   = unsafeRunSync(s.runCollect.map(_.takeWhile(p)))
      listTakeWhile.succeeded ==> (streamTakeWhile must_=== listTakeWhile)
    }

  private def takeWhileShortCircuits =
    unsafeRun(
      (Stream(1) ++ Stream.fail("Ouch"))
        .takeWhile(_ => false)
        .runDrain
        .either
        .map(_ must beRight(()))
    )

  private def tap = {
    var sum      = 0
    val s        = Stream(1, 1).tap[Any, Nothing](a => IO.effectTotal(sum += a))
    val elements = unsafeRunSync(s.runCollect)

    (elements must_=== Success(List(1, 1))) and (sum must_=== 2)
  }

  private def throttleEnforceFreeElements = unsafeRun {
    Stream(1, 2, 3, 4)
      .throttleEnforce(0, Duration.Infinity)(_ => 0)
      .runCollect must_=== List(1, 2, 3, 4)
  }

  private def throttleEnforceNoBandwidth = unsafeRun {
    Stream(1, 2, 3, 4)
      .throttleEnforce(0, Duration.Infinity)(_ => 1)
      .runCollect must_=== List()
  }

  private def throttleShapeFreeElements = unsafeRun {
    Stream(1, 2, 3, 4)
      .throttleShape(1, Duration.Infinity)(_ => 0)
      .runCollect must_=== List(1, 2, 3, 4)
  }

  private def timeoutSucceed =
    unsafeRun {
      Stream
        .succeed(1)
        .timeout(Duration.Infinity)
        .runCollect
        .map(_ must_=== List(1))
    }

  private def timeoutInterrupt =
    unsafeRun {
      Stream
        .range(0, 5)
        .tap(_ => ZIO.sleep(Duration.Infinity))
        .timeout(Duration.Zero)
        .runDrain
        .ignore
        .map(_ => true must_=== true)
    }

  private def toQueue = prop { c: Chunk[Int] =>
    val s = Stream.fromChunk(c)
    val result = unsafeRunSync {
      s.toQueue(1000).use { queue: Queue[Take[Nothing, Int]] =>
        waitForSize(queue, c.length + 1) *> queue.takeAll
      }
    }
    result must_=== Success(c.toSeq.toList.map(i => Take.Value(i)) :+ Take.End)
  }

  private def transduce = unsafeRun {
    val s      = Stream('1', '2', ',', '3', '4')
    val parser = ZSink.collectAllWhile[Char](_.isDigit).map(_.mkString.toInt) <* ZSink.collectAllWhile[Char](_ == ',')

    s.transduce(parser).runCollect.map(_ must_=== List(12, 34))
  }

  private def transduceNoRemainder = unsafeRun {
    val sink = Sink.fold(100)(_ % 2 == 0)((s, a: Int) => (s + a, Chunk[Int]()))
    ZStream(1, 2, 3, 4).transduce(sink).runCollect.map(_ must_=== List(101, 105, 104))
  }

  private def transduceWithRemainder = unsafeRun {
    val sink = Sink
      .fold[Int, Int, (Int, Boolean)]((0, true))(_._2) { (s, a: Int) =>
        a match {
          case 1 => ((s._1 + 100, true), Chunk.empty)
          case 2 => ((s._1 + 100, true), Chunk.empty)
          case 3 => ((s._1 + 3, false), Chunk.single(a + 1))
          case _ => ((s._1 + 4, false), Chunk.empty)
        }
      }
      .map(_._1)

    ZStream(1, 2, 3).transduce(sink).runCollect.map(_ must_=== List(203, 4))
  }

  private def transduceSinkMore = unsafeRun {
    val sink = Sink.foldLeft(0)((s, a: Int) => s + a)
    ZStream(1, 2, 3).transduce(sink).runCollect.map(_ must_=== List(1 + 2 + 3))
  }

  private def transduceManaged = {
    final class TestSink(ref: Ref[Int]) extends ZSink[Any, Throwable, Int, Int, List[Int]] {
      type State = (List[Int], Boolean)

      def extract(state: State) = UIO.succeed((state._1, Chunk.empty))

      def initial = UIO.succeed((Nil, true))

      def step(state: State, a: Int) =
        for {
          i <- ref.get
          _ <- if (i != 1000) IO.fail(new IllegalStateException(i.toString)) else IO.unit
        } yield (List(a, a), false)

      def cont(state: State) = state._2
    }

    val stream = ZStream(1, 2, 3, 4)

    for {
      resource <- Ref.make(0)
      sink     = ZManaged.make(resource.set(1000).as(new TestSink(resource)))(_ => resource.set(2000))
      result   <- stream.transduceManaged(sink).runCollect
      i        <- resource.get
      _        <- if (i != 2000) IO.fail(new IllegalStateException(i.toString)) else IO.unit
    } yield result must_=== List(List(1, 1), List(2, 2), List(3, 3), List(4, 4))
  }

  private def transduceManagedError = unsafeRun {
    val fail = "I'm such a failure!"
    val sink = ZManaged.fail(fail)
    ZStream(1, 2, 3).transduceManaged(sink).runCollect.either.map(_ must beLeft(fail))
  }

  private def unfold = {
    val s = Stream.unfold(0) { i =>
      if (i < 10) Some((i, i + 1))
      else None
    }

    unsafeRunSync(s.runCollect) must_=== Success((0 to 9).toList)
  }

  private def unfoldM = {
    val s = Stream.unfoldM(0) { i =>
      if (i < 10) IO.succeed(Some((i, i + 1)))
      else IO.succeed(None)
    }

    unsafeRunSync(s.runCollect) must_=== Success((0 to 9).toList)
  }

  private def unTake =
    unsafeRun(
      Stream
        .range(0, 10)
        .toQueue[Nothing, Int](1)
        .use { q =>
          Stream.fromQueue(q).unTake.run(Sink.collectAll[Int])
        }
        .map(_ must_=== (0 to 10).toList)
    )

  private def unTakeError = {
    val e = new RuntimeException("boom")
    unsafeRunSync(
      (Stream.range(0, 10) ++ Stream.fail(e))
        .toQueue[Throwable, Int](1)
        .use { q =>
          Stream.fromQueue(q).unTake.run(Sink.collectAll[Int])
        }
    ) must_== Failure(Cause.fail(e))
  }

  private def unNone =
    prop(
      (s: Stream[String, Option[Int]]) =>
        unsafeRunSync(s.unNone.runCollect) must_=== unsafeRunSync(s.runCollect.map(_.flatten))
    )

  private def zipWith = unsafeRun {
    val s1 = Stream(1, 2, 3)
    val s2 = Stream(1, 2)

    s1.zipWith(s2)((a, b) => a.flatMap(a => b.map(a + _))).runCollect.map(_ must_=== List(2, 4))
  }

  private def zipWithIndex =
    prop(
      (s: Stream[String, Byte]) =>
        unsafeRunSync(s.zipWithIndex.runCollect) must_=== unsafeRunSync(s.runCollect.map(_.zipWithIndex))
    )

  private def zipWithIgnoreRhs = unsafeRun {
    val s1 = Stream(1, 2, 3)
    val s2 = Stream(1, 2)
    s1.zipWith(s2)((a, _) => a).runCollect.map(_ must_=== List(1, 2, 3))
  }

  private def zipWithPrioritizesFailure = unsafeRun {
    Stream.never
      .zipWith(Stream.fail("Ouch"))((_, _) => None)
      .runCollect
      .either
      .map(_ must_=== Left("Ouch"))
  }

  private def zipWithLatest = nonFlaky {
    import zio.test.mock.MockClock

    for {
      clock <- MockClock.make(MockClock.DefaultData)
      s1    = Stream.iterate(0)(_ + 1).fixed(100.millis).provide(clock)
      s2    = Stream.iterate(0)(_ + 1).fixed(70.millis).provide(clock)
      s3    = s1.zipWithLatest(s2)((_, _))
      q     <- Queue.unbounded[(Int, Int)]
      _     <- s3.foreach(q.offer).fork
      a     <- q.take
      _     <- clock.clock.setTime(70.millis)
      b     <- q.take
      _     <- clock.clock.setTime(100.millis)
      c     <- q.take
      _     <- clock.clock.setTime(140.millis)
      d     <- q.take
      _     <- clock.clock.setTime(210.millis)
    } yield List(a, b, c, d) must_=== List(0 -> 0, 0 -> 1, 1 -> 1, 1 -> 2)
  }

  private def interleave = unsafeRun {
    val s1 = Stream(2, 3)
    val s2 = Stream(5, 6, 7)

    s1.interleave(s2).runCollect.map(_ must_=== List(2, 5, 3, 6, 7))
  }

  private def interleaveWith =
    prop { (b: Stream[String, Boolean], s1: Stream[String, Int], s2: Stream[String, Int]) =>
      def interleave(b: List[Boolean], s1: => List[Int], s2: => List[Int]): List[Int] =
        b.headOption.map { hd =>
          if (hd) s1 match {
            case h :: t =>
              h :: interleave(b.tail, t, s2)
            case _ =>
              if (s2.isEmpty) List.empty
              else interleave(b.tail, List.empty, s2)
          } else
            s2 match {
              case h :: t =>
                h :: interleave(b.tail, s1, t)
              case _ =>
                if (s1.isEmpty) List.empty
                else interleave(b.tail, s1, List.empty)
            }
        }.getOrElse(List.empty)
      val interleavedStream = unsafeRunSync(s1.interleaveWith(s2)(b).runCollect)
      val interleavedLists = for {
        b  <- unsafeRunSync(b.runCollect)
        s1 <- unsafeRunSync(s1.runCollect)
        s2 <- unsafeRunSync(s2.runCollect)
      } yield interleave(b, s1, s2)
      (!interleavedLists.succeeded) || (interleavedStream must_=== interleavedLists)
    }

  @silent("Any")
  def toInputStream = {
    val stream                                    = Stream(1, 2, 3).map(_.toByte)
    val streamResult: Exit[Throwable, List[Byte]] = unsafeRunSync(stream.runCollect)
    val inputStreamResult = unsafeRunSync(stream.toInputStream.use { inputStream =>
      ZIO.succeed(
        Iterator
          .continually(inputStream.read)
          .takeWhile(_ != -1)
          .map(_.toByte)
          .toList
      )
    })
    streamResult must_=== inputStreamResult
  }
}
