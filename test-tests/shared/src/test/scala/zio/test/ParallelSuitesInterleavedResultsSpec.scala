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
  override def spec = suite("SM")(
    suite("SMS")(
      test("SMS 1") {
        assertTrue(true)
      }
    )
  )
}

object SlowMinimalSpec extends ZIOSpecDefault {
  override def spec = suite("SS")(
    suite("SSFast ")(
      test("SSF 1") {
        Live.live(ZIO.sleep(1.second)).map(_ => assertTrue(true))
      },
      test("SSF 2") {
        Live.live(ZIO.sleep(1.second)).map(_ => assertTrue(true))
      }
    ),
    suite("SSMedium")(
      test("SSM 1") {
        Live.live(ZIO.sleep(1.second)).map(_ => assertTrue(true))
      },
      test("SSM 2") {
        Live.live(ZIO.sleep(2.second)).map(_ => assertTrue(true))
      }
    ),
    suite("SSSlow")(
      test("SSS 1") {
        Live.live(ZIO.sleep(2.second)).map(_ => assertTrue(true))
      },
      test("SSS 2") {
        Live.live(ZIO.sleep(3.second)).map(_ => assertTrue(true))
      }
    )
  )
}
