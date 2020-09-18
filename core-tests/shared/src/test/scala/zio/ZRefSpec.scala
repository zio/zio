package zio

import zio.test.Assertion._
import zio.test._
import zio.test.environment.TestEnvironment

object ZRefSpec extends ZIOBaseSpec {

  def spec: ZSpec[TestEnvironment, Any] = suite("ZRefSpec")(
    suite("Atomic")(
      testM("get") {
        for {
          ref   <- Ref.make(current)
          value <- ref.get
        } yield assert(value)(equalTo(current))
      },
      testM("getAndSet") {
        for {
          ref    <- Ref.make(current)
          value1 <- ref.getAndSet(update)
          value2 <- ref.get
        } yield assert(value1)(equalTo(current)) && assert(value2)(equalTo(update))
      },
      testM("getAndUpdate") {
        for {
          ref    <- Ref.make(current)
          value1 <- ref.getAndUpdate(_ => update)
          value2 <- ref.get
        } yield assert(value1)(equalTo(current)) && assert(value2)(equalTo(update))
      },
      testM("getAndUpdateSome") {
        for {
          ref    <- Ref.make[State](Active)
          value1 <- ref.getAndUpdateSome { case Closed => Changed }
          value2 <- ref.get
        } yield assert(value1)(equalTo(Active)) && assert(value2)(equalTo(Active))
      },
      testM("getAndUpdateSome twice") {
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
      testM("modify") {
        for {
          ref   <- Ref.make(current)
          r     <- ref.modify(_ => ("hello", update))
          value <- ref.get
        } yield assert(r)(equalTo("hello")) && assert(value)(equalTo(update))
      },
      testM("modifySome") {
        for {
          ref   <- Ref.make[State](Active)
          value <- ref.modifySome("State doesn't change") { case Closed => ("active", Active) }
        } yield assert(value)(equalTo("State doesn't change"))
      },
      testM("modifySome twice") {
        for {
          ref    <- Ref.make[State](Active)
          value1 <- ref.modifySome("doesn't change the state") { case Active => ("changed", Changed) }
          value2 <- ref.modifySome("doesn't change the state") {
                      case Active  => ("changed", Changed)
                      case Changed => ("closed", Closed)
                    }
        } yield assert(value1)(equalTo("changed")) && assert(value2)(equalTo("closed"))
      },
      testM("set") {
        for {
          ref   <- Ref.make(current)
          _     <- ref.set(update)
          value <- ref.get
        } yield assert(value)(equalTo(update))
      },
      testM("toString") {
        assertM(Ref.make(42).map(_.toString))(equalTo("Ref(42)"))
      },
      testM("update") {
        for {
          ref   <- Ref.make(current)
          _     <- ref.update(_ => update)
          value <- ref.get
        } yield assert(value)(equalTo(update))
      },
      testM("updateAndGet") {
        for {
          ref   <- Ref.make(current)
          value <- ref.updateAndGet(_ => update)
        } yield assert(value)(equalTo(update))
      },
      testM("updateSome") {
        for {
          ref   <- Ref.make[State](Active)
          _     <- ref.updateSome { case Closed => Changed }
          value <- ref.get
        } yield assert(value)(equalTo(Active))
      },
      testM("updateSome twice") {
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
      testM("updateSomeAndGet") {
        for {
          ref   <- Ref.make[State](Active)
          value <- ref.updateSomeAndGet { case Closed => Changed }
        } yield assert(value)(equalTo(Active))
      },
      testM("updateSomeAndGet twice") {
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
      testM("atomicity") {
        for {
          ref   <- Derived.make(0)
          _     <- ZIO.collectAllPar(ZIO.replicate(100)(ref.update(_ + 1)))
          value <- ref.get
        } yield assert(value)(equalTo(100))
      },
      testM("get") {
        for {
          ref   <- Derived.make(current)
          value <- ref.get
        } yield assert(value)(equalTo(current))
      },
      testM("getAndSet") {
        for {
          ref    <- Derived.make(current)
          value1 <- ref.getAndSet(update)
          value2 <- ref.get
        } yield assert(value1)(equalTo(current)) && assert(value2)(equalTo(update))
      },
      testM("getAndUpdate") {
        for {
          ref    <- Derived.make(current)
          value1 <- ref.getAndUpdate(_ => update)
          value2 <- ref.get
        } yield assert(value1)(equalTo(current)) && assert(value2)(equalTo(update))
      },
      testM("getAndUpdateSome") {
        for {
          ref    <- Derived.make[State](Active)
          value1 <- ref.getAndUpdateSome { case Closed => Changed }
          value2 <- ref.get
        } yield assert(value1)(equalTo(Active)) && assert(value2)(equalTo(Active))
      },
      testM("getAndUpdateSome twice") {
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
      testM("modify") {
        for {
          ref   <- Derived.make(current)
          r     <- ref.modify(_ => ("hello", update))
          value <- ref.get
        } yield assert(r)(equalTo("hello")) && assert(value)(equalTo(update))
      },
      testM("modifySome") {
        for {
          ref   <- Derived.make[State](Active)
          value <- ref.modifySome("State doesn't change") { case Closed => ("active", Active) }
        } yield assert(value)(equalTo("State doesn't change"))
      },
      testM("modifySome twice") {
        for {
          ref    <- Derived.make[State](Active)
          value1 <- ref.modifySome("doesn't change the state") { case Active => ("changed", Changed) }
          value2 <- ref.modifySome("doesn't change the state") {
                      case Active  => ("changed", Changed)
                      case Changed => ("closed", Closed)
                    }
        } yield assert(value1)(equalTo("changed")) && assert(value2)(equalTo("closed"))
      },
      testM("set") {
        for {
          ref   <- Derived.make(current)
          _     <- ref.set(update)
          value <- ref.get
        } yield assert(value)(equalTo(update))
      },
      testM("update") {
        for {
          ref   <- Derived.make(current)
          _     <- ref.update(_ => update)
          value <- ref.get
        } yield assert(value)(equalTo(update))
      },
      testM("updateAndGet") {
        for {
          ref   <- Derived.make(current)
          value <- ref.updateAndGet(_ => update)
        } yield assert(value)(equalTo(update))
      },
      testM("updateSome") {
        for {
          ref   <- Derived.make[State](Active)
          _     <- ref.updateSome { case Closed => Changed }
          value <- ref.get
        } yield assert(value)(equalTo(Active))
      },
      testM("updateSome twice") {
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
      testM("updateSomeAndGet") {
        for {
          ref   <- Derived.make[State](Active)
          value <- ref.updateSomeAndGet { case Closed => Changed }
        } yield assert(value)(equalTo(Active))
      },
      testM("updateSomeAndGet twice") {
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
      testM("atomicity") {
        for {
          ref   <- DerivedAll.make(0)
          _     <- ZIO.collectAllPar(ZIO.replicate(100)(ref.update(_ + 1)))
          value <- ref.get
        } yield assert(value)(equalTo(100))
      },
      testM("get") {
        for {
          ref   <- DerivedAll.make(current)
          value <- ref.get
        } yield assert(value)(equalTo(current))
      },
      testM("getAndSet") {
        for {
          ref    <- DerivedAll.make(current)
          value1 <- ref.getAndSet(update)
          value2 <- ref.get
        } yield assert(value1)(equalTo(current)) && assert(value2)(equalTo(update))
      },
      testM("getAndUpdate") {
        for {
          ref    <- DerivedAll.make(current)
          value1 <- ref.getAndUpdate(_ => update)
          value2 <- ref.get
        } yield assert(value1)(equalTo(current)) && assert(value2)(equalTo(update))
      },
      testM("getAndUpdateSome") {
        for {
          ref    <- DerivedAll.make[State](Active)
          value1 <- ref.getAndUpdateSome { case Closed => Changed }
          value2 <- ref.get
        } yield assert(value1)(equalTo(Active)) && assert(value2)(equalTo(Active))
      },
      testM("getAndUpdateSome twice") {
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
      testM("modify") {
        for {
          ref   <- DerivedAll.make(current)
          r     <- ref.modify(_ => ("hello", update))
          value <- ref.get
        } yield assert(r)(equalTo("hello")) && assert(value)(equalTo(update))
      },
      testM("modifySome") {
        for {
          ref   <- DerivedAll.make[State](Active)
          value <- ref.modifySome("State doesn't change") { case Closed => ("active", Active) }
        } yield assert(value)(equalTo("State doesn't change"))
      },
      testM("modifySome twice") {
        for {
          ref    <- DerivedAll.make[State](Active)
          value1 <- ref.modifySome("doesn't change the state") { case Active => ("changed", Changed) }
          value2 <- ref.modifySome("doesn't change the state") {
                      case Active  => ("changed", Changed)
                      case Changed => ("closed", Closed)
                    }
        } yield assert(value1)(equalTo("changed")) && assert(value2)(equalTo("closed"))
      },
      testM("set") {
        for {
          ref   <- DerivedAll.make(current)
          _     <- ref.set(update)
          value <- ref.get
        } yield assert(value)(equalTo(update))
      },
      testM("update") {
        for {
          ref   <- DerivedAll.make(current)
          _     <- ref.update(_ => update)
          value <- ref.get
        } yield assert(value)(equalTo(update))
      },
      testM("updateAndGet") {
        for {
          ref   <- DerivedAll.make(current)
          value <- ref.updateAndGet(_ => update)
        } yield assert(value)(equalTo(update))
      },
      testM("updateSome") {
        for {
          ref   <- DerivedAll.make[State](Active)
          _     <- ref.updateSome { case Closed => Changed }
          value <- ref.get
        } yield assert(value)(equalTo(Active))
      },
      testM("updateSome twice") {
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
      testM("updateSomeAndGet") {
        for {
          ref   <- DerivedAll.make[State](Active)
          value <- ref.updateSomeAndGet { case Closed => Changed }
        } yield assert(value)(equalTo(Active))
      },
      testM("updateSomeAndGet twice") {
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
      testM("readOnly") {
        for {
          ref     <- Ref.make(current)
          readOnly = ref.readOnly
          _       <- ref.set(update)
          value   <- readOnly.get
        } yield assert(value)(equalTo(update))
      },
      testM("writeOnly") {
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
