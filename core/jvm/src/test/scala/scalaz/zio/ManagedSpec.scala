package scalaz.zio

import org.specs2.ScalaCheck
import scala.collection.mutable

class ManagedSpec extends AbstractRTSSpec with GenIO with ScalaCheck {
  def is = "ManagedSpec".title ^ s2"""
  Invokes cleanups in reverse order of acquisition. $invokesCleanupsInReverse
  """

  def invokesCleanupsInReverse = {
    val effects = new mutable.ListBuffer[Int]
    def res(x: Int) =
      Managed.mk(IO.sync { effects += x; () })(_ => IO.sync { effects += x; () })

    val (first, second, third) = (res(1), res(2), res(3))

    val composed = for {
      _ <- first
      _ <- second
      _ <- third
    } yield ()

    val program = composed.use(_ => IO.unit)

    unsafeRun(program)

    effects must be_===(List(1, 2, 3, 3, 2, 1))
  }
}
