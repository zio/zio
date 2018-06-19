package scalaz.ioeffect

import cats.Eq
import cats.effect.laws.util.TestInstances
import cats.syntax.all._
import org.scalacheck.{ Arbitrary, Cogen, Gen }

import catz._

trait IOScalaCheckInstances extends TestInstances {

  implicit def cogenTask[A]: Cogen[Task[A]] =
    Cogen[Unit].contramap(_ => ()) // YOLO

  //Todo: define in terms of `unsafeRunAsync` which John said he'll add
  // This for sure doesn't mean that for x: IO[A] y: IO[A], x === y.
  // It's nonsensical in a theoretical sense anyway to compare to IO values
  // In the presence of side effects, so I consider this more for the sake of
  // A test than a "law" in itself.
  implicit def catsEQ[A](implicit E: Eq[A]): Eq[Task[A]] =
    new Eq[Task[A]] {
      def eqv(x: Task[A], y: Task[A]): Boolean =
        (tryUnsafePerformIO(x), tryUnsafePerformIO(y)) match {
          case (ExitResult.Completed(x1), ExitResult.Completed(y1)) => x1 === y1
          case (ExitResult.Failed(x1), ExitResult.Failed(y1))       => x1 === y1
          case (ExitResult.Terminated(x1), ExitResult.Terminated(y1)) =>
            x1 === y1
          case _ => false
        }
    }

  implicit def arbitraryIO[A](implicit A: Arbitrary[A], CG: Cogen[A]): Arbitrary[Task[A]] = {
    import Arbitrary._
    def genPure: Gen[Task[A]] =
      arbitrary[A].map(IO.now)

    def genApply: Gen[Task[A]] =
      arbitrary[A].map(IO.syncThrowable[A](_))

    def genFail: Gen[Task[A]] =
      arbitrary[Throwable].map(IO.fail[Throwable, A])

    def genAsync: Gen[Task[A]] =
      arbitrary[(Either[Throwable, A] => Unit) => Unit]
        .map(catsEffectInstance.async[A])

    def genNestedAsync: Gen[Task[A]] =
      arbitrary[(Either[Throwable, Task[A]] => Unit) => Unit]
        .map(k => catsEffectInstance.async(k).flatMap(x => x))

    def genSimpleTask: Gen[Task[A]] = Gen.frequency(
      1 -> genPure,
      1 -> genApply,
      1 -> genFail,
      1 -> genAsync,
      1 -> genNestedAsync,
      1 -> genBindSuspend
    )

    def genBindSuspend: Gen[Task[A]] =
      arbitrary[A].map(IO.syncThrowable(_).flatMap(IO.now))

    def genFlatMap: Gen[Task[A]] =
      for {
        ioa <- genSimpleTask
        f   <- arbitrary[A => Task[A]]
      } yield ioa.flatMap(f)

    def getMapOne: Gen[Task[A]] =
      for {
        ioa <- genSimpleTask
        f   <- arbitrary[A => A]
      } yield ioa.map(f)

    def getMapTwo: Gen[Task[A]] =
      for {
        ioa <- genSimpleTask
        f1  <- arbitrary[A => A]
        f2  <- arbitrary[A => A]
      } yield ioa.map(f1).map(f2)

    Arbitrary(
      Gen.frequency(
        5  -> genPure,
        5  -> genApply,
        1  -> genFail,
        5  -> genBindSuspend,
        5  -> genAsync,
        5  -> genNestedAsync,
        5  -> getMapOne,
        5  -> getMapTwo,
        10 -> genFlatMap
      )
    )
  }

}
