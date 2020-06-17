package zio.stream.compression

/** Signals that exception occurred in compression/decompression */
class CompressionException(cause: Exception) extends Exception(cause)
