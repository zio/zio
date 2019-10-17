package zio.examples.test

import zio.{ clock, console, random, Managed }
import zio.test.{ assertM, suite, testM, DefaultRunnableSpec }
import zio.test.Assertion._
import zio.test.mock.{ MockClock, MockConsole, MockRandom, MockSpec }

object MockingExampleSpec
    extends DefaultRunnableSpec(
      suite("suite with mocks")(
        testM("expect call returning output") {
          val app = clock.nanoTime
          val mockEnv: Managed[Nothing, MockClock] =
            MockSpec.expectOut(MockClock.Service.nanoTime)(1000L)

          val result = app.provideManaged(mockEnv)
          assertM(result, equalTo(1000L))
        },
        testM("expect call with input satisfying assertion") {
          val app = console.putStrLn("foo")
          val mockEnv: Managed[Nothing, MockConsole] =
            MockSpec.expectIn(MockConsole.Service.putStrLn)(equalTo("foo"))

          val result = app.provideManaged(mockEnv)
          assertM(result, isUnit)
        },
        testM("expect call with input satisfying assertion and transforming it into output") {
          val app = random.nextInt(1)
          val mockEnv: Managed[Nothing, MockRandom] =
            MockSpec.expect(MockRandom.Service.nextInt._0)(equalTo(1))(_ + 41)

          val result = app.provideManaged(mockEnv)
          assertM(result, equalTo(42))
        },
        testM("expect call with input satisfying assertion and returning output") {
          val app = random.nextInt(1)
          val mockEnv: Managed[Nothing, MockRandom] =
            MockSpec.expect_(MockRandom.Service.nextInt._0)(equalTo(1))(42)

          val result = app.provideManaged(mockEnv)
          assertM(result, equalTo(42))
        },
        testM("expect call for overloaded method") {
          val app = random.nextInt
          val mockEnv: Managed[Nothing, MockRandom] =
            MockSpec.expectOut(MockRandom.Service.nextInt._1)(42)

          val result = app.provideManaged(mockEnv)
          assertM(result, equalTo(42))
        },
        testM("expect calls from multiple modules") {
          val app = random.nextInt.map(_.toString) >>= console.putStrLn

          val randomEnv: Managed[Nothing, MockRandom] =
            MockSpec.expectOut(MockRandom.Service.nextInt._1)(42)

          val consoleEnv: Managed[Nothing, MockConsole] =
            MockSpec.expectIn(MockConsole.Service.putStrLn)(equalTo("42"))

          val mockEnv = (randomEnv &&& consoleEnv).map { case (r, c) => new MockRandom with MockConsole {
            val random  = r.random
            val console = c.console
          }}

          val result = app.provideManaged(mockEnv)
          assertM(result, isUnit)
        },
        testM("failure if invalid method") {
          val app = random.nextInt
          val mockEnv: Managed[Nothing, MockRandom] =
            MockSpec.expectOut(MockRandom.Service.nextLong._0)(42)

          val result = app.provideManaged(mockEnv)
          assertM(result, equalTo(42))
        },
        testM("failure if invalid arguments") {
          val app = console.putStrLn("foo")
          val mockEnv: Managed[Nothing, MockConsole] =
            MockSpec.expectIn(MockConsole.Service.putStrLn)(equalTo("bar"))

          val result = app.provideManaged(mockEnv)
          assertM(result, isUnit)
        },
        testM("failure if unmet expectations") {
          val app = random.nextInt
          val mockEnv: Managed[Nothing, MockRandom] =
            MockSpec.expectOut(MockRandom.Service.nextInt._1)(42) *>
            MockSpec.expectOut(MockRandom.Service.nextInt._1)(42)

          val result = app.provideManaged(mockEnv)
          assertM(result, equalTo(42))
        }
      )
    )
