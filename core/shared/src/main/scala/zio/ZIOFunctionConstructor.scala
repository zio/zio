package zio

/**
 * A `ZIOFunctionConstructor[Input]` knows how to construct a `ZIO` value from a
 * function of type `In`. This allows the type of the `ZIO` value constructed to
 * depend on `In`.
 */
sealed abstract class ZIOFunctionConstructor[In] {

  /**
   * The type of the `ZIO` value.
   */
  type Out

  /**
   * Constructs a `ZIO` value from the specified input.
   */
  def apply(in: In)(implicit trace: Trace): Out
}

object ZIOFunctionConstructor {

  type WithOut[In, Out0] = ZIOFunctionConstructor[In] { type Out = Out0 }

  implicit def function1Constructor[A: Tag, R, E, Z]
    : ZIOFunctionConstructor.WithOut[A => ZIO[R, E, Z], ZIO[R with A, E, Z]] =
    new ZIOFunctionConstructor[A => ZIO[R, E, Z]] {
      type Out = ZIO[R with A, E, Z]
      def apply(f: A => ZIO[R, E, Z])(implicit trace: Trace): ZIO[R with A, E, Z] =
        ZIO.serviceWithZIO[A](f)
    }

  implicit def function2Constructor[A: Tag, B: Tag, R, E, Z]
    : ZIOFunctionConstructor.WithOut[(A, B) => ZIO[R, E, Z], ZIO[R with A with B, E, Z]] =
    new ZIOFunctionConstructor[(A, B) => ZIO[R, E, Z]] {
      type Out = ZIO[R with A with B, E, Z]
      def apply(f: (A, B) => ZIO[R, E, Z])(implicit trace: Trace): ZIO[R with A with B, E, Z] =
        ZIO.environmentWithZIO[A with B](env => f(env.get[A], env.get[B]))
    }

  implicit def function3Constructor[A: Tag, B: Tag, C: Tag, R, E, Z]
    : ZIOFunctionConstructor.WithOut[(A, B, C) => ZIO[R, E, Z], ZIO[R with A with B with C, E, Z]] =
    new ZIOFunctionConstructor[(A, B, C) => ZIO[R, E, Z]] {
      type Out = ZIO[R with A with B with C, E, Z]
      def apply(f: (A, B, C) => ZIO[R, E, Z])(implicit trace: Trace): ZIO[R with A with B with C, E, Z] =
        ZIO.environmentWithZIO[A with B with C](env => f(env.get[A], env.get[B], env.get[C]))
    }

  implicit def function4Constructor[A: Tag, B: Tag, C: Tag, D: Tag, R, E, Z]
    : ZIOFunctionConstructor.WithOut[(A, B, C, D) => ZIO[R, E, Z], ZIO[R with A with B with C with D, E, Z]] =
    new ZIOFunctionConstructor[(A, B, C, D) => ZIO[R, E, Z]] {
      type Out = ZIO[R with A with B with C with D, E, Z]
      def apply(f: (A, B, C, D) => ZIO[R, E, Z])(implicit trace: Trace): ZIO[R with A with B with C with D, E, Z] =
        ZIO.environmentWithZIO[A with B with C with D](env => f(env.get[A], env.get[B], env.get[C], env.get[D]))
    }

  implicit def function5Constructor[A: Tag, B: Tag, C: Tag, D: Tag, F: Tag, R, E, Z]
    : ZIOFunctionConstructor.WithOut[(A, B, C, D, F) => ZIO[R, E, Z], ZIO[
      R with A with B with C with D with F,
      E,
      Z
    ]] =
    new ZIOFunctionConstructor[(A, B, C, D, F) => ZIO[R, E, Z]] {
      type Out = ZIO[R with A with B with C with D with F, E, Z]
      def apply(
        f: (A, B, C, D, F) => ZIO[R, E, Z]
      )(implicit trace: Trace): ZIO[R with A with B with C with D with F, E, Z] =
        ZIO.environmentWithZIO[A with B with C with D with F](env =>
          f(env.get[A], env.get[B], env.get[C], env.get[D], env.get[F])
        )
    }

  implicit def function6Constructor[A: Tag, B: Tag, C: Tag, D: Tag, F: Tag, G: Tag, R, E, Z]
    : ZIOFunctionConstructor.WithOut[(A, B, C, D, F, G) => ZIO[R, E, Z], ZIO[
      R with A with B with C with D with F with G,
      E,
      Z
    ]] =
    new ZIOFunctionConstructor[(A, B, C, D, F, G) => ZIO[R, E, Z]] {
      type Out = ZIO[R with A with B with C with D with F with G, E, Z]
      def apply(
        f: (A, B, C, D, F, G) => ZIO[R, E, Z]
      )(implicit trace: Trace): ZIO[R with A with B with C with D with F with G, E, Z] =
        ZIO.environmentWithZIO[A with B with C with D with F with G](env =>
          f(env.get[A], env.get[B], env.get[C], env.get[D], env.get[F], env.get[G])
        )
    }

  implicit def function7Constructor[A: Tag, B: Tag, C: Tag, D: Tag, F: Tag, G: Tag, H: Tag, R, E, Z]
    : ZIOFunctionConstructor.WithOut[(A, B, C, D, F, G, H) => ZIO[R, E, Z], ZIO[
      R with A with B with C with D with F with G with H,
      E,
      Z
    ]] =
    new ZIOFunctionConstructor[(A, B, C, D, F, G, H) => ZIO[R, E, Z]] {
      type Out = ZIO[R with A with B with C with D with F with G with H, E, Z]
      def apply(
        f: (A, B, C, D, F, G, H) => ZIO[R, E, Z]
      )(implicit trace: Trace): ZIO[R with A with B with C with D with F with G with H, E, Z] =
        ZIO.environmentWithZIO[A with B with C with D with F with G with H](env =>
          f(env.get[A], env.get[B], env.get[C], env.get[D], env.get[F], env.get[G], env.get[H])
        )
    }
}
