/*
 * Copyright 2017-2020 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.stacktracer.impls

import org.objectweb.asm._
import zio.internal.stacktracer.Tracer
import zio.internal.stacktracer.ZTraceElement.SourceLocation
import zio.stacktracer.impls.AsmTracer.MethodSearchVisitor

import java.lang.invoke.SerializedLambda
import scala.util.{ Failure, Success, Try }

/**
 * Java 8+ only
 *
 * file:line extraction will work for lambdas only.
 * For subclasses of scala.Function1 it will point to the class definition
 */
final class AsmTracer extends Tracer {

  def traceLocation(lambda: AnyRef): SourceLocation = {
    val clazz       = lambda.getClass
    val classLoader = clazz.getClassLoader

    Try(clazz.getDeclaredMethod("writeReplace")) match {
      case Failure(_) =>
        val name   = clazz.getName
        val reader = new ClassReader(classLoader.getResourceAsStream(name.replace('.', '/') + ".class"))

        val cv = new MethodSearchVisitor("apply", null, Opcodes.ASM7)

        reader.accept(cv, 0)

        SourceLocation(cv.sourcefile, name, "apply", cv.line)

      case Success(replaceMethod) =>
        replaceMethod.setAccessible(true)

        val sl         = replaceMethod.invoke(lambda).asInstanceOf[SerializedLambda]
        val reader     = new ClassReader(classLoader.getResourceAsStream(sl.getImplClass.replace('.', '/') + ".class"))
        val methodName = sl.getImplMethodName
        val cv         = new MethodSearchVisitor(methodName, sl.getImplMethodSignature, Opcodes.ASM7)
        reader.accept(cv, 0)

        val classString = sl.getImplClass.replace('/', '.')

        SourceLocation(cv.sourcefile, classString, methodName, cv.line)
    }
  }

}

object AsmTracer {
  final class MethodSearchVisitor(
    methodName: String,
    methodSignature: String,
    apiVersion: Int
  ) extends ClassVisitor(apiVersion) {

    var sourcefile: String = _
    var line: Int          = _

    override def visitSource(source: String, debug: String): Unit =
      sourcefile = source

    override def visitMethod(
      access: Int,
      name: String,
      descriptor: String,
      signature: String,
      exceptions: Array[String]
    ): MethodVisitor =
      if (name == methodName && (methodSignature == null || methodSignature == descriptor)) {
        new MethodVisitor(apiVersion) {
          override def visitLineNumber(line: Int, start: Label): Unit =
            MethodSearchVisitor.this.line = line
        }
      } else {
        super.visitMethod(access, name, descriptor, signature, exceptions)
      }
  }
}
