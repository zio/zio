package scalasjs

import zio.json.*

final case class Todo(id: String, title: String, completed: Boolean)

object Todo:
  given JsonCodec[Todo] = DeriveJsonCodec.gen[Todo]
