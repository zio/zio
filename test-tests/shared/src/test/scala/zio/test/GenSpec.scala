package zio.test

import zio.random.Random
import zio.test.Assertion._
import zio.test.GenSpecUtil._
import zio.ZIO

object GenSpec
    extends ZIOBaseSpec(
      suite("GenSpec")(
        suite("zipWithPar")(
          testM("left preservation") {
            checkM(gens, gens) { (a, b) =>
              assertSamePrefix(a.zipPar(b).map(_._1), a)
            }
          },
          testM("right preservation") {
            checkM(gens, gens) { (a, b) =>
              assertSamePrefix(a.zipPar(b).map(_._2), b)
            }
          }
        )
      )
    )

object GenSpecUtil {

  val gens: Gen[Random with Sized, Gen[Any, Int]] =
    Gen.listOf1(Gen.int(-10, 10)).map(as => Gen.fromIterable(as))

  def assertSamePrefix[R, A](left: Gen[R, A], right: Gen[R, A]): ZIO[R, Nothing, TestResult] =
    for {
      l <- left.sample.map(_.value).runCollect
      r <- right.sample.map(_.value).runCollect
      n = l.length.min(r.length)
    } yield assert(l.take(n), equalTo(r.take(n)))
}
