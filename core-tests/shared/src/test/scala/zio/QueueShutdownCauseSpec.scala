package zio {

  import zio.QueueSpecUtil._
  import zio.test.Assertion._
  import zio.test.TestAspect.{exceptJS, nonFlaky}
  import zio.test._
  import zioqueueexternal.ExternalPackageShutdownCauseHelper

  object QueueShutdownCauseSpec extends ZIOBaseSpec {

    def spec = suite("QueueShutdownCauseSpec")(
      test("shutdownCause drains buffered items and fails future operations with the cause") {
        val boom  = new RuntimeException("worker failed")
        val cause = Cause.die(boom)

        for {
          queue     <- Queue.bounded[Int](4)
          _         <- queue.offerAll(Chunk(1, 2, 3))
          remaining <- queue.shutdownCause(cause)
          isDown    <- queue.isShutdown
          _         <- queue.awaitShutdown
          offer     <- queue.offer(4).sandbox.either
          offerAll  <- queue.offerAll(Chunk(4, 5)).sandbox.either
          take      <- queue.take.sandbox.either
          takeAll   <- queue.takeAll.sandbox.either
          takeUpTo  <- queue.takeUpTo(1).sandbox.either
          poll      <- queue.poll.sandbox.either
          size      <- queue.size.sandbox.either
          isEmpty   <- queue.isEmpty.sandbox.either
          isFull    <- queue.isFull.sandbox.either
        } yield assertTrue(
          remaining == Chunk(1, 2, 3),
          isDown
        ) &&
          assert(offer.left.map(_.untraced))(isLeft(equalTo(cause))) &&
          assert(offerAll.left.map(_.untraced))(isLeft(equalTo(cause))) &&
          assert(take.left.map(_.untraced))(isLeft(equalTo(cause))) &&
          assert(takeAll.left.map(_.untraced))(isLeft(equalTo(cause))) &&
          assert(takeUpTo.left.map(_.untraced))(isLeft(equalTo(cause))) &&
          assert(poll.left.map(_.untraced))(isLeft(equalTo(cause))) &&
          assert(size.left.map(_.untraced))(isLeft(equalTo(cause))) &&
          assert(isEmpty.left.map(_.untraced))(isLeft(equalTo(cause))) &&
          assert(isFull.left.map(_.untraced))(isLeft(equalTo(cause)))
      },
      test("shutdownCause fails suspended takers with the cause") {
        val boom  = new RuntimeException("worker failed")
        val cause = Cause.die(boom)

        for {
          queue     <- Queue.bounded[Int](2)
          f1        <- queue.take.fork
          f2        <- queue.take.fork
          _         <- waitForSize(queue, -2)
          remaining <- queue.shutdownCause(cause)
          res1      <- f1.join.sandbox.either
          res2      <- f2.join.sandbox.either
        } yield assertTrue(remaining.isEmpty) &&
          assert(res1.left.map(_.untraced))(isLeft(equalTo(cause))) &&
          assert(res2.left.map(_.untraced))(isLeft(equalTo(cause)))
      },
      test("shutdownCause drains buffered items and fails suspended back-pressured putters with the cause") {
        val boom  = new RuntimeException("worker failed")
        val cause = Cause.die(boom)

        for {
          queue     <- Queue.bounded[Int](2)
          _         <- queue.offerAll(Chunk(10, 20))
          f1        <- queue.offer(30).fork
          f2        <- queue.offerAll(Chunk(40, 50)).fork
          _         <- waitForSize(queue, 5)
          remaining <- queue.shutdownCause(cause)
          res1      <- f1.join.sandbox.either
          res2      <- f2.join.sandbox.either
        } yield assertTrue(remaining == Chunk(10, 20)) &&
          assert(res1.left.map(_.untraced))(isLeft(equalTo(cause))) &&
          assert(res2.left.map(_.untraced))(isLeft(equalTo(cause)))
      },
      test(
        "shutdownCause during partially enqueued back-pressured offerAll drains fitted prefix and fails fiber with the cause"
      ) {
        val boom  = new RuntimeException("partial offerAll shutdown")
        val cause = Cause.die(boom)

        for {
          queue     <- Queue.bounded[Int](2)
          fiber     <- queue.offerAll(Chunk(1, 2, 3, 4)).fork
          _         <- waitForSize(queue, 4)
          remaining <- queue.shutdownCause(cause)
          res       <- fiber.join.sandbox.either
        } yield assertTrue(remaining == Chunk(1, 2)) &&
          assert(res.left.map(_.untraced))(isLeft(equalTo(cause)))
      },
      test("shutdownCause is atomic under concurrent invocation and only the winner drains items and sets the cause") {
        val cause1 = Cause.die(new RuntimeException("first cause"))
        val cause2 = Cause.die(new RuntimeException("second cause"))

        for {
          queue      <- Queue.unbounded[Int]
          _          <- queue.offerAll(Chunk(1, 2, 3))
          pair       <- queue.shutdownCause(cause1).zipPar(queue.shutdownCause(cause2))
          (r1, r2)    = pair
          _          <- queue.shutdown
          take       <- queue.take.sandbox.either
          failedCause = take.left.map(_.untraced)
        } yield assertTrue(
          (r1 == Chunk(1, 2, 3) && r2.isEmpty && failedCause == Left(cause1)) ||
            (r2 == Chunk(1, 2, 3) && r1.isEmpty && failedCause == Left(cause2))
        )
      } @@ exceptJS(nonFlaky),
      test(
        "shutdownCause racing with concurrent offers on bounded queue preserves exact accepted == drained invariant"
      ) {
        val cause = Cause.die(new RuntimeException("concurrent shutdown"))

        for {
          queue       <- Queue.bounded[Int](64)
          offerFibers <- ZIO.foreach(1 to 32)(i => queue.offer(i).sandbox.either.fork)
          drained     <- queue.shutdownCause(cause)
          results     <- ZIO.foreach(offerFibers)(_.join)
          accepted     = (1 to 32).zip(results).collect { case (i, Right(true)) => i }.toSet
          rejected     = (1 to 32).zip(results).collect { case (i, Left(_)) => i }.toSet
          drainedSet   = drained.toSet
        } yield assertTrue(
          accepted == drainedSet,
          rejected.intersect(drainedSet).isEmpty,
          accepted.size + rejected.size == 32
        )
      } @@ exceptJS(nonFlaky),
      test("shutdownCause racing with back-pressured offers and concurrent takers never double-owns or loses items") {
        val cause = Cause.die(new RuntimeException("backpressured shutdown"))

        for {
          queue       <- Queue.bounded[Int](4)
          offerFibers <- ZIO.foreach(1 to 24)(i => queue.offer(i).sandbox.either.fork)
          takeFibers  <- ZIO.foreach(1 to 4)(_ => queue.take.sandbox.either.fork)
          drained     <- queue.shutdownCause(cause)
          offerRes    <- ZIO.foreach(offerFibers)(_.join)
          takeRes     <- ZIO.foreach(takeFibers)(_.join)
          accepted     = (1 to 24).zip(offerRes).collect { case (i, Right(true)) => i }.toSet
          rejected     = (1 to 24).zip(offerRes).collect { case (i, Left(_)) => i }.toSet
          taken        = takeRes.collect { case Right(i) => i }.toSet
          drainedSet   = drained.toSet
        } yield assertTrue(
          accepted == (taken ++ drainedSet),
          taken.intersect(drainedSet).isEmpty,
          rejected.intersect(drainedSet).isEmpty,
          rejected.intersect(taken).isEmpty,
          accepted.size + rejected.size == 24
        )
      } @@ exceptJS(nonFlaky),
      test("shutdownCause racing with concurrent offers on full sliding queue never drains rejected items") {
        val cause = Cause.die(new RuntimeException("sliding shutdown"))

        for {
          queue       <- Queue.sliding[Int](8)
          _           <- queue.offerAll(Chunk.fromIterable(1 to 8))
          offerFibers <- ZIO.foreach(9 to 40)(i => queue.offer(i).sandbox.either.fork)
          drained     <- queue.shutdownCause(cause)
          results     <- ZIO.foreach(offerFibers)(_.join)
          rejected     = (9 to 40).zip(results).collect { case (i, Left(_)) => i }.toSet
          drainedSet   = drained.toSet
        } yield assertTrue(
          rejected.intersect(drainedSet).isEmpty,
          drained.size <= 8
        )
      } @@ exceptJS(nonFlaky),
      test("shutdownCause works across unbounded, dropping, and sliding queues and composite Causes") {
        val d1    = new IllegalArgumentException("d1")
        val d2    = new IllegalStateException("d2")
        val cause = Cause.die(d1) && Cause.die(d2)

        for {
          unbounded <- Queue.unbounded[Int]
          dropping  <- Queue.dropping[Int](2)
          sliding   <- Queue.sliding[Int](2)
          _         <- unbounded.offerAll(Chunk(1, 2))
          _         <- dropping.offerAll(Chunk(1, 2, 3))
          _         <- sliding.offerAll(Chunk(1, 2, 3))
          uRem      <- unbounded.shutdownCause(cause)
          dRem      <- dropping.shutdownCause(cause)
          sRem      <- sliding.shutdownCause(cause)
          uTake     <- unbounded.take.sandbox.either
          dOffer    <- dropping.offer(4).sandbox.either
          sTakeAll  <- sliding.takeAll.sandbox.either
        } yield assertTrue(
          uRem == Chunk(1, 2),
          dRem == Chunk(1, 2),
          sRem == Chunk(2, 3)
        ) &&
          assert(uTake.left.map(_.untraced))(isLeft(equalTo(cause))) &&
          assert(dOffer.left.map(_.untraced))(isLeft(equalTo(cause))) &&
          assert(sTakeAll.left.map(_.untraced))(isLeft(equalTo(cause)))
      },
      test("shutdownCause on Enqueue and Dequeue views works from external packages and propagates the cause") {
        val cause1 = Cause.die(new RuntimeException("enqueue shutdown"))
        val cause2 = Cause.die(new RuntimeException("dequeue shutdown"))

        for {
          q1 <- Queue.bounded[Int](2)
          _  <- q1.offer(1)
          _  <- ExternalPackageShutdownCauseHelper.shutdownEnqueue(q1, cause1)
          r1 <- q1.take.sandbox.either
          q2 <- Queue.bounded[Int](2)
          _  <- q2.offer(2)
          _  <- ExternalPackageShutdownCauseHelper.shutdownDequeue(q2, cause2)
          r2 <- q2.offer(3).sandbox.either
        } yield assert(r1.left.map(_.untraced))(isLeft(equalTo(cause1))) &&
          assert(r2.left.map(_.untraced))(isLeft(equalTo(cause2)))
      },
      test("concurrent offer and shutdownCause preserves exact instance identity for equal case class values") {
        final case class Resource(id: Int)
        val cause     = Cause.die(new RuntimeException("resource identity race"))
        val instances = Chunk.fill(8)(new Resource(1))

        for {
          queue   <- Queue.bounded[Resource](16)
          fibers  <- ZIO.foreach(instances)(r => queue.offer(r).sandbox.either.map(r -> _).fork)
          rem     <- queue.shutdownCause(cause)
          results <- ZIO.foreach(fibers)(_.join)
          accepted = results.collect { case (r, Right(true)) => r }
          failedOk = results.forall {
                       case (r, Right(true)) => r.id == 1
                       case (r, Left(c))     => r.id == 1 && c.untraced == cause
                       case _                => false
                     }
          exactRefMatch = accepted.length == rem.length &&
                            accepted.forall(a => rem.exists(_ eq a)) &&
                            rem.forall(d => accepted.exists(_ eq d))
        } yield assertTrue(
          failedOk,
          exactRefMatch
        )
      } @@ TestAspect.nonFlaky(100),
      test(
        "concurrent offer/offerAll and shutdownCause with boxed Int values outside cache (> 127) never loses or duplicates elements"
      ) {
        val cause  = Cause.die(new RuntimeException("boxed int race"))
        val single = Chunk.fromIterable(10000 until 10008)
        val batch  = Chunk.fromIterable(20000 until 20008)

        for {
          queue          <- Queue.bounded[Int](32)
          offerFibers    <- ZIO.foreach(single)(n => queue.offer(n).sandbox.either.map(n -> _).fork)
          batchFiber     <- queue.offerAll(batch).sandbox.either.fork
          rem            <- queue.shutdownCause(cause)
          offerRes       <- ZIO.foreach(offerFibers)(_.join)
          batchRes       <- batchFiber.join
          acceptedSingles = offerRes.collect { case (n, Right(true)) => n }
          acceptedBatch   = batchRes.fold(_ => Chunk.empty[Int], leftover => batch.dropRight(leftover.length))
          expected        = (acceptedSingles ++ acceptedBatch).sorted
        } yield assertTrue(
          rem.sorted == expected
        )
      } @@ TestAspect.nonFlaky(100),
      test("concurrent take and shutdownCause on unbounded queue preserves strict FIFO prefix/suffix linearizability") {
        val cause = Cause.die(new RuntimeException("unbounded fifo drain race"))
        val items = Chunk.fromIterable(1000 until 1016)

        for {
          queue      <- Queue.unbounded[Int]
          _          <- queue.offerAll(items)
          takeFibers <- ZIO.foreach(1 to 4)(_ => queue.take.sandbox.either.fork)
          rem        <- queue.shutdownCause(cause)
          takeRes    <- ZIO.foreach(takeFibers)(_.join)
          taken       = takeRes.collect { case Right(v) => v }
          failedOk = takeRes.forall {
                       case Right(_) => true
                       case Left(c)  => c.untraced == cause
                     }
        } yield assertTrue(
          failedOk,
          taken.sorted == items.take(taken.length),
          rem == items.drop(taken.length)
        )
      } @@ TestAspect.nonFlaky(100),
      test("losing concurrent shutdownCause caller waits until winner completes drain and shutdownHook") {
        val cause1 = Cause.die(new RuntimeException("winner cause"))
        val cause2 = Cause.die(new RuntimeException("loser cause"))

        for {
          queue <- Queue.unbounded[Int]
          _     <- queue.offerAll(1 to 64)
          f1    <- queue.shutdownCause(cause1).fork
          f2    <- queue.shutdownCause(cause2).fork
          r1    <- f1.join
          r2    <- f2.join
          isSd  <- queue.isShutdown
          size  <- queue.size.sandbox.either
        } yield assertTrue(
          isSd,
          (r1 == Chunk.fromIterable(1 to 64) && r2.isEmpty) || (r2 == Chunk.fromIterable(1 to 64) && r1.isEmpty),
          size.isLeft
        )
      } @@ TestAspect.nonFlaky(100),
      test("shutdownCause on one queue never spins or blocks on in-flight polling of an unrelated queue") {
        val cause = Cause.die(new RuntimeException("isolated queue shutdown"))

        for {
          queueA   <- Queue.bounded[Int](16)
          queueB   <- Queue.unbounded[Int]
          _        <- queueA.offerAll(1 to 8)
          _        <- queueB.offerAll(1 to 1024)
          pollerB  <- (queueB.poll *> queueB.offer(1)).forever.fork
          drainedA <- queueA.shutdownCause(cause)
          _        <- pollerB.interrupt
          _        <- queueB.shutdown
        } yield assertTrue(
          drainedA == Chunk.fromIterable(1 to 8)
        )
      } @@ TestAspect.nonFlaky(50),
      test("concurrent offer and offerAll with identical boxed Int values on bounded(1) never loses accepted items") {
        val cause = Cause.die(new RuntimeException("identical boxed int surplus race"))
        val value = 6

        if (TestPlatform.isJVM) {
          ZIO.suspendSucceed {
            val entered   = new java.util.concurrent.CountDownLatch(2)
            val drainedL  = new java.util.concurrent.CountDownLatch(1)
            val enqueued1 = new java.util.concurrent.CountDownLatch(1)
            val surplus2  = new java.util.concurrent.CountDownLatch(1)

            val iterable1 = new Iterable[Int] {
              override def dropWhile(p: Int => Boolean): Iterable[Int] = this
              override def isEmpty: Boolean = {
                entered.countDown()
                drainedL.await(5, java.util.concurrent.TimeUnit.SECONDS)
                false
              }
              override def size: Int = 1
              override def iterator: Iterator[Int] = new Iterator[Int] {
                private var step = 0
                override def hasNext: Boolean = {
                  step += 1
                  if (step == 1) true
                  else if (step == 2) {
                    enqueued1.countDown()
                    surplus2.await(5, java.util.concurrent.TimeUnit.SECONDS)
                    false
                  } else false
                }
                override def next(): Int = value
              }
            }

            val iterable2 = new Iterable[Int] {
              override def dropWhile(p: Int => Boolean): Iterable[Int] = this
              override def isEmpty: Boolean = {
                entered.countDown()
                enqueued1.await(5, java.util.concurrent.TimeUnit.SECONDS)
                false
              }
              override def size: Int               = 1
              override def iterator: Iterator[Int] = Iterator.single(value)
            }

            for {
              queue <- Queue.bounded[Int](1)
              f1    <- ZIO.blocking(queue.offerAll(iterable1).sandbox.either).fork
              f2 <- ZIO
                      .blocking(
                        queue
                          .offerAll(iterable2)
                          .map { rem =>
                            surplus2.countDown()
                            rem
                          }
                          .tapErrorCause(_ => ZIO.succeed(surplus2.countDown()))
                          .sandbox
                          .either
                      )
                      .fork
              _       <- ZIO.blocking(ZIO.succeed(entered.await(5, java.util.concurrent.TimeUnit.SECONDS)))
              drained <- queue.shutdownCause(cause)
              _       <- ZIO.succeed(drainedL.countDown())
              res1    <- f1.join
              res2    <- f2.join
              allRes   = List(res1, res2)
              totalAccepted = allRes.foldLeft(0) {
                                case (acc, Right(rem)) => acc + (1 - rem.length)
                                case (acc, Left(_))    => acc
                              }
              allErrorsMatch = allRes.forall {
                                 case Right(_) => true
                                 case Left(c)  => c.untraced == cause
                               }
            } yield assertTrue(
              allErrorsMatch,
              totalAccepted == drained.length
            )
          }
        } else {
          for {
            queue      <- Queue.bounded[Int](1)
            singleWork <- ZIO.foreach(1 to 12)(_ => queue.offer(value).sandbox.either.fork)
            batchWork  <- ZIO.foreach(1 to 4)(_ => queue.offerAll(Chunk.fill(3)(value)).sandbox.either.fork)
            drained    <- queue.shutdownCause(cause)
            singleRes  <- ZIO.foreach(singleWork)(_.join)
            batchRes   <- ZIO.foreach(batchWork)(_.join)
            acceptedSingles = singleRes.count {
                                case Right(true) => true
                                case _           => false
                              }
            acceptedBatches = batchRes.foldLeft(0) {
                                case (acc, Right(rem)) => acc + (3 - rem.length)
                                case (acc, Left(_))    => acc
                              }
            totalAccepted = acceptedSingles + acceptedBatches
            allSingleErrorsMatch = singleRes.forall {
                                     case Right(_) => true
                                     case Left(c)  => c.untraced == cause
                                   }
            allBatchErrorsMatch = batchRes.forall {
                                    case Right(_) => true
                                    case Left(c)  => c.untraced == cause
                                  }
          } yield assertTrue(
            allSingleErrorsMatch,
            allBatchErrorsMatch,
            totalAccepted == drained.length
          )
        }
      } @@ exceptJS(nonFlaky(10)),
      test("takeBetween(0, max) fails with shutdown cause when queue is already shut down") {
        val cause = Cause.die(new RuntimeException("takeBetween zero min"))

        for {
          queue   <- Queue.bounded[Int](4)
          _       <- queue.shutdownCause(cause)
          resZero <- queue.takeBetween(0, 2).sandbox.either
          resNeg  <- queue.takeBetween(-1, 2).sandbox.either
        } yield assertTrue(
          resZero == Left(cause),
          resNeg == Left(cause)
        )
      }
    )
  }
}

package zioqueueexternal {
  import zio.{Cause, Dequeue, Enqueue, Trace, UIO}

  object ExternalPackageShutdownCauseHelper {
    def shutdownEnqueue[A](enqueue: Enqueue[A], cause: Cause[Nothing])(implicit trace: Trace): UIO[Unit] =
      enqueue.shutdownCause(cause)

    def shutdownDequeue[A](dequeue: Dequeue[A], cause: Cause[Nothing])(implicit trace: Trace): UIO[Unit] =
      dequeue.shutdownCause(cause)
  }
}
