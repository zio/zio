package zio

/**
 * A `ZIOFunctionConstructor[In]` knows how to construct a `ZIO` value from a
 * function of type `In` that returns a `ZIO`. This allows the type of the `ZIO`
 * value constructed to depend on `In`.
 *
 * It is the implicit parameter of `ZIO.fromFunctionZIO`. Instances exist for
 * functions of one to seven arguments: each argument is read from the
 * environment by its type, and the function's own environment `R` is added to
 * the environment of the result.
 *
 * {{{
 * val length: ZIO[String, Nothing, Int] =
 *   ZIO.fromFunctionZIO((s: String) => ZIO.succeed(s.length))
 *
 * val total: ZIO[Int with String, Nothing, Int] =
 *   ZIO.fromFunctionZIO((n: Int, s: String) => ZIO.succeed(n + s.length))
 * }}}
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

/**
 * The instances of `ZIOFunctionConstructor`, for functions of one to seven
 * arguments.
 */
object ZIOFunctionConstructor {

  /** A `ZIOFunctionConstructor[In]` whose result type `Out` is `Out0`. */
  type WithOut[In, Out0] = ZIOFunctionConstructor[In] { type Out = Out0 }

  /**
   * Constructs a `ZIO` from a function of one argument that returns a `ZIO`.
   * The argument is the service of its type read from the environment, and the
   * environment `R` of the returned `ZIO` is added to the result.
   */
  implicit def function1Constructor[A: Tag, R, E, Z]
    : ZIOFunctionConstructor.WithOut[A => ZIO[R, E, Z], ZIO[R with A, E, Z]] =
    new ZIOFunctionConstructor[A => ZIO[R, E, Z]] {
      type Out = ZIO[R with A, E, Z]
      def apply(f: A => ZIO[R, E, Z])(implicit trace: Trace): ZIO[R with A, E, Z] =
        ZIO.serviceWithZIO[A](f)
    }

  /**
   * Constructs a `ZIO` from a function of two arguments that returns a `ZIO`.
   * Each argument is the service of its type read from the environment, and the
   * environment `R` of the returned `ZIO` is added to the result.
   */
  implicit def function2Constructor[A: Tag, B: Tag, R, E, Z]
    : ZIOFunctionConstructor.WithOut[(A, B) => ZIO[R, E, Z], ZIO[R with A with B, E, Z]] =
    new ZIOFunctionConstructor[(A, B) => ZIO[R, E, Z]] {
      type Out = ZIO[R with A with B, E, Z]
      def apply(f: (A, B) => ZIO[R, E, Z])(implicit trace: Trace): ZIO[R with A with B, E, Z] =
        ZIO.environmentWithZIO[A with B](env => f(env.get[A], env.get[B]))
    }

  /**
   * Constructs a `ZIO` from a function of three arguments that returns a `ZIO`.
   * Each argument is the service of its type read from the environment, and the
   * environment `R` of the returned `ZIO` is added to the result.
   */
  implicit def function3Constructor[A: Tag, B: Tag, C: Tag, R, E, Z]
    : ZIOFunctionConstructor.WithOut[(A, B, C) => ZIO[R, E, Z], ZIO[R with A with B with C, E, Z]] =
    new ZIOFunctionConstructor[(A, B, C) => ZIO[R, E, Z]] {
      type Out = ZIO[R with A with B with C, E, Z]
      def apply(f: (A, B, C) => ZIO[R, E, Z])(implicit trace: Trace): ZIO[R with A with B with C, E, Z] =
        ZIO.environmentWithZIO[A with B with C](env => f(env.get[A], env.get[B], env.get[C]))
    }

  /**
   * Constructs a `ZIO` from a function of four arguments that returns a `ZIO`.
   * Each argument is the service of its type read from the environment, and the
   * environment `R` of the returned `ZIO` is added to the result.
   */
  implicit def function4Constructor[A: Tag, B: Tag, C: Tag, D: Tag, R, E, Z]
    : ZIOFunctionConstructor.WithOut[(A, B, C, D) => ZIO[R, E, Z], ZIO[R with A with B with C with D, E, Z]] =
    new ZIOFunctionConstructor[(A, B, C, D) => ZIO[R, E, Z]] {
      type Out = ZIO[R with A with B with C with D, E, Z]
      def apply(f: (A, B, C, D) => ZIO[R, E, Z])(implicit trace: Trace): ZIO[R with A with B with C with D, E, Z] =
        ZIO.environmentWithZIO[A with B with C with D](env => f(env.get[A], env.get[B], env.get[C], env.get[D]))
    }

  /**
   * Constructs a `ZIO` from a function of five arguments that returns a `ZIO`.
   * Each argument is the service of its type read from the environment, and the
   * environment `R` of the returned `ZIO` is added to the result.
   */
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

  /**
   * Constructs a `ZIO` from a function of six arguments that returns a `ZIO`.
   * Each argument is the service of its type read from the environment, and the
   * environment `R` of the returned `ZIO` is added to the result.
   */
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

  /**
   * Constructs a `ZIO` from a function of seven arguments that returns a `ZIO`.
   * Each argument is the service of its type read from the environment, and the
   * environment `R` of the returned `ZIO` is added to the result.
   */
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
