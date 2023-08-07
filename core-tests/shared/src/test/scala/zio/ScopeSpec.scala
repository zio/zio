package zio

import zio.test._

object ScopeSpec extends ZIOBaseSpec {

  def resource(id: Int)(ref: Ref[Chunk[Action]]): ZIO[Scope, Nothing, Int] =
    ZIO
      .uninterruptible(ref.update(_ :+ Action.Acquire(id)).as(id))
      .ensuring(Scope.addFinalizer(ref.update(_ :+ Action.Release(id))))

  def spec = suite("ScopeSpec")(
    suite("scoped")(
      test("runs finalizers when scope is closed") {
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
    ),
    suite("parallelFinalizers")(
      test("runs finalizers when scope is closed") {
        for {
          ref <- Ref.make[Chunk[Action]](Chunk.empty)
          _ <- ZIO.scoped {
                 for {
                   tuple <- ZIO.parallelFinalizers {
                              resource(1)(ref).zipPar(resource(2)(ref))
                            }
                   (resource1, resource2) = tuple
                   _                     <- ref.update(_ :+ Action.Use(resource1)).zipPar(ref.update(_ :+ Action.Use(resource2)))
                 } yield ()
               }
          actions <- ref.get
        } yield assertTrue(actions.slice(0, 2).toSet == Set(Action.acquire(1), Action.acquire(2))) &&
          assertTrue(actions.slice(2, 4).toSet == Set(Action.use(1), Action.use(2))) &&
          assertTrue(actions.slice(4, 6).toSet == Set(Action.release(1), Action.release(2)))
      },
      test("runs finalizers in parallel") {
        for {
          promise <- Promise.make[Nothing, Unit]
          _ <- ZIO.scoped {
                 ZIO.parallelFinalizers {
                   ZIO.addFinalizer(promise.succeed(())) *> ZIO.addFinalizer(promise.await)
                 }
               }
        } yield assertCompletes
      }
    ),
    test("using") {
      for {
        ref1 <- Ref.make[Chunk[Action]](Chunk.empty)
        ref2 <- Ref.make[Chunk[Action]](Chunk.empty)
        _ <- ZIO.scoped {
               for {
                 _ <- ZIO.using(resource(1)(ref1)) { _ =>
                        ref1.update(_ :+ Action.Use(1)) *>
                          resource(2)(ref2)
                      }
                 _ <- ref2.update(_ :+ Action.Use(2))
               } yield ()
             }
        actions1 <- ref1.get
        actions2 <- ref2.get
      } yield assertTrue(actions1 == Chunk(Action.acquire(1), Action.use(1), Action.release(1))) &&
        assertTrue(actions2 == Chunk(Action.acquire(2), Action.use(2), Action.release(2)))
    },
    suite("sequentialFinalizers") {
      test("preserves order of nested finalizers") {
        for {
          ref     <- Ref.make[Chunk[Action]](Chunk.empty)
          left     = ZIO.sequentialFinalizers(resource(1)(ref) *> resource(2)(ref))
          right    = ZIO.sequentialFinalizers(resource(3)(ref) *> resource(4)(ref))
          _       <- ZIO.scoped(ZIO.parallelFinalizers(left.zipPar(right)))
          actions <- ref.get
        } yield assertTrue {
          actions.indexOf(Action.release(2)) < actions.indexOf(Action.release(1)) &&
          actions.indexOf(Action.release(4)) < actions.indexOf(Action.release(3))
        }
      }
    },
    test("withEarlyRelease") {
      for {
        ref     <- Ref.make[Chunk[Action]](Chunk.empty)
        left     = resource(1)(ref)
        right    = resource(2)(ref).withEarlyRelease
        _       <- left *> right.flatMap { case (release, _) => release }
        actions <- ref.get
      } yield assertTrue {
        actions(0) == Action.acquire(1) &&
        actions(1) == Action.acquire(2) &&
        actions(2) == Action.release(2)
      }
    },
    test("propagates FiberRef values") {
      for {
        fiberRef <- FiberRef.make(false)
        ref      <- Ref.make(false)
        scope    <- Scope.make
        _        <- fiberRef.locally(true)(scope.addFinalizer(fiberRef.get.flatMap(ref.set)))
        _        <- fiberRef.get.debug
        _        <- scope.close(Exit.unit)
        value    <- ref.get
      } yield assertTrue(value)
    }
  )

  sealed trait Action

  object Action {
    final case class Acquire(id: Int) extends Action
    final case class Use(id: Int)     extends Action
    final case class Release(id: Int) extends Action

    def acquire(id: Int): Action =
      Action.Acquire(id)

    def use(id: Int): Action =
      Action.Use(id)

    def release(id: Int): Action =
      Action.Release(id)
  }

}
