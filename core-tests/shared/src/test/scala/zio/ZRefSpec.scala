package zio

import zio.test.Assertion._
import zio.test._

object ZRefSpec extends ZIOBaseNewSpec {

  def spec = suite("ZRefSpec")(
    suite("Atomic")(
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
        assertM(Ref.make(42).map(_.toString))(equalTo("Ref(42)"))
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
    ),
    suite("Derived")(
      test("atomicity") {
        for {
          ref   <- Derived.make(0)
          _     <- ZIO.collectAllPar(ZIO.replicate(100)(ref.update(_ + 1)))
          value <- ref.get
        } yield assert(value)(equalTo(100))
      },
      test("get") {
        for {
          ref   <- Derived.make(current)
          value <- ref.get
        } yield assert(value)(equalTo(current))
      },
      test("getAndSet") {
        for {
          ref    <- Derived.make(current)
          value1 <- ref.getAndSet(update)
          value2 <- ref.get
        } yield assert(value1)(equalTo(current)) && assert(value2)(equalTo(update))
      },
      test("getAndUpdate") {
        for {
          ref    <- Derived.make(current)
          value1 <- ref.getAndUpdate(_ => update)
          value2 <- ref.get
        } yield assert(value1)(equalTo(current)) && assert(value2)(equalTo(update))
      },
      test("getAndUpdateSome") {
        for {
          ref    <- Derived.make[State](Active)
          value1 <- ref.getAndUpdateSome { case Closed => Changed }
          value2 <- ref.get
        } yield assert(value1)(equalTo(Active)) && assert(value2)(equalTo(Active))
      },
      test("getAndUpdateSome twice") {
        for {
          ref    <- Derived.make[State](Active)
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
          ref   <- Derived.make(current)
          r     <- ref.modify(_ => ("hello", update))
          value <- ref.get
        } yield assert(r)(equalTo("hello")) && assert(value)(equalTo(update))
      },
      test("modifySome") {
        for {
          ref   <- Derived.make[State](Active)
          value <- ref.modifySome("State doesn't change") { case Closed => ("active", Active) }
        } yield assert(value)(equalTo("State doesn't change"))
      },
      test("modifySome twice") {
        for {
          ref    <- Derived.make[State](Active)
          value1 <- ref.modifySome("doesn't change the state") { case Active => ("changed", Changed) }
          value2 <- ref.modifySome("doesn't change the state") {
                      case Active  => ("changed", Changed)
                      case Changed => ("closed", Closed)
                    }
        } yield assert(value1)(equalTo("changed")) && assert(value2)(equalTo("closed"))
      },
      test("set") {
        for {
          ref   <- Derived.make(current)
          _     <- ref.set(update)
          value <- ref.get
        } yield assert(value)(equalTo(update))
      },
      test("update") {
        for {
          ref   <- Derived.make(current)
          _     <- ref.update(_ => update)
          value <- ref.get
        } yield assert(value)(equalTo(update))
      },
      test("updateAndGet") {
        for {
          ref   <- Derived.make(current)
          value <- ref.updateAndGet(_ => update)
        } yield assert(value)(equalTo(update))
      },
      test("updateSome") {
        for {
          ref   <- Derived.make[State](Active)
          _     <- ref.updateSome { case Closed => Changed }
          value <- ref.get
        } yield assert(value)(equalTo(Active))
      },
      test("updateSome twice") {
        for {
          ref    <- Derived.make[State](Active)
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
          ref   <- Derived.make[State](Active)
          value <- ref.updateSomeAndGet { case Closed => Changed }
        } yield assert(value)(equalTo(Active))
      },
      test("updateSomeAndGet twice") {
        for {
          ref    <- Derived.make[State](Active)
          value1 <- ref.updateSomeAndGet { case Active => Changed }
          value2 <- ref.updateSomeAndGet {
                      case Active  => Changed
                      case Changed => Closed
                    }
        } yield assert(value1)(equalTo(Changed)) && assert(value2)(equalTo(Closed))
      }
    ),
    suite("DerivedAll")(
      test("atomicity") {
        for {
          ref   <- DerivedAll.make(0)
          _     <- ZIO.collectAllPar(ZIO.replicate(100)(ref.update(_ + 1)))
          value <- ref.get
        } yield assert(value)(equalTo(100))
      },
      test("get") {
        for {
          ref   <- DerivedAll.make(current)
          value <- ref.get
        } yield assert(value)(equalTo(current))
      },
      test("getAndSet") {
        for {
          ref    <- DerivedAll.make(current)
          value1 <- ref.getAndSet(update)
          value2 <- ref.get
        } yield assert(value1)(equalTo(current)) && assert(value2)(equalTo(update))
      },
      test("getAndUpdate") {
        for {
          ref    <- DerivedAll.make(current)
          value1 <- ref.getAndUpdate(_ => update)
          value2 <- ref.get
        } yield assert(value1)(equalTo(current)) && assert(value2)(equalTo(update))
      },
      test("getAndUpdateSome") {
        for {
          ref    <- DerivedAll.make[State](Active)
          value1 <- ref.getAndUpdateSome { case Closed => Changed }
          value2 <- ref.get
        } yield assert(value1)(equalTo(Active)) && assert(value2)(equalTo(Active))
      },
      test("getAndUpdateSome twice") {
        for {
          ref    <- DerivedAll.make[State](Active)
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
          ref   <- DerivedAll.make(current)
          r     <- ref.modify(_ => ("hello", update))
          value <- ref.get
        } yield assert(r)(equalTo("hello")) && assert(value)(equalTo(update))
      },
      test("modifySome") {
        for {
          ref   <- DerivedAll.make[State](Active)
          value <- ref.modifySome("State doesn't change") { case Closed => ("active", Active) }
        } yield assert(value)(equalTo("State doesn't change"))
      },
      test("modifySome twice") {
        for {
          ref    <- DerivedAll.make[State](Active)
          value1 <- ref.modifySome("doesn't change the state") { case Active => ("changed", Changed) }
          value2 <- ref.modifySome("doesn't change the state") {
                      case Active  => ("changed", Changed)
                      case Changed => ("closed", Closed)
                    }
        } yield assert(value1)(equalTo("changed")) && assert(value2)(equalTo("closed"))
      },
      test("set") {
        for {
          ref   <- DerivedAll.make(current)
          _     <- ref.set(update)
          value <- ref.get
        } yield assert(value)(equalTo(update))
      },
      test("update") {
        for {
          ref   <- DerivedAll.make(current)
          _     <- ref.update(_ => update)
          value <- ref.get
        } yield assert(value)(equalTo(update))
      },
      test("updateAndGet") {
        for {
          ref   <- DerivedAll.make(current)
          value <- ref.updateAndGet(_ => update)
        } yield assert(value)(equalTo(update))
      },
      test("updateSome") {
        for {
          ref   <- DerivedAll.make[State](Active)
          _     <- ref.updateSome { case Closed => Changed }
          value <- ref.get
        } yield assert(value)(equalTo(Active))
      },
      test("updateSome twice") {
        for {
          ref    <- DerivedAll.make[State](Active)
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
          ref   <- DerivedAll.make[State](Active)
          value <- ref.updateSomeAndGet { case Closed => Changed }
        } yield assert(value)(equalTo(Active))
      },
      test("updateSomeAndGet twice") {
        for {
          ref    <- DerivedAll.make[State](Active)
          value1 <- ref.updateSomeAndGet { case Active => Changed }
          value2 <- ref.updateSomeAndGet {
                      case Active  => Changed
                      case Changed => Closed
                    }
        } yield assert(value1)(equalTo(Changed)) && assert(value2)(equalTo(Closed))
      }
    ),
    suite("combinators")(
      test("readOnly") {
        for {
          ref     <- Ref.make(current)
          readOnly = ref.readOnly
          _       <- ref.set(update)
          value   <- readOnly.get
        } yield assert(value)(equalTo(update))
      },
      test("writeOnly") {
        for {
          ref      <- Ref.make(current)
          writeOnly = ref.writeOnly
          _        <- writeOnly.set(update)
          value    <- ref.get
        } yield assert(value)(equalTo(update))
      }
    )
  )

  val (current, update) = ("value", "new value")

  sealed trait State
  case object Active  extends State
  case object Changed extends State
  case object Closed  extends State

  object Derived {
    def make[A](a: A): UIO[Ref[A]] =
      Ref.make(a).map(ref => ref.fold(identity, identity, Right(_), Right(_)))
  }

  object DerivedAll {
    def make[A](a: A): UIO[Ref[A]] =
      Ref.make(a).map(ref => ref.foldAll(identity, identity, identity, a => _ => Right(a), Right(_)))
  }
}
