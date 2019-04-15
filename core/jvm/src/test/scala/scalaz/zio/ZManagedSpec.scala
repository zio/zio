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

}
