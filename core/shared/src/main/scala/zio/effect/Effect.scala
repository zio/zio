package zio.effect

import zio.internal.Platform
import zio._

trait Effect extends Serializable {
  val effect: Effect.Service[Any]
}

object Effect extends Serializable {

  trait Service[R] extends Serializable {

    /**
     *
     * Imports a synchronous effect into a pure `ZIO` value, translating any
     * throwables into a `Throwable` failure in the returned value.
     *
     * {{{
     * def putStrLn(line: String): Task[Unit] = Task.effect(println(line))
     * }}}
     */
    def apply[A](effect: => A): Task[A]

    /**
     * Imports an asynchronous effect into a pure `ZIO` value. See `effectAsyncMaybe` for
     * the more expressive variant of this function that can return a value
     * synchronously.
     *
     * The callback function `ZIO[R, E, A] => Unit` must be called at most once.
     */
    def async[E, A](register: (ZIO[R, E, A] => Unit) => Unit): ZIO[R, E, A] =
      asyncMaybe((callback: ZIO[R, E, A] => Unit) => {
        register(callback)

        None
      })

    /**
     * Imports an asynchronous effect into a pure `IO` value. The effect has the
     * option of returning the value synchronously, which is useful in cases
     * where it cannot be determined if the effect is synchronous or asynchronous
     * until the effect is actually executed. The effect also has the option of
     * returning a canceler, which will be used by the runtime to cancel the
     * asynchronous effect if the fiber executing the effect is interrupted.
     *
     * If the register function returns a value synchronously then the callback
     * function `ZIO[R, E, A] => Unit` must not be called. Otherwise the callback
     * function must be called at most once.
     */
    def asyncInterrupt[E, A](
      register: (ZIO[R, E, A] => Unit) => Either[Canceler, ZIO[R, E, A]]
    ): ZIO[R, E, A] = {
      import java.util.concurrent.atomic.AtomicBoolean
      import internal.OneShot

      total((new AtomicBoolean(false), OneShot.make[UIO[Any]])).flatMap {
        case (started, cancel) =>
          ZIO.flatten {
            asyncMaybe((k: UIO[ZIO[R, E, A]] => Unit) => {
              started.set(true)

              try register(io => k(ZIO.succeed(io))) match {
                case Left(canceler) =>
                  cancel.set(canceler)
                  None
                case Right(io) => Some(ZIO.succeed(io))
              } finally if (!cancel.isSet) cancel.set(ZIO.unit)
            })
          }.onInterrupt(ZIO.flatten(total(if (started.get) cancel.get() else ZIO.unit)))
      }
    }

    /**
     * Imports an asynchronous effect into a pure `ZIO` value. This formulation is
     * necessary when the effect is itself expressed in terms of `ZIO`.
     */
    def asyncM[E, A](register: (ZIO[R, E, A] => Unit) => ZIO[R, E, _]): ZIO[R, E, A] =
      for {
        p <- Promise.make[E, A]
        r <- ZIO.runtime[R]
        a <- ZIO.uninterruptibleMask { restore =>
              restore(
                register(k => r.unsafeRunAsync_(k.to(p)))
                  .catchAll(p.fail)
              ).fork.flatMap { f =>
                restore(p.await).onInterrupt(f.interrupt)
              }
            }
      } yield a

    /**
     * Imports an asynchronous effect into a pure `ZIO` value, possibly returning
     * the value synchronously.
     */
    def asyncMaybe[E, A](register: (ZIO[R, E, A] => Unit) => Option[ZIO[R, E, A]]): ZIO[R, E, A]

    /**
     * Returns a lazily constructed effect, whose construction may itself require effects.
     * When no environment is required (i.e., when R == Any) it is conceptually equivalent to `flatten(effect(io))`.
     */
    def suspend[A](rio: => RIO[R, A]): RIO[R, A]

    /**
     *  Returns a lazily constructed effect, whose construction may itself require
     * effects. The effect must not throw any exceptions. When no environment is required (i.e., when R == Any)
     * it is conceptually equivalent to `flatten(effectTotal(zio))`. If you wonder if the effect throws exceptions,
     * do not use this method, use [[Effect.Live.effect.suspend]].
     */
    def suspendTotal[E, A](zio: => ZIO[R, E, A]): ZIO[R, E, A]

    /**
     * Returns a lazily constructed effect, whose construction may itself require effects.
     * The effect must not throw any exceptions. When no environment is required (i.e., when R == Any)
     * it is conceptually equivalent to `flatten(effectTotal(zio))`. If you wonder if the effect throws exceptions,
     * do not use this method, use [[Effect.Live.effect.suspend]].
     */
    def suspendTotalWith[E, A](p: Platform => ZIO[R, E, A]): ZIO[R, E, A]

    /**
     * Returns a lazily constructed effect, whose construction may itself require effects.
     * When no environment is required (i.e., when R == Any) it is conceptually equivalent to `flatten(effect(io))`.
     */
    def suspendWith[A](p: Platform => RIO[R, A]): RIO[R, A]

    /**
     * Imports a total synchronous effect into a pure `ZIO` value.
     * The effect must not throw any exceptions. If you wonder if the effect
     * throws exceptions, then do not use this method, use [[Effect.Live.effect]].
     *
     * {{{
     * val nanoTime: UIO[Long] = IO.effectTotal(System.nanoTime())
     * }}}
     */
    def total[A](effect: => A): UIO[A]
  }

  trait Live extends Effect {

    val effect: Service[Any] = new Service[Any] {

      override def apply[A](effect: => A): Task[A] =
        new ZIO.EffectPartial(() => effect)

      override def asyncMaybe[E, A](
        register: (ZIO[Any, E, A] => Unit) => Option[ZIO[Any, E, A]]
      ): ZIO[Any, E, A] =
        new ZIO.EffectAsync(register)

      override def suspend[A](rio: => RIO[Any, A]): RIO[Any, A] =
        new ZIO.EffectSuspendPartialWith(_ => rio)

      override def suspendTotal[E, A](zio: => ZIO[Any, E, A]): ZIO[Any, E, A] =
        new ZIO.EffectSuspendTotalWith(_ => zio)

      override def suspendTotalWith[E, A](p: Platform => ZIO[Any, E, A]): ZIO[Any, E, A] =
        new ZIO.EffectSuspendTotalWith(p)

      override def suspendWith[A](p: Platform => RIO[Any, A]): RIO[Any, A] =
        new ZIO.EffectSuspendPartialWith(p)

      override def total[A](effect: => A): UIO[A] =
        new ZIO.EffectTotal(() => effect)
    }
  }

  object Live extends Live
}
