package zio

import zio.test.Assertion._
import zio.test.TestAspect.{jvm, nonFlaky}
import zio.test._

object RefSpec extends ZIOBaseSpec {

  def spec = suite("RefSpec")(
    suite("Atomic")(
      test("race") {
        for {
          _ <- ZIO.unit.race(ZIO.unit)
        } yield assertCompletes
      } @@ jvm(nonFlaky),
      test("get") {
        for {
          ref   <- Ref.make(current)
          value <- ref.get
        } yield assert(value)(equalTo(current))
      },
      test("getAndSet") {
        for {
          ref    <- Ref.make(current)
          value1 <- ref.getAndSet(update)
          value2 <- ref.get
        } yield assert(value1)(equalTo(current)) && assert(value2)(equalTo(update))
      },
      test("getAndUpdate") {
        for {
          ref    <- Ref.make(current)
          value1 <- ref.getAndUpdate(_ => update)
          value2 <- ref.get
        } yield assert(value1)(equalTo(current)) && assert(value2)(equalTo(update))
      },
      test("getAndUpdateSome") {
        for {
          ref    <- Ref.make[State](Active)
          value1 <- ref.getAndUpdateSome { case Closed => Changed }
          value2 <- ref.get
        } yield assert(value1)(equalTo(Active)) && assert(value2)(equalTo(Active))
      },
      test("getAndUpdateSome twice") {
        for {
          ref    <- Ref.make[State](Active)
          value1 <- ref.getAndUpdateSome { case Active => Changed }
          value2 <- ref.getAndUpdateSome {
                      case Active  => Changed
                      case Changed => Closed
                    }
          value3 <- ref.get
        } yield assert(value1)(equalTo(Active)) && assert(value2)(equalTo(Changed)) && assert(value3)(equalTo(Closed))
      },
      test("modify") {
        for {
          ref   <- Ref.make(current)
          r     <- ref.modify(_ => ("hello", update))
          value <- ref.get
        } yield assert(r)(equalTo("hello")) && assert(value)(equalTo(update))
      },
      test("modifySome") {
        for {
          ref   <- Ref.make[State](Active)
          value <- ref.modifySome("State doesn't change") { case Closed => ("active", Active) }
        } yield assert(value)(equalTo("State doesn't change"))
      },
      test("modifySome twice") {
        for {
          ref    <- Ref.make[State](Active)
          value1 <- ref.modifySome("doesn't change the state") { case Active => ("changed", Changed) }
          value2 <- ref.modifySome("doesn't change the state") {
                      case Active  => ("changed", Changed)
                      case Changed => ("closed", Closed)
                    }
        } yield assert(value1)(equalTo("changed")) && assert(value2)(equalTo("closed"))
      },
      test("set") {
        for {
          ref   <- Ref.make(current)
          _     <- ref.set(update)
          value <- ref.get
        } yield assert(value)(equalTo(update))
      },
      test("toString") {
        assertZIO(Ref.make(42).map(_.toString))(equalTo("Ref(42)"))
      },
      test("update") {
        for {
          ref   <- Ref.make(current)
          _     <- ref.update(_ => update)
          value <- ref.get
        } yield assert(value)(equalTo(update))
      },
      test("updateAndGet") {
        for {
          ref   <- Ref.make(current)
          value <- ref.updateAndGet(_ => update)
        } yield assert(value)(equalTo(update))
      },
      test("updateSome") {
        for {
          ref   <- Ref.make[State](Active)
          _     <- ref.updateSome { case Closed => Changed }
          value <- ref.get
        } yield assert(value)(equalTo(Active))
      },
      test("updateSome twice") {
        for {
          ref    <- Ref.make[State](Active)
          _      <- ref.updateSome { case Active => Changed }
          value1 <- ref.get
          _ <- ref.updateSomeAndGet {
                 case Active  => Changed
                 case Changed => Closed
               }
          value2 <- ref.get
        } yield assert(value1)(equalTo(Changed)) && assert(value2)(equalTo(Closed))
      },
      test("updateSomeAndGet") {
        for {
          ref   <- Ref.make[State](Active)
          value <- ref.updateSomeAndGet { case Closed => Changed }
        } yield assert(value)(equalTo(Active))
      },
      test("updateSomeAndGet twice") {
        for {
          ref    <- Ref.make[State](Active)
          value1 <- ref.updateSomeAndGet { case Active => Changed }
          value2 <- ref.updateSomeAndGet {
                      case Active  => Changed
                      case Changed => Closed
                    }
        } yield assert(value1)(equalTo(Changed)) && assert(value2)(equalTo(Closed))
      }
    )
  )

  val (current, update) = ("value", "new value")

  sealed trait State
  case object Active  extends State
  case object Changed extends State
  case object Closed  extends State
}
