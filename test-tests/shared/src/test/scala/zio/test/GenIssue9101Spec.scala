package zio.test

import zio._
import zio.test._
import zio.test.Assertion._

/**
 * Test for Issue #9101: zio.test.Gen generates the same data every time in certain conditions
 */
object GenIssue9101Spec extends ZIOSpecDefault {
  def spec = suite("Gen Issue #9101 Spec")(
    test("Gen should generate different UUIDs when fromIterable is used second") {
      val gen = for {
        id <- Gen.uuid
        _ <- Gen.fromIterable(LazyList.iterate(0)(_ + 1))
      } yield id

      // Generate multiple samples and verify they are different
      for {
        ids <- gen.sample.take(10).runCollect
      } yield assert(ids.map(_.value).distinct.length)(isGreaterThan(1))
    },
    test("Gen should generate different UUIDs when fromIterable is used first") {
      val gen = for {
        _ <- Gen.fromIterable(LazyList.iterate(0)(_ + 1))
        id <- Gen.uuid
      } yield id

      for {
        ids <- gen.sample.take(10).runCollect
      } yield assert(ids.map(_.value).distinct.length)(isGreaterThan(1))
    }
  )
}
