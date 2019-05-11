package scalaz.zio

import org.specs2.ScalaCheck

import scala.collection.mutable
import duration._
import org.specs2.matcher.describe.Diffable
import scalaz.zio.Exit.Failure
import scalaz.zio.Exit.Cause.Interrupt

class ZManagedSpec(implicit ee: org.specs2.concurrent.ExecutionEnv) extends TestRuntime with GenIO with ScalaCheck {
  def is = "ZManagedSpec".title ^ s2"""
  ZManaged.make
    Invokes cleanups in reverse order of acquisition. $invokesCleanupsInReverse
    Properly performs parallel acquire and release. $parallelAcquireAndRelease
    Constructs an uninterruptible Managed value. $uninterruptible
  ZManaged.traverse
    Invokes cleanups in reverse order of acquisition. $traverse
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
    Returns elements in the correct order
    Runs finalizers
  ZManaged.foreachPar
    Returns elements in the correct order
    Runs finalizers
    Runs reservations in parallel
    Runs acquisitions in parallel
  ZManaged.foreachParN
    Returns elements in the correct order
    Uses at most n fibers for reservation
    Uses at most n fibers for acquisition
    Runs finalizers
    Cancels early if effects fail
  ZManaged.foreach_
    Runs finalizers
  ZManaged.foreachPar_
    Runs finalizers
    Runs reservations in parallel
    Runs acquisitions in parallel
  ZManaged.foreachParN_
    Uses at most n fibers for reservation
    Uses at most n fibers for acquisition
    Runs finalizers
    Cancels early if effects fail
  ZManaged.mergeAll
    Merges elements in the correct order
    Runs finalizers
  ZManaged.mergeAllPar
    Merges elements in the correct order
    Runs reservations in parallel
    Runs acquisitions in parallel
    Runs finalizers
  ZManaged.mergeAllParN
    Merges elements in the correct order
    Uses at most n fibers for reservation
    Uses at most n fibers for acquisition
    Runs finalizers
    Cancels early if effects fail
  ZManaged.reduceAll
    Reduces elements in the correct order
    Runs finalizers
  ZManaged.reduceAllPar
    Reduces elements in the correct order
    Runs reservations in parallel
    Runs acquisitions in parallel
    Runs finalizers
  ZManaged.reduceAllParN
    Reduces elements in the correct order
    Uses at most n fibers for reservation
    Uses at most n fibers for acquisition
    Runs finalizers
    Cancels early if effects fail
  ZManged.retry
    Should retry the reservation
    Should retry the acquisition
    Should share retries between both
  ZManaged.timed
    Should time both the reservation and the acquisition
  ZManaged.timeout
    Returns None if the reservation takes longer than d
    Returns None if the acquisition takes longer than d
    Returns None if (reservation >>= acquisition) takes longer than d
    Runs finalizers if returning None and reservation is successful
  ZManaged.zipPar
    Does not swallow exit cause if one reservation fails
    Runs finalizers if one acquisition fails
    Does not swallow acquisition if one acquisition fails
    Run finalizers if one reservation fails
  Check flatten returns the same as ZManaged.flatten
  Check absolve returns the same as ZManaged.absolve
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

  private def traverse = {
    val effects = new mutable.ListBuffer[Int]
    def res(x: Int) =
      ZManaged.make(IO.effectTotal { effects += x; () })(_ => IO.effectTotal { effects += x; () })

    val resources = ZManaged.foreach(List(1, 2, 3))(res)

    unsafeRun(resources.use(_ => IO.unit))

    effects must be_===(List(1, 2, 3, 3, 2, 1))
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

}
