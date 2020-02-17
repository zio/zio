package zio.examples.test

import zio.test.Assertion._
import zio.test.junit.JUnitRunnableSpec
import zio.test.mock.Expectation.{unit, value, valueF}
import zio.test.mock.{MockClock, MockConsole, MockRandom}
import zio.test.{assertM, suite, testM}
import zio.{clock, console, random}

class MockExampleSpecWithJUnit extends JUnitRunnableSpec {

  def spec = suite("suite with mocks")(
    testM("expect call returning output") {
      val app = clock.nanoTime
      val env = MockClock.nanoTime returns value(1000L)
      val out = app.provideLayer(env)
      assertM(out)(equalTo(1000L))
    },
    testM("expect call with input satisfying assertion") {
      val app = console.putStrLn("foo")
      val env = MockConsole.putStrLn(equalTo("foo")) returns unit
      val out = app.provideLayer(env)
      assertM(out)(isUnit)
    },
    testM("expect call with input satisfying assertion and transforming it into output") {
      val app = random.nextInt(1)
      val env = MockRandom.nextInt._0(equalTo(1)) returns valueF(_ + 41)
      val out = app.provideLayer(env)
      assertM(out)(equalTo(42))
    },
    testM("expect call with input satisfying assertion and returning output") {
      val app = random.nextInt(1)
      val env = MockRandom.nextInt._0(equalTo(1)) returns value(42)
      val out = app.provideLayer(env)
      assertM(out)(equalTo(42))
    },
    testM("expect call for overloaded method") {
      val app = random.nextInt
      val env = MockRandom.nextInt._1 returns value(42)
      val out = app.provideLayer(env)
      assertM(out)(equalTo(42))
    },
    testM("expect calls from multiple modules") {
      val app =
        for {
          n <- random.nextInt
          _ <- console.putStrLn(n.toString)
        } yield ()

      val env = (MockRandom.nextInt._1 returns value(42)) andThen (MockConsole.putStrLn(equalTo("42")) returns unit)
      val out = app.provideLayer(env)
      assertM(out)(isUnit)
    },
    testM("expect repeated calls") {
      val app = random.nextInt *> random.nextInt
      val env = (MockRandom.nextInt._1 returns value(42)).repeats(1 to 3)
      val out = app.provideLayer(env)
      assertM(out)(equalTo(42))
    },
    testM("expect all of calls, in sequential order") {
      val app = random.nextInt *> random.nextLong
      val env = (MockRandom.nextInt._1 returns value(42)) andThen (MockRandom.nextLong._0 returns value(42L))
      val out = app.provideLayer(env)
      assertM(out)(equalTo(42L))
    },
    testM("expect all of calls, in any order") {
      val app = random.nextLong *> random.nextInt
      val env = (MockRandom.nextInt._1 returns value(42)) and (MockRandom.nextLong._0 returns value(42L))
      val out = app.provideLayer(env)
      assertM(out)(equalTo(42))
    },
    testM("expect one of calls") {
      val app = random.nextLong
      val env = (MockRandom.nextInt._1 returns value(42)) or (MockRandom.nextLong._0 returns value(42L))
      val out = app.provideLayer(env)
      assertM(out)(equalTo(42L))
    },
    testM("failure if invalid method") {
      val app = random.nextInt
      val env = MockRandom.nextLong._0 returns value(42)
      val out = app.provideLayer(env)
      assertM(out)(equalTo(42))
    },
    testM("failure if invalid arguments") {
      val app = console.putStrLn("foo")
      val env = MockConsole.putStrLn(equalTo("bar")) returns unit
      val out = app.provideLayer(env)
      assertM(out)(isUnit)
    },
    testM("failure if unmet expectations") {
      val app = random.nextInt
      val env = (MockRandom.nextInt._1 returns value(42)) andThen (MockRandom.nextInt._1 returns value(42))
      val out = app.provideLayer(env)
      assertM(out)(equalTo(42))
    },
    testM("failure if unexpected calls") {
      val app = random.nextInt *> random.nextLong
      val env = MockRandom.nextInt._1 returns value(42)
      val out = app.provideLayer(env)
      assertM(out)(equalTo(42L))
    }
  )
}
