package zio

import zio.test.Assertion._
import zio.test.TestAspect.nonFlaky
import zio.test._

object ZLayerSpec extends ZIOBaseSpec {

  import ZIOTag._

  def testSize[R](layer: Layer[Nothing, R], n: Int, label: String = ""): UIO[TestResult] =
    ZIO.scoped {
      layer.build.flatMap { env =>
        ZIO.succeed(assert(env.size)(if (label == "") equalTo(n) else equalTo(n) ?? label))
      }
    }

  val acquire1 = "Acquiring Module 1"
  val acquire2 = "Acquiring Module 2"
  val acquire3 = "Acquiring Module 3"
  val release1 = "Releasing Module 1"
  val release2 = "Releasing Module 2"
  val release3 = "Releasing Module 3"

  trait Service1

  def makeLayer1(ref: Ref[Vector[String]]): ZLayer[Any, Nothing, Service1] =
    ZLayer.scoped {
      ZIO.acquireRelease(ref.update(_ :+ acquire1).as(new Service1 {}))(_ => ref.update(_ :+ release1))
    }

  trait Service2

  def makeLayer2(ref: Ref[Vector[String]]): ZLayer[Any, Nothing, Service2] =
    ZLayer.scoped {
      ZIO.acquireRelease(ref.update(_ :+ acquire2).as(new Service2 {}))(_ => ref.update(_ :+ release2))
    }

  trait Service3

  def makeLayer3(ref: Ref[Vector[String]]): ZLayer[Any, Nothing, Service3] =
    ZLayer.scoped {
      ZIO.acquireRelease(ref.update(_ :+ acquire3).as(new Service3 {}))(_ => ref.update(_ :+ release3))
    }

  def makeRef: UIO[Ref[Vector[String]]] =
    Ref.make(Vector.empty)

  def spec =
    suite("ZLayerSpec")(
      test("Size of >>> (1)") {
        val layer = ZLayer.succeed(1) >>> ((i: Int) => i.toString).toLayer

        testSize(layer, 1)
      },
      test("Size of >>> (2)") {
        val layer = ZLayer.succeed(1) >>>
          (((i: Int) => i.toString).toLayer ++
            ((i: Int) => i % 2 == 0).toLayer)

        testSize(layer, 2)
      },
      test("Size of Test layers") {
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
        val layer = ZEnv.live >>>
          (Annotations.live ++ (Live.default >>> TestConsole.debug) ++
            Live.default ++ TestRandom.deterministic ++ Sized.live(100)
            ++ TestSystem.default)

        testSize(layer, 6)
      },
      test("sharing with ++") {
        val expected = Vector(acquire1, release1)
        for {
          ref    <- makeRef
          layer1  = makeLayer1(ref)
          env     = (layer1 ++ layer1).build
          _      <- ZIO.scoped(env.unit)
          actual <- ref.get
        } yield assert(actual)(equalTo(expected))
      } @@ nonFlaky,
      test("sharing itself with ++") {
        val m1     = new Service1 {}
        val layer1 = ZLayer.succeed(m1)
        val env    = layer1 ++ (layer1 ++ layer1)
        ZIO.scoped(env.build.flatMap(m => ZIO.attempt(assert(m.get)(equalTo(m1)))))
      } @@ nonFlaky,
      test("sharing with >>>") {
        val expected = Vector(acquire1, release1)
        for {
          ref    <- makeRef
          layer1  = makeLayer1(ref)
          env     = (layer1 >>> layer1).build
          _      <- ZIO.scoped(env.unit)
          actual <- ref.get
        } yield assert(actual)(equalTo(expected))
      } @@ nonFlaky,
      test("sharing with multiple layers") {
        for {
          ref    <- makeRef
          layer1  = makeLayer1(ref)
          layer2  = makeLayer2(ref)
          layer3  = makeLayer3(ref)
          env     = ((layer1 >>> layer2) ++ (layer1 >>> layer3)).build
          _      <- ZIO.scoped(env.unit)
          actual <- ref.get
        } yield assert(actual(0))(equalTo(acquire1)) &&
          assert(actual.slice(1, 3))(hasSameElements(Vector(acquire2, acquire3))) &&
          assert(actual.slice(3, 5))(hasSameElements(Vector(release2, release3))) &&
          assert(actual(5))(equalTo(release1))
      } @@ nonFlaky,
      test("finalizers with ++") {
        for {
          ref    <- makeRef
          layer1  = makeLayer1(ref)
          layer2  = makeLayer2(ref)
          env     = (layer1 ++ layer2).build
          _      <- ZIO.scoped(env.unit)
          actual <- ref.get
        } yield assert(actual.slice(0, 2))(hasSameElements(Vector(acquire1, acquire2))) &&
          assert(actual.slice(2, 4))(hasSameElements(Vector(release1, release2)))
      } @@ nonFlaky,
      test("finalizers with >>>") {
        val expected = Vector(acquire1, acquire2, release2, release1)
        for {
          ref    <- makeRef
          layer1  = makeLayer1(ref)
          layer2  = makeLayer2(ref)
          env     = (layer1 >>> layer2).build
          _      <- ZIO.scoped(env.unit)
          actual <- ref.get
        } yield assert(actual)(equalTo(expected))
      } @@ nonFlaky,
      test("finalizers with multiple layers") {
        val expected =
          Vector(acquire1, acquire2, acquire3, release3, release2, release1)
        for {
          ref    <- makeRef
          layer1  = makeLayer1(ref)
          layer2  = makeLayer2(ref)
          layer3  = makeLayer3(ref)
          env     = (layer1 >>> layer2 >>> layer3).build
          _      <- ZIO.scoped(env.unit)
          actual <- ref.get
        } yield assert(actual)(equalTo(expected))
      } @@ nonFlaky,
      test("map does not interfere with sharing") {
        for {
          ref    <- makeRef
          layer1  = makeLayer1(ref)
          layer2  = makeLayer2(ref)
          layer3  = makeLayer3(ref)
          env     = ((layer1.map(identity) >>> layer2) ++ (layer1 >>> layer3)).build
          _      <- ZIO.scoped(env.unit)
          actual <- ref.get
        } yield assert(actual(0))(equalTo(acquire1)) &&
          assert(actual.slice(1, 3))(hasSameElements(Vector(acquire2, acquire3))) &&
          assert(actual.slice(3, 5))(hasSameElements(Vector(release2, release3))) &&
          assert(actual(5))(equalTo(release1))
      } @@ nonFlaky,
      test("mapError does not interfere with sharing") {
        implicit val canFail = CanFail
        for {
          ref   <- makeRef
          layer1 = makeLayer1(ref)
          layer2 = makeLayer2(ref)
          layer3 = makeLayer3(ref)
          env =
            ((layer1.mapError(identity) >>> layer2) ++ (layer1 >>> layer3)).build
          _      <- ZIO.scoped(env.unit)
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
          layer1  = makeLayer1(ref)
          layer2  = makeLayer2(ref)
          layer3  = makeLayer3(ref)
          env     = ((layer1.orDie >>> layer2) ++ (layer1 >>> layer3)).build
          _      <- ZIO.scoped(env.unit)
          actual <- ref.get
        } yield assert(actual(0))(equalTo(acquire1)) &&
          assert(actual.slice(1, 3))(hasSameElements(Vector(acquire2, acquire3))) &&
          assert(actual.slice(3, 5))(hasSameElements(Vector(release2, release3))) &&
          assert(actual(5))(equalTo(release1))
      } @@ nonFlaky,
      test("interruption with ++") {
        for {
          ref    <- makeRef
          layer1  = makeLayer1(ref)
          layer2  = makeLayer2(ref)
          env     = (layer1 ++ layer2).build
          fiber  <- ZIO.scoped(env.unit).fork
          _      <- fiber.interrupt
          actual <- ref.get
        } yield (assert(actual)(contains(acquire1)) ==> assert(actual)(contains(release1))) &&
          (assert(actual)(contains(acquire2)) ==> assert(actual)(contains(release2)))
      } @@ zioTag(interruption) @@ nonFlaky,
      test("interruption with >>>") {
        for {
          ref    <- makeRef
          layer1  = makeLayer1(ref)
          layer2  = makeLayer2(ref)
          env     = (layer1 >>> layer2).build
          fiber  <- ZIO.scoped(env.unit).fork
          _      <- fiber.interrupt
          actual <- ref.get
        } yield (assert(actual)(contains(acquire1)) ==> assert(actual)(contains(release1))) &&
          (assert(actual)(contains(acquire2)) ==> assert(actual)(contains(release2)))
      } @@ zioTag(interruption) @@ nonFlaky,
      test("interruption with multiple layers") {
        for {
          ref    <- makeRef
          layer1  = makeLayer1(ref)
          layer2  = makeLayer2(ref)
          layer3  = makeLayer3(ref)
          env     = ((layer1 >>> layer2) ++ (layer1 >>> layer3)).build
          fiber  <- ZIO.scoped(env.unit).fork
          _      <- fiber.interrupt
          actual <- ref.get
        } yield (assert(actual)(contains(acquire1)) ==> assert(actual)(contains(release1))) &&
          (assert(actual)(contains(acquire2)) ==> assert(actual)(contains(release2))) &&
          (assert(actual)(contains(acquire3)) ==> assert(actual)(contains(release3)))
      } @@ zioTag(interruption) @@ nonFlaky,
      test("layers can be acquired in parallel") {
        for {
          promise <- Promise.make[Nothing, Unit]
          layer1   = ZLayer(ZIO.never)
          layer2 =
            ZLayer(ZIO.acquireRelease(promise.succeed(()).map(ZEnvironment(_)))(_ => ZIO.unit))
          env = (layer1 ++ layer2).build
          _  <- ZIO.scoped(env.unit).forkDaemon
          _  <- promise.await
        } yield assertCompletes
      },
      test("map can map a layer to an unrelated type") {
        case class A(name: String, value: Int)
        case class B(name: String)
        val l1: Layer[Nothing, A]          = ZLayer.succeed(A("name", 1))
        val l2: ZLayer[String, Nothing, B] = (B.apply _).toLayer
        val live: Layer[Nothing, B]        = l1.map(a => ZEnvironment(a.get[A].name)) >>> l2
        assertM(ZIO.service[B].provide(live))(equalTo(B("name")))
      },
      test("memoization") {
        val expected = Vector(acquire1, release1)
        for {
          ref     <- makeRef
          memoized = makeLayer1(ref).memoize
          _ <- ZIO.scoped {
                 memoized.flatMap { layer =>
                   for {
                     _ <- ZIO.environment[Service1].provideLayer(layer)
                     _ <- ZIO.environment[Service1].provideLayer(layer)
                   } yield ()
                 }
               }
          actual <- ref.get
        } yield assert(actual)(equalTo(expected))
      } @@ nonFlaky,
      test("orElse") {
        for {
          ref    <- makeRef
          layer1  = makeLayer1(ref)
          layer2  = makeLayer2(ref)
          env     = ((layer1 >>> ZLayer.fail("fail")) orElse layer2).build
          fiber  <- ZIO.scoped(env.unit).fork
          _      <- fiber.interrupt
          actual <- ref.get
        } yield (assert(actual)(contains(acquire1)) ==> assert(actual)(contains(release1))) &&
          (assert(actual)(contains(acquire2)) ==> assert(actual)(contains(release2)))
      } @@ nonFlaky,
      test("passthrough") {
        val layer: ZLayer[Int, Nothing, String] =
          ((_: Int).toString).toLayer
        val live: ZLayer[Any, Nothing, Int with String] =
          ZLayer.succeed(1) >>> layer.passthrough
        val zio = for {
          i <- ZIO.service[Int]
          s <- ZIO.service[String]
        } yield (i, s)
        assertM(zio.provide(live))(equalTo((1, "1")))
      },
      test("fresh with ++") {
        val expected = Vector(acquire1, acquire1, release1, release1)
        for {
          ref    <- makeRef
          layer1  = makeLayer1(ref)
          env     = (layer1 ++ layer1.fresh).build
          _      <- ZIO.scoped(env)
          result <- ref.get
        } yield assert(result)(equalTo(expected))
      } @@ nonFlaky,
      test("fresh with >>>") {
        val expected = Vector(acquire1, acquire1, release1, release1)
        for {
          ref    <- makeRef
          layer1  = makeLayer1(ref)
          env     = (layer1 >>> layer1.fresh).build
          _      <- ZIO.scoped(env)
          result <- ref.get
        } yield assert(result)(equalTo(expected))
      } @@ nonFlaky,
      test("fresh with multiple layers") {
        val expected = Vector(acquire1, acquire1, release1, release1)
        for {
          ref    <- makeRef
          layer1  = makeLayer1(ref)
          env     = ((layer1 ++ layer1) ++ (layer1 ++ layer1).fresh).build
          _      <- ZIO.scoped(env)
          result <- ref.get
        } yield assert(result)(equalTo(expected))
      } @@ nonFlaky,
      test("fresh with identical fresh layers") {
        for {
          ref    <- makeRef
          layer1  = makeLayer1(ref)
          layer2  = makeLayer2(ref)
          layer3  = makeLayer3(ref)
          env     = ((layer1.fresh >>> layer2) ++ (layer1.fresh >>> layer3)).build
          _      <- ZIO.scoped(env)
          result <- ref.get
        } yield assert(result)(hasSize(equalTo(8)))
      } @@ nonFlaky,
      test("preserves identity of acquired resources") {
        for {
          testRef <- Ref.make(Vector[String]())
          layer =
            ZLayer {
              for {
                ref <-
                  ZIO.acquireRelease(Ref.make[Vector[String]](Vector()))(_.get.flatMap(testRef.set))
                _ <- ZIO.unit
              } yield ref
            }
          _      <- ZIO.scoped(layer.build.flatMap(_.get.update(_ :+ "test")))
          result <- testRef.get
        } yield assert(result)(equalTo(Vector("test")))
      },
      test("retry") {
        for {
          ref    <- Ref.make(0)
          effect  = ref.update(_ + 1) *> ZIO.fail("fail")
          layer   = ZLayer.fromZIOEnvironment(effect).retry(Schedule.recurs(3))
          _      <- ZIO.scoped(layer.build).ignore
          result <- ref.get
        } yield assert(result)(equalTo(4))
      },
      test("error handling") {
        val sleep  = ZIO.sleep(100.milliseconds).provide(Clock.live)
        val layer1 = ZLayer.fail("foo")
        val layer2 = ZLayer.succeed("bar")
        val layer3 = ZLayer.succeed("baz")
        val layer4 = ZLayer.scoped[Any](ZIO.acquireRelease(sleep)(_ => sleep))
        val env    = layer1 ++ ((layer2 ++ layer3) >+> layer4)
        assertM(ZIO.unit.provideCustomLayer(env).exit)(fails(equalTo("foo")))
      },
      test("project") {
        final case class Person(name: String, age: Int)
        val personLayer = ZLayer.succeed(Person("User", 42))
        val ageLayer    = personLayer.project(_.age)
        assertM(ZIO.service[Int].provide(ageLayer))(equalTo(42))
      },
      test("tap") {
        for {
          ref   <- Ref.make("foo")
          layer  = ZLayer.succeed("bar").tap(r => ref.set(r.get))
          _     <- ZIO.scoped(layer.build)
          value <- ref.get
        } yield assert(value)(equalTo("bar"))
      },
      test("provides a partial environment to an effect") {
        val needsIntAndString = ZIO.environment[Int & String]
        val providesInt       = ZLayer.succeed(10)
        val needsString       = ZIO.provideLayer(providesInt)(needsIntAndString)
        needsString
          .provide(ZLayer.succeed("hi"))
          .map { result =>
            assertTrue(
              result.get[Int] == 10,
              result.get[String] == "hi"
            )
          }
      },
      test(">>> provides a partial environment to another layer") {
        final case class FooService(ref: Ref[Int], string: String, boolean: Boolean) {
          def get: UIO[(Int, String, Boolean)] = ref.get.map(i => (i, string, boolean))
        }
        val fooBuilder    = (FooService.apply _).toLayer
        val provideRefInt = Ref.make(10).toLayer

        val needsStringAndBoolean = provideRefInt >>> fooBuilder

        ZIO
          .serviceWithZIO[FooService](_.get)
          .provide(needsStringAndBoolean, ZLayer.succeed("hi"), ZLayer.succeed(true))
          .map { case (int, string, boolean) =>
            assertTrue(
              int == 10,
              string == "hi",
              boolean == true
            )
          }
      },
      test(">+> provides a partial environment to another layer") {
        final case class FooService(ref: Ref[Int], string: String, boolean: Boolean) {
          def get: UIO[(Int, String, Boolean)] = ref.get.map(i => (i, string, boolean))
        }
        val fooBuilder    = (FooService.apply _).toLayer
        val provideRefInt = Ref.make(10).toLayer

        val needsStringAndBoolean = provideRefInt >+> fooBuilder

        ZIO
          .serviceWithZIO[FooService](_.get)
          .zip(ZIO.serviceWithZIO[Ref[Int]](_.get))
          .provide(needsStringAndBoolean, ZLayer.succeed("hi"), ZLayer.succeed(true))
          .map { case (int, string, boolean, int2) =>
            assertTrue(
              int == 10,
              int2 == 10,
              string == "hi",
              boolean == true
            )
          }
      },
      test("apply provides an effect with part of its required environment") {
        val needsIntAndString = ZIO.environment[Int & String]
        val providesInt       = ZLayer.succeed(10)
        val needsString       = providesInt(needsIntAndString)
        needsString
          .provideLayer(ZLayer.succeed("hi"))
          .map { result =>
            assertTrue(
              result.get[Int] == 10,
              result.get[String] == "hi"
            )
          }
      },
      test("caching values in dependencies") {
        case class Config(value: Int)
        case class A(value: Int)
        val aLayer = ((conf: Config) => A(conf.value)).toLayer

        case class B(value: Int)
        val bLayer = ((a: A) => B(a.value)).toLayer

        case class C(value: Int)
        val cLayer = ((a: A) => C(a.value)).toLayer

        val fedB = (ZLayer.succeed(Config(1)) >>> aLayer) >>> bLayer
        val fedC = (ZLayer.succeed(Config(2)) >>> aLayer) >>> cLayer
        for {
          tuple <- ZIO.scoped((fedB ++ fedC).build.map(v => (v.get[B], v.get[C])))
          (a, b) = tuple
        } yield assert(a.value)(equalTo(b.value))
      },
      test("extend scope") {
        for {
          ref  <- Ref.make[Vector[String]](Vector.empty)
          layer = makeLayer1(ref).extendScope
          acquire <- ZIO.scoped {
                       for {
                         _       <- ZIO.unit.provideLayer(layer)
                         acquire <- ref.get
                       } yield acquire
                     }
          release <- ref.get
        } yield assertTrue(acquire == Vector("Acquiring Module 1")) &&
          assertTrue(release == Vector("Acquiring Module 1", "Releasing Module 1"))
      }
    )
}
