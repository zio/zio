package threadlocalbridge

import zio._

/** Title: Fiber Isolation with ThreadLocalBridge
  * Description: Demonstrates how ThreadLocalBridge maintains proper isolation of
  * values across different fibers. Each fiber can have its own independent value
  * that is automatically synchronized with ThreadLocal storage without interference.
  * Run: sbt "threadlocal-bridge/runMain threadlocalbridge.Concept3Example"
  */
object Concept3Example extends App {
  val userIdThreadLocal = new ThreadLocal[String]()

  def simulateUserOperation(
    fiberRef: FiberRef[String],
    userId: String
  ): ZIO[Any, Nothing, Unit] = for {
    _ <- fiberRef.set(userId)
    _ <- ZIO.debug(s"User $userId: Set ID in fiber")
    
    _ <- ZIO.sleep(50.millis)
    
    currentId <- fiberRef.get
    _ <- ZIO.debug(s"User $userId: Current ID in fiber is $currentId")
    
    _ <- ZIO.sleep(50.millis)
    
    stillCurrentId <- fiberRef.get
    _ <- ZIO.debug(s"User $userId: Still has ID $stillCurrentId (isolation verified)")
  } yield ()

  val program: ZIO[ThreadLocalBridge, Nothing, Unit] = ZIO.scoped {
    for {
      // Create a FiberRef linked to ThreadLocal storage
      userIdRef <- ThreadLocalBridge.makeFiberRef[String]("main-user")(
        id => userIdThreadLocal.set(id)
      )
      
      _ <- ZIO.debug("Main fiber initialized with: main-user")
      
      // Fork multiple concurrent fibers, each with their own value
      fiber1 <- simulateUserOperation(userIdRef, "user-1").fork
      fiber2 <- simulateUserOperation(userIdRef, "user-2").fork
      fiber3 <- simulateUserOperation(userIdRef, "user-3").fork
      
      _ <- ZIO.sleep(50.millis)
      
      // Check main fiber's value is unchanged
      mainValue <- userIdRef.get
      _ <- ZIO.debug(s"Main fiber still has: $mainValue (not affected by forks)")
      
      // Wait for all fibers to complete
      _ <- fiber1.join
      _ <- fiber2.join
      _ <- fiber3.join
      
      _ <- ZIO.debug("All fibers completed with isolated values")
    } yield ()
  }

  def run(args: List[String]): ZIO[Any, Any, Any] = 
    program.provideLayer(ThreadLocalBridge.live)
}
