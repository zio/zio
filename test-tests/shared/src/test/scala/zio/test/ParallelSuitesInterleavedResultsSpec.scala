package zio.test

import zio._

object MultiCMinimalSpec extends ZIOSpecDefault {
  override def spec = suite("M")(
    suite("MF")(
      test("MF 1") {
        assertTrue(false)
      },
      test("MF 2") {
        assertTrue(true)
      }
    ),
    suite("MS")(
      test("MS 1") {
        Live.live(ZIO.sleep(1.second)).map(_ => assertTrue(true))
      },
      test("MS 2") {
        Live.live(ZIO.sleep(3.second)).map(_ => assertTrue(true))
      }
    )
  )
}

object SmallMinimalSpec extends ZIOSpecDefault {
  override def spec = suite("A")(
    suite("AS")(
      test("AS 1") {
        assertTrue(true)
      }
    )
  )
}

object SlowMinimalSpec extends ZIOSpecDefault {
  override def spec = suite("F")(
    suite("F1")(
      test("F1 1") {
        Live.live(ZIO.sleep(1.second)).map(_ => assertTrue(true))
      },
      test("F1 2") {
        Live.live(ZIO.sleep(1.second)).map(_ => assertTrue(true))
      }
    ),
    suite("F2")(
      test("F2 1") {
        Live.live(ZIO.sleep(1.second)).map(_ => assertTrue(true))
      },
      test("F2 2") {
        Live.live(ZIO.sleep(2.second)).map(_ => assertTrue(true))
      }
    ),
    suite("F3")(
      test("F3 1") {
        Live.live(ZIO.sleep(2.second)).map(_ => assertTrue(true))
      },
      test("F3 2") {
        Live.live(ZIO.sleep(3.second)).map(_ => assertTrue(true))
      }
    )
  )
}
