package zio.stream.experimental

import zio._
import zio.stream.experimental.ZStreamUtils.nPulls
import zio.test.Assertion.{ equalTo, isFalse, isTrue }
import zio.test._

object ZStreamSpec extends ZIOBaseSpec {
  def assertLazy(f: (=> Nothing) => Any): UIO[TestResult] =
    UIO.effectTotal {
      val _ = f(throw new RuntimeException("not lazy"))
      assertCompletes
    }

  def spec = suite("ZStreamSpec")(
    suite("Combinators")(
      testM("map") {
        ZStream
          .fromEffect(UIO.succeed(1))
          .map(_.toString)
          .process
          .use(nPulls(_, 3))
          .map(assert(_)(equalTo(List(Right("1"), Left(Right(())), Left(Right(()))))))
      },
      testM("filter - keep elements that satisfy the predicate") {
        ZStream
          .fromEffect(UIO.succeed(1))
          .filter(_ > 0)
          .process
          .use(nPulls(_, 3))
          .map(assert(_)(equalTo(List(Right(1), Left(Right(())), Left(Right(()))))))
      },
      testM("filter - filter out elements that do not satisfy the predicate") {
        ZStream
          .fromEffect(UIO.succeed(1))
          .filter(_ < 0)
          .process
          .use(nPulls(_, 3))
          .map(assert(_)(equalTo(List(Left(Right(())), Left(Right(())), Left(Right(()))))))
      }
    ),
    suite("Constructors")(
      suite("fromEffect")(
        testM("success") {
          ZStream
            .fromEffect(UIO.succeed(1))
            .process
            .use(nPulls(_, 3))
            .map(assert(_)(equalTo(List(Right(1), Left(Right(())), Left(Right(()))))))
        },
        testM("failure") {
          ZStream
            .fromEffect(IO.fail("Ouch"))
            .process
            .use(nPulls(_, 3))
            .map(assert(_)(equalTo(List(Left(Left("Ouch")), Left(Right(())), Left(Right(()))))))
        }
      ),
      suite("managed")(
        testM("success") {
          for {
            ref <- Ref.make(false)
            pulls <- ZStream
                      .managed(Managed.make(UIO.succeed(1))(_ => ref.set(true)))
                      .process
                      .use(nPulls(_, 3))
            fin <- ref.get
          } yield assert(fin)(isTrue) && assert(pulls)(equalTo(List(Right(1), Left(Right(())), Left(Right(())))))
        },
        testM("acquisition failure") {
          for {
            ref <- Ref.make(false)
            pulls <- ZStream
                      .managed(Managed.make(IO.fail("Ouch"))(_ => ref.set(true)))
                      .process
                      .use(nPulls(_, 3))
            fin <- ref.get
          } yield assert(fin)(isFalse) && assert(pulls)(
            equalTo(List(Left(Left("Ouch")), Left(Right(())), Left(Right(()))))
          )
        },
        testM("inner failure") {
          for {
            ref <- Ref.make(false)
            pulls <- ZStream
                      .managed(Managed.make(UIO.succeed(1))(_ => ref.set(true)) *> Managed.fail("Ouch"))
                      .process
                      .use(nPulls(_, 3))
            fin <- ref.get
          } yield assert(fin)(isTrue) && assert(pulls)(
            equalTo(List(Left(Left("Ouch")), Left(Right(())), Left(Right(()))))
          )
        }
      ),
      suite("fromIterable")(
        testM("success")(checkM(Gen.listOf(Gen.anyInt)) { (list: List[Int]) =>
          ZStream
            .fromIterable(list)
            .process
            .use(nPulls(_, list.size + 2))
            .map(
              assert(_)(
                equalTo(
                  list.map(Right(_)) ++
                    List(
                      Left(Right(())),
                      Left(Right(()))
                    )
                )
              )
            )
        }),
        testM("lazy") {
          assertLazy(ZStream.fromIterable)
        }
      ),
      suite("fromIterator")(
        testM("success")(checkM(Gen.listOf(Gen.anyInt)) { (list: List[Int]) =>
          ZStream
            .fromIterator(list.iterator)
            .process
            .use(nPulls(_, list.size + 2))
            .map(
              assert(_)(
                equalTo(
                  list.map(Right(_)) ++
                    List(
                      Left(Right(())),
                      Left(Right(()))
                    )
                )
              )
            )
        }),
        testM("lazy") {
          assertLazy(ZStream.fromIterator)
        }
      )
    )
  )
}
