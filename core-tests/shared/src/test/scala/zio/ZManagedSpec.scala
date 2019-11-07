package zio

import zio.Cause.Interrupt
import zio.duration._
import zio.Exit.Failure
import zio.test.{ Gen, testM, _ }
import zio.test.Assertion._
import zio.test.environment._

object ZManagedSpec
    extends ZIOBaseSpec(
      suite("ZManaged")(
        suite("preallocate")(
          testM("runs finalizer on interruption") {
            for {
              ref    <- Ref.make(0)
              res    = ZManaged.reserve(Reservation(ZIO.interrupt, _ => ref.update(_ + 1)))
              _      <- res.preallocate.run.ignore
              result <- assertM(ref.get, equalTo(1))
            } yield result
          },
          testM("runs finalizer on interruption") {
            for {
              ref    <- Ref.make(0)
              res    = ZManaged.reserve(Reservation(ZIO.interrupt, _ => ref.update(_ + 1)))
              _      <- res.preallocate.run.ignore
              result <- assertM(ref.get, equalTo(1))
            } yield result
          },
          testM("runs finalizer when resource closes") {
            for {
              ref    <- Ref.make(0)
              res    = ZManaged.reserve(Reservation(ZIO.unit, _ => ref.update(_ + 1)))
              _      <- res.preallocate.flatMap(_.use_(ZIO.unit))
              result <- assertM(ref.get, equalTo(1))
            } yield result
          },
          testM("propagates failures in acquire") {
            for {
              exit <- ZManaged.fromEffect(ZIO.fail("boom")).preallocate.either
            } yield assert(exit, isLeft(equalTo("boom")))
          },
          testM("propagates failures in reserve") {
            for {
              exit <- ZManaged.make(ZIO.fail("boom"))(_ => ZIO.unit).preallocate.either
            } yield assert(exit, isLeft(equalTo("boom")))
          }
        ),
        suite("make")(
          testM("Invokes cleanups in reverse order of acquisition.") {
            for {
              effects <- Ref.make[List[Int]](Nil)
              res     = (x: Int) => ZManaged.make(effects.update(x :: _))(_ => effects.update(x :: _))
              program = res(1) *> res(2) *> res(3)
              values  <- program.use_(ZIO.unit) *> effects.get
            } yield assert(values, equalTo(List(1, 2, 3, 3, 2, 1)))
          },
          testM("Properly performs parallel acquire and release") {
            for {
              log      <- Ref.make[List[String]](Nil)
              a        = ZManaged.make(UIO.succeed("A"))(_ => log.update("A" :: _))
              b        = ZManaged.make(UIO.succeed("B"))(_ => log.update("B" :: _))
              result   <- a.zipWithPar(b)(_ + _).use(ZIO.succeed)
              cleanups <- log.get
            } yield assert(result.length, equalTo(2)) && assert(cleanups, hasSize(equalTo(2)))
          },
          testM("Constructs an uninterruptible Managed value") {
            ZManagedSpecUtil.doInterrupt(io => ZManaged.make(io)(_ => IO.unit), None)
          }
        ),
        suite("makeEffect")(
          testM("Invokes cleanups in reverse order of acquisition.") {
            var effects               = List[Int]()
            def acquire(x: Int): Int  = { effects = x :: effects; x }
            def release(x: Int): Unit = effects = x :: effects

            val res     = (x: Int) => ZManaged.makeEffect(acquire(x))(release)
            val program = res(1) *> res(2) *> res(3)

            for {
              _ <- program.use_(ZIO.unit)
            } yield assert(effects, equalTo(List(1, 2, 3, 3, 2, 1)))
          }
        ),
        suite("reserve")(
          testM("Interruption is possible when using this form") {
            ZManagedSpecUtil
              .doInterrupt(io => ZManaged.reserve(Reservation(io, _ => IO.unit)), Some(Failure(Interrupt)))
          }
        ),
        suite("makeExit")(
          testM("Invokes with the failure of the use") {
            val ex = new RuntimeException("Use died")

            def res(exits: Ref[List[Exit[Any, Any]]]) =
              for {
                _ <- ZManaged.makeExit(UIO.unit)((_, e) => exits.update(e :: _))
                _ <- ZManaged.makeExit(UIO.unit)((_, e) => exits.update(e :: _))
              } yield ()

            for {
              exits  <- Ref.make[List[Exit[Any, Any]]](Nil)
              _      <- res(exits).use_(ZIO.die(ex)).run
              result <- exits.get
            } yield assert(
              result,
              equalTo(List[Exit[Any, Any]](Exit.Failure(Cause.Die(ex)), Exit.Failure(Cause.Die(ex))))
            )
          },
          testM("Invokes with the failure of the subsequent acquire") {
            val useEx     = new RuntimeException("Use died")
            val acquireEx = new RuntimeException("Acquire died")

            def res(exits: Ref[List[Exit[Any, Any]]]) =
              for {
                _ <- ZManaged.makeExit(UIO.unit)((_, e) => exits.update(e :: _))
                _ <- ZManaged.makeExit(ZIO.die(acquireEx))((_, e) => exits.update(e :: _))
              } yield ()

            for {
              exits  <- Ref.make[List[Exit[Any, Any]]](Nil)
              _      <- res(exits).use_(ZIO.die(useEx)).run
              result <- exits.get
            } yield assert(result, equalTo(List[Exit[Any, Any]](Exit.Failure(Cause.Die(acquireEx)))))
          }
        ),
        suite("fromEffect")(
          testM("Performed interruptibly") {
            assertM(
              ZManaged.fromEffect(ZIO.checkInterruptible(ZIO.succeed)).use(ZIO.succeed),
              equalTo(InterruptStatus.interruptible)
            )
          }
        ),
        suite("fromEffectUninterruptible")(
          testM("Performed uninterruptibly") {
            assertM(
              ZManaged.fromEffectUninterruptible(ZIO.checkInterruptible(ZIO.succeed)).use(ZIO.succeed),
              equalTo(InterruptStatus.uninterruptible)
            )
          }
        ),
        suite("ensuring")(
          testM("Runs on successes") {
            for {
              effects <- Ref.make[List[String]](Nil)
              _ <- ZManaged
                    .finalizer(effects.update("First" :: _))
                    .ensuring(effects.update("Second" :: _))
                    .use_(ZIO.unit)
              result <- effects.get
            } yield assert(result, equalTo(List("Second", "First")))
          },
          testM("Runs on failures") {
            for {
              effects <- Ref.make[List[String]](Nil)
              _       <- ZManaged.fromEffect(ZIO.fail(())).ensuring(effects.update("Ensured" :: _)).use_(ZIO.unit).either
              result  <- effects.get
            } yield assert(result, equalTo(List("Ensured")))
          },
          testM("Works when finalizers have defects") {
            for {
              effects <- Ref.make[List[String]](Nil)
              _ <- ZManaged
                    .finalizer(ZIO.dieMessage("Boom"))
                    .ensuring(effects.update("Ensured" :: _))
                    .use_(ZIO.unit)
                    .run
              result <- effects.get
            } yield assert(result, equalTo(List("Ensured")))
          }
        ),
        suite("ensuringFirst")(
          testM("Runs on successes") {
            for {
              effects <- Ref.make[List[String]](Nil)
              _ <- ZManaged
                    .finalizer(effects.update("First" :: _))
                    .ensuringFirst(effects.update("Second" :: _))
                    .use_(ZIO.unit)
              result <- effects.get
            } yield assert(result, equalTo(List("First", "Second")))
          },
          testM("Runs on failures") {
            for {
              effects <- Ref.make[List[String]](Nil)
              _       <- ZManaged.fromEffect(ZIO.fail(())).ensuringFirst(effects.update("Ensured" :: _)).use_(ZIO.unit).either
              result  <- effects.get
            } yield assert(result, equalTo(List("Ensured")))
          },
          testM("Works when finalizers have defects") {
            for {
              effects <- Ref.make[List[String]](Nil)
              _ <- ZManaged
                    .finalizer(ZIO.dieMessage("Boom"))
                    .ensuringFirst(effects.update("Ensured" :: _))
                    .use_(ZIO.unit)
                    .run
              result <- effects.get
            } yield assert(result, equalTo(List("Ensured")))
          }
        ),
        suite("flatMap")(
          testM("All finalizers run even when finalizers have defects") {
            for {
              effects <- Ref.make[List[String]](Nil)
              _ <- (for {
                    _ <- ZManaged.finalizer(ZIO.dieMessage("Boom"))
                    _ <- ZManaged.finalizer(effects.update("First" :: _))
                    _ <- ZManaged.finalizer(ZIO.dieMessage("Boom"))
                    _ <- ZManaged.finalizer(effects.update("Second" :: _))
                    _ <- ZManaged.finalizer(ZIO.dieMessage("Boom"))
                    _ <- ZManaged.finalizer(effects.update("Third" :: _))
                  } yield ()).use_(ZIO.unit).run
              result <- effects.get
            } yield assert(result, equalTo(List("First", "Second", "Third")))
          }
        ),
        suite("foldM")(
          testM("Runs onFailure on failure") {
            for {
              effects <- Ref.make[List[Int]](Nil)
              res     = (x: Int) => Managed.make(effects.update(x :: _).unit)(_ => effects.update(x :: _))
              program = Managed.fromEffect(IO.fail(())).foldM(_ => res(1), _ => Managed.unit)
              values  <- program.use_(ZIO.unit).ignore *> effects.get
            } yield assert(values, equalTo(List(1, 1)))
          },
          testM("Runs onSuccess on success") {
            import zio.CanFail.canFail
            for {
              effects <- Ref.make[List[Int]](Nil)
              res     = (x: Int) => Managed.make(effects.update(x :: _).unit)(_ => effects.update(x :: _))
              program = ZManaged.succeed(()).foldM(_ => Managed.unit, _ => res(1))
              values  <- program.use_(ZIO.unit).ignore *> effects.get
            } yield assert(values, equalTo(List(1, 1)))
          },
          testM("Invokes cleanups") {
            for {
              effects <- Ref.make[List[Int]](Nil)
              res     = (x: Int) => Managed.make(effects.update(x :: _).unit)(_ => effects.update(x :: _))
              program = res(1).flatMap(_ => ZManaged.fail(())).foldM(_ => res(2), _ => res(3))
              values  <- program.use_(ZIO.unit).ignore *> effects.get
            } yield assert(values, equalTo(List(1, 2, 2, 1)))
          },
          testM("Invokes cleanups on interrupt - 1") {
            import zio.CanFail.canFail
            for {
              effects <- Ref.make[List[Int]](Nil)
              res     = (x: Int) => Managed.make(effects.update(x :: _).unit)(_ => effects.update(x :: _))
              program = res(1).flatMap(_ => ZManaged.interrupt).foldM(_ => res(2), _ => res(3))
              values  <- program.use_(ZIO.unit).sandbox.ignore *> effects.get
            } yield assert(values, equalTo(List(1, 1)))
          },
          testM("Invokes cleanups on interrupt - 2") {
            for {
              effects <- Ref.make[List[Int]](Nil)
              res     = (x: Int) => Managed.make(effects.update(x :: _).unit)(_ => effects.update(x :: _))
              program = res(1).flatMap(_ => ZManaged.fail(())).foldM(_ => res(2), _ => res(3))
              values  <- program.use_(ZIO.interrupt).sandbox.ignore *> effects.get
            } yield assert(values, equalTo(List(1, 2, 2, 1)))
          },
          testM("Invokes cleanups on interrupt - 3") {
            for {
              effects <- Ref.make[List[Int]](Nil)
              res     = (x: Int) => Managed.make(effects.update(x :: _).unit)(_ => effects.update(x :: _))
              program = res(1).flatMap(_ => ZManaged.fail(())).foldM(_ => res(2) *> ZManaged.interrupt, _ => res(3))
              values  <- program.use_(ZIO.unit).sandbox.ignore *> effects.get
            } yield assert(values, equalTo(List(1, 2, 2, 1)))
          }
        ),
        suite("foreach")(
          testM("Returns elements in the correct order") {
            def res(int: Int) =
              ZManaged.succeed(int)

            val managed = ZManaged.foreach(List(1, 2, 3, 4))(res)
            managed.use[Any, Nothing, TestResult](res => ZIO.succeed(assert(res, equalTo(List(1, 2, 3, 4)))))
          },
          testM("Runs finalizers") {
            ZManagedSpecUtil.testFinalizersPar(4, res => ZManaged.foreach(List(1, 2, 3, 4))(_ => res))
          },
          testM("Invokes cleanups in reverse order of acquisition") {
            for {
              effects <- Ref.make[List[Int]](Nil)
              res     = (x: Int) => ZManaged.make(effects.update(x :: _).unit)(_ => effects.update(x :: _))
              program = ZManaged.foreach(List(1, 2, 3))(res)
              values  <- program.use_(ZIO.unit) *> effects.get
            } yield assert(values, equalTo(List(1, 2, 3, 3, 2, 1)))
          }
        ),
        suite("foreachPar")(
          testM("Returns elements in the correct order") {
            def res(int: Int) =
              ZManaged.succeed(int)

            val managed = ZManaged.foreachPar(List(1, 2, 3, 4))(res)
            managed.use[Any, Nothing, TestResult](res => ZIO.succeed(assert(res, equalTo(List(1, 2, 3, 4)))))
          },
          testM("Runs finalizers") {
            ZManagedSpecUtil.testFinalizersPar(4, res => ZManaged.foreachPar(List(1, 2, 3, 4))(_ => res))
          },
          testM("Runs reservations in parallel") {
            ZManagedSpecUtil.testReservePar(4, res => ZManaged.foreachPar(List(1, 2, 3, 4))(_ => res))
          },
          testM("Runs acquisitions in parallel") {
            ZManagedSpecUtil.testAcquirePar(4, res => ZManaged.foreachPar(List(1, 2, 3, 4))(_ => res))
          }
        ),
        suite("foreachParN")(
          testM("Returns elements in the correct order") {
            def res(int: Int) =
              ZManaged.succeed(int)

            val managed = ZManaged.foreachParN(2)(List(1, 2, 3, 4))(res)
            managed.use[Any, Nothing, TestResult](res => ZIO.succeed(assert(res, equalTo(List(1, 2, 3, 4)))))
          },
          testM("Uses at most n fibers for reservation") {
            ZManagedSpecUtil.testFinalizersPar(4, res => ZManaged.foreachParN(2)(List(1, 2, 3, 4))(_ => res))
          },
          testM("Uses at most n fibers for acquisition") {
            ZManagedSpecUtil.testReservePar(2, res => ZManaged.foreachParN(2)(List(1, 2, 3, 4))(_ => res))
          },
          testM("Runs finalizers") {
            ZManagedSpecUtil.testAcquirePar(2, res => ZManaged.foreachParN(2)(List(1, 2, 3, 4))(_ => res))
          }
        ),
        suite("foreach_")(
          testM("Runs finalizers") {
            ZManagedSpecUtil.testFinalizersPar(4, res => ZManaged.foreach_(List(1, 2, 3, 4))(_ => res))
          }
        ),
        suite("foreachPar_")(
          testM("Runs finalizers") {
            ZManagedSpecUtil.testFinalizersPar(4, res => ZManaged.foreachPar_(List(1, 2, 3, 4))(_ => res))
          },
          testM("Runs reservations in parallel") {
            ZManagedSpecUtil.testReservePar(4, res => ZManaged.foreachPar_(List(1, 2, 3, 4))(_ => res))
          },
          testM("Runs acquisitions in parallel") {
            ZManagedSpecUtil.testAcquirePar(4, res => ZManaged.foreachPar_(List(1, 2, 3, 4))(_ => res))
          }
        ),
        suite("foreachParN_")(
          testM("Uses at most n fibers for reservation") {
            ZManagedSpecUtil.testFinalizersPar(4, res => ZManaged.foreachParN_(2)(List(1, 2, 3, 4))(_ => res))
          },
          testM("Uses at most n fibers for acquisition") {
            ZManagedSpecUtil.testReservePar(2, res => ZManaged.foreachParN_(2)(List(1, 2, 3, 4))(_ => res))
          },
          testM("Runs finalizers") {
            ZManagedSpecUtil.testReservePar(2, res => ZManaged.foreachParN_(2)(List(1, 2, 3, 4))(_ => res))
          }
        ),
        suite("fork")(
          testM("Runs finalizers properly") {
            for {
              finalized <- Ref.make(false)
              latch     <- Promise.make[Nothing, Unit]
              _ <- ZManaged
                    .reserve(Reservation(latch.succeed(()) *> ZIO.never, _ => finalized.set(true)))
                    .fork
                    .use_(latch.await)
              result <- finalized.get
            } yield assert(result, isTrue)
          },
          testM("Acquires interruptibly") {
            for {
              finalized    <- Ref.make(false)
              acquireLatch <- Promise.make[Nothing, Unit]
              useLatch     <- Promise.make[Nothing, Unit]
              fib <- ZManaged
                      .reserve(
                        Reservation(
                          acquireLatch.succeed(()) *> ZIO.never,
                          _ => finalized.set(true)
                        )
                      )
                      .fork
                      .use_(useLatch.succeed(()) *> ZIO.never)
                      .fork
              _      <- acquireLatch.await
              _      <- useLatch.await
              _      <- fib.interrupt
              result <- finalized.get
            } yield assert(result, isTrue)
          }
        ),
        suite("fromAutoCloseable")(
          testM("Runs finalizers properly") {
            for {
              runtime <- ZIO.runtime[Any]
              effects <- Ref.make(List[String]())
              closeable = UIO(new AutoCloseable {
                def close(): Unit = runtime.unsafeRun(effects.update("Closed" :: _).unit)
              })
              _      <- ZManaged.fromAutoCloseable(closeable).use_(ZIO.unit)
              result <- effects.get
            } yield assert(result, equalTo(List("Closed")))
          }
        ),
        suite("mergeAll")(
          testM("Merges elements in the correct order") {
            def res(int: Int) =
              ZManaged.succeed(int)

            val managed = ZManaged.mergeAll(List(1, 2, 3, 4).map(res))(List[Int]()) { case (acc, a) => a :: acc }
            managed.use[Any, Nothing, TestResult](res => ZIO.succeed(assert(res, equalTo(List(4, 3, 2, 1)))))
          },
          testM("Runs finalizers") {
            ZManagedSpecUtil.testFinalizersPar(4, res => ZManaged.mergeAll(List.fill(4)(res))(()) { case (_, b) => b })
          }
        ),
        suite("mergeAllPar")(
          testM("Merges elements in the correct order") {
            def res(int: Int) =
              ZManaged.succeed(int)

            val managed = ZManaged.mergeAllPar(List(1, 2, 3, 4).map(res))(List[Int]()) { case (acc, a) => a :: acc }
            managed.use[Any, Nothing, TestResult](res => ZIO.succeed(assert(res, equalTo(List(4, 3, 2, 1)))))
          },
          testM("Runs reservations in parallel") {
            ZManagedSpecUtil.testReservePar(4, res => ZManaged.mergeAllPar(List.fill(4)(res))(()) { case (_, b) => b })
          },
          testM("Runs acquisitions in parallel") {
            ZManagedSpecUtil.testAcquirePar(4, res => ZManaged.mergeAllPar(List.fill(4)(res))(()) { case (_, b) => b })
          },
          testM("Runs finalizers") {
            ZManagedSpecUtil
              .testFinalizersPar(4, res => ZManaged.mergeAllPar(List.fill(4)(res))(()) { case (_, b) => b })
          }
        ),
        suite("mergeAllParN")(
          testM("Merges elements in the correct order") {
            def res(int: Int) =
              ZManaged.succeed(int)
            val managed = ZManaged.mergeAllParN(2)(List(1, 2, 3, 4).map(res))(List[Int]()) { case (acc, a) => a :: acc }
            managed.use[Any, Nothing, TestResult](res => ZIO.succeed(assert(res, equalTo(List(4, 3, 2, 1)))))
          },
          testM("Uses at most n fibers for reservation") {
            ZManagedSpecUtil
              .testReservePar(2, res => ZManaged.mergeAllParN(2)(List.fill(4)(res))(0) { case (a, _) => a })
          },
          testM("Uses at most n fibers for acquisition") {
            ZManagedSpecUtil
              .testAcquirePar(2, res => ZManaged.mergeAllParN(2)(List.fill(4)(res))(0) { case (a, _) => a })
          },
          testM("Runs finalizers") {
            ZManagedSpecUtil
              .testFinalizersPar(4, res => ZManaged.mergeAllParN(2)(List.fill(4)(res))(0) { case (a, _) => a })
          },
          testM("All finalizers run even when finalizers have defects") {
            for {
              releases <- Ref.make[Int](0)
              _ <- ZManaged
                    .mergeAllParN(2)(
                      List(
                        ZManaged.finalizer(ZIO.dieMessage("Boom")),
                        ZManaged.finalizer(releases.update(_ + 1)),
                        ZManaged.finalizer(ZIO.dieMessage("Boom")),
                        ZManaged.finalizer(releases.update(_ + 1)),
                        ZManaged.finalizer(ZIO.dieMessage("Boom")),
                        ZManaged.finalizer(releases.update(_ + 1))
                      )
                    )(())((_, _) => ())
                    .use_(ZIO.unit)
                    .run
              count <- releases.get
            } yield assert(count, equalTo(3))
          }
        ),
        suite("onExit")(
          testM("Calls the cleanup") {
            for {
              finalizersRef <- Ref.make[List[String]](Nil)
              resultRef     <- Ref.make[Option[Exit[Nothing, String]]](None)
              _ <- ZManaged
                    .make(UIO.succeed("42"))(_ => finalizersRef.update("First" :: _))
                    .onExit(e => finalizersRef.update("Second" :: _) *> resultRef.set(Some(e)))
                    .use_(ZIO.unit)
              finalizers <- finalizersRef.get
              result     <- resultRef.get
            } yield assert(finalizers, equalTo(List("Second", "First"))) && assert(
              result,
              isSome(succeeds(equalTo("42")))
            )
          }
        ),
        suite("onExitFirst")(
          testM("Calls the cleanup") {
            for {
              finalizersRef <- Ref.make[List[String]](Nil)
              resultRef     <- Ref.make[Option[Exit[Nothing, String]]](None)
              _ <- ZManaged
                    .make(UIO.succeed("42"))(_ => finalizersRef.update("First" :: _))
                    .onExitFirst(e => finalizersRef.update("Second" :: _) *> resultRef.set(Some(e)))
                    .use_(ZIO.unit)
              finalizers <- finalizersRef.get
              result     <- resultRef.get
            } yield assert(finalizers, equalTo(List("First", "Second"))) && assert(
              result,
              isSome(succeeds(equalTo("42")))
            )
          }
        ),
        suite("reduceAll")(
          testM("Reduces elements in the correct order") {
            def res(int: Int) =
              ZManaged.succeed(List(int))

            val managed = ZManaged.reduceAll(ZManaged.succeed(Nil), List(1, 2, 3, 4).map(res)) {
              case (a1, a2) => a1 ++ a2
            }
            managed.use[Any, Nothing, TestResult](res => ZIO.succeed(assert(res, equalTo(List(1, 2, 3, 4)))))
          },
          testM("Runs finalizers") {
            ZManagedSpecUtil.testFinalizersPar(
              4,
              res => ZManaged.reduceAll(ZManaged.succeed(0), List.fill(4)(res)) { case (a, _) => a }
            )
          }
        ),
        suite("reduceAllPar")(
          testM("Reduces elements in the correct order") {
            def res(int: Int) =
              ZManaged.succeed(List(int))

            val managed = ZManaged.reduceAllPar(ZManaged.succeed(Nil), List(1, 2, 3, 4).map(res)) {
              case (a1, a2) => a1 ++ a2
            }
            managed.use[Any, Nothing, TestResult](res => ZIO.succeed(assert(res, equalTo(List(1, 2, 3, 4)))))
          },
          testM("Runs reservations in parallel") {
            ZManagedSpecUtil.testReservePar(
              4,
              res => ZManaged.reduceAllPar(ZManaged.succeed(0), List.fill(4)(res)) { case (a, _) => a }
            )
          },
          testM("Runs acquisitions in parallel") {
            ZManagedSpecUtil.testAcquirePar(
              4,
              res => ZManaged.reduceAllPar(ZManaged.succeed(0), List.fill(4)(res)) { case (a, _) => a }
            )
          },
          testM("Runs finalizers") {
            ZManagedSpecUtil.testFinalizersPar(
              4,
              res => ZManaged.reduceAllPar(ZManaged.succeed(0), List.fill(4)(res)) { case (a, _) => a }
            )
          }
        ),
        suite("reduceAllParN")(
          testM("Reduces elements in the correct order") {
            def res(int: Int) =
              ZManaged.succeed(List(int))

            val managed = ZManaged.reduceAllParN(2)(ZManaged.succeed(Nil), List(1, 2, 3, 4).map(res)) {
              case (acc, a) => a ++ acc
            }
            managed.use[Any, Nothing, TestResult](res => ZIO.succeed(assert(res, equalTo(List(4, 3, 2, 1)))))
          },
          testM("Uses at most n fibers for reservation") {
            ZManagedSpecUtil.testFinalizersPar(
              4,
              res => ZManaged.reduceAllParN(2)(ZManaged.succeed(0), List.fill(4)(res)) { case (a, _) => a }
            )
          },
          testM("Uses at most n fibers for acquisition") {
            ZManagedSpecUtil.testReservePar(
              2,
              res => ZManaged.reduceAllParN(2)(ZManaged.succeed(0), List.fill(4)(res)) { case (a, _) => a }
            )
          },
          testM("Runs finalizers") {
            ZManagedSpecUtil.testAcquirePar(
              2,
              res => ZManaged.reduceAllParN(2)(ZManaged.succeed(0), List.fill(4)(res)) { case (a, _) => a }
            )
          },
          testM("All finalizers run even when finalizers have defects") {
            for {
              releases <- Ref.make[Int](0)
              _ <- ZManaged
                    .reduceAllParN(2)(
                      ZManaged.finalizer(ZIO.dieMessage("Boom")),
                      List(
                        ZManaged.finalizer(releases.update(_ + 1)),
                        ZManaged.finalizer(ZIO.dieMessage("Boom")),
                        ZManaged.finalizer(releases.update(_ + 1)),
                        ZManaged.finalizer(ZIO.dieMessage("Boom")),
                        ZManaged.finalizer(releases.update(_ + 1))
                      )
                    )((_, _) => ())
                    .use_(ZIO.unit)
                    .run
              count <- releases.get
            } yield assert(count, equalTo(3))
          }
        ),
        suite("retry")(
          testM("Should retry the reservation") {
            for {
              retries <- Ref.make(0)
              program = ZManaged
                .make(retries.update(_ + 1).flatMap(r => if (r == 3) ZIO.unit else ZIO.fail(())))(_ => ZIO.unit)
              _ <- program.retry(Schedule.recurs(3)).use(_ => ZIO.unit).ignore
              r <- retries.get
            } yield assert(r, equalTo(3))
          },
          testM("Should retry the acquisition") {
            for {
              retries <- Ref.make(0)
              program = Managed.reserve(
                Reservation(retries.update(_ + 1).flatMap(r => if (r == 3) ZIO.unit else ZIO.fail(())), _ => ZIO.unit)
              )
              _ <- program.retry(Schedule.recurs(3)).use(_ => ZIO.unit).ignore
              r <- retries.get
            } yield assert(r, equalTo(3))
          },
          testM("Should share retries between both") {
            for {
              retries1 <- Ref.make(0)
              retries2 <- Ref.make(0)
              program = ZManaged {
                retries1.update(_ + 1).flatMap { r1 =>
                  if (r1 < 3) ZIO.fail(())
                  else
                    ZIO.succeed {
                      Reservation(
                        retries2.update(_ + 1).flatMap(r2 => if (r2 == 3) ZIO.unit else ZIO.fail(())),
                        _ => ZIO.unit
                      )
                    }
                }
              }
              _  <- program.retry(Schedule.recurs(6)).use(_ => ZIO.unit).ignore
              r1 <- retries1.get
              r2 <- retries2.get
            } yield assert(r1, equalTo(3)) && assert(r2, equalTo(3))
          }
        ),
        suite("timed")(
          testM("Should time both the reservation and the acquisition") {
            val managed = ZManaged(
              clock.sleep(20.milliseconds) *> ZIO.succeed(Reservation(clock.sleep(20.milliseconds), _ => ZIO.unit))
            )
            val test = managed.timed.use {
              case (duration, _) =>
                ZIO.succeed(assert(duration.toNanos, isGreaterThanEqualTo(40.milliseconds.toNanos)))
            }
            def awaitSleeps(n: Int): ZIO[TestClock, Nothing, Unit] =
              TestClock.sleeps.flatMap {
                case x if x.length >= n => ZIO.unit
                case _                  => ZIO.sleep(20.milliseconds).provide(zio.clock.Clock.Live) *> awaitSleeps(n)
              }
            for {
              f      <- test.fork
              _      <- awaitSleeps(1)
              _      <- TestClock.adjust(20.milliseconds)
              _      <- awaitSleeps(1)
              _      <- TestClock.adjust(20.milliseconds)
              result <- f.join
            } yield result
          }
        ),
        suite("timeout")(
          testM("Returns Some if the timeout isn't reached") {
            val managed = ZManaged.make(ZIO.succeed(1))(_ => ZIO.unit)
            managed.timeout(Duration.Infinity).use(res => ZIO.succeed(assert(res, isSome(equalTo(1)))))
          },
          testM("Returns None if the reservation takes longer than d") {
            for {
              latch   <- Promise.make[Nothing, Unit]
              managed = ZManaged.make(latch.await)(_ => ZIO.unit)
              res     <- managed.timeout(Duration.Zero).use(res => ZIO.succeed(assert(res, isNone)))
              _       <- latch.succeed(())
            } yield res
          },
          testM("Returns None if the acquisition takes longer than d") {
            for {
              latch   <- Promise.make[Nothing, Unit]
              managed = ZManaged.reserve(Reservation(latch.await, _ => ZIO.unit))
              res     <- managed.timeout(Duration.Zero).use(res => ZIO.succeed(assert(res, isNone)))
              _       <- latch.succeed(())
            } yield res
          },
          testM("Runs finalizers if returning None and reservation is successful") {
            for {
              reserveLatch <- Promise.make[Nothing, Unit]
              releaseLatch <- Promise.make[Nothing, Unit]
              managed      = ZManaged.reserve(Reservation(reserveLatch.await, _ => releaseLatch.succeed(())))
              res          <- managed.timeout(Duration.Zero).use(ZIO.succeed)
              _            <- reserveLatch.succeed(())
              _            <- releaseLatch.await
            } yield assert(res, isNone)
          },
          testM("Runs finalizers if returning None and reservation is successful after timeout") {
            for {
              acquireLatch <- Promise.make[Nothing, Unit]
              releaseLatch <- Promise.make[Nothing, Unit]
              managed = ZManaged(
                acquireLatch.await *> ZIO.succeed(Reservation(ZIO.unit, _ => releaseLatch.succeed(())))
              )
              res <- managed.timeout(Duration.Zero).use(ZIO.succeed)
              _   <- acquireLatch.succeed(())
              _   <- releaseLatch.await
            } yield assert(res, isNone)
          }
        ),
        suite("withEarlyRelease")(
          testM("Provides a canceler that can be used to eagerly evaluate the finalizer") {
            for {
              ref     <- Ref.make(false)
              managed = ZManaged.make(ZIO.unit)(_ => ref.set(true)).withEarlyRelease
              result <- managed.use {
                         case (canceler, _) => canceler *> ref.get
                       }
            } yield assert(result, isTrue)
          },
          testM("The canceler should run uninterruptibly") {
            for {
              ref     <- Ref.make(true)
              latch   <- Promise.make[Nothing, Unit]
              managed = Managed.make(ZIO.unit)(_ => latch.succeed(()) *> ZIO.never.whenM(ref.get)).withEarlyRelease
              result <- managed.use {
                         case (canceler, _) =>
                           for {
                             fiber        <- canceler.fork
                             _            <- latch.await
                             interruption <- withLive(fiber.interrupt)(_.timeout(5.seconds))
                             _            <- ref.set(false)
                           } yield interruption
                       }
            } yield assert(result, isNone)
          },
          testM("If completed, the canceler should cause the regular finalizer to not run") {
            for {
              latch   <- Promise.make[Nothing, Unit]
              ref     <- Ref.make(0)
              managed = ZManaged.make(ZIO.unit)(_ => ref.update(_ + 1)).withEarlyRelease
              _       <- managed.use(_._1).ensuring(latch.succeed(()))
              _       <- latch.await
              result  <- ref.get
            } yield assert(result, equalTo(1))
          },
          testM("The canceler will run with an exit value indicating the effect was interrupted") {
            for {
              ref     <- Ref.make(false)
              managed = ZManaged.makeExit(ZIO.unit)((_, e) => ref.set(e.interrupted))
              _       <- managed.withEarlyRelease.use(_._1)
              result  <- ref.get
            } yield assert(result, isTrue)
          }
        ),
        suite("withEarlyReleaseExit")(
          testM("Allows specifying an exit value") {
            for {
              ref     <- Ref.make(false)
              managed = ZManaged.makeExit(ZIO.unit)((_, e) => ref.set(e.succeeded))
              _       <- managed.withEarlyReleaseExit(Exit.unit).use(_._1)
              result  <- ref.get
            } yield assert(result, isTrue)
          }
        ),
        suite("zipPar")(
          testM("Does not swallow exit cause if one reservation fails") {
            (for {
              latch  <- Promise.make[Nothing, Unit]
              first  = ZManaged.fromEffect(latch.succeed(()) *> ZIO.sleep(Duration.Infinity))
              second = ZManaged.fromEffect(latch.await *> ZIO.fail(()))
              _      <- first.zipPar(second).use_(ZIO.unit)
            } yield ()).run
              .map(assert(_, fails(equalTo(()))))
          },
          testM("Runs finalizers if one acquisition fails") {
            for {
              releases <- Ref.make(0)
              first    = ZManaged.unit
              second   = ZManaged.reserve(Reservation(ZIO.fail(()), _ => releases.update(_ + 1)))
              _        <- first.zipPar(second).use(_ => ZIO.unit).ignore
              r        <- releases.get
            } yield assert(r, equalTo(1))
          },
          testM("Does not swallow acquisition if one acquisition fails") {
            (for {
              latch  <- Promise.make[Nothing, Unit]
              first  = ZManaged.fromEffect(latch.succeed(()) *> ZIO.sleep(Duration.Infinity))
              second = ZManaged.reserve(Reservation(latch.await *> ZIO.fail(()), _ => ZIO.unit))
              _      <- first.zipPar(second).use_(ZIO.unit)
            } yield ()).run
              .map(assert(_, equalTo(Exit.Failure(Cause.Both(Cause.Fail(()), Cause.Interrupt)))))
          },
          testM("Run finalizers if one reservation fails") {
            for {
              reserveLatch <- Promise.make[Nothing, Unit]
              releases     <- Ref.make[Int](0)
              first        = ZManaged.reserve(Reservation(reserveLatch.succeed(()), _ => releases.update(_ + 1)))
              second       = ZManaged.fromEffect(reserveLatch.await *> ZIO.fail(()))
              _            <- first.zipPar(second).use_(ZIO.unit).orElse(ZIO.unit)
              count        <- releases.get
            } yield assert(count, equalTo(1))
          }
        ),
        suite("flatten")(
          testM("Returns the same as ZManaged.flatten") {
            checkM(Gen.string(Gen.alphaNumericChar)) { str =>
              val test = for {
                flatten1 <- ZManaged.succeed(ZManaged.succeed(str)).flatten
                flatten2 <- ZManaged.flatten(ZManaged.succeed(ZManaged.succeed(str)))
              } yield assert(flatten1, equalTo(flatten2))
              test.use[Any, Nothing, TestResult](r => ZIO.succeed(r))
            }
          }
        ),
        suite("absolve")(
          testM("Returns the same as ZManaged.absolve") {
            checkM(Gen.string(Gen.alphaNumericChar)) { str =>
              val managedEither: ZManaged[Any, Nothing, Either[Nothing, String]] = ZManaged.succeed(Right(str))
              val test = for {
                abs1 <- managedEither.absolve
                abs2 <- ZManaged.absolve(managedEither)
              } yield assert(abs1, equalTo(abs2))
              test.use[Any, Nothing, TestResult](result => ZIO.succeed(result))
            }
          }
        ),
        suite("switchable")(
          testM("runs the right finalizer on interruption") {
            for {
              effects <- Ref.make(List[String]())
              latch   <- Promise.make[Nothing, Unit]
              fib <- ZManaged
                      .switchable[Any, Nothing, Unit]
                      .use { switch =>
                        switch(ZManaged.finalizer(effects.update("First" :: _))) *>
                          switch(ZManaged.finalizer(effects.update("Second" :: _))) *>
                          latch.succeed(()) *>
                          ZIO.never
                      }
                      .fork
              _      <- latch.await
              _      <- fib.interrupt
              result <- effects.get
            } yield assert(result, equalTo(List("Second", "First")))
          }
        ),
        suite("catch")(
          testM("catchAllCause") {
            val zm: ZManaged[Any, String, String] =
              for {
                _ <- ZManaged.succeed("foo")
                f <- ZManaged.fail("Uh oh!")
              } yield f

            val errorToVal = zm.catchAllCause(c => ZManaged.succeed(c.failureOption.getOrElse(c.toString)))
            assertM(errorToVal.use(ZIO.succeed), equalTo("Uh oh!"))
          },
          testM("catchAllSomeCause transforms cause if matched") {
            val zm: ZManaged[Any, String, String] =
              for {
                _ <- ZManaged.succeed("foo")
                f <- ZManaged.fail("Uh oh!")
              } yield f

            val errorToVal = zm.catchSomeCause {
              case Cause.Fail("Uh oh!") => ZManaged.succeed("matched")
            }
            assertM(errorToVal.use(ZIO.succeed), equalTo("matched"))
          },
          testM("catchAllSomeCause keeps the failure cause if not matched") {
            val zm: ZManaged[Any, String, String] =
              for {
                _ <- ZManaged.succeed("foo")
                f <- ZManaged.fail("Uh oh!")
              } yield f

            val errorToVal = zm.catchSomeCause {
              case Cause.Fail("not matched") => ZManaged.succeed("matched")
            }
            val executed = errorToVal.use[Any, String, String](ZIO.succeed).run
            assertM(executed, fails(equalTo("Uh oh!")))
          }
        )
      )
    )

object ZManagedSpecUtil {
  def countDownLatch(n: Int): UIO[UIO[Unit]] =
    Ref.make(n).map { counter =>
      counter.update(_ - 1) *> {
        def await: UIO[Unit] = counter.get.flatMap { n =>
          if (n <= 0) ZIO.unit
          else ZIO.sleep(10.milliseconds).provide(zio.clock.Clock.Live) *> await
        }
        await
      }
    }

  def doInterrupt(
    managed: IO[Nothing, Unit] => ZManaged[Any, Nothing, Unit],
    expected: Option[Exit[Nothing, Unit]]
  ) =
    for {
      never              <- Promise.make[Nothing, Unit]
      reachedAcquisition <- Promise.make[Nothing, Unit]
      managedFiber       <- managed(reachedAcquisition.succeed(()) *> never.await).use_(IO.unit).fork
      _                  <- reachedAcquisition.await
      interruption       <- managedFiber.interrupt.timeout(5.seconds).provide(zio.clock.Clock.Live)
    } yield assert(interruption, equalTo(expected))

  def testFinalizersPar[R, E](
    n: Int,
    f: ZManaged[Any, Nothing, Unit] => ZManaged[R, E, Any]
  ) =
    for {
      releases <- Ref.make[Int](0)
      baseRes  = ZManaged.make(ZIO.succeed(()))(_ => releases.update(_ + 1))
      res      = f(baseRes)
      _        <- res.use_(ZIO.unit)
      count    <- releases.get
    } yield assert(count, equalTo(n))

  def testAcquirePar[R, E](
    n: Int,
    f: ZManaged[Any, Nothing, Unit] => ZManaged[R, E, Any]
  ) =
    for {
      effects      <- Ref.make(0)
      countDown    <- countDownLatch(n + 1)
      reserveLatch <- Promise.make[Nothing, Unit]
      baseRes = ZManaged.reserve(
        Reservation(effects.update(_ + 1) *> countDown *> reserveLatch.await, _ => ZIO.unit)
      )
      res   = f(baseRes)
      _     <- res.use_(ZIO.unit).fork *> countDown
      count <- effects.get
      _     <- reserveLatch.succeed(())
    } yield assert(count, equalTo(n))

  def testReservePar[R, E, A](
    n: Int,
    f: ZManaged[Any, Nothing, Unit] => ZManaged[R, E, A]
  ) =
    for {
      effects      <- Ref.make(0)
      countDown    <- countDownLatch(n + 1)
      reserveLatch <- Promise.make[Nothing, Unit]
      baseRes = ZManaged.make(effects.update(_ + 1) *> countDown *> reserveLatch.await)(
        _ => ZIO.unit
      )
      res   = f(baseRes)
      _     <- res.use_(ZIO.unit).fork *> countDown
      count <- effects.get
      _     <- reserveLatch.succeed(())
    } yield assert(count, equalTo(n))
}
