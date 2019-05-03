package scalaz.zio

import org.specs2.mutable

class StacktracesSpec(implicit ee: org.specs2.concurrent.ExecutionEnv) extends TestRuntime
  with mutable.SpecificationLike {

//  def is = "StacktracesSpec".title ^ s2"""
//    basic test $basicTest
//    foreach $foreachTrace
//    left-associative fold $leftAssociativeFold
//    nested left binds $nestedLeftBinds
//  """

  // Using mutable Spec here for now to easily run individual tests from Intellij

  "basic test" >> basicTest
  "foreach" >> foreachTrace
  "left-associative fold" >> leftAssociativeFold
  "nested left binds" >> nestedLeftBinds
  "fiber ancestry" >> fiberAncestry

  def basicTest = {
    val res = unsafeRun(for {
      _     <- ZIO.unit
      trace <- ZIO.trace
    } yield trace)

    res must_=== res
  }

  def foreachTrace = {
    val res = unsafeRun(for {
      _     <- ZIO.effectTotal(())
      _     <- ZIO.foreach_(1 to 10)(_ => ZIO.unit *> ZIO.trace)
      trace <- ZIO.trace
    } yield trace)

    System.err.println(res.prettyPrint)

    res must_=== res
  }

  def leftAssociativeFold = {
    def left(): ZIO[Any, Nothing, ZTrace] =
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

    System.err.println(res.prettyPrint)
    System.err.println(res.executionTrace.mkString("\n"))

    res must_=== res
  }

  def nestedLeftBinds = {

    def m2 = for {
      trace <- ZIO.trace
      _ <- UIO(println(trace.prettyPrint))
    } yield println()

    def m1 = for {
      _ <- m2
      _ <- ZIO.unit
    } yield ()

    def m0: ZIO[Any, Unit, Unit] = (for {
      _ <- m1
    } yield ())
      .foldM(
        failure = _ => IO.fail(()),
        success = _ => IO.trace
          .flatMap(t => UIO(println(t.prettyPrint)))
      )

    val res = unsafeRun(m0)

    res must_== (())
  }

  def fiberAncestry = {

    def m0 = for {
      _ <- m1.fork
    } yield ()

    def m1 = for {
      _ <- ZIO.unit
      _ <- ZIO.unit
      _ <- m2.fork
      _ <- ZIO.unit
    } yield ()

    def m2 = for {
      trace <- ZIO.trace
      _ <- UIO(println(trace.prettyPrint))
    } yield ()

    val res = unsafeRun(m0)

    res must_== (())
  }

}

//object x extends scala.App {
//  new StacktracesSpec()(null).nestedLeftBinds
//}
