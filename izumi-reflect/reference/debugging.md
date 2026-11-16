# Debugging and DebugProperties

> The DebugProperties settings of izumi-reflect: debug logs for subtype checks and tag creation, assertions and caches.

`izumi-reflect` can explain what it does: it can print how each tag is built during compilation, how each subtype check is decided at runtime, and the inheritance data stored inside a tag. This page describes the switches that control this output and the other settings in `izumi.reflect.DebugProperties`. For a step-by-step procedure, see [Debug a Wrong Tag or Subtype Check](../guides/debug-a-wrong-tag.md).

## DebugProperties

`DebugProperties` is an object whose fields hold the names of all supported settings, so you can refer to them from code instead of typing the strings:

```scala
import izumi.reflect.DebugProperties

DebugProperties.`izumi.reflect.debug.macro.rtti`
// res0: String = "izumi.reflect.debug.macro.rtti"
```

Most settings are Java system properties, read once when the library first needs them. One is a compiler option for Scala 2 macros. The table lists them all:

| Setting                                     | Kind                  | Default | Scala versions | Effect                                                                                               |
| ------------------------------------------- | --------------------- | ------- | -------------- | ---------------------------------------------------------------------------------------------------- |
| `izumi.reflect.debug.macro.rtti`            | System property       | `false` | 2 and 3        | Logs subtype checks at runtime; on Scala 2, also logs tag creation during compilation                |
| `izumi.reflect.debug.macro.rtti.assertions` | System property       | `false` | 2              | Enables extra consistency checks while tags are created                                              |
| `izumi.reflect.rtti.optimized.equals`       | System property       | `true`  | 2 and 3        | Compares serialized tags by their strings first; set to `false` to always compare the full structure |
| `izumi.reflect.rtti.cache.compile`          | Scala 2 macro setting | `true`  | 2              | Caches tags computed during compilation                                                              |
| `izumi.reflect.rtti.cache.runtime`          | System property       | `true`  | 2 (JVM)        | Caches tags built from `scala-reflect` types at runtime                                              |

## Debug Output

The `izumi.reflect.debug.macro.rtti` property turns on the library's debug log. It has two parts, which you enable in different places.

### Runtime Subtype Checks

When the property is set in the JVM that runs your program, every call to `LightTypeTag#<:<` prints each step of the check to standard output: which parents of the left type it visits and which rule decides the result. Set it when you start the program:

```bash
java -Dizumi.reflect.debug.macro.rtti=true -jar app.jar
```

In sbt, `run` and `test` usually fork a separate JVM, so pass the property to the forked JVM in `build.sbt`:

```scala
Test / javaOptions += "-Dizumi.reflect.debug.macro.rtti=true"
```

The library also enables the log when a shorter prefix of the property name is set to `true`, such as `-Dizumi.reflect=true` or `-Dizumi=true`.

### Tag Creation During Compilation (Scala 2)

On Scala 2, the same property also makes the macros print how they build each tag, as compiler info messages at the position of the `Tag` in your source. The property must be set in the JVM that runs the compiler, which is sbt itself:

```bash
sbt -Dizumi.reflect.debug.macro.rtti=true compile
```

You can also add the flag to the `.jvmopts` file of your project. IntelliJ IDEA compiles with its own Scala compile server, which does not read `.jvmopts`: add the flag to the VM options of the compile server in **Settings → Build, Execution, Deployment → Compiler → Scala Compiler → Scala Compile Server** instead.

On Scala 3, the macros have no debug output: the switch is turned off in the library code, so this property only affects runtime subtype checks.

## Inspecting a Tag

`LightTypeTag#debug` returns a description of a tag together with the inheritance data it carries. This data decides the result of every subtype check, so it is the first thing to look at when a check returns an unexpected result:

```scala
import izumi.reflect.Tag

println(Tag[Some[Int]].tag.debug("someInt"))
// ?? begin someInt: scala.Some[+scala.Int]
// ??bases:
//  - ? %0 ? scala.Some[+0] ->   
//    * ? %0 ? scala.Option[+0]
//    * ? %0 ? scala.collection.IterableOnce[+0]
//    * java.io.Serializable
//    * scala.Equals
//    * scala.Product
//  - scala.Int ->   
//    * scala.AnyVal
//  - scala.Some[+scala.Int] ->   
//    * java.io.Serializable
//    * scala.Equals
//    * scala.Product
//    * scala.Option[+scala.Int]
//    * scala.collection.IterableOnce[+scala.Int]
// ??inheritance:
//  - scala.Int ->   
//    * scala.AnyVal
//  - scala.Some ->   
//    * java.io.Serializable
//    * scala.Equals
//    * scala.Option
//    * scala.Product
//    * scala.collection.IterableOnce
// ?? end someInt
```

The `bases` section maps each type, and each type constructor written as a type lambda, to its known parents. The `inheritance` section maps class names to parent class names, without type arguments. If a parent you expect is missing from both, `LightTypeTag#<:<` can't see the relationship. The output of `LightTypeTag#debug` is also the most useful thing to attach to a bug report.

## Other Settings

The remaining settings change how the library works internally. You rarely need them; they exist to work around suspected bugs.

### Assertions

`-Dizumi.reflect.debug.macro.rtti.assertions=true` enables extra consistency checks in the Scala 2 code that creates tags. Like the debug log, set it in the sbt JVM to check tag creation during compilation.

### Optimized Equality

Tags created at compile time are stored in a serialized form. By default, two such tags with the same format version are compared by their serialized strings, and different strings mean different types. If you suspect two tags of the same type compare as not equal, set `-Dizumi.reflect.rtti.optimized.equals=false` in the JVM of your program to compare their full structure instead.

### Caches

The Scala 2 macros cache tags they compute during compilation. To turn the cache off, pass a macro setting to the compiler, not a system property:

```scala
scalacOptions += "-Xmacro-settings:izumi.reflect.rtti.cache.compile=false"
```

The system property `izumi.reflect.rtti.cache.runtime=false` turns off the cache used by `LightTypeTagImpl.makeLightTypeTag`, which builds tags from `scala-reflect` types at runtime on Scala 2. See [Scala 2 vs Scala 3](scala-2-vs-scala-3.md#interoperating-with-scala-reflect).

## Running the Examples

All code from this page is available as runnable examples in the `izumi-reflect-examples` project.

Prints the name of the debug property and the inheritance data of a tag:

```scala title="izumi-reflect/izumi-reflect-examples/src/main/scala-3/izumi/reflect/examples/debugging/InspectTagExample.scala" showLineNumbers
//> using scala 3.3.8
//> using dep dev.zio::izumi-reflect:3.0.11
// Run from this folder:  scala-cli run InspectTagExample.scala
// Or from the repository root:  sbt "izumi-reflect-examples/runMain izumi.reflect.examples.debugging.InspectTagExample"

package izumi.reflect.examples.debugging

import izumi.reflect.{DebugProperties, Tag}

/**
  * Debugging and DebugProperties ? Inspecting a Tag
  *
  * Prints the name of the debug property and the inheritance data of a tag.
  */
object InspectTagExample {
  def main(args: Array[String]): Unit = {
    println(s"debug property: ${DebugProperties.`izumi.reflect.debug.macro.rtti`}")
    println(Tag[Some[Int]].tag.debug("someInt"))
  }
}
```

([source](https://github.com/zio/izumi-reflect/tree/develop/izumi-reflect/izumi-reflect-examples/src/main/scala-3/izumi/reflect/examples/debugging/InspectTagExample.scala)) Run it with `scala-cli run InspectTagExample.scala` from that folder, or with `sbt "izumi-reflect-examples/runMain izumi.reflect.examples.debugging.InspectTagExample"` from the repository root.
