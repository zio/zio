package threadlocalbridge

import zio._

/** Title: Understanding ThreadLocal Limitations with ZIO Fibers
  * Description: Demonstrates why plain Java ThreadLocal doesn't work reliably with
  * ZIO's fiber-based concurrency model, as fibers may run on different threads.
  * Run: sbt "threadlocal-bridge/runMain threadlocalbridge.Concept1Example"
  */
object Concept1Example extends App {
  val myThreadLocal = new ThreadLocal[String]()

  def printThreadInfo(label: String): ZIO[Any, Nothing, Unit] = ZIO.succeed {
    val value = myThreadLocal.get()
    val thread = Thread.currentThread().getName
    println(s"[$label] Thread: $thread, ThreadLocal value: $value")
  }

  val program: ZIO[Any, Nothing, Unit] = for {
    _ <- ZIO.succeed(myThreadLocal.set("Main Value"))
    _ <- printThreadInfo("After set in main")
    
    // Spawn a fiber that may run on a different thread
    _ <- ZIO.scoped {
      ZIO.succeed(println(s"In forked fiber: ThreadLocal value is ${myThreadLocal.get()}")).fork
    }
    
    // Wait briefly and check main thread again
    _ <- ZIO.sleep(100.millis)
    _ <- printThreadInfo("Back in main after fork")
  } yield ()

  def run(args: List[String]): ZIO[Any, Any, Any] = program
}
