package zio

private[zio] trait ExitVersionSpecific {

  /**
   * Evaluates `code` and returns `Exit.Success` with its result, or
   * `Exit.Failure` with the `Throwable` it throws as a typed failure. A
   * `VirtualMachineError` (such as `StackOverflowError` or `OutOfMemoryError`)
   * is not caught: it is thrown to the caller.
   */
  inline def attempt[A](inline code: => A): Exit[Throwable, A] =
    try Exit.succeed(code)
    catch {
      case t if nonFatal(t) => Exit.fail(t)
    }
}
