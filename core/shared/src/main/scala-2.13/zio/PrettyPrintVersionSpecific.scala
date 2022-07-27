package zio

trait PrettyPrintVersionSpecific {
  def labels(product: Product): Iterator[String] = product.productElementNames
}
