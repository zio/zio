package zio.examples.test

import zio.console.Console
import zio.random.Random
import zio.test.Assertion._
import zio.test.environment.TestEnvironment
import zio.test.mock.Expectation.{ unit, value, valueF }
import zio.test.mock.{ MockClock, MockConsole, MockRandom }
import zio.test.{ assertM, suite, testM, DefaultRunnableSpec }
import zio.{ clock, console, random }

object MockingExampleSpec extends DefaultRunnableSpec {

  def spec = suite("suite with mocks")(
    testM("expect call returning output") {
      import MockClock._

      val app     = clock.nanoTime
      val mockEnv = nanoTime returns value(1000L)
      val result  = app.provideLayer(mockEnv)
      assertM(result)(equalTo(1000L))
    },
    testM("expect call with input satisfying assertion") {
      import MockConsole._

      val app     = console.putStrLn("foo")
      val mockEnv = putStrLn(equalTo("foo")) returns unit
      val result  = app.provideLayer(mockEnv)
      assertM(result)(isUnit)
    },
    testM("expect call with input satisfying assertion and transforming it into output") {
      import MockRandom._

      val app     = random.nextInt(1)
      val mockEnv = nextInt._0(equalTo(1)) returns valueF(_ + 41)
      val result  = app.provideLayer(mockEnv)
      assertM(result)(equalTo(42))
    },
    testM("expect call with input satisfying assertion and returning output") {
      import MockRandom._

      val app     = random.nextInt(1)
      val mockEnv = nextInt._0(equalTo(1)) returns value(42)
      val result  = app.provideLayer(mockEnv)
      assertM(result)(equalTo(42))
    },
    testM("expect call for overloaded method") {
      import MockRandom._

      val app     = random.nextInt
      val mockEnv = nextInt._1 returns value(42)
      val result  = app.provideLayer(mockEnv)
      assertM(result)(equalTo(42))
    },
    testM("expect calls from multiple modules") {
      import MockConsole._
      import MockRandom._

      val app        = random.nextInt.map(_.toString) >>= console.putStrLn
      val randomEnv  = nextInt._1 returns value(42)
      val consoleEnv = putStrLn(equalTo("42")) returns unit

      val result = app.provideLayer[Nothing, TestEnvironment, Random with Console](randomEnv ++ consoleEnv)
      assertM(result)(isUnit)
    },
    testM("failure if invalid method") {
      import MockRandom._

      val app     = random.nextInt
      val mockEnv = nextLong._0 returns value(42)
      val result  = app.provideLayer(mockEnv)
      assertM(result)(equalTo(42))
    },
    testM("failure if invalid arguments") {
      import MockConsole._

      val app     = console.putStrLn("foo")
      val mockEnv = putStrLn(equalTo("bar")) returns unit
      val result  = app.provideLayer(mockEnv)
      assertM(result)(isUnit)
    },
    testM("failure if unmet expectations") {
      import MockRandom._

      val app = random.nextInt
      val mockEnv =
        (nextInt._1 returns value(42)) *>
          (nextInt._1 returns value(42))

      val result = app.provideLayer(mockEnv)
      assertM(result)(equalTo(42))
    }
  )
}
