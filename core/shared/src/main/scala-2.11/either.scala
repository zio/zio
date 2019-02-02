package scalaz.zio

trait EitherCompat {
  implicit class EitherOps[L, R](e: Either[L, R]) {
    def map[A](f: R => A): Either[L, A] = e.right.map(f)
  }
}
