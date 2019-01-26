package scalaz.zio
package system

import org.specs2.Specification

class SystemSpec extends Specification with RTS {
  def is = s2"""
    Fetch the current time from the system clock and check that:
      It returns a reasonable instant                                 $currentTime1
      It returns a reasonable timestamp                               $currentTimeMillis1

    Fetch the JVMs nanoTime and check that:
      It returns something compatible with System.nanoTime            $nanoTime1

    Fetch an environment variable and check that:
      If it exists, return a reasonable value                         $env1
      If it does not exist, return None                               $env2

    Fetch a VM property and check that:
      If it exists, return a reasonable value                         $prop1
      If it does not exist, return None                               $prop2

    Fetch the system's line separator and check that:
      It is identical to System.lineSeparator                         $lineSep1
  """

  def currentTime1 = {
    val start = System.currentTimeMillis
    val io    = unsafeRun(currentTime).toEpochMilli
    val end   = System.currentTimeMillis

    io must beBetween(start, end)
  }

  def currentTimeMillis1 = {
    val start = System.currentTimeMillis
    val io    = unsafeRun(currentTimeMillis)
    val end   = System.currentTimeMillis

    io must beBetween(start, end)
  }

  def nanoTime1 = {
    val start = System.nanoTime
    val io    = unsafeRun(nanoTime)
    val end   = System.nanoTime

    io must beBetween(start, end)
  }

  def env1 = {
    val io = unsafeRun(system.env("PATH"))
    io must beSome
    io.get must contain("/bin")
  }

  def env2 = {
    val io = unsafeRun(system.env("QWERTY"))
    io must beNone
  }

  def prop1 = {
    val io = unsafeRun(property("java.vm.name"))
    io must beSome
    io.get must contain("VM")
  }

  def prop2 = {
    val io = unsafeRun(property("qwerty"))
    io must beNone
  }

  def lineSep1 = unsafeRun(lineSeparator) must_=== (System.lineSeparator)
}
