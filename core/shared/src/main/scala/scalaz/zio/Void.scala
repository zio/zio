package scalaz.zio

/**
 * Represents something that can't exist
 */
abstract final class Void {
  def absurd[A]: A
}
