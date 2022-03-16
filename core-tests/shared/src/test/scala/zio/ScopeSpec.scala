package zio

import zio.test._

object ScopeSpec extends ZIOBaseSpec {

  def resource(id: Int)(ref: Ref[Chunk[Action]]): ZIO[Scope, Nothing, Int] =
    ZIO
      .uninterruptible(ref.update(_ :+ Action.Acquire(id)).as(id))
      .ensuring(Scope.addFinalizer(ref.update(_ :+ Action.Release(id))))

  def spec = suite("ScopeSpec")(
    test("basic example") {
      for {
        ref <- Ref.make[Chunk[Action]](Chunk.empty)
        _ <- ZIO.scoped {
               for {
                 resource <- resource(1)(ref)
                 _        <- ref.update(_ :+ Action.Use(resource))
               } yield ()
             }
        actions <- ref.get
      } yield assertTrue(actions(0) == Action.Acquire(1)) &&
        assertTrue(actions(1) == Action.Use(1)) &&
        assertTrue(actions(2) == Action.Release(1))
    }
  )

  sealed trait Action

  object Action {
    final case class Acquire(id: Int) extends Action
    final case class Use(id: Int)     extends Action
    final case class Release(id: Int) extends Action
  }

}
