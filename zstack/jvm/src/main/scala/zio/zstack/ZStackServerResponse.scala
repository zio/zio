package zio.zstack

sealed trait ZStackServerResponse

case object Success extends ZStackServerResponse
case object Fail extends ZStackServerResponse

