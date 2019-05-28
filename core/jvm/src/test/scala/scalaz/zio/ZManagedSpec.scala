package scalaz.zio

import org.scalacheck.{ Gen, _ }
import org.specs2.ScalaCheck
import org.specs2.matcher.MatchResult
import org.specs2.matcher.describe.Diffable
import scalaz.zio.Exit.Cause.Interrupt
import scalaz.zio.Exit.{ Cause, Failure }
import scalaz.zio.clock.Clock
import scalaz.zio.duration._

import java.util.concurrent.atomic.AtomicInteger

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
  ZManaged.foldM
    Runs onFailure on failure $foldMFailure
    Runs onSucess on success $foldMSuccess
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
    Cancels early if effects fail $foreachParNCancel
    Does not start unnecessary reservations $foreachParNUnneccesary
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
    Cancels early if effects fail $foreachParN_Cancel
    Does not start unnecessary reservations $foreachParN_Unneccesary
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
    Cancels early if effects fail $mergeAllParNCancel
    Does not start unnecessary reservations $mergeAllParNUnneccesary
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
    Cancels early if effects fail $reduceAllParNCancel
    Does not start unnecessary reservations $reduceAllParNUnneccesary
  ZManged.retry
    Should retry the reservation $retryReservation
    Should retry the acquisition $retryAcquisition
    Should share retries between both $retryBoth
  ZManaged.timed
    Should time both the reservation and the acquisition $timed
  ZManaged.timeout
    Returns None if the reservation takes longer than d $timeoutReservation
    Returns None if the acquisition takes longer than d $timeoutAcquisition
    Returns None if (reservation >>= acquisition) takes longer than d $timeoutAcquisitionAndReservation
    Runs finalizers if returning None and reservation is successful $timeoutRunFinalizers
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

  private def parallelAcquireAndRelease = {
    val cleanups = new mutable.ListBuffer[String]

    def managed(v: String): ZManaged[Any, Nothing, String] =
      ZManaged.make(IO.succeed(v))(_ => IO.effectTotal { cleanups += v; () })

    val program = managed("A").zipWithPar(managed("B"))(_ + _).use[Any, Nothing, String](IO.succeed)

    val result = unsafeRun(program)

    result must haveSize(2)
    result.size === cleanups.size
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

  private def zipParFailAcquisition = {
    val first  = ZManaged.unit
    val second = ZManaged.reserve(Reservation(ZIO.fail(()), ZIO.unit))

    unsafeRunSync(first.zipPar(second).use(_ => ZIO.unit)) must be_===(Exit.Failure(Cause.Fail(())))
  }

  private def zipParFailReservation = {
    val first  = ZManaged.unit
    val second = ZManaged.fromEffect(ZIO.fail(()))

    unsafeRunSync(first.zipPar(second).use(_ => ZIO.unit)) must be_===(Exit.Failure(Cause.Fail(())))
  }

  private def zipParFailAcquisitionRelease = {
    var releases = 0
    val first    = ZManaged.unit
    val second   = ZManaged.reserve(Reservation(ZIO.fail(()), ZIO.effectTotal(releases += 1)))

    val exit = unsafeRunSync(first.zipPar(second).use(_ => ZIO.unit))
    (exit must be_===(Exit.Failure(Cause.Fail(())))) && (releases must be_===(1))
  }

  private def zipParFailReservationRelease = {
    var releases = 0
    val first    = ZManaged.reserve(Reservation(ZIO.unit, ZIO.effectTotal(releases += 1)))
    val second   = ZManaged.make(ZIO.fail(()))(_ => ZIO.effectTotal(releases += 1))

    val exit = unsafeRunSync(first.zipPar(second).use(_ => ZIO.unit))
    (exit must be_===(Exit.Failure(Cause.Fail(())))) && (releases must be_===(1))
  }

  private def testFlatten =
    forAll(Gen.alphaStr) { str =>
      unsafeRun((for {
        flatten1 <- ZManaged.succeedLazy(ZManaged.succeedLazy(str)).flatten
        flatten2 <- ZManaged.flatten(ZManaged.succeedLazy(ZManaged.succeedLazy(str)))
      } yield flatten1 must ===(flatten2)).use[Any, Nothing, MatchResult[String]](result => ZIO.succeed(result)))
    }

  def testAbsolve =
    forAll(Gen.alphaStr) { str =>
      val managedEither: ZManaged[Any, Nothing, Either[Nothing, String]] = ZManaged.succeed(Right(str))
      unsafeRun((for {
        abs1 <- managedEither.absolve
        abs2 <- ZManaged.absolve(managedEither)
      } yield abs1 must ===(abs2)).use[Any, Nothing, MatchResult[String]](result => ZIO.succeed(result)))
    }

  def timeoutReservation = {
    val managed = ZManaged.make(clock.sleep(5.seconds))(_ => ZIO.unit)
    unsafeRun {
      managed.timeout(1.millisecond).use(res => ZIO.succeed(res must be_===(None)))
    }
  }

  def timeoutAcquisition = {
    val managed = ZManaged.reserve(Reservation(clock.sleep(5.seconds), ZIO.unit))
    unsafeRun {
      managed.timeout(1.millisecond).use(res => ZIO.succeed(res must be_===(None)))
    }
  }

  def timeoutAcquisitionAndReservation = {
    val managed = ZManaged(clock.sleep(1.millisecond) *> ZIO.succeed(Reservation(clock.sleep(5.seconds), ZIO.unit)))
    unsafeRun {
      managed.timeout(100.millisecond).use(res => ZIO.succeed(res must be_===(None)))
    }
  }

  def timeoutRunFinalizers =
    unsafeRun(for {
      latch   <- Promise.make[Nothing, Unit]
      release <- Ref.make[Int](0)
      managed = ZManaged.reserve(Reservation(ZIO.never, release.update(_ + 1) *> latch.succeed(())))
      res     <- managed.timeout(10.millisecond).use(ZIO.succeed(_))
      _       <- latch.await
      v       <- release.get
    } yield (res must beNone) && (v must be_===(1)))

  def timed = {
    val managed = ZManaged(
      clock.sleep(100.milliseconds) *> ZIO.succeed(Reservation(clock.sleep(100.milliseconds), ZIO.unit))
    )
    unsafeRun {
      managed.timed.use { case (duration, _) => ZIO.succeed((duration >= 200.milliseconds) must beTrue) }
    }
  }

  def foreachOrder = {
    def res(int: Int) =
      ZManaged.succeed(int)

    val managed = ZManaged.foreach(List(1, 2, 3, 4))(res)
    unsafeRun {
      managed.use[Any, Nothing, MatchResult[List[Int]]](res => ZIO.succeed(res must be_===(List(1, 2, 3, 4))))
    }
  }

  def foreachFinalizers = {
    var effect = 0
    val res    = ZManaged.make(ZIO.unit)(_ => ZIO.effectTotal(effect += 1))

    val managed = ZManaged.foreach(List(1, 2, 3, 4))(_ => res)
    unsafeRun {
      managed.use[Any, Nothing, Unit](_ => ZIO.unit)
    }
    effect must be_===(4)
  }

  def foreachFinalizerOrder = {
    val effects = new mutable.ListBuffer[Int]
    def res(x: Int) =
      ZManaged.make(IO.effectTotal { effects += x; () })(_ => IO.effectTotal { effects += x; () })

    val resources = ZManaged.foreach(List(1, 2, 3))(res)

    unsafeRun(resources.use(_ => IO.unit))

    effects must be_===(List(1, 2, 3, 3, 2, 1))
  }

  def foreachParOrder = {
    def res(int: Int) =
      ZManaged.succeed(int)

    val managed = ZManaged.foreachPar(List(1, 2, 3, 4))(res)
    unsafeRun {
      managed.use[Any, Nothing, MatchResult[List[Int]]](res => ZIO.succeed(res must be_===(List(1, 2, 3, 4))))
    }
  }

  def foreachParFinalizers = {
    val effect = new AtomicInteger(0)
    val res    = ZManaged.make(ZIO.unit)(_ => ZIO.effectTotal(effect.incrementAndGet))

    val managed = ZManaged.foreachPar(List(1, 2, 3, 4))(_ => res)
    unsafeRun {
      managed.use[Any, Nothing, Unit](_ => ZIO.unit)
    }
    effect.get must be_===(4)
  }

  def foreachParReservePar = {
    val effect = new AtomicInteger(0)
    val res    = ZManaged.make(ZIO.effectTotal(effect.incrementAndGet) *> clock.sleep(100.seconds))(_ => ZIO.unit)

    val managed = ZManaged.foreachPar(List(1, 2, 3, 4))(_ => res)
    unsafeRun {
      managed.use[Clock, Nothing, Unit](_ => ZIO.unit).fork *> clock.sleep(100.milliseconds)
    }
    effect.get must be_===(4)
  }

  def foreachParAcquirePar = {
    val effect = new AtomicInteger(0)
    val res =
      ZManaged.reserve(Reservation(ZIO.effectTotal(effect.incrementAndGet) *> clock.sleep(100.seconds), ZIO.unit))

    val managed = ZManaged.foreachPar(List(1, 2, 3, 4))(_ => res)
    unsafeRun {
      managed.use[Clock, Nothing, Unit](_ => ZIO.unit).fork *> clock.sleep(100.milliseconds)
    }
    effect.get must be_===(4)
  }

  def foreachParNOrder = {
    def res(int: Int) =
      ZManaged.succeed(int)

    val managed = ZManaged.foreachParN(2)(List(1, 2, 3, 4))(res)
    unsafeRun {
      managed.use[Any, Nothing, MatchResult[List[Int]]](res => ZIO.succeed(res must be_===(List(1, 2, 3, 4))))
    }
  }

  def foreachParNFinalizers = {
    val effect = new AtomicInteger(0)
    val res    = ZManaged.make(ZIO.unit)(_ => ZIO.effectTotal(effect.incrementAndGet))

    val managed = ZManaged.foreachParN(2)(List(1, 2, 3, 4))(_ => res)
    unsafeRun {
      managed.use[Any, Nothing, Unit](_ => ZIO.unit)
    }
    effect.get must be_===(4)
  }

  def foreachParNReservePar = {
    val effect = new AtomicInteger(0)
    val res    = ZManaged.make(ZIO.effectTotal(effect.incrementAndGet) *> clock.sleep(100.seconds))(_ => ZIO.unit)

    val managed = ZManaged.foreachParN(2)(List(1, 2, 3, 4))(_ => res)
    unsafeRun {
      managed.use[Clock, Nothing, Unit](_ => ZIO.unit).fork *> clock.sleep(100.milliseconds)
    }
    effect.get must be_===(2)
  }

  def foreachParNAcquirePar = {
    val effect = new AtomicInteger(0)
    val res =
      ZManaged.reserve(Reservation(ZIO.effectTotal(effect.incrementAndGet) *> clock.sleep(100.seconds), ZIO.unit))

    val managed = ZManaged.foreachParN(2)(List(1, 2, 3, 4))(_ => res)
    unsafeRun {
      managed.use[Clock, Nothing, Unit](_ => ZIO.unit).fork *> clock.sleep(100.milliseconds)
    }
    effect.get must be_===(2)
  }

  def foreachParNCancel = {
    val effect = new AtomicInteger(0)
    val res = ZManaged.reserve(
      Reservation(clock.sleep(100.milliseconds) *> ZIO.effectTotal(effect.incrementAndGet) *> ZIO.interrupt, ZIO.unit)
    )

    val managed = ZManaged.foreachParN(2)(List.fill(4)(1))(_ => res)
    unsafeRun {
      managed.use[Clock, Nothing, Unit](_ => ZIO.unit).orElse(ZIO.unit)
    }
    effect.get must be_<=(2)
  }

  def foreachParNUnneccesary = {
    val effect = new AtomicInteger(0)
    val res = ZManaged.make(clock.sleep(50.milliseconds) *> ZIO.effectTotal(effect.incrementAndGet) *> ZIO.interrupt)(
      _ => ZIO.unit
    )

    val managed = ZManaged.foreachParN(2)(List.fill(100)(1))(_ => res)
    unsafeRun {
      managed.use[Clock, Nothing, Unit](_ => ZIO.unit).orElse(ZIO.unit)
    }
    effect.get must be_<=(4)
  }

  def foreach_Finalizers = {
    val effect = new AtomicInteger(0)
    val res    = ZManaged.make(ZIO.unit)(_ => ZIO.effectTotal(effect.incrementAndGet))

    val managed = ZManaged.foreach_(List(1, 2, 3, 4))(_ => res)
    unsafeRun {
      managed.use[Any, Nothing, Unit](_ => ZIO.unit)
    }
    effect.get must be_===(4)
  }

  def foreachPar_Finalizers = {
    val effect = new AtomicInteger(0)
    val res    = ZManaged.make(ZIO.unit)(_ => ZIO.effectTotal(effect.incrementAndGet))

    val managed = ZManaged.foreachPar_(List(1, 2, 3, 4))(_ => res)
    unsafeRun {
      managed.use[Any, Nothing, Unit](_ => ZIO.unit)
    }
    effect.get must be_===(4)
  }

  def foreachPar_ReservePar = {
    val effect = new AtomicInteger(0)
    val res    = ZManaged.make(ZIO.effectTotal(effect.incrementAndGet) *> clock.sleep(100.seconds))(_ => ZIO.unit)

    val managed = ZManaged.foreachPar_(List(1, 2, 3, 4))(_ => res)
    unsafeRun {
      managed.use[Clock, Nothing, Unit](_ => ZIO.unit).fork *> clock.sleep(10.milliseconds)
    }
    effect.get must be_===(4)
  }

  def foreachPar_AcquirePar = {
    val effect = new AtomicInteger(0)
    val res =
      ZManaged.reserve(Reservation(ZIO.effectTotal(effect.incrementAndGet) *> clock.sleep(100.seconds), ZIO.unit))

    val managed = ZManaged.foreachPar_(List(1, 2, 3, 4))(_ => res)
    unsafeRun {
      managed.use[Clock, Nothing, Unit](_ => ZIO.unit).fork *> clock.sleep(10.milliseconds)
    }
    effect.get must be_===(4)
  }

  def foreachParN_Finalizers = {
    val effect = new AtomicInteger(0)
    val res    = ZManaged.make(ZIO.unit)(_ => ZIO.effectTotal(effect.incrementAndGet))

    val managed = ZManaged.foreachParN_(2)(List(1, 2, 3, 4))(_ => res)
    unsafeRun {
      managed.use[Any, Nothing, Unit](_ => ZIO.unit)
    }
    effect.get must be_===(4)
  }

  def foreachParN_ReservePar = {
    val effect = new AtomicInteger(0)
    val res    = ZManaged.make(ZIO.effectTotal(effect.incrementAndGet) *> clock.sleep(100.seconds))(_ => ZIO.unit)

    val managed = ZManaged.foreachParN_(2)(List(1, 2, 3, 4))(_ => res)
    unsafeRun {
      managed.use[Clock, Nothing, Unit](_ => ZIO.unit).fork *> clock.sleep(100.milliseconds)
    }
    effect.get must be_===(2)
  }

  def foreachParN_AcquirePar = {
    val effect = new AtomicInteger(0)
    val res =
      ZManaged.reserve(Reservation(ZIO.effectTotal(effect.incrementAndGet) *> clock.sleep(100.seconds), ZIO.unit))

    val managed = ZManaged.foreachParN_(2)(List(1, 2, 3, 4))(_ => res)
    unsafeRun {
      managed.use[Clock, Nothing, Unit](_ => ZIO.unit).fork *> clock.sleep(100.milliseconds)
    }
    effect.get must be_===(2)
  }

  def foreachParN_Cancel = {
    val effect = new AtomicInteger(0)
    val res = ZManaged.reserve(
      Reservation(clock.sleep(100.milliseconds) *> ZIO.effectTotal(effect.incrementAndGet) *> ZIO.interrupt, ZIO.unit)
    )

    val managed = ZManaged.foreachParN_(2)(List.fill(4)(1))(_ => res)
    unsafeRun {
      managed.use[Clock, Nothing, Unit](_ => ZIO.unit).orElse(ZIO.unit)
    }
    effect.get must be_<=(2)
  }

  def foreachParN_Unneccesary = {
    val effect = new AtomicInteger(0)
    val res = ZManaged.make(clock.sleep(50.milliseconds) *> ZIO.effectTotal(effect.incrementAndGet) *> ZIO.interrupt)(
      _ => ZIO.unit
    )

    val managed = ZManaged.foreachParN_(2)(List.fill(100)(1))(_ => res)
    unsafeRun {
      managed.use[Clock, Nothing, Unit](_ => ZIO.unit).orElse(ZIO.unit)
    }
    effect.get must be_<=(4)
  }

  def mergeAllOrder = {
    def res(int: Int) =
      ZManaged.succeed(int)

    val managed = ZManaged.mergeAll(List(1, 2, 3, 4).map(res))(List[Int]()) { case (acc, a) => a :: acc }
    unsafeRun {
      managed.use[Any, Nothing, MatchResult[List[Int]]](res => ZIO.succeed(res must be_===(List(4, 3, 2, 1))))
    }
  }

  def mergeAllFinalizers = {
    var effect = 0
    def res(int: Int) =
      ZManaged.make(ZIO.succeed(int))(_ => ZIO.effectTotal(effect += int))

    val managed = ZManaged.mergeAll(List(1, 1, 1, 1).map(res))(0)(_ + _)
    unsafeRun {
      managed.use[Any, Nothing, Unit](_ => ZIO.unit)
    }
    effect must be_===(4)
  }

  def mergeAllParOrder = {
    def res(int: Int) =
      ZManaged.succeed(int)

    val managed = ZManaged.mergeAllPar(List(1, 2, 3, 4).map(res))(List[Int]()) { case (acc, a) => a :: acc }
    unsafeRun {
      managed.use[Any, Nothing, MatchResult[List[Int]]](res => ZIO.succeed(res must be_===(List(4, 3, 2, 1))))
    }
  }

  def mergeAllParFinalizers = {
    val effect = new AtomicInteger(0)
    def res(int: Int) =
      ZManaged.make(ZIO.succeed(int))(_ => ZIO.effectTotal(effect.incrementAndGet))

    val managed = ZManaged.mergeAllPar(List(1, 1, 1, 1).map(res))(0)(_ + _)
    unsafeRun {
      managed.use[Any, Nothing, Unit](_ => ZIO.unit)
    }
    effect.get must be_===(4)
  }

  def mergeAllParReservePar = {
    val effect = new AtomicInteger(0)
    def res(int: Int) =
      ZManaged.make(ZIO.effectTotal(effect.incrementAndGet) *> clock.sleep(100.seconds) *> ZIO.succeed(int))(
        _ => ZIO.unit
      )

    val managed = ZManaged.mergeAllPar(List(1, 2, 3, 4).map(res))(0)(_ + _)
    unsafeRun {
      managed.use[Clock, Nothing, Unit](_ => ZIO.unit).fork *> clock.sleep(100.milliseconds)
    }
    effect.get must be_===(4)
  }

  def mergeAllParAcquirePar = {
    val effect = new AtomicInteger(0)
    def res(int: Int) =
      ZManaged.reserve(
        Reservation(ZIO.effectTotal(effect.incrementAndGet) *> clock.sleep(100.seconds) *> ZIO.succeed(int), ZIO.unit)
      )

    val managed = ZManaged.mergeAllPar(List(1, 2, 3, 4).map(res))(0)(_ + _)
    unsafeRun {
      managed.use[Clock, Nothing, Unit](_ => ZIO.unit).fork *> clock.sleep(10.milliseconds)
    }
    effect.get must be_===(4)
  }

  def mergeAllParNOrder = {
    def res(int: Int) =
      ZManaged.succeed(int)

    val managed = ZManaged.mergeAllParN(2)(List(1, 2, 3, 4).map(res))(List[Int]()) { case (acc, a) => a :: acc }
    unsafeRun {
      managed.use[Any, Nothing, MatchResult[List[Int]]](res => ZIO.succeed(res must be_===(List(4, 3, 2, 1))))
    }
  }

  def mergeAllParNFinalizers = {
    val effect = new AtomicInteger(0)
    def res(int: Int) =
      ZManaged.make(ZIO.succeed(int))(_ => ZIO.effectTotal(effect.incrementAndGet))

    val managed = ZManaged.mergeAllParN(2)(List(1, 1, 1, 1).map(res))(0)(_ + _)
    unsafeRun {
      managed.use[Any, Nothing, Unit](_ => ZIO.unit)
    }
    effect.get must be_===(4)
  }

  def mergeAllParNReservePar = {
    val effect = new AtomicInteger(0)
    def res(int: Int) =
      ZManaged.make(ZIO.effectTotal(effect.incrementAndGet) *> clock.sleep(100.seconds) *> ZIO.succeed(int))(
        _ => ZIO.unit
      )

    val managed = ZManaged.mergeAllParN(2)(List(1, 2, 3, 4).map(res))(0)(_ + _)
    unsafeRun {
      managed.use[Clock, Nothing, Unit](_ => ZIO.unit).fork *> clock.sleep(100.milliseconds)
    }
    effect.get must be_===(2)
  }

  def mergeAllParNAcquirePar = {
    val effect = new AtomicInteger(0)
    def res(int: Int) =
      ZManaged.reserve(
        Reservation(ZIO.effectTotal(effect.incrementAndGet) *> clock.sleep(100.seconds) *> ZIO.succeed(int), ZIO.unit)
      )

    val managed = ZManaged.mergeAllParN(2)(List(1, 2, 3, 4).map(res))(0)(_ + _)
    unsafeRun {
      managed.use[Clock, Nothing, Unit](_ => ZIO.unit).fork *> clock.sleep(10.milliseconds)
    }
    effect.get must be_===(2)
  }

  def mergeAllParNCancel = {
    val effect = new AtomicInteger(0)
    def res(int: Int) =
      ZManaged.reserve(
        Reservation(
          clock.sleep(100.milliseconds) *> ZIO.effectTotal(effect.incrementAndGet) *> ZIO.interrupt.const(int),
          ZIO.unit
        )
      )

    val managed = ZManaged.mergeAllParN(2)(List(1, 2, 3, 4).map(res))(0)(_ + _)
    unsafeRun {
      managed.use[Clock, Nothing, Unit](_ => ZIO.unit).orElse(ZIO.unit)
    }
    effect.get must be_<=(2)
  }

  def mergeAllParNUnneccesary = {
    val effect = new AtomicInteger(0)
    def res(int: Int) =
      ZManaged.make(
        clock.sleep(100.milliseconds) *> ZIO.effectTotal(effect.incrementAndGet) *> ZIO.interrupt.const(int)
      )(_ => ZIO.unit)

    val managed = ZManaged.mergeAllParN(2)(List.fill(100)(0).map(res))(0)(_ + _)
    unsafeRun {
      managed.use[Clock, Nothing, Unit](_ => ZIO.unit).orElse(ZIO.unit)
    }
    effect.get must be_<=(4)
  }

  def reduceAllOrder = {
    def res(int: Int) =
      ZManaged.succeed(List(int))

    val managed = ZManaged.reduceAll(ZManaged.succeed(Nil), List(1, 2, 3, 4).map(res)) { case (a1, a2) => a1 ++ a2 }
    unsafeRun {
      managed.use[Any, Nothing, MatchResult[List[Int]]](res => ZIO.succeed(res must be_===(List(1, 2, 3, 4))))
    }
  }

  def reduceAllFinalizers = {
    var effect = 0
    def res(int: Int) =
      ZManaged.make(ZIO.succeed(int))(_ => ZIO.effectTotal(effect += int))

    val managed = ZManaged.reduceAll(ZManaged.succeed(0), List(1, 1, 1, 1).map(res))(_ + _)
    unsafeRun {
      managed.use[Any, Nothing, Unit](_ => ZIO.unit)
    }
    effect must be_===(4)
  }

  def reduceAllParOrder = {
    def res(int: Int) =
      ZManaged.succeed(List(int))

    val managed = ZManaged.reduceAllPar(ZManaged.succeed(Nil), List(1, 2, 3, 4).map(res)) { case (a1, a2) => a1 ++ a2 }
    unsafeRun {
      managed.use[Any, Nothing, MatchResult[List[Int]]](res => ZIO.succeed(res must be_===(List(1, 2, 3, 4))))
    }
  }

  def reduceAllParFinalizers = {
    val effect = new AtomicInteger(0)
    def res(int: Int) =
      ZManaged.make(ZIO.succeed(int))(_ => ZIO.effectTotal(effect.incrementAndGet))

    val managed = ZManaged.reduceAllPar(ZManaged.succeed(0), List(1, 1, 1, 1).map(res))(_ + _)
    unsafeRun {
      managed.use[Any, Nothing, Unit](_ => ZIO.unit)
    }
    effect.get must be_===(4)
  }

  def reduceAllParReservePar = {
    val effect = new AtomicInteger(0)
    def res(int: Int) =
      ZManaged.make(ZIO.effectTotal(effect.incrementAndGet) *> clock.sleep(100.seconds) *> ZIO.succeed(int))(
        _ => ZIO.unit
      )

    val managed = ZManaged.reduceAllPar(ZManaged.succeed(0), List(1, 2, 3, 4).map(res))(_ + _)
    unsafeRun {
      managed.use[Clock, Nothing, Unit](_ => ZIO.unit).fork *> clock.sleep(100.milliseconds)
    }
    effect.get must be_===(4)
  }

  def reduceAllParAcquirePar = {
    val effect = new AtomicInteger(0)
    def res(int: Int) =
      ZManaged.reserve(
        Reservation(ZIO.effectTotal(effect.incrementAndGet) *> clock.sleep(100.seconds) *> ZIO.succeed(int), ZIO.unit)
      )

    val managed = ZManaged.reduceAllPar(ZManaged.succeed(0), List(1, 2, 3, 4).map(res))(_ + _)
    unsafeRun {
      managed.use[Clock, Nothing, Unit](_ => ZIO.unit).fork *> clock.sleep(10.milliseconds)
    }
    effect.get must be_===(4)
  }

  def reduceAllParNOrder = {
    def res(int: Int) =
      ZManaged.succeed(List(int))

    val managed = ZManaged.reduceAllParN(2)(ZManaged.succeed(Nil), List(1, 2, 3, 4).map(res)) {
      case (acc, a) => a ++ acc
    }
    unsafeRun {
      managed.use[Any, Nothing, MatchResult[List[Int]]](res => ZIO.succeed(res must be_===(List(4, 3, 2, 1))))
    }
  }

  def reduceAllParNFinalizers = {
    val effect = new AtomicInteger(0)
    def res(int: Int) =
      ZManaged.make(ZIO.succeed(int))(_ => ZIO.effectTotal(effect.incrementAndGet))

    val managed = ZManaged.reduceAllParN(2)(ZManaged.succeed(0), List(1, 1, 1, 1).map(res))(_ + _)
    unsafeRun {
      managed.use[Any, Nothing, Unit](_ => ZIO.unit)
    }
    effect.get must be_===(4)
  }

  def reduceAllParNReservePar = {
    val effect = new AtomicInteger(0)
    def res(int: Int) =
      ZManaged.make(ZIO.effectTotal(effect.incrementAndGet) *> clock.sleep(100.seconds) *> ZIO.succeed(int))(
        _ => ZIO.unit
      )

    val managed = ZManaged.reduceAllParN(2)(ZManaged.succeed(0), List(1, 2, 3, 4).map(res))(_ + _)
    unsafeRun {
      managed.use[Clock, Nothing, Unit](_ => ZIO.unit).fork *> clock.sleep(100.milliseconds)
    }
    effect.get must be_===(2)
  }

  def reduceAllParNAcquirePar = {
    val effect = new AtomicInteger(0)
    def res(int: Int) =
      ZManaged.reserve(
        Reservation(ZIO.effectTotal(effect.incrementAndGet) *> clock.sleep(100.seconds) *> ZIO.succeed(int), ZIO.unit)
      )

    val managed = ZManaged.reduceAllParN(2)(ZManaged.succeed(0), List(1, 2, 3, 4).map(res))(_ + _)
    unsafeRun {
      managed.use[Clock, Nothing, Unit](_ => ZIO.unit).fork *> clock.sleep(10.milliseconds)
    }
    effect.get must be_===(2)
  }

  def reduceAllParNCancel = {
    val effect = new AtomicInteger(0)
    def res(int: Int) =
      ZManaged.reserve(
        Reservation(
          clock.sleep(100.milliseconds) *> ZIO.effectTotal(effect.incrementAndGet) *> ZIO.interrupt.const(int),
          ZIO.unit
        )
      )

    val managed = ZManaged.reduceAllParN(2)(ZManaged.succeed(0), List(1, 2, 3, 4).map(res))(_ + _)
    unsafeRun {
      managed.use[Clock, Nothing, Unit](_ => ZIO.unit).orElse(ZIO.unit)
    }
    effect.get must be_<=(2)
  }

  def reduceAllParNUnneccesary = {
    val effect = new AtomicInteger(0)
    def res(int: Int) =
      ZManaged.make(
        clock.sleep(100.milliseconds) *> ZIO.effectTotal(effect.incrementAndGet) *> ZIO.interrupt.const(int)
      )(_ => ZIO.unit)

    val managed = ZManaged.reduceAllParN(2)(ZManaged.succeed(0), List.fill(100)(0).map(res))(_ + _)
    unsafeRun {
      managed.use[Clock, Nothing, Unit](_ => ZIO.unit).orElse(ZIO.unit)
    }
    effect.get must be_<=(4)
  }

}
