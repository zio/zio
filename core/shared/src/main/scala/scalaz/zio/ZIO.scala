package scalaz.zio

/**
 * A type class that allows people to use ZIO-specific code with
 * other effect monads, so long as they can lift an arbitrary `IO`
 * computation into their type. See the `interop` modules for
 * instances of this type class for other effect types.
 */
trait ZIO[F[_, _]] {
  def liftZIO[E, A](io: IO[E, A]): F[E, A]
}
object ZIO extends Serializable {
  final def apply[F[_, _]](implicit F: ZIO[F]): ZIO[F] = F

  implicit final val ZIOIO: ZIO[IO] = new ZIO[IO] {
    final def liftZIO[E, A](io: IO[E, A]): IO[E, A] = io
  }
}
