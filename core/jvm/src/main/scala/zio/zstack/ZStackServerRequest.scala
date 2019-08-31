package zio.zstack

final case class ZStackServerRequest(
  command: String,
  args: Option[List[String]]
  )
