package zio.test

trait SourceLocationVariants {
  implicit def generate: SourceLocation = macro Macros.sourceLocation_impl
}
