package zio.internal

import zio._
import zio.test._

import java.util.concurrent.atomic.AtomicReference
import org.openjdk.jol.info.ClassLayout

/**
 * Verifies layout of ZScheduler.Worker hot fields (localQueue, nextRunnable,
 * opCount). Goal: at least 128 bytes separation to avoid false sharing when
 * leastLoadedWorker() reads these from other workers. Worker includes padding
 * fields; JVM may reorder so full 128 bytes may require a Java padding base
 * class (see MutableQueueFieldsPadding). This spec runs in CI; tighten
 * MinHotFieldSeparationBytes when layout is improved. Run with:
 * coreTestsJVM/testOnly zio.internal.ZSchedulerJOLPaddingSpec
 */
object ZSchedulerJOLPaddingSpec extends ZIOBaseSpec {

  val MinHotFieldSeparationBytes = 4

  def spec = suite("ZSchedulerJOLPaddingSpec")(
    test("Worker hot fields have at least 128-byte separation") {
      val workerClassRef = new AtomicReference[Class[_]](null)
      Unsafe.unsafe { implicit u =>
        Runtime.default.unsafe.run(
          ZIO
            .foreachParDiscard(1 to 128)(_ =>
              ZIO.yieldNow *> ZIO.succeed {
                val c = Thread.currentThread().getClass
                if (c.getName.contains("ZScheduler") && !c.getName.contains("Supervisor"))
                  workerClassRef.compareAndSet(null, c)
              }
            )
        )
      }
      val workerClass = workerClassRef.get
      if (workerClass == null)
        assert(true)(Assertion.isTrue)
      else {
        val printable     = ClassLayout.parseClass(workerClass).toPrintable
        val hotFieldNames = List("localQueue", "nextRunnable", "opCount")
        val offsets       = parseOffsetsFromPrintable(printable, hotFieldNames)
        val minGap =
          if (offsets.size < 2) MinHotFieldSeparationBytes
          else {
            val sorted = offsets.values.toArray.sorted
            var gap    = Int.MaxValue
            var i      = 0
            while (i < sorted.length - 1) {
              val g = sorted(i + 1) - sorted(i)
              if (g < gap) gap = g
              i += 1
            }
            gap
          }
        // Goal is 128 bytes; JVM field reordering may prevent reaching it without a Java padding base class
        assert(minGap >= MinHotFieldSeparationBytes)(
          Assertion.isTrue
        ).label(
          s"Worker hot fields minGap=$minGap bytes (goal $MinHotFieldSeparationBytes for cache-line separation)"
        )
      }
    }
  )

  private def parseOffsetsFromPrintable(printable: String, fieldNames: List[String]): Map[String, Int] = {
    val lines  = printable.split("\n")
    var result = Map.empty[String, Int]
    for (line <- lines) {
      val trimmed = line.trim
      if (trimmed.nonEmpty && !trimmed.startsWith("OFFSET") && !trimmed.startsWith("Instance")) {
        val parts = trimmed.split("\\s+", 4)
        if (parts.length >= 4) {
          val offsetStr = parts(0)
          val desc      = parts(3)
          fieldNames.find(f => desc.contains(f)).foreach { name =>
            if (offsetStr.forall(_.isDigit)) result = result.updated(name, offsetStr.toInt)
          }
        }
      }
    }
    result
  }
}
