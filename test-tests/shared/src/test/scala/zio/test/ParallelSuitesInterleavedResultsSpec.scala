package zio.test

import zio._

object SpecWithBrokenLayer extends ZIOSpecDefault {
  override val layer = ZLayer.fromZIO(ZIO.attempt(???))
  override def spec =
    test("a") {
      assertTrue(true)
    }
}

object Spec1 extends ZIOSpecDefault {
  override def spec =
    test("Single 1") {
      assertTrue(true)
    }
}
object Spec2 extends ZIOSpecDefault {
  override def spec =
    test("Single 2") {
      assertTrue(true)
    }
}

object BMinimalSpec extends ZIOSpecDefault {
//  override val layer = ZLayer.fromZIO(ZIO.sleep(2.seconds) *> ZIO.succeed(42))
  override def spec = suite("BSpec")(
    test("B 1") {
      Live.live(ZIO.sleep(1.second)).map(_ => assertTrue(true))
    },
    test("B 2") {
      Live.live(ZIO.sleep(1.second)).map(_ => assertTrue(true))
    }
  )
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
