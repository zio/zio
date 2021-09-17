package zio.test

trait PrettyPrintVersionSpecific {
  def labels(product: Product): Iterator[String] = {
    val _ = product
    Iterator.continually("")
  }
}
