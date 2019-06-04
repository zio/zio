package zio.interop

import cats.effect.{ Concurrent, Resource }
import org.specs2.Specification
import org.specs2.specification.AroundTimeout
import zio.{ DefaultRuntime, Promise, Task }
import zio.interop.catz._

class CatsInteropSpec extends Specification with AroundTimeout with DefaultRuntime {

  def is = s2"""
      Resource
        cats fiber wrapped in Resource can be canceled $catsResourceInterruptible
      """

  def catsResourceInterruptible = {

    val io = for {
      p        <- Promise.make[Nothing, Int]
      resource = Resource.make(Concurrent[Task].start(p.succeed(1) *> Task.never))(_.cancel)
      _        <- resource.use(_ => p.await)
    } yield 0

    unsafeRun(io) must be_===(0)
  }

}
