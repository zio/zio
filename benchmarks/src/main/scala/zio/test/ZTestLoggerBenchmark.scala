package zio.test

import org.openjdk.jmh.annotations.{Scope => JmhScope, _}
import zio.BenchmarkUtil.unsafeRun
import zio._

import java.util.concurrent.{CountDownLatch, Executors, TimeUnit}

@State(JmhScope.Benchmark)
@BenchmarkMode(Array(Mode.SingleShotTime))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
@Fork(1)
class ZTestLoggerBenchmark {

  @Param(Array("100", "1000", "10000"))
  var messageCount: Int = _

  private val workerCount = 16
  private val message     = () => "entry"

  private var executor                          = Executors.newFixedThreadPool(workerCount)
  private var logger: ZTestLogger[String, Unit] = _
  private var start: CountDownLatch             = _
  private var done: CountDownLatch              = _
  private var workers: Array[Runnable]          = _

  @Setup(Level.Trial)
  def setupWorkers(): Unit = {
    val quotient  = messageCount / workerCount
    val remainder = messageCount % workerCount

    workers = Array.tabulate(workerCount) { worker =>
      val count = quotient + (if (worker < remainder) 1 else 0)
      new Runnable {
        def run(): Unit = {
          val currentLogger = logger
          val currentStart  = start
          val currentDone   = done
          try {
            currentStart.await()
            var i = 0
            while (i < count) {
              currentLogger(
                Trace.empty,
                FiberId.None,
                LogLevel.Info,
                message,
                Cause.empty,
                FiberRefs.empty,
                Nil,
                Map.empty
              )
              i += 1
            }
          } finally currentDone.countDown()
        }
      }
    }
  }

  @Setup(Level.Invocation)
  def setupInvocation(): Unit = {
    logger = ZTestLogger.unsafe.make()(Unsafe).asInstanceOf[ZTestLogger[String, Unit]]
    start = new CountDownLatch(1)
    done = new CountDownLatch(workerCount)
  }

  @TearDown(Level.Trial)
  def shutdownWorkers(): Unit =
    executor.shutdown()

  @Benchmark
  def logConcurrently(): Int = {
    var i = 0
    while (i < workers.length) {
      executor.execute(workers(i))
      i += 1
    }
    start.countDown()
    done.await()
    unsafeRun(logger.logOutput).size
  }
}
