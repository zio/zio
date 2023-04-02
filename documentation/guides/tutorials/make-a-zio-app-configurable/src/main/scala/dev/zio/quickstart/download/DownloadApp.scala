package dev.zio.quickstart.download

import zio._
import zio.http._
import zio.http.model._
import zio.stream.ZStream

/** An http app that:
  *   - Accepts a `Request` and returns a `Response`
  *   - May fail with type of `Throwable`
  *   - Does not require any environment
  */
object DownloadApp {
  def apply() =
    Http.collect[Request] {
      // GET /download
      case Method.GET -> !! / "download" =>
        val fileName = "file.txt"
        http.Response(
          status = Status.Ok,
          headers = Headers
            .contentType("application/octet-stream") ++
            Headers.contentDisposition(s"attachment; filename=${fileName}"),
          body = Body.fromStream(
            ZStream.fromResource(fileName)
          )
        )

      // Download a large file using streams
      // GET /download/stream
      case Method.GET -> !! / "download" / "stream" =>
        val file = "bigfile.txt"

        http.Response(
          status = Status.Ok,
          headers = Headers.contentType("application/octet-stream") ++
            Headers.contentDisposition(s"attachment; filename=${file}"),
          body = Body.fromStream(
            ZStream
              .fromResource(file)
              .schedule(Schedule.spaced(50.millis))
          )
        )
    }
}
