package scalaz.zio
package interop

import scala.concurrent.duration._

import cats.effect.Effect
import fs2.Stream
import org.specs2.concurrent.ExecutionEnv
import org.specs2.Specification
import org.specs2.specification.AroundTimeout

class ZioWithFs2Spec(implicit ee: ExecutionEnv) extends Specification with AroundTimeout {

  def is = s2"""
  A simple fs2 join must
    work if `F` is `cats.effect.IO`          ${simpleJoin(fIsCats)}
    work if `F` is `scalaz.zio.interop.Task` ${simpleJoin(fIsZio)}
  """

  def simpleJoin(ints: => List[Int]) = upTo(2.seconds) {
    ints must_=== List(1, 1)
  }

  def fIsCats = testCaseJoin[cats.effect.IO].unsafeRunSync()

  def fIsZio: List[Int] = {
    import catz._
    unsafePerformIO(testCaseJoin[scalaz.zio.interop.Task])
  }

  def testCaseJoin[F[_]: Effect]: F[List[Int]] = {
    def one: F[Int]                   = Effect[F].delay(1)
    val s: Stream[F, Int]             = Stream.eval(one)
    val ss: Stream[F, Stream[F, Int]] = Stream.emits(List(s, s))
    ss.join(2).compile.toList
  }
}
