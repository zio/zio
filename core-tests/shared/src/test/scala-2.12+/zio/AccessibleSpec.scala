package zio

import zio.test.assertCompletes

object AccessibleSpec extends ZIOBaseSpec {

  val spec = suite("AccessibleSpec")(
    test("it can access methods of a service") {
      for {
        _ <- TestService(_.withArgs(1))
        _ <- TestService(_.withArgsZIO(1))
        _ <- TestService(_.withoutArgs)
        _ <- TestService(_.withoutArgsZIO)
        _ <- TestService(_.polymorphic(1))
        _ <- TestService(_.polymorphicZIO(1))
      } yield assertCompletes
    }
  ).provideLayer(TestService.test)

  trait TestService {
    def withArgsZIO(a: Int): ZIO[Any, Nothing, Int]
    def withArgs(a: Int): Int
    def withoutArgsZIO: ZIO[Any, Nothing, Int]
    def withoutArgs: Int
    def polymorphicZIO[A](a: A): ZIO[Any, Nothing, A]
    def polymorphic[A](a: A): A
  }

  object TestService extends Accessible[TestService] {
    val test =
      ZLayer.succeed(new TestService {
        override def withArgsZIO(a: Int): ZIO[Any, Nothing, Int]   = ZIO.succeed(a)
        override def withArgs(a: Int): Int                         = a
        override def withoutArgsZIO: ZIO[Any, Nothing, Int]        = ZIO.succeed(0)
        override def withoutArgs: Int                              = 0
        override def polymorphicZIO[A](a: A): ZIO[Any, Nothing, A] = ZIO.succeed(a)
        override def polymorphic[A](a: A): A                       = a
      })
  }
}
