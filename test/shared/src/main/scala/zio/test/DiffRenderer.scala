package zio.test

import zio.test.MessageMarkup.{ Fragment, Line }
import zio.test.diff.DiffComponent

private[test] object DiffRenderer {
  def renderDiff(diff: Vector[DiffComponent]) =
    diff.map {
      case DiffComponent.Unchanged(text) => Fragment.green(text).toLine
      case DiffComponent.Changed(actual, expected) =>
        Fragment.plain("[-") + Fragment.red(actual) + Fragment.plain("+") + Fragment.blue(expected) + Fragment.plain(
          "]"
        )
      case DiffComponent.Inserted(text) =>
        Fragment.plain("[+") + Fragment.red(text) + Fragment.plain("]")
      case DiffComponent.Deleted(text) => Fragment.plain("[-") + Fragment.red(text) + Fragment.plain("]")
    }.foldLeft(Line())(_ ++ _)
      .toMessage
      .replace("\n", "\\n\n")
      .replace("\r", "\\r\r")
      .replace("\t", "\\t")
      .splitOnLineBreaks

}
