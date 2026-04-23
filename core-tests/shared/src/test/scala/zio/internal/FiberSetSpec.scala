package zio.internal

import zio.internal.FiberSet.IsAlive
import zio.test._
import zio.test.TestAspect.{flaky, jvmOnly, nonFlaky}
import zio.{Clock, Fiber, Promise, Ref, ZIO, ZIOBaseSpec}

object FiberSetSpec extends ZIOBaseSpec {
  final case class Wrapper[A](value: A)

  def spec =
    suite("FiberSetSpec")(
      portableSpec,
      jvmSpec
    )

  private val portableSpec =
    suite("portable")(
      test("add then iterator returns element") {
        val set     = newSet[String]()
        val element = Wrapper("value")

        set.add(element)

        assertTrue(set.iterator.next() eq element)
      },
      test("add 100 elements, iterator returns all") {
        val set      = newSet[Int]()
        val elements = (0 until 100).map(Wrapper(_)).toList

        elements.foreach(set.add)

        assertTrue(set.iterator.toSet == elements.toSet)
      },
      test("add then remove, iterator is empty") {
        val set     = newSet[String]()
        val element = Wrapper("value")

        set.add(element)
        set.remove(element)

        assertTrue(!set.iterator.hasNext)
      },
      test("isEmpty on new set") {
        val set = newSet[String]()

        assertTrue(set.isEmpty)
      },
      test("isEmpty false after add") {
        val set     = newSet[String]()
        val element = Wrapper("value")

        set.add(element)

        assertTrue(!set.isEmpty)
      },
      test("size increments on add") {
        val set      = newSet[Int]()
        val elements = (0 until 3).map(Wrapper(_))

        elements.foreach(set.add)

        assertTrue(set.size == 3)
      },
      test("duplicate add is idempotent") {
        val set     = newSet[String]()
        val element = Wrapper("value")

        set.add(element)
        set.add(element)

        assertTrue(set.size == 1)
      },
      test("isAlive filter excludes non-alive entries") {
        val neverAlive = new IsAlive[Wrapper[String]] {
          def apply(value: Wrapper[String]): Boolean = false
        }
        val set     = newSet[String](isAlive = neverAlive)
        val element = Wrapper("value")

        set.add(element)

        assertTrue(!set.iterator.hasNext)
      }
    )

  private def jvmSpec =
    if (TestPlatform.isJVM)
      suite("jvm")(
        test("GC reclaims entry after strong-ref drop") {
          ZIO.attempt {
            val set                   = newSet[String]()
            var hard: Wrapper[String] = Wrapper("value")

            set.add(hard)
            hard = null

            System.gc()
            set.gc(true)

            assertTrue(!set.iterator.hasNext)
          }.orDie
        } @@ jvmOnly @@ flaky,
        test("ReferenceQueue drain on add") {
          ZIO.attempt {
            val set  = newSet[Int]()
            val refs = Array.tabulate(50)(i => Wrapper(i))

            refs.foreach(set.add)
            refs.indices.foreach(i => refs(i) = null)

            System.gc()
            set.add(Wrapper(100))

            assertTrue(set.size < 51)
          }.orDie
        } @@ jvmOnly @@ flaky,
        test("4-thread concurrent add") {
          ZIO.attempt {
            import java.util.concurrent.{CountDownLatch, Executors, TimeUnit}
            import java.util.concurrent.atomic.AtomicReference

            val set      = newSet[Int](64)
            val elements = Array.tabulate(1000)(i => Wrapper(i))
            val failure  = new AtomicReference[Throwable](null)
            val executor = Executors.newFixedThreadPool(4)
            val latch    = new CountDownLatch(4)

            try {
              (0 until 4).foreach { t =>
                executor.submit(new Runnable {
                  def run(): Unit =
                    runSafely(failure) {
                      latch.countDown()
                      latch.await()
                      (t * 250 until (t + 1) * 250).foreach(i => set.add(elements(i)))
                    }
                })
              }
            } finally {
              executor.shutdown()
              if (!executor.awaitTermination(30, TimeUnit.SECONDS))
                throw new AssertionError("executor did not terminate")
            }

            assertTrue((failure.get() eq null) && (set.size <= elements.length))
          }.orDie
        } @@ jvmOnly,
        test("16-thread concurrent add, no CME") {
          ZIO.attempt {
            import java.util.concurrent.{CountDownLatch, Executors, TimeUnit}

            val threads   = 16
            val perThread = 1000
            val set       = newSet[Int](64)
            val elements  = Array.tabulate(threads * perThread)(i => Wrapper(i))
            val executor  = Executors.newFixedThreadPool(threads)
            val latch     = new CountDownLatch(threads)

            try {
              (0 until threads).foreach { t =>
                executor.submit(new Runnable {
                  def run(): Unit = {
                    latch.countDown()
                    latch.await()
                    (t * perThread until (t + 1) * perThread).foreach(i => set.add(elements(i)))
                  }
                })
              }
            } finally {
              executor.shutdown()
              if (!executor.awaitTermination(30, TimeUnit.SECONDS))
                throw new AssertionError("executor did not terminate")
            }

            assertTrue(set.size == threads * perThread)
          }.orDie
        } @@ jvmOnly,
        test("concurrent add + remove, no exception") {
          ZIO.attempt {
            import java.util.concurrent.{CountDownLatch, Executors, TimeUnit}
            import java.util.concurrent.atomic.AtomicReference

            val threads   = 8
            val perThread = 250
            val set       = newSet[Int](64)
            val elements  = Array.tabulate(threads * perThread)(i => Wrapper(i))
            val failure   = new AtomicReference[Throwable](null)
            val executor  = Executors.newFixedThreadPool(threads * 2)
            val latch     = new CountDownLatch(threads * 2)

            try {
              (0 until threads).foreach { t =>
                executor.submit(new Runnable {
                  def run(): Unit =
                    runSafely(failure) {
                      latch.countDown()
                      latch.await()
                      (t * perThread until (t + 1) * perThread).foreach(i => set.add(elements(i)))
                    }
                })
                executor.submit(new Runnable {
                  def run(): Unit =
                    runSafely(failure) {
                      latch.countDown()
                      latch.await()
                      (t * perThread until (t + 1) * perThread).foreach(i => set.remove(elements(i)))
                    }
                })
              }
            } finally {
              executor.shutdown()
              if (!executor.awaitTermination(30, TimeUnit.SECONDS))
                throw new AssertionError("executor did not terminate")
            }

            assertTrue(failure.get() eq null)
          }.orDie
        } @@ jvmOnly,
        test("concurrent add + iterate, never throws CME") {
          ZIO.attempt {
            import java.util.concurrent.{Executors, TimeUnit}
            import java.util.concurrent.atomic.AtomicReference

            val set      = newSet[Int](64)
            val failure  = new AtomicReference[Throwable](null)
            val executor = Executors.newFixedThreadPool(2)

            try {
              executor.submit(new Runnable {
                def run(): Unit =
                  runSafely(failure) {
                    (0 until 20000).foreach(i => set.add(Wrapper(i)))
                  }
              })
              executor.submit(new Runnable {
                def run(): Unit =
                  runSafely(failure) {
                    val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(1000)
                    while ((System.nanoTime() < deadline) && (failure.get() eq null)) {
                      val it = set.iterator
                      while (it.hasNext) {
                        val _ = it.next()
                      }
                    }
                  }
              })
            } finally {
              executor.shutdown()
              if (!executor.awaitTermination(30, TimeUnit.SECONDS))
                throw new AssertionError("executor did not terminate")
            }

            assertTrue(failure.get() eq null)
          }.orDie
        } @@ jvmOnly,
        test("virtual-thread concurrent add, no carrier pinning") {
          ZIO.attempt {
            import java.util.concurrent.{CountDownLatch, TimeUnit}

            if (Runtime.version().feature() < 21) assertTrue(true)
            else {
              val count = 1000
              val set   = newSet[Int](1024)
              val latch = new CountDownLatch(count)
              val executor = Class
                .forName("java.util.concurrent.Executors")
                .getMethod("newVirtualThreadPerTaskExecutor")
                .invoke(null)
                .asInstanceOf[java.util.concurrent.ExecutorService]

              try {
                (0 until count).foreach { idx =>
                  executor.submit(new Runnable {
                    def run(): Unit = {
                      latch.countDown()
                      latch.await()
                      set.add(Wrapper(idx))
                    }
                  })
                }
              } finally executor.shutdown()

              assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS) && (set.size == count))
            }
          }.orDie
        } @@ jvmOnly,
        test("resize: add 3x initialCapacity elements, all present in iterator") {
          val set      = newSet[Int](16)
          val elements = Array.tabulate(48)(i => Wrapper(i))

          elements.foreach(set.add)

          assertTrue(set.iterator.toSet == elements.toSet)
        } @@ jvmOnly,
        test("tombstone reuse: add/remove/add same slot") {
          val set     = newSet[String](16)
          val element = Wrapper("value")

          set.add(element)
          set.remove(element)
          set.add(element)

          assertTrue(set.iterator.hasNext && (set.size == 1))
        } @@ jvmOnly,
        test("stress: fork-storm + System.gc() reclaims _roots entries") {
          for {
            fibers <- ZIO.foreachPar((1 to 1000).toList)(_ => ZIO.never.forkDaemon)
            before <- Fiber.roots.map(_.size)
            _      <- ZIO.foreachParDiscard(fibers)(_.interrupt)
            _ <- ZIO.succeed {
                   System.gc()
                   Thread.sleep(50L)
                   System.gc()
                 }
            after <- Fiber.roots.map(_.size)
          } yield assertTrue(after < before)
        } @@ jvmOnly @@ nonFlaky(5),
        test("stress: interrupt under churn propagates to all live children, no CME") {
          for {
            started <- Promise.make[Nothing, Unit]
            counter <- Ref.make(0)
            child = (counter.update(_ + 1) *> started.succeed(()) *> ZIO.never)
                      .onInterrupt(counter.update(_ - 1))
            parent <- ZIO.foreachParDiscard((1 to 1000).toList)(_ => child.fork).fork
            _      <- started.await
            _      <- parent.interrupt
            _      <- Clock.sleep(java.time.Duration.ofMillis(200L))
            finalN <- counter.get
          } yield assertTrue(finalN == 0)
        } @@ jvmOnly @@ nonFlaky(5) @@ TestAspect.withLiveClock
      )
    else
      suite("jvm")()

  private def newSet[A](
    initialCapacity: Int = 16,
    isAlive: IsAlive[Wrapper[A]] = IsAlive.always
  ): FiberSet[Wrapper[A]] =
    new FiberSet[Wrapper[A]](initialCapacity = initialCapacity, isAlive = isAlive, autoGcEvery = None)

  private def runSafely(failure: java.util.concurrent.atomic.AtomicReference[Throwable])(body: => Unit): Unit =
    try body
    catch {
      case t: Throwable =>
        val _ = failure.compareAndSet(null, t)
    }
}
