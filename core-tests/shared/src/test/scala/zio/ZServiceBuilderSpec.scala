package zio

import zio.test.Assertion._
import zio.test.TestAspect.nonFlaky
import zio.test._

object ZServiceBuilderSpec extends ZIOBaseSpec {

  import ZIOTag._

  trait Animal
  trait Dog extends Animal
  trait Cat extends Animal

  def testSize[R](serviceBuilder: ServiceBuilder[Nothing, R], n: Int, label: String = ""): UIO[TestResult] =
    serviceBuilder.build.use(env => ZIO.succeed(assert(env.size)(if (label == "") equalTo(n) else equalTo(n) ?? label)))

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

  def makeServiceBuilder1(ref: Ref[Vector[String]]): ZServiceBuilder[Any, Nothing, Module1] =
    ZServiceBuilder {
      ZManaged.acquireReleaseWith(ref.update(_ :+ acquire1).as(ZEnvironment(new Module1.Service {})))(_ =>
        ref.update(_ :+ release1)
      )
    }

  type Module2 = Module2.Service

  object Module2 {
    trait Service
  }

  def makeServiceBuilder2(ref: Ref[Vector[String]]): ZServiceBuilder[Any, Nothing, Module2] =
    ZServiceBuilder {
      ZManaged.acquireReleaseWith(ref.update(_ :+ acquire2).as(ZEnvironment(new Module2.Service {})))(_ =>
        ref.update(_ :+ release2)
      )
    }

  type Module3 = Module3.Service

  object Module3 {
    trait Service
  }

  def makeServiceBuilder3(ref: Ref[Vector[String]]): ZServiceBuilder[Any, Nothing, Module3] =
    ZServiceBuilder {
      ZManaged.acquireReleaseWith(ref.update(_ :+ acquire3).as(ZEnvironment(new Module3.Service {})))(_ =>
        ref.update(_ :+ release3)
      )
    }

  def makeRef: UIO[Ref[Vector[String]]] =
    Ref.make(Vector.empty)

  def spec =
    suite("ZServiceBuilderSpec")(
      test("Size of >>> (1)") {
        val serviceBuilder = ZServiceBuilder.succeed(1) >>> ((i: Int) => i.toString).toServiceBuilder

        testSize(serviceBuilder, 1)
      },
      test("Size of >>> (2)") {
        val serviceBuilder = ZServiceBuilder.succeed(1) >>>
          (((i: Int) => i.toString).toServiceBuilder ++
            ((i: Int) => i % 2 == 0).toServiceBuilder)

        testSize(serviceBuilder, 2)
      },
      test("Size of Test service builders") {
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
        val serviceBuilder = ZEnv.live >>>
          (Annotations.live ++ (Live.default >>> TestConsole.debug) ++
            Live.default ++ TestRandom.deterministic ++ Sized.live(100)
            ++ TestSystem.default)

        testSize(serviceBuilder, 6)
      },
      test("sharing with ++") {
        val expected = Vector(acquire1, release1)
        for {
          ref            <- makeRef
          serviceBuilder1 = makeServiceBuilder1(ref)
          env             = (serviceBuilder1 ++ serviceBuilder1).build
          _              <- env.useDiscard(ZIO.unit)
          actual         <- ref.get
        } yield assert(actual)(equalTo(expected))
      } @@ nonFlaky,
      test("sharing itself with ++") {
        val m1              = new Module1.Service {}
        val serviceBuilder1 = ZServiceBuilder.succeed(m1)
        val env             = serviceBuilder1 ++ (serviceBuilder1 ++ serviceBuilder1)
        env.build.use(m => ZIO(assert(m.get)(equalTo(m1))))
      } @@ nonFlaky,
      test("sharing with >>>") {
        val expected = Vector(acquire1, release1)
        for {
          ref            <- makeRef
          serviceBuilder1 = makeServiceBuilder1(ref)
          env             = (serviceBuilder1 >>> serviceBuilder1).build
          _              <- env.useDiscard(ZIO.unit)
          actual         <- ref.get
        } yield assert(actual)(equalTo(expected))
      } @@ nonFlaky,
      test("sharing with multiple service builders") {
        for {
          ref            <- makeRef
          serviceBuilder1 = makeServiceBuilder1(ref)
          serviceBuilder2 = makeServiceBuilder2(ref)
          serviceBuilder3 = makeServiceBuilder3(ref)
          env             = ((serviceBuilder1 >>> serviceBuilder2) ++ (serviceBuilder1 >>> serviceBuilder3)).build
          _              <- env.useDiscard(ZIO.unit)
          actual         <- ref.get
        } yield assert(actual(0))(equalTo(acquire1)) &&
          assert(actual.slice(1, 3))(hasSameElements(Vector(acquire2, acquire3))) &&
          assert(actual.slice(3, 5))(hasSameElements(Vector(release2, release3))) &&
          assert(actual(5))(equalTo(release1))
      } @@ nonFlaky,
      test("finalizers with ++") {
        for {
          ref            <- makeRef
          serviceBuilder1 = makeServiceBuilder1(ref)
          serviceBuilder2 = makeServiceBuilder2(ref)
          env             = (serviceBuilder1 ++ serviceBuilder2).build
          _              <- env.useDiscard(ZIO.unit)
          actual         <- ref.get
        } yield assert(actual.slice(0, 2))(hasSameElements(Vector(acquire1, acquire2))) &&
          assert(actual.slice(2, 4))(hasSameElements(Vector(release1, release2)))
      } @@ nonFlaky,
      test("finalizers with >>>") {
        val expected = Vector(acquire1, acquire2, release2, release1)
        for {
          ref            <- makeRef
          serviceBuilder1 = makeServiceBuilder1(ref)
          serviceBuilder2 = makeServiceBuilder2(ref)
          env             = (serviceBuilder1 >>> serviceBuilder2).build
          _              <- env.useDiscard(ZIO.unit)
          actual         <- ref.get
        } yield assert(actual)(equalTo(expected))
      } @@ nonFlaky,
      test("finalizers with multiple service builders") {
        val expected =
          Vector(acquire1, acquire2, acquire3, release3, release2, release1)
        for {
          ref            <- makeRef
          serviceBuilder1 = makeServiceBuilder1(ref)
          serviceBuilder2 = makeServiceBuilder2(ref)
          serviceBuilder3 = makeServiceBuilder3(ref)
          env             = (serviceBuilder1 >>> serviceBuilder2 >>> serviceBuilder3).build
          _              <- env.useDiscard(ZIO.unit)
          actual         <- ref.get
        } yield assert(actual)(equalTo(expected))
      } @@ nonFlaky,
      test("map does not interfere with sharing") {
        for {
          ref            <- makeRef
          serviceBuilder1 = makeServiceBuilder1(ref)
          serviceBuilder2 = makeServiceBuilder2(ref)
          serviceBuilder3 = makeServiceBuilder3(ref)
          env             = ((serviceBuilder1.map(identity) >>> serviceBuilder2) ++ (serviceBuilder1 >>> serviceBuilder3)).build
          _              <- env.useDiscard(ZIO.unit)
          actual         <- ref.get
        } yield assert(actual(0))(equalTo(acquire1)) &&
          assert(actual.slice(1, 3))(hasSameElements(Vector(acquire2, acquire3))) &&
          assert(actual.slice(3, 5))(hasSameElements(Vector(release2, release3))) &&
          assert(actual(5))(equalTo(release1))
      } @@ nonFlaky,
      test("mapError does not interfere with sharing") {
        implicit val canFail = CanFail
        for {
          ref            <- makeRef
          serviceBuilder1 = makeServiceBuilder1(ref)
          serviceBuilder2 = makeServiceBuilder2(ref)
          serviceBuilder3 = makeServiceBuilder3(ref)
          env =
            ((serviceBuilder1.mapError(identity) >>> serviceBuilder2) ++ (serviceBuilder1 >>> serviceBuilder3)).build
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
          ref            <- makeRef
          serviceBuilder1 = makeServiceBuilder1(ref)
          serviceBuilder2 = makeServiceBuilder2(ref)
          serviceBuilder3 = makeServiceBuilder3(ref)
          env             = ((serviceBuilder1.orDie >>> serviceBuilder2) ++ (serviceBuilder1 >>> serviceBuilder3)).build
          _              <- env.useDiscard(ZIO.unit)
          actual         <- ref.get
        } yield assert(actual(0))(equalTo(acquire1)) &&
          assert(actual.slice(1, 3))(hasSameElements(Vector(acquire2, acquire3))) &&
          assert(actual.slice(3, 5))(hasSameElements(Vector(release2, release3))) &&
          assert(actual(5))(equalTo(release1))
      } @@ nonFlaky,
      test("interruption with ++") {
        for {
          ref            <- makeRef
          serviceBuilder1 = makeServiceBuilder1(ref)
          serviceBuilder2 = makeServiceBuilder2(ref)
          env             = (serviceBuilder1 ++ serviceBuilder2).build
          fiber          <- env.useDiscard(ZIO.unit).fork
          _              <- fiber.interrupt
          actual         <- ref.get
        } yield (assert(actual)(contains(acquire1)) ==> assert(actual)(contains(release1))) &&
          (assert(actual)(contains(acquire2)) ==> assert(actual)(contains(release2)))
      } @@ zioTag(interruption) @@ nonFlaky,
      test("interruption with >>>") {
        for {
          ref            <- makeRef
          serviceBuilder1 = makeServiceBuilder1(ref)
          serviceBuilder2 = makeServiceBuilder2(ref)
          env             = (serviceBuilder1 >>> serviceBuilder2).build
          fiber          <- env.useDiscard(ZIO.unit).fork
          _              <- fiber.interrupt
          actual         <- ref.get
        } yield (assert(actual)(contains(acquire1)) ==> assert(actual)(contains(release1))) &&
          (assert(actual)(contains(acquire2)) ==> assert(actual)(contains(release2)))
      } @@ zioTag(interruption) @@ nonFlaky,
      test("interruption with multiple service builders") {
        for {
          ref            <- makeRef
          serviceBuilder1 = makeServiceBuilder1(ref)
          serviceBuilder2 = makeServiceBuilder2(ref)
          serviceBuilder3 = makeServiceBuilder3(ref)
          env             = ((serviceBuilder1 >>> serviceBuilder2) ++ (serviceBuilder1 >>> serviceBuilder3)).build
          fiber          <- env.useDiscard(ZIO.unit).fork
          _              <- fiber.interrupt
          actual         <- ref.get
        } yield (assert(actual)(contains(acquire1)) ==> assert(actual)(contains(release1))) &&
          (assert(actual)(contains(acquire2)) ==> assert(actual)(contains(release2))) &&
          (assert(actual)(contains(acquire3)) ==> assert(actual)(contains(release3)))
      } @@ zioTag(interruption) @@ nonFlaky,
      test("service builders can be acquired in parallel") {
        for {
          promise        <- Promise.make[Nothing, Unit]
          serviceBuilder1 = ZServiceBuilder(ZManaged.never)
          serviceBuilder2 =
            ZServiceBuilder(Managed.acquireReleaseWith(promise.succeed(()).map(ZEnvironment(_)))(_ => ZIO.unit))
          env = (serviceBuilder1 ++ serviceBuilder2).build
          _  <- env.useDiscard(ZIO.unit).forkDaemon
          _  <- promise.await
        } yield assertCompletes
      },
      test("map can map a service builder to an unrelated type") {
        case class A(name: String, value: Int)
        case class B(name: String)
        val l1: ServiceBuilder[Nothing, A]          = ZServiceBuilder.succeed(A("name", 1))
        val l2: ZServiceBuilder[String, Nothing, B] = (B.apply _).toServiceBuilder
        val live: ServiceBuilder[Nothing, B]        = l1.map(a => ZEnvironment(a.get[A].name)) >>> l2
        assertM(ZIO.access[B](_.get).inject(live))(equalTo(B("name")))
      },
      test("memoization") {
        val expected = Vector(acquire1, release1)
        for {
          ref     <- makeRef
          memoized = makeServiceBuilder1(ref).memoize
          _ <- memoized.use { serviceBuilder =>
                 for {
                   _ <- ZIO.environment[Module1].provideServices(serviceBuilder)
                   _ <- ZIO.environment[Module1].provideServices(serviceBuilder)
                 } yield ()
               }
          actual <- ref.get
        } yield assert(actual)(equalTo(expected))
      } @@ nonFlaky,
      test("orElse") {
        for {
          ref            <- makeRef
          serviceBuilder1 = makeServiceBuilder1(ref)
          serviceBuilder2 = makeServiceBuilder2(ref)
          env             = ((serviceBuilder1 >>> ZServiceBuilder.fail("fail")) orElse serviceBuilder2).build
          fiber          <- env.useDiscard(ZIO.unit).fork
          _              <- fiber.interrupt
          actual         <- ref.get
        } yield (assert(actual)(contains(acquire1)) ==> assert(actual)(contains(release1))) &&
          (assert(actual)(contains(acquire2)) ==> assert(actual)(contains(release2)))
      } @@ nonFlaky,
      test("passthrough") {
        val serviceBuilder: ZServiceBuilder[Int, Nothing, String] =
          ((_: Int).toString).toServiceBuilder
        val live: ZServiceBuilder[Any, Nothing, Int with String] =
          ZServiceBuilder.succeed(1) >>> serviceBuilder.passthrough
        val zio = for {
          i <- ZIO.environment[Int].map(_.get[Int])
          s <- ZIO.environment[String].map(_.get[String])
        } yield (i, s)
        assertM(zio.inject(live))(equalTo((1, "1")))
      },
      test("fresh with ++") {
        val expected = Vector(acquire1, acquire1, release1, release1)
        for {
          ref            <- makeRef
          serviceBuilder1 = makeServiceBuilder1(ref)
          env             = (serviceBuilder1 ++ serviceBuilder1.fresh).build
          _              <- env.useNow
          result         <- ref.get
        } yield assert(result)(equalTo(expected))
      } @@ nonFlaky,
      test("fresh with >>>") {
        val expected = Vector(acquire1, acquire1, release1, release1)
        for {
          ref            <- makeRef
          serviceBuilder1 = makeServiceBuilder1(ref)
          env             = (serviceBuilder1 >>> serviceBuilder1.fresh).build
          _              <- env.useNow
          result         <- ref.get
        } yield assert(result)(equalTo(expected))
      } @@ nonFlaky,
      test("fresh with multiple service builders") {
        val expected = Vector(acquire1, acquire1, release1, release1)
        for {
          ref            <- makeRef
          serviceBuilder1 = makeServiceBuilder1(ref)
          env             = ((serviceBuilder1 ++ serviceBuilder1) ++ (serviceBuilder1 ++ serviceBuilder1).fresh).build
          _              <- env.useNow
          result         <- ref.get
        } yield assert(result)(equalTo(expected))
      } @@ nonFlaky,
      test("fresh with identical fresh service builders") {
        for {
          ref            <- makeRef
          serviceBuilder1 = makeServiceBuilder1(ref)
          serviceBuilder2 = makeServiceBuilder2(ref)
          serviceBuilder3 = makeServiceBuilder3(ref)
          env             = ((serviceBuilder1.fresh >>> serviceBuilder2) ++ (serviceBuilder1.fresh >>> serviceBuilder3)).build
          _              <- env.useNow
          result         <- ref.get
        } yield assert(result)(hasSize(equalTo(8)))
      } @@ nonFlaky,
      test("preserves identity of acquired resources") {
        for {
          testRef <- Ref.make(Vector[String]())
          serviceBuilder = ZServiceBuilder {
                             for {
                               ref <-
                                 Ref.make[Vector[String]](Vector()).toManagedWith(ref => ref.get.flatMap(testRef.set))
                               _ <- ZManaged.unit
                             } yield ZEnvironment(ref)
                           }
          _      <- serviceBuilder.build.use(_.get.update(_ :+ "test"))
          result <- testRef.get
        } yield assert(result)(equalTo(Vector("test")))
      },
      test("retry") {
        for {
          ref           <- Ref.make(0)
          effect         = ref.update(_ + 1) *> ZIO.fail("fail")
          serviceBuilder = ZServiceBuilder.fromZIOMany(effect).retry(Schedule.recurs(3))
          _             <- serviceBuilder.build.useNow.ignore
          result        <- ref.get
        } yield assert(result)(equalTo(4))
      },
      test("error handling") {
        val sleep           = ZIO.sleep(100.milliseconds).inject(Clock.live)
        val serviceBuilder1 = ZServiceBuilder.fail("foo")
        val serviceBuilder2 = ZServiceBuilder.succeed("bar")
        val serviceBuilder3 = ZServiceBuilder.succeed("baz")
        val serviceBuilder4 = ZManaged.acquireReleaseWith(sleep)(_ => sleep).toServiceBuilder
        val env             = serviceBuilder1 ++ ((serviceBuilder2 ++ serviceBuilder3) >+> serviceBuilder4)
        assertM(ZIO.unit.provideCustomServices(env).exit)(fails(equalTo("foo")))
      },
      test("project") {
        final case class Person(name: String, age: Int)
        val personServiceBuilder = ZServiceBuilder.succeed(Person("User", 42))
        val ageServiceBuilder    = personServiceBuilder.project(_.age)
        assertM(ZIO.service[Int].inject(ageServiceBuilder))(equalTo(42))
      },
      test("tap") {
        for {
          ref           <- Ref.make("foo")
          serviceBuilder = ZServiceBuilder.succeed("bar").tap(r => ref.set(r.get))
          _             <- serviceBuilder.build.useNow
          value         <- ref.get
        } yield assert(value)(equalTo("bar"))
      },
      test("provides a partial environment to an effect") {
        val needsIntAndString = ZIO.environment[Int & String]
        val providesInt       = ZServiceBuilder.succeed(10)
        val needsString       = ZIO.provide(providesInt)(needsIntAndString)
        needsString
          .inject(ZServiceBuilder.succeed("hi"))
          .map { result =>
            assertTrue(
              result.get[Int] == 10,
              result.get[String] == "hi"
            )
          }
      },
      test(">>> provides a partial environment to another service builder") {
        final case class FooService(ref: Ref[Int], string: String, boolean: Boolean) {
          def get: UIO[(Int, String, Boolean)] = ref.get.map(i => (i, string, boolean))
        }
        val fooBuilder    = (FooService.apply _).toServiceBuilder
        val provideRefInt = Ref.make(10).toServiceBuilder

        val needsStringAndBoolean = provideRefInt >>> fooBuilder

        ZIO
          .serviceWith[FooService](_.get)
          .inject(needsStringAndBoolean, ZServiceBuilder.succeed("hi"), ZServiceBuilder.succeed(true))
          .map { case (int, string, boolean) =>
            assertTrue(
              int == 10,
              string == "hi",
              boolean == true
            )
          }
      },
      test(">+> provides a partial environment to another service builder") {
        final case class FooService(ref: Ref[Int], string: String, boolean: Boolean) {
          def get: UIO[(Int, String, Boolean)] = ref.get.map(i => (i, string, boolean))
        }
        val fooBuilder    = (FooService.apply _).toServiceBuilder
        val provideRefInt = Ref.make(10).toServiceBuilder

        val needsStringAndBoolean = provideRefInt >+> fooBuilder

        ZIO
          .serviceWith[FooService](_.get)
          .zip(ZIO.serviceWith[Ref[Int]](_.get))
          .inject(needsStringAndBoolean, ZServiceBuilder.succeed("hi"), ZServiceBuilder.succeed(true))
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
