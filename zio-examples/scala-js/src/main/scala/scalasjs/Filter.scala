package scalasjs

enum Filter derives CanEqual:
  case All, Active, Completed

object Filter:
  def fromHash(hash: String): Filter = hash match
    case "#/active"    => Filter.Active
    case "#/completed" => Filter.Completed
    case _             => Filter.All

  def hash(f: Filter): String = f match
    case Filter.All       => "#/"
    case Filter.Active    => "#/active"
    case Filter.Completed => "#/completed"

  def apply(todos: List[Todo], f: Filter): List[Todo] = f match
    case Filter.All       => todos
    case Filter.Active    => todos.filterNot(_.completed)
    case Filter.Completed => todos.filter(_.completed)
