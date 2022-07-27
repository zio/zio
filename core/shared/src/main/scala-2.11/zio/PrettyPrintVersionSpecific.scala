package zio.test

import zio.stacktracer.TracingImplicits.disableAutoTrace

trait PrettyPrintVersionSpecific {
  def labels(product: Product): Iterator[String] = {
    val _ = product
    Iterator.continually("")
  }
}
