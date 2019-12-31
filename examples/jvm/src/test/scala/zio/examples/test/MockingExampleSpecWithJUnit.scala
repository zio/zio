package zio.examples.test

import zio.test.Assertion._
import zio.test.junit.JUnitRunnableSpec
import zio.test.mock.Expectation.{unit, value, valueF}
import zio.test.mock.{MockClock, MockConsole, MockRandom}
import zio.test.{assertM, suite, testM}
import zio.{clock, console, random}

class MockingExampleSpecWithJUnit extends JUnitRunnableSpec {

  def spec = suite("suite with mocks")(
    testM("expect call returning output") {
      val app     = clock.nanoTime
      val mockEnv = MockClock.nanoTime returns value(1000L)
      val result  = app.provideManaged(mockEnv)
      assertM(result)(equalTo(1000L))
    },
    testM("expect call with input satisfying assertion") {
      val app     = console.putStrLn("foo")
      val mockEnv = MockConsole.putStrLn(equalTo("foo")) returns unit
      val result  = app.provideManaged(mockEnv)
      assertM(result)(isUnit)
    },
    testM("expect call with input satisfying assertion and transforming it into output") {
      val app     = random.nextInt(1)
      val mockEnv = MockRandom.nextInt._0(equalTo(1)) returns valueF(_ + 41)
      val result  = app.provideManaged(mockEnv)
      assertM(result)(equalTo(42))
    },
    testM("expect call with input satisfying assertion and returning output") {
      val app     = random.nextInt(1)
      val mockEnv = MockRandom.nextInt._0(equalTo(1)) returns value(42)
      val result  = app.provideManaged(mockEnv)
      assertM(result)(equalTo(42))
    },
    testM("expect call for overloaded method") {
      val app     = random.nextInt
      val mockEnv = MockRandom.nextInt._1 returns value(42)
      val result  = app.provideManaged(mockEnv)
      assertM(result)(equalTo(42))
    },
    testM("expect calls from multiple modules") {
      val app        = random.nextInt.map(_.toString) >>= console.putStrLn
      val randomEnv  = MockRandom.nextInt._1 returns value(42)
      val consoleEnv = MockConsole.putStrLn(equalTo("42")) returns unit

      val mockEnv = (randomEnv &&& consoleEnv).map {
        case (r, c) =>
          new MockRandom with MockConsole {
            val random  = r.random
            val console = c.console
          }
      }

      val result = app.provideManaged(mockEnv)
      assertM(result)(isUnit)
    },
    testM("failure if invalid method") {
      val app     = random.nextInt
      val mockEnv = MockRandom.nextLong._0 returns value(42)
      val result  = app.provideManaged(mockEnv)
      assertM(result)(equalTo(42))
    },
    testM("failure if invalid arguments") {
      val app     = console.putStrLn("foo")
      val mockEnv = MockConsole.putStrLn(equalTo("bar")) returns unit
      val result  = app.provideManaged(mockEnv)
      assertM(result)(isUnit)
    },
    testM("failure if unmet expectations") {
      val app = random.nextInt
      val mockEnv =
        (MockRandom.nextInt._1 returns value(42)) *>
          (MockRandom.nextInt._1 returns value(42))

      val result = app.provideManaged(mockEnv)
      assertM(result)(equalTo(42))
    }
  )
}
