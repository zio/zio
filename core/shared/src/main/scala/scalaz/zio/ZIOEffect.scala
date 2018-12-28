package scalaz.zio

/**
 * A type class that allows people to use ZIO-specific code with
 * other effect monads, so long as they can lift an arbitrary `IO`
 * computation into their type. See the `interop` modules for
 * instances of this type class for other effect types.
 */
trait ZIOEffect[F[_, _]] {
  def liftZIO[E, A](io: IO[E, A]): F[E, A]
}
object ZIOEffect extends Serializable {
  final def apply[F[_, _]](implicit F: ZIOEffect[F]): ZIOEffect[F] = F

  implicit final val ZIOIO: ZIOEffect[IO] = new ZIOEffect[IO] {
    final def liftZIO[E, A](io: IO[E, A]): IO[E, A] = io
  }
}
