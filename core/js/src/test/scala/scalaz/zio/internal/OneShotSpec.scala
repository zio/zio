package scalaz.zio.internal

import org.specs2.Specification

class OneShotSpec extends Specification {
  def is =
    "OneShotSpec".title ^ s2"""
      Make a new OneShot
         set must accept a non-null value.              $setNonNull
         set must not accept a null value.              $setNull
         isSet must report if a value is set.           $isSet
         get must fail if no value is set.              $getWithNoValue
         cannot set value twice                         $setTwice
    """

  def setNonNull = {
    val oneShot = OneShot.make[Int]
    oneShot.set(1)

    oneShot.get() must_=== 1
  }

  def setNull = {
    val oneShot = OneShot.make[Object]
    oneShot.set(null) must throwA[Error]
  }

  def isSet = {
    val oneShot = OneShot.make[Int]
    oneShot.isSet must beFalse
    oneShot.set(1)
    oneShot.isSet must beTrue
  }

  def getWithNoValue = {
    val oneShot = OneShot.make[Object]
    oneShot.get() must throwA[Error]
  }

  def setTwice = {
    val oneShot = OneShot.make[Int]
    oneShot.set(1)
    oneShot.set(2) must throwA[Error]
  }
}
