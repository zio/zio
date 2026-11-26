package zio

import zio.internal.macros.LayerMacros

private[zio] transparent trait ZIOVersionSpecific[-R, +E, +A] { self: ZIO[R, E, A] =>

  /**
   * Splits the environment into two parts, assembling one part using the
   * specified layer and leaving the remainder `R0`.
   *
   * {{{
   * trait Database { def query(sql: String): UIO[Int] }
   * trait Mailer { def send(to: String): UIO[Unit] }
   *
   * val databaseLayer: ULayer[Database] =
   *   ZLayer.succeed(new Database { def query(sql: String): UIO[Int] = ZIO.succeed(42) })
   *
   * val report: ZIO[Database with Mailer, Nothing, Unit] =
   *   ZIO.serviceWithZIO[Database](_.query("select count(*) from orders")) *>
   *     ZIO.serviceWithZIO[Mailer](_.send("ops@example.com"))
   *
   * val needsMailer: ZIO[Mailer, Nothing, Unit] = report.provideSome[Mailer](databaseLayer)
   * }}}
   */
  def provideSome[R0] =
    new ProvideSomePartiallyApplied[R0, R, E, A](self)

  /**
   * Equivalent to `provideSome`, but does not require providing the remainder
   * type: the macro infers it from the layers. It is available on Scala 3 only.
   *
   * {{{
   * trait Database { def query(sql: String): UIO[Int] }
   * trait Mailer { def send(to: String): UIO[Unit] }
   *
   * val databaseLayer: ULayer[Database] =
   *   ZLayer.succeed(new Database { def query(sql: String): UIO[Int] = ZIO.succeed(42) })
   *
   * val report: ZIO[Database with Mailer, Nothing, Unit] =
   *   ZIO.serviceWithZIO[Database](_.query("select count(*) from orders")) *>
   *     ZIO.serviceWithZIO[Mailer](_.send("ops@example.com"))
   *
   * val needsMailer = report.provideSomeAuto(databaseLayer) // Inferred type is ZIO[Mailer, Nothing, Unit]
   * }}}
   *
   * Note for Intellij users: By default, Intellij will not show correct type on
   * hover. To fix this enable `Use types reported by Scala compiler
   * (experimental)` in `Settings | Languages & Frameworks | Scala | Editor`
   */
  inline transparent def provideSomeAuto[E1 >: E](inline layer: ZLayer[_, E1, _]*): ZIO[_, E1, A] =
    ${ LayerMacros.provideDynamicImpl[R, E1, A]('self, 'layer) }

  /**
   * Automatically assembles a layer for the ZIO effect from the given layers,
   * providing its whole environment, and returns an effect with no
   * requirements. A macro wires the layers by their input and output types at
   * compile time: a missing layer, two layers for the same type or a circular
   * dependency is a compile error, and an unused layer is a compile warning.
   *
   * {{{
   * trait Inventory { def count(sku: String): UIO[Int] }
   *
   * val inventoryLayer: ULayer[Inventory] =
   *   ZLayer.succeed(new Inventory { def count(sku: String): UIO[Int] = ZIO.succeed(7) })
   *
   * val stock: UIO[Int] =
   *   ZIO.serviceWithZIO[Inventory](_.count("SKU-2002")).provide(inventoryLayer)
   * }}}
   */
  inline def provide[E1 >: E](inline layer: ZLayer[_, E1, _]*): ZIO[Any, E1, A] =
    ${ LayerMacros.provideStaticImpl[Any, R, E1, A]('self, 'layer) }

}

/**
 * The result of `ZIO#provideSome[R0]`: call it with the layers that build the
 * part of the environment that `R0` does not cover.
 */
final class ProvideSomePartiallyApplied[R0, -R, +E, +A](val self: ZIO[R, E, A]) extends AnyVal {

  /**
   * Builds the part of the environment that `R0` does not cover from the given
   * layers, wiring them at compile time, and returns an effect that requires
   * only `R0`. A missing layer is a compile error and an unused layer is a
   * compile warning.
   */
  inline def apply[E1 >: E](inline layer: ZLayer[_, E1, _]*): ZIO[R0, E1, A] =
    ${ LayerMacros.provideSomeStaticImpl[R0, R, E1, A]('self, 'layer) }
}
