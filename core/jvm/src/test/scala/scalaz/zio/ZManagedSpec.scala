package scalaz.zio

import org.specs2.ScalaCheck

import scala.collection.mutable
import duration._
import org.specs2.matcher.describe.Diffable

class ZManagedSpec(implicit ee: org.specs2.concurrent.ExecutionEnv) extends TestRuntime with GenIO with ScalaCheck {
  def is = "ZManagedSpec".title ^ s2"""
  ZManaged.make
    Invokes cleanups in reverse order of acquisition. $invokesCleanupsInReverse
    Properly performs parallel acquire and release. $parallelAcquireAndRelease
    Constructs an uninterruptible Managed value. $uninterruptible
  ZManaged.traverse
    Invokes cleanups in reverse order of acquisition. $traverse
  ZManaged.reserve 
    Works via ZIO.reserve.      $reserve1
    Works via ZManaged.reserve. $reserve2
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

  private def uninterruptible = {
    val program = for {
      never              <- Promise.make[Nothing, Unit]
      reachedAcquisition <- Promise.make[Nothing, Unit]
      managedFiber       <- ZManaged.make(reachedAcquisition.succeed(()) *> never.await)(_ => IO.unit).use_(IO.unit).fork
      _                  <- reachedAcquisition.await
      interruption       <- managedFiber.interrupt.timeout(5.seconds).either
    } yield interruption

    implicit val d: Diffable[Right[Nothing, Option[Exit[Nothing, Unit]]]] =
      Diffable.eitherRightDiffable[Option[Exit[Nothing, Unit]]] //    TODO: Dotty has ambiguous implicits
    unsafeRun(program) must be_===(Right(None))
  }

  private def reserve1 = {
    val intent  = ZIO.succeed(Reservation(UIO.succeed(21), UIO.unit))
    val booking = ZIO.reserve(intent)(a => ZIO.succeed(a * 2))
    unsafeRun(booking) must be_===(42)
  }

  private def reserve2 = {
    // Note: 'use' is more flexible in this case but less inferrable?
    val intent  = ZManaged.reserve(Reservation(UIO.succeed(10), UIO.unit))
    val booking = intent.use[Any, Nothing, String]((a: Int) => ZIO.succeed("X" * a))
    unsafeRun(booking) must be_===("XXXXXXXXXX")
  }
}
