package zio.test

import zio._
import zio.test.Assertion.equalTo

object ManagedSpec
    extends ZIOBaseSpec(
      suite("ManagedSpec")(
        suite("managed shared")(
          testM("first test") {
            for {
              _      <- ZIO.accessM[Ref[Int]](_.update(_ + 1))
              result <- ZIO.accessM[Ref[Int]](_.get)
            } yield assert(result, equalTo(2))
          },
          testM("second test") {
            for {
              _      <- ZIO.accessM[Ref[Int]](_.update(_ + 1))
              result <- ZIO.accessM[Ref[Int]](_.get)
            } yield assert(result, equalTo(3))
          },
          testM("third test") {
            for {
              _      <- ZIO.accessM[Ref[Int]](_.update(_ + 1))
              result <- ZIO.accessM[Ref[Int]](_.get)
            } yield assert(result, equalTo(4))
          }
        ).provideManagedShared(Ref.make(1).toManaged(_.set(-10))),
        suite("managed per test")(
          testM("first test") {
            for {
              _      <- ZIO.accessM[Ref[Int]](_.update(_ + 1))
              result <- ZIO.accessM[Ref[Int]](_.get)
            } yield assert(result, equalTo(2))
          },
          testM("second test") {
            for {
              _      <- ZIO.accessM[Ref[Int]](_.update(_ + 1))
              result <- ZIO.accessM[Ref[Int]](_.get)
            } yield assert(result, equalTo(2))
          }
        ).provideManaged(Ref.make(1).toManaged(_.set(-10)))
      )
    )
