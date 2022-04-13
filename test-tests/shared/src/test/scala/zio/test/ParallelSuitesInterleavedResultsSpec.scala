package zio.test

import zio._

object AMinimalSpec extends ZIOSpecDefault {
  override val layer = ZLayer.fromZIO(ZIO.attempt(???))

  override def spec = suite("ASpec")(
    test("test before delay") {
      Live.live(ZIO.sleep(1.second)).map(_ => assertTrue(true))
    },
    test("test after delay") {
      Live.live(ZIO.sleep(3.second)).map(_ => assertTrue(true))
    },
    test("test after big delay") {
      Live.live(ZIO.sleep(5.second)).map(_ => assertTrue(true))
    }
  ) @@ TestAspect.ignore

}

object BMinimalSpec extends ZIOSpec[Int] {
  override val layer = ZLayer.fromZIO(ZIO.sleep(2.seconds) *> ZIO.succeed(42))
  override def spec = suite("BSpec")(
    test("B 1") {
      Live.live(ZIO.sleep(1.second)).map(_ => assertTrue(true))
    },
    test("B 2") {
      Live.live(ZIO.sleep(1.second)).map(_ => assertTrue(true))
    }
  ) @@ TestAspect.ignore
}

object MultiCMinimalSpec extends ZIOSpecDefault {
  override def spec = suite("MultiSpec")(
    suite("fast inner suite")(
      test("fast test 1") {
        assertTrue(true)
      },
      test("fast test 2") {
        assertTrue(true)
      }
    ),
    suite("slow suite")(
      test("slow 1") {
        Live.live(ZIO.sleep(1.second)).map(_ => assertTrue(true))
      },
      test("slow 2") {
        Live.live(ZIO.sleep(3.second)).map(_ => assertTrue(true))
      }
    )
  ) @@ TestAspect.ignore
}

object SmallMinimalSpec extends ZIOSpecDefault {
  override def spec = suite("SmallMultiSpec")(
    suite("fast inner suite")(
      test("fast test 1") {
        assertTrue(true)
      }
    ),
    suite("slow suite")(
      test("slow 1") {
        Live.live(ZIO.sleep(1.second)).map(_ => assertTrue(true))
      }
    )
  ) @@ TestAspect.ignore
}

object SlowMinimalSpec extends ZIOSpecDefault {
  override def spec = suite("SM")(
    suite("SMFast ")(
      test("SMF 1") {
        Live.live(ZIO.sleep(1.second)).map(_ => assertTrue(true))
      },
      test("SMF 2") {
        Live.live(ZIO.sleep(1.second)).map(_ => assertTrue(true))
      }
    ),
    suite("SMMedium")(
      test("SMM 1") {
        Live.live(ZIO.sleep(1.second)).map(_ => assertTrue(true))
      },
      test("SMM 2") {
        Live.live(ZIO.sleep(2.second)).map(_ => assertTrue(true))
      }
    ),
    suite("SMSlow")(
      test("SMS 1") {
        Live.live(ZIO.sleep(2.second)).map(_ => assertTrue(true))
      },
      test("SMS 2") {
        Live.live(ZIO.sleep(3.second)).map(_ => assertTrue(true))
      }
    )
  ) @@ TestAspect.ignore
}

object SingleMinimalSpec extends ZIOSpecDefault {
  override def spec =
    test("Single spec not in a suite") {
      Live.live(ZIO.sleep(2.second)).map(_ => assertTrue(true))
    } @@ TestAspect.ignore

}
