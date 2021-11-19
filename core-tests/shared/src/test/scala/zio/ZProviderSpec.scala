package zio

import zio.test.Assertion._
import zio.test.TestAspect.nonFlaky
import zio.test._

object ZProviderSpec extends ZIOBaseSpec {

  import ZIOTag._

  trait Animal
  trait Dog extends Animal
  trait Cat extends Animal

  def testSize[R](provider: Provider[Nothing, R], n: Int, label: String = ""): UIO[TestResult] =
    provider.build.use(env => ZIO.succeed(assert(env.size)(if (label == "") equalTo(n) else equalTo(n) ?? label)))

  val acquire1 = "Acquiring Module 1"
  val acquire2 = "Acquiring Module 2"
  val acquire3 = "Acquiring Module 3"
  val release1 = "Releasing Module 1"
  val release2 = "Releasing Module 2"
  val release3 = "Releasing Module 3"

  type Module1 = Module1.Service

  object Module1 {
    trait Service
  }

  def makeProvider1(ref: Ref[Vector[String]]): ZProvider[Any, Nothing, Module1] =
    ZProvider {
      ZManaged.acquireReleaseWith(ref.update(_ :+ acquire1).as(new Module1.Service {}))(_ => ref.update(_ :+ release1))
    }

  type Module2 = Module2.Service

  object Module2 {
    trait Service
  }

  def makeProvider2(ref: Ref[Vector[String]]): ZProvider[Any, Nothing, Module2] =
    ZProvider {
      ZManaged.acquireReleaseWith(ref.update(_ :+ acquire2).as(new Module2.Service {}))(_ => ref.update(_ :+ release2))
    }

  type Module3 = Module3.Service

  object Module3 {
    trait Service
  }

  def makeProvider3(ref: Ref[Vector[String]]): ZProvider[Any, Nothing, Module3] =
    ZProvider {
      ZManaged.acquireReleaseWith(ref.update(_ :+ acquire3).as(new Module3.Service {}))(_ => ref.update(_ :+ release3))
    }

  def makeRef: UIO[Ref[Vector[String]]] =
    Ref.make(Vector.empty)

  def spec =
    suite("ZProviderSpec")(
      test("Size of >>> (1)") {
        val provider = ZProvider.succeed(1) >>> ((i: Int) => i.toString).toProvider

        testSize(provider, 1)
      },
      test("Size of >>> (2)") {
        val provider = ZProvider.succeed(1) >>>
          (((i: Int) => i.toString).toProvider ++
            ((i: Int) => i % 2 == 0).toProvider)

        testSize(provider, 2)
      },
      test("Size of Test providers") {
        for {
          r1 <- testSize(Annotations.live, 1, "Annotations.live")
          r2 <- testSize(ZEnv.live >>> Live.default >>> TestConsole.debug, 1, "TestConsole.default")
          r3 <- testSize(ZEnv.live >>> Live.default, 1, "Live.default")
          r4 <- testSize(ZEnv.live >>> TestRandom.deterministic, 1, "TestRandom.live")
          r5 <- testSize(Sized.live(100), 1, "Sized.live(100)")
          r6 <- testSize(TestSystem.default, 1, "TestSystem.default")
        } yield r1 && r2 && r3 && r4 && r5 && r6
      },
      test("Size of >>> (9)") {
        val provider = ZEnv.live >>>
          (Annotations.live ++ (Live.default >>> TestConsole.debug) ++
            Live.default ++ TestRandom.deterministic ++ Sized.live(100)
            ++ TestSystem.default)

        testSize(provider, 6)
      },
      test("sharing with ++") {
        val expected = Vector(acquire1, release1)
        for {
          ref      <- makeRef
          provider1 = makeProvider1(ref)
          env       = (provider1 ++ provider1).build
          _        <- env.useDiscard(ZIO.unit)
          actual   <- ref.get
        } yield assert(actual)(equalTo(expected))
      } @@ nonFlaky,
      test("sharing itself with ++") {
        val m1        = new Module1.Service {}
        val provider1 = ZProvider.succeed(m1)
        val env       = provider1 ++ (provider1 ++ provider1)
        env.build.use(m => ZIO(assert(m.get)(equalTo(m1))))
      } @@ nonFlaky,
      test("sharing with >>>") {
        val expected = Vector(acquire1, release1)
        for {
          ref      <- makeRef
          provider1 = makeProvider1(ref)
          env       = (provider1 >>> provider1).build
          _        <- env.useDiscard(ZIO.unit)
          actual   <- ref.get
        } yield assert(actual)(equalTo(expected))
      } @@ nonFlaky,
      test("sharing with multiple providers") {
        for {
          ref      <- makeRef
          provider1 = makeProvider1(ref)
          provider2 = makeProvider2(ref)
          provider3 = makeProvider3(ref)
          env       = ((provider1 >>> provider2) ++ (provider1 >>> provider3)).build
          _        <- env.useDiscard(ZIO.unit)
          actual   <- ref.get
        } yield assert(actual(0))(equalTo(acquire1)) &&
          assert(actual.slice(1, 3))(hasSameElements(Vector(acquire2, acquire3))) &&
          assert(actual.slice(3, 5))(hasSameElements(Vector(release2, release3))) &&
          assert(actual(5))(equalTo(release1))
      } @@ nonFlaky,
      test("finalizers with ++") {
        for {
          ref      <- makeRef
          provider1 = makeProvider1(ref)
          provider2 = makeProvider2(ref)
          env       = (provider1 ++ provider2).build
          _        <- env.useDiscard(ZIO.unit)
          actual   <- ref.get
        } yield assert(actual.slice(0, 2))(hasSameElements(Vector(acquire1, acquire2))) &&
          assert(actual.slice(2, 4))(hasSameElements(Vector(release1, release2)))
      } @@ nonFlaky,
      test("finalizers with >>>") {
        val expected = Vector(acquire1, acquire2, release2, release1)
        for {
          ref      <- makeRef
          provider1 = makeProvider1(ref)
          provider2 = makeProvider2(ref)
          env       = (provider1 >>> provider2).build
          _        <- env.useDiscard(ZIO.unit)
          actual   <- ref.get
        } yield assert(actual)(equalTo(expected))
      } @@ nonFlaky,
      test("finalizers with multiple providers") {
        val expected =
          Vector(acquire1, acquire2, acquire3, release3, release2, release1)
        for {
          ref      <- makeRef
          provider1 = makeProvider1(ref)
          provider2 = makeProvider2(ref)
          provider3 = makeProvider3(ref)
          env       = (provider1 >>> provider2 >>> provider3).build
          _        <- env.useDiscard(ZIO.unit)
          actual   <- ref.get
        } yield assert(actual)(equalTo(expected))
      } @@ nonFlaky,
      test("map does not interfere with sharing") {
        for {
          ref      <- makeRef
          provider1 = makeProvider1(ref)
          provider2 = makeProvider2(ref)
          provider3 = makeProvider3(ref)
          env       = ((provider1.map(identity) >>> provider2) ++ (provider1 >>> provider3)).build
          _        <- env.useDiscard(ZIO.unit)
          actual   <- ref.get
        } yield assert(actual(0))(equalTo(acquire1)) &&
          assert(actual.slice(1, 3))(hasSameElements(Vector(acquire2, acquire3))) &&
          assert(actual.slice(3, 5))(hasSameElements(Vector(release2, release3))) &&
          assert(actual(5))(equalTo(release1))
      } @@ nonFlaky,
      test("mapError does not interfere with sharing") {
        implicit val canFail = CanFail
        for {
          ref      <- makeRef
          provider1 = makeProvider1(ref)
          provider2 = makeProvider2(ref)
          provider3 = makeProvider3(ref)
          env =
            ((provider1.mapError(identity) >>> provider2) ++ (provider1 >>> provider3)).build
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
          ref      <- makeRef
          provider1 = makeProvider1(ref)
          provider2 = makeProvider2(ref)
          provider3 = makeProvider3(ref)
          env       = ((provider1.orDie >>> provider2) ++ (provider1 >>> provider3)).build
          _        <- env.useDiscard(ZIO.unit)
          actual   <- ref.get
        } yield assert(actual(0))(equalTo(acquire1)) &&
          assert(actual.slice(1, 3))(hasSameElements(Vector(acquire2, acquire3))) &&
          assert(actual.slice(3, 5))(hasSameElements(Vector(release2, release3))) &&
          assert(actual(5))(equalTo(release1))
      } @@ nonFlaky,
      test("interruption with ++") {
        for {
          ref      <- makeRef
          provider1 = makeProvider1(ref)
          provider2 = makeProvider2(ref)
          env       = (provider1 ++ provider2).build
          fiber    <- env.useDiscard(ZIO.unit).fork
          _        <- fiber.interrupt
          actual   <- ref.get
        } yield (assert(actual)(contains(acquire1)) ==> assert(actual)(contains(release1))) &&
          (assert(actual)(contains(acquire2)) ==> assert(actual)(contains(release2)))
      } @@ zioTag(interruption) @@ nonFlaky,
      test("interruption with >>>") {
        for {
          ref      <- makeRef
          provider1 = makeProvider1(ref)
          provider2 = makeProvider2(ref)
          env       = (provider1 >>> provider2).build
          fiber    <- env.useDiscard(ZIO.unit).fork
          _        <- fiber.interrupt
          actual   <- ref.get
        } yield (assert(actual)(contains(acquire1)) ==> assert(actual)(contains(release1))) &&
          (assert(actual)(contains(acquire2)) ==> assert(actual)(contains(release2)))
      } @@ zioTag(interruption) @@ nonFlaky,
      test("interruption with multiple providers") {
        for {
          ref      <- makeRef
          provider1 = makeProvider1(ref)
          provider2 = makeProvider2(ref)
          provider3 = makeProvider3(ref)
          env       = ((provider1 >>> provider2) ++ (provider1 >>> provider3)).build
          fiber    <- env.useDiscard(ZIO.unit).fork
          _        <- fiber.interrupt
          actual   <- ref.get
        } yield (assert(actual)(contains(acquire1)) ==> assert(actual)(contains(release1))) &&
          (assert(actual)(contains(acquire2)) ==> assert(actual)(contains(release2))) &&
          (assert(actual)(contains(acquire3)) ==> assert(actual)(contains(release3)))
      } @@ zioTag(interruption) @@ nonFlaky,
      test("providers can be acquired in parallel") {
        for {
          promise  <- Promise.make[Nothing, Unit]
          provider1 = ZProvider(ZManaged.never)
          provider2 =
            ZProvider(Managed.acquireReleaseWith(promise.succeed(()).map(ZEnvironment(_)))(_ => ZIO.unit))
          env = (provider1 ++ provider2).build
          _  <- env.useDiscard(ZIO.unit).forkDaemon
          _  <- promise.await
        } yield assertCompletes
      },
      test("map can map a provider to an unrelated type") {
        case class A(name: String, value: Int)
        case class B(name: String)
        val l1: Provider[Nothing, A]          = ZProvider.succeed(A("name", 1))
        val l2: ZProvider[String, Nothing, B] = (B.apply _).toProvider
        val live: Provider[Nothing, B]        = l1.map(a => ZEnvironment(a.get[A].name)) >>> l2
        assertM(ZIO.service[B].inject(live))(equalTo(B("name")))
      },
      test("memoization") {
        val expected = Vector(acquire1, release1)
        for {
          ref     <- makeRef
          memoized = makeProvider1(ref).memoize
          _ <- memoized.use { provider =>
                 for {
                   _ <- ZIO.environment[Module1].provide(provider)
                   _ <- ZIO.environment[Module1].provide(provider)
                 } yield ()
               }
          actual <- ref.get
        } yield assert(actual)(equalTo(expected))
      } @@ nonFlaky,
      test("orElse") {
        for {
          ref      <- makeRef
          provider1 = makeProvider1(ref)
          provider2 = makeProvider2(ref)
          env       = ((provider1 >>> ZProvider.fail("fail")) orElse provider2).build
          fiber    <- env.useDiscard(ZIO.unit).fork
          _        <- fiber.interrupt
          actual   <- ref.get
        } yield (assert(actual)(contains(acquire1)) ==> assert(actual)(contains(release1))) &&
          (assert(actual)(contains(acquire2)) ==> assert(actual)(contains(release2)))
      } @@ nonFlaky,
      test("passthrough") {
        val provider: ZProvider[Int, Nothing, String] =
          ((_: Int).toString).toProvider
        val live: ZProvider[Any, Nothing, Int with String] =
          ZProvider.succeed(1) >>> provider.passthrough
        val zio = for {
          i <- ZIO.service[Int]
          s <- ZIO.service[String]
        } yield (i, s)
        assertM(zio.inject(live))(equalTo((1, "1")))
      },
      test("fresh with ++") {
        val expected = Vector(acquire1, acquire1, release1, release1)
        for {
          ref      <- makeRef
          provider1 = makeProvider1(ref)
          env       = (provider1 ++ provider1.fresh).build
          _        <- env.useNow
          result   <- ref.get
        } yield assert(result)(equalTo(expected))
      } @@ nonFlaky,
      test("fresh with >>>") {
        val expected = Vector(acquire1, acquire1, release1, release1)
        for {
          ref      <- makeRef
          provider1 = makeProvider1(ref)
          env       = (provider1 >>> provider1.fresh).build
          _        <- env.useNow
          result   <- ref.get
        } yield assert(result)(equalTo(expected))
      } @@ nonFlaky,
      test("fresh with multiple providers") {
        val expected = Vector(acquire1, acquire1, release1, release1)
        for {
          ref      <- makeRef
          provider1 = makeProvider1(ref)
          env       = ((provider1 ++ provider1) ++ (provider1 ++ provider1).fresh).build
          _        <- env.useNow
          result   <- ref.get
        } yield assert(result)(equalTo(expected))
      } @@ nonFlaky,
      test("fresh with identical fresh providers") {
        for {
          ref      <- makeRef
          provider1 = makeProvider1(ref)
          provider2 = makeProvider2(ref)
          provider3 = makeProvider3(ref)
          env       = ((provider1.fresh >>> provider2) ++ (provider1.fresh >>> provider3)).build
          _        <- env.useNow
          result   <- ref.get
        } yield assert(result)(hasSize(equalTo(8)))
      } @@ nonFlaky,
      test("preserves identity of acquired resources") {
        for {
          testRef <- Ref.make(Vector[String]())
          provider = ZProvider {
                       for {
                         ref <-
                           Ref.make[Vector[String]](Vector()).toManagedWith(ref => ref.get.flatMap(testRef.set))
                         _ <- ZManaged.unit
                       } yield ref
                     }
          _      <- provider.build.use(_.get.update(_ :+ "test"))
          result <- testRef.get
        } yield assert(result)(equalTo(Vector("test")))
      },
      test("retry") {
        for {
          ref     <- Ref.make(0)
          effect   = ref.update(_ + 1) *> ZIO.fail("fail")
          provider = ZProvider.fromZIOEnvironment(effect).retry(Schedule.recurs(3))
          _       <- provider.build.useNow.ignore
          result  <- ref.get
        } yield assert(result)(equalTo(4))
      },
      test("error handling") {
        val sleep     = ZIO.sleep(100.milliseconds).inject(Clock.live)
        val provider1 = ZProvider.fail("foo")
        val provider2 = ZProvider.succeed("bar")
        val provider3 = ZProvider.succeed("baz")
        val provider4 = ZManaged.acquireReleaseWith(sleep)(_ => sleep).toProvider
        val env       = provider1 ++ ((provider2 ++ provider3) >+> provider4)
        assertM(ZIO.unit.provideCustom(env).exit)(fails(equalTo("foo")))
      },
      test("project") {
        final case class Person(name: String, age: Int)
        val personProvider = ZProvider.succeed(Person("User", 42))
        val ageProvider    = personProvider.project(_.age)
        assertM(ZIO.service[Int].inject(ageProvider))(equalTo(42))
      },
      test("tap") {
        for {
          ref     <- Ref.make("foo")
          provider = ZProvider.succeed("bar").tap(r => ref.set(r.get))
          _       <- provider.build.useNow
          value   <- ref.get
        } yield assert(value)(equalTo("bar"))
      },
      test("provides a partial environment to an effect") {
        val needsIntAndString = ZIO.environment[Int & String]
        val providesInt       = ZProvider.succeed(10)
        val needsString       = ZIO.provide(providesInt)(needsIntAndString)
        needsString
          .inject(ZProvider.succeed("hi"))
          .map { result =>
            assertTrue(
              result.get[Int] == 10,
              result.get[String] == "hi"
            )
          }
      },
      test(">>> provides a partial environment to another provider") {
        final case class FooService(ref: Ref[Int], string: String, boolean: Boolean) {
          def get: UIO[(Int, String, Boolean)] = ref.get.map(i => (i, string, boolean))
        }
        val fooBuilder    = (FooService.apply _).toProvider
        val provideRefInt = Ref.make(10).toProvider

        val needsStringAndBoolean = provideRefInt >>> fooBuilder

        ZIO
          .serviceWithZIO[FooService](_.get)
          .inject(needsStringAndBoolean, ZProvider.succeed("hi"), ZProvider.succeed(true))
          .map { case (int, string, boolean) =>
            assertTrue(
              int == 10,
              string == "hi",
              boolean == true
            )
          }
      },
      test(">+> provides a partial environment to another provider") {
        final case class FooService(ref: Ref[Int], string: String, boolean: Boolean) {
          def get: UIO[(Int, String, Boolean)] = ref.get.map(i => (i, string, boolean))
        }
        val fooBuilder    = (FooService.apply _).toProvider
        val provideRefInt = Ref.make(10).toProvider

        val needsStringAndBoolean = provideRefInt >+> fooBuilder

        ZIO
          .serviceWithZIO[FooService](_.get)
          .zip(ZIO.serviceWithZIO[Ref[Int]](_.get))
          .inject(needsStringAndBoolean, ZProvider.succeed("hi"), ZProvider.succeed(true))
          .map { case (int, string, boolean, int2) =>
            assertTrue(
              int == 10,
              int2 == 10,
              string == "hi",
              boolean == true
            )
          }
      }
    )
}
