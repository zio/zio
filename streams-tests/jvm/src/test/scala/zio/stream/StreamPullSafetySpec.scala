package zio.stream

import zio._
import zio.test._
import zio.test.Assertion.{ equalTo, isFalse, isTrue }
import ZStream.Pull
import StreamUtils.threePulls

object StreamPullSafetySpec
    extends ZIOBaseSpec(
      suite("StreamPullSafetySpec")(
        testM("Stream.empty is safe to pull again") {
          Stream.empty.process
            .use(threePulls(_))
            .map(assert(_, equalTo(List(Left(None), Left(None), Left(None)))))
        },
        suite("Stream.effectAsync")(
          testM("is safe to pull again after error") {
            Stream
              .effectAsync[String, Int] { k =>
                List(1, 2, 3).foreach { n =>
                  k(if (n % 2 == 0) Pull.fail("Ouch") else Pull.emit(n))
                }
              }
              .process
              .use(threePulls(_))
              .map(assert(_, equalTo(List(Right(1), Left(Some("Ouch")), Right(3)))))
          },
          testM("is safe to pull again after end") {
            Stream
              .effectAsync[String, Int] { k =>
                k(Pull.emit(1))
                k(Pull.end)
              }
              .process
              .use(threePulls(_))
              .map(assert(_, equalTo(List(Right(1), Left(None), Left(None)))))
          }
        ),
        suite("Stream.effectAsyncM")(
          testM("is safe to pull again after error") {
            Stream
              .effectAsyncM[String, Int] { k =>
                List(1, 2, 3).foreach { n =>
                  k(if (n % 2 == 0) Pull.fail("Ouch") else Pull.emit(n))
                }
                UIO.unit
              }
              .process
              .use(threePulls(_))
              .map(assert(_, equalTo(List(Right(1), Left(Some("Ouch")), Right(3)))))
          },
          testM("is safe to pull again after end") {
            Stream
              .effectAsyncM[String, Int] { k =>
                k(Pull.emit(1))
                k(Pull.end)
                UIO.unit
              }
              .process
              .use(threePulls(_))
              .map(assert(_, equalTo(List(Right(1), Left(None), Left(None)))))
          }
        ),
        suite("Stream.effectAsyncMaybe")(
          testM("is safe to pull again after error async case") {
            Stream
              .effectAsyncMaybe[String, Int] { k =>
                List(1, 2, 3).foreach { n =>
                  k(if (n % 2 == 0) Pull.fail("Ouch") else Pull.emit(n))
                }
                None
              }
              .process
              .use(threePulls(_))
              .map(assert(_, equalTo(List(Right(1), Left(Some("Ouch")), Right(3)))))
          },
          testM("is safe to pull again after error sync case") {
            Stream
              .effectAsyncMaybe[String, Int] { k =>
                k(Pull.fail("Ouch async"))
                Some(Stream.fail("Ouch sync"))
              }
              .process
              .use(threePulls(_))
              .map(assert(_, equalTo(List(Left(Some("Ouch sync")), Left(None), Left(None)))))
          },
          testM("is safe to pull again after end async case") {
            Stream
              .effectAsyncMaybe[String, Int] { k =>
                k(Pull.emit(1))
                k(Pull.end)
                None
              }
              .process
              .use(threePulls(_))
              .map(assert(_, equalTo(List(Right(1), Left(None), Left(None)))))
          },
          testM("is safe to pull again after end sync case") {
            Stream
              .effectAsyncMaybe[String, Int] { k =>
                k(Pull.fail("Ouch async"))
                Some(Stream.empty)
              }
              .process
              .use(threePulls(_))
              .map(assert(_, equalTo(List(Left(None), Left(None), Left(None)))))
          }
        ),
        suite("Stream.effectAsyncInterrupt")(
          testM("is safe to pull again after error async case") {
            for {
              ref <- Ref.make(false)
              pulls <- Stream
                        .effectAsyncInterrupt[String, Int] { k =>
                          List(1, 2, 3).foreach { n =>
                            k(if (n % 2 == 0) Pull.fail("Ouch") else Pull.emit(n))
                          }
                          Left(ref.set(true))
                        }
                        .process
                        .use(threePulls(_))
              fin <- ref.get
            } yield assert(fin, isTrue) && assert(pulls, equalTo(List(Right(1), Left(Some("Ouch")), Right(3))))
          },
          testM("is safe to pull again after error sync case") {
            Stream
              .effectAsyncInterrupt[String, Int] { k =>
                k(IO.fail(Some("Ouch async")))
                Right(Stream.fail("Ouch sync"))
              }
              .process
              .use(threePulls(_))
              .map(assert(_, equalTo(List(Left(Some("Ouch sync")), Left(None), Left(None)))))
          },
          testM("is safe to pull again after end async case") {
            for {
              ref <- Ref.make(false)
              pulls <- Stream
                        .effectAsyncInterrupt[String, Int] { k =>
                          k(Pull.emit(1))
                          k(Pull.end)
                          Left(ref.set(true))
                        }
                        .process
                        .use(threePulls(_))
              fin <- ref.get
            } yield assert(fin, isTrue) && assert(pulls, equalTo(List(Right(1), Left(None), Left(None))))
          },
          testM("is safe to pull again after end sync case") {
            Stream
              .effectAsyncInterrupt[String, Int] { k =>
                k(IO.fail(Some("Ouch async")))
                Right(Stream.empty)
              }
              .process
              .use(threePulls(_))
              .map(assert(_, equalTo(List(Left(None), Left(None), Left(None)))))
          }
        ),
        testM("Stream.fail is safe to pull again") {
          Stream
            .fail("Ouch")
            .process
            .use(threePulls(_))
            .map(assert(_, equalTo(List(Left(Some("Ouch")), Left(None), Left(None)))))
        },
        suite("Stream.managed")(
          testM("is safe to pull again after success") {
            for {
              ref   <- Ref.make(false)
              pulls <- Stream.managed(Managed.make(UIO.succeed(5))(_ => ref.set(true))).process.use(threePulls(_))
              fin   <- ref.get
            } yield assert(fin, isTrue) && assert(pulls, equalTo(List(Right(5), Left(None), Left(None))))
          },
          testM("is safe to pull again after failed acquisition") {
            for {
              ref   <- Ref.make(false)
              pulls <- Stream.managed(Managed.make(IO.fail("Ouch"))(_ => ref.set(true))).process.use(threePulls(_))
              fin   <- ref.get
            } yield assert(fin, isFalse) && assert(pulls, equalTo(List(Left(Some("Ouch")), Left(None), Left(None))))
          },
          testM("is safe to pull again after inner failure") {
            for {
              ref <- Ref.make(false)
              pulls <- Stream
                        .managed(Managed.make(UIO.succeed(5))(_ => ref.set(true)))
                        .flatMap(_ => Stream.fail("Ouch"))
                        .process
                        .use(threePulls(_))
              fin <- ref.get
            } yield assert(fin, isTrue) && assert(pulls, equalTo(List(Left(Some("Ouch")), Left(None), Left(None))))
          }
        )
      )
    )
