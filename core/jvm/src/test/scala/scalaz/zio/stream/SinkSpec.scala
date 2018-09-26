package scalaz.zio.stream

import org.specs2.ScalaCheck
import scala.{ Stream => _ }
import scalaz.zio.{ AbstractRTSSpec, ExitResult, GenIO, IO }

class SinkSpec extends AbstractRTSSpec with GenIO with ScalaCheck {
  def is = "SinkSpec".title ^ s2"""
  A sink written with Sink.foldM works properly. $jsonNumArrayParsingSinkFoldM
  A sink written with combinators works properly. $jsonNumArrayParsingSinkWithCombinators
  """

  def jsonNumArrayParsingSinkFoldM = {
    sealed trait ParserState
    object ParserState {
      case object Start               extends ParserState
      case class Element(acc: String) extends ParserState
      case object Done                extends ParserState
    }

    val numArrayParser =
      Sink
        .foldM(IO.now((ParserState.Start: ParserState, List.empty[Int]))) { (s, a: Char) =>
          s match {
            case (ParserState.Start, acc) =>
              a match {
                case a if a.isWhitespace => IO.now(Sink.Step.more((ParserState.Start, acc)))
                case '['                 => IO.now(Sink.Step.more((ParserState.Element(""), acc)))
                case _                   => IO.fail("Expected '['")
              }

            case (ParserState.Element(el), acc) =>
              a match {
                case a if a.isDigit => IO.now(Sink.Step.more((ParserState.Element(el + a), acc)))
                case ','            => IO.now(Sink.Step.more((ParserState.Element(""), acc :+ el.toInt)))
                case ']'            => IO.now(Sink.Step.done((ParserState.Done, acc :+ el.toInt), Chunk.empty))
                case _              => IO.fail("Expected a digit or ,")
              }

            case (ParserState.Done, acc) =>
              IO.now(Sink.Step.done((ParserState.Done, acc), Chunk.empty))
          }
        }
        .map(_._2)
        .chunked

    val src1         = StreamChunk.point(Chunk.fromArray(Array('[', '1', '2')))
    val src2         = StreamChunk.point(Chunk.fromArray(Array('3', ',', '4', ']')))
    val partialParse = unsafeRunSync(src1.run(numArrayParser))
    val fullParse    = unsafeRunSync((src1 ++ src2).run(numArrayParser))

    (partialParse must_=== (ExitResult.Succeeded(List()))) and
      (fullParse must_=== (ExitResult.Succeeded(List(123, 4))))
  }

  def jsonNumArrayParsingSinkWithCombinators = {
    val comma = Sink.readWhile[Char](_ == ',')
    val brace = Sink.read1[String, Char](a => s"Expected closing brace; instead: ${a}")((_: Char) == ']')
    val number: Sink[String, Char, Char, Int] =
      Sink.readWhile[Char](_.isDigit).map(_.mkString.toInt)
    val numbers = (number ~ (comma *> number).repeatWhile(_ != ']'))
      .map(tp => tp._1 :: tp._2)

    val elements = numbers <* brace

    lazy val start: Sink[String, Char, Char, List[Int]] =
      Sink.more(IO.fail("Input was empty")) {
        case a if a.isWhitespace => start
        case '['                 => elements
        case _                   => Sink.fail("Expected '['")
      }

    val src1         = StreamChunk.point(Chunk.fromArray(Array('[', '1', '2')))
    val src2         = StreamChunk.point(Chunk.fromArray(Array('3', ',', '4', ']')))
    val partialParse = unsafeRunSync(src1.run(start.chunked))
    val fullParse    = unsafeRunSync((src1 ++ src2).run(start.chunked))

    (partialParse must_=== (ExitResult.checked("Expected closing brace; instead: None"))) and
      (fullParse must_=== (ExitResult.Succeeded(List(123, 4))))
  }
}
