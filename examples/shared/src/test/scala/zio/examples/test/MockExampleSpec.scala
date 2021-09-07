package zio.examples.test

import zio.test.Assertion._
import zio.test.mock.Expectation.{unit, value, valueF}
import zio.test.mock.{MockClock, MockConsole, MockRandom}
import zio.test.{assertM, DefaultRunnableSpec}
import zio.{Clock, Console, Random, ZIO}

object MockExampleSpec extends DefaultRunnableSpec {

  def spec = suite("suite with mocks")(
    test("expect no call") {
      def maybeConsole(invokeConsole: Boolean) =
        ZIO.when(invokeConsole)(Console.printLine("foo"))

      val maybeTest1 = maybeConsole(false).unit.inject(MockConsole.empty)
      val maybeTest2 = maybeConsole(true).unit.inject(MockConsole.PrintLine(equalTo("foo"), unit))
      assertM(maybeTest1)(isUnit) *> assertM(maybeTest2)(isUnit)
    },
    test("expect no call on skipped branch") {
      def branchingProgram(predicate: Boolean) =
        ZIO.succeed(predicate).flatMap {
          case true  => Console.readLine
          case false => Clock.nanoTime
        }

      val clockLayer      = MockClock.NanoTime(value(42L)).toLayer
      val noCallToConsole = branchingProgram(false).inject(MockConsole.empty ++ clockLayer)

      val consoleLayer  = MockConsole.ReadLine(value("foo")).toLayer
      val noCallToClock = branchingProgram(true).inject(MockClock.empty ++ consoleLayer)
      assertM(noCallToConsole)(equalTo(42L)) *> assertM(noCallToClock)(equalTo("foo"))
    },
    test("expect no call on multiple skipped branches") {
      def branchingProgram(predicate: Boolean) =
        ZIO.succeed(predicate).flatMap {
          case true  => Console.readLine
          case false => Clock.nanoTime
        }

      def composedBranchingProgram(p1: Boolean, p2: Boolean) =
        branchingProgram(p1) <*> branchingProgram(p2)

      val clockLayer = (MockClock.NanoTime(value(42L)) andThen MockClock.NanoTime(value(42L))).toLayer
      val noCallToConsole = composedBranchingProgram(false, false)
        .inject(MockConsole.empty ++ clockLayer)

      val consoleLayer = (MockConsole.ReadLine(value("foo")) andThen MockConsole.ReadLine(value("foo"))).toLayer
      val noCallToClock = composedBranchingProgram(true, true)
        .inject(MockClock.empty ++ consoleLayer)

      assertM(noCallToConsole)(equalTo((42L, 42L))) *> assertM(noCallToClock)(equalTo(("foo", "foo")))
    },
    test("should fail if call for unexpected method") {
      def maybeConsole(invokeConsole: Boolean) =
        ZIO.when(invokeConsole)(Console.printLine("foo"))

      val maybeTest1 = maybeConsole(true).unit.inject(MockConsole.empty)
      val maybeTest2 = maybeConsole(false).unit.inject(MockConsole.PrintLine(equalTo("foo"), unit))
      assertM(maybeTest1)(isUnit) *> assertM(maybeTest2)(isUnit)
    },
    test("expect call returning output") {
      val app = Clock.nanoTime
      val env = MockClock.NanoTime(value(1000L))
      val out = app.provideLayer(env)
      assertM(out)(equalTo(1000L))
    },
    test("expect call with input satisfying assertion") {
      val app = Console.printLine("foo")
      val env = MockConsole.PrintLine(equalTo("foo"), unit)
      val out = app.provideLayer(env)
      assertM(out)(isUnit)
    },
    test("expect call with input satisfying assertion and transforming it into output") {
      val app = Random.nextIntBounded(1)
      val env = MockRandom.NextIntBounded(equalTo(1), valueF(_ + 41))
      val out = app.provideLayer(env)
      assertM(out)(equalTo(42))
    },
    test("expect call with input satisfying assertion and returning output") {
      val app = Random.nextIntBounded(1)
      val env = MockRandom.NextIntBounded(equalTo(1), value(42))
      val out = app.provideLayer(env)
      assertM(out)(equalTo(42))
    },
    test("expect call for overloaded method") {
      val app = Random.nextInt
      val env = MockRandom.NextInt(value(42))
      val out = app.provideLayer(env)
      assertM(out)(equalTo(42))
    },
    test("expect calls from multiple modules") {
      val app =
        for {
          n <- Random.nextInt
          _ <- Console.printLine(n.toString)
        } yield ()

      val env = MockRandom.NextInt(value(42)) andThen MockConsole.PrintLine(equalTo("42"), unit)
      val out = app.provideLayer(env)
      assertM(out)(isUnit)
    },
    test("expect repeated calls") {
      val app = Random.nextInt *> Random.nextInt
      val env = MockRandom.NextInt(value(42)).repeats(1 to 3)
      val out = app.provideLayer(env)
      assertM(out)(equalTo(42))
    },
    test("expect all of calls, in sequential order") {
      val app = Random.nextInt *> Random.nextLong
      val env = MockRandom.NextInt(value(42)) andThen MockRandom.NextLong(value(42L))
      val out = app.provideLayer(env)
      assertM(out)(equalTo(42L))
    },
    test("expect all of calls, in any order") {
      val app = Random.nextLong *> Random.nextInt
      val env = MockRandom.NextInt(value(42)) and MockRandom.NextLong(value(42L))
      val out = app.provideLayer(env)
      assertM(out)(equalTo(42))
    },
    test("expect one of calls") {
      val app = Random.nextLong
      val env = MockRandom.NextInt(value(42)) or MockRandom.NextLong(value(42L))
      val out = app.provideLayer(env)
      assertM(out)(equalTo(42L))
    },
    test("failure if invalid method") {
      val app = Random.nextInt
      val env = MockRandom.NextLong(value(42L))
      val out = app.provideLayer(env)
      assertM(out)(equalTo(42))
    },
    test("failure if invalid arguments") {
      val app = Console.printLine("foo")
      val env = MockConsole.PrintLine(equalTo("bar"), unit)
      val out = app.provideLayer(env)
      assertM(out)(isUnit)
    },
    test("failure if unmet expectations") {
      val app = Random.nextInt
      val env = MockRandom.NextInt(value(42)) andThen MockRandom.NextInt(value(42))
      val out = app.provideLayer(env)
      assertM(out)(equalTo(42))
    },
    test("failure if unexpected calls") {
      val app = Random.nextInt *> Random.nextLong
      val env = MockRandom.NextInt(value(42))
      val out = app.provideLayer(env)
      assertM(out)(equalTo(42L))
    }
  )
}
