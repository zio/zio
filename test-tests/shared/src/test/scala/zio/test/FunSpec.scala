package zio.test

import zio.test.Assertion._
import zio.{Random, ZIO}

import scala.math.abs

object FunSpec extends ZIOBaseSpec {

  def spec: ZSpec[Environment, Failure] = suite("FunSpec")(
    testM("fun converts effects into pure functions") {
      for {
        f <- Fun.make((n: Int) => Random.nextIntBounded(n))
        n <- Random.nextInt.map(abs(_))
      } yield assert(f(n))(equalTo(f(n)))
    },
    testM("fun does not have race conditions") {
      for {
        f       <- Fun.make((_: Int) => Random.nextIntBounded(6))
        results <- ZIO.foreachPar(List.range(0, 1000))(n => ZIO.effectTotal((n % 6, f(n % 6))))
      } yield assert(results.distinct.length)(equalTo(6))
    },
    testM("fun is showable") {
      for {
        f <- Fun.make((_: String) => Random.nextBoolean)
        p  = f("Scala")
        q  = f("Haskell")
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
