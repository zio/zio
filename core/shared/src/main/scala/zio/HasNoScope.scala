package zio

/**
 * Evidence that the environment type `R` does not contain `zio.Scope`. The
 * compiler derives it for a single type or an intersection such as `A with B`
 * that does not contain `Scope`. It does not derive it when `R` contains
 * `Scope`, or when `R` is or contains an abstract type parameter: add a
 * `HasNoScope` context bound to the type parameter in that case.
 *
 * {{{
 * def unscoped[R: HasNoScope, A](zio: ZIO[R, Nothing, A]): ZIO[R, Nothing, A] =
 *   zio
 *
 * val ok: UIO[Int] = unscoped(ZIO.succeed(1))
 * // unscoped(ZIO.scope.as(1)) does not compile: the environment is Scope
 * }}}
 */
@scala.annotation.implicitNotFound(
  """Can not prove that ${R} does not contain Scope.
If ${R} contains a zio.Scope, please handle it explicitly. If it contains a generic type, add a context bound
like def myMethod[R: HasNoScope](...) = ..."""
)
sealed trait HasNoScope[R]

/**
 * The companion of [[HasNoScope]]: it holds the instances that the compiler
 * derives.
 */
object HasNoScope extends HasNoScopeCompanionVersionSpecific {

  /** The value that every derived `HasNoScope` instance is cast from. */
  override val instance: HasNoScope[Any] = new HasNoScope[Any] {}
}
