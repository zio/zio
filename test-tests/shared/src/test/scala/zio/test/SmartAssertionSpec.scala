package zio.test

import zio.duration.durationInt
import zio.test.SmartTestTypes._
import zio.test.environment.TestClock
import zio.{Chunk, Fiber, NonEmptyChunk}

import java.time.LocalDateTime
import scala.collection.immutable.SortedSet

object SmartAssertionSpec extends ZIOBaseSpec {

  /* Developer Note:
   *
   * Switch TestAspect.failing to TestAspect.identity to easily preview
   * the error messages.
   */
  val failing: TestAspectPoly = TestAspect.failing

  private val company: Company = Company("Ziverge", List(User("Bobo", List.tabulate(2)(n => Post(s"Post #$n")))))

  def spec: ZSpec[Environment, Failure] = {
    suite("SmartAssertionSpec")(
      extensionMethodAssertionsSuite,
      userDefinedAssertionsSuite @@ failing,
      suite("Array")(
        suite("==")(
          test("success") {
            val a1 = Array(1, 2, 3)
            val a2 = Array(1, 2, 3)
            assertTrue(a1 == a2)
          },
          test("failure") {
            val a1 = Array(1, 2, 3)
            val a2 = Array(1, 3, 2)
            assertTrue(a1 == a2)
          } @@ failing
        )
      ),
      test("multiple assertions") {
        val array = Array(1, 8, 2, 3, 888)
        assertTrue(
          !(array(0) == 1),
          array(3) == 10,
          array(1) < 2
        )
      } @@ failing,
      test("missing element") {
        assertTrue(company.users(8).posts.exists(_.title == "hi"))
      } @@ failing,
      test("fails predicate") {
        assertTrue(company.users.head.posts.exists(_.title == "hii"))
      } @@ failing,
      test("nested access") {
        val company = Company("Cool Company", List.empty)
        assertTrue(company.users.head.posts.exists(_.title == "hii"))
      } @@ failing,
      test("boolean method") {
        assertTrue(company.users.head.posts.head.publishDate.isDefined)
      } @@ failing,
      test("boolean method with args") {
        assertTrue(company.users.head.posts.head.publishDate.contains(LocalDateTime.MAX))
      } @@ failing,
      test("forall") {
        val list = List(10, 5, 8, 3, 4)
        assertTrue(list.forall(_ % 2 == 0))
      } @@ failing,
      test("right.get") {
        val myEither: Either[String, Int] = Left("string")
        case class Cool(int: Int)
        assertTrue(myEither.right.get + 1 > 18)
      } @@ failing,
      test("string contains") {
        val myString = "something"
        assertTrue(myString.contains("aoseunoth") && myString == "coool")
      } @@ failing,
      suite("referencing literals")(
        test("List") {
          val list = List(10, 23, 8, 8)
          assertTrue((list intersect List(23)).head + 31 == 3)
        },
        test("Case Class") {
          assertTrue(Company("Nice", List.empty).name.contains("aoeu"))
        },
        test("Array") {
          val array = Array(1, 2, 3, 9, 8)
          assertTrue(array.head == 3)
        },
        test("Object constructor") {
          assertTrue(zio.duration.Duration.fromNanos(1000) == zio.duration.Duration.Zero)
        }
      ) @@ failing,
      suite("contains")(
        test("Option") {
          assertTrue(company.users.head.posts.head.publishDate.contains(LocalDateTime.MAX))
        }
      ) @@ failing,
      suite("Either")(
        test("right.get") {
          val myEither: Either[String, Int] = Left("string")
          assertTrue(myEither.right.get + 1 > 11233)
        }
      ) @@ failing,
      suite("Exceptions")(
        test("throws") {
          case class Bomb(name: String) {
            def getName: String = throw new Error("SPLODE")
          }
          val bomb = Bomb("boomy")
          assertTrue(bomb.getName.contains("HIII"))
        }
      ) @@ failing,
      test(".get") {
        case class Person(name: String, age: Int)
        val result = Some(Person("Kit", 30))
        assertTrue(result.get.name == "Kitttty")
      } @@ failing,
      test("calling a method with args") {
        case class Person(name: String = "Fred", age: Int = 42) {
          def say(words: String*): String = words.mkString(" ")
        }
        val person = Person()
        assertTrue {
          person.say("ping", "pong") == "pong pong!"
        }
      } @@ failing,
      test("calling a method with args") {
        case class Person(name: String = "Fred", age: Int = 42) {
          def say(words: String*): String = words.mkString(" ")
        }
        assertTrue {
          Person().say("ping", "pong") == "pong pong!"
        }
      } @@ failing,
      test("calling a method with args") {
        case class Person(name: String = "Fred", age: Int = 41) {
          def say(words: String*): String = words.mkString(" ")
        }

        val person = Person()

        assertTrue(
          person.say("ping", "pong") != "ping pong",
          !(person.say("ping", "pong") == "ping pong")
        )
      } @@ failing,
      test("contains") {
        val list = Some(List(1, 8, 132, 83))
        assertTrue(list.get.contains(78))
      } @@ failing,
      testM("sleep delays effect until time is adjusted") {
        for {
          ref    <- zio.Ref.make(false)
          _      <- ref.set(true).delay(10.hours).fork
          _      <- TestClock.adjust(9.hours)
          result <- ref.get
        } yield assertTrue(!result)
      },
      test("contains must succeed when iterable contains specified element") {
        assertTrue(Seq("zio1", "scala").contains("scala"))
      },
      test("contains array") {
        assertTrue(Array(1, 2, 3, 4, 8, 9, 1).toList.contains(10))
      } @@ failing,
      test("contains iterable") {
        assertTrue(Seq(1, 2, 3, 4, 8, 10, 1, 1).contains(9))
      } @@ failing,
      test("contains string") {
        assertTrue("Howdy".contains("no"))
      } @@ failing,
      test("endsWith iterable") {
        assertTrue(Seq(1, 2, 3, 4, 8, 10, 1, 1).endsWith(List(9, 10)))
      } @@ failing,
      test("endsWith string") {
        assertTrue("Howdy".endsWith("no"))
      } @@ failing,
      test("duration equality") {
        assertTrue(zio.duration.Duration.fromNanos(1000) == zio.duration.Duration.Zero)
      } @@ failing,
      test("string contains") {
        assertTrue("FUNNY HOUSE".contains("OH NO"))
      } @@ failing,
      test("contains must fail when iterable does not contain specified element") {
        assertTrue(Seq("zio", "scala").contains("java"))
      } @@ failing,
      test("containsString must succeed when string is found") {
        assertTrue("this is a value".contains("a value"))
      },
      test("containsString must return false when the string is not contained") {
        assertTrue("this is a value".contains("_NOTHING_"))
      } @@ failing,
      test("endWith must succeed when the supplied value ends with the specified sequence") {
        assertTrue(List(1, 2, 3, 4, 3).endsWith(List(3, 4, 3)))
      },
      test("startsWith must fail when the supplied value does not end with the specified sequence") {
        assertTrue(List(1, 2, 3, 4, 5).endsWith(List(1, 2, 3)))
      } @@ failing,
      test("endsWithString must succeed when the supplied value ends with the specified string") {
        assertTrue("zio".endsWith("o"))
      },
      test("endsWithString must fail when the supplied value does not end with the specified string") {
        assertTrue("zio".endsWith("z"))
      } @@ failing,
      test("equalTo must succeed when value equals specified value") {
        assertTrue(42 == 42)
      },
      test("equalTo must fail when value does not equal specified value") {
        assertTrue(0 == 42)
      } @@ failing,
      test("equalTo must succeed when array equals specified array") {
        assertTrue(Array(1, 2, 3).sameElements(Array(1, 2, 3)))
      },
      test("equalTo must not have type inference issues") {
        val list: List[Int] = List(1, 2, 3, 4)
        assertTrue(list.filter(_ => false) == List.empty[Int])
      },
      test("exists must succeed when at least one element of iterable satisfy specified assertion") {
        assertTrue(Seq(1, 42, 5).exists(_ == 42))
      },
      test("exists must fail when all elements of iterable do not satisfy specified assertion") {
        val value = Seq(1, 42, 5)
        assertTrue(value.exists(_ == 423))
      } @@ failing,
      test("forall must succeed when all elements of iterable satisfy specified assertion") {
        assertTrue(Seq("a", "bb", "ccc").forall(l => l.nonEmpty && l.length <= 3))
      },
      test("forall must fail when one element of iterable do not satisfy specified assertion") {
        assertTrue(Seq("a", "bb", "ccccc").forall(l => l.nonEmpty && l.length <= 3))
      } @@ failing,
      test("forall must succeed when an iterable is empty") {
        val emptySeq = Seq.empty[String]
        assertTrue(emptySeq.forall(l => l.nonEmpty && l.length <= 3))
      },
      test("forall must work with iterables that are not lists") {
        assertTrue(SortedSet(1, 2, 3).forall(_ > 0))
      },
      test("hasAt must fail when an index is outside of a sequence range") {
        assertTrue(Seq(1, 2, 3)(2) == 5)
      } @@ failing,
      test("has at contains") {
        assertTrue(Seq(List(5), List(1, 2, 3), List(1, 2, 3))(2).contains(12))
      } @@ failing,
      test("head") {
        assertTrue(Seq(1, 2, 3, 19).head == 1)
      },
      test("hasAt must succeed when a value is equal to a specific assertion") {
        assertTrue(!(Seq(1, 2, 3)(1) == 2))
      } @@ failing,
      test("hasFirst must succeed when a head is equal to a specific assertion") {
        assertTrue(Seq(1, 2, 3).head == 1)
      },
      test("hasFirst must fail when a head is not equal to a specific assertion") {
        assertTrue(!(Seq(1, 2, 3).head == 1))
      } @@ failing,
      test("hasIntersection must succeed when intersection satisfies specified assertion") {
        val seq = Seq(1, 2, 3, 4, 5)
        assertTrue((seq intersect Seq(4, 5, 6, 7, 8)).length == 105)
      } @@ failing,
      test("hasIntersection must succeed when intersection satisfies specified assertion") {
        val seq = Seq(1, 2, 3, 4, 5)
        assertTrue(seq.intersect(Seq(4, 5, 6, 7, 8)).length == 108)
      } @@ failing,
      test("hasIntersection must succeed when empty intersection satisfies specified assertion") {
        assertTrue((Seq(1, 2, 3, 4) intersect Seq(5, 6, 7)).isEmpty)
      },
      test("Basic equality") {
        val result = 1
        assertTrue {
          def cool(int: Int) = int * 3

          cool(result) > 400
        }
      } @@ failing,
      test("nested access") {
        case class Ziverge(people: Seq[Person]) {
          def isValid = true
        }
        case class Pet(name: String = "Spike")
        case class Person(name: String, age: Int, pet: Pet)
        val person  = Person("Vigoo", 23, Pet())
        val company = Ziverge(Seq(person))

        val string = "hello"
        assertTrue(
          !company.isValid,
          !(string == "hello"),
          person.age == 2,
          person.age > 10700
        )
      } @@ failing,
      test("hasAt must fail when an index is outside of a sequence range") {
        assertTrue(!(Seq(1, 2, 3)(2) == 3))
      } @@ failing,
      testM("check") {
        check(Gen.anyInt) { int =>
          assertTrue(int < 800)
        }
      } @@ failing,
      suite("Diffing")(
        test("No implicit Diff") {
          val int = 100
          assertTrue(int == 200)
        }
          @@ failing,
        test("With implicit Diff") {
          val string = "Sunday Everyday"
          assertTrue(string == "Saturday Todays")
        } @@ failing,
        test("List diffs") {
          val l1 = List("Alpha", "This is a wonderful way to dance and party", "Potato")
          val l2 = List("Alpha", "This is a wonderful way to live and die", "Potato", "Bruce Lee", "Potato", "Ziverge")
          assertTrue(l1 == l2)
        } @@ failing,
        test("Array diffs") {
          val l1 = Array("Alpha", "This is a wonderful way to dance and party", "Potato")
          val l2 = Array("Alpha", "This is a wonderful way to live and die", "Potato", "Bruce Lee", "Potato", "Ziverge")
          assertTrue(l1 == l2)
        } @@ failing,
        test("Chunk diffs") {
          val l1 = Chunk("Alpha", "This is a wonderful way to dance and party", "Potato")
          val l2 = Chunk("Alpha", "This is a wonderful way to live and die", "Potato", "Bruce Lee", "Potato", "Ziverge")
          assertTrue(l1 == l2)
        } @@ failing,
        test("NonEmptyChunk diffs") {
          val l1 = NonEmptyChunk("Alpha", "This is a wonderful way to dance and party", "Potato")
          val l2 =
            NonEmptyChunk(
              "Alpha",
              "This is a wonderful way to live and die",
              "Potato",
              "Bruce Lee",
              "Potato",
              "Ziverge"
            )
          assertTrue(l1 == l2)
        } @@ failing,
        test("Set diffs") {
          val l1 = Set(1, 2, 3, 4)
          val l2 = Set(1, 2, 8, 4, 5)
          assertTrue(l1 == l2)
        } @@ failing,
        test("Map diffs") {
          val l1 = Map("name" -> "Kit", "age" -> "100")
          val l2 = Map("name" -> "Bill", "rage" -> "9000")
          assertTrue(l1 == l2)
        } @@ failing
      ),
      test("Package qualified identifiers") {
        assertTrue(zio.duration.Duration.fromNanos(0) == zio.duration.Duration.Zero)
      },
      suite("isInstanceOf")(
        test("success") {
          val res = MyClass("coo")
          assertTrue(res.isInstanceOf[MyClass])
        },
        test("failure") {
          val res: Any = OtherClass("")
          assertTrue(res.isInstanceOf[MyClass])
        } @@ failing
      ),
      suite("asInstanceOf")(
        test("success") {
          val res: Color = Red(12)
          assertTrue(res.asInstanceOf[Red].foo > 10)
        },
        test("failure") {
          val res: Color = Blue("Hello")
          assertTrue(res.asInstanceOf[Red].foo > 10)
        } @@ failing
      ),
      suite("Map")(
        suite(".apply")(
          test("success") {
            val map = Map("one" -> 1, "two" -> 2)
            assertTrue(map("one") < 3)
          },
          test("failure") {
            val map = Map("one" -> 1, "two" -> 2)
            assertTrue(map("zero") < 3)
          } @@ failing
        )
      ),
      suite("subtype option")(
        test("success") {
          trait Parent
          case class Child(x: String) extends Parent
          val someParent: Option[Parent] = Some(Child("hii"))
          val someChild                  = Child("hii")
          assertTrue(someParent.contains(someChild))
        },
        test("failure") {
          trait Parent
          case class Child(x: String) extends Parent
          val someParent: Option[Parent] = None
          val someChild                  = Child("hii")
          assertTrue(someParent.contains(someChild))
        } @@ failing
      ),
      suite("custom assertions")(
        test("reports source location of actual usage") {
          customAssertion("hello")
        } @@ failing
      )
    )
  }

  private lazy val extensionMethodAssertionsSuite =
    suite("extension method smart assertions")(
      suite("Either")(
        suite("$right")(
          test("success") {
            val either: Either[String, Int] = Right(10)
            assertTrue(either.$right == 10)
          },
          test("failure") {
            val either: Either[String, Int] = Left("Howdy")
            assertTrue(either.$right == 10)
          } @@ failing
        ),
        suite("$left")(
          test("success") {
            val either: Either[String, Int] = Left("Howdy")
            assertTrue(either.$left == "Howdy")
          },
          test("failure") {
            val either: Either[String, Int] = Right(10)
            assertTrue(either.$left == "Howdy")
          } @@ failing
        )
      ),
      suite("Option")(
        suite("$some")(
          test("success") {
            val option: Option[Int] = Some(10)
            assertTrue(option.$some == 10)
          },
          test("failure") {
            val option: Option[Int] = None
            assertTrue(option.$some == 10)
          } @@ failing
        )
      ),
      suite("$throws")(
        test("success") {
          def boom: Int = throw new Error("BOOM!")
          assertTrue(boom.$throws.isInstanceOf[Error])
        },
        test("failure") {
          def boom: Int = 10
          assertTrue(boom.$throws.isInstanceOf[Error])
        } @@ failing
      ),
      suite("$as")(
        test("success") {
          val res: Color = Red(12)
          assertTrue(res.$as[Red].foo > 10)
        },
        test("failure") {
          val res: Color = Blue("Hello")
          assertTrue(res.$as[Red].foo > 11)
        } @@ failing
      ),
      suite("$is")(
        test("success") {
          val res: Color = Red(12)
          assertTrue(res.$is[Red])
        },
        test("failure") {
          val res: Color = Blue("Hello")
          assertTrue(res.$is[Red])
        } @@ failing
      ),
      suite("Exit")(
        suite("$success")(
          test("success") {
            val exit: zio.Exit[String, Int] = zio.Exit.succeed(10)
            assertTrue(exit.$success == 10)
          },
          test("failure Exit.Success") {
            val exit: zio.Exit[String, Int] = zio.Exit.die(new Error("Oops!"))
            assertTrue(exit.$success == 10)
          } @@ failing,
          test("failure Exit.Failure") {
            val exit: zio.Exit[String, Int] = zio.Exit.fail("Failed.")
            assertTrue(exit.$success == 10)
          } @@ failing
        ),
        suite("$cause")(
          test("success") {
            val exit: zio.Exit[String, Int] = zio.Exit.fail("Failed.")
            assertTrue(exit.$cause == zio.Cause.fail("Failed."))
          },
          test("failure Exit.Success") {
            val exit: zio.Exit[String, Int] = zio.Exit.succeed(10)
            assertTrue(exit.$cause == zio.Cause.fail("Fail"))
          } @@ failing
        ),
        suite("$die")(
          test("success") {
            val exit: zio.Exit[String, Int] = zio.Exit.die(new Error("Oops!"))
            assertTrue(exit.$die.getMessage == "Oops!")
          },
          test("failure Exit.Success") {
            val exit: zio.Exit[String, Int] = zio.Exit.succeed(10)
            assertTrue(exit.$die.getMessage == "Oops!")
          } @@ failing,
          test("failure Exit.Failure") {
            val exit: zio.Exit[String, Int] = zio.Exit.fail("Failed.")
            assertTrue(exit.$die.getMessage == "Oops!")
          } @@ failing
        ),
        suite("$failure")(
          test("success") {
            val exit: zio.Exit[String, Int] = zio.Exit.fail("Doh!")
            assertTrue(exit.$fail == "Doh!")
          },
          test("failure Exit.Success") {
            val exit: zio.Exit[String, Int] = zio.Exit.succeed(10)
            assertTrue(exit.$fail == "Doh!")
          } @@ failing,
          test("failure Exit.Failure") {
            val exit: zio.Exit[String, Int] = zio.Exit.die(new Error("Died!"))
            assertTrue(exit.$fail == "Doh!")
          } @@ failing
        )
      ),
      suite("Cause")(
        suite("$fail")(
          test("success") {
            val cause: zio.Cause[String] = zio.Cause.fail("Dang!")
            assertTrue(cause.$fail == "Dang!")
          },
          test("failure Cause.Die") {
            val cause: zio.Cause[String] = zio.Cause.die(new Error("DIE!"))
            assertTrue(cause.$fail == "Dang!")
          } @@ failing,
          test("failure Cause.Interrupt") {
            val cause: zio.Cause[String] = zio.Cause.interrupt(Fiber.Id(0, 0))
            assertTrue(cause.$fail == "Dang!")
          } @@ failing
        ),
        suite("$die")(
          test("success") {
            val cause: zio.Cause[String] = zio.Cause.die(new Error("DIE!"))
            assertTrue(cause.$die.getMessage == "DIE!")
          },
          test("failure Cause.Fail") {
            val cause: zio.Cause[String] = zio.Cause.fail("Dang!")
            assertTrue(cause.$die.getMessage == "DIE!")
          } @@ failing,
          test("failure Cause.Interrupt") {
            val cause: zio.Cause[String] = zio.Cause.interrupt(Fiber.Id(0, 0))
            assertTrue(cause.$die.getMessage == "DIE!")
          } @@ failing
        ),
        suite("$interrupt")(
          test("success") {
            val cause: zio.Cause[String] = zio.Cause.interrupt(Fiber.Id(0, 0))
            assertTrue(cause.$interrupt == Fiber.Id(0, 0))
          },
          test("failure Cause.Fail") {
            val cause: zio.Cause[String] = zio.Cause.fail("Dang!")
            assertTrue(cause.$interrupt == Fiber.Id(0, 0))
          } @@ failing,
          test("failure Cause.Die") {
            val cause: zio.Cause[String] = zio.Cause.die(new Error("DIE!"))
            assertTrue(cause.$interrupt == Fiber.Id(0, 0))
          } @@ failing
        )
      )
    )

  private lazy val userDefinedAssertionsSuite =
    suite("user-defined smart assertions")(
      test("custom assertions") {
        val double = 12.5
        assertTrue(double ~= 12.8)
      },
      test("custom assertions") {
        val double = 12.3
        assertTrue(double.asEven * 3 == 13.5)
      },
      test("custom assertions") {
        val thing: Color = Blue("blue")
        assertTrue(thing.asRed.foo == 12)
      },
      test("custom assertions") {
        val double = 12.3
        assertTrue((double.asEven: Double) == 13.5)
      },
      test("custom assertions") {
        val thing: Color = Blue("blue")
        assertTrue(thing.asRed.run == Red(12))
      },
      test("custom assertions") {
        val thing: Color = Blue("blue")
        assertTrue(Color.asRed(thing).run == Red(12))
      }
    )

  // The implicit SourceLocation will be used by assertTrue to report the
  // actual location.
  def customAssertion(string: String)(implicit sourceLocation: SourceLocation): Assert =
    assertTrue(string == "coool")

  // Test Types
  sealed trait Color

  object Color {
    def asRed(color: Color): SmartAssertion[Red] = SmartAssertion.fromEither {
      color match {
        case Red(int) => Right(Red(int))
        case _        => Left(s"$color IS NOT NICE!")
      }
    }
  }

  final case class Red(foo: Int)     extends Color
  final case class Blue(bar: String) extends Color

  implicit final class ColorOps(private val self: Color) extends AnyVal {
    def asRed: SmartAssertion[Red] =
      SmartAssertion.fromEither {
        self match {
          case Red(int) => Right(Red(int))
          case _        => Left(s"$self IS NOT NICE!")
        }
      }
  }

  private final case class MyClass(name: String)
  private final case class OtherClass(name: String)

  implicit final class DoubleOps(private val self: Double) extends AnyVal {
    def ~=(that: Double): SmartAssertion[Boolean] =
      SmartAssertion.cond(self == that, s"THIS AIN'T RIGHT $self !~= $that")

    def asEven: SmartAssertion[Double] =
      SmartAssertion.fromEither(Either.cond(self % 2 == 0, self, s"$self must be even."))
  }
}
