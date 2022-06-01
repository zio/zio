// package zio.internal

// import zio.test._
// // import zio.test.TestAspect.ignore
// import zio.internal.zio2.FiberRuntime

// object NewEncodingSpec extends zio.ZIOBaseSpec {
//   import zio.{Promise => _, _}

//   import zio2.{ZIO => Effect}

//   def newFib(n: Int): Effect[Any, Nothing, Int] =
//     if (n <= 1) Effect.succeed(n)
//     else
//       for {
//         a <- newFib(n - 1)
//         b <- newFib(n - 2)
//       } yield a + b

//   def oldFib(n: Int): ZIO[Any, Nothing, Int] =
//     if (n <= 1) ZIO.succeed(n)
//     else
//       for {
//         a <- oldFib(n - 1)
//         b <- oldFib(n - 2)
//       } yield a + b

//   def runFibTest(num: Int, maxDepth: Int = 1000) =
//     test(s"fib(${num})") {
//       for {
//         actual   <- ZIO.fromFuture(_ => newFib(num).unsafeRunToFuture(maxDepth))
//         expected <- oldFib(num)
//       } yield assertTrue(actual == expected)
//     }

//   def newSum(n: Int): Effect[Any, Nothing, Int] =
//     Effect.succeed(n).flatMap(n => if (n <= 0) Effect.succeed(0) else newSum(n - 1).map(_ + n))

//   def oldSum(n: Int): ZIO[Any, Nothing, Int] =
//     ZIO.succeed(n).flatMap(n => if (n <= 0) ZIO.succeed(0) else oldSum(n - 1).map(_ + n))

//   def runSumTest(num: Int, maxDepth: Int = 1000) =
//     test(s"sum(${num})") {
//       for {
//         actual   <- ZIO.fromFuture(_ => newSum(num).unsafeRunToFuture(maxDepth))
//         expected <- oldSum(num)
//       } yield assertTrue(actual == expected)
//     }

//   final case class Failed(value: Int) extends Exception

//   def newFailAfter(n: Int): Effect[Any, Nothing, Int] = {
//     def runLoop(i: Int): Effect[Any, Failed, Nothing] =
//       if (i >= n) Effect.fail(Failed(i))
//       else Effect.succeed(i).flatMap(j => runLoop(j + 1))

//     runLoop(0).catchAll { case Failed(i) =>
//       Effect.succeed(i)
//     }
//   }

//   def oldFailAfter(n: Int): ZIO[Any, Nothing, Int] = {
//     def runLoop(i: Int): ZIO[Any, Throwable, Nothing] =
//       if (i >= n) ZIO.fail(Failed(i))
//       else ZIO.succeed(i).flatMap(j => runLoop(j + 1))

//     runLoop(0).catchAll {
//       case Failed(i) => ZIO.succeed(i)
//       case _         => ???
//     }
//   }

//   def runFailAfterTest(num: Int, maxDepth: Int = 1000) =
//     test(s"failAfter(${num})") {
//       for {
//         actual   <- ZIO.fromFuture(_ => newFailAfter(num).unsafeRunToFuture(maxDepth))
//         expected <- oldFailAfter(num)
//       } yield assertTrue(actual == expected)
//     }

//   def newAsyncAfter(n: Int): Effect[Any, Nothing, Int] = {
//     def runLoop(i: Int): Effect[Any, Nothing, Int] =
//       if (i >= n) Effect.async[Any, Nothing, Int](k => k(Effect.succeed(i)))
//       else Effect.succeed(i).flatMap(j => runLoop(j + 1)).map(_ + i)

//     runLoop(0)
//   }

//   def oldAsyncAfter(n: Int): ZIO[Any, Nothing, Int] = {
//     def runLoop(i: Int): ZIO[Any, Nothing, Int] =
//       if (i >= n) ZIO.async[Any, Nothing, Int](k => k(ZIO.succeed(i)))
//       else ZIO.succeed(i).flatMap(j => runLoop(j + 1)).map(_ + i)

//     runLoop(0)
//   }

//   def newTerminalFail(n: Int): Effect[Any, Nothing, Exit[Failed, Int]] = {
//     def runLoop(i: Int): Effect[Any, Failed, Nothing] =
//       if (i >= n) Effect.fail(Failed(i))
//       else Effect.succeed(i).flatMap(j => runLoop(j + 1))

//     runLoop(0).exit
//   }

//   def oldTerminalFail(n: Int): ZIO[Any, Nothing, Exit[Failed, Int]] = {
//     def runLoop(i: Int): ZIO[Any, Failed, Nothing] =
//       if (i >= n) ZIO.fail(Failed(i))
//       else ZIO.succeed(i).flatMap(j => runLoop(j + 1))

//     runLoop(0).exit
//   }

//   def runTerminalFailTest(num: Int, maxDepth: Int = 1000) =
//     test(s"terminalFail(${num})") {
//       for {
//         actual   <- ZIO.fromFuture(_ => newTerminalFail(num).unsafeRunToFuture(maxDepth))
//         expected <- oldTerminalFail(num)
//       } yield assertTrue(actual == expected)
//     }

//   def runAsyncAfterTest(num: Int, maxDepth: Int = 1000) =
//     test(s"asyncAfter(${num})") {
//       for {
//         actual   <- ZIO.fromFuture(_ => newAsyncAfter(num).unsafeRunToFuture(maxDepth))
//         expected <- oldAsyncAfter(num)
//       } yield assertTrue(actual == expected)
//     }

//   def secondLevelCallStack =
//     for {
//       _ <- Effect.succeed(10)
//       _ <- Effect.succeed(20)
//       _ <- Effect.succeed(30)
//       t <- Effect.trace
//     } yield t

//   def firstLevelCallStack =
//     for {
//       _ <- Effect.succeed(10)
//       _ <- Effect.succeed(20)
//       _ <- Effect.succeed(30)
//       t <- secondLevelCallStack
//     } yield t

//   def stackTraceTest1 =
//     for {
//       _ <- Effect.succeed(10)
//       _ <- Effect.succeed(20)
//       _ <- Effect.succeed(30)
//       t <- firstLevelCallStack
//     } yield t

//   def secondLevelCallStackFail =
//     for {
//       _ <- Effect.succeed(10)
//       _ <- Effect.succeed(20)
//       _ <- Effect.succeed(30)
//       t <- Effect.fail("Uh oh!")
//     } yield t

//   def firstLevelCallStackFail =
//     for {
//       _ <- Effect.succeed(10)
//       _ <- Effect.succeed(20)
//       _ <- Effect.succeed(30)
//       t <- secondLevelCallStackFail
//     } yield t

//   def stackTraceTest2 =
//     for {
//       _ <- Effect.succeed(10)
//       _ <- Effect.succeed(20)
//       _ <- Effect.succeed(30)
//       t <- firstLevelCallStackFail
//     } yield t

//   def spec =
//     suite("NewEncodingSpec") {
//       suite("stack traces") {
//         test("2nd-level trace") {
//           for {
//             t <- ZIO.fromFuture(_ => stackTraceTest1.unsafeRunToFuture())
//           } yield assertTrue(t.size == 3) &&
//             assertTrue(t.stackTrace(0).toString().contains("secondLevelCallStack")) &&
//             assertTrue(t.stackTrace(1).toString().contains("firstLevelCallStack")) &&
//             assertTrue(t.stackTrace(2).toString().contains("stackTraceTest1"))
//         } +
//           test("2nd-level auto-trace through fail") {
//             for {
//               exit <- ZIO.fromFuture(_ => stackTraceTest2.exit.unsafeRunToFuture())
//               t     = exit.causeOption.get.trace
//             } yield assertTrue(t.size == 4) &&
//               assertTrue(t.stackTrace(0).toString().contains("secondLevelCallStackFail")) &&
//               assertTrue(t.stackTrace(1).toString().contains("firstLevelCallStackFail")) &&
//               assertTrue(t.stackTrace(2).toString().contains("stackTraceTest2")) &&
//               assertTrue(t.stackTrace(3).toString().contains("spec"))
//           }
//       } +
//         suite("fib") {
//           runFibTest(0) +
//             runFibTest(5) +
//             runFibTest(10) +
//             runFibTest(20)
//         } +
//         suite("fib - trampoline stress") {
//           runFibTest(0, 2) +
//             runFibTest(5, 2) +
//             runFibTest(10, 2) +
//             runFibTest(20, 2)
//         } +
//         suite("sum") {
//           runSumTest(0) +
//             runSumTest(100) +
//             runSumTest(1000) +
//             runSumTest(10000)
//         } +
//         suite("sum - trampoline stress") {
//           runSumTest(0, 2) +
//             runSumTest(100, 2) +
//             runSumTest(1000, 2) +
//             runSumTest(10000, 2)
//         } +
//         suite("failAfter") {
//           runFailAfterTest(0) +
//             runFailAfterTest(100) +
//             runFailAfterTest(1000) +
//             runFailAfterTest(10000)
//         } +
//         suite("failAfter - trampoline stress") {
//           runFailAfterTest(0, 2) +
//             runFailAfterTest(100, 2) +
//             runFailAfterTest(1000, 2) +
//             runFailAfterTest(10000, 2)
//         } +
//         suite("asyncAfter") {
//           runAsyncAfterTest(0) +
//             runAsyncAfterTest(100) +
//             runAsyncAfterTest(1000) +
//             runAsyncAfterTest(10000)
//         } +
//         suite("asyncAfter - trampoline stress") {
//           runAsyncAfterTest(0, 2) +
//             runAsyncAfterTest(100, 2) +
//             runAsyncAfterTest(1000, 2) +
//             runAsyncAfterTest(10000, 2)
//         } +
//         suite("terminalFail") {
//           runTerminalFailTest(0) +
//             runTerminalFailTest(100) +
//             runTerminalFailTest(1000) +
//             runTerminalFailTest(10000)
//         } +
//         suite("terminalFail - trampoline stress") {
//           runTerminalFailTest(0, 2) +
//             runTerminalFailTest(100, 2) +
//             runTerminalFailTest(1000, 2) +
//             runTerminalFailTest(10000, 2)
//         } +
//         suite("defects") {
//           test("death in succeed") {
//             for {
//               result <- ZIO.fromFuture(_ => Effect.succeed(throw TestException).exit.unsafeRunToFuture())
//             } yield assertTrue(result.causeOption.get.defects(0) == TestException)
//           } +
//             test("death in succeed after async") {
//               for {
//                 result <-
//                   ZIO.fromFuture(_ =>
//                     (Effect.unit *> Effect.yieldNow *> Effect.succeed(throw TestException)).exit.unsafeRunToFuture()
//                   )
//               } yield assertTrue(result.causeOption.get.defects(0) == TestException)
//             }
//         } +
//         suite("interruption") {
//           test("simple interruption of never") {
//             val never = Effect.async[Any, Nothing, Int](_ => ())

//             val fiber = FiberRuntime(FiberId.unsafeMake(Trace.empty), FiberRefs.empty)

//             for {
//               fiberId  <- ZIO.fiberId
//               executor <- ZIO.blockingExecutor
//               _        <- ZIO.succeed(executor.unsafeSubmitOrThrow { () => fiber.outerRunLoop(never, Chunk.empty, 1000); () })
//               exit     <- ZIO.fromFuture(_ => fiber.interruptAs(fiberId).unsafeRunToFuture())
//             } yield assertTrue(exit.isInterrupted)
//           }
//         } +
//         suite("finalizers") {
//           test("ensuring - success") {
//             var finalized = false

//             val finalize = Effect.succeed { finalized = true }

//             for {
//               _ <- ZIO.fromFuture(_ => Effect.succeed(()).ensuring(finalize).exit.unsafeRunToFuture())
//             } yield assertTrue(finalized == true)
//           } +
//             test("ensuring - success after async") {
//               var finalized = false

//               val finalize = Effect.succeed { finalized = true }

//               for {
//                 _ <-
//                   ZIO.fromFuture(_ =>
//                     (Effect.unit *> Effect.yieldNow *> Effect.succeed(())).ensuring(finalize).exit.unsafeRunToFuture()
//                   )
//               } yield assertTrue(finalized == true)
//             } +
//             test("ensuring - failure") {
//               var finalized = false

//               val finalize = Effect.succeed { finalized = true }

//               for {
//                 _ <- ZIO.fromFuture(_ => Effect.fail(()).ensuring(finalize).exit.unsafeRunToFuture())
//               } yield assertTrue(finalized == true)
//             } +
//             test("ensuring - failure after async") {
//               var finalized = false

//               val finalize = Effect.succeed { finalized = true }

//               for {
//                 _ <- ZIO.fromFuture(_ =>
//                        (Effect.unit *> Effect.yieldNow *> Effect.fail(())).ensuring(finalize).exit.unsafeRunToFuture()
//                      )
//               } yield assertTrue(finalized == true)
//             } +
//             test("ensuring - double failure") {
//               var finalized = false

//               val finalize1 = Effect.succeed(throw TestException)
//               val finalize2 = Effect.succeed { finalized = true }

//               for {
//                 _ <- ZIO.fromFuture(_ =>
//                        Effect
//                          .fail(())
//                          .ensuring(finalize1)
//                          .ensuring(finalize2)
//                          .exit
//                          .unsafeRunToFuture()
//                      )
//               } yield assertTrue(finalized == true)
//             } +
//             test("foldCauseZIO finalization - success") {
//               var finalized = false

//               val finalize = Effect.succeed { finalized = true }

//               for {
//                 _ <- ZIO.fromFuture(_ =>
//                        Effect.succeed(()).foldCauseZIO(_ => finalize, _ => finalize).exit.unsafeRunToFuture()
//                      )
//               } yield assertTrue(finalized == true)
//             } +
//             test("foldCauseZIO finalization - failure") {
//               var finalized = false

//               val finalize = Effect.succeed { finalized = true }

//               for {
//                 _ <- ZIO.fromFuture(_ =>
//                        Effect.fail(()).foldCauseZIO(_ => finalize, _ => finalize).exit.unsafeRunToFuture()
//                      )
//               } yield assertTrue(finalized == true)
//             } +
//             test("foldCauseZIO nested finalization - double failure") {
//               var finalized = false

//               val finalize1 = Effect.succeed(throw TestException)
//               val finalize2 = Effect.succeed { finalized = true }

//               for {
//                 _ <- ZIO.fromFuture(_ =>
//                        Effect
//                          .fail(())
//                          .foldCauseZIO(_ => finalize1, _ => finalize1)
//                          .foldCauseZIO(_ => finalize2, _ => finalize2)
//                          .exit
//                          .unsafeRunToFuture()
//                      )
//               } yield assertTrue(finalized == true)
//             }
//         }
//     }
// }

// object TestException extends Exception("Test exception")
