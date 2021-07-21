package zio.test.snapshot

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

@js.native
trait Fs extends js.Object {
  type FsCallback1[A] = js.Function2[Any, A, Any]

  /**
   * Asynchronously reads the entire contents of a file.
   * @param file     filename or file descriptor
   * @param encoding the encoding (default = null)
   * @param callback The callback is passed two arguments (err, data), where data is the contents of the file.
   *                 If no encoding is specified, then the raw buffer is returned.
   * @example fs.readFile(file[, options], callback)
   */
  def readFile(file: String, encoding: String, callback: FsCallback1[String]): Unit = js.native

}

@js.native
@JSImport("fs", JSImport.Namespace)
object Fs extends Fs
