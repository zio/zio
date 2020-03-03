package zio.test.diff

final case class DiffResult(components: Vector[DiffComponent])

sealed trait DiffComponent
object DiffComponent {
  final case class Unchanged(text: String)                   extends DiffComponent
  final case class Changed(actual: String, expected: String) extends DiffComponent
  final case class Inserted(text: String)                    extends DiffComponent
  final case class Deleted(text: String)                     extends DiffComponent
}
