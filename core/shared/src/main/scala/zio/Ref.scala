/*
 * Copyright 2017-2024 John A. De Goes and the ZIO Contributors
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

import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.util.concurrent.atomic.AtomicReference

/**
 * A `Ref` is a purely functional description of a mutable reference that fibers
 * can share. The fundamental operations of a `Ref` are `set` and `get`. `set`
 * sets the reference to a new value. `get` gets the current value of the
 * reference. `update` and `modify` change the value with a function, as one
 * atomic step with respect to the other operations on the same `Ref`.
 *
 * A `Ref` created with `Ref.make` is implemented with compare-and-swap
 * operations on an `AtomicReference`. When other fibers change the value
 * between the read and the write, the update function is called again with the
 * new value, so it can run more than once for one call and should have no side
 * effects. To run effects within update operations, create a
 * `Ref.Synchronized`, a `Ref` that runs its updates one at a time, at some cost
 * to performance. Its writes wait for one another, while its `get` does not
 * wait.
 *
 * The value inside a `Ref` should normally be immutable: the compare-and-swap
 * operations do not protect a mutable value that is changed in place.
 * `Ref.Synchronized` runs its updates one at a time, but its `get` can still
 * return the mutable value while an update changes it.
 *
 * {{{
 * val count: UIO[Int] =
 *   for {
 *     ref <- Ref.make(0)
 *     _   <- ZIO.foreachParDiscard(1 to 100)(_ => ref.update(_ + 1))
 *     n   <- ref.get
 *   } yield n
 * // succeeds with 100
 * }}}
 *
 * @see
 *   [[https://zio.dev/reference/concurrency/ref/ Ref]]
 */
sealed abstract class Ref[A] extends Serializable {

  /**
   * Reads the value from the `Ref`.
   */
  def get(implicit trace: Trace): UIO[A]

  /**
   * Atomically modifies the `Ref` with the specified function, which computes a
   * return value for the modification. `f` receives the current value and
   * returns a pair: the first element is what `modify` returns and the second
   * one is the new value. This is a more powerful version of `update`.
   *
   * On a `Ref` created with `Ref.make`, `f` can run more than once when other
   * fibers change the value at the same time.
   *
   * {{{
   * val ticket: UIO[String] =
   *   for {
   *     next <- Ref.make(41)
   *     n    <- next.modify(n => (s"ticket-\$n", n + 1))
   *   } yield n
   * // succeeds with ticket-41, and the Ref then holds 42
   * }}}
   */
  def modify[B](f: A => (B, A))(implicit trace: Trace): UIO[B]

  /**
   * Writes a new value to the `Ref`, with a guarantee of immediate consistency
   * (at some cost to performance).
   */
  def set(a: A)(implicit trace: Trace): UIO[Unit]

  /**
   * Writes a new value to the `Ref` without providing a guarantee of immediate
   * consistency.
   */
  def setAsync(a: A)(implicit trace: Trace): UIO[Unit]

  /**
   * Atomically writes the specified value to the `Ref`, returning the value
   * immediately before modification.
   */
  def getAndSet(a: A)(implicit trace: Trace): UIO[A] =
    modify(v => (v, a))

  /**
   * Atomically modifies the `Ref` with the specified function, returning the
   * value immediately before modification.
   */
  def getAndUpdate(f: A => A)(implicit trace: Trace): UIO[A] =
    modify(v => (v, f(v)))

  /**
   * Atomically modifies the `Ref` with the specified partial function,
   * returning the value immediately before modification. If the function is
   * undefined on the current value it doesn't change it.
   */
  def getAndUpdateSome(pf: PartialFunction[A, A])(implicit trace: Trace): UIO[A] =
    modify { v =>
      val result = pf.applyOrElse[A, A](v, identity)
      (v, result)
    }

  /**
   * Atomically modifies the `Ref` with the specified partial function, which
   * computes a return value for the modification if the function is defined on
   * the current value. Otherwise it returns `default` and leaves the value
   * unchanged. This is a more powerful version of `updateSome`.
   */
  def modifySome[B](default: B)(pf: PartialFunction[A, (B, A)])(implicit trace: Trace): UIO[B] =
    modify(v => pf.applyOrElse[A, (B, A)](v, _ => (default, v)))

  /**
   * Atomically modifies the `Ref` with the specified function.
   *
   * On a `Ref` created with `Ref.make`, `f` can run more than once when other
   * fibers change the value at the same time.
   */
  def update(f: A => A)(implicit trace: Trace): UIO[Unit] =
    modify(v => ((), f(v)))

  /**
   * Atomically modifies the `Ref` with the specified function and returns the
   * updated value.
   */
  def updateAndGet(f: A => A)(implicit trace: Trace): UIO[A] =
    modify { v =>
      val result = f(v)
      (result, result)
    }

  /**
   * Atomically modifies the `Ref` with the specified partial function. If the
   * function is undefined on the current value it doesn't change it.
   */
  def updateSome(pf: PartialFunction[A, A])(implicit trace: Trace): UIO[Unit] =
    modify { v =>
      val result = pf.applyOrElse[A, A](v, identity)
      ((), result)
    }

  /**
   * Atomically modifies the `Ref` with the specified partial function. If the
   * function is undefined on the current value it returns the old value without
   * changing it.
   */
  def updateSomeAndGet(pf: PartialFunction[A, A])(implicit trace: Trace): UIO[A] =
    modify { v =>
      val result = pf.applyOrElse[A, A](v, identity)
      (result, result)
    }

  /**
   * Atomically increments the current value of the `Ref` by 1, returning the
   * value immediately before modification.
   */
  final def getAndIncrement(implicit num: math.Numeric[A], trace: Trace): UIO[A] =
    getAndUpdate(v => num.plus(v, num.one))

  /**
   * Atomically decrements the current value of the `Ref` by 1, returning the
   * value immediately before modification.
   */
  final def getAndDecrement(implicit num: math.Numeric[A], trace: Trace): UIO[A] =
    getAndUpdate(v => num.minus(v, num.one))

  /**
   * Atomically adds `delta` to the current value of the `Ref`, returning the
   * value immediately before modification.
   */
  final def getAndAdd(delta: A)(implicit num: math.Numeric[A], trace: Trace): UIO[A] =
    getAndUpdate(v => num.plus(v, delta))

  /**
   * Atomically increments the current value of the `Ref` by 1 and returns the
   * updated value.
   */
  final def incrementAndGet(implicit num: math.Numeric[A], trace: Trace): UIO[A] =
    updateAndGet(v => num.plus(v, num.one))

  /**
   * Atomically decrements the current value of the `Ref` by 1 and returns the
   * updated value.
   */
  final def decrementAndGet(implicit num: math.Numeric[A], trace: Trace): UIO[A] =
    updateAndGet(v => num.minus(v, num.one))

  /**
   * Atomically adds `delta` to the current value of the `Ref` and returns the
   * updated value.
   */
  final def addAndGet(delta: A)(implicit num: math.Numeric[A], trace: Trace): UIO[A] =
    updateAndGet(v => num.plus(v, delta))

}

/**
 * Constructors for [[Ref]], and `Ref.Synchronized`, a `Ref` whose updates can
 * run effects.
 */
object Ref extends Serializable {

  /**
   * Creates a new `Ref` with the specified value. Each run of the returned
   * effect evaluates `a` and creates a new `Ref`.
   */
  def make[A](a: => A)(implicit trace: Trace): UIO[Ref[A]] =
    ZIO.succeed(unsafe.make(a)(Unsafe))

  /**
   * Constructors that create a `Ref` outside of a ZIO effect. They need an
   * implicit `Unsafe` value, which `Unsafe.unsafe` provides.
   */
  object unsafe {

    /**
     * Creates a new `Ref` with the specified value, outside of a ZIO effect.
     */
    def make[A](a: A)(implicit unsafe: Unsafe): Ref.Atomic[A] = new Atomic[A](a)
  }

  /**
   * A `Ref.Synchronized` is a purely functional description of a mutable
   * reference. The fundamental operations of a `Ref.Synchronized` are `set` and
   * `get`. `set` sets the reference to a new value. `get` gets the current
   * value of the reference.
   *
   * Unlike an ordinary `Ref`, a `Ref.Synchronized` allows performing effects
   * within update operations (`modifyZIO`, `updateZIO` and the other `*ZIO`
   * methods), at some cost to performance. It runs its writes one at a time:
   * `set` and every update, including the pure ones inherited from `Ref`, wait
   * for the write in progress to finish, so the update function runs once per
   * call. `get` does not wait: while an update is in progress it returns the
   * value from before that update. When the effect of an update fails or is
   * interrupted, the value is left unchanged.
   *
   * {{{
   * def fetchName(id: Int): UIO[String] = ZIO.succeed(s"user-\$id")
   *
   * val names: UIO[List[(Int, String)]] =
   *   for {
   *     cache <- Ref.Synchronized.make(Map.empty[Int, String])
   *     _ <- ZIO.foreachParDiscard(List(1, 2, 1)) { id =>
   *            cache.updateZIO { m =>
   *              if (m.contains(id)) ZIO.succeed(m)
   *              else fetchName(id).map(name => m + (id -> name))
   *            }
   *          }
   *     m <- cache.get
   *   } yield m.toList.sortBy(_._1)
   * // succeeds with List((1,user-1), (2,user-2))
   * }}}
   *
   * @see
   *   [[https://zio.dev/reference/concurrency/refsynchronized/ Ref.Synchronized]]
   */
  sealed abstract class Synchronized[A] extends Ref[A] {

    /**
     * Reads the value from the `Ref`. It does not wait for an update in
     * progress: it returns the value from before that update.
     */
    def get(implicit trace: Trace): UIO[A]

    /**
     * Atomically modifies the `Ref.Synchronized` with the specified effectful
     * function, which computes a return value for the modification. The effect
     * returns a pair: the first element is the result and the second one is the
     * new value. When the effect fails, `modifyZIO` fails with the same error;
     * when it fails or is interrupted, the value is left unchanged. This is a
     * more powerful version of `updateZIO`.
     */
    def modifyZIO[R, E, B](f: A => ZIO[R, E, (B, A)])(implicit trace: Trace): ZIO[R, E, B]

    /**
     * Writes a new value to the `Ref`, with a guarantee of immediate
     * consistency (at some cost to performance). It waits for an update in
     * progress to finish first.
     */
    def set(a: A)(implicit trace: Trace): UIO[Unit]

    /**
     * Writes a new value to the `Ref` without providing a guarantee of
     * immediate consistency. It waits for an update in progress to finish
     * first.
     */
    def setAsync(a: A)(implicit trace: Trace): UIO[Unit]

    /**
     * Atomically modifies the `Ref.Synchronized` with the specified function,
     * returning the value immediately before modification.
     */
    def getAndUpdateZIO[R, E](f: A => ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
      modifyZIO(v => f(v).map(result => (v, result)))

    /**
     * Atomically modifies the `Ref.Synchronized` with the specified partial
     * function, returning the value immediately before modification. If the
     * function is undefined on the current value it doesn't change it.
     */
    def getAndUpdateSomeZIO[R, E](pf: PartialFunction[A, ZIO[R, E, A]])(implicit
      trace: Trace
    ): ZIO[R, E, A] =
      modifyZIO(v => pf.applyOrElse[A, ZIO[R, E, A]](v, ZIO.succeed(_)).map(result => (v, result)))

    /**
     * Atomically modifies the `Ref.Synchronized` with the specified function,
     * which computes a return value for the modification. It waits for an
     * update in progress to finish first, and calls `f` once.
     */
    final def modify[B](f: A => (B, A))(implicit trace: Trace): UIO[B] =
      modifyZIO(a => ZIO.succeed(f(a)))

    /**
     * Atomically modifies the `Ref.Synchronized` with the specified function,
     * which computes a return value for the modification if the function is
     * defined on the current value. Otherwise it returns `default` and leaves
     * the value unchanged. This is a more powerful version of `updateSomeZIO`.
     */
    def modifySomeZIO[R, E, B](default: B)(pf: PartialFunction[A, ZIO[R, E, (B, A)]])(implicit
      trace: Trace
    ): ZIO[R, E, B] =
      modifyZIO(v => pf.applyOrElse[A, ZIO[R, E, (B, A)]](v, _ => ZIO.succeed((default, v))))

    /**
     * Atomically modifies the `Ref.Synchronized` with the specified function.
     */
    def updateZIO[R, E](f: A => ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, Unit] =
      modifyZIO(v => f(v).map(result => ((), result)))

    /**
     * Atomically modifies the `Ref.Synchronized` with the specified function,
     * returning the value immediately after modification.
     */
    def updateAndGetZIO[R, E](f: A => ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
      modifyZIO(v => f(v).map(result => (result, result)))

    /**
     * Atomically modifies the `Ref.Synchronized` with the specified partial
     * function. If the function is undefined on the current value it doesn't
     * change it.
     */
    def updateSomeZIO[R, E](pf: PartialFunction[A, ZIO[R, E, A]])(implicit
      trace: Trace
    ): ZIO[R, E, Unit] =
      modifyZIO(v => pf.applyOrElse[A, ZIO[R, E, A]](v, ZIO.succeed(_)).map(result => ((), result)))

    /**
     * Atomically modifies the `Ref.Synchronized` with the specified partial
     * function. If the function is undefined on the current value it returns
     * the old value without changing it.
     */
    def updateSomeAndGetZIO[R, E](pf: PartialFunction[A, ZIO[R, E, A]])(implicit
      trace: Trace
    ): ZIO[R, E, A] =
      modifyZIO(v => pf.applyOrElse[A, ZIO[R, E, A]](v, ZIO.succeed(_)).map(result => (result, result)))
  }

  /**
   * Constructors for `Ref.Synchronized`.
   */
  object Synchronized {
    private[zio] abstract class Internal[A] extends Synchronized[A]

    /**
     * Creates a new `Ref.Synchronized` with the specified value.
     */
    def make[A](a: => A)(implicit trace: Trace): UIO[Synchronized[A]] =
      ZIO.succeed(unsafe.make(a)(Unsafe))

    /**
     * Constructors that create a `Ref.Synchronized` outside of a ZIO effect.
     * They need an implicit `Unsafe` value.
     */
    object unsafe {

      /**
       * Creates a new `Ref.Synchronized` with the specified value, outside of a
       * ZIO effect.
       */
      def make[A](a: A)(implicit unsafe: Unsafe): Synchronized[A] = {
        val ref       = Ref.unsafe.make(a)
        val semaphore = Semaphore.unsafe.make(1)
        new Ref.Synchronized[A] {
          def get(implicit trace: Trace): UIO[A] =
            ref.get
          def modifyZIO[R, E, B](f: A => ZIO[R, E, (B, A)])(implicit trace: Trace): ZIO[R, E, B] =
            semaphore.withPermit(get.flatMap(f).flatMap { case (b, a) => ref.set(a).as(b) })
          def set(a: A)(implicit trace: Trace): UIO[Unit] =
            semaphore.withPermit(ref.set(a))
          def setAsync(a: A)(implicit trace: Trace): UIO[Unit] =
            semaphore.withPermit(ref.setAsync(a))
        }
      }
    }
  }

  @deprecated("Kept for binary compatibility only. Do not use", "2.1.15")
  private[zio] object Atomic {}
  private[zio] final class Atomic[A](initial: A) extends Ref[A] { self =>
    override def get(implicit trace: Trace): UIO[A] =
      ZIO.succeed(unsafe.get(Unsafe))

    override def getAndSet(a: A)(implicit trace: Trace): UIO[A] =
      ZIO.succeed(unsafe.getAndSet(a)(Unsafe))

    override def getAndUpdate(f: A => A)(implicit trace: Trace): UIO[A] =
      ZIO.succeed(unsafe.getAndUpdate(f)(Unsafe))

    override def getAndUpdateSome(pf: PartialFunction[A, A])(implicit trace: Trace): UIO[A] =
      ZIO.succeed(unsafe.getAndUpdateSome(pf)(Unsafe))

    override def modify[B](f: A => (B, A))(implicit trace: Trace): UIO[B] =
      ZIO.succeed(unsafe.modify(f)(Unsafe))

    override def modifySome[B](default: B)(pf: PartialFunction[A, (B, A)])(implicit trace: Trace): UIO[B] =
      ZIO.succeed(unsafe.modifySome(default)(pf)(Unsafe))

    override def set(a: A)(implicit trace: Trace): UIO[Unit] =
      ZIO.succeed(unsafe.set(a)(Unsafe))

    override def setAsync(a: A)(implicit trace: Trace): UIO[Unit] =
      ZIO.succeed(unsafe.setAsync(a)(Unsafe))

    override def update(f: A => A)(implicit trace: Trace): UIO[Unit] =
      ZIO.succeed(unsafe.update(f)(Unsafe))

    override def updateAndGet(f: A => A)(implicit trace: Trace): UIO[A] =
      ZIO.succeed(unsafe.updateAndGet(f)(Unsafe))

    override def updateSome(pf: PartialFunction[A, A])(implicit trace: Trace): UIO[Unit] =
      ZIO.succeed(unsafe.updateSome(pf)(Unsafe))

    override def updateSomeAndGet(pf: PartialFunction[A, A])(implicit trace: Trace): UIO[A] =
      ZIO.succeed(unsafe.updateSomeAndGet(pf)(Unsafe))

    trait UnsafeAPI extends Serializable {
      def get(implicit unsafe: Unsafe): A
      def getAndSet(a: A)(implicit unsafe: Unsafe): A
      def getAndUpdate(f: A => A)(implicit unsafe: Unsafe): A
      def getAndUpdateSome(pf: PartialFunction[A, A])(implicit unsafe: Unsafe): A
      def modify[B](f: A => (B, A))(implicit unsafe: Unsafe): B
      def modifySome[B](default: B)(pf: PartialFunction[A, (B, A)])(implicit unsafe: Unsafe): B
      def set(a: A)(implicit unsafe: Unsafe): Unit
      def setAsync(a: A)(implicit unsafe: Unsafe): Unit
      def update(f: A => A)(implicit unsafe: Unsafe): Unit
      def updateAndGet(f: A => A)(implicit unsafe: Unsafe): A
      def updateSome(pf: PartialFunction[A, A])(implicit unsafe: Unsafe): Unit
      def updateSomeAndGet(pf: PartialFunction[A, A])(implicit unsafe: Unsafe): A
    }

    val unsafe: UnsafeAPI = {
      // NOTE: Instantiate the AtomicReference with a null and then set its value
      // so that the initial value is not added as a class field allowing it to be GC'd
      val ref0 = new AtomicReference[A]() with UnsafeAPI { ref: AtomicReference[A] =>
        def get(implicit unsafe: Unsafe): A =
          ref.asInstanceOf[AtomicReference[A]].get

        def getAndSet(a: A)(implicit unsafe: Unsafe): A =
          ref.asInstanceOf[AtomicReference[A]].getAndSet(a)

        def getAndUpdate(f: A => A)(implicit unsafe: Unsafe): A =
          ref.asInstanceOf[AtomicReference[A]].getAndUpdate(f.apply)

        def getAndUpdateSome(pf: PartialFunction[A, A])(implicit unsafe: Unsafe): A =
          ref
            .asInstanceOf[AtomicReference[A]]
            .getAndUpdate((current: A) => pf.applyOrElse(current, (_: Any) => current))

        def modify[B](f: A => (B, A))(implicit unsafe: Unsafe): B = {
          var loop = true
          var b: B = null.asInstanceOf[B]
          while (loop) {
            val current = ref.asInstanceOf[AtomicReference[A]].get
            val tuple   = f(current)
            b = tuple._1
            loop = !ref.compareAndSet(current, tuple._2)
          }
          b
        }

        def modifySome[B](default: B)(pf: PartialFunction[A, (B, A)])(implicit unsafe: Unsafe): B = {
          var loop = true
          var b: B = null.asInstanceOf[B]
          while (loop) {
            val current = ref.asInstanceOf[AtomicReference[A]].get
            val tuple   = pf.applyOrElse(current, (_: Any) => (default, current))
            b = tuple._1
            loop = !ref.compareAndSet(current, tuple._2)
          }
          b
        }

        def set(a: A)(implicit unsafe: Unsafe): Unit =
          ref.asInstanceOf[AtomicReference[A]].set(a)

        def setAsync(a: A)(implicit unsafe: Unsafe): Unit =
          ref.lazySet(a)

        def update(f: A => A)(implicit unsafe: Unsafe): Unit =
          ref.asInstanceOf[AtomicReference[A]].updateAndGet(f.apply)

        def updateAndGet(f: A => A)(implicit unsafe: Unsafe): A =
          ref.asInstanceOf[AtomicReference[A]].updateAndGet(f.apply)

        def updateSome(pf: PartialFunction[A, A])(implicit unsafe: Unsafe): Unit =
          ref
            .asInstanceOf[AtomicReference[A]]
            .updateAndGet((current: A) => pf.applyOrElse(current, (_: Any) => current))

        def updateSomeAndGet(pf: PartialFunction[A, A])(implicit unsafe: Unsafe): A =
          ref
            .asInstanceOf[AtomicReference[A]]
            .updateAndGet((current: A) => pf.applyOrElse(current, (_: Any) => current))
      }
      ref0.asInstanceOf[AtomicReference[A]].set(initial)
      ref0
    }

    @deprecated("Kept for binary compatibility only. Do not use", "2.1.15")
    private[zio] def value: AtomicReference[A] = unsafe.asInstanceOf[AtomicReference[A]]
  }
}
