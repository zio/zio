package zio.autowire

import zio._
import zio.internal.macros.StringUtils.StringOps
import zio.test.Assertion._
import zio.test._

object AutoWireSpec extends ZIOBaseSpec {

  def containsStringWithoutAnsi(element: String): Assertion[String] =
    Assertion.assertion("containsStringWithoutAnsi")(_.unstyled.contains(element))

  def spec =
    suite("AutoWireSpec")(
      suite("ZIO")(
        suite("`zio.provide`")(
          test("automatically constructs a layer from its dependencies") {
            val doubleLayer: ULayer[Double] = ZLayer.succeed(100.1)
            val stringLayer                 = ZLayer.succeed("this string is 28 chars long")
            val intLayer =
              ZLayer {
                for {
                  str    <- ZIO.service[String]
                  double <- ZIO.service[Double]
                } yield str.length + double.toInt
              }

            val program: URIO[Int, Int] = ZIO.service[Int]
            val injected: ZIO[Any, Nothing, Int] =
              program.provide(intLayer, stringLayer, doubleLayer)

            injected.map(result => assertTrue(result == 128))
          },
          test("automatically memoizes non-val layers") {
            def sideEffectingLayer(ref: Ref[Int]): ZLayer[Any, Nothing, String] =
              ZLayer(ref.update(_ + 1).as("Howdy"))

            val layerA: URLayer[String, Int]     = ZLayer.succeed(1)
            val layerB: URLayer[String, Boolean] = ZLayer.succeed(true)

            for {
              ref <- Ref.make(0)
              _ <- (ZIO.service[Int] <*> ZIO.service[Boolean])
                     .provide(layerA, layerB, sideEffectingLayer(ref))
              result <- ref.get
            } yield assertTrue(result == 1)
          },
          test("reports duplicate layers") {
            val checked =
              typeCheck("ZIO.service[Int].provide(ZLayer.succeed(12), ZLayer.succeed(13))")
            assertZIO(checked)(
              isLeft(
                containsStringWithoutAnsi("Ambiguous layers!") &&
                  containsStringWithoutAnsi("ZLayer.succeed(12)") &&
                  containsStringWithoutAnsi("ZLayer.succeed(13)")
              )
            )
          } @@ TestAspect.exceptScala3,
          test("reports missing top-level layers") {
            val program: URIO[String with Int, String] = ZIO.succeed("test")
            val _                                      = program

            val checked = typeCheck("program.provide(ZLayer.succeed(3))")
            assertZIO(checked)(isLeft(containsStringWithoutAnsi("String")))
          } @@ TestAspect.exceptScala3,
          test("reports multiple missing top-level layers") {
            val program: URIO[String with Int, String] = ZIO.succeed("test")
            val _                                      = program

            val checked = typeCheck("program.provide()")
            assertZIO(checked)(
              isLeft(containsStringWithoutAnsi("String") && containsStringWithoutAnsi("Int"))
            )
          } @@ TestAspect.exceptScala3,
          test("reports missing transitive dependencies") {
            import TestLayer._
            val program: URIO[OldLady, Boolean] = ZIO.service[OldLady].flatMap(_.willDie)
            val _                               = program

            val checked = typeCheck("program.provide(OldLady.live)")
            assertZIO(checked)(
              isLeft(
                containsStringWithoutAnsi("zio.autowire.AutoWireSpec.TestLayer.Fly") &&
                  containsStringWithoutAnsi("Required by TestLayer.OldLady.live")
              )
            )
          } @@ TestAspect.exceptScala3,
          test("reports nested missing transitive dependencies") {
            import TestLayer._
            val program: URIO[OldLady, Boolean] = ZIO.service[OldLady].flatMap(_.willDie)
            val _                               = program

            val checked = typeCheck("program.provide(OldLady.live, Fly.live)")
            assertZIO(checked)(
              isLeft(
                containsStringWithoutAnsi("zio.autowire.AutoWireSpec.TestLayer.Spider") &&
                  containsStringWithoutAnsi("Required by TestLayer.Fly.live")
              )
            )
          } @@ TestAspect.exceptScala3,
          test("reports circular dependencies") {
            import TestLayer._
            val program: URIO[OldLady, Boolean] = ZIO.service[OldLady].flatMap(_.willDie)
            val _                               = program

            val checked = typeCheck("program.provide(OldLady.live, Fly.manEatingFly)")
            assertZIO(checked)(
              isLeft(
                containsStringWithoutAnsi("TestLayer.Fly.manEatingFly") &&
                  containsStringWithoutAnsi("OldLady.live") &&
                  containsStringWithoutAnsi(
                    "A layer simultaneously requires and is required by another"
                  )
              )
            )
          } @@ TestAspect.exceptScala3
        ),
        suite("`ZLayer.make`")(
          test("automatically constructs a layer") {
            val doubleLayer = ZLayer.succeed(100.1)
            val stringLayer: ULayer[String] =
              ZLayer.succeed("this string is 28 chars long")
            val intLayer = ZLayer {
              (ZIO.service[String] <*> ZIO.service[Double]).map { case (str, double) =>
                str.length + double.toInt
              }
            }

            val layer =
              ZLayer.make[Int](intLayer, stringLayer, doubleLayer)
            val provided = ZIO.service[Int].provideLayer(layer)
            assertZIO(provided)(equalTo(128))
          },
          test("building fresh layer only once") {
            var timesBuilt = 0

            trait Service1
            object Service1 {
              val live: ZLayer[Any, Throwable, Service1] =
                ZLayer.fromZIO(ZIO.attempt(timesBuilt += 1).as(new Service1 {}))
            }

            trait Service2
            object Service2 {
              val live: ZLayer[Service1, Nothing, Service2] =
                ZLayer.fromZIO(ZIO.unit.as(new Service2 {}))
            }

            assertZIO(
              ZLayer.make[Service1 & Service2](Service1.live.fresh, Service2.live).build *> ZIO.succeed(timesBuilt)
            )(equalTo(1))
          },
          test("correctly decomposes nested, aliased intersection types") {
            type StringAlias           = String
            type HasBooleanDoubleAlias = Boolean with Double
            type And2[A, B]            = A with B
            type FinalAlias            = And2[Int, StringAlias] with HasBooleanDoubleAlias
            val _ = ZIO.environment[FinalAlias]

            val checked = typeCheck("ZLayer.make[FinalAlias]()")
            assertZIO(checked)(
              isLeft(
                containsStringWithoutAnsi("Int") &&
                  containsStringWithoutAnsi("String") &&
                  containsStringWithoutAnsi("Boolean") &&
                  containsStringWithoutAnsi("Double")
              )
            )
          } @@ TestAspect.exceptScala3,
          test("works correctly with function parameters") {
            case class MyLayer()

            def createLayerByFunction(x: () => MyLayer) = ZLayer.succeed(x())

            val byName =
              ZIO
                .service[MyLayer]
                .provide(
                  createLayerByFunction { () =>
                    val byNameLayer = MyLayer()
                    byNameLayer
                  }
                )
            assertTrue(byName != null)
          },
          test("return error when passing by-name on Scala 2 (https://github.com/zio/zio/issues/7732)") {
            assertZIO(typeCheck {
              """
                case class MyLayer()

                def createLayerByName(x: => MyLayer) = ZLayer.succeed(x)

                val byName =
                  ZIO
                    .service[MyLayer]
                    .provide(
                      createLayerByName {
                        val byNameLayer = MyLayer()
                        byNameLayer
                      }
                    )
              """
            })(isLeft(anything))
          } @@ TestAspect.exceptScala3
        ),
        suite("`ZLayer.makeSome`")(
          test("automatically constructs a layer, leaving off some remainder") {
            val stringLayer = ZLayer.succeed("this string is 28 chars long")
            val intLayer = ZLayer {
              (ZIO.service[String] <*> ZIO.service[Double]).map { case (str, double) =>
                str.length + double.toInt
              }
            }
            val program = ZIO.service[Int]

            val layer =
              ZLayer.makeSome[Double, Int](intLayer, stringLayer)
            val provided =
              program.provideLayer(
                ZLayer.succeed(true) ++ ZLayer.succeed(100.1) >>> layer
              )
            assertZIO(provided)(equalTo(128))
          },
          test("makeSome 1 layer") {
            val test1 = typeCheck {
              """def test1[A, B](a: ZLayer[A, Nothing, B]): ZLayer[A, Nothing, B] =
              ZLayer.makeSome[A, B](a)"""
            }

            val test2 = typeCheck {
              """def test2[A](a: ZLayer[A, Nothing, Int]): ZLayer[A, Nothing, A & Int] =
              ZLayer.makeSome[A, A & Int](a)"""
            }

            assertZIO(test1)(isRight(anything)) &&
            assertZIO(test2)(isRight(anything))
          },
          test("makeSome 2 simple layers") {

            def test1[I1, O1, I2](
              a: ZLayer[I1, Nothing, O1],
              b: ZLayer[I2, Nothing, Int]
            ): ZLayer[I1 & I2, Nothing, O1 & Int] =
              ZLayer.makeSome[I1 & I2, O1 & Int](a, b)

            def test2[I1, O1](a: ZLayer[I1, Nothing, O1], b: ZLayer[O1, Nothing, Int]): ZLayer[I1, Nothing, O1 & Int] =
              ZLayer.makeSome[I1, O1 & Int](a, b)

            def test3[I1, O1](a: ZLayer[I1, Nothing, O1], b: ZLayer[I1, Nothing, Int]): ZLayer[I1, Nothing, O1 & Int] =
              ZLayer.makeSome[I1, O1 & Int](a, b)

            val t1 = test1 _
            val t2 = test2 _
            val t3 = test3 _

            assertTrue(dummy(t1, t2, t3))

          },
          test("makeSome 2 complex layers") {

            def test1[R, R1](a: ZLayer[R1 & Int, Nothing, R], b: ZLayer[Int, Nothing, R1]): ZLayer[Int, Nothing, R] =
              ZLayer.makeSome[Int, R](a, b)

            def test2[I1, I3, O1, O2](
              a: ZLayer[I1 & Double, Nothing, O1 & O2],
              b: ZLayer[I3 & Float, Nothing, Int]
            ): ZLayer[I1 & Double & I3 & Float, Nothing, O1 & O2 & Int] =
              ZLayer.makeSome[I1 & Double & I3 & Float, O1 & O2 & Int](a, b)

            def test3[I1, I4, O1, O2](
              a: ZLayer[I1 & Double, Nothing, O1 & O2],
              b: ZLayer[I1 & Float, Nothing, Int]
            ): ZLayer[I1 & Double & Float, Nothing, O1 & O2 & Int] =
              ZLayer.makeSome[I1 & Double & Float, O1 & O2 & Int](a, b)

            def test4[I1, O1](
              a: ZLayer[I1 & Double, Nothing, O1 & String],
              b: ZLayer[O1 & Float, Nothing, Int]
            ): ZLayer[I1 & Double & Float, Nothing, O1 & String & Int] =
              ZLayer.makeSome[I1 & Double & Float, O1 & String & Int](a, b)

            val t1 = test1 _
            val t2 = test2 _
            val t3 = test3 _
            val t4 = test4 _

            assertTrue(dummy(t1, t2, t3, t4))
          },
          test("makeSome complex layer and providing") {

            def test1[R, R1](a: ZLayer[R1 & Int, Nothing, R], b: ZLayer[Int, Nothing, R1]): ZLayer[Int, Nothing, R] =
              ZLayer.makeSome[Int, R](a, b)

            val la = ZLayer {
              (ZIO.service[String] <*> ZIO.service[Int]).map { case (str, int) =>
                (str.length + int).toLong
              }
            }
            val lb = ZLayer(ZIO.service[Int].map(n => n.toString))

            val program =
              ZIO.service[Long].provideLayer(ZLayer.succeed(8) >>> test1(la, lb))

            assertZIO(program)(equalTo(9.toLong))
          }
        )
      )
    )

  def dummy(f: Any*): Boolean = {
    var l: List[Any] = f.toList
    l = Nil
    l.isEmpty
  }

  object TestLayer {
    trait OldLady {
      def willDie: UIO[Boolean]
    }

    object OldLady {
      def live: URLayer[Fly, OldLady] = ZLayer.succeed(new OldLady {
        override def willDie: UIO[Boolean] = ZIO.succeed(false)
      })
    }

    trait Fly {}
    object Fly {
      def live: URLayer[Spider, Fly]          = ZLayer.succeed(new Fly {})
      def manEatingFly: URLayer[OldLady, Fly] = ZLayer.succeed(new Fly {})
    }

    trait Spider {}
    object Spider {
      def live: ULayer[Spider] = ZLayer.succeed(new Spider {})
    }
  }
}
