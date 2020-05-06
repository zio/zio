package zio.test

import scala.math.abs

import zio.test.Assertion._
import zio.{ random, ZIO }

object FunSpec extends ZIOBaseSpec {

  def spec = suite("FunSpec")(
    testM("fun converts effects into pure functions") {
      for {
        f <- Fun.make((n: Int) => random.nextIntBounded(n))
        n <- random.nextInt.map(abs(_))
      } yield assert(f(n))(equalTo(f(n)))
    },
    testM("fun does not have race conditions") {
      for {
        f       <- Fun.make((_: Int) => random.nextIntBounded(6))
        results <- ZIO.foreachPar(List.range(0, 1000))(n => ZIO.effectTotal((n % 6, f(n % 6))))
      } yield assert(results.distinct.length)(equalTo(6))
    },
    testM("fun is showable") {
      for {
        f <- Fun.make((_: String) => random.nextBoolean)
        p = f("Scala")
        q = f("Haskell")
      } yield {
        assert(f.toString)(equalTo(s"Fun(Scala -> $p, Haskell -> $q)")) ||
        assert(f.toString)(equalTo(s"Fun(Haskell -> $q, Scala -> $p)"))
      }
    },
    testM("fun is supported on Scala.js") {
      for {
        f <- Fun.make((_: Int) => ZIO.foreach(List.range(0, 100000))(ZIO.succeed(_)))
      } yield assert(f(1))(anything)
    }
  )
}
