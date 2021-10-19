package zio.macros

import zio._
import zio.stream._
import zio.test.Assertion._
import zio.test._

object AccessibleSpecFlat extends DefaultRunnableSpec {

  def spec: ZSpec[Environment, Failure] = suite("AccessibleSpecPlain")(
    suite("Accessible macro")(
      testM("compiles when applied to object with empty Service") {
        assertM(typeCheck {
          """
            @accessible
            trait Module
          """
        })(isRight(anything))
      },
      testM("fails when applied to object without a Service") {
        assertM(typeCheck {
          """
            @accessible
            object Module
          """
        })(isLeft(anything))
      },
      testM("fails when applied to trait") {
        assertM(typeCheck {
          """
            @accessible
            trait Module
          """
        })(isRight(anything))
      },
      testM("fails when applied to class") {
        assertM(typeCheck {
          """
            @accessible
            class Module
          """
        })(isLeft(anything))
      },
      testM("generates accessor for values") {
        assertM(typeCheck {
          """
            @accessible
            trait Module {
              val foo: UIO[Unit]
            }

            object Check {
              val foo: ZIO[Has[Module], Nothing, Unit] =
                Module.foo
            }
          """
        })(isRight(anything))
      },
      testM("generates accessor for functions") {
        assertM(typeCheck {
          """
            @accessible
            trait Module {
              def foo(i: Int): UIO[Unit]
            }

            object Check {
              def foo(i: Int): ZIO[Has[Module], Nothing, Unit] =
                Module.foo(i)
            }
          """
        })(isRight(anything))
      },
      testM("generates accessor for varargs functions") {
        assertM(typeCheck {
          """
            @accessible
            trait Module {
              def varargsFoo(a: Int, b: Int*): UIO[Unit]
            }

            object Check {
              def varargsFoo(a: Int, b: Int*): ZIO[Has[Module], Nothing, Unit] =
                Module.varargsFoo(a, b: _*)
            }
          """
        })(isRight(anything))
      },
      testM("generates accessors for members returning ZManaged") {
        assertM(typeCheck {
          """
            @accessible
            trait Module {
              def umanaged(s: String): UManaged[Int]
              def urmanaged(s: String): URManaged[Has[String], Int]
              def zmanaged(s: String): ZManaged[Has[String], String, Int]
            }

            object Check {
              def umanaged(s: String): ZManaged[Has[Module], Nothing, Int] =
                Module.umanaged(s)
              def urmanaged(s: String): ZManaged[Has[String] with Has[Module], Nothing, Int] =
                Module.urmanaged(s)
              def zmanaged(s: String): ZManaged[Has[String] with Has[Module], String, Int] =
                Module.zmanaged(s)
            }
          """
        })(isRight(anything))
      },
      testM("generates accessor for service with default method implementations") {
        assertM(typeCheck {
          """
             @accessible
             trait Module {
               def foo(x: Int): Task[Unit] = foo(x.toString)
               def foo(x: String): Task[Unit]
             }
          """.stripMargin
        })(isRight(anything))
      },
      testM("generates accessor for service with one type param") {
        assertM(typeCheck {
          """
             @accessible
             trait Module[T] {
               val v: Task[T]
               def f1: UIO[Unit]
               def f2(): UIO[Unit]
               def f3(t: T): UIO[Unit]
               def f4(t: T)(i: Int): UIO[Unit]
               def f5(t: T)(implicit i: Int): UIO[Unit]
               def f6(t: T*): UIO[Unit]
             }

             object Check {
               def v[T: Tag]: ZIO[Has[Module[T]], Throwable, T] =
                 Module.v[T]
               def f1[T: Tag]: ZIO[Has[Module[T]], Nothing, Unit] =
                 Module.f1[T]
               def f2[T: Tag](): ZIO[Has[Module[T]], Nothing, Unit] =
                 Module.f2[T]()
               def f3[T: Tag](t: T): ZIO[Has[Module[T]], Nothing, Unit] =
                 Module.f3[T](t)
               def f4[T: Tag](t: T)(i: Int): ZIO[Has[Module[T]], Nothing, Unit] =
                 Module.f4[T](t)(i)
               def f5[T: Tag](t: T)(implicit i: Int): ZIO[Has[Module[T]], Nothing, Unit] =
                 Module.f5[T](t)
               def f6[T: Tag](t: T*): ZIO[Has[Module[T]], Nothing, Unit] =
                 Module.f6[T](t: _*)
             }
          """
        })(isRight(anything))
      },
      testM("generates accessor for service with contravariant type param") {
        assertM(typeCheck {
          """
             @accessible
             trait Module[-R] {
               val v: RIO[R, Unit]
             }

             object Check {
               def v[R: Tag]: ZIO[Has[Module[R]] with R, Throwable, Unit] =
                 Module.v[R]
             }
          """
        })(isRight(anything))
      },
      testM("generates accessor for service with two type params and type bounds") {
        assertM(typeCheck {
          """
             trait Foo
             trait Bar

             @accessible
             trait Module[T <: Foo, U >: Bar] {
               val v: Task[T]
               def f1: UIO[U]
               def f2(): UIO[U]
               def f3(t: T): UIO[U]
               def f4(t: T)(u: U): UIO[U]
               def f5(t: T)(implicit u: U): UIO[U]
               def f6(t: T*): UIO[U]
             }

             object Check {
               def v[T <: Foo: Tag, U >: Bar: Tag]: ZIO[Has[Module[T, U]], Throwable, T] =
                 Module.v[T, U]
               def f1[T <: Foo: Tag, U >: Bar: Tag]: ZIO[Has[Module[T, U]], Nothing, U] =
                 Module.f1[T, U]
               def f2[T <: Foo: Tag, U >: Bar: Tag](): ZIO[Has[Module[T, U]], Nothing, U] =
                 Module.f2[T, U]()
               def f3[T <: Foo: Tag, U >: Bar: Tag](t: T): ZIO[Has[Module[T, U]], Nothing, U] =
                 Module.f3[T, U](t)
               def f4[T <: Foo: Tag, U >: Bar: Tag](t: T)(u: U): ZIO[Has[Module[T, U]], Nothing, U] =
                 Module.f4[T, U](t)(u)
               def f5[T <: Foo: Tag, U >: Bar: Tag](t: T)(implicit u: U): ZIO[Has[Module[T, U]], Nothing, U] =
                 Module.f5[T, U](t)
               def f6[T <: Foo: Tag, U >: Bar: Tag](t: T*): ZIO[Has[Module[T, U]], Nothing, U] =
                 Module.f6[T, U](t: _*)
             }
          """
        })(isRight(anything))
      },
      testM("generates accessors for all capabilities") {
        assertM(typeCheck {
          """
            @accessible
            trait Module {
              val static                                 : UIO[String]
              def zeroArgs                               : UIO[Int]
              def zeroArgsWithParens()                   : UIO[Long]
              def singleArg(arg1: Int)                   : UIO[String]
              def multiArgs(arg1: Int, arg2: Long)       : UIO[String]
              def multiParamLists(arg1: Int)(arg2: Long) : UIO[String]
              def typedVarargs[T](arg1: Int, arg2: T*)   : UIO[T]
              def command(arg1: Int)                     : UIO[Unit]
              def overloaded(arg1: Int)                  : UIO[String]
              def overloaded(arg1: Long)                 : UIO[String]

              val staticManaged                                 : UManaged[String]
              def zeroArgsManaged                               : UManaged[Int]
              def zeroArgsTypedManaged[T]                       : UManaged[T]
              def zeroArgsWithParensManaged()                   : UManaged[Long]
              def singleArgManaged(arg1: Int)                   : UManaged[String]
              def multiArgsManaged(arg1: Int, arg2: Long)       : UManaged[String]
              def multiParamListsManaged(arg1: Int)(arg2: Long) : UManaged[String]
              def typedVarargsManaged[T](arg1: Int, arg2: T*)   : UManaged[T]
              def commandManaged(arg1: Int)                     : UManaged[Unit]
              def overloadedManaged(arg1: Int)                  : UManaged[String]
              def overloadedManaged(arg1: Long)                 : UManaged[String]

              def function(arg1: Int)                    : String
              def sink(arg1: Int)                        : ZSink[Any, Nothing, Int, Int, List[Int]]
              def stream(arg1: Int)                      : ZStream[Any, Nothing, Int]
            }

            object Check {
              val static                                 : ZIO[Has[Module], Nothing, String] = Module.static
              def zeroArgs                               : ZIO[Has[Module], Nothing, Int]    = Module.zeroArgs
              def zeroArgsWithParens()                   : ZIO[Has[Module], Nothing, Long]   = Module.zeroArgsWithParens()
              def singleArg(arg1: Int)                   : ZIO[Has[Module], Nothing, String] = Module.singleArg(arg1)
              def multiArgs(arg1: Int, arg2: Long)       : ZIO[Has[Module], Nothing, String] = Module.multiArgs(arg1, arg2)
              def multiParamLists(arg1: Int)(arg2: Long) : ZIO[Has[Module], Nothing, String] = Module.multiParamLists(arg1)(arg2)
              def typedVarargs[T](arg1: Int, arg2: T*)   : ZIO[Has[Module], Nothing, T]      = Module.typedVarargs[T](arg1, arg2: _*)
              def command(arg1: Int)                     : ZIO[Has[Module], Nothing, Unit]   = Module.command(arg1)
              def overloaded(arg1: Int)                  : ZIO[Has[Module], Nothing, String] = Module.overloaded(arg1)
              def overloaded(arg1: Long)                 : ZIO[Has[Module], Nothing, String] = Module.overloaded(arg1)

              val staticManaged                                 : ZManaged[Has[Module], Nothing, String] = Module.staticManaged
              def zeroArgsManaged                               : ZManaged[Has[Module], Nothing, Int]    = Module.zeroArgsManaged
              def zeroArgsTypedManaged[T]                       : ZManaged[Has[Module], Nothing, T]      = Module.zeroArgsTypedManaged[T]
              def zeroArgsWithParensManaged()                   : ZManaged[Has[Module], Nothing, Long]   = Module.zeroArgsWithParensManaged()
              def singleArgManaged(arg1: Int)                   : ZManaged[Has[Module], Nothing, String] = Module.singleArgManaged(arg1)
              def multiArgsManaged(arg1: Int, arg2: Long)       : ZManaged[Has[Module], Nothing, String] = Module.multiArgsManaged(arg1, arg2)
              def multiParamListsManaged(arg1: Int)(arg2: Long) : ZManaged[Has[Module], Nothing, String] = Module.multiParamListsManaged(arg1)(arg2)
              def typedVarargsManaged[T](arg1: Int, arg2: T*)   : ZManaged[Has[Module], Nothing, T]      = Module.typedVarargsManaged[T](arg1, arg2: _*)
              def commandManaged(arg1: Int)                     : ZManaged[Has[Module], Nothing, Unit]   = Module.commandManaged(arg1)
              def overloadedManaged(arg1: Int)                  : ZManaged[Has[Module], Nothing, String] = Module.overloadedManaged(arg1)
              def overloadedManaged(arg1: Long)                 : ZManaged[Has[Module], Nothing, String] = Module.overloadedManaged(arg1)

              def function(arg1: Int)                    : ZIO[Has[Module], Throwable, String] = Module.function(arg1)
              def sink(arg1: Int)                        : ZSink[Has[Module], Nothing, Int, Int, List[Int]] = Module.sink(arg1)
              def stream(arg1: Int)                      : ZStream[Has[Module], Nothing, Int] = Module.stream(arg1)
            }
          """
        })(isRight(anything))
      },
      testM("generates accessor for throwing values") {
        assertM(typeCheck {
          """
            @accessible
            trait Module {
              @throwing
              val test: Unit
            }
            object Check {
              val foo: ZIO[Has[Module], Nothing, Unit] =
                Module.test
            }
          """
        })(isRight(anything))
      },
      testM("generates accessor for throwing methods") {
        assertM(typeCheck {
          """
            @accessible
            trait Module {
              @throwing
              def test: Unit
            }
            object Check {
              def foo: ZIO[Has[Module], Throwable, Unit] =
                Module.test
            }
          """
        })(isRight(anything))
      },
      testM("generates accessor for values") {
        assertM(typeCheck {
          """
            @accessible
            trait Module {
              val test: Unit
            }
            object Check {
              val foo: ZIO[Has[Module], Nothing, Unit] =
                Module.test
            }
          """
        })(isRight(anything))
      },
      testM("generates accessor for methods") {
        assertM(typeCheck {
          """
            @accessible
            trait Module {
              def test: Unit
            }
            object Check {
              def foo: ZIO[Has[Module], Nothing, Unit] =
                Module.test
            }
          """
        })(isRight(anything))
      },
      testM("bubbles the error to the error channel for defs") {
        @accessible
        trait Module {
          @throwing
          def test(): Unit = throw new Exception("ups")
        }
        def layer = ZLayer.succeed(new Module {})
        assertM(Module.test().flip.provideLayer(layer))(hasField("message", _.getMessage, equalTo("ups")))
      }
    )
  )
}
