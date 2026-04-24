package zio.stream

import zio._
import zio.metrics.MetricLabel
import zio.test.Assertion._
import zio.test._

object ZStreamAspectSpec extends ZIOBaseSpec {

  def spec =
    suite("ZStreamAspectSpec")(
      suite("annotated")(
        test("annotates logs") {
          for {
            annotations <- (ZStream.logAnnotations @@ ZStreamAspect.annotated("key", "value")).runHead
          } yield assert(annotations)(isSome(hasKey("key", equalTo("value"))))
        }
      ),
      suite("rechunk")(
        test("rechunks to size") {
          val stream = ZStream.range(0, 10, 9)
          for {
            chunks <- (stream @@ ZStreamAspect.rechunk(3)).mapChunks(c => Chunk(c)).runCollect
          } yield assertTrue(
            chunks == Chunk(Chunk(0, 1, 2), Chunk(3, 4, 5), Chunk(6, 7, 8), Chunk(9))
          )
        },
        test("handles empty stream") {
          val stream = ZStream.empty
          for {
            result <- (stream @@ ZStreamAspect.rechunk(5)).runCollect
          } yield assertTrue(result.isEmpty)
        }
      ),
      suite("tagged")(
        test("tags metrics with a single key-value pair") {
          for {
            tags <- (ZStream.fromZIO(FiberRef.currentTags.get) @@ ZStreamAspect.tagged("service", "api")).runHead
          } yield assertTrue(tags.is(_.some) == Set(MetricLabel("service", "api")))
        },
        test("tags metrics with multiple key-value pairs") {
          for {
            tags <-
              (ZStream
                .fromZIO(FiberRef.currentTags.get) @@ ZStreamAspect
                .tagged(("service", "api"), ("version", "v1"))).runHead
          } yield assertTrue(tags.is(_.some) == Set(MetricLabel("service", "api"), MetricLabel("version", "v1")))
        },
        test("tags are scoped to the stream") {
          val serviceTag = MetricLabel("scoped-service", "test")
          for {
            tagsBefore <- FiberRef.currentTags.get
            result <-
              (ZStream.fromZIO(FiberRef.currentTags.get) @@ ZStreamAspect.tagged("scoped-service", "test")).runHead
            tagsAfter <- FiberRef.currentTags.get
          } yield assertTrue(
            result.is(_.some) == Set(serviceTag),
            tagsBefore.isEmpty,
            tagsAfter.isEmpty
          )
        },
        test("multiple tags accumulate") {
          val aspect = ZStreamAspect.tagged("first", "1") @@ ZStreamAspect.tagged("second", "2")
          for {
            tags <- (ZStream.fromZIO(FiberRef.currentTags.get).take(1) @@ aspect).runHead
          } yield assertTrue(
            tags.isDefined,
            tags.get.contains(MetricLabel("first", "1")),
            tags.get.contains(MetricLabel("second", "2"))
          )
        }
      ),
      suite("operators")(
        test(">>> applies aspects in order") {
          for {
            order <- withAspects("first", "second") { (head, tail) =>
                       tail.foldLeft(head)(_ >>> _)
                     }
          } yield assertTrue(order == Chunk("start-second", "start-first", "end-first", "end-second"))
        },
        test("@@ applies aspects in order") {
          for {
            order <- withAspects("first", "second") { (head, tail) =>
                       tail.foldLeft(head)(_ @@ _)
                     }
          } yield assertTrue(order == Chunk("start-second", "start-first", "end-first", "end-second"))
        },
        test("andThen applies aspects in order") {
          for {
            order <- withAspects("first", "second") { (head, tail) =>
                       tail.foldLeft(head)(_.andThen(_))
                     }
          } yield assertTrue(order == Chunk("start-second", "start-first", "end-first", "end-second"))
        },
        test("three-way >>> composition applies in order") {
          for {
            order <- withAspects("first", "second", "third") { (head, tail) =>
                       tail.foldLeft(head)(_ >>> _)
                     }
          } yield assertTrue(
            order == Chunk(
              "start-third",
              "start-second",
              "start-first",
              "end-first",
              "end-second",
              "end-third"
            )
          )
        }
      )
    )
  private type Aspect = ZStreamAspect[Nothing, Any, Nothing, Any, Nothing, Any]

  private def trackingAspect(ref: Ref[Chunk[String]], label: String): Aspect =
    new ZStreamAspect[Nothing, Any, Nothing, Any, Nothing, Any] {
      def apply[R, E, A](stream: ZStream[R, E, A])(implicit trace: Trace): ZStream[R, E, A] =
        ZStream.scoped(
          ZIO.acquireRelease(ref.update(_ :+ s"start-$label"))(_ => ref.update(_ :+ s"end-$label"))
        ) *> stream
    }

  private def withAspects(labels: String*)(
    compose: (Aspect, Seq[Aspect]) => Aspect,
    n: Int = 3
  ): UIO[Chunk[String]] =
    for {
      ref     <- Ref.make(Chunk.empty[String])
      aspects  = labels.map(trackingAspect(ref, _))
      composed = compose(aspects.head, aspects.tail)
      _       <- (ZStream.range(0, n) @@ composed).runDrain
      order   <- ref.get
    } yield order
}
