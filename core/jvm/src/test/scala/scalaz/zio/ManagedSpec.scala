package scalaz.zio

import org.specs2.ScalaCheck
import scala.collection.mutable

class ManagedSpec(implicit ee: org.specs2.concurrent.ExecutionEnv) extends AbstractRTSSpec with GenIO with ScalaCheck {
  def is = "ManagedSpec".title ^ s2"""
  Invokes cleanups in reverse order of acquisition. $invokesCleanupsInReverse
  Properly performs parallel acquire and release. $parallelAcquireAndRelease
  """

  def invokesCleanupsInReverse = {
    val effects = new mutable.ListBuffer[Int]
    def res(x: Int) =
      Managed(IO.sync { effects += x; () })(_ => IO.sync { effects += x; () })

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

  def parallelAcquireAndRelease = {
    val cleanups = new mutable.ListBuffer[String]

    def managed(v: String): Managed[Any, Nothing, String] =
      Managed(IO.now(v))(_ => IO.sync { cleanups += v; () })

    val program = managed("A").parWith(managed("B"))(_ + _).use[Any, Nothing, String](IO.now)

    val result = unsafeRun(program)

    result must haveSize(2)
    result.size === cleanups.size
  }
}
