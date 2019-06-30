package zio

import java.util.concurrent.CountDownLatch

import org.scalacheck.{ Gen, _ }

import org.specs2.ScalaCheck
import org.specs2.matcher.MatchResult
import org.specs2.matcher.describe.Diffable
import zio.Cause.Interrupt
import zio.Exit.Failure
import zio.duration._

import scala.collection.mutable

class ZManagedSpec(implicit ee: org.specs2.concurrent.ExecutionEnv) extends TestRuntime with GenIO with ScalaCheck {
  import Prop.forAll

  def is = "ZManagedSpec".title ^ s2"""
  ZManaged.make
    Invokes cleanups in reverse order of acquisition. $invokesCleanupsInReverse
    Properly performs parallel acquire and release. $parallelAcquireAndRelease
    Constructs an uninterruptible Managed value. $uninterruptible
  ZManaged.reserve
    Interruption is possible when using this form. $interruptible
  ZManaged.ensuring
    Runs on successes $ensuringSuccess
    Runs on failures $ensuringFailure
    Works when finalizers have defects $ensuringWorksWithDefects
  ZManaged.flatMap
    All finalizers run even when finalizers have defects $flatMapFinalizersWithDefects
  ZManaged.foldM
    Runs onFailure on failure $foldMFailure
    Runs onSuccess on success $foldMSuccess
    Invokes cleanups $foldMCleanup
    Invokes cleanups on interrupt 1 $foldMCleanupInterrupt1
    Invokes cleanups on interrupt 2 $foldMCleanupInterrupt2
    Invokes cleanups on interrupt 3 $foldMCleanupInterrupt3
  ZManaged.foreach
    Returns elements in the correct order $foreachOrder
    Runs finalizers $foreachFinalizers
    Invokes cleanups in reverse order of acquisition. $foreachFinalizerOrder
  ZManaged.foreachPar
    Returns elements in the correct order $foreachParOrder
    Runs finalizers $foreachParFinalizers
    Runs reservations in parallel $foreachParReservePar
    Runs acquisitions in parallel $foreachParAcquirePar
  ZManaged.foreachParN
    Returns elements in the correct order $foreachParNOrder
    Uses at most n fibers for reservation $foreachParNReservePar
    Uses at most n fibers for acquisition $foreachParNAcquirePar
    Runs finalizers $foreachParNFinalizers
  ZManaged.foreach_
    Runs finalizers $foreach_Finalizers
  ZManaged.foreachPar_
    Runs finalizers $foreachPar_Finalizers
    Runs reservations in parallel $foreachPar_ReservePar
    Runs acquisitions in parallel $foreachPar_AcquirePar
  ZManaged.foreachParN_
    Uses at most n fibers for reservation $foreachParN_ReservePar
    Uses at most n fibers for acquisition $foreachParN_AcquirePar
    Runs finalizers $foreachParN_Finalizers
  ZManaged.fork
    Runs finalizers properly $forkFinalizer
    Acquires interruptibly   $forkAcquisitionIsInterruptible
  ZManaged.fromAutoCloseable
    Runs finalizers properly $fromAutoCloseable
  ZManaged.mergeAll
    Merges elements in the correct order $mergeAllOrder
    Runs finalizers $mergeAllFinalizers
  ZManaged.mergeAllPar
    Merges elements in the correct order $mergeAllParOrder
    Runs reservations in parallel $mergeAllParReservePar
    Runs acquisitions in parallel $mergeAllParAcquirePar
    Runs finalizers $mergeAllParFinalizers
  ZManaged.mergeAllParN
    Merges elements in the correct order $mergeAllParNOrder
    Uses at most n fibers for reservation $mergeAllParNReservePar
    Uses at most n fibers for acquisition $mergeAllParNAcquirePar
    Runs finalizers $mergeAllParNFinalizers
  ZManaged.reduceAll
    Reduces elements in the correct order $reduceAllOrder
    Runs finalizers $reduceAllFinalizers
  ZManaged.reduceAllPar
    Reduces elements in the correct order $reduceAllParOrder
    Runs reservations in parallel $reduceAllParReservePar
    Runs acquisitions in parallel $reduceAllParAcquirePar
    Runs finalizers $reduceAllParFinalizers
  ZManaged.reduceAllParN
    Reduces elements in the correct order $reduceAllParNOrder
    Uses at most n fibers for reservation $reduceAllParNReservePar
    Uses at most n fibers for acquisition $reduceAllParNAcquirePar
    Runs finalizers $reduceAllParNFinalizers
  ZManged.retry
    Should retry the reservation $retryReservation
    Should retry the acquisition $retryAcquisition
    Should share retries between both $retryBoth
  ZManaged.timed
    Should time both the reservation and the acquisition $timed
  ZManaged.timeout
    Returns Some if the timeout isn't reached $timeoutHappy
    Returns None if the reservation takes longer than d $timeoutReservation
    Returns None if the acquisition takes longer than d $timeoutAcquisition
    Runs finalizers if returning None and reservation is successful $timeoutRunFinalizers1
    Runs finalizers if returning None and reservation is successful after timeout $timeoutRunFinalizers2|
  ZManaged.zipPar
    Does not swallow exit cause if one reservation fails $zipParFailReservation
    Runs finalizers if one acquisition fails $zipParFailAcquisitionRelease
    Does not swallow acquisition if one acquisition fails $zipParFailAcquisition
    Run finalizers if one reservation fails $zipParFailReservationRelease
  Check flatten returns the same as ZManaged.flatten $testFlatten
  Check absolve returns the same as ZManaged.absolve $testAbsolve
  """

  private def invokesCleanupsInReverse = {
    val effects = new mutable.ListBuffer[Int]
    def res(x: Int) =
      ZManaged.make(IO.effectTotal { effects += x; () })(_ => IO.effectTotal { effects += x; () })

    val (first, second, third) = (res(1), res(2), res(3))

    val composed = for {
      _ <- first
      _ <- second
      _ <- third
    } yield ()

    val program = composed.use[Any, Nothing, Unit](_ => IO.unit)

    unsafeRun(program)

    effects must be_===(List(1, 2, 3, 3, 2, 1))
  }

  private def parallelAcquireAndRelease = unsafeRun {
    for {
      log      <- Ref.make[List[String]](Nil)
      a        = ZManaged.make(UIO.succeed("A"))(_ => log.update("A" :: _))
      b        = ZManaged.make(UIO.succeed("B"))(_ => log.update("B" :: _))
      result   <- a.zipWithPar(b)(_ + _).use(ZIO.succeed)
      cleanups <- log.get
    } yield (result must haveSize(2)) and (cleanups must haveSize(2))
  }

  private def uninterruptible =
    doInterrupt(io => ZManaged.make(io)(_ => IO.unit), None)

  // unlike make, reserve allows interruption
  private def interruptible =
    doInterrupt(io => ZManaged.reserve(Reservation(io, IO.unit)), Some(Failure(Interrupt)))

  private def doInterrupt(
    managed: IO[Nothing, Unit] => ZManaged[Any, Nothing, Unit],
    expected: Option[Exit[Nothing, Unit]]
  ) = {
    val program = for {
      never              <- Promise.make[Nothing, Unit]
      reachedAcquisition <- Promise.make[Nothing, Unit]
      managedFiber       <- managed(reachedAcquisition.succeed(()) *> never.await).use_(IO.unit).fork
      _                  <- reachedAcquisition.await
      interruption       <- managedFiber.interrupt.timeout(5.seconds).either
    } yield interruption

    implicit val d: Diffable[Right[Nothing, Option[Exit[Nothing, Unit]]]] =
      Diffable.eitherRightDiffable[Option[Exit[Nothing, Unit]]] //    TODO: Dotty has ambiguous implicits
    unsafeRun(program) must be_===(Right(expected))
  }

  private def ensuringSuccess = unsafeRun {
    for {
      effects <- Ref.make[List[String]](Nil)
      _       <- ZManaged.finalizer(effects.update("First" :: _)).ensuring(effects.update("Second" :: _)).use_(ZIO.unit)
      result  <- effects.get
    } yield result must_=== List("Second", "First")
  }

  private def ensuringFailure = unsafeRun {
    for {
      effects <- Ref.make[List[String]](Nil)
      _       <- ZManaged.fromEffect(ZIO.fail(())).ensuring(effects.update("Ensured" :: _)).use_(ZIO.unit).either
      result  <- effects.get
    } yield result must_=== List("Ensured")
  }

  private def ensuringWorksWithDefects = unsafeRun {
    for {
      effects <- Ref.make[List[String]](Nil)
      _       <- ZManaged.finalizer(ZIO.dieMessage("Boom")).ensuring(effects.update("Ensured" :: _)).use_(ZIO.unit).run
      result  <- effects.get
    } yield result must_=== List("Ensured")
  }

  private def flatMapFinalizersWithDefects = unsafeRun {
    for {
      effects <- Ref.make[List[String]](Nil)
      _ <- (for {
            _ <- ZManaged.finalizer(ZIO.dieMessage("Boom"))
            _ <- ZManaged.finalizer(effects.update("First" :: _))
            _ <- ZManaged.finalizer(ZIO.dieMessage("Boom"))
            _ <- ZManaged.finalizer(effects.update("Second" :: _))
            _ <- ZManaged.finalizer(ZIO.dieMessage("Boom"))
            _ <- ZManaged.finalizer(effects.update("Third" :: _))
          } yield ()).use_(ZIO.unit).run
      result <- effects.get
    } yield (result must_=== List("First", "Second", "Third"))
  }

  private def foldMFailure = {
    val effects = new mutable.ListBuffer[Int]
    def res(x: Int): ZManaged[Any, Unit, Unit] =
      ZManaged.make(IO.effectTotal { effects += x; () })(_ => IO.effectTotal { effects += x; () })

    val resource = ZManaged.fromEffect(ZIO.fail(())).foldM(_ => res(1), _ => ZManaged.unit)

    unsafeRun(resource.use(_ => IO.unit))

    effects must be_===(List(1, 1))
  }

  private def foldMSuccess = {
    val effects = new mutable.ListBuffer[Int]
    def res(x: Int): ZManaged[Any, Unit, Unit] =
      ZManaged.make(IO.effectTotal { effects += x; () })(_ => IO.effectTotal { effects += x; () })

    val resource = ZManaged.succeed(()).foldM(_ => ZManaged.unit, _ => res(1))

    unsafeRun(resource.use(_ => IO.unit))

    effects must be_===(List(1, 1))
  }

  private def foldMCleanup = {
    val effects = new mutable.ListBuffer[Int]
    def res(x: Int): ZManaged[Any, Unit, Unit] =
      ZManaged.make(IO.effectTotal { effects += x; () })(_ => IO.effectTotal { effects += x; () })

    val resource = res(1).flatMap(_ => ZManaged.fail(())).foldM(_ => res(2), _ => res(3))

    unsafeRun(resource.use(_ => IO.unit).orElse(ZIO.unit))

    effects must be_===(List(1, 2, 2, 1))
  }

  private def foldMCleanupInterrupt1 = {
    val effects = new mutable.ListBuffer[Int]
    def res(x: Int): ZManaged[Any, Unit, Unit] =
      ZManaged.make(IO.effectTotal { effects += x; () })(_ => IO.effectTotal { effects += x; () })

    val resource = res(1).flatMap(_ => ZManaged.fromEffect(ZIO.interrupt)).foldM(_ => res(2), _ => res(3))

    unsafeRun(resource.use(_ => IO.unit).orElse(ZIO.unit))

    effects must be_===(List(1, 1))
  }

  private def foldMCleanupInterrupt2 = {
    val effects = new mutable.ListBuffer[Int]
    def res(x: Int): ZManaged[Any, Unit, Unit] =
      ZManaged.make(IO.effectTotal { effects += x; () })(_ => IO.effectTotal { effects += x; () })

    val resource = res(1).flatMap(_ => ZManaged.fail(())).foldM(_ => res(2), _ => res(3))

    unsafeRun(resource.use(_ => IO.interrupt.unit).orElse(ZIO.unit))

    effects must be_===(List(1, 2, 2, 1))
  }

  private def foldMCleanupInterrupt3 = {
    val effects = new mutable.ListBuffer[Int]
    def res(x: Int): ZManaged[Any, Unit, Unit] =
      ZManaged.make(IO.effectTotal { effects += x; () })(_ => IO.effectTotal { effects += x; () })

    val resource = res(1)
      .flatMap(_ => ZManaged.fail(()))
      .foldM(_ => res(2).flatMap(_ => ZManaged.fromEffect(ZIO.interrupt)), _ => res(3))

    unsafeRun(resource.use(_ => IO.unit).orElse(ZIO.unit))

    effects must be_===(List(1, 2, 2, 1))
  }

  private def fromAutoCloseable =
    unsafeRun {
      for {
        runtime <- ZIO.runtime[Any]
        effects <- Ref.make(List[String]())
        closeable = UIO(new AutoCloseable {
          def close(): Unit = runtime.unsafeRun(effects.update("Closed" :: _).unit)
        })
        _      <- ZManaged.fromAutoCloseable(closeable).use_(ZIO.unit)
        result <- effects.get
      } yield result must_=== List("Closed")
    }

  private def retryReservation = {
    var retries = 0

    val managed = ZManaged.make(ZIO.effectTotal(retries += 1) *> { if (retries == 3) ZIO.unit else ZIO.fail(()) }) {
      _ =>
        ZIO.unit
    }

    unsafeRun(managed.retry(Schedule.recurs(3)).use(_ => ZIO.unit))
    retries must be_===(3)
  }

  private def retryAcquisition = {
    var retries = 0

    val managed = ZManaged.reserve(Reservation(ZIO.effectTotal(retries += 1) *> {
      if (retries == 3) ZIO.unit else ZIO.fail(())
    }, ZIO.unit))

    unsafeRun(managed.retry(Schedule.recurs(3)).use(_ => ZIO.unit))
    retries must be_===(3)
  }

  private def retryBoth = {
    var retries1 = 0
    var retries2 = 0

    val managed = ZManaged {

      ZIO.effectTotal(retries1 += 1) *> {
        if (retries1 < 3) ZIO.fail(())
        else
          ZIO.succeed {
            Reservation(ZIO.effectTotal(retries2 += 1) *> { if (retries2 == 3) ZIO.unit else ZIO.fail(()) }, ZIO.unit)
          }
      }

    }

    unsafeRun(managed.retry(Schedule.recurs(6)).use(_ => ZIO.unit))
    (retries1 must be_===(3)) && (retries2 must be_===(3))
  }

  private def zipParFailAcquisition =
    unsafeRunSync {
      for {
        latch  <- Promise.make[Nothing, Unit]
        first  = ZManaged.fromEffect(latch.succeed(()) *> ZIO.sleep(Duration.Infinity))
        second = ZManaged.reserve(Reservation(latch.await *> ZIO.fail(()), ZIO.unit))
        _      <- first.zipPar(second).use_(ZIO.unit)
      } yield ()
    } must be_===(Exit.Failure(Cause.Both(Cause.Fail(()), Cause.Interrupt)))

  private def zipParFailReservation =
    unsafeRunSync {
      for {
        latch  <- Promise.make[Nothing, Unit]
        first  = ZManaged.fromEffect(latch.succeed(()) *> ZIO.sleep(Duration.Infinity))
        second = ZManaged.fromEffect(latch.await *> ZIO.fail(()))
        _      <- first.zipPar(second).use_(ZIO.unit)
      } yield ()
    } must be_===(Exit.Failure(Cause.Both(Cause.Fail(()), Cause.Interrupt)))

  private def zipParFailAcquisitionRelease = {
    var releases = 0
    val first    = ZManaged.unit
    val second   = ZManaged.reserve(Reservation(ZIO.fail(()), ZIO.effectTotal(releases += 1)))

    unsafeRunSync(first.zipPar(second).use(_ => ZIO.unit))
    releases must be_===(1)
  }

  private def zipParFailReservationRelease =
    unsafeRun {
      for {
        reserveLatch <- Promise.make[Nothing, Unit]
        releases     <- Ref.make[Int](0)
        first        = ZManaged.reserve(Reservation(reserveLatch.succeed(()), releases.update(_ + 1)))
        second       = ZManaged.fromEffect(reserveLatch.await *> ZIO.fail(()))
        _            <- first.zipPar(second).use_(ZIO.unit).orElse(ZIO.unit)
        count        <- releases.get
      } yield count must be_===(1)
    }

  private def testFlatten =
    forAll(Gen.alphaStr) { str =>
      unsafeRun((for {
        flatten1 <- ZManaged.succeedLazy(ZManaged.succeedLazy(str)).flatten
        flatten2 <- ZManaged.flatten(ZManaged.succeedLazy(ZManaged.succeedLazy(str)))
      } yield flatten1 must ===(flatten2)).use[Any, Nothing, MatchResult[String]](result => ZIO.succeed(result)))
    }

  private def testAbsolve =
    forAll(Gen.alphaStr) { str =>
      val managedEither: ZManaged[Any, Nothing, Either[Nothing, String]] = ZManaged.succeed(Right(str))
      unsafeRun((for {
        abs1 <- managedEither.absolve
        abs2 <- ZManaged.absolve(managedEither)
      } yield abs1 must ===(abs2)).use[Any, Nothing, MatchResult[String]](result => ZIO.succeed(result)))
    }

  private def timeoutHappy =
    unsafeRun {
      val managed = ZManaged.make(ZIO.succeed(1))(_ => ZIO.unit)
      managed.timeout(Duration.Infinity).use(res => ZIO.succeed(res must_=== (Some(1))))
    }

  private def timeoutReservation =
    unsafeRun {
      for {
        latch   <- Promise.make[Nothing, Unit]
        managed = ZManaged.make(latch.await)(_ => ZIO.unit)
        res     <- managed.timeout(Duration.Zero).use(res => ZIO.succeed(res must_=== (None)))
        _       <- latch.succeed(())
      } yield res
    }

  private def timeoutAcquisition =
    unsafeRun {
      for {
        latch   <- Promise.make[Nothing, Unit]
        managed = ZManaged.reserve(Reservation(latch.await, ZIO.unit))
        res     <- managed.timeout(Duration.Zero).use(res => ZIO.succeed(res must_=== (None)))
        _       <- latch.succeed(())
      } yield res
    }

  private def timeoutRunFinalizers1 =
    unsafeRun {
      for {
        reserveLatch <- Promise.make[Nothing, Unit]
        releaseLatch <- Promise.make[Nothing, Unit]
        managed      = ZManaged.reserve(Reservation(reserveLatch.await, releaseLatch.succeed(())))
        res          <- managed.timeout(Duration.Zero).use(ZIO.succeed)
        _            <- reserveLatch.succeed(())
        _            <- releaseLatch.await
      } yield res must_=== (None)
    }

  private def timeoutRunFinalizers2 =
    unsafeRun {
      for {
        acquireLatch <- Promise.make[Nothing, Unit]
        releaseLatch <- Promise.make[Nothing, Unit]
        managed      = ZManaged(acquireLatch.await *> ZIO.succeed(Reservation(ZIO.unit, releaseLatch.succeed(()))))
        res          <- managed.timeout(Duration.Zero).use(ZIO.succeed)
        _            <- acquireLatch.succeed(())
        _            <- releaseLatch.await
      } yield res must_=== (None)
    }

  private def timed = {
    val managed = ZManaged(
      clock.sleep(20.milliseconds) *> ZIO.succeed(Reservation(clock.sleep(20.milliseconds), ZIO.unit))
    )
    unsafeRun {
      managed.timed.use { case (duration, _) => ZIO.succeed(duration must be_>=(40.milliseconds)) }
    }
  }

  private def foreachOrder = {
    def res(int: Int) =
      ZManaged.succeed(int)

    val managed = ZManaged.foreach(List(1, 2, 3, 4))(res)
    unsafeRun {
      managed.use[Any, Nothing, MatchResult[List[Int]]](res => ZIO.succeed(res must be_===(List(1, 2, 3, 4))))
    }
  }

  private def foreachFinalizers =
    unsafeRun {
      testFinalizersPar(4, res => ZManaged.foreach(List(1, 2, 3, 4))(_ => res))
    }

  def foreachFinalizerOrder = {
    val effects = new mutable.ListBuffer[Int]
    def res(x: Int) =
      ZManaged.make(IO.effectTotal { effects += x; () })(_ => IO.effectTotal { effects += x; () })

    val resources = ZManaged.foreach(List(1, 2, 3))(res)

    unsafeRun(resources.use(_ => IO.unit))

    effects must be_===(List(1, 2, 3, 3, 2, 1))
  }

  private def foreachParOrder = {
    def res(int: Int) =
      ZManaged.succeed(int)

    val managed = ZManaged.foreachPar(List(1, 2, 3, 4))(res)
    unsafeRun {
      managed.use[Any, Nothing, MatchResult[List[Int]]](res => ZIO.succeed(res must be_===(List(1, 2, 3, 4))))
    }
  }

  private def foreachParFinalizers =
    unsafeRun {
      testFinalizersPar(4, res => ZManaged.foreachPar(List(1, 2, 3, 4))(_ => res))
    }

  private def foreachParReservePar =
    unsafeRun {
      testReservePar(4, res => ZManaged.foreachPar(List(1, 2, 3, 4))(_ => res))
    }

  private def foreachParAcquirePar =
    unsafeRun {
      testAcquirePar(4, res => ZManaged.foreachPar(List(1, 2, 3, 4))(_ => res))
    }

  private def foreachParNOrder = {
    def res(int: Int) =
      ZManaged.succeed(int)

    val managed = ZManaged.foreachParN(2)(List(1, 2, 3, 4))(res)
    unsafeRun {
      managed.use[Any, Nothing, MatchResult[List[Int]]](res => ZIO.succeed(res must be_===(List(1, 2, 3, 4))))
    }
  }

  private def foreachParNFinalizers =
    unsafeRun {
      testFinalizersPar(4, res => ZManaged.foreachParN(2)(List(1, 2, 3, 4))(_ => res))
    }

  private def foreachParNReservePar =
    unsafeRun {
      testReservePar(2, res => ZManaged.foreachParN(2)(List(1, 2, 3, 4))(_ => res))
    }

  private def foreachParNAcquirePar =
    unsafeRun {
      testAcquirePar(2, res => ZManaged.foreachParN(2)(List(1, 2, 3, 4))(_ => res))
    }

  private def foreach_Finalizers =
    unsafeRun {
      testFinalizersPar(4, res => ZManaged.foreach_(List(1, 2, 3, 4))(_ => res))
    }

  private def foreachPar_Finalizers =
    unsafeRun {
      testFinalizersPar(4, res => ZManaged.foreachPar_(List(1, 2, 3, 4))(_ => res))
    }

  private def foreachPar_ReservePar =
    unsafeRun {
      testReservePar(4, res => ZManaged.foreachPar_(List(1, 2, 3, 4))(_ => res))
    }

  private def foreachPar_AcquirePar =
    unsafeRun {
      testAcquirePar(4, res => ZManaged.foreachPar_(List(1, 2, 3, 4))(_ => res))
    }

  private def foreachParN_Finalizers =
    unsafeRun {
      testFinalizersPar(4, res => ZManaged.foreachParN_(2)(List(1, 2, 3, 4))(_ => res))
    }

  private def foreachParN_ReservePar =
    unsafeRun {
      testReservePar(2, res => ZManaged.foreachParN_(2)(List(1, 2, 3, 4))(_ => res))
    }

  private def foreachParN_AcquirePar =
    unsafeRun {
      testReservePar(2, res => ZManaged.foreachParN_(2)(List(1, 2, 3, 4))(_ => res))
    }

  private def forkFinalizer = unsafeRun {
    for {
      finalized <- Ref.make(false)
      latch     <- Promise.make[Nothing, Unit]
      _ <- ZManaged
            .reserve(Reservation(latch.succeed(()) *> ZIO.never, finalized.set(true)))
            .fork
            .use_(latch.await)
      result <- finalized.get
    } yield result must_=== true
  }

  private def forkAcquisitionIsInterruptible = unsafeRun {
    for {
      finalized    <- Ref.make(false)
      acquireLatch <- Promise.make[Nothing, Unit]
      useLatch     <- Promise.make[Nothing, Unit]
      fib <- ZManaged
              .reserve(
                Reservation(
                  acquireLatch.succeed(()) *> ZIO.never,
                  finalized.set(true)
                )
              )
              .fork
              .use_(useLatch.succeed(()) *> ZIO.never)
              .fork
      _      <- acquireLatch.await
      _      <- useLatch.await
      _      <- fib.interrupt
      result <- finalized.get
    } yield result must_=== true
  }

  private def mergeAllOrder = {
    def res(int: Int) =
      ZManaged.succeed(int)

    val managed = ZManaged.mergeAll(List(1, 2, 3, 4).map(res))(List[Int]()) { case (acc, a) => a :: acc }
    unsafeRun {
      managed.use[Any, Nothing, MatchResult[List[Int]]](res => ZIO.succeed(res must be_===(List(4, 3, 2, 1))))
    }
  }

  private def mergeAllFinalizers =
    unsafeRun {
      testFinalizersPar(4, res => ZManaged.mergeAll(List.fill(4)(res))(()) { case (_, b) => b })
    }

  private def mergeAllParOrder = {
    def res(int: Int) =
      ZManaged.succeed(int)

    val managed = ZManaged.mergeAllPar(List(1, 2, 3, 4).map(res))(List[Int]()) { case (acc, a) => a :: acc }
    unsafeRun {
      managed.use[Any, Nothing, MatchResult[List[Int]]](res => ZIO.succeed(res must be_===(List(4, 3, 2, 1))))
    }
  }

  private def mergeAllParFinalizers =
    unsafeRun {
      testFinalizersPar(4, res => ZManaged.mergeAllPar(List.fill(4)(res))(()) { case (_, b) => b })
    }

  private def mergeAllParReservePar =
    unsafeRun {
      testReservePar(4, res => ZManaged.mergeAllPar(List.fill(4)(res))(()) { case (_, b) => b })
    }

  private def mergeAllParAcquirePar =
    unsafeRun {
      testAcquirePar(4, res => ZManaged.mergeAllPar(List.fill(4)(res))(()) { case (_, b) => b })
    }

  private def mergeAllParNOrder = {
    def res(int: Int) =
      ZManaged.succeed(int)

    val managed = ZManaged.mergeAllParN(2)(List(1, 2, 3, 4).map(res))(List[Int]()) { case (acc, a) => a :: acc }
    unsafeRun {
      managed.use[Any, Nothing, MatchResult[List[Int]]](res => ZIO.succeed(res must be_===(List(4, 3, 2, 1))))
    }
  }

  private def mergeAllParNFinalizers =
    unsafeRun {
      testFinalizersPar(4, res => ZManaged.mergeAllParN(2)(List.fill(4)(res))(0) { case (a, _) => a })
    }

  private def mergeAllParNReservePar =
    unsafeRun {
      testReservePar(2, res => ZManaged.mergeAllParN(2)(List.fill(4)(res))(0) { case (a, _) => a })
    }

  private def mergeAllParNAcquirePar =
    unsafeRun {
      testAcquirePar(2, res => ZManaged.mergeAllParN(2)(List.fill(4)(res))(0) { case (a, _) => a })
    }

  private def reduceAllOrder = {
    def res(int: Int) =
      ZManaged.succeed(List(int))

    val managed = ZManaged.reduceAll(ZManaged.succeed(Nil), List(1, 2, 3, 4).map(res)) { case (a1, a2) => a1 ++ a2 }
    unsafeRun {
      managed.use[Any, Nothing, MatchResult[List[Int]]](res => ZIO.succeed(res must be_===(List(1, 2, 3, 4))))
    }
  }

  private def reduceAllFinalizers =
    unsafeRun {
      testFinalizersPar(4, res => ZManaged.reduceAll(ZManaged.succeed(0), List.fill(4)(res)) { case (a, _) => a })
    }

  private def reduceAllParOrder = {
    def res(int: Int) =
      ZManaged.succeed(List(int))

    val managed = ZManaged.reduceAllPar(ZManaged.succeed(Nil), List(1, 2, 3, 4).map(res)) { case (a1, a2) => a1 ++ a2 }
    unsafeRun {
      managed.use[Any, Nothing, MatchResult[List[Int]]](res => ZIO.succeed(res must be_===(List(1, 2, 3, 4))))
    }
  }

  private def reduceAllParFinalizers =
    unsafeRun {
      testFinalizersPar(4, res => ZManaged.reduceAllPar(ZManaged.succeed(0), List.fill(4)(res)) { case (a, _) => a })
    }

  private def reduceAllParReservePar =
    unsafeRun {
      testReservePar(4, res => ZManaged.reduceAllPar(ZManaged.succeed(0), List.fill(4)(res)) { case (a, _) => a })
    }

  private def reduceAllParAcquirePar =
    unsafeRun {
      testAcquirePar(4, res => ZManaged.reduceAllPar(ZManaged.succeed(0), List.fill(4)(res)) { case (a, _) => a })
    }

  private def reduceAllParNOrder = {
    def res(int: Int) =
      ZManaged.succeed(List(int))

    val managed = ZManaged.reduceAllParN(2)(ZManaged.succeed(Nil), List(1, 2, 3, 4).map(res)) {
      case (acc, a) => a ++ acc
    }
    unsafeRun {
      managed.use[Any, Nothing, MatchResult[List[Int]]](res => ZIO.succeed(res must be_===(List(4, 3, 2, 1))))
    }
  }

  private def reduceAllParNFinalizers =
    unsafeRun {
      testFinalizersPar(
        4,
        res => ZManaged.reduceAllParN(2)(ZManaged.succeed(0), List.fill(4)(res)) { case (a, _) => a }
      )
    }

  private def reduceAllParNReservePar =
    unsafeRun {
      testReservePar(2, res => ZManaged.reduceAllParN(2)(ZManaged.succeed(0), List.fill(4)(res)) { case (a, _) => a })
    }

  private def reduceAllParNAcquirePar =
    unsafeRun {
      testAcquirePar(2, res => ZManaged.reduceAllParN(2)(ZManaged.succeed(0), List.fill(4)(res)) { case (a, _) => a })
    }

  private def testReservePar[R, E, A](
    n: Int,
    f: ZManaged[Any, Nothing, Unit] => ZManaged[R, E, A]
  ): ZIO[R, E, MatchResult[Int]] = {
    val latch = new CountDownLatch(n)
    for {
      effects      <- Ref.make(0)
      reserveLatch <- Promise.make[Nothing, Unit]
      baseRes = ZManaged.make(effects.update(_ + 1) *> ZIO.effectTotal(latch.countDown()) *> reserveLatch.await)(
        _ => ZIO.unit
      )
      res   = f(baseRes)
      _     <- res.use_(ZIO.unit).fork *> ZIO.effectTotal(latch.await())
      count <- effects.get
      _     <- reserveLatch.succeed(())
    } yield count must be_===(n)
  }

  private def testAcquirePar[R, E](
    n: Int,
    f: ZManaged[Any, Nothing, Unit] => ZManaged[R, E, _]
  ): ZIO[R, E, MatchResult[Int]] = {
    val latch = new CountDownLatch(n)
    for {
      effects      <- Ref.make(0)
      reserveLatch <- Promise.make[Nothing, Unit]
      baseRes = ZManaged.reserve(
        Reservation(effects.update(_ + 1) *> ZIO.effectTotal(latch.countDown()) *> reserveLatch.await, ZIO.unit)
      )
      res   = f(baseRes)
      _     <- res.use_(ZIO.unit).fork *> ZIO.effectTotal(latch.await())
      count <- effects.get
      _     <- reserveLatch.succeed(())
    } yield count must be_===(n)
  }

  private def testFinalizersPar[R, E](
    n: Int,
    f: ZManaged[Any, Nothing, Unit] => ZManaged[R, E, _]
  ): ZIO[R, E, MatchResult[Int]] =
    for {
      releases <- Ref.make[Int](0)
      baseRes  = ZManaged.make(ZIO.succeed(()))(_ => releases.update(_ + 1))
      res      = f(baseRes)
      _        <- res.use_(ZIO.unit)
      count    <- releases.get
    } yield count must be_===(n)

}
