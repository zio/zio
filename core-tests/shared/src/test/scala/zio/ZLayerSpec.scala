package zio

import zio.clock._
import zio.duration._
import zio.test.Assertion._
import zio.test.TestAspect.nonFlaky
import zio.test._
import zio.test.environment._

object ZLayerSpec extends ZIOBaseSpec {

  import ZIOTag._

  trait Animal
  trait Dog extends Animal
  trait Cat extends Animal

  def testSize[R <: Has[_]](layer: Layer[Nothing, R], n: Int, label: String = ""): UIO[TestResult] =
    layer.build.use(env => ZIO.succeed(assert(env.size)(if (label == "") equalTo(n) else equalTo(n) ?? label)))

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

  def makeLayer1(ref: Ref[Vector[String]]): ZLayer[Any, Nothing, Module1] =
    ZLayer {
      ZManaged.make(ref.update(_ :+ acquire1).as(Has(new Module1.Service {})))(_ => ref.update(_ :+ release1))
    }

  type Module2 = Has[Module2.Service]

  object Module2 {
    trait Service
  }

  def makeLayer2(ref: Ref[Vector[String]]): ZLayer[Any, Nothing, Module2] =
    ZLayer {
      ZManaged.make(ref.update(_ :+ acquire2).as(Has(new Module2.Service {})))(_ => ref.update(_ :+ release2))
    }

  type Module3 = Has[Module3.Service]

  object Module3 {
    trait Service
  }

  def makeLayer3(ref: Ref[Vector[String]]): ZLayer[Any, Nothing, Module3] =
    ZLayer {
      ZManaged.make(ref.update(_ :+ acquire3).as(Has(new Module3.Service {})))(_ => ref.update(_ :+ release3))
    }

  def makeRef: UIO[Ref[Vector[String]]] =
    Ref.make(Vector.empty)

  def spec: ZSpec[Environment, Failure] =
    suite("ZLayerSpec")(
      testM("Size of >>> (1)") {
        val layer = ZLayer.succeed(1) >>> ZLayer.fromService((i: Int) => i.toString)

        testSize(layer, 1)
      },
      testM("Size of >>> (2)") {
        val layer = ZLayer.succeed(1) >>>
          (ZLayer.fromService((i: Int) => i.toString) ++
            ZLayer.fromService((i: Int) => i % 2 == 0))

        testSize(layer, 2)
      },
      testM("Size of Test layers") {
        for {
          r1 <- testSize(Annotations.live, 1, "Annotations.live")
          r2 <- testSize(ZEnv.live >>> Live.default >>> TestConsole.debug, 2, "TestConsole.default")
          r3 <- testSize(ZEnv.live >>> Live.default, 1, "Live.default")
          r4 <- testSize(ZEnv.live >>> TestRandom.deterministic, 2, "TestRandom.live")
          r5 <- testSize(Sized.live(100), 1, "Sized.live(100)")
          r6 <- testSize(TestSystem.default, 2, "TestSystem.default")
        } yield r1 && r2 && r3 && r4 && r5 && r6
      },
      testM("Size of >>> (9)") {
        val layer = (ZEnv.live >>>
          (Annotations.live ++ (Live.default >>> TestConsole.debug) ++
            Live.default ++ TestRandom.deterministic ++ Sized.live(100)
            ++ TestSystem.default))

        testSize(layer, 9)
      },
      testM("sharing with ++") {
        val expected = Vector(acquire1, release1)
        for {
          ref    <- makeRef
          layer1  = makeLayer1(ref)
          env     = (layer1 ++ layer1).build
          _      <- env.use_(ZIO.unit)
          actual <- ref.get
        } yield assert(actual)(equalTo(expected))
      } @@ nonFlaky,
      testM("sharing itself with ++") {
        val m1     = new Module1.Service {}
        val layer1 = ZLayer.succeed(m1)
        val env    = layer1 ++ (layer1 ++ layer1)
        env.build.use(m => ZIO(assert(m.get)(equalTo(m1))))
      } @@ nonFlaky,
      testM("sharing with >>>") {
        val expected = Vector(acquire1, release1)
        for {
          ref    <- makeRef
          layer1  = makeLayer1(ref)
          env     = (layer1 >>> layer1).build
          _      <- env.use_(ZIO.unit)
          actual <- ref.get
        } yield assert(actual)(equalTo(expected))
      } @@ nonFlaky,
      testM("sharing with multiple layers") {
        for {
          ref    <- makeRef
          layer1  = makeLayer1(ref)
          layer2  = makeLayer2(ref)
          layer3  = makeLayer3(ref)
          env     = ((layer1 >>> layer2) ++ (layer1 >>> layer3)).build
          _      <- env.use_(ZIO.unit)
          actual <- ref.get
        } yield assert(actual(0))(equalTo(acquire1)) &&
          assert(actual.slice(1, 3))(hasSameElements(Vector(acquire2, acquire3))) &&
          assert(actual.slice(3, 5))(hasSameElements(Vector(release2, release3))) &&
          assert(actual(5))(equalTo(release1))
      } @@ nonFlaky,
      testM("finalizers with ++") {
        for {
          ref    <- makeRef
          layer1  = makeLayer1(ref)
          layer2  = makeLayer2(ref)
          env     = (layer1 ++ layer2).build
          _      <- env.use_(ZIO.unit)
          actual <- ref.get
        } yield assert(actual.slice(0, 2))(hasSameElements(Vector(acquire1, acquire2))) &&
          assert(actual.slice(2, 4))(hasSameElements(Vector(release1, release2)))
      } @@ nonFlaky,
      testM("finalizers with >>>") {
        val expected = Vector(acquire1, acquire2, release2, release1)
        for {
          ref    <- makeRef
          layer1  = makeLayer1(ref)
          layer2  = makeLayer2(ref)
          env     = (layer1 >>> layer2).build
          _      <- env.use_(ZIO.unit)
          actual <- ref.get
        } yield assert(actual)(equalTo(expected))
      } @@ nonFlaky,
      testM("finalizers with multiple layers") {
        val expected =
          Vector(acquire1, acquire2, acquire3, release3, release2, release1)
        for {
          ref    <- makeRef
          layer1  = makeLayer1(ref)
          layer2  = makeLayer2(ref)
          layer3  = makeLayer3(ref)
          env     = (layer1 >>> layer2 >>> layer3).build
          _      <- env.use_(ZIO.unit)
          actual <- ref.get
        } yield assert(actual)(equalTo(expected))
      } @@ nonFlaky,
      testM("map does not interfere with sharing") {
        for {
          ref    <- makeRef
          layer1  = makeLayer1(ref)
          layer2  = makeLayer2(ref)
          layer3  = makeLayer3(ref)
          env     = ((layer1.map(identity) >>> layer2) ++ (layer1 >>> layer3)).build
          _      <- env.use_(ZIO.unit)
          actual <- ref.get
        } yield assert(actual(0))(equalTo(acquire1)) &&
          assert(actual.slice(1, 3))(hasSameElements(Vector(acquire2, acquire3))) &&
          assert(actual.slice(3, 5))(hasSameElements(Vector(release2, release3))) &&
          assert(actual(5))(equalTo(release1))
      } @@ nonFlaky,
      testM("mapError does not interfere with sharing") {
        implicit val canFail = CanFail
        for {
          ref    <- makeRef
          layer1  = makeLayer1(ref)
          layer2  = makeLayer2(ref)
          layer3  = makeLayer3(ref)
          env     = ((layer1.mapError(identity) >>> layer2) ++ (layer1 >>> layer3)).build
          _      <- env.use_(ZIO.unit)
          actual <- ref.get
        } yield assert(actual(0))(equalTo(acquire1)) &&
          assert(actual.slice(1, 3))(hasSameElements(Vector(acquire2, acquire3))) &&
          assert(actual.slice(3, 5))(hasSameElements(Vector(release2, release3))) &&
          assert(actual(5))(equalTo(release1))
      } @@ nonFlaky,
      testM("orDie does not interfere with sharing") {
        implicit val canFail = CanFail
        for {
          ref    <- makeRef
          layer1  = makeLayer1(ref)
          layer2  = makeLayer2(ref)
          layer3  = makeLayer3(ref)
          env     = ((layer1.orDie >>> layer2) ++ (layer1 >>> layer3)).build
          _      <- env.use_(ZIO.unit)
          actual <- ref.get
        } yield assert(actual(0))(equalTo(acquire1)) &&
          assert(actual.slice(1, 3))(hasSameElements(Vector(acquire2, acquire3))) &&
          assert(actual.slice(3, 5))(hasSameElements(Vector(release2, release3))) &&
          assert(actual(5))(equalTo(release1))
      } @@ nonFlaky,
      testM("interruption with ++") {
        for {
          ref    <- makeRef
          layer1  = makeLayer1(ref)
          layer2  = makeLayer2(ref)
          env     = (layer1 ++ layer2).build
          fiber  <- env.use_(ZIO.unit).fork
          _      <- fiber.interrupt
          actual <- ref.get
        } yield (assert(actual)(contains(acquire1)) ==> assert(actual)(contains(release1))) &&
          (assert(actual)(contains(acquire2)) ==> assert(actual)(contains(release2)))
      } @@ zioTag(interruption) @@ nonFlaky,
      testM("interruption with >>>") {
        for {
          ref    <- makeRef
          layer1  = makeLayer1(ref)
          layer2  = makeLayer2(ref)
          env     = (layer1 >>> layer2).build
          fiber  <- env.use_(ZIO.unit).fork
          _      <- fiber.interrupt
          actual <- ref.get
        } yield (assert(actual)(contains(acquire1)) ==> assert(actual)(contains(release1))) &&
          (assert(actual)(contains(acquire2)) ==> assert(actual)(contains(release2)))
      } @@ zioTag(interruption) @@ nonFlaky,
      testM("interruption with multiple layers") {
        for {
          ref    <- makeRef
          layer1  = makeLayer1(ref)
          layer2  = makeLayer2(ref)
          layer3  = makeLayer3(ref)
          env     = ((layer1 >>> layer2) ++ (layer1 >>> layer3)).build
          fiber  <- env.use_(ZIO.unit).fork
          _      <- fiber.interrupt
          actual <- ref.get
        } yield (assert(actual)(contains(acquire1)) ==> assert(actual)(contains(release1))) &&
          (assert(actual)(contains(acquire2)) ==> assert(actual)(contains(release2))) &&
          (assert(actual)(contains(acquire3)) ==> assert(actual)(contains(release3)))
      } @@ zioTag(interruption) @@ nonFlaky,
      testM("layers can be acquired in parallel") {
        for {
          promise <- Promise.make[Nothing, Unit]
          layer1   = ZLayer.fromManagedMany(ZManaged.never)
          layer2   = ZLayer.fromManagedMany(Managed.make(promise.succeed(()).map(Has(_)))(_ => ZIO.unit))
          env      = (layer1 ++ layer2).build
          _       <- env.use_(ZIO.unit).forkDaemon
          _       <- promise.await
        } yield assertCompletes
      },
      testM("map can map the output of a layer to an unrelated type") {
        case class A(name: String, value: Int)
        case class B(name: String)
        val l1: Layer[Nothing, Has[A]]               = ZLayer.succeed(A("name", 1))
        val l2: ZLayer[Has[String], Nothing, Has[B]] = ZLayer.fromService(B.apply)
        val live: Layer[Nothing, Has[B]]             = l1.map(a => Has(a.get[A].name)) >>> l2
        assertM(ZIO.access[Has[B]](_.get).provideLayerManual(live))(equalTo(B("name")))
      },
      testM("memoization") {
        val expected = Vector(acquire1, release1)
        for {
          ref     <- makeRef
          memoized = makeLayer1(ref).memoize
          _ <- memoized.use { layer =>
                 for {
                   _ <- ZIO.environment[Module1].provideLayerManual(layer)
                   _ <- ZIO.environment[Module1].provideLayerManual(layer)
                 } yield ()
               }
          actual <- ref.get
        } yield assert(actual)(equalTo(expected))
      } @@ nonFlaky,
      testM("orElse") {
        for {
          ref    <- makeRef
          layer1  = makeLayer1(ref)
          layer2  = makeLayer2(ref)
          env     = ((layer1 >>> ZLayer.fail("fail")) orElse layer2).build
          fiber  <- env.use_(ZIO.unit).fork
          _      <- fiber.interrupt
          actual <- ref.get
        } yield (assert(actual)(contains(acquire1)) ==> assert(actual)(contains(release1))) &&
          (assert(actual)(contains(acquire2)) ==> assert(actual)(contains(release2)))
      } @@ nonFlaky,
      testM("passthrough") {
        val layer: ZLayer[Has[Int], Nothing, Has[String]] =
          ZLayer.fromService(_.toString)
        val live: ZLayer[Any, Nothing, Has[Int] with Has[String]] =
          ZLayer.succeed(1) >>> layer.passthrough
        val zio = for {
          i <- ZIO.environment[Has[Int]].map(_.get[Int])
          s <- ZIO.environment[Has[String]].map(_.get[String])
        } yield (i, s)
        assertM(zio.provideLayerManual(live))(equalTo((1, "1")))
      },
      testM("fresh with ++") {
        val expected = Vector(acquire1, acquire1, release1, release1)
        for {
          ref    <- makeRef
          layer1  = makeLayer1(ref)
          env     = (layer1 ++ layer1.fresh).build
          _      <- env.useNow
          result <- ref.get
        } yield assert(result)(equalTo(expected))
      } @@ nonFlaky,
      testM("fresh with >>>") {
        val expected = Vector(acquire1, acquire1, release1, release1)
        for {
          ref    <- makeRef
          layer1  = makeLayer1(ref)
          env     = (layer1 >>> layer1.fresh).build
          _      <- env.useNow
          result <- ref.get
        } yield assert(result)(equalTo(expected))
      } @@ nonFlaky,
      testM("fresh with multiple layers") {
        val expected = Vector(acquire1, acquire1, release1, release1)
        for {
          ref    <- makeRef
          layer1  = makeLayer1(ref)
          env     = ((layer1 ++ layer1) ++ (layer1 ++ layer1).fresh).build
          _      <- env.useNow
          result <- ref.get
        } yield assert(result)(equalTo(expected))
      } @@ nonFlaky,
      testM("fresh with identical fresh layers") {
        for {
          ref    <- makeRef
          layer1  = makeLayer1(ref)
          layer2  = makeLayer2(ref)
          layer3  = makeLayer3(ref)
          env     = ((layer1.fresh >>> layer2) ++ (layer1.fresh >>> layer3)).build
          _      <- env.useNow
          result <- ref.get
        } yield assert(result)(hasSize(equalTo(8)))
      } @@ nonFlaky,
      testM("preserves identity of acquired resources") {
        for {
          testRef <- Ref.make(Vector[String]())
          layer = ZLayer.fromManagedMany(
                    for {
                      ref <- Ref.make[Vector[String]](Vector()).toManaged(ref => ref.get.flatMap(testRef.set))
                      _   <- ZManaged.unit
                    } yield ref
                  )
          _      <- layer.build.use(ref => ref.update(_ :+ "test"))
          result <- testRef.get
        } yield assert(result)(equalTo(Vector("test")))
      },
      testM("retry") {
        for {
          ref    <- Ref.make(0)
          effect  = ref.update(_ + 1) *> ZIO.fail("fail")
          layer   = ZLayer.fromEffectMany(effect).retry(Schedule.recurs(3))
          _      <- layer.build.useNow.ignore
          result <- ref.get
        } yield assert(result)(equalTo(4))
      },
      testM("error handling") {
        val sleep  = ZIO.sleep(100.milliseconds).provideLayerManual(Clock.live)
        val layer1 = ZLayer.fail("foo")
        val layer2 = ZLayer.succeed("bar")
        val layer3 = ZLayer.succeed("baz")
        val layer4 = ZLayer.fromAcquireRelease(sleep)(_ => sleep)
        val env    = layer1 ++ ((layer2 ++ layer3) >+> layer4)
        assertM(ZIO.unit.provideCustomLayerManual(env).run)(fails(equalTo("foo")))
      },
      testM("project") {
        final case class Person(name: String, age: Int)
        val personLayer = ZLayer.succeed(Person("User", 42))
        val ageLayer    = personLayer.project(_.age)
        assertM(ZIO.service[Int].provideLayerManual(ageLayer))(equalTo(42))
      },
      testM("tap") {
        for {
          ref   <- Ref.make("foo")
          layer  = ZLayer.succeed("bar").tap(r => ref.set(r.get))
          _     <- layer.build.useNow
          value <- ref.get
        } yield assert(value)(equalTo("bar"))
      }
    )
}
