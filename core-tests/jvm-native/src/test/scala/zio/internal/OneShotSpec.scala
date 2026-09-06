package zio.internal

import zio.ZIOBaseSpec
import zio.test.Assertion._
import zio.test.TestAspect.blocking
import zio.test._

object OneShotSpec extends ZIOBaseSpec {

  def spec = suite("OneShotSpec")(
    suite("OneShotSpec")(
      test("set must accept a non-null value") {
        val oneShot = OneShot.make[Int]
        oneShot.set(1)

        assert(oneShot.get())(equalTo(1))
      },
      test("set must not accept a null value") {
        val oneShot = OneShot.make[Object]

        assert(oneShot.set(null))(throwsA[Error])
      },
      test("isSet must report if a value is set") {
        val oneShot = OneShot.make[Int]

        val resultBeforeSet = oneShot.isSet

        oneShot.set(1)

        val resultAfterSet = oneShot.isSet

        assert(resultBeforeSet)(isFalse) && assert(resultAfterSet)(isTrue)
      },
      test("cannot set value twice") {
        val oneShot = OneShot.make[Int]
        oneShot.set(1)

        assert(oneShot.set(2))(throwsA[Error])
      },
      test("cannot set value while another thread holds the lock") {
        val oneShot = OneShot.make[Unit]
        val lock    = OneShot.make[Unit]

        val t = new Thread(() => {
          lock.set(())
          oneShot.set(())
        })

        oneShot.lock()
        t.start()
        lock.get()
        Thread.sleep(10L)
        val isSet = oneShot.isSet
        oneShot.unlock()

        assertTrue(!isSet)
      },
      suite("get(timeout)")(
        test("succeeds if value is set within the timeout") {
          val oneShot = OneShot.make[Int]

          new Thread(() => {
            Thread.sleep(10L)
            oneShot.set(1)
          }).start()

          assert(oneShot.get(20L))(equalTo(1))
        },
        test("fails if no value is set") {
          val oneShot = OneShot.make[Object]

          assert(oneShot.get(10L))(throwsA[OneShot.TimeoutException])
        }
      ) @@ blocking,
      suite("tryGet(timeout)")(
        test("succeeds if value is set within the timeout") {
          val oneShot = OneShot.make[Int]

          new Thread(() => {
            Thread.sleep(10L)
            oneShot.set(1)
          }).start()

          assert(oneShot.tryGet(20L))(equalTo(1))
        },
        test("returns null if the value is not set within the timeout") {
          val oneShot = OneShot.make[Object]

          assert(oneShot.tryGet(10L))(equalTo(null))
        }
      ) @@ blocking
    )
  )
}
