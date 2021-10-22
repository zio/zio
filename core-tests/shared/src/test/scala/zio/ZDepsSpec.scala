package zio

import zio.test.Assertion._
import zio.test.TestAspect.nonFlaky
import zio.test._
import zio.test.environment._

object ZDepsSpec extends ZIOBaseSpec {

  import ZIOTag._

  trait Animal
  trait Dog extends Animal
  trait Cat extends Animal

  def testSize[R <: Has[_]](deps: Deps[Nothing, R], n: Int, label: String = ""): UIO[TestResult] =
    deps.build.use(env => ZIO.succeed(assert(env.size)(if (label == "") equalTo(n) else equalTo(n) ?? label)))

  val acquire1 = "Acquiring Module 1"
  val acquire2 = "Acquiring Module 2"
  val acquire3 = "Acquiring Module 3"
  val release1 = "Releasing Module 1"
  val release2 = "Releasing Module 2"
  val release3 = "Releasing Module 3"

  type Module1 = Has[Module1.Service]

  object Module1 {
    trait Service
  }

  def makeDeps1(ref: Ref[Vector[String]]): ZDeps[Any, Nothing, Module1] =
    ZDeps {
      ZManaged.acquireReleaseWith(ref.update(_ :+ acquire1).as(Has(new Module1.Service {})))(_ =>
        ref.update(_ :+ release1)
      )
    }

  type Module2 = Has[Module2.Service]

  object Module2 {
    trait Service
  }

  def makeDeps2(ref: Ref[Vector[String]]): ZDeps[Any, Nothing, Module2] =
    ZDeps {
      ZManaged.acquireReleaseWith(ref.update(_ :+ acquire2).as(Has(new Module2.Service {})))(_ =>
        ref.update(_ :+ release2)
      )
    }

  type Module3 = Has[Module3.Service]

  object Module3 {
    trait Service
  }

  def makeDeps3(ref: Ref[Vector[String]]): ZDeps[Any, Nothing, Module3] =
    ZDeps {
      ZManaged.acquireReleaseWith(ref.update(_ :+ acquire3).as(Has(new Module3.Service {})))(_ =>
        ref.update(_ :+ release3)
      )
    }

  def makeRef: UIO[Ref[Vector[String]]] =
    Ref.make(Vector.empty)

  def spec: ZSpec[Environment, Failure] =
    suite("ZDepsSpec")(
      test("Size of >>> (1)") {
        val deps = ZDeps.succeed(1) >>> ((i: Int) => i.toString).toDeps

        testSize(deps, 1)
      },
      test("Size of >>> (2)") {
        val deps = ZDeps.succeed(1) >>>
          (((i: Int) => i.toString).toDeps ++
            ((i: Int) => i % 2 == 0).toDeps)

        testSize(deps, 2)
      },
      test("Size of Test dependencies") {
        for {
          r1 <- testSize(Annotations.live, 1, "Annotations.live")
          r2 <- testSize(ZEnv.live >>> Live.default >>> TestConsole.debug, 2, "TestConsole.default")
          r3 <- testSize(ZEnv.live >>> Live.default, 1, "Live.default")
          r4 <- testSize(ZEnv.live >>> TestRandom.deterministic, 2, "TestRandom.live")
          r5 <- testSize(Sized.live(100), 1, "Sized.live(100)")
          r6 <- testSize(TestSystem.default, 2, "TestSystem.default")
        } yield r1 && r2 && r3 && r4 && r5 && r6
      },
      test("Size of >>> (9)") {
        val deps = ZEnv.live >>>
          (Annotations.live ++ (Live.default >>> TestConsole.debug) ++
            Live.default ++ TestRandom.deterministic ++ Sized.live(100)
            ++ TestSystem.default)

        testSize(deps, 9)
      },
      test("sharing with ++") {
        val expected = Vector(acquire1, release1)
        for {
          ref    <- makeRef
          deps1   = makeDeps1(ref)
          env     = (deps1 ++ deps1).build
          _      <- env.useDiscard(ZIO.unit)
          actual <- ref.get
        } yield assert(actual)(equalTo(expected))
      } @@ nonFlaky,
      test("sharing itself with ++") {
        val m1    = new Module1.Service {}
        val deps1 = ZDeps.succeed(m1)
        val env   = deps1 ++ (deps1 ++ deps1)
        env.build.use(m => ZIO(assert(m.get)(equalTo(m1))))
      } @@ nonFlaky,
      test("sharing with >>>") {
        val expected = Vector(acquire1, release1)
        for {
          ref    <- makeRef
          deps1   = makeDeps1(ref)
          env     = (deps1 >>> deps1).build
          _      <- env.useDiscard(ZIO.unit)
          actual <- ref.get
        } yield assert(actual)(equalTo(expected))
      } @@ nonFlaky,
      test("sharing with multiple dependencies") {
        for {
          ref    <- makeRef
          deps1   = makeDeps1(ref)
          deps2   = makeDeps2(ref)
          deps3   = makeDeps3(ref)
          env     = ((deps1 >>> deps2) ++ (deps1 >>> deps3)).build
          _      <- env.useDiscard(ZIO.unit)
          actual <- ref.get
        } yield assert(actual(0))(equalTo(acquire1)) &&
          assert(actual.slice(1, 3))(hasSameElements(Vector(acquire2, acquire3))) &&
          assert(actual.slice(3, 5))(hasSameElements(Vector(release2, release3))) &&
          assert(actual(5))(equalTo(release1))
      } @@ nonFlaky,
      test("finalizers with ++") {
        for {
          ref    <- makeRef
          deps1   = makeDeps1(ref)
          deps2   = makeDeps2(ref)
          env     = (deps1 ++ deps2).build
          _      <- env.useDiscard(ZIO.unit)
          actual <- ref.get
        } yield assert(actual.slice(0, 2))(hasSameElements(Vector(acquire1, acquire2))) &&
          assert(actual.slice(2, 4))(hasSameElements(Vector(release1, release2)))
      } @@ nonFlaky,
      test("finalizers with >>>") {
        val expected = Vector(acquire1, acquire2, release2, release1)
        for {
          ref    <- makeRef
          deps1   = makeDeps1(ref)
          deps2   = makeDeps2(ref)
          env     = (deps1 >>> deps2).build
          _      <- env.useDiscard(ZIO.unit)
          actual <- ref.get
        } yield assert(actual)(equalTo(expected))
      } @@ nonFlaky,
      test("finalizers with multiple dependencies") {
        val expected =
          Vector(acquire1, acquire2, acquire3, release3, release2, release1)
        for {
          ref    <- makeRef
          deps1   = makeDeps1(ref)
          deps2   = makeDeps2(ref)
          deps3   = makeDeps3(ref)
          env     = (deps1 >>> deps2 >>> deps3).build
          _      <- env.useDiscard(ZIO.unit)
          actual <- ref.get
        } yield assert(actual)(equalTo(expected))
      } @@ nonFlaky,
      test("map does not interfere with sharing") {
        for {
          ref    <- makeRef
          deps1   = makeDeps1(ref)
          deps2   = makeDeps2(ref)
          deps3   = makeDeps3(ref)
          env     = ((deps1.map(identity) >>> deps2) ++ (deps1 >>> deps3)).build
          _      <- env.useDiscard(ZIO.unit)
          actual <- ref.get
        } yield assert(actual(0))(equalTo(acquire1)) &&
          assert(actual.slice(1, 3))(hasSameElements(Vector(acquire2, acquire3))) &&
          assert(actual.slice(3, 5))(hasSameElements(Vector(release2, release3))) &&
          assert(actual(5))(equalTo(release1))
      } @@ nonFlaky,
      test("mapError does not interfere with sharing") {
        implicit val canFail = CanFail
        for {
          ref    <- makeRef
          deps1   = makeDeps1(ref)
          deps2   = makeDeps2(ref)
          deps3   = makeDeps3(ref)
          env     = ((deps1.mapError(identity) >>> deps2) ++ (deps1 >>> deps3)).build
          _      <- env.useDiscard(ZIO.unit)
          actual <- ref.get
        } yield assert(actual(0))(equalTo(acquire1)) &&
          assert(actual.slice(1, 3))(hasSameElements(Vector(acquire2, acquire3))) &&
          assert(actual.slice(3, 5))(hasSameElements(Vector(release2, release3))) &&
          assert(actual(5))(equalTo(release1))
      } @@ nonFlaky,
      test("orDie does not interfere with sharing") {
        implicit val canFail = CanFail
        for {
          ref    <- makeRef
          deps1   = makeDeps1(ref)
          deps2   = makeDeps2(ref)
          deps3   = makeDeps3(ref)
          env     = ((deps1.orDie >>> deps2) ++ (deps1 >>> deps3)).build
          _      <- env.useDiscard(ZIO.unit)
          actual <- ref.get
        } yield assert(actual(0))(equalTo(acquire1)) &&
          assert(actual.slice(1, 3))(hasSameElements(Vector(acquire2, acquire3))) &&
          assert(actual.slice(3, 5))(hasSameElements(Vector(release2, release3))) &&
          assert(actual(5))(equalTo(release1))
      } @@ nonFlaky,
      test("interruption with ++") {
        for {
          ref    <- makeRef
          deps1   = makeDeps1(ref)
          deps2   = makeDeps2(ref)
          env     = (deps1 ++ deps2).build
          fiber  <- env.useDiscard(ZIO.unit).fork
          _      <- fiber.interrupt
          actual <- ref.get
        } yield (assert(actual)(contains(acquire1)) ==> assert(actual)(contains(release1))) &&
          (assert(actual)(contains(acquire2)) ==> assert(actual)(contains(release2)))
      } @@ zioTag(interruption) @@ nonFlaky,
      test("interruption with >>>") {
        for {
          ref    <- makeRef
          deps1   = makeDeps1(ref)
          deps2   = makeDeps2(ref)
          env     = (deps1 >>> deps2).build
          fiber  <- env.useDiscard(ZIO.unit).fork
          _      <- fiber.interrupt
          actual <- ref.get
        } yield (assert(actual)(contains(acquire1)) ==> assert(actual)(contains(release1))) &&
          (assert(actual)(contains(acquire2)) ==> assert(actual)(contains(release2)))
      } @@ zioTag(interruption) @@ nonFlaky,
      test("interruption with multiple dependencies") {
        for {
          ref    <- makeRef
          deps1   = makeDeps1(ref)
          deps2   = makeDeps2(ref)
          deps3   = makeDeps3(ref)
          env     = ((deps1 >>> deps2) ++ (deps1 >>> deps3)).build
          fiber  <- env.useDiscard(ZIO.unit).fork
          _      <- fiber.interrupt
          actual <- ref.get
        } yield (assert(actual)(contains(acquire1)) ==> assert(actual)(contains(release1))) &&
          (assert(actual)(contains(acquire2)) ==> assert(actual)(contains(release2))) &&
          (assert(actual)(contains(acquire3)) ==> assert(actual)(contains(release3)))
      } @@ zioTag(interruption) @@ nonFlaky,
      test("dependencies can be acquired in parallel") {
        for {
          promise <- Promise.make[Nothing, Unit]
          deps1    = ZDeps(ZManaged.never)
          deps2    = ZDeps(Managed.acquireReleaseWith(promise.succeed(()).map(Has(_)))(_ => ZIO.unit))
          env      = (deps1 ++ deps2).build
          _       <- env.useDiscard(ZIO.unit).forkDaemon
          _       <- promise.await
        } yield assertCompletes
      },
      test("map can map a dependency to an unrelated type") {
        case class A(name: String, value: Int)
        case class B(name: String)
        val l1: Deps[Nothing, Has[A]]               = ZDeps.succeed(A("name", 1))
        val l2: ZDeps[Has[String], Nothing, Has[B]] = (B.apply _).toDeps
        val live: Deps[Nothing, Has[B]]             = l1.map(a => Has(a.get[A].name)) >>> l2
        assertM(ZIO.access[Has[B]](_.get).inject(live))(equalTo(B("name")))
      },
      test("memoization") {
        val expected = Vector(acquire1, release1)
        for {
          ref     <- makeRef
          memoized = makeDeps1(ref).memoize
          _ <- memoized.use { deps =>
                 for {
                   _ <- ZIO.environment[Module1].provideDeps(deps)
                   _ <- ZIO.environment[Module1].provideDeps(deps)
                 } yield ()
               }
          actual <- ref.get
        } yield assert(actual)(equalTo(expected))
      } @@ nonFlaky,
      test("orElse") {
        for {
          ref    <- makeRef
          deps1   = makeDeps1(ref)
          deps2   = makeDeps2(ref)
          env     = ((deps1 >>> ZDeps.fail("fail")) orElse deps2).build
          fiber  <- env.useDiscard(ZIO.unit).fork
          _      <- fiber.interrupt
          actual <- ref.get
        } yield (assert(actual)(contains(acquire1)) ==> assert(actual)(contains(release1))) &&
          (assert(actual)(contains(acquire2)) ==> assert(actual)(contains(release2)))
      } @@ nonFlaky,
      test("passthrough") {
        val deps: ZDeps[Has[Int], Nothing, Has[String]] =
          ((_: Int).toString).toDeps
        val live: ZDeps[Any, Nothing, Has[Int] with Has[String]] =
          ZDeps.succeed(1) >>> deps.passthrough
        val zio = for {
          i <- ZIO.environment[Has[Int]].map(_.get[Int])
          s <- ZIO.environment[Has[String]].map(_.get[String])
        } yield (i, s)
        assertM(zio.inject(live))(equalTo((1, "1")))
      },
      test("fresh with ++") {
        val expected = Vector(acquire1, acquire1, release1, release1)
        for {
          ref    <- makeRef
          deps1   = makeDeps1(ref)
          env     = (deps1 ++ deps1.fresh).build
          _      <- env.useNow
          result <- ref.get
        } yield assert(result)(equalTo(expected))
      } @@ nonFlaky,
      test("fresh with >>>") {
        val expected = Vector(acquire1, acquire1, release1, release1)
        for {
          ref    <- makeRef
          deps1   = makeDeps1(ref)
          env     = (deps1 >>> deps1.fresh).build
          _      <- env.useNow
          result <- ref.get
        } yield assert(result)(equalTo(expected))
      } @@ nonFlaky,
      test("fresh with multiple dependencies") {
        val expected = Vector(acquire1, acquire1, release1, release1)
        for {
          ref    <- makeRef
          deps1   = makeDeps1(ref)
          env     = ((deps1 ++ deps1) ++ (deps1 ++ deps1).fresh).build
          _      <- env.useNow
          result <- ref.get
        } yield assert(result)(equalTo(expected))
      } @@ nonFlaky,
      test("fresh with identical fresh dependencies") {
        for {
          ref    <- makeRef
          deps1   = makeDeps1(ref)
          deps2   = makeDeps2(ref)
          deps3   = makeDeps3(ref)
          env     = ((deps1.fresh >>> deps2) ++ (deps1.fresh >>> deps3)).build
          _      <- env.useNow
          result <- ref.get
        } yield assert(result)(hasSize(equalTo(8)))
      } @@ nonFlaky,
      test("preserves identity of acquired resources") {
        for {
          testRef <- Ref.make(Vector[String]())
          deps = ZDeps {
                   for {
                     ref <- Ref.make[Vector[String]](Vector()).toManagedWith(ref => ref.get.flatMap(testRef.set))
                     _   <- ZManaged.unit
                   } yield ref
                 }
          _      <- deps.build.use(ref => ref.update(_ :+ "test"))
          result <- testRef.get
        } yield assert(result)(equalTo(Vector("test")))
      },
      test("retry") {
        for {
          ref    <- Ref.make(0)
          effect  = ref.update(_ + 1) *> ZIO.fail("fail")
          deps    = ZDeps.fromZIOMany(effect).retry(Schedule.recurs(3))
          _      <- deps.build.useNow.ignore
          result <- ref.get
        } yield assert(result)(equalTo(4))
      },
      test("error handling") {
        val sleep = ZIO.sleep(100.milliseconds).inject(Clock.live)
        val deps1 = ZDeps.fail("foo")
        val deps2 = ZDeps.succeed("bar")
        val deps3 = ZDeps.succeed("baz")
        val deps4 = ZManaged.acquireReleaseWith(sleep)(_ => sleep).toDeps
        val env   = deps1 ++ ((deps2 ++ deps3) >+> deps4)
        assertM(ZIO.unit.provideCustomDeps(env).exit)(fails(equalTo("foo")))
      },
      test("project") {
        final case class Person(name: String, age: Int)
        val personDeps = ZDeps.succeed(Person("User", 42))
        val ageDeps    = personDeps.project(_.age)
        assertM(ZIO.service[Int].inject(ageDeps))(equalTo(42))
      },
      test("tap") {
        for {
          ref   <- Ref.make("foo")
          deps   = ZDeps.succeed("bar").tap(r => ref.set(r.get))
          _     <- deps.build.useNow
          value <- ref.get
        } yield assert(value)(equalTo("bar"))
      }
    )
}
