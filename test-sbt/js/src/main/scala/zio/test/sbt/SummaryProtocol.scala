package zio.test.sbt

import zio.test.Summary

/**
 * The ScalaJS SBT Runner uses strings to send and receive summaries. To transmit structured Summary data, we need to
 * serialize to and from strings.
 */
object SummaryProtocol {
  def serialize(summary: Summary): String =
    List(
      summary.success.toString,
      summary.fail.toString,
      summary.ignore.toString,
      summary.summary
    ).map(escape).mkString("\t")

  def deserialize(s: String): Option[Summary] =
    s.split('\t').map(unescape) match {
      case Array(success, fail, ignore, summary) =>
        Some(Summary(success.toInt, fail.toInt, ignore.toInt, summary))

      case _ => None
    }

  def escape(token: String): String   = token.replace("\t", "\\t")
  def unescape(token: String): String = token.replace("\\t", "\t")
}
