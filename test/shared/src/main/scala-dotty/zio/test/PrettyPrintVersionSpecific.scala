package zio.test

trait PrettyPrintVersionSpecific {
  def labels(product: Product): Iterator[String] = product.productElementNames
}
