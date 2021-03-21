package zio.stream.compression

/** Signals that exception occurred in compression/decompression */
class CompressionException private (message: String, cause: Exception) extends Exception(message, cause)

object CompressionException {
  def apply(message: String, cause: Option[Exception] = None) = new CompressionException(message, cause.getOrElse(null))

  def apply(cause: Exception) = new CompressionException(cause.getMessage(), cause)
}
