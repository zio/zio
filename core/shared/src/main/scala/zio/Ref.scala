/*
 * Copyright 2017-2022 John A. De Goes and the ZIO Contributors
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

package zio

import com.github.ghik.silencer.silent
import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.stm.STM

import java.util.concurrent.atomic.AtomicReference

/**
 * A `Ref[RA, RB, EA, EB, A, B]` is a polymorphic, purely functional description
 * of a mutable reference. The fundamental operations of a `Ref` are `set` and
 * `get`. `set` takes a value of type `A` and sets the reference to a new value,
 * requiring an environment of type `RA` and potentially failing with an error
 * of type `EA`. `get` gets the current value of the reference and returns a
 * value of type `B`, requiring an environment of type `RB` and potentially
 * failing with an error of type `EB`.
 *
 * By default, `Ref` is implemented in terms of compare and swap operations for
 * maximum performance and does not support performing effects within update
 * operations. If you need to perform effects within update operations you can
 * create a `Ref.Synchronized`, a specialized type of `Ref` that supports
 * performing effects within update operations at some cost to performance. In
 * this case writes will semantically block other writers, while multiple
 * readers can read simultaneously.
 *
 * `Ref.Synchronized` also supports composing multiple `ZRRefef.Synchronized`
 * values together to form a single `Ref.Synchronized` value that can be
 * atomically updated using the `zip` operator. In this case reads and writes
 * will semantically block other readers and writers.
 *
 * NOTE: While `Ref` provides the functional equivalent of a mutable reference,
 * the value inside the `Ref` should normally be immutable since compare and
 * swap operations are not safe for mutable values that do not support
 * concurrent access. If you do need to use a mutable value `Ref.Synchronized`
 * will guarantee that access to the value is properly synchronized.
 */
sealed abstract class Ref[A] extends Serializable { self =>

  /**
   * Reads the value from the `Ref`.
   */
  def get(implicit trace: ZTraceElement): UIO[A]

  /**
   * Writes a new value to the `Ref`, with a guarantee of immediate consistency
   * (at some cost to performance).
   */
  def set(a: A)(implicit trace: ZTraceElement): UIO[Unit]

  /**
   * Writes a new value to the `Ref` without providing a guarantee of immediate
   * consistency.
   */
  def setAsync(a: A)(implicit trace: ZTraceElement): UIO[Unit]
}

object Ref extends Serializable {

  /**
   * Creates a new `Ref` with the specified value.
   */
  def make[A](a: => A)(implicit trace: ZTraceElement): UIO[Ref[A]] =
    UIO.succeed(unsafeMake(a))

  private[zio] def unsafeMake[A](a: A): Ref.Atomic[A] =
    Atomic(new AtomicReference(a))

  implicit class UnifiedSyntax[A](private val self: Ref[A]) extends AnyVal {

    /**
     * Atomically writes the specified value to the `Ref`, returning the value
     * immediately before modification.
     */
    def getAndSet(a: A)(implicit trace: ZTraceElement): UIO[A] =
      self match {
        case atomic: Atomic[A] => atomic.getAndSet(a)
        case derived           => derived.modify(v => (v, a))
      }

    /**
     * Atomically modifies the `Ref` with the specified function, returning the
     * value immediately before modification.
     */
    def getAndUpdate(f: A => A)(implicit trace: ZTraceElement): UIO[A] =
      self match {
        case atomic: Atomic[A] => atomic.getAndUpdate(f)
        case derived           => derived.modify(v => (v, f(v)))
      }

    /**
     * Atomically modifies the `Ref` with the specified partial function,
     * returning the value immediately before modification. If the function is
     * undefined on the current value it doesn't change it.
     */
    def getAndUpdateSome(pf: PartialFunction[A, A])(implicit trace: ZTraceElement): UIO[A] =
      self match {
        case atomic: Atomic[A] => atomic.getAndUpdateSome(pf)
        case derived =>
          derived.modify { v =>
            val result = pf.applyOrElse[A, A](v, identity)
            (v, result)
          }
      }

    /**
     * Atomically modifies the `Ref` with the specified function, which computes
     * a return value for the modification. This is a more powerful version of
     * `update`.
     */
    @silent("unreachable code")
    def modify[B](f: A => (B, A))(implicit trace: ZTraceElement): UIO[B] =
      self match {
        case atomic: Atomic[A] => atomic.modify(f)
        case ref: Synchronized[A] =>
          ref.modifyZIO(a => ZIO.succeedNow(f(a)))
      }

    /**
     * Atomically modifies the `Ref` with the specified partial function, which
     * computes a return value for the modification if the function is defined
     * on the current value otherwise it returns a default value. This is a more
     * powerful version of `updateSome`.
     */
    def modifySome[B](default: B)(pf: PartialFunction[A, (B, A)])(implicit trace: ZTraceElement): UIO[B] =
      self match {
        case atomic: Atomic[A] => atomic.modifySome(default)(pf)
        case derived =>
          derived.modify(v => pf.applyOrElse[A, (B, A)](v, _ => (default, v)))
      }

    /**
     * Atomically modifies the `Ref` with the specified function.
     */
    def update(f: A => A)(implicit trace: ZTraceElement): UIO[Unit] =
      self match {
        case atomic: Atomic[A] => atomic.update(f)
        case derived           => derived.modify(v => ((), f(v)))
      }

    /**
     * Atomically modifies the `Ref` with the specified function and returns the
     * updated value.
     */
    def updateAndGet(f: A => A)(implicit trace: ZTraceElement): UIO[A] =
      self match {
        case atomic: Atomic[A] => atomic.updateAndGet(f)
        case derived =>
          derived.modify { v =>
            val result = f(v)
            (result, result)
          }
      }

    /**
     * Atomically modifies the `Ref` with the specified partial function. If the
     * function is undefined on the current value it doesn't change it.
     */
    def updateSome(pf: PartialFunction[A, A])(implicit trace: ZTraceElement): UIO[Unit] =
      self match {
        case atomic: Atomic[A] => atomic.updateSome(pf)
        case derived =>
          derived.modify { v =>
            val result = pf.applyOrElse[A, A](v, identity)
            ((), result)
          }
      }

    /**
     * Atomically modifies the `Ref` with the specified partial function. If the
     * function is undefined on the current value it returns the old value
     * without changing it.
     */
    def updateSomeAndGet(pf: PartialFunction[A, A])(implicit trace: ZTraceElement): UIO[A] =
      self match {
        case atomic: Atomic[A] => atomic.updateSomeAndGet(pf)
        case derived =>
          derived.modify { v =>
            val result = pf.applyOrElse[A, A](v, identity)
            (result, result)
          }
      }
  }

  /**
   * A `Ref.Synchronized[RA, RB, EA, EB, A, B]` is a polymorphic, purely
   * functional description of a mutable reference. The fundamental operations
   * of a `Ref.Synchronized` are `set` and `get`. `set` takes a value of type
   * `A` and sets the reference to a new value, requiring an environment of type
   * `RA` and potentially failing with an error of type `EA`. `get` gets the
   * current value of the reference and returns a value of type `B`, requiring
   * an environment of type `RB` and potentially failing with an error of type
   * `EB`.
   *
   * When the error and value types of the `Ref.Synchronized` are unified, that
   * is, it is a `Ref.Synchronized[R, R, E, E, A, A]`, the `Ref.Synchronized`
   * also supports atomic `modify` and `update` operations.
   *
   * Unlike an ordinary `Ref`, a `Ref.Synchronized` allows performing effects
   * within update operations, at some cost to performance. Writes will
   * semantically block other writers, while multiple readers can read
   * simultaneously.
   *
   * `Ref.Synchronized` also supports composing multiple `Ref.Synchronized`
   * values together to form a single `Ref.Synchronized` value that can be
   * atomically updated using the `zip` operator. In this case reads and writes
   * will semantically block other readers and writers.
   */
  sealed abstract class Synchronized[A] extends Ref[A] { self =>

    protected def semaphores: Set[Semaphore]

    protected def unsafeGet(implicit trace: ZTraceElement): UIO[A]

    protected def unsafeSet(a: A)(implicit trace: ZTraceElement): UIO[Unit]

    protected def unsafeSetAsync(a: A)(implicit trace: ZTraceElement): UIO[Unit]

    /**
     * Reads the value from the `Ref`.
     */
    final def get(implicit trace: ZTraceElement): UIO[A] =
      if (semaphores.size == 1) unsafeGet else withPermit(unsafeGet)

    /**
     * Writes a new value to the `Ref`, with a guarantee of immediate
     * consistency (at some cost to performance).
     */
    final def set(a: A)(implicit trace: ZTraceElement): UIO[Unit] =
      withPermit(unsafeSet(a))

    /**
     * Writes a new value to the `Ref` without providing a guarantee of
     * immediate consistency.
     */
    final def setAsync(a: A)(implicit trace: ZTraceElement): UIO[Unit] =
      withPermit(unsafeSetAsync(a))

    /**
     * Performs the specified effect every time a value is written to this
     * `Ref.Synchronized`.
     */
    final def tapInput(f: A => UIO[Any])(implicit
      trace: ZTraceElement
    ): Synchronized[A] =
      ???

    private final def withPermit[R, E, A](zio: ZIO[R, E, A])(implicit trace: ZTraceElement): ZIO[R, E, A] =
      ZIO.uninterruptibleMask { restore =>
        restore(STM.foreach(semaphores)(_.acquire).commit) *>
          restore(zio).ensuring(STM.foreach(semaphores)(_.release).commit)
      }
  }

  object Synchronized {

    /**
     * Creates a new `RefM` and a `Dequeue` that will emit every change to the
     * `RefM`.
     */
    @deprecated("use SubscriptionRef", "2.0.0")
    def dequeueRef[A](a: => A)(implicit trace: ZTraceElement): UIO[(RefM[A], Dequeue[A])] =
      for {
        ref   <- make(a)
        queue <- Queue.unbounded[A]
      } yield (ref.tapInput(queue.offer), queue)

    /**
     * Creates a new `Ref.Synchronized` with the specified value.
     */
    def make[A](a: => A)(implicit trace: ZTraceElement): UIO[Synchronized[A]] =
      for {
        ref       <- Ref.make(a)
        semaphore <- Semaphore.make(1)
      } yield new Ref.Synchronized[A] {
        val semaphores: Set[Semaphore] =
          Set(semaphore)
        def unsafeGet(implicit trace: ZTraceElement): ZIO[Any, Nothing, A] =
          ref.get
        def unsafeSet(a: A)(implicit trace: ZTraceElement): ZIO[Any, Nothing, Unit] =
          ref.set(a)
        def unsafeSetAsync(a: A)(implicit trace: ZTraceElement): ZIO[Any, Nothing, Unit] =
          ref.setAsync(a)
      }

    implicit class UnifiedSyntax[A](private val self: Synchronized[A]) extends AnyVal {

      /**
       * Atomically modifies the `RefM` with the specified function, returning
       * the value immediately before modification.
       */
      @deprecated("use getAndUpdateZIO", "2.0.0")
      def getAndUpdateM[R, E](f: A => ZIO[R, E, A])(implicit trace: ZTraceElement): ZIO[R, E, A] =
        getAndUpdateZIO(f)

      /**
       * Atomically modifies the `Ref.Synchronized` with the specified function,
       * returning the value immediately before modification.
       */
      def getAndUpdateZIO[R, E](f: A => ZIO[R, E, A])(implicit trace: ZTraceElement): ZIO[R, E, A] =
        modifyZIO(v => f(v).map(result => (v, result)))

      /**
       * Atomically modifies the `RefM` with the specified partial function,
       * returning the value immediately before modification. If the function is
       * undefined on the current value it doesn't change it.
       */
      @deprecated("use getAndUpdateSomeZIO", "2.0.0")
      def getAndUpdateSomeM[R, E](pf: PartialFunction[A, ZIO[R, E, A]])(implicit
        trace: ZTraceElement
      ): ZIO[R, E, A] =
        getAndUpdateSomeZIO(pf)

      /**
       * Atomically modifies the `Ref.Synchronized` with the specified partial
       * function, returning the value immediately before modification. If the
       * function is undefined on the current value it doesn't change it.
       */
      def getAndUpdateSomeZIO[R, E](pf: PartialFunction[A, ZIO[R, E, A]])(implicit
        trace: ZTraceElement
      ): ZIO[R, E, A] =
        modifyZIO(v => pf.applyOrElse[A, ZIO[R, E, A]](v, ZIO.succeedNow).map(result => (v, result)))

      /**
       * Atomically modifies the `RefM` with the specified function, which
       * computes a return value for the modification. This is a more powerful
       * version of `update`.
       */
      @deprecated("use modifyZIO", "2.0.0")
      def modifyM[R, E, B](f: A => ZIO[R, E, (B, A)])(implicit trace: ZTraceElement): ZIO[R, E, B] =
        modifyZIO(f)

      /**
       * Atomically modifies the `Ref.Synchronized` with the specified function,
       * which computes a return value for the modification. This is a more
       * powerful version of `update`.
       */
      def modifyZIO[R, E, B](f: A => ZIO[R, E, (B, A)])(implicit trace: ZTraceElement): ZIO[R, E, B] =
        self.withPermit(self.unsafeGet.flatMap(f).flatMap { case (b, a) => self.unsafeSet(a).as(b) })

      /**
       * Atomically modifies the `RefM` with the specified function, which
       * computes a return value for the modification if the function is defined
       * in the current value otherwise it returns a default value. This is a
       * more powerful version of `updateSome`.
       */
      @deprecated("use modifySomeZIO", "2.0.0")
      def modifySomeM[R, E, B](default: B)(pf: PartialFunction[A, ZIO[R, E, (B, A)]])(implicit
        trace: ZTraceElement
      ): ZIO[R, E, B] =
        modifySomeZIO[R, E, B](default)(pf)

      /**
       * Atomically modifies the `Ref.Synchronized` with the specified function,
       * which computes a return value for the modification if the function is
       * defined in the current value otherwise it returns a default value. This
       * is a more powerful version of `updateSome`.
       */
      def modifySomeZIO[R, E, B](default: B)(pf: PartialFunction[A, ZIO[R, E, (B, A)]])(implicit
        trace: ZTraceElement
      ): ZIO[R, E, B] =
        modifyZIO(v => pf.applyOrElse[A, ZIO[R, E, (B, A)]](v, _ => ZIO.succeedNow((default, v))))

      /**
       * Atomically modifies the `RefM` with the specified function.
       */
      @deprecated("use updateZIO", "2.0.0")
      def updateM[R, E](f: A => ZIO[R, E, A])(implicit trace: ZTraceElement): ZIO[R, E, Unit] =
        updateZIO(f)

      /**
       * Atomically modifies the `Ref.Synchronized` with the specified function.
       */
      def updateZIO[R, E](f: A => ZIO[R, E, A])(implicit trace: ZTraceElement): ZIO[R, E, Unit] =
        modifyZIO(v => f(v).map(result => ((), result)))

      /**
       * Atomically modifies the `RefM` with the specified function, returning
       * the value immediately after modification.
       */
      @deprecated("use updateAndGetZIO", "2.0.0")
      def updateAndGetM[R, E](f: A => ZIO[R, E, A])(implicit trace: ZTraceElement): ZIO[R, E, A] =
        updateAndGetZIO(f)

      /**
       * Atomically modifies the `Ref.Synchronized` with the specified function,
       * returning the value immediately after modification.
       */
      def updateAndGetZIO[R, E](f: A => ZIO[R, E, A])(implicit trace: ZTraceElement): ZIO[R, E, A] =
        modifyZIO(v => f(v).map(result => (result, result)))

      /**
       * Atomically modifies the `RefM` with the specified partial function. If
       * the function is undefined on the current value it doesn't change it.
       */
      @deprecated("use updateSomeZIO", "2.0.0")
      def updateSomeM[R, E](pf: PartialFunction[A, ZIO[R, E, A]])(implicit
        trace: ZTraceElement
      ): ZIO[R, E, Unit] =
        updateSomeZIO(pf)

      /**
       * Atomically modifies the `Ref.Synchronized` with the specified partial
       * function. If the function is undefined on the current value it doesn't
       * change it.
       */
      def updateSomeZIO[R, E](pf: PartialFunction[A, ZIO[R, E, A]])(implicit
        trace: ZTraceElement
      ): ZIO[R, E, Unit] =
        modifyZIO(v => pf.applyOrElse[A, ZIO[R, E, A]](v, ZIO.succeedNow).map(result => ((), result)))

      /**
       * Atomically modifies the `RefM` with the specified partial function. If
       * the function is undefined on the current value it returns the old value
       * without changing it.
       */
      @deprecated("use updateSomeAndGetZIO", "2.0.0")
      def updateSomeAndGetM[R, E](pf: PartialFunction[A, ZIO[R, E, A]])(implicit
        trace: ZTraceElement
      ): ZIO[R, E, A] =
        updateSomeAndGetZIO(pf)

      /**
       * Atomically modifies the `Ref.Synchronized` with the specified partial
       * function. If the function is undefined on the current value it returns
       * the old value without changing it.
       */
      def updateSomeAndGetZIO[R, E](pf: PartialFunction[A, ZIO[R, E, A]])(implicit
        trace: ZTraceElement
      ): ZIO[R, E, A] =
        modifyZIO(v => pf.applyOrElse[A, ZIO[R, E, A]](v, ZIO.succeedNow).map(result => (result, result)))
    }
  }

  private[zio] final case class Atomic[A](value: AtomicReference[A]) extends Ref[A] {
    self =>

    def get(implicit trace: ZTraceElement): UIO[A] =
      ZIO.succeed(unsafeGet)

    def getAndSet(a: A)(implicit trace: ZTraceElement): UIO[A] =
      ZIO.succeed(unsafeGetAndSet(a))

    def getAndUpdate(f: A => A)(implicit trace: ZTraceElement): UIO[A] =
      ZIO.succeed(unsafeGetAndUpdate(f))

    def getAndUpdateSome(pf: PartialFunction[A, A])(implicit trace: ZTraceElement): UIO[A] =
      ZIO.succeed(unsafeGetAndUpdateSome(pf))

    def modify[B](f: A => (B, A))(implicit trace: ZTraceElement): UIO[B] =
      ZIO.succeed(unsafeModify(f))

    def modifySome[B](default: B)(pf: PartialFunction[A, (B, A)])(implicit trace: ZTraceElement): UIO[B] =
      ZIO.succeed(unsafeModifySome(default)(pf))

    def set(a: A)(implicit trace: ZTraceElement): UIO[Unit] =
      ZIO.succeed(unsafeSet(a))

    def setAsync(a: A)(implicit trace: ZTraceElement): UIO[Unit] =
      ZIO.succeed(unsafeSetAsync(a))

    override def toString: String =
      s"Ref(${value.get})"

    def unsafeGet: A =
      value.get

    def unsafeGetAndSet(a: A): A = {
      var loop       = true
      var current: A = null.asInstanceOf[A]
      while (loop) {
        current = value.get
        loop = !value.compareAndSet(current, a)
      }
      current
    }

    def unsafeGetAndUpdate(f: A => A): A = {
      var loop       = true
      var current: A = null.asInstanceOf[A]
      while (loop) {
        current = value.get
        val next = f(current)
        loop = !value.compareAndSet(current, next)
      }
      current
    }

    def unsafeGetAndUpdateSome(pf: PartialFunction[A, A]): A = {
      var loop       = true
      var current: A = null.asInstanceOf[A]
      while (loop) {
        current = value.get
        val next = pf.applyOrElse(current, (_: A) => current)
        loop = !value.compareAndSet(current, next)
      }
      current
    }

    def unsafeModify[B](f: A => (B, A)): B = {
      var loop = true
      var b: B = null.asInstanceOf[B]
      while (loop) {
        val current = value.get
        val tuple   = f(current)
        b = tuple._1
        loop = !value.compareAndSet(current, tuple._2)
      }
      b
    }

    def unsafeModifySome[B](default: B)(pf: PartialFunction[A, (B, A)]): B = {
      var loop = true
      var b: B = null.asInstanceOf[B]
      while (loop) {
        val current = value.get
        val tuple   = pf.applyOrElse(current, (_: A) => (default, current))
        b = tuple._1
        loop = !value.compareAndSet(current, tuple._2)
      }
      b
    }

    def unsafeSet(a: A): Unit =
      value.set(a)

    def unsafeSetAsync(a: A): Unit =
      value.lazySet(a)

    def unsafeUpdate(f: A => A): Unit = {
      var loop    = true
      var next: A = null.asInstanceOf[A]
      while (loop) {
        val current = value.get
        next = f(current)
        loop = !value.compareAndSet(current, next)
      }
      ()
    }

    def unsafeUpdateAndGet(f: A => A): A = {
      var loop    = true
      var next: A = null.asInstanceOf[A]
      while (loop) {
        val current = value.get
        next = f(current)
        loop = !value.compareAndSet(current, next)
      }
      next
    }

    def unsafeUpdateSome(pf: PartialFunction[A, A]): Unit = {
      var loop    = true
      var next: A = null.asInstanceOf[A]
      while (loop) {
        val current = value.get
        next = pf.applyOrElse(current, (_: A) => current)
        loop = !value.compareAndSet(current, next)
      }
      ()
    }

    def unsafeUpdateSomeAndGet(pf: PartialFunction[A, A]): A = {
      var loop    = true
      var next: A = null.asInstanceOf[A]
      while (loop) {
        val current = value.get
        next = pf.applyOrElse(current, (_: A) => current)
        loop = !value.compareAndSet(current, next)
      }
      next
    }

    def update(f: A => A)(implicit trace: ZTraceElement): UIO[Unit] =
      ZIO.succeed(unsafeUpdate(f))

    def updateAndGet(f: A => A)(implicit trace: ZTraceElement): UIO[A] =
      ZIO.succeed(unsafeUpdateAndGet(f))

    def updateSome(pf: PartialFunction[A, A])(implicit trace: ZTraceElement): UIO[Unit] =
      ZIO.succeed(unsafeUpdateSome(pf))

    def updateSomeAndGet(pf: PartialFunction[A, A])(implicit trace: ZTraceElement): UIO[A] =
      ZIO.succeed(unsafeUpdateSomeAndGet(pf))
  }
}
