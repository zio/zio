package zio.test

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport
import zio.{Task, ZIO}


trait FileIOPlatformSpecific {

  def existsFile(path: String): Task[Boolean] =
    ZIO.effectAsync(
      register =>
        FS.access(path, FS.constants.F_OK, (err) => register(ZIO.succeed(err == null))
    ))

  // FIXME it has problems when file does not exists, I can't catch that Exception
  def readFile(path: String): Task[String] =
    ZIO.effectAsync(
      register =>
        try {
          FS.readFile(
            path,
            "UTF-8",
            (err, result) =>
              register(
                if(err != null)
                  ZIO.fail(new Exception(s"Can not read file '$path'"))
                else
                  ZIO.succeed(result)
              )
          )
        } catch {
          case e: Throwable => register(ZIO.fail(new Exception(s"Can not read file '$path'")))
        }
    )

  def writeFile(path: String, content: String): Task[Unit] =
    ZIO.effectAsync(
      register =>
        FS.writeFile(path, content, None, (err) => register(
          if(err == null)
            ZIO.succeed(())
          else
            ZIO.fail(new Exception(s"Error writing to file: $path"))
        )
    ))
}

@js.native
trait FSConstants extends js.Object {

  type FileMode = Integer

  /////////////////////////////////////////////////////////////////////////////////
  //      File Access Constants
  //
  //      The following constants are meant for use with fs.access().
  /////////////////////////////////////////////////////////////////////////////////                         `

  /**
   * File is visible to the calling process. This is useful for determining if a file exists, but says
   * nothing about rwx permissions. Default if no mode is specified.
   */
  val F_OK: FileMode = js.native
}

@js.native
@JSImport("fs", JSImport.Namespace)
object FS extends js.Any {

//  @js.native
//  class FileIOError(val message0: String = js.native) extends js.Any

  type FsCallback1[A] = js.Function2[Any, A, Any]

  def constants: FSConstants = js.native

  /**
   * Asynchronously reads the entire contents of a file.
   * @param file     filename or file descriptor
   * @param encoding the encoding (default = null)
   * @param callback The callback is passed two arguments (err, data), where data is the contents of the file.
   *                 If no encoding is specified, then the raw buffer is returned.
   * @example fs.readFile(file[, options], callback)
   */
  def readFile(file: String, encoding: String, callback: FsCallback1[String]): Unit = js.native

  def access(file: String, mode: Integer, callback: js.Function1[Any, Any]): Unit = js.native

  def writeFile(file: String, data: String, options: Option[String], callback: js.Function1[Any, Any]): Unit = js.native
}