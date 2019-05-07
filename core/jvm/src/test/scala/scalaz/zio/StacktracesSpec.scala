package scalaz.zio

import org.specs2.mutable
import scalaz.zio.stacktracer.SourceLocation

class StacktracesSpec(implicit ee: org.specs2.concurrent.ExecutionEnv)
    extends TestRuntime
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
  "foreach fail" >> foreachFail
  "foreachPar fail" >> foreachParFail
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

  def foreachFail = {

    def res() =
      unsafeRunSync(for {
        _ <- ZIO
              .foreach_(1 to 10) { i =>
                if (i == 7)
                  ZIO.unit *> // FIXME: flatMap required to get the line...
                    ZIO.fail("Dummy error!")
                else
                  ZIO.unit *> // FIXME: flatMap required to get the line...
                    ZIO.trace
              }
              .foldCauseM(e => UIO(println(e)), _ => ZIO.unit)
        trace <- ZIO.trace
        _     <- UIO(println(trace.prettyPrint))
        _     <- UIO(println(trace))
      } yield ())

    res() must_!= Exit.fail("Dummy error!")
  }

  def foreachParFail = {

    import duration._
    def res() =
      unsafeRunSync(for {
        _ <- ZIO.foreachPar(1 to 10) { i =>
              ZIO.sleep(1.second) *> (if (i >= 7) UIO(i / 0) else UIO(i / 10))
            }
      } yield ())

    res().fold(_.traces.head.stackTrace, _ => Nil) must have size 1 and contain {
      (_: SourceLocation).method.exists(_ contains "foreachParFail")
    }
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

    def m2 =
      for {
        trace <- ZIO.trace
        _     <- UIO(println(trace.prettyPrint))
      } yield println()

    def m1 =
      for {
        _ <- m2
        _ <- ZIO.unit
      } yield ()

    def m0: ZIO[Any, Unit, Unit] =
      (for {
        _ <- m1
      } yield ())
        .foldM(
          failure = _ => IO.fail(()),
          success = _ =>
            IO.trace
              .flatMap(t => UIO(println(t.prettyPrint)))
        )

    val res = unsafeRun(m0)

    res must_== (())
  }

  def fiberAncestry = {

    def m0 =
      for {
        _ <- m1.fork
      } yield ()

    def m1 =
      for {
        _ <- ZIO.unit
        _ <- ZIO.unit
        _ <- m2.fork
        _ <- ZIO.unit
      } yield ()

    def m2 =
      for {
        trace <- ZIO.trace
        _     <- UIO(println(trace.prettyPrint))
      } yield ()

    val res = unsafeRun(m0)

    res must_== (())
  }

}
