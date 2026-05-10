package zio.internal

import zio._

import scala.concurrent.Await
import scala.concurrent.duration.{Duration => ScDuration, SECONDS}

/**
 * Verifies the worker-interrupt immune system in ZScheduler.
 *
 * Pre-fix, this program wedged the global ZScheduler:
 *   - 16 fibers each called Thread.currentThread().interrupt().
 *   - ZScheduler workers read isInterrupted at every loop checkpoint and
 *     exited, leaving the state field, `idle`/`cache` queues, and `workers[]`
 *     array referring to dead threads.
 *   - Subsequent parallel work stalled forever because maybeUnparkWorker
 *     read currentActive==poolSize and never unparked.
 *
 * Post-fix, ZScheduler uses a private `closing` flag for termination and
 * clears the thread interrupt flag after every runnable. Workers survive
 * foreign interrupts; subsequent work completes normally. close() then shuts
 * the scheduler down cleanly, draining queues and unparking workers.
 *
 * Run via: sbt 'coreTestsJVM/Test/runMain zio.internal.ZSchedulerInterruptRepro'
 */
object ZSchedulerInterruptRepro {

  def main(args: Array[String]): Unit = {
    val poolSize = java.lang.Runtime.getRuntime.availableProcessors

    println(s"--- ZScheduler interrupt-immunity verification ---")
    println(s"Pool size (poolSize):           $poolSize")

    Unsafe.unsafe { implicit u =>
      Runtime.default.unsafe
        .run(ZIO.foreachParDiscard(1 to poolSize * 4)(_ => ZIO.unit))
        .getOrThrowFiberFailure()
    }
    Thread.sleep(100)
    println(s"Post-warmup worker threads:     ${countWorkers()}")

    // Phase A: fibers that call Thread.currentThread.interrupt on the carrier.
    val phaseA = Unsafe.unsafe { implicit u =>
      val interruptCarrier = ZIO.succeed {
        val t = Thread.currentThread()
        if (t.getName.startsWith("ZScheduler-Worker-")) t.interrupt()
      }
      val program = ZIO.foreachParDiscard(1 to poolSize * 4)(_ => interruptCarrier)
      Runtime.default.unsafe.runToFuture(program)
    }
    val phaseATimeout = 5
    try {
      Await.result(phaseA, ScDuration(phaseATimeout.toLong, SECONDS))
      println(s"Interrupt fibers completed:     ok")
    } catch {
      case _: java.util.concurrent.TimeoutException =>
        println(s"Interrupt fibers DID NOT FINISH in ${phaseATimeout}s (regression)")
        phaseA.cancel()
    }
    Thread.sleep(200)

    val survivors = countWorkers()
    println(s"After interrupt phase:          $survivors live workers (was $poolSize)")
    if (survivors == poolSize) println(s"** Immunity confirmed: no workers died from foreign interrupts.")
    else                       println(s"** Regression: ${poolSize - survivors} worker(s) died.")

    // Phase B: subsequent parallel workload — should complete promptly.
    val phaseB = Unsafe.unsafe { implicit u =>
      val tasks   = 1000
      val program = ZIO.foreachParDiscard(1 to tasks)(_ => ZIO.unit)
      Runtime.default.unsafe.runToFuture(program)
    }
    val phaseBTimeout = 5
    val phaseBStart   = java.lang.System.currentTimeMillis()
    try {
      Await.result(phaseB, ScDuration(phaseBTimeout.toLong, SECONDS))
      val took = java.lang.System.currentTimeMillis() - phaseBStart
      println(s"Subsequent parallel work:       1000 tasks in ${took}ms (healthy)")
    } catch {
      case _: java.util.concurrent.TimeoutException =>
        println(s"Subsequent parallel work:       STALLED, did not finish in ${phaseBTimeout}s (regression)")
        phaseB.cancel()
    }

    // Phase C: close() shuts the scheduler down. Exercised against a
    // *private* scheduler so we don't disturb Runtime.default for the rest
    // of the JVM. Verifies that close() makes the worker threads exit and
    // makes submit reject.
    val privateScheduler = new ZScheduler(autoBlocking = true)
    val schedulerName    = s"private-scheduler-${java.lang.System.identityHashCode(privateScheduler)}"
    Thread.sleep(100)
    val privateInitial = countByPrefix("ZScheduler-Worker-") - poolSize
    println(s"--- close() test on a private scheduler ---")
    println(s"Private scheduler workers:      $privateInitial (expected $poolSize)")
    // Submit a no-op runnable to exercise the live path.
    val executed = new java.util.concurrent.atomic.AtomicInteger(0)
    Unsafe.unsafe { implicit u =>
      var i = 0
      while (i < 100) {
        privateScheduler.submit(new Runnable {
          override def run(): Unit = { executed.incrementAndGet(); () }
        })
        i += 1
      }
    }
    Thread.sleep(200)
    println(s"Pre-close runnables executed:   ${executed.get()} / 100")

    privateScheduler.close()
    // After close, submit returns false and tasks are not enqueued.
    val postCloseAccepted = Unsafe.unsafe { implicit u =>
      privateScheduler.submit(new Runnable { override def run(): Unit = () })
    }
    println(s"submit() after close returns:   $postCloseAccepted (expected false)")

    // Give workers time to exit.
    val deadline = java.lang.System.currentTimeMillis() + 2000
    while (
      java.lang.System.currentTimeMillis() < deadline &&
      countByPrefix("ZScheduler-Worker-") > poolSize
    ) Thread.sleep(50)
    val privateAfter = countByPrefix("ZScheduler-Worker-") - poolSize
    println(s"Private scheduler workers post: $privateAfter (expected 0)")
    if (privateAfter == 0) println(s"** Close confirmed: all private workers exited.")
    else                   println(s"** Regression: $privateAfter private workers still alive.")

    // Idempotence.
    privateScheduler.close()
    println(s"--- end ---")
    java.lang.System.exit(0)
    // Reference the name var so the compiler doesn't warn (we keep it for debugging).
    val _ = schedulerName
  }

  private def countWorkers(): Int = countByPrefix("ZScheduler-Worker-")

  private def countByPrefix(prefix: String): Int = {
    val keys  = Thread.getAllStackTraces().keySet().toArray
    var count = 0
    var i     = 0
    while (i < keys.length) {
      val t = keys(i).asInstanceOf[Thread]
      if (t.isAlive && t.getName.startsWith(prefix)) count += 1
      i += 1
    }
    count
  }
}
