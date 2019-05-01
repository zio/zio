package scalaz.zio

import scalaz.zio.stacktracer.SourceLocation

class StacktracesSpec(implicit ee: org.specs2.concurrent.ExecutionEnv) extends TestRuntime {

  def is = "StacktracesSpec".title ^ s2"""
    basic test $basicTest
    foreach $foreachTrace
    left-associative fold $leftAssociativeFold
  """

  def basicTest = {
    val res = unsafeRun(for {
      _     <- ZIO.unit
      trace <- ZIO.trace
    } yield trace)

    res must_=== Nil
  }

  def foreachTrace = {
    val res = unsafeRun(for {
      _     <- ZIO.foreach_(1 to 10)(_ => ZIO.unit *> ZIO.trace)
      trace <- ZIO.trace
    } yield trace)

    res must_=== Nil
  }

  def leftAssociativeFold = {
    def left(): ZIO[Any, Nothing, List[SourceLocation]] =
      (1 to 10)
        .foldLeft(ZIO.unit *> ZIO.unit) { (acc, i) =>
          acc *> UIO(println(i))
        } *>
        ZIO.unit *>
        ZIO.unit *>
        ZIO.unit *>
        ZIO.trace

    val res = unsafeRun(for {
      trace <- left()
    } yield trace)

    res must_=== Nil
  }

}
