package zio

import zio.optics._
import zio.test.Assertion._
import zio.test._

object ZRefSpec extends ZIOBaseSpec {

  def spec = suite("ZRefSpec")(
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
    suite("DerivedS")(
      testM("atomicity") {
        for {
          ref   <- DerivedS.make(0)
          _     <- ZIO.collectAllPar(ZIO.replicate(100)(ref.update(_ + 1)))
          value <- ref.get
        } yield assert(value)(equalTo(100))
      },
      testM("get") {
        for {
          ref   <- DerivedS.make(current)
          value <- ref.get
        } yield assert(value)(equalTo(current))
      },
      testM("getAndSet") {
        for {
          ref    <- DerivedS.make(current)
          value1 <- ref.getAndSet(update)
          value2 <- ref.get
        } yield assert(value1)(equalTo(current)) && assert(value2)(equalTo(update))
      },
      testM("getAndUpdate") {
        for {
          ref    <- DerivedS.make(current)
          value1 <- ref.getAndUpdate(_ => update)
          value2 <- ref.get
        } yield assert(value1)(equalTo(current)) && assert(value2)(equalTo(update))
      },
      testM("getAndUpdateSome") {
        for {
          ref    <- DerivedS.make[State](Active)
          value1 <- ref.getAndUpdateSome { case Closed => Changed }
          value2 <- ref.get
        } yield assert(value1)(equalTo(Active)) && assert(value2)(equalTo(Active))
      },
      testM("getAndUpdateSome twice") {
        for {
          ref    <- DerivedS.make[State](Active)
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
          ref   <- DerivedS.make(current)
          r     <- ref.modify(_ => ("hello", update))
          value <- ref.get
        } yield assert(r)(equalTo("hello")) && assert(value)(equalTo(update))
      },
      testM("modifySome") {
        for {
          ref   <- DerivedS.make[State](Active)
          value <- ref.modifySome("State doesn't change") { case Closed => ("active", Active) }
        } yield assert(value)(equalTo("State doesn't change"))
      },
      testM("modifySome twice") {
        for {
          ref    <- DerivedS.make[State](Active)
          value1 <- ref.modifySome("doesn't change the state") { case Active => ("changed", Changed) }
          value2 <- ref.modifySome("doesn't change the state") {
                     case Active  => ("changed", Changed)
                     case Changed => ("closed", Closed)
                   }
        } yield assert(value1)(equalTo("changed")) && assert(value2)(equalTo("closed"))
      },
      testM("set") {
        for {
          ref   <- DerivedS.make(current)
          _     <- ref.set(update)
          value <- ref.get
        } yield assert(value)(equalTo(update))
      },
      testM("update") {
        for {
          ref   <- DerivedS.make(current)
          _     <- ref.update(_ => update)
          value <- ref.get
        } yield assert(value)(equalTo(update))
      },
      testM("updateAndGet") {
        for {
          ref   <- DerivedS.make(current)
          value <- ref.updateAndGet(_ => update)
        } yield assert(value)(equalTo(update))
      },
      testM("updateSome") {
        for {
          ref   <- DerivedS.make[State](Active)
          _     <- ref.updateSome { case Closed => Changed }
          value <- ref.get
        } yield assert(value)(equalTo(Active))
      },
      testM("updateSome twice") {
        for {
          ref    <- DerivedS.make[State](Active)
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
          ref   <- DerivedS.make[State](Active)
          value <- ref.updateSomeAndGet { case Closed => Changed }
        } yield assert(value)(equalTo(Active))
      },
      testM("updateSomeAndGet twice") {
        for {
          ref    <- DerivedS.make[State](Active)
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
          ref      <- Ref.make(current)
          readOnly = ref.readOnly
          _        <- ref.set(update)
          value    <- readOnly.get
        } yield assert(value)(equalTo(update))
      },
      testM("writeOnly") {
        for {
          ref       <- Ref.make(current)
          writeOnly = ref.writeOnly
          _         <- writeOnly.set(update)
          value     <- ref.get
        } yield assert(value)(equalTo(update))
      }
    ),
    suite("optics")(
      suite("lens")(
        testM("set and get") {
          checkM(Gen.anyInt.zip(Gen.anyInt), Gen.anyInt) { (s, a) =>
            for {
              ref     <- Ref.make(s)
              derived = ref.accessField(first)
              _       <- derived.set(a)
              value   <- derived.get
            } yield assert(value)(equalTo(a))
          }
        },
        testM("get and set") {
          checkM(Gen.anyInt.zip(Gen.anyInt)) { s =>
            for {
              ref     <- Ref.make(s)
              derived = ref.accessField(first)
              value1  <- derived.get
              _       <- derived.set(value1)
              value2  <- ref.get
            } yield assert(value2)(equalTo(s))
          }
        },
        testM("double set") {
          checkM(Gen.anyInt.zip(Gen.anyInt), Gen.anyInt) { (s, a) =>
            for {
              ref     <- Ref.make(s)
              derived = ref.accessField(first)
              _       <- derived.set(a)
              value1  <- ref.get
              _       <- derived.set(a)
              value2  <- ref.get
            } yield assert(value1)(equalTo(value2))
          }
        }
      ),
      suite("optional")(
        testM("modifies matching field") {
          for {
            ref     <- Ref.make(Vector(1, 2, 3, 4, 5))
            derived = ref.accessField(index(1))
            _       <- derived.update(_ * 10)
            value   <- ref.get
          } yield assert(value)(equalTo(Vector(1, 20, 3, 4, 5)))
        }
      ),
      suite("prism")(
        testM("set and get") {
          checkM(Gen.either(Gen.anyInt, Gen.anyInt), Gen.anyInt) { (s, a) =>
            for {
              ref     <- Ref.make(s)
              derived = ref.accessCase(Prism.left)
              _       <- derived.set(a)
              value   <- derived.get
            } yield assert(value)(equalTo(a))
          }
        },
        testM("get and set") {
          checkM(Gen.either(Gen.anyInt, Gen.anyInt)) { s =>
            for {
              ref     <- Ref.make(s)
              derived = ref.accessCase(Prism.left)
              _       <- derived.get.foldM(_ => ZIO.unit, derived.set)
              value   <- ref.get
            } yield assert(value)(equalTo(s))
          }
        }
      ),
      suite("traversal")(
        testM("modifies matching fields") {
          for {
            ref     <- Ref.make(List(1, 2, 3, 4, 5))
            derived = ref.accessElements(filter(_ % 2 == 0))
            _       <- derived.update(_.map(_ * 10))
            value   <- ref.get
          } yield assert(value)(equalTo(List(1, 20, 3, 40, 5)))
        }
      )
    ),
    suite("examples from documentation")(
      testM("lens") {
        case class Person(name: String, age: Int)
        def age: Lens[Person, Int] =
          Lens(person => person.age, age => person => person.copy(age = age))
        for {
          ref   <- Ref.make(Person("User", 42))
          view  = ref.accessField(age)
          _     <- view.update(_ + 1)
          value <- ref.get
        } yield assert(value)(equalTo(Person("User", 43)))
      },
      testM("prism") {
        def left[A, B]: Prism[Either[A, B], A] =
          Prism(s => s match { case Left(a) => Some(a); case _ => None }, a => Left(a))
        for {
          ref   <- Ref.make[Either[List[String], Int]](Left(Nil))
          view  = ref.accessCase(left)
          _     <- view.update("fail" :: _)
          value <- ref.get
        } yield assert(value)(isLeft(equalTo(List("fail"))))
      },
      testM("optional") {
        def index[A](n: Int): Optional[Vector[A], A] =
          Optional(
            s => if (s.isDefinedAt(n)) Some(s(n)) else None,
            a => s => if (s.isDefinedAt(n)) s.updated(n, a) else s
          )
        for {
          ref   <- Ref.make(Vector(1, 2, 3))
          view  = ref.accessField(index(2))
          _     <- view.set(4)
          value <- ref.get
        } yield assert(value)(equalTo(Vector(1, 2, 4)))
      },
      testM("traversal") {
        def slice[A](from: Int, until: Int): Traversal[Vector[A], A] =
          Traversal(
            s => s.slice(from, until).toList,
            as =>
              s => {
                val n = ((until min s.length) - from) max 0
                if (as.length < n) None else Some(s.patch(from, as.take(n), n))
              }
          )
        def negate(as: List[Int]): List[Int] =
          for (a <- as) yield -a
        for {
          ref   <- Ref.make(Vector(0, 1, 2, 3, 4, 5, 6, 7, 8, 9))
          view  = ref.accessElements(slice(3, 6))
          _     <- view.update(negate)
          value <- ref.get
        } yield assert(value)(equalTo(Vector(0, 1, 2, -3, -4, -5, 6, 7, 8, 9)))
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

  object DerivedS {
    def make[A](a: A): UIO[Ref[A]] =
      Ref.make(a).map(ref => ref.foldS(identity, identity, a => _ => Right(a), Right(_)))
  }

  def filter[A](f: A => Boolean): Traversal[List[A], A] =
    Traversal(
      s => s.filter(f),
      a =>
        s => {
          def loop(a: List[A], s: List[A], acc: List[A]): Option[List[A]] =
            (a, s) match {
              case (h1 :: t1, h2 :: t2) if (f(h2)) => loop(t1, t2, h1 :: acc)
              case (_, h2 :: _) if (f(h2))         => None
              case (a, h2 :: t2)                   => loop(a, t2, h2 :: acc)
              case (_, _)                          => Some(acc.reverse)
              case _                               => None
            }
          loop(a, s, List.empty)
        }
    )

  def first[A, B]: Lens[(A, B), A] =
    Lens(s => s._1, a => s => (a, s._2))

  def index[A](n: Int): Optional[Vector[A], A] =
    Optional(
      s => if (0 <= n && n < s.length) Some(s(n)) else None,
      a => s => if (0 <= n && n < s.length) s.updated(n, a) else s
    )
}
