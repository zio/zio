package zio.stream

import org.specs2.ScalaCheck

import scala.{ Stream => _ }
import zio._
import zio.duration._

import zio.QueueSpec.waitForSize

class StreamSpec(implicit ee: org.specs2.concurrent.ExecutionEnv)
    extends TestRuntime
    with StreamTestUtils
    with GenIO
    with ScalaCheck {

  import ArbitraryChunk._

  def is = "StreamSpec".title ^ s2"""
  Stream monad laws
    left identity           $monadLaw1
    right identity          $monadLaw2
    associativity           $monadLaw3

  Stream stack safety
    deep flatMap            $deepFlatMap

  Stream.++                 $concat

  Stream bracketing
    bracket                              $bracket
    bracket short circuits               $bracketShortCircuits
    no acquisition when short circuiting $bracketNoAcquisition

  Stream.collect            $collect
  Stream.collectWhile
    collectWhile                $collectWhile
    collectWhile short circuits $collectWhileShortCircuits

  Stream.drain              $drain

  Stream.dropWhile
    dropWhile         $dropWhile
    short circuits    $dropWhileShortCircuiting

  Stream.filter
    filter            $filter
    short circuits #1 $filterShortCircuiting1
    short circuits #2 $filterShortCircuiting2

  Stream.filterM
    filterM           $filterM
    short circuits #1 $filterMShortCircuiting1
    short circuits #2 $filterMShortCircuiting2

  Stream.flatMapPar/flattenPar/mergeAll
    consistent with flatMap     $flatMapParConsistency
    short circuiting            $flatMapParShortCircuiting
    interruption propagation    $flatMapParInterruptionPropagation
    errors interrupt all fibers $flatMapParErrorsInterruptAllFibers
    finalizer ordering          $flatMapParFinalizerOrdering

  Stream.foreach0           $foreach0
  Stream.foreach            $foreach
  Stream.forever            $forever
  Stream.fromChunk          $fromChunk
  Stream.fromInputStream    $fromInputStream
  Stream.fromIterable       $fromIterable
  Stream.fromQueue          $fromQueue
  Stream.map                $map
  Stream.mapConcat          $mapConcat
  Stream.mapAccum           $mapAccum
  Stream.mapAccumM          $mapAccumM

  Stream merging
    merge                         $merge
    mergeEither                   $mergeEither
    mergeWith                     $mergeWith
    mergeWith short circuit       $mergeWithShortCircuit
    mergeWith prioritizes failure $mergeWithPrioritizesFailure

  Stream.peel               $peel
  Stream.range              $range

  Stream.repeat
    repeat                  $repeat
    short circuits          $repeatShortCircuits

  Stream.spaced
    spaced                  $spaced
    short circuits          $spacedShortCircuits

  Stream.take
    take                     $take
    take short circuits      $takeShortCircuits
    take(0) short circuits   $take0ShortCircuitsStreamNever
    take(1) short circuits   $take1ShortCircuitsStreamNever
    takeWhile                $takeWhile
    takeWhile short circuits $takeWhileShortCircuits

  Stream.tap                $tap
  Stream.toQueue            $toQueue

  Stream.transduce
    transduce                            $transduce
    no remainder                         $transduceNoRemainder
    with remainder                       $transduceWithRemainder
    with a sink that always signals more $transduceSinkMore

  Stream.unfold             $unfold
  Stream.unfoldM            $unfoldM

  Stream zipping
    zipWith                     $zipWith
    zipWithIndex                $zipWithIndex
    zipWith ignore RHS          $zipWithIgnoreRhs
    zipWith prioritizes failure $zipWithPrioritizesFailure

  Stream combinators
    unTake happy path       $unTake
    unTake with error       $unTakeError
    buffer the Stream                      $bufferStream
    buffer the Stream with Error           $bufferStreamError
    fast producer progress independently   $fastProducerSlowConsumer
  """

  import ArbitraryStream._
  import Exit._

  //in scala 2.11 the proof for Any in not found by the compiler
  import Stream.ConformsAnyProof

  private def filter =
    prop { (s: Stream[String, Byte], p: Byte => Boolean) =>
      slurp(s.filter(p)) must_=== slurp(s).map(_.filter(p))
    }

  private def filterShortCircuiting1 = unsafeRun {
    (Stream(1) ++ Stream.fail("Ouch"))
      .filter(_ => true)
      .take(1)
      .runDrain
      .either
      .map(_ must beRight(()))
  }

  private def filterShortCircuiting2 = unsafeRun {
    (Stream(1) ++ Stream.fail("Ouch"))
      .take(1)
      .filter(_ => true)
      .runDrain
      .either
      .map(_ must beRight(()))
  }

  private def filterM =
    prop { (s: Stream[String, Byte], p: Byte => Boolean) =>
      slurp(s.filterM(s => IO.succeed(p(s)))) must_=== slurp(s).map(_.filter(p))
    }

  private def filterMShortCircuiting1 = unsafeRun {
    (Stream(1) ++ Stream.fail("Ouch"))
      .take(1)
      .filterM(_ => UIO.succeed(true))
      .runDrain
      .either
      .map(_ must beRight(()))
  }

  private def filterMShortCircuiting2 = unsafeRun {
    (Stream(1) ++ Stream.fail("Ouch"))
      .filterM(_ => UIO.succeed(true))
      .take(1)
      .runDrain
      .either
      .map(_ must beRight(()))
  }

  private def dropWhile =
    prop { (s: Stream[String, Byte], p: Byte => Boolean) =>
      slurp(s.dropWhile(p)) must_=== slurp(s).map(_.dropWhile(p))
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

  private def takeWhile =
    prop { (s: Stream[String, Byte], p: Byte => Boolean) =>
      val streamTakeWhile = slurp(s.takeWhile(p))
      val listTakeWhile   = slurp(s).map(_.takeWhile(p))
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

  private def collectWhile = {
    val s = Stream(Some(1), Some(2), Some(3), None, Some(4)).collectWhile { case Some(v) => v }
    slurp(s) must_=== Success(List(1, 2, 3))
  }

  private def collectWhileShortCircuits =
    unsafeRun {
      (Stream(Option(1)) ++ Stream.fail("Ouch")).collectWhile {
        case None => 1
      }.runDrain.either
        .map(_ must beRight(()))
    }

  private def map =
    prop { (s: Stream[String, Byte], f: Byte => Int) =>
      slurp(s.map(f)) must_=== slurp(s).map(_.map(f))
    }

  private def concat =
    prop { (s1: Stream[String, Byte], s2: Stream[String, Byte]) =>
      val listConcat = (slurp(s1) zip slurp(s2)).map {
        case (left, right) => left ++ right
      }
      val streamConcat = slurp(s1 ++ s2)
      (streamConcat.succeeded && listConcat.succeeded) ==> (streamConcat must_=== listConcat)
    }

  private def mapConcat = {
    import ArbitraryChunk._
    prop { (s: Stream[String, Byte], f: Byte => Chunk[Int]) =>
      slurp(s.mapConcat(f)) must_=== slurp(s).map(_.flatMap(v => f(v).toSeq))
    }
  }

  private def mapAccum = {
    val stream = Stream(1, 1, 1).mapAccum(0)((acc, el) => (acc + el, acc + el))
    slurp(stream) must_=== Success(List(1, 2, 3))
  }

  private def mapAccumM = {
    val stream = Stream(1, 1, 1).mapAccumM(0)((acc, el) => IO.succeed((acc + el, acc + el)))
    (slurp(stream) must_=== Success(List(1, 2, 3))) and (slurp(stream) must_=== Success(List(1, 2, 3)))
  }

  private def unfold = {
    val s = Stream.unfold(0)(i => if (i < 10) Some((i, i + 1)) else None)
    slurp(s) must_=== Success((0 to 9).toList) and (slurp(s) must_=== Success((0 to 9).toList))
  }

  private def unfoldM = {
    val s = Stream.unfoldM(0)(i => if (i < 10) IO.succeed(Some((i, i + 1))) else IO.succeed(None))
    slurp(s) must_=== Success((0 to 9).toList) and (slurp(s) must_=== Success((0 to 9).toList))
  }

  private def range = {
    val s = Stream.range(0, 9)
    slurp(s) must_=== Success((0 to 9).toList) and (slurp(s) must_=== Success((0 to 9).toList))
  }

  private def take =
    prop { (s: Stream[String, Byte], n: Int) =>
      val takeStreamesult = slurp(s.take(n))
      val takeListResult  = slurp(s).map(_.take(n))
      (takeListResult.succeeded ==> (takeStreamesult must_=== takeListResult))
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
        units <- Stream.never.take(0).run(Sink.collect[Unit])
      } yield units must_=== List()
    )

  private def take1ShortCircuitsStreamNever =
    unsafeRun(
      for {
        ints <- (Stream(1) ++ Stream.never).take(1).run(Sink.collect[Int])
      } yield ints must_=== List(1)
    )

  private def foreach0 = {
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

  private def foreach = {
    var sum = 0
    val s   = Stream(1, 1, 1, 1, 1)

    unsafeRun(s.foreach[Any, Nothing](a => IO.effectTotal(sum += a)))
    sum must_=== 5
  }

  private def collect = {
    val s = Stream(Left(1), Right(2), Left(3)).collect {
      case Right(n) => n
    }

    slurp(s) must_=== Success(List(2)) and (slurp(s) must_=== Success(List(2)))
  }

  private def monadLaw1 =
    prop((x: Int, f: Int => Stream[String, Int]) => slurp(Stream(x).flatMap(f)) must_=== slurp(f(x)))

  private def monadLaw2 =
    prop((m: Stream[String, Int]) => slurp(m.flatMap(i => Stream(i))) must_=== slurp(m))

  private def monadLaw3 =
    prop { (m: Stream[String, Int], f: Int => Stream[String, Int], g: Int => Stream[String, Int]) =>
      val leftStream  = m.flatMap(f).flatMap(g)
      val rightStream = m.flatMap(x => f(x).flatMap(g))
      slurp(leftStream) must_=== slurp(rightStream)
    }

  private def deepFlatMap = {
    def fib(n: Int): Stream[Nothing, Int] =
      if (n <= 1) Stream.succeedLazy(n)
      else
        fib(n - 1).flatMap { a =>
          fib(n - 2).flatMap { b =>
            Stream.succeedLazy(a + b)
          }
        }

    val stream   = fib(20)
    val expected = 6765

    slurp(stream).toEither must beRight(List(expected))
  }

  private def flatMapParConsistency = prop { (n: Long, m: List[Int]) =>
    val flatMap    = Stream.fromIterable(m).flatMap(i => Stream(i, i))
    val flatMapPar = Stream.fromIterable(m).flatMapPar(n)(i => Stream(i, i))

    (n > 0) ==> (slurp(flatMap).map(_.toSet) must_=== slurp(flatMapPar).map(_.toSet))
  }

  private def flatMapParShortCircuiting = unsafeRun {
    Stream
      .mergeAll(2)(
        Stream.never,
        Stream(1)
      )
      .take(1)
      .run(Sink.collect[Int])
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
                .run(Sink.collect[Unit])
                .fork
      _         <- latch.await
      _         <- fiber.interrupt
      cancelled <- substreamCancelled.get
    } yield cancelled must_=== true
  }

  private def flatMapParErrorsInterruptAllFibers = unsafeRun {
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
            .bracket(execution.update("OuterAcquire" :: _).const(inner))(_ => execution.update("OuterRelease" :: _))
            .flatMapPar(2)(identity)
            .runDrain
      results <- execution.get
    } yield results must_=== List("OuterRelease", "InnerRelease", "InnerAcquire", "OuterAcquire")
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

  private def merge =
    prop { (s1: Stream[String, Int], s2: Stream[String, Int]) =>
      val mergedStream = slurp(s1 merge s2).map(_.toSet)
      val mergedLists  = (slurp(s1) zip slurp(s2)).map { case (left, right) => left ++ right }.map(_.toSet)
      (!mergedStream.succeeded && !mergedLists.succeeded) || (mergedStream must_=== mergedLists)
    }

  private def mergeEither = {
    val s1 = Stream(1, 2)
    val s2 = Stream(1, 2)

    val merge = s1.mergeEither(s2)
    val list: List[Either[Int, Int]] = slurp(merge).toEither.fold(
      _ => List.empty,
      identity
    )

    list must containTheSameElementsAs(List(Left(1), Left(2), Right(1), Right(2)))
  }

  private def mergeWith = {
    val s1 = Stream(1, 2)
    val s2 = Stream(1, 2)

    val merge = s1.mergeWith(s2)(_.toString, _.toString)
    val list: List[String] = slurp(merge).toEither.fold(
      _ => List.empty,
      identity
    )

    list must containTheSameElementsAs(List("1", "2", "1", "2"))
  }

  private def mergeWithShortCircuit = {
    val s1 = Stream(1, 2)
    val s2 = Stream(1, 2)

    val merge = s1.mergeWith(s2)(_.toString, _.toString)
    val list: List[String] = slurp0(merge)(_ => false).toEither.fold(
      _ => List("9"),
      identity
    )

    list must_=== List()
  }

  private def mergeWithPrioritizesFailure = unsafeRun {
    val s1 = Stream.never
    val s2 = Stream.fail("Ouch")

    s1.mergeWith(s2)(_ => (), _ => ())
      .runCollect
      .either
      .map(_ must_=== Left("Ouch"))
  }

  private def transduce = {
    val s          = Stream('1', '2', ',', '3', '4')
    val parser     = ZSink.readWhile[Char](_.isDigit).map(_.mkString.toInt) <* ZSink.readWhile(_ == ',')
    val transduced = s.transduce(parser)

    slurp(transduced) must_=== Success(List(12, 34))
  }

  private def transduceNoRemainder = {
    val sink = Sink.fold(100) { (s, a: Int) =>
      if (a % 2 == 0)
        ZSink.Step.more(s + a)
      else
        ZSink.Step.done(s + a, Chunk.empty)
    }
    val transduced = ZStream(1, 2, 3, 4).transduce(sink)

    slurp(transduced) must_=== Success(List(101, 105, 104))
  }

  private def transduceWithRemainder = {
    val sink = Sink.fold(0) { (s, a: Int) =>
      a match {
        case 1 => ZSink.Step.more(s + 100)
        case 2 => ZSink.Step.more(s + 100)
        case 3 => ZSink.Step.done(s + 3, Chunk(a + 1))
        case _ => ZSink.Step.done(s + 4, Chunk.empty)
      }
    }
    val transduced = ZStream(1, 2, 3).transduce(sink)

    slurp(transduced) must_=== Success(List(203, 4))
  }

  private def transduceSinkMore = {
    val sink = Sink.fold(0) { (s, a: Int) =>
      ZSink.Step.more(s + a)
    }
    val transduced = ZStream(1, 2, 3).transduce(sink)

    slurp(transduced) must_=== Success(List(1 + 2 + 3))
  }

  private def peel = {
    val s      = Stream('1', '2', ',', '3', '4')
    val parser = ZSink.readWhile[Char](_.isDigit).map(_.mkString.toInt) <* ZSink.readWhile(_ == ',')
    val peeled = s.peel(parser).use[Any, Int, (Int, Exit[Nothing, List[Char]])] {
      case (n, rest) =>
        IO.succeed((n, slurp(rest)))
    }

    unsafeRun(peeled) must_=== ((12, Success(List('3', '4'))))
  }

  private def tap = {
    var sum     = 0
    val s       = Stream(1, 1).tap[Any, Nothing](a => IO.effectTotal(sum += a))
    val slurped = slurp(s)

    (slurped must_=== Success(List(1, 1))) and (sum must_=== 2)
  }

  private def zipWith = {
    val s1     = Stream(1, 2, 3)
    val s2     = Stream(1, 2)
    val zipped = s1.zipWith(s2)((a, b) => a.flatMap(a => b.map(a + _)))

    slurp(zipped) must_=== Success(List(2, 4))
  }

  private def zipWithIndex =
    prop((s: Stream[String, Byte]) => slurp(s.zipWithIndex) must_=== slurp(s).map(_.zipWithIndex))

  private def zipWithIgnoreRhs = {
    val s1     = Stream(1, 2, 3)
    val s2     = Stream(1, 2)
    val zipped = s1.zipWith(s2)((a, _) => a)

    slurp(zipped) must_=== Success(List(1, 2, 3))
  }

  private def zipWithPrioritizesFailure = unsafeRun {
    Stream.never
      .zipWith(Stream.fail("Ouch"))((_, _) => None)
      .runCollect
      .either
      .map(_ must_=== Left("Ouch"))
  }

  private def fromIterable = prop { l: List[Int] =>
    val s = Stream.fromIterable(l)
    slurp(s) must_=== Success(l) and (slurp(s) must_=== Success(l))
  }

  private def fromChunk = prop { c: Chunk[Int] =>
    val s = Stream.fromChunk(c)
    (slurp(s) must_=== Success(c.toSeq.toList)) and (slurp(s) must_=== Success(c.toSeq.toList))
  }

  private def fromQueue = prop { c: Chunk[Int] =>
    val result = unsafeRunSync {
      for {
        queue <- Queue.unbounded[Int]
        _     <- queue.offerAll(c.toSeq)
        fiber <- Stream
                  .fromQueue(queue)
                  .fold[Any, Nothing, Int, List[Int]]
                  .flatMap { fold =>
                    fold(List[Int](), _ => true, (acc, el) => IO.succeed(el :: acc))
                  }
                  .use(ZIO.succeed)
                  .map(_.reverse)
                  .fork
        _     <- waitForSize(queue, -1)
        _     <- queue.shutdown
        items <- fiber.join
      } yield items
    }
    result must_=== Success(c.toSeq.toList)
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

  private def drain =
    unsafeRun(
      for {
        ref <- Ref.make(List[Int]())
        _   <- Stream.range(0, 10).mapM(i => ref.update(i :: _)).drain.run(Sink.drain)
        l   <- ref.get
      } yield l.reverse must_=== (0 to 10).toList
    )

  private def bracket =
    unsafeRun(
      for {
        done           <- Ref.make(false)
        iteratorStream = Stream.bracket(UIO(0 to 2))(_ => done.set(true)).flatMap(Stream.fromIterable)
        result         <- iteratorStream.run(Sink.collect[Int])
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
        result   <- iteratorStream.run(Sink.collect[Int])
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

  private def unTake =
    unsafeRun(
      Stream
        .range(0, 10)
        .toQueue[Nothing, Int](1)
        .use { q =>
          Stream.fromQueue(q).unTake.run(Sink.collect[Int])
        }
        .map(_ must_=== (0 to 10).toList)
    )

  private def unTakeError = {
    val e = new RuntimeException("boom")
    unsafeRunSync(
      (Stream.range(0, 10) ++ Stream.fail(e))
        .toQueue[Throwable, Int](1)
        .use { q =>
          Stream.fromQueue(q).unTake.run(Sink.collect[Int])
        }
    ) must_== Failure(Cause.fail(e))
  }

  private def bufferStream = prop { list: List[Int] =>
    unsafeRunSync(
      Stream
        .fromIterable(list)
        .buffer(2)
        .run(Sink.collect[Int])
    ) must_== (Success(list))
  }

  private def bufferStreamError = {
    val e = new RuntimeException("boom")
    unsafeRunSync(
      (Stream.range(0, 10) ++ Stream.fail(e))
        .buffer(2)
        .run(Sink.collect[Int])
    ) must_== Failure(Cause.Fail(e))
  }

  private def fastProducerSlowConsumer =
    // TODO: flaky
    unsafeRun(
      for {
        promise <- Promise.make[Nothing, Unit]
        ref     <- Ref.make(List[Int]())
        _ <- Stream
              .range(1, 4)
              .mapM(i => ref.update(i :: _) <* promise.succeed(()))
              .buffer(2)
              .mapM(_ => IO.never)
              .run(Sink.drain)
              .fork
        _    <- promise.await
        list <- ref.get
      } yield {
        list.reverse must_=== (1 to 4).toList
      }
    )

  private def repeat =
    unsafeRun(
      Stream(1)
        .repeat(Schedule.recurs(4))
        .run(Sink.collect[Int])
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

  private def spaced =
    unsafeRun(
      Stream(1, 2, 3)
        .spaced(Schedule.recurs(1))
        .run(Sink.collect[Int])
        .map(_ must_=== List(1, 1, 2, 2, 3, 3))
    )

  private def spacedShortCircuits =
    unsafeRun(
      Stream(1, 2, 3)
        .spaced(Schedule.recurs(1))
        .take(3)
        .run(Sink.collect[Int])
        .map(_ must_=== List(1, 1, 2))
    )

  private def fromInputStream = unsafeRun {
    import java.io.ByteArrayInputStream

    val data = List.fill(4096)("0123456789").mkString.getBytes
    val is   = new ByteArrayInputStream(data)

    ZStream.fromInputStream(is).run(Sink.collect[Chunk[Byte]]) map { chunks =>
      chunks.flatMap(_.toArray[Byte]).toArray must_=== data
    }
  }
}
