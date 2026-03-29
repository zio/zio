package zio

/**
 * @note This file previously contained a duplicate set of helper apps that
 *       overlapped with [[ZIOAppSpecHelper]].  It has been consolidated: all
 *       canonical helper apps now live in [[ZIOAppSpecHelper]], which is the
 *       single authoritative source used by [[ZIOAppSpec]].
 *
 *       The two apps below are retained here only because they exercise
 *       composition scenarios that are distinct from the helpers in
 *       [[ZIOAppSpecHelper]].  If you add new helpers, prefer adding them to
 *       [[ZIOAppSpecHelper]] and referencing them from [[ZIOAppSpec]].
 */
object ZIOAppSpecHelpers {

  /** A simple app that prints a greeting. */
  object App1 extends ZIOAppDefault {
    override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO.succeed(println("App1 started"))
  }

  /** A simple app that prints a greeting. */
  object App2 extends ZIOAppDefault {
    override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      ZIO.succeed(println("App2 started"))
  }

  /**
   * Demonstrates composing two [[ZIOApp]] instances with the `<>` operator so
   * that both apps run sequentially within a single process entry-point.
   */
  object ComposedApp extends ZIOAppDefault {
    override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
      (App1 <> App2).run
  }
}
