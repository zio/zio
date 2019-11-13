package zio.test

import zio.random.Random
import zio.test.Assertion._
import zio.test.GenSpecUtil._
import zio.test.TestAspect._
import zio.ZIO

object GenSpec
    extends ZIOBaseSpec(
      suite("GenSpec")(
        suite("zipWithPar")(
          testM("left preservation") {
            checkM(deterministic, deterministic) { (a, b) =>
              for {
                left  <- sample(a.zipPar(b).map(_._1))
                right <- sample(a)
              } yield assert(left, startsWith(right))
            }
          },
          testM("right preservation") {
            checkM(deterministic, deterministic) { (a, b) =>
              for {
                left  <- sample(a.zipPar(b).map(_._2))
                right <- sample(b)
              } yield assert(left, startsWith(right))
            }
          },
          testM("shrinking") {
            checkM(random, random) { (a, b) =>
              for {
                left  <- shrink(a.zipPar(b))
                right <- shrink(a.zip(b))
              } yield assert(left, equalTo(right))
            }
          }
        )
      ) @@ scala2Only
    )

object GenSpecUtil {

  val deterministic: Gen[Random with Sized, Gen[Any, Int]] =
    Gen.listOf1(Gen.int(-10, 10)).map(as => Gen.fromIterable(as))

  val random: Gen[Any, Gen[Random, Int]] =
    Gen.const(Gen.int(-10, 10))

  def sample[R, A](gen: Gen[R, A]): ZIO[R, Nothing, List[A]] =
    gen.sample.map(_.value).runCollect

  def shrink[R, A](gen: Gen[R, A]): ZIO[R, Nothing, A] =
    gen.sample.take(1).flatMap(_.shrinkSearch(_ => true)).take(1000).runLast.map(_.get)
}
