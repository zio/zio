package scalaz.zio

import org.specs2.ScalaCheck
import scala.collection.mutable
import duration._

class ManagedSpec(implicit ee: org.specs2.concurrent.ExecutionEnv) extends TestRuntime with GenIO with ScalaCheck {
  def is = "ManagedSpec".title ^ s2"""
  Managed.make
    Invokes cleanups in reverse order of acquisition. $invokesCleanupsInReverse
    Properly performs parallel acquire and release. $parallelAcquireAndRelease
    Constructs an uninterruptible Managed value. $uninterruptible
  Managed.traverse
    Invokes cleanups in reverse order of acquisition. $traverse
  """

  private def invokesCleanupsInReverse = {
    val effects = new mutable.ListBuffer[Int]
    def res(x: Int) =
      Managed.make(IO.defer { effects += x; () })(_ => IO.defer { effects += x; () })

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

    def managed(v: String): Managed[Any, Nothing, String] =
      Managed.make(IO.succeed(v))(_ => IO.defer { cleanups += v; () })

    val program = managed("A").zipWithPar(managed("B"))(_ + _).use[Any, Nothing, String](IO.succeed)

    val result = unsafeRun(program)

    result must haveSize(2)
    result.size === cleanups.size
  }

  private def traverse = {
    val effects = new mutable.ListBuffer[Int]
    def res(x: Int) =
      Managed.make(IO.defer { effects += x; () })(_ => IO.defer { effects += x; () })

    val resources = Managed.foreach(List(1, 2, 3))(res)

    unsafeRun(resources.use(_ => IO.unit))

    effects must be_===(List(1, 2, 3, 3, 2, 1))
  }

  private def uninterruptible = {
    val program = for {
      never              <- Promise.make[Nothing, Unit]
      reachedAcquisition <- Promise.make[Nothing, Unit]
      managedFiber       <- Managed.make(reachedAcquisition.succeed(()) *> never.await)(_ => IO.unit).use_(IO.unit).fork
      _                  <- reachedAcquisition.await
      interruption       <- managedFiber.interrupt.timeout(5.seconds).attempt
    } yield interruption

    unsafeRun(program) must be_===(Right(None))
  }
}
