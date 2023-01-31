package zio.test

import zio._
import zio.stream._
import zio.test.Assertion._

object StreamSpec extends ZIOBaseSpec {

  def spec =
    suite("StreamSpec")(
      suite("flatMapStream")(
        test("implements breadth first search") {
          val expected = List(
            Map(
              (0, 0) -> 1
            ),
            Map(
              (0, 1) -> 1,
              (1, 0) -> 1
            ),
            Map(
              (0, 2) -> 1,
              (1, 1) -> 1,
              (2, 0) -> 1
            ),
            Map(
              (0, 3) -> 1,
              (1, 2) -> 1,
              (2, 1) -> 1,
              (3, 0) -> 1
            )
          )
//          val stream = flatMapStreamOrig(nats)(a => flatMapStreamOrig(nats)(b => ZStream.succeed(Some((a, b)))))
          val stream = flatMapStream(nats)(a => flatMapStream(nats)(b => ZStream.succeed(Some((a, b)))))
          for {
            actual <- runCollectUnordered(100)(stream)
          } yield assert(actual.take(4))(equalTo(expected))
        },
        test("is consistent with ZStream#flatMap") {
          check(genStream, genIntStreamFunction) { (stream, f) =>
            val left  = flatMapStream(stream.filter(_.isDefined))(a => f(a).filter(_.isDefined))
            val right = stream.collectSome.flatMap(a => f(a).collectSome).map(Some(_))
            assertEqualStream(left, right)
          }
        },
        suite("laws")(
          test("left identity") {
            check(genStream) { stream =>
              val left  = flatMapStream(stream)(a => ZStream(Some(a)))
              val right = stream
              assertEqualStream(left, right)
            }
          },
          test("right identity") {
            check(Gen.int, Gen.listOf(Gen.option(Gen.int))) { (a, lst) =>
              val f     = (_: Int) => ZStream.fromIterable(lst)
              val left  = flatMapStream(ZStream(Some(a)))(f)
              val right = f(a)
              assertEqualStream(left, right)
            }
          },
          test("associativity") {
            check(Gen.int, genIntStreamFunction, genIntStreamFunction) { (a, f, g) =>
              val left  = flatMapStream(flatMapStream(ZStream(Some(a)))(f))(g)
              val right = flatMapStream(ZStream(Some(a)))(a => flatMapStream(f(a))(g))
              assertEqualStream(left, right)
            }
          }
        ),
        suite("resource safety")(
          test("releases inner streams when they are done") {
            val expected = List(
              "outer acquire",
              "inner1 acquire",
              "inner2 acquire",
              "inner2 release",
              "inner1 release",
              "outer release"
            )
            for {
              ref <- Ref.make[List[String]](List.empty)
              resource = (label: String) =>
                           ZStream.acquireReleaseWith(
                             ref.update(s"$label acquire" :: _)
                           )(_ => ref.update(s"$label release" :: _))
              stream = resource("outer") *>
                         ZStream(
                           Some(resource("inner1") *> ZStream(Some(1), None, Some(2))),
                           Some(resource("inner2") *> ZStream(Some(3)))
                         )
              _      <- flatMapStream(stream)(identity).runDrain
              actual <- ref.get.map(_.reverse)
            } yield assert(actual)(equalTo(expected))
          },
          test("releases inner streams when interrupted") {
            val expected = List(
              "outer acquire",
              "inner1 acquire",
              "inner1 release",
              "outer release"
            )
            for {
              promise <- Promise.make[Nothing, Unit]
              ref     <- Ref.make[List[String]](List.empty)
              resource = (label: String) =>
                           ZStream.acquireReleaseWith(
                             ref.update(s"$label acquire" :: _)
                           )(_ => ref.update(s"$label release" :: _))
              stream = resource("outer") *>
                         ZStream(
                           Some(resource("inner1") *> ZStream(Some(1), None, Some(2))),
                           Some(ZStream.fromZIO(promise.succeed(()).asSome)),
                           Some(ZStream.never),
                           Some(resource("inner2") *> ZStream(Some(3)))
                         )
              fiber  <- flatMapStream(stream)(identity).runDrain.fork
              _      <- promise.await
              _      <- fiber.interrupt
              actual <- ref.get.map(_.reverse)
            } yield assert(actual)(equalTo(expected))
          }
        )
      )
    )

  def collectUnordered[A](as: Iterable[Option[A]]): List[Map[A, Int]] =
    as
      .foldLeft(::(Map.empty[A, Int], Nil)) {
        case (list, None) =>
          ::(Map.empty[A, Int], list)
        case (head :: tail, Some(a)) =>
          head.get(a) match {
            case None    => ::(head.updated(a, 1), tail)
            case Some(n) => ::(head.updated(a, (n + 1)), tail)
          }
      }
      .tail
      .reverse

  def assertEqualStream[R, E, A](
    left: ZStream[R, E, Option[A]],
    right: ZStream[R, E, Option[A]]
  ): ZIO[R, E, TestResult] =
    for {
      actual   <- runCollectUnordered(100)(left)
      expected <- runCollectUnordered(100)(right)
    } yield assert(actual)(equalTo(expected))

  lazy val genIntStreamFunction: Gen[Any, Int => ZStream[Any, Nothing, Option[Int]]] =
    Gen.function(genStream)

  lazy val genStream: Gen[Any, ZStream[Any, Nothing, Option[Int]]] =
    for {
      as <- Gen.listOf(Gen.option(Gen.int))
      n  <- Gen.small(n => Gen.int(1, n), 1)
    } yield ZStream.fromIterable(as).rechunk(n)

  lazy val nats: ZStream[Any, Nothing, Option[Int]] =
    ZStream.iterate(0)(_ + 1).map(Some(_)).intersperse(None)

  def runCollectUnordered[R, E, A](n: Int)(stream: ZStream[R, E, Option[A]]): ZIO[R, E, List[Map[A, Int]]] =
    stream.take(n.toLong).runCollect.map(collectUnordered)
}
