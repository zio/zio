package zio

private[zio] trait ExitVersionSpecific {
  def attempt[A](code: => A): Exit[Throwable, A] =
    try Exit.succeed(code)
    catch {
      case t if nonFatal(t) => Exit.fail(t)
    }
}
