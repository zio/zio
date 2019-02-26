package scalaz.zio
package console

import java.io.{ ByteArrayOutputStream, PrintStream, StringReader }

import org.specs2.concurrent.ExecutionEnv

class PackageSpec(implicit ee: ExecutionEnv) extends TestRuntime {
  def is = "PackageSpec".title ^ s2"""
    Check that the following is possible:
      Print a string to the stream                 $putStr1
      Print a string with a newline                $putStrLn1
      Get a line of text from stream               $getStrLn1
  """

  def stream(): (PrintStream, ByteArrayOutputStream) = {
    val baos = new ByteArrayOutputStream
    (new PrintStream(baos), baos)
  }

  def putStr1 = {
    val (p, out) = stream()
    val str      = "Hello World"
    unsafeRun(putStr(p)(str))
    out.toString must_=== str
  }

  def putStrLn1 = {
    val (p, out) = stream()
    val str      = "FooBar"
    unsafeRun(putStrLn(p)(str))
    out.toString must_=== s"${str}\n"
  }

  def getStrLn1 = {
    val v = "42"
    val r = new StringReader(v)
    val s = unsafeRun(getStrLn(r))
    s must_=== v
  }
}
