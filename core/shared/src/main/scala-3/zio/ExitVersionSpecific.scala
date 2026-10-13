package zio

private[zio] trait ExitVersionSpecific {
  inline def attempt[A](inline code: => A): Exit[Throwable, A] =
    try Exit.succeed(code)
    catch {
      case t if nonFatal(t) => Exit.fail(t)
    }
}
