package zio.macros

import zio._
import zio.test.Assertion._
import zio.test._

object AccessibleMSpec extends DefaultRunnableSpec {

  def spec: ZSpec[Environment, Failure] = suite("AccessibleMSpec")(
    suite("AccessibleM macro")(
      testM("compiles when applied to object with empty Service") {
        assertM(typeCheck {
          """
            @accessibleM[UIO]
            object Module {
              trait Service[F[_]]
            }
          """
        })(isRight(anything))
      },
      testM("fails when applied to object without a Service") {
        assertM(typeCheck {
          """
            @accessibleM[UIO]
            object Module
          """
        })(isLeft(anything))
      },
      testM("success when applied to trait") {
        assertM(typeCheck {
          """
            @accessibleM[UIO]
            trait Module[F[_]]
          """
        })(isRight(anything))
      },
      testM("fails when applied to class") {
        assertM(typeCheck {
          """
            @accessibleM[UIO]
            class Module[F[_]]
          """
        })(isLeft(anything))
      },
      testM("fails when applied to object with Service without type param") {
        assertM(typeCheck {
          """
            @accessibleM[UIO]
            object Module {
              trait Service
            }
          """
        })(isLeft(anything))
      },
      testM("fails when applied to Service with Service without suitable type param") {
        assertM(typeCheck {
          """
            @accessibleM[UIO]
            object Module {
              trait Service[F[_, _]]
            }
          """
        })(isLeft(anything))
      },
      testM("fails when applied to Service with Service with multiple suitable type params") {
        assertM(typeCheck {
          """
            @accessibleM[UIO]
            object Module {
              trait Service[F[_], G[_]]
            }
          """
        })(isLeft(anything))
      },
      testM("fails when applied to non-ZIO type param") {
        assertM(typeCheck {
          """
            @accessibleM[List]
            object Module {
              trait Service[F[_]]
            }
          """
        })(isLeft(anything))
      },
      testM("generates accessor for values") {
        assertM(typeCheck {
          """
            @accessibleM[UIO]
            object Module {
              trait Service[F[_]] {
                val foo: F[Unit]
              }
            }

            object Check {
              val foo: ZIO[Has[Module.Service[UIO]], Nothing, Unit] =
                Module.foo
            }
          """
        })(isRight(anything))
      },
      testM("generates accessor for functions") {
        assertM(typeCheck {
          """
            @accessibleM[UIO]
            object Module {
              trait Service[F[_]] {
                def foo(i: Int): F[Unit]
              }
            }

            object Check {
              def foo(i: Int): ZIO[Has[Module.Service[UIO]], Nothing, Unit] =
                Module.foo(i)
            }
          """
        })(isRight(anything))
      },
      testM("generates accessor for varargs functions") {
        assertM(typeCheck {
          """
            @accessibleM[UIO]
            object Module {
              trait Service[F[_]] {
                def varargsFoo(a: Int, b: Int*): F[Unit]
              }
            }

            object Check {
              def varargsFoo(a: Int, b: Int*): ZIO[Has[Module.Service[UIO]], Nothing, Unit] =
                Module.varargsFoo(a, b: _*)
            }
          """
        })(isRight(anything))
      },
      testM("compiles when applied to method with simple return type") {
        assertM(typeCheck {
          """
            @accessibleM[UIO]
            object Module {
              trait Service[F[_]] {
                def foo(a: Int): Task[Unit]
              }
            }

            object Check {
              def foo(a: Int): ZIO[Has[Module.Service[UIO]], Throwable, Unit] =
                Module.foo(a)
            }
          """
        })(isRight(anything))
      },
      testM("generates accessors for members returning ZManaged") {
        assertM(typeCheck {
          """
            @accessibleM[TaskManaged]
            object Module {
              trait Service[F[_]] {
                def managed(s: String): F[Int]
              }
            }

            object Check {
              def managed(s: String): ZManaged[Has[Module.Service[TaskManaged]], Throwable, Int] =
                Module.managed(s)
            }
          """
        })(isRight(anything))
      },
      testM("generates accessor for service with default method implementations") {
        assertM(typeCheck {
          """
             @accessibleM[Task]
             object Module {
               trait Service[F[_]] {
                 def foo(x: Int): F[Unit] = foo(x.toString)
                 def foo(x: String): F[Unit]
               }
             }

             object Check {
              def foo(x: Int): ZIO[Has[Module.Service[Task]], Throwable, Unit] =
                Module.foo(x)
              def foo(x: String): ZIO[Has[Module.Service[Task]], Throwable, Unit] =
                Module.foo(x)
            }
          """.stripMargin
        })(isRight(anything))
      },
      testM("generates accessor for service with one type param other than F") {
        assertM(typeCheck {
          """
             @accessibleM[Task]
             object Module {
               trait Service[F[_], T] {
                 val v: F[T]
                 def f1: F[Unit]
                 def f2(): F[Unit]
                 def f3(t: T): F[Unit]
                 def f4(t: T)(i: Int): F[Unit]
                 def f5(t: T)(implicit i: Int): F[Unit]
                 def f6(t: T*): F[Unit]
               }
             }

             object Check {
               def v[T: Tag]: ZIO[Has[Module.Service[Task, T]], Throwable, T] =
                 Module.v[T]
               def f1[T: Tag]: ZIO[Has[Module.Service[Task, T]], Throwable, Unit] =
                 Module.f1[T]
               def f2[T: Tag](): ZIO[Has[Module.Service[Task, T]], Throwable, Unit] =
                 Module.f2[T]()
               def f3[T: Tag](t: T): ZIO[Has[Module.Service[Task, T]], Throwable, Unit] =
                 Module.f3[T](t)
               def f4[T: Tag](t: T)(i: Int): ZIO[Has[Module.Service[Task, T]], Throwable, Unit] =
                 Module.f4[T](t)(i)
               def f5[T: Tag](t: T)(implicit i: Int): ZIO[Has[Module.Service[Task, T]], Throwable, Unit] =
                 Module.f5[T](t)
               def f6[T: Tag](t: T*): ZIO[Has[Module.Service[Task, T]], Throwable, Unit] =
                 Module.f6[T](t: _*)
             }
          """
        })(isRight(anything))
      },
      testM("generates accessor for service with covariant type param") {
        assertM(typeCheck {
          """
             @accessibleM[UIO]
             object Module {
               trait Service[+A, F[_]] {
                 val v: F[A]
               }
             }

             object Check {
               def v[A: Tag]: ZIO[Has[Module.Service[A, UIO]], Nothing, A] =
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

             @accessibleM[Task]
             object Module {
               trait Service[T <: Foo, M[_], U >: Bar] {
                 val v: M[T]
                 def f1: M[U]
                 def f2(): M[U]
                 def f3(t: T): M[U]
                 def f4(t: T)(u: U): M[U]
                 def f5(t: T)(implicit u: U): M[U]
                 def f6(t: T*): M[U]
               }
             }

             object Check {
               def v[T <: Foo: Tag, U >: Bar: Tag]: ZIO[Has[Module.Service[T, Task, U]], Throwable, T] =
                 Module.v[T, U]
               def f1[T <: Foo: Tag, U >: Bar: Tag]: ZIO[Has[Module.Service[T, Task, U]], Throwable, U] =
                 Module.f1[T, U]
               def f2[T <: Foo: Tag, U >: Bar: Tag](): ZIO[Has[Module.Service[T, Task, U]], Throwable, U] =
                 Module.f2[T, U]()
               def f3[T <: Foo: Tag, U >: Bar: Tag](t: T): ZIO[Has[Module.Service[T, Task, U]], Throwable, U] =
                 Module.f3[T, U](t)
               def f4[T <: Foo: Tag, U >: Bar: Tag](t: T)(u: U): ZIO[Has[Module.Service[T, Task, U]], Throwable, U] =
                 Module.f4[T, U](t)(u)
               def f5[T <: Foo: Tag, U >: Bar: Tag](t: T)(implicit u: U): ZIO[Has[Module.Service[T, Task, U]], Throwable, U] =
                 Module.f5[T, U](t)
               def f6[T <: Foo: Tag, U >: Bar: Tag](t: T*): ZIO[Has[Module.Service[T, Task, U]], Throwable, U] =
                 Module.f6[T, U](t: _*)
             }
          """
        })(isRight(anything))
      },
      testM("generates accessors for ZIO capabilities") {
        assertM(typeCheck {
          """
            @accessibleM[UIO]
            object Module {
              trait Service[M[_]] {
                val static                                 : M[String]
                def zeroArgs                               : M[Int]
                def zeroArgsWithParens()                   : M[Long]
                def singleArg(arg1: Int)                   : M[String]
                def multiArgs(arg1: Int, arg2: Long)       : M[String]
                def multiParamLists(arg1: Int)(arg2: Long) : M[String]
                def typedVarargs[T](arg1: Int, arg2: T*)   : M[T]
                def command(arg1: Int)                     : M[Unit]
                def overloaded(arg1: Int)                  : M[String]
                def overloaded(arg1: Long)                 : M[String]
              }
            }

            object Check {
              val static                                 : ZIO[Has[Module.Service[UIO]], Nothing, String] = Module.static
              def zeroArgs                               : ZIO[Has[Module.Service[UIO]], Nothing, Int]    = Module.zeroArgs
              def zeroArgsWithParens()                   : ZIO[Has[Module.Service[UIO]], Nothing, Long]   = Module.zeroArgsWithParens()
              def singleArg(arg1: Int)                   : ZIO[Has[Module.Service[UIO]], Nothing, String] = Module.singleArg(arg1)
              def multiArgs(arg1: Int, arg2: Long)       : ZIO[Has[Module.Service[UIO]], Nothing, String] = Module.multiArgs(arg1, arg2)
              def multiParamLists(arg1: Int)(arg2: Long) : ZIO[Has[Module.Service[UIO]], Nothing, String] = Module.multiParamLists(arg1)(arg2)
              def typedVarargs[T](arg1: Int, arg2: T*)   : ZIO[Has[Module.Service[UIO]], Nothing, T]      = Module.typedVarargs[T](arg1, arg2: _*)
              def command(arg1: Int)                     : ZIO[Has[Module.Service[UIO]], Nothing, Unit]   = Module.command(arg1)
              def overloaded(arg1: Int)                  : ZIO[Has[Module.Service[UIO]], Nothing, String] = Module.overloaded(arg1)
              def overloaded(arg1: Long)                 : ZIO[Has[Module.Service[UIO]], Nothing, String] = Module.overloaded(arg1)
            }
          """
        })(isRight(anything))
      },
      testM("generates accessors for ZManaged capabilities") {
        assertM(typeCheck {
          """
            @accessibleM[UManaged]
            object Module {
              trait Service[M[_]] {
                val staticManaged                                 : M[String]
                def zeroArgsManaged                               : M[Int]
                def zeroArgsTypedManaged[T]                       : M[T]
                def zeroArgsWithParensManaged()                   : M[Long]
                def singleArgManaged(arg1: Int)                   : M[String]
                def multiArgsManaged(arg1: Int, arg2: Long)       : M[String]
                def multiParamListsManaged(arg1: Int)(arg2: Long) : M[String]
                def typedVarargsManaged[T](arg1: Int, arg2: T*)   : M[T]
                def commandManaged(arg1: Int)                     : M[Unit]
                def overloadedManaged(arg1: Int)                  : M[String]
                def overloadedManaged(arg1: Long)                 : M[String]
              }
            }

            object Check {
              val staticManaged                                 : ZManaged[Has[Module.Service[UManaged]], Nothing, String] = Module.staticManaged
              def zeroArgsManaged                               : ZManaged[Has[Module.Service[UManaged]], Nothing, Int]    = Module.zeroArgsManaged
              def zeroArgsTypedManaged[T]                       : ZManaged[Has[Module.Service[UManaged]], Nothing, T]      = Module.zeroArgsTypedManaged[T]
              def zeroArgsWithParensManaged()                   : ZManaged[Has[Module.Service[UManaged]], Nothing, Long]   = Module.zeroArgsWithParensManaged()
              def singleArgManaged(arg1: Int)                   : ZManaged[Has[Module.Service[UManaged]], Nothing, String] = Module.singleArgManaged(arg1)
              def multiArgsManaged(arg1: Int, arg2: Long)       : ZManaged[Has[Module.Service[UManaged]], Nothing, String] = Module.multiArgsManaged(arg1, arg2)
              def multiParamListsManaged(arg1: Int)(arg2: Long) : ZManaged[Has[Module.Service[UManaged]], Nothing, String] = Module.multiParamListsManaged(arg1)(arg2)
              def typedVarargsManaged[T](arg1: Int, arg2: T*)   : ZManaged[Has[Module.Service[UManaged]], Nothing, T]      = Module.typedVarargsManaged[T](arg1, arg2: _*)
              def commandManaged(arg1: Int)                     : ZManaged[Has[Module.Service[UManaged]], Nothing, Unit]   = Module.commandManaged(arg1)
              def overloadedManaged(arg1: Int)                  : ZManaged[Has[Module.Service[UManaged]], Nothing, String] = Module.overloadedManaged(arg1)
              def overloadedManaged(arg1: Long)                 : ZManaged[Has[Module.Service[UManaged]], Nothing, String] = Module.overloadedManaged(arg1)
            }
          """
        })(isRight(anything))
      },
      testM("generates accessors for method capabilities") {
        assertM(typeCheck {
          """
            @accessibleM[UIO]
            object Module {
              trait Service[F[_]] {
                def function(arg1: Int) : String
              }
            }

            object Check {
              def function(arg1: Int) : ZIO[Has[Module.Service[UIO]], Throwable, String] = Module.function(arg1)
            }
          """
        })(isRight(anything))
      },
      testM("preserves type constructor covariance") {
        assertM(typeCheck {
          """
             @accessibleM[UIO]
             object Module {
               trait Service[F[+_]] {
                 val v: F[Int]
                 final val vCov: F[Any] = v
               }
             }

             object Check {
               def assertServiceVariant[S[F[+_]]] = ()
               assertServiceVariant[Module.Service]

               val v: ZIO[Has[Module.Service[UIO]], Nothing, Int] =
                 Module.v

               val vCov: ZIO[Has[Module.Service[UIO]], Nothing, Any] =
                 Module.vCov
             }
          """
        })(isRight(anything))
      },
      testM("preserves Service covariance") {
        assertM(typeCheck {
          """
             @accessibleM[UIO]
             object Module {
               trait Service[+F[_]] {
                 val v: F[Int]
               }

               val narrow: Service[UIO] = ???
               val widen: Service[Task] = narrow
             }

             object Check {
               def assertServiceVariant[S[+F[_]]] = ()
               assertServiceVariant[Module.Service]

               val v: ZIO[Has[Module.Service[UIO]], Nothing, Int] =
                 Module.v
             }
          """
        })(isRight(anything))
      },
      testM("preserves Service contravariance") {
        assertM(typeCheck {
          """
             @accessibleM[UIO]
             object Module {
               trait Service[-F[_]] {
                 val v: F[Int]
               }

               val narrow: Service[Task] = ???
               val widen: Service[UIO] = narrow
             }

             object Check {
               def assertServiceVariant[S[-F[_]]] = ()
               assertServiceVariant[Module.Service]

               val v: ZIO[Has[Module.Service[UIO]], Nothing, Int] =
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
             @accessibleM[UIO]
             object Module {
               trait Service[F[-_]] {
                 val v: F[AnyVal]
               }
             }

             object Check {
               val v: ZIO[Has[Module.Service[UIO]], Nothing, Int] =
                 Module.v
             }
          """
        })(isLeft(anything))
      }
    )
  )
}
