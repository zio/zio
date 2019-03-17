/*
 * Copyright 2017-2019 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package scalaz.zio.stream

import scalaz.zio._
import scalaz.zio.clock.Clock

import scala.annotation.implicitNotFound

/**
 * A `Stream[E, A]` represents an effectful stream that can produce values of
 * type `A`, or potentially fail with a value of type `E`.
 *
 * Streams have a very similar API to Scala collections, making them immediately
 * familiar to most developers. Unlike Scala collections, streams can be used
 * on effectful streams of data, such as HTTP connections, files, and so forth.
 *
 * Streams do not leak resources. This guarantee holds in the presence of early
 * termination (not all of a stream is consumed), failure, or even interruption.
 *
 * Thanks to only first-order types, appropriate variance annotations, and
 * specialized effect type (ZIO), streams feature extremely good type inference
 * and should almost never require specification of any type parameters.
 *
 */
trait ZStream[-R, +E, +A] extends Serializable { self =>
  import ZStream.Fold

  /**
   * Executes an effectful fold over the stream of values.
   */
  def fold[R1 <: R, E1 >: E, A1 >: A, S]: Fold[R1, E1, A1, S]

  def foldLeft[A1 >: A, S](s: S)(f: (S, A1) => S): ZIO[R, E, S] =
    self.fold[R, E, A1, S].flatMap(f0 => f0(s, _ => true, (s, a) => IO.succeed(f(s, a))))

  /**
   * Concatenates the specified stream to this stream.
   */
  final def ++[R1 <: R, E1 >: E, A1 >: A](that: => ZStream[R1, E1, A1]): ZStream[R1, E1, A1] =
    new ZStream[R1, E1, A1] {
      override def fold[R2 <: R1, E2 >: E1, A2 >: A1, S]: Fold[R2, E2, A2, S] =
        IO.succeedLazy { (s, cont, f) =>
          self.fold[R2, E2, A, S].flatMap { f0 =>
            f0(s, cont, f).flatMap { s =>
              if (cont(s)) that.fold[R2, E2, A1, S].flatMap(f1 => f1(s, cont, f))
              else IO.succeed(s)
            }
          }
        }
    }

  /**
   * Converts this stream to a stream that executes its effects but emits no
   * elements. Useful for sequencing effects using streams:
   *
   * {{{
   * (Stream(1, 2, 3).tap(i => ZIO(println(i))) ++
   *   Stream.lift(ZIO(println("Done!"))).drain ++
   *   Stream(4, 5, 6).tap(i => ZIO(println(i)))).run(Sink.drain)
   * }}}
   */
  final def drain: ZStream[R, E, Nothing] =
    new ZStream[R, E, Nothing] {
      override def fold[R1 <: R, E1 >: E, A1 >: Nothing, S]: Fold[R1, E1, A1, S] =
        IO.succeedLazy { (s, cont, _) =>
          self.fold[R1, E1, A, S].flatMap { f0 =>
            f0(s, cont, (s, _) => IO.succeed(s))
          }
        }
    }

  /**
   * Filters this stream by the specified predicate, retaining all elements for
   * which the predicate evaluates to true.
   */
  def filter(pred: A => Boolean): ZStream[R, E, A] = new ZStream[R, E, A] {
    override def fold[R1 <: R, E1 >: E, A1 >: A, S]: Fold[R1, E1, A1, S] =
      IO.succeedLazy { (s, cont, f) =>
        self.fold[R1, E1, A, S].flatMap { f0 =>
          f0(s, cont, (s, a) => if (pred(a)) f(s, a) else IO.succeed(s))
        }
      }
  }

  /**
   * Filters this stream by the specified effectful predicate, retaining all elements for
   * which the predicate evaluates to true.
   */
  final def filterM[R1 <: R, E1 >: E](pred: A => ZIO[R1, E1, Boolean]): ZStream[R1, E1, A] = new ZStream[R1, E1, A] {
    override def fold[R2 <: R1, E2 >: E1, A1 >: A, S]: Fold[R2, E2, A1, S] =
      IO.succeedLazy { (s, cont, g) =>
        self.fold[R2, E2, A, S].flatMap { f0 =>
          f0(s, cont, (s, a) => pred(a).flatMap(if (_) g(s, a) else IO.succeed(s)))
        }
      }
  }

  /**
   * Filters this stream by the specified predicate, removing all elements for
   * which the predicate evaluates to true.
   */
  final def filterNot(pred: A => Boolean): ZStream[R, E, A] = filter(a => !pred(a))

  /**
   * Consumes elements of the stream, passing them to the specified callback,
   * and terminating consumption when the callback returns `false`.
   */
  final def foreachWhile[R1 <: R, E1 >: E](f: A => ZIO[R1, E1, Boolean]): ZIO[R1, E1, Unit] =
    self
      .fold[R1, E1, A, Boolean]
      .flatMap[R1, E1, Boolean] { f0 =>
        f0(true, identity, (cont, a) => if (cont) f(a) else IO.succeed(cont))
      }
      .void

  /**
   * Performs a filter and map in a single step.
   */
  def collect[B](pf: PartialFunction[A, B]): ZStream[R, E, B] =
    new ZStream[R, E, B] {
      override def fold[R1 <: R, E1 >: E, B1 >: B, S]: Fold[R1, E1, B1, S] =
        IO.succeedLazy { (s, cont, f) =>
          self.fold[R1, E1, A, S].flatMap { f0 =>
            f0(s, cont, (s, a) => if (pf.isDefinedAt(a)) f(s, pf(a)) else IO.succeed(s))
          }
        }
    }

  /**
   * Drops the specified number of elements from this stream.
   */
  final def drop(n: Int): ZStream[R, E, A] =
    self.zipWithIndex.filter(_._2 > n - 1).map(_._1)

  /**
   * Drops all elements of the stream for as long as the specified predicate
   * evaluates to `true`.
   */
  def dropWhile(pred: A => Boolean): ZStream[R, E, A] = new ZStream[R, E, A] {
    override def fold[R1 <: R, E1 >: E, A1 >: A, S]: Fold[R1, E1, A1, S] =
      IO.succeedLazy { (s, cont, f) =>
        self.fold[R1, E1, A, (Boolean, S)].flatMap { f0 =>
          def func(tp: (Boolean, S), a: A): ZIO[R1, E1, (Boolean, S)] =
            (tp, a) match {
              case ((true, s), a) if pred(a) => IO.succeed(true   -> s)
              case ((_, s), a)               => f(s, a).map(false -> _)
            }

          f0(true -> s, tp => cont(tp._2), func).map(_._2)
        }
      }
  }

  /**
   * Maps each element of this stream to another stream, and returns the
   * concatenation of those streams.
   */
  final def flatMap[R1 <: R, E1 >: E, B](f: A => ZStream[R1, E1, B]): ZStream[R1, E1, B] = new ZStream[R1, E1, B] {
    override def fold[R2 <: R1, E2 >: E1, B1 >: B, S]: Fold[R2, E2, B1, S] =
      IO.succeedLazy { (s, cont, g) =>
        self.fold[R2, E2, A, S].flatMap { f0 =>
          f0(s, cont, (s, a) => f(a).fold[R2, E2, B1, S].flatMap(h => h(s, cont, g)))
        }
      }
  }

  /**
   * Consumes all elements of the stream, passing them to the specified callback.
   */
  final def foreach[R1 <: R, E1 >: E](f: A => ZIO[R1, E1, Unit]): ZIO[R1, E1, Unit] =
    foreachWhile(f.andThen(_.const(true)))

  /**
   * Repeats this stream forever.
   */
  def forever: ZStream[R, E, A] =
    new ZStream[R, E, A] {
      override def fold[R1 <: R, E1 >: E, A1 >: A, S]: Fold[R1, E1, A1, S] =
        IO.succeedLazy { (s, cont, f) =>
          def loop(s: S): ZIO[R1, E1, S] =
            self.fold[R1, E1, A, S].flatMap { f0 =>
              f0(s, cont, f).flatMap(s => if (cont(s)) loop(s) else IO.succeed(s))
            }

          loop(s)
        }
    }

  /**
   * Maps over elements of the stream with the specified function.
   */
  def map[B](f: A => B): ZStream[R, E, B] = new ZStream[R, E, B] {
    override def fold[R1 <: R, E1 >: E, B1 >: B, S]: Fold[R1, E1, B1, S] =
      IO.succeedLazy { (s, cont, g) =>
        self.fold[R1, E1, A, S].flatMap(f0 => f0(s, cont, (s, a) => g(s, f(a))))
      }
  }

  /**
   * Maps each element to a chunk, and flattens the chunks into the output of
   * this stream.
   */
  def mapConcat[B](f: A => Chunk[B]): ZStream[R, E, B] = new ZStream[R, E, B] {
    override def fold[R1 <: R, E1 >: E, B1 >: B, S]: Fold[R1, E1, B1, S] =
      IO.succeedLazy { (s, cont, g) =>
        self.fold[R1, E1, A, S].flatMap(f0 => f0(s, cont, (s, a) => f(a).foldMLazy(s)(cont)(g)))
      }
  }

  /**
   * Maps over elements of the stream with the specified effectful function.
   */
  final def mapM[R1 <: R, E1 >: E, B](f: A => ZIO[R1, E1, B]): ZStream[R1, E1, B] = new ZStream[R1, E1, B] {
    override def fold[R2 <: R1, E2 >: E1, B1 >: B, S]: Fold[R2, E2, B1, S] =
      IO.succeedLazy { (s, cont, g) =>
        self.fold[R2, E2, A, S].flatMap(f0 => f0(s, cont, (s, a) => f(a).flatMap(g(s, _))))
      }
  }

  /**
   * Merges this stream and the specified stream together.
   */
  final def merge[R1 <: R, E1 >: E, A1 >: A](that: ZStream[R1, E1, A1], capacity: Int = 1): ZStream[R1, E1, A1] =
    self.mergeWith(that, capacity)(identity, identity)

  /**
   * Merges this stream and the specified stream together to produce a stream of
   * eithers.
   */
  final def mergeEither[R1 <: R, E1 >: E, B](
    that: ZStream[R1, E1, B],
    capacity: Int = 1
  ): ZStream[R1, E1, Either[A, B]] =
    self.mergeWith(that, capacity)(Left(_), Right(_))

  /**
   * Merges this stream and the specified stream together to a common element
   * type with the specified mapping functions.
   */
  final def mergeWith[R1 <: R, E1 >: E, B, C](
    that: ZStream[R1, E1, B],
    capacity: Int = 1
  )(l: A => C, r: B => C): ZStream[R1, E1, C] =
    new ZStream[R1, E1, C] {
      override def fold[R2 <: R1, E2 >: E1, C1 >: C, S]: Fold[R2, E2, C1, S] =
        IO.succeedLazy { (s, cont, f) =>
          type Elem = Either[Take[E2, A], Take[E2, B]]

          def loop(leftDone: Boolean, rightDone: Boolean, s: S, queue: Queue[Elem]): ZIO[R2, E2, S] =
            queue.take.flatMap {
              case Left(Take.Fail(e))  => IO.fail(e)
              case Right(Take.Fail(e)) => IO.fail(e)
              case Left(Take.End) =>
                if (rightDone) IO.succeed(s)
                else loop(true, rightDone, s, queue)
              case Left(Take.Value(a)) =>
                f(s, l(a)).flatMap { s =>
                  if (cont(s)) loop(leftDone, rightDone, s, queue)
                  else IO.succeed(s)
                }
              case Right(Take.End) =>
                if (leftDone) IO.succeed(s)
                else loop(leftDone, true, s, queue)
              case Right(Take.Value(b)) =>
                f(s, r(b)).flatMap { s =>
                  if (cont(s)) loop(leftDone, rightDone, s, queue)
                  else IO.succeed(s)
                }
            }

          if (cont(s)) {
            (for {
              queue  <- Queue.bounded[Elem](capacity)
              putL   = (a: A) => queue.offer(Left(Take.Value(a))).void
              putR   = (b: B) => queue.offer(Right(Take.Value(b))).void
              catchL = (e: E2) => queue.offer(Left(Take.Fail(e)))
              catchR = (e: E2) => queue.offer(Right(Take.Fail(e)))
              endL   = queue.offer(Left(Take.End))
              endR   = queue.offer(Right(Take.End))
              _      <- (self.foreach(putL) *> endL).catchAll(catchL).fork
              _      <- (that.foreach(putR) *> endR).catchAll(catchR).fork
              step   <- loop(false, false, s, queue)
            } yield step).supervise
          } else IO.succeed(s)
        }
    }

  /**
   * Peels off enough material from the stream to construct an `R` using the
   * provided `Sink`, and then returns both the `R` and the remainder of the
   * `Stream` in a managed resource. Like all `Managed` resources, the provided
   * remainder is valid only within the scope of `Managed`.
   */
  final def peel[R1 <: R, E1 >: E, A1 >: A, B](
    sink: ZSink[R1, E1, A1, A1, B]
  ): ZManaged[R1, E1, (B, ZStream[R1, E1, A1])] = {
    type Folder = (Any, A1) => IO[E1, Any]
    type Cont   = Any => Boolean
    type Fold   = (Any, Cont, Folder)
    type State  = Either[sink.State, Fold]
    type Result = (B, ZStream[R, E1, A1])

    def feed[R2 <: R1, S](chunk: Chunk[A1])(s: S, cont: S => Boolean, f: (S, A1) => ZIO[R2, E1, S]): ZIO[R2, E1, S] =
      chunk.foldMLazy(s)(cont)(f)

    def tail(resume: Promise[Nothing, Fold], done: Promise[E1, Any]): ZStream[R, E1, A1] =
      new ZStream[R, E1, A1] {
        override def fold[R2 <: R, E2 >: E1, A2 >: A1, S]: ZStream.Fold[R2, E2, A2, S] =
          IO.succeedLazy { (s, cont, f) =>
            if (!cont(s)) IO.succeed(s)
            else
              resume.succeed((s, cont.asInstanceOf[Cont], f.asInstanceOf[Folder])) *>
                done.await.asInstanceOf[IO[E2, S]]
          }
      }

    def acquire(lstate: sink.State): ZIO[R1, Nothing, (Fiber[E1, State], Promise[E1, Result])] =
      for {
        resume <- Promise.make[Nothing, Fold]
        done   <- Promise.make[E1, Any]
        result <- Promise.make[E1, Result]
        fiber <- self
                  .fold[R1, E1, A1, State]
                  .flatMap { f0 =>
                    f0(
                      Left(lstate), {
                        case Left(_)             => true
                        case Right((s, cont, _)) => cont(s)
                      }, {
                        case (Left(lstate), a) =>
                          sink.step(lstate, a).flatMap { step =>
                            if (ZSink.Step.cont(step)) IO.succeed(Left(ZSink.Step.state(step)))
                            else {
                              val lstate = ZSink.Step.state(step)
                              val as     = ZSink.Step.leftover(step)

                              sink.extract(lstate).flatMap { r =>
                                result.succeed(r -> tail(resume, done)) *>
                                  resume.await
                                    .flatMap(t => feed(as)(t._1, t._2, t._3).map(s => Right((s, t._2, t._3))))
                              }
                            }
                          }
                        case (Right((rstate, cont, f)), a) =>
                          f(rstate, a).flatMap { rstate =>
                            ZIO.succeed(Right((rstate, cont, f)))
                          }
                      }
                    )
                  }
                  .onError(c => result.done(IO.halt(c)).void)
                  .fork
        _ <- fiber.await.flatMap {
              case Exit.Success(Left(_)) =>
                done.done(
                  IO.die(new Exception("Logic error: Stream.peel's inner stream ended with a Left"))
                )
              case Exit.Success(Right((rstate, _, _))) => done.succeed(rstate)
              case Exit.Failure(c)                     => done.done(IO.halt(c))
            }.fork.void
      } yield (fiber, result)

    ZManaged
      .fromEffect(sink.initial)
      .flatMap { step =>
        ZManaged.make(acquire(ZSink.Step.state(step)))(_._1.interrupt).flatMap { t =>
          ZManaged.fromEffect(t._2.await)
        }
      }
  }

  /**
   * Repeats the entire stream using the specified schedule. The stream will execute normally,
   * and then repeat again according to the provided schedule.
   */
  def repeat[R1 <: R](schedule: ZSchedule[R1, Unit, _]): ZStream[R1 with Clock, E, A] =
    new ZStream[R1 with Clock, E, A] {
      override def fold[R2 <: R1 with Clock, E1 >: E, A1 >: A, S]: Fold[R2, E1, A1, S] =
        IO.succeedLazy { (s, cont, f) =>
          def loop(s: S, sched: schedule.State): ZIO[R2, E1, S] =
            self.fold[R2, E1, A1, S].flatMap { f0 =>
              f0(s, cont, f).zip(schedule.update((), sched)).flatMap {
                case (s, decision) =>
                  if (decision.cont) IO.unit.delay(decision.delay) *> loop(s, decision.state)
                  else IO.succeed(s)
              }
            }

          schedule.initial.flatMap(loop(s, _))
        }
    }

  /**
   * Repeats elements of the stream using the provided schedule.
   */
  def repeatElems[R1 <: R, B](schedule: ZSchedule[R1, A, B]): ZStream[R1 with Clock, E, A] =
    new ZStream[R1 with Clock, E, A] {
      override def fold[R2 <: R1 with Clock, E1 >: E, A1 >: A, S]: Fold[R2, E1, A1, S] =
        IO.succeedLazy { (s, cont, f) =>
          def loop(s: S, sched: schedule.State, a: A): ZIO[R2, E1, S] =
            schedule.update(a, sched).flatMap { decision =>
              if (decision.cont)
                IO.unit.delay(decision.delay) *> f(s, a).flatMap(loop(_, decision.state, a))
              else IO.succeed(s)
            }

          schedule.initial.flatMap { sched =>
            self.fold[R2, E1, A, S].flatMap { f =>
              f(s, cont, (s, a) => loop(s, sched, a))
            }
          }
        }
    }

  /**
   * Runs the sink on the stream to produce either the sink's result or an error.
   */
  def run[R1 <: R, E1 >: E, A0, A1 >: A, B](sink: ZSink[R1, E1, A0, A1, B]): ZIO[R1, E1, B] =
    sink.initial.flatMap { state =>
      self.fold[R1, E1, A1, ZSink.Step[sink.State, A0]].flatMap { f =>
        f(state, ZSink.Step.cont, (s, a) => sink.step(ZSink.Step.state(s), a)).flatMap { step =>
          sink.extract(ZSink.Step.state(step))
        }
      }
    }

  /**
   * Statefully maps over the elements of this stream to produce new elements.
   */
  def mapAccum[S1, B](s1: S1)(f1: (S1, A) => (S1, B)): ZStream[R, E, B] = new ZStream[R, E, B] {
    override def fold[R1 <: R, E1 >: E, B1 >: B, S]: Fold[R1, E1, B1, S] =
      IO.succeedLazy { (s, cont, f) =>
        self.fold[R1, E1, A, (S, S1)].flatMap { f0 =>
          f0(s -> s1, tp => cont(tp._1), {
            case ((s, s1), a) =>
              val (s2, b) = f1(s1, a)

              f(s, b).map(s => s -> s2)
          }).map(_._1)
        }
      }
  }

  /**
   * Statefully and effectfully maps over the elements of this stream to produce
   * new elements.
   */
  final def mapAccumM[E1 >: E, S1, B](s1: S1)(f1: (S1, A) => IO[E1, (S1, B)]): ZStream[R, E1, B] =
    new ZStream[R, E1, B] {
      override def fold[R1 <: R, E2 >: E1, B1 >: B, S]: Fold[R1, E2, B1, S] =
        IO.succeedLazy { (s, cont, f) =>
          self.fold[R1, E2, A, (S, S1)].flatMap { f0 =>
            f0(s -> s1, tp => cont(tp._1), {
              case ((s, s1), a) =>
                f1(s1, a).flatMap {
                  case (s1, b) =>
                    f(s, b).map(s => s -> s1)
                }
            }).map(_._1)
          }
        }
    }

  /**
   * Takes the specified number of elements from this stream.
   */
  final def take(n: Int): ZStream[R, E, A] =
    self.zipWithIndex.takeWhile(_._2 < n).map(_._1)

  /**
   * Takes all elements of the stream for as long as the specified predicate
   * evaluates to `true`.
   */
  def takeWhile(pred: A => Boolean): ZStream[R, E, A] = new ZStream[R, E, A] {
    override def fold[R1 <: R, E1 >: E, A1 >: A, S]: Fold[R1, E1, A1, S] =
      IO.succeedLazy { (s, cont, f) =>
        self.fold[R1, E1, A, (Boolean, S)].flatMap { f0 =>
          f0(true -> s, tp => tp._1 && cont(tp._2), {
            case ((_, s), a) =>
              if (pred(a)) f(s, a).map(true -> _)
              else IO.succeed(false         -> s)
          }).map(_._2)
        }
      }
  }

  /**
   * Converts the stream to a managed queue. After managed queue is used, the
   * queue will never again produce values and should be discarded.
   */
  final def toQueue[E1 >: E, A1 >: A](capacity: Int = 1): ZManaged[R, Nothing, Queue[Take[E1, A1]]] =
    for {
      queue    <- ZManaged.make(Queue.bounded[Take[E1, A1]](capacity))(_.shutdown)
      offerVal = (a: A) => queue.offer(Take.Value(a)).void
      offerErr = (e: E) => queue.offer(Take.Fail(e))
      enqueuer = (self.foreach[R, E](offerVal).catchAll(offerErr) *> queue.offer(Take.End)).fork
      _        <- ZManaged.make(enqueuer)(_.interrupt)
    } yield queue

  /**
   * Applies a transducer to the stream, which converts one or more elements
   * of type `A` into elements of type `C`.
   */
  final def transduce[R1 <: R, E1 >: E, A1 >: A, C](sink: ZSink[R1, E1, A1, A1, C]): ZStream[R1, E1, C] =
    new ZStream[R1, E1, C] {
      override def fold[R2 <: R1, E2 >: E1, C1 >: C, S2]: Fold[R2, E2, C1, S2] =
        IO.succeedLazy { (s2, cont, f) =>
          def feed(s1: sink.State, s2: S2, a: Chunk[A1]): ZIO[R2, E2, ZSink.Step[(sink.State, S2), A1]] =
            sink.stepChunk(s1, a).flatMap { step =>
              if (ZSink.Step.cont(step)) IO.succeed(ZSink.Step.leftMap(step)((_, s2)))
              else {
                sink.extract(ZSink.Step.state(step)).flatMap { c =>
                  f(s2, c).flatMap { s2 =>
                    if (cont(s2))
                      sink.initial.flatMap(initStep => feed(ZSink.Step.state(initStep), s2, ZSink.Step.leftover(step)))
                    else IO.succeed(ZSink.Step.more((s1, s2)))
                  }
                }
              }
            }

          sink.initial.flatMap { initStep =>
            val s1 = ZSink.Step.leftMap(initStep)((_, s2))

            self.fold[R2, E2, A, ZSink.Step[(sink.State, S2), A1]].flatMap { f0 =>
              f0(s1, step => cont(ZSink.Step.state(step)._2), { (s, a) =>
                val (s1, s2) = ZSink.Step.state(s)
                feed(s1, s2, Chunk(a))
              }).flatMap { step =>
                val (s1, s2) = ZSink.Step.state(step)

                sink
                  .extract(s1)
                  .foldM(_ => IO.succeed(s2), c => f(s2, c))
              }
            }
          }
        }
    }

  /**
   * Adds an effect to consumption of every element of the stream.
   */
  final def tap[R1 <: R, E1 >: E](f: A => ZIO[R1, E1, _]): ZStream[R1, E1, A] =
    new ZStream[R1, E1, A] {
      override def fold[R2 <: R1, E2 >: E1, A1 >: A, S]: Fold[R2, E2, A1, S] =
        IO.succeedLazy { (s, cont, g) =>
          self.fold[R2, E2, A, S].flatMap { f0 =>
            f0(s, cont, (s, a2) => f(a2) *> g(s, a2))
          }
        }
    }

  /**
   * Zips this stream together with the specified stream.
   */
  final def zip[R1 <: R, E1 >: E, B](that: ZStream[R1, E1, B], lc: Int = 1, rc: Int = 1): ZStream[R1, E1, (A, B)] =
    self.zipWith(that, lc, rc)(
      (left, right) => left.flatMap(a => right.map(a -> _))
    )

  /**
   * Zips two streams together with a specified function.
   */
  final def zipWith[R1 <: R, E1 >: E, B, C](that: ZStream[R1, E1, B], lc: Int = 1, rc: Int = 1)(
    f: (Option[A], Option[B]) => Option[C]
  ): ZStream[R1, E1, C] =
    new ZStream[R1, E1, C] {
      override def fold[R2 <: R1, E2 >: E1, A1 >: C, S]: Fold[R2, E2, A1, S] =
        IO.succeedLazy { (s, cont, g) =>
          def loop(
            leftDone: Boolean,
            rightDone: Boolean,
            q1: Queue[Take[E2, A]],
            q2: Queue[Take[E2, B]],
            s: S
          ): ZIO[R2, E2, S] = {
            val takeLeft: ZIO[R2, E2, Option[A]]  = if (leftDone) IO.succeed(None) else Take.option(q1.take)
            val takeRight: ZIO[R2, E2, Option[B]] = if (rightDone) IO.succeed(None) else Take.option(q2.take)

            def handleSuccess(left: Option[A], right: Option[B]): ZIO[R2, E2, S] =
              f(left, right) match {
                case None => IO.succeed(s)
                case Some(c) =>
                  g(s, c).flatMap { s =>
                    if (cont(s)) loop(left.isEmpty, right.isEmpty, q1, q2, s)
                    else IO.succeed(s)
                  }
              }

            takeLeft.raceWith(takeRight)(
              (leftResult, rightFiber) =>
                leftResult.fold(
                  e => rightFiber.interrupt *> ZIO.halt(e),
                  l => rightFiber.join.flatMap(r => handleSuccess(l, r))
                ),
              (rightResult, leftFiber) =>
                rightResult.fold(
                  e => leftFiber.interrupt *> ZIO.halt(e),
                  r => leftFiber.join.flatMap(l => handleSuccess(l, r))
                )
            )
          }

          self.toQueue[E2, A](lc).use(q1 => that.toQueue[E2, B](rc).use(q2 => loop(false, false, q1, q2, s)))
        }
    }

  /**
   * Zips this stream together with the index of elements of the stream.
   */
  def zipWithIndex: ZStream[R, E, (A, Int)] = new ZStream[R, E, (A, Int)] {
    override def fold[R1 <: R, E1 >: E, A1 >: (A, Int), S]: Fold[R1, E1, A1, S] =
      IO.succeedLazy { (s, cont, f) =>
        self.fold[R1, E1, A, (S, Int)].flatMap { f0 =>
          f0((s, 0), tp => cont(tp._1), {
            case ((s, index), a) => f(s, (a, index)).map(s => (s, index + 1))
          }).map(_._1)
        }
      }
  }
}

trait Stream_Functions extends Serializable {

  import ZStream.Fold

  type ConformsR[A]
  implicit val ConformsAnyProof: ConformsR[Any]

  final def apply[A](as: A*): Stream[Nothing, A] = fromIterable(as)

  /**
   * Constructs a pure stream from the specified `Iterable`.
   */
  final def fromIterable[A](it: Iterable[A]): Stream[Nothing, A] = StreamPure.fromIterable(it)

  final def fromChunk[@specialized A](c: Chunk[A]): Stream[Nothing, A] =
    new StreamPure[A] {
      override def fold[R, E, A1 >: A, S]: ZStream.Fold[R, E, A1, S] =
        IO.succeedLazy((s, cont, f) => c.foldMLazy(s)(cont)(f))

      override def foldPureLazy[A1 >: A, S](s: S)(cont: S => Boolean)(f: (S, A1) => S): S =
        c.foldLeftLazy(s)(cont)(f)
    }

  /**
   * Returns the empty stream.
   */
  final val empty: Stream[Nothing, Nothing] = StreamPure.empty

  /**
   * Returns a stream that emits nothing and never ends.
   */
  final val never: Stream[Nothing, Nothing] =
    new Stream[Nothing, Nothing] {
      override def fold[R <: Any, E >: Nothing, A >: Nothing, S]: Fold[R, E, A, S] =
        ZIO.never
    }

  /**
   * Constructs a singleton stream from a strict value.
   */
  final def succeed[A](a: A): Stream[Nothing, A] = StreamPure.succeed(a)

  /**
   * Constructs a singleton stream from a lazy value.
   */
  final def succeedLazy[A](a: => A): Stream[Nothing, A] = StreamPure.succeedLazy(a)

  /**
   * Constructs a stream that fails without emitting any values.
   */
  final def fail[E](error: E): Stream[E, Nothing] =
    new Stream[E, Nothing] {
      override def fold[R <: Any, E1 >: E, A >: Nothing, S]: Fold[R, E1, A, S] =
        IO.succeed { (_, _, _) =>
          IO.fail(error)
        }
    }

  /**
   * Lifts an effect producing an `A` into a stream producing that `A`.
   */
  final def fromEffect[R: ConformsR, E, A](fa: ZIO[R, E, A]): ZStream[R, E, A] = new ZStream[R, E, A] {
    override def fold[R1 <: R, E1 >: E, A1 >: A, S]: Fold[R1, E1, A1, S] =
      IO.succeedLazy { (s, cont, f) =>
        if (cont(s)) fa.flatMap(f(s, _))
        else IO.succeed(s)
      }
  }

  /**
   * Flattens a stream of streams into a stream, by concatenating all the
   * substreams.
   */
  final def flatten[R: ConformsR, E, A](fa: ZStream[R, E, ZStream[R, E, A]]): ZStream[R, E, A] =
    fa.flatMap(identity)

  /**
   * Unwraps a stream wrapped inside of an `IO` value.
   */
  final def unwrap[R: ConformsR, E, A](stream: ZIO[R, E, ZStream[R, E, A]]): ZStream[R, E, A] =
    new ZStream[R, E, A] {
      override def fold[R1 <: R, E1 >: E, A1 >: A, S]: Fold[R1, E1, A1, S] =
        IO.succeedLazy { (s, cont, f) =>
          stream.flatMap(_.fold[R1, E1, A1, S].flatMap(f0 => f0(s, cont, f)))
        }
    }

  /**
   * Constructs a stream from a resource that must be acquired and released.
   */
  final def bracket[R: ConformsR, E, A, B](
    acquire: ZIO[R, E, A]
  )(release: A => UIO[Unit])(read: A => ZIO[R, E, Option[B]]): ZStream[R, E, B] =
    managed(ZManaged.make(acquire)(release))(read)

  final def managed[R: ConformsR, E, A, B](m: ZManaged[R, E, A])(read: A => ZIO[R, E, Option[B]]) =
    new ZStream[R, E, B] {
      override def fold[R1 <: R, E1 >: E, B1 >: B, S]: Fold[R1, E1, B1, S] =
        IO.succeedLazy { (s, cont, f) =>
          if (cont(s))
            m use { a =>
              def loop(s: S): ZIO[R1, E1, S] =
                read(a).flatMap {
                  case None => IO.succeed(s)
                  case Some(b) =>
                    f(s, b) flatMap { s =>
                      if (cont(s)) loop(s)
                      else IO.succeed(s)
                    }
                }

              loop(s)
            } else IO.succeed(s)
        }
    }

  /**
   * Constructs an infinite stream from a `Queue2`.
   */
  final def fromQueue[A](queue: Queue[A]): Stream[Nothing, A] =
    unfoldM(())(_ => queue.take.map(a => Some((a, ()))) <> IO.succeed(None))
  final def fromQueue[RB: ConformsR, EB, B](queue: Queue2[_, _, RB, EB, _, B]): ZStream[RB, EB, B] =
    unfoldM(())(_ => queue.take.map(b => Some((b, ()))) <> IO.succeed(None))

  /**
   * Constructs a stream from effectful state. This method should not be used
   * for resources that require safe release. See `Stream.fromResource`.
   */
  final def unfoldM[R: ConformsR, S, E, A](s: S)(f0: S => ZIO[R, E, Option[(A, S)]]): ZStream[R, E, A] =
    new ZStream[R, E, A] {
      override def fold[R1 <: R, E1 >: E, A1 >: A, S2]: Fold[R1, E1, A1, S2] =
        IO.succeedLazy { (s2, cont, f) =>
          def loop(s: S, s2: S2): ZIO[R1, E1, (S, S2)] =
            if (!cont(s2)) IO.succeed((s, s2))
            else
              f0(s).flatMap {
                case None => IO.succeed((s, s2))
                case Some((a, s)) =>
                  f(s2, a).flatMap(loop(s, _))
              }

          loop(s, s2).map(_._2)
        }
    }

  /**
   * Constructs a stream from state.
   */
  final def unfold[S, A](s: S)(f0: S => Option[(A, S)]): Stream[Nothing, A] =
    new StreamPure[A] {
      override def fold[R, E, A1 >: A, S2]: Fold[R, E, A1, S2] =
        IO.succeedLazy { (s2, cont, f) =>
          def loop(s: S, s2: S2): ZIO[R, E, (S, S2)] =
            if (!cont(s2)) IO.succeed((s, s2))
            else
              f0(s) match {
                case None => IO.succeed((s, s2))
                case Some((a, s)) =>
                  f(s2, a).flatMap(loop(s, _))
              }

          loop(s, s2).map(_._2)
        }

      override def foldPureLazy[A1 >: A, S2](s2: S2)(cont: S2 => Boolean)(f: (S2, A1) => S2): S2 = {
        def loop(s: S, s2: S2): (S, S2) =
          if (!cont(s2)) (s, s2)
          else
            f0(s) match {
              case None => (s, s2)
              case Some((a, s)) =>
                loop(s, f(s2, a))
            }

        loop(s, s2)._2
      }
    }

  /**
   * Constructs a stream from a range of integers (inclusive).
   */
  final def range(min: Int, max: Int): Stream[Nothing, Int] =
    unfold(min)(cur => if (cur > max) None else Some((cur, cur + 1)))
}

object Stream extends Stream_Functions {
  @implicitNotFound(
    "The environment type of all Stream methods must be Any. If you want to use an environment, please use StreamR."
  )
  sealed trait ConformsR1[A]

  type ConformsR[A] = ConformsR1[Any]
  implicit val ConformsAnyProof: ConformsR1[Any] = new ConformsR1[Any] {}
}

object ZStream extends Stream_Functions {

  sealed trait ConformsR1[A]

  private val _ConformsR1: ConformsR1[Any] = new ConformsR1[Any] {}

  type ConformsR[A] = ConformsR1[A]
  implicit def ConformsRProof[A]: ConformsR[A] = _ConformsR1.asInstanceOf[ConformsR1[A]]

  implicit val ConformsAnyProof: ConformsR[Any] = _ConformsR1

  type Fold[R, E, +A, S] = ZIO[R, Nothing, (S, S => Boolean, (S, A) => ZIO[R, E, S]) => ZIO[R, E, S]]
}
