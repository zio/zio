package zio.test

trait PrettyPrintVersionSpecific {
  def labels(product: Product): Iterator[String] = Iterator.continually("")
}