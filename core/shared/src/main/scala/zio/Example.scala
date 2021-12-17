import zio.Chunk

object Example extends scala.App {

  import zio.Chunk

  case class Grid[T](w: Int, private val init: T, private val data: Chunk[Chunk[T]]) {

    private def updated(x: Int, t: T): Grid[T] =
      copy(data = data.updated(0, data(0).updated(x, t)))

    def map(f: T => T): Grid[T] =
      (0 until w).foldLeft(this) { case (acc, x) =>
        Console.println(s"p = {$x, 0}")
        acc.updated(x, f(data(0)(x)))
      }

  }

  def fill[T](w: Int)(init: T): Grid[T] =
    Grid(w = w, init = init, data = Chunk.fill(1)(Chunk.fill(w)(init)))

  val g = fill(w = 257)(20)

// Throws:
  Console.println(g.map(_ + 1))
}
