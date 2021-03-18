package zio.examples.test

import zio.test.Assertion._
import zio.test.mock.Expectation.{value, valueF}
import zio.test.mock.{MockClock, MockConsole, MockRandom}
import zio.test.{assertM, DefaultRunnableSpec}
import zio.{Clock, Console, random, ZIO}

object MockExampleSpec extends DefaultRunnableSpec {

  def spec = suite("suite with mocks")(
    testM("expect no call") {
      def maybeConsole(invokeConsole: Boolean) =
        ZIO.when(invokeConsole)(Console.putStrLn("foo"))

      val maybeTest1 = maybeConsole(false).provideLayer(MockConsole.empty)
      val maybeTest2 = maybeConsole(true).provideLayer(MockConsole.PutStrLn(equalTo("foo")))
      assertM(maybeTest1)(isUnit) *> assertM(maybeTest2)(isUnit)
    },
    testM("expect no call on skipped branch") {
      def branchingProgram(predicate: Boolean) =
        ZIO.succeed(predicate).flatMap {
          case true  => Console.getStrLn
          case false => Clock.nanoTime
        }

      val clockLayer      = MockClock.NanoTime(value(42L)).toLayer
      val noCallToConsole = branchingProgram(false).provideLayer(MockConsole.empty ++ clockLayer)

      val consoleLayer  = MockConsole.GetStrLn(value("foo")).toLayer
      val noCallToClock = branchingProgram(true).provideLayer(MockClock.empty ++ consoleLayer)
      assertM(noCallToConsole)(equalTo(42L)) *> assertM(noCallToClock)(equalTo("foo"))
    },
    testM("expect no call on multiple skipped branches") {
      def branchingProgram(predicate: Boolean) =
        ZIO.succeed(predicate).flatMap {
          case true  => Console.getStrLn
          case false => Clock.nanoTime
        }

      def composedBranchingProgram(p1: Boolean, p2: Boolean) =
        branchingProgram(p1) &&& branchingProgram(p2)

      val clockLayer = (MockClock.NanoTime(value(42L)) andThen MockClock.NanoTime(value(42L))).toLayer
      val noCallToConsole = composedBranchingProgram(false, false)
        .provideLayer(MockConsole.empty ++ clockLayer)

      val consoleLayer = (MockConsole.GetStrLn(value("foo")) andThen MockConsole.GetStrLn(value("foo"))).toLayer
      val noCallToClock = composedBranchingProgram(true, true)
        .provideLayer(MockClock.empty ++ consoleLayer)

      assertM(noCallToConsole)(equalTo((42L, 42L))) *> assertM(noCallToClock)(equalTo(("foo", "foo")))
    },
    testM("should fail if call for unexpected method") {
      def maybeConsole(invokeConsole: Boolean) =
        ZIO.when(invokeConsole)(Console.putStrLn("foo"))

      val maybeTest1 = maybeConsole(true).provideLayer(MockConsole.empty)
      val maybeTest2 = maybeConsole(false).provideLayer(MockConsole.PutStrLn(equalTo("foo")))
      assertM(maybeTest1)(isUnit) *> assertM(maybeTest2)(isUnit)
    },
    testM("expect call returning output") {
      val app = Clock.nanoTime
      val env = MockClock.NanoTime(value(1000L))
      val out = app.provideLayer(env)
      assertM(out)(equalTo(1000L))
    },
    testM("expect call with input satisfying assertion") {
      val app = Console.putStrLn("foo")
      val env = MockConsole.PutStrLn(equalTo("foo"))
      val out = app.provideLayer(env)
      assertM(out)(isUnit)
    },
    testM("expect call with input satisfying assertion and transforming it into output") {
      val app = random.nextIntBounded(1)
      val env = MockRandom.NextIntBounded(equalTo(1), valueF(_ + 41))
      val out = app.provideLayer(env)
      assertM(out)(equalTo(42))
    },
    testM("expect call with input satisfying assertion and returning output") {
      val app = random.nextIntBounded(1)
      val env = MockRandom.NextIntBounded(equalTo(1), value(42))
      val out = app.provideLayer(env)
      assertM(out)(equalTo(42))
    },
    testM("expect call for overloaded method") {
      val app = random.nextInt
      val env = MockRandom.NextInt(value(42))
      val out = app.provideLayer(env)
      assertM(out)(equalTo(42))
    },
    testM("expect calls from multiple modules") {
      val app =
        for {
          n <- random.nextInt
          _ <- Console.putStrLn(n.toString)
        } yield ()

      val env = MockRandom.NextInt(value(42)) andThen MockConsole.PutStrLn(equalTo("42"))
      val out = app.provideLayer(env)
      assertM(out)(isUnit)
    },
    testM("expect repeated calls") {
      val app = random.nextInt *> random.nextInt
      val env = MockRandom.NextInt(value(42)).repeats(1 to 3)
      val out = app.provideLayer(env)
      assertM(out)(equalTo(42))
    },
    testM("expect all of calls, in sequential order") {
      val app = random.nextInt *> random.nextLong
      val env = MockRandom.NextInt(value(42)) andThen MockRandom.NextLong(value(42L))
      val out = app.provideLayer(env)
      assertM(out)(equalTo(42L))
    },
    testM("expect all of calls, in any order") {
      val app = random.nextLong *> random.nextInt
      val env = MockRandom.NextInt(value(42)) and MockRandom.NextLong(value(42L))
      val out = app.provideLayer(env)
      assertM(out)(equalTo(42))
    },
    testM("expect one of calls") {
      val app = random.nextLong
      val env = MockRandom.NextInt(value(42)) or MockRandom.NextLong(value(42L))
      val out = app.provideLayer(env)
      assertM(out)(equalTo(42L))
    },
    testM("failure if invalid method") {
      val app = random.nextInt
      val env = MockRandom.NextLong(value(42L))
      val out = app.provideLayer(env)
      assertM(out)(equalTo(42))
    },
    testM("failure if invalid arguments") {
      val app = Console.putStrLn("foo")
      val env = MockConsole.PutStrLn(equalTo("bar"))
      val out = app.provideLayer(env)
      assertM(out)(isUnit)
    },
    testM("failure if unmet expectations") {
      val app = random.nextInt
      val env = MockRandom.NextInt(value(42)) andThen MockRandom.NextInt(value(42))
      val out = app.provideLayer(env)
      assertM(out)(equalTo(42))
    },
    testM("failure if unexpected calls") {
      val app = random.nextInt *> random.nextLong
      val env = MockRandom.NextInt(value(42))
      val out = app.provideLayer(env)
      assertM(out)(equalTo(42L))
    }
  )
}
