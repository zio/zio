package zio.internal.macros

final case class Node[+A](inputs: List[String], outputs: List[String], value: A) {
  def map[B](f: A => B): Node[B] = copy(value = f(value))
}
