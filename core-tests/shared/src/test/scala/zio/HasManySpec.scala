package zio

import zio.test._
import zio.test.Assertion._

object HasManySpec extends ZIOSpecDefault {

  def spec = suite("HasManySpec")(
    suite("accessAt")(
      test("access a service at a single key") {
        val zio1 = ZIO.serviceAt[Int]("Jane Doe")
        assertM(zio1.provide(Has(Map("Jane Doe" -> 42))))(isSome(equalTo(42)))
      },
      test("access services at multiple keys") {
        val zio1 = ZIO.serviceAt[Int]("Jane Doe")
        val zio2 = ZIO.serviceAt[Int]("John Doe")
        val zio3 = zio1 <*> zio2
        assertM(zio3.provide(Has(Map("Jane Doe" -> 42, "John Doe" -> 43))))(equalTo((Some(42), Some(43))))
      }
    ),
    test("composition") {
      val zio1 = ZIO.serviceAt[Int]("Jane Doe")
      val zio2 = ZIO.service[Int]
      val zio3 = zio1 <*> zio2
      assertM(zio3.provide(Has(Map("Jane Doe" -> 42)) ++ Has(43)))(equalTo((Some(42), 43)))
    },
    suite("updateServiceAt")(
      test("updates the service at exists at the specified key") {
        val zio1 = ZIO.serviceAt[Int]("Jane Doe").updateServiceAt[Int]("Jane Doe")(_ + 1)
        assertM(zio1.provide(Has(Map("Jane Doe" -> 42))))(equalTo((Some(43))))
      },
      test("does nothing if the service does not exist") {
        val zio1 = ZIO.serviceAt[Int]("Jane Doe").updateServiceAt[Int]("John Doe")(_ + 1)
        assertM(zio1.provide(Has(Map("Jane Doe" -> 42))))(equalTo((Some(42))))
      },
      test("updates multiple services") {
        val zio1 = ZIO.serviceAt[Int]("Jane Doe")
        val zio2 = ZIO.serviceAt[Double](false)
        val zio3 = zio1 <*> zio2
        val zio4 = zio3.updateServiceAt[Int]("Jane Doe")(_ + 1).updateServiceAt[Double](false)(_ + 1.0)
        assertM(zio4.provide(Has(Map("Jane Doe" -> 42)) ++ Has(Map(false -> 0.0))))(equalTo((Some(43), Some(1.0))))
      }
    )
  )
}
