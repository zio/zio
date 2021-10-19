package zio.macros

import zio._
import zio.test.Assertion._
import zio.test._

object AccessibleMMSpec extends DefaultRunnableSpec {

  def spec: ZSpec[Environment, Failure] = suite("AccessibleMMSpec")(
    suite("AccessibleMM macro")(
      testM("compiles when applied to object with empty Service") {
        assertM(typeCheck {
          """
            @accessibleMM[IO]
            object Module {
              trait Service[F[_, _]]
            }
          """
        })(isRight(anything))
      },
      testM("fails when applied to object without a Service") {
        assertM(typeCheck {
          """
            @accessibleMM[IO]
            object Module
          """
        })(isLeft(anything))
      },
      testM("success when applied to trait") {
        assertM(typeCheck {
          """
            @accessibleMM[IO]
            trait Module[F[_, _]]
          """
        })(isRight(anything))
      },
      testM("fails when applied to class") {
        assertM(typeCheck {
          """
            @accessibleMM[IO]
            class Module[F[_, _]]
          """
        })(isLeft(anything))
      },
      testM("fails when applied to object with Service without type param") {
        assertM(typeCheck {
          """
            @accessibleMM[IO]
            object Module {
              trait Service
            }
          """
        })(isLeft(anything))
      },
      testM("fails when applied to Service with Service without suitable type param") {
        assertM(typeCheck {
          """
            @accessibleMM[IO]
            object Module {
              trait Service[F[_]]
            }
          """
        })(isLeft(anything))
      },
      testM("fails when applied to Service with Service with multiple suitable type params") {
        assertM(typeCheck {
          """
            @accessibleMM[IO]
            object Module {
              trait Service[F[_, _], G[_, _]]
            }
          """
        })(isLeft(anything))
      },
      testM("fails when applied to non-ZIO type param") {
        assertM(typeCheck {
          """
            @accessibleMM[Either]
            object Module {
              trait Service[F[_, _]]
            }
          """
        })(isLeft(anything))
      },
      testM("generates accessor for values") {
        assertM(typeCheck {
          """
            @accessibleMM[URIO]
            object Module {
              trait Service[F[_, _]] {
                val foo: F[Has[Unit], Unit]
              }
            }

            object Check {
              val foo: ZIO[Has[Module.Service[URIO]] with Has[Unit], Nothing, Unit] =
                Module.foo
            }
          """
        })(isRight(anything))
      },
      testM("generates accessor for functions") {
        assertM(typeCheck {
          """
            @accessibleMM[URIO]
            object Module {
              trait Service[F[_, _]] {
                def foo(i: Int): F[Has[Unit], Unit]
              }
            }

            object Check {
              def foo(i: Int): ZIO[Has[Module.Service[URIO]] with Has[Unit], Nothing, Unit] =
                Module.foo(i)
            }
          """
        })(isRight(anything))
      },
      testM("generates accessor for varargs functions") {
        assertM(typeCheck {
          """
            @accessibleMM[IO]
            object Module {
              trait Service[F[_, _]] {
                def varargsFoo(a: Int, b: Int*): F[Unit, Unit]
              }
            }

            object Check {
              def varargsFoo(a: Int, b: Int*): ZIO[Has[Module.Service[IO]], Unit, Unit] =
                Module.varargsFoo(a, b: _*)
            }
          """
        })(isRight(anything))
      },
      testM("compiles when applied to method with simple return type") {
        assertM(typeCheck {
          """
            @accessibleMM[IO]
            object Module {
              trait Service[F[_, _]] {
                def foo(a: Int): Task[Unit]
              }
            }

            object Check {
              def foo(a: Int): ZIO[Has[Module.Service[IO]], Throwable, Unit] =
                Module.foo(a)
            }
          """
        })(isRight(anything))
      },
      testM("generates accessors for members returning ZManaged") {
        assertM(typeCheck {
          """
            @accessibleMM[RManaged]
            object Module {
              trait Service[F[_, _]] {
                def managed(s: String): F[Has[Unit], Int]
              }
            }

            object Check {
              def managed(s: String): ZManaged[Has[Module.Service[RManaged]] with Has[Unit], Throwable, Int] =
                Module.managed(s)
            }
          """
        })(isRight(anything))
      },
      testM("generates accessor for service with default method implementations") {
        assertM(typeCheck {
          """
             @accessibleMM[IO]
             object Module {
               trait Service[F[_, _]] {
                 def foo(x: Int): F[Unit, Unit] = foo(x.toString)
                 def foo(x: String): F[Unit, Unit]
               }
             }

             object Check {
              def foo(x: Int): ZIO[Has[Module.Service[IO]], Unit, Unit] =
                Module.foo(x)
              def foo(x: String): ZIO[Has[Module.Service[IO]], Unit, Unit] =
                Module.foo(x)
            }
          """.stripMargin
        })(isRight(anything))
      },
      testM("generates accessor for service with one type param other than F") {
        assertM(typeCheck {
          """
             @accessibleMM[IO]
             object Module {
               trait Service[F[_, _], T] {
                 val v: F[Int, T]
                 def f1: F[Int, Unit]
                 def f2(): F[Int, Unit]
                 def f3(t: T): F[Int, Unit]
                 def f4(t: T)(i: Int): F[Int, Unit]
                 def f5(t: T)(implicit i: Int): F[Int, Unit]
                 def f6(t: T*): F[Int, Unit]
               }
             }

             object Check {
               def v[T: Tag]: ZIO[Has[Module.Service[IO, T]], Int, T] =
                 Module.v[T]
               def f1[T: Tag]: ZIO[Has[Module.Service[IO, T]], Int, Unit] =
                 Module.f1[T]
               def f2[T: Tag](): ZIO[Has[Module.Service[IO, T]], Int, Unit] =
                 Module.f2[T]()
               def f3[T: Tag](t: T): ZIO[Has[Module.Service[IO, T]], Int, Unit] =
                 Module.f3[T](t)
               def f4[T: Tag](t: T)(i: Int): ZIO[Has[Module.Service[IO, T]], Int, Unit] =
                 Module.f4[T](t)(i)
               def f5[T: Tag](t: T)(implicit i: Int): ZIO[Has[Module.Service[IO, T]], Int, Unit] =
                 Module.f5[T](t)
               def f6[T: Tag](t: T*): ZIO[Has[Module.Service[IO, T]], Int, Unit] =
                 Module.f6[T](t: _*)
             }
          """
        })(isRight(anything))
      },
      testM("generates accessor for service with contravariant type param") {
        assertM(typeCheck {
          """
             @accessibleMM[RIO]
             object Module {
               trait Service[-A, F[_, _]] {
                 val v: F[A, Int]
               }
             }

             object Check {
               def v[A: Tag]: ZIO[Has[Module.Service[A, RIO]] with A, Throwable, Int] =
                 Module.v[A]
             }
          """
        })(isRight(anything))
      },
      testM("generates accessor for service with two type params and type bounds") {
        assertM(typeCheck {
          """
             trait Foo
             trait Bar

             @accessibleMM[IO]
             object Module {
               trait Service[T <: Foo, M[_, _], U >: Bar] {
                 val v: M[U, T]
                 def f1: M[T, U]
                 def f2(): M[T, U]
                 def f3(t: T): M[T, U]
                 def f4(t: T)(u: U): M[T, U]
                 def f5(t: T)(implicit u: U): M[T, U]
                 def f6(t: T*): M[T, U]
               }
             }

             object Check {
               def v[T <: Foo: Tag, U >: Bar: Tag]: ZIO[Has[Module.Service[T, IO, U]], U, T] =
                 Module.v[T, U]
               def f1[T <: Foo: Tag, U >: Bar: Tag]: ZIO[Has[Module.Service[T, IO, U]], T, U] =
                 Module.f1[T, U]
               def f2[T <: Foo: Tag, U >: Bar: Tag](): ZIO[Has[Module.Service[T, IO, U]], T, U] =
                 Module.f2[T, U]()
               def f3[T <: Foo: Tag, U >: Bar: Tag](t: T): ZIO[Has[Module.Service[T, IO, U]], T, U] =
                 Module.f3[T, U](t)
               def f4[T <: Foo: Tag, U >: Bar: Tag](t: T)(u: U): ZIO[Has[Module.Service[T, IO, U]], T, U] =
                 Module.f4[T, U](t)(u)
               def f5[T <: Foo: Tag, U >: Bar: Tag](t: T)(implicit u: U): ZIO[Has[Module.Service[T, IO, U]], T, U] =
                 Module.f5[T, U](t)
               def f6[T <: Foo: Tag, U >: Bar: Tag](t: T*): ZIO[Has[Module.Service[T, IO, U]], T, U] =
                 Module.f6[T, U](t: _*)
             }
          """
        })(isRight(anything))
      },
      testM("generates accessors for ZIO capabilities") {
        assertM(typeCheck {
          """
            @accessibleMM[IO]
            object Module {
              trait Service[M[_, _]] {
                val static                                 : M[Int, String]
                def zeroArgs                               : M[Int, Int]
                def zeroArgsWithParens()                   : M[Int, Long]
                def singleArg(arg1: Int)                   : M[Int, String]
                def multiArgs(arg1: Int, arg2: Long)       : M[Int, String]
                def multiParamLists(arg1: Int)(arg2: Long) : M[Int, String]
                def typedVarargs[T](arg1: Int, arg2: T*)   : M[Int, T]
                def command(arg1: Int)                     : M[Int, Unit]
                def overloaded(arg1: Int)                  : M[Int, String]
                def overloaded(arg1: Long)                 : M[Int, String]
              }
            }

            object Check {
              val static                                 : ZIO[Has[Module.Service[IO]], Int, String] = Module.static
              def zeroArgs                               : ZIO[Has[Module.Service[IO]], Int, Int]    = Module.zeroArgs
              def zeroArgsWithParens()                   : ZIO[Has[Module.Service[IO]], Int, Long]   = Module.zeroArgsWithParens()
              def singleArg(arg1: Int)                   : ZIO[Has[Module.Service[IO]], Int, String] = Module.singleArg(arg1)
              def multiArgs(arg1: Int, arg2: Long)       : ZIO[Has[Module.Service[IO]], Int, String] = Module.multiArgs(arg1, arg2)
              def multiParamLists(arg1: Int)(arg2: Long) : ZIO[Has[Module.Service[IO]], Int, String] = Module.multiParamLists(arg1)(arg2)
              def typedVarargs[T](arg1: Int, arg2: T*)   : ZIO[Has[Module.Service[IO]], Int, T]      = Module.typedVarargs[T](arg1, arg2: _*)
              def command(arg1: Int)                     : ZIO[Has[Module.Service[IO]], Int, Unit]   = Module.command(arg1)
              def overloaded(arg1: Int)                  : ZIO[Has[Module.Service[IO]], Int, String] = Module.overloaded(arg1)
              def overloaded(arg1: Long)                 : ZIO[Has[Module.Service[IO]], Int, String] = Module.overloaded(arg1)
            }
          """
        })(isRight(anything))
      },
      testM("generates accessors for ZManaged capabilities") {
        assertM(typeCheck {
          """
            @accessibleMM[URManaged]
            object Module {
              trait Service[M[_, _]] {
                val staticManaged                                 : M[Has[Int], String]
                def zeroArgsManaged                               : M[Has[Int], Int]
                def zeroArgsTypedManaged[T]                       : M[Has[Int], T]
                def zeroArgsWithParensManaged()                   : M[Has[Int], Long]
                def singleArgManaged(arg1: Int)                   : M[Has[Int], String]
                def multiArgsManaged(arg1: Int, arg2: Long)       : M[Has[Int], String]
                def multiParamListsManaged(arg1: Int)(arg2: Long) : M[Has[Int], String]
                def typedVarargsManaged[T](arg1: Int, arg2: T*)   : M[Has[Int], T]
                def commandManaged(arg1: Int)                     : M[Has[Int], Unit]
                def overloadedManaged(arg1: Int)                  : M[Has[Int], String]
                def overloadedManaged(arg1: Long)                 : M[Has[Int], String]
              }
            }

            object Check {
              val staticManaged                                 : ZManaged[Has[Module.Service[URManaged]] with Has[Int], Nothing, String] = Module.staticManaged
              def zeroArgsManaged                               : ZManaged[Has[Module.Service[URManaged]] with Has[Int], Nothing, Int]    = Module.zeroArgsManaged
              def zeroArgsTypedManaged[T]                       : ZManaged[Has[Module.Service[URManaged]] with Has[Int], Nothing, T]      = Module.zeroArgsTypedManaged[T]
              def zeroArgsWithParensManaged()                   : ZManaged[Has[Module.Service[URManaged]] with Has[Int], Nothing, Long]   = Module.zeroArgsWithParensManaged()
              def singleArgManaged(arg1: Int)                   : ZManaged[Has[Module.Service[URManaged]] with Has[Int], Nothing, String] = Module.singleArgManaged(arg1)
              def multiArgsManaged(arg1: Int, arg2: Long)       : ZManaged[Has[Module.Service[URManaged]] with Has[Int], Nothing, String] = Module.multiArgsManaged(arg1, arg2)
              def multiParamListsManaged(arg1: Int)(arg2: Long) : ZManaged[Has[Module.Service[URManaged]] with Has[Int], Nothing, String] = Module.multiParamListsManaged(arg1)(arg2)
              def typedVarargsManaged[T](arg1: Int, arg2: T*)   : ZManaged[Has[Module.Service[URManaged]] with Has[Int], Nothing, T]      = Module.typedVarargsManaged[T](arg1, arg2: _*)
              def commandManaged(arg1: Int)                     : ZManaged[Has[Module.Service[URManaged]] with Has[Int], Nothing, Unit]   = Module.commandManaged(arg1)
              def overloadedManaged(arg1: Int)                  : ZManaged[Has[Module.Service[URManaged]] with Has[Int], Nothing, String] = Module.overloadedManaged(arg1)
              def overloadedManaged(arg1: Long)                 : ZManaged[Has[Module.Service[URManaged]] with Has[Int], Nothing, String] = Module.overloadedManaged(arg1)
            }
          """
        })(isRight(anything))
      },
      testM("generates accessors for method capabilities") {
        assertM(typeCheck {
          """
            @accessibleMM[IO]
            object Module {
              trait Service[F[_, _]] {
                def function(arg1: Int) : String
              }
            }

            object Check {
              def function(arg1: Int) : ZIO[Has[Module.Service[IO]], Throwable, String] = Module.function(arg1)
            }
          """
        })(isRight(anything))
      },
      testM("preserves type constructor co- and contravariance") {
        assertM(typeCheck {
          """
             @accessibleMM[URIO]
             object Module {
               trait Service[F[-_, +_]] {
                 val v: F[Any, Int]
                 final val vCov: F[Int, Any] = v
               }
             }

             object Check {
               def assertServiceVariant[S[F[-_, +_]]] = ()
               assertServiceVariant[Module.Service]

               val v: ZIO[Has[Module.Service[URIO]] with Any, Nothing, Int] =
                 Module.v

               val vCov: ZIO[Has[Module.Service[URIO]] with Int, Nothing, Any] =
                 Module.vCov
             }
          """
        })(isRight(anything))
      },
      testM("preserves Service covariance") {
        assertM(typeCheck {
          """
             @accessibleMM[URIO]
             object Module {
               trait Service[+F[_, _]] {
                 val v: F[Any, Int]
               }

               val narrow: Service[URIO] = ???
               val widen: Service[RIO] = narrow
             }

             object Check {
               def assertServiceVariant[S[+F[_, _]]] = ()
               assertServiceVariant[Module.Service]

               val v: ZIO[Has[Module.Service[URIO]], Nothing, Int] =
                 Module.v
             }
          """
        })(isRight(anything))
      },
      testM("preserves Service contravariance") {
        assertM(typeCheck {
          """
             @accessibleMM[URIO]
             object Module {
               trait Service[-F[_, _]] {
                 val v: F[Any, Int]
               }

               val narrow: Service[RIO] = ???
               val widen: Service[URIO] = narrow
             }

             object Check {
               def assertServiceVariant[S[-F[_, _]]] = ()
               assertServiceVariant[Module.Service]

               val v: ZIO[Has[Module.Service[URIO]], Nothing, Int] =
                 Module.v
             }
          """
        })(isRight(anything))
      },
      // this test mimics the situation when covariant type appears in contravariant position
      // in reality, the code will not compile due to true variance check, but in tests `c.typecheck` doesn't check it
      testM("fails when contravariant type appears in covariant position") {
        assertM(typeCheck {
          """
             @accessibleMM[URIO]
             object Module {
               trait Service[F[+_, +_]] {
                 val v: F[Int, Int]
               }
             }

             object Check {
               val v: ZIO[Has[Module.Service[URIO]] with AnyVal, Nothing, Int] =
                 Module.v
             }
          """
        })(isLeft(anything))
      }
    )
  )
}
