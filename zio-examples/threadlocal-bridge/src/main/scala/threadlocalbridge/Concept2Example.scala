package threadlocalbridge

import zio._

/** Title: Introducing ThreadLocalBridge
  * Description: Shows how to use ZIO's ThreadLocalBridge to safely create FiberRefs
  * that are linked to Java ThreadLocal storage, ensuring values are properly
  * maintained across fiber boundaries and async operations.
  * Run: sbt "threadlocal-bridge/runMain threadlocalbridge.Concept2Example"
  */
object Concept2Example extends App {
  val myThreadLocal = new ThreadLocal[String]()

  def printBridgeValue(label: String, fiberRef: FiberRef[String]): ZIO[Any, Nothing, Unit] = for {
    value <- fiberRef.get
    thread <- ZIO.succeed(Thread.currentThread().getName)
    _ <- ZIO.debug(s"[$label] Thread: $thread, Value: $value")
  } yield ()

  val program: ZIO[ThreadLocalBridge, Nothing, Unit] = ZIO.scoped {
    for {
      // Create a FiberRef linked to our ThreadLocal
      myFiberRef <- ThreadLocalBridge.makeFiberRef[String]("Initial Value")(
        value => myThreadLocal.set(value)
      )
      
      _ <- printBridgeValue("After creation", myFiberRef)
      
      // Update the value through the FiberRef
      _ <- myFiberRef.set("Modified Value")
      _ <- printBridgeValue("After set", myFiberRef)
      
      // Verify ThreadLocal is synchronized
      syncedValue <- ZIO.succeed(Option(myThreadLocal.get()))
      _ <- ZIO.debug(s"ThreadLocal value synchronized: $syncedValue")
      
      // Use the value in a forked fiber
      _ <- ZIO.scoped {
        for {
          value <- myFiberRef.get
          _ <- ZIO.debug(s"In fiber: Value = $value")
          _ <- ZIO.unit
        } yield ()
      }.fork.flatMap(_.join)
      
      _ <- printBridgeValue("Back in main", myFiberRef)
    } yield ()
  }

  def run(args: List[String]): ZIO[Any, Any, Any] = 
    program.provideLayer(ThreadLocalBridge.live)
}
