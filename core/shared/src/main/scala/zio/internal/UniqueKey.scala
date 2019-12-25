package zio.internal

/**
 * This can be used whenever an arbitrary number of unique keys needs to be generated as
 * this will just use memory location for equality.
 */
final class UniqueKey

object UniqueKey {
  def apply(): UniqueKey = new UniqueKey()
}
