package zio.test

trait SourceLocationVariants {
  inline given SourceLocation = ${Macros.sourceLocation_impl}
}