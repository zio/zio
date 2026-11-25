# Introduction to ZIO Constraintless

> ZIO Constraintless allows you to build programs as mere descriptions with maximum polymorphism, maximum modularity, zero abstraction leakage, and zero casting.

[![Development](https://img.shields.io/badge/Project%20Stage-Development-green.svg)](https://github.com/zio/zio/wiki/Project-Stages) ![CI Badge](https://github.com/zio/zio-constraintless/workflows/CI/badge.svg) [![Sonatype Releases](https://img.shields.io/maven-central/v/dev.zio/zio-constraintless_2.13.svg?label=Sonatype%20Release)](https://central.sonatype.com/artifact/dev.zio/zio-constraintless_2.13) [![Sonatype Snapshots](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fcentral.sonatype.com%2Frepository%2Fmaven-snapshots%2Fdev%2Fzio%2Fzio-constraintless_2.13%2Fmaven-metadata.xml&label=Sonatype%20Snapshot)](https://central.sonatype.com/repository/maven-snapshots/dev/zio/zio-constraintless_2.13/) [![javadoc](https://javadoc.io/badge2/dev.zio/zio-constraintless_2.13/javadoc.svg)](https://javadoc.io/doc/dev.zio/zio-constraintless_2.13) [![ZIO Constraintless](https://img.shields.io/github/stars/zio/zio-constraintless?style=social)](https://github.com/zio/zio-constraintless)

## Introduction

ZIO Constraintless is a Scala take on the paper [Deconstraining](https://web.archive.org/web/20260115140505/http://www.doc.ic.ac.uk/~wlj05/files/Deconstraining.pdf), which shows how to parametrise a program with logical constraints at every node without compromising modularity. An excerpt from the paper:

_"The key principle that underpins our idea is that implementation-specific constraints should be imposed at the point of use of a data type, not at the point of definition, i.e. it embodies the established principle that an interface should be separated from its implementation(s)."_

The library is small. It has four types: [`TypeList`](./reference.md#typelist) describes the types a program may contain, [`IsElementOf`](./reference.md#iselementof) and [`AreElementsOf`](./reference.md#areelementsof) prove that types belong to such a list, and [`Instances`](./reference.md#instances) turns a list of types into the type class instances an interpreter needs.

## Installation

ZIO Constraintless is published for Scala 2.12, 2.13 and 3 (the Scala 3 artifact needs Scala 3.8 or newer). Add the following to your `build.sbt` file for a JVM project:

```scala
libraryDependencies ++= Seq(
  // Type-level lists, membership evidence and Instances (JVM)
  "dev.zio" %% "zio-constraintless" % "0.3.10"
)
```

For a Scala.js or Scala Native project, use `%%%` instead:

```scala
libraryDependencies ++= Seq(
  // Type-level lists, membership evidence and Instances (Scala.js and Scala Native)
  "dev.zio" %%% "zio-constraintless" % "0.3.10"
)
```

## Example

Imagine an expression tree whose leaves can hold values of several types, and two interpreters over it: one that pretty-prints and one that converts every leaf to an `Int`. Each interpreter needs its own type class (`Show` and `ToInt`), and the tree must not know about either. In the following program the tree only records which types it may contain, and each interpreter asks for the instances it needs at the point of use:

```scala
import zio.constraintless._
import zio.constraintless.TypeList._

trait Show[A] { def show(a: A): String }
trait ToInt[A] { def toInt(a: A): Int }

sealed trait Expr[As <: TypeList, A]
case class Value[As <: TypeList, A](a: A, ev: A IsElementOf As) extends Expr[As, A]

def pretty[As <: TypeList, A](e: Expr[As, A])(implicit show: Instances[Show, As]): String =
  e match {
    case Value(a, ev) => show.withInstance((s: Show[A]) => s.show(a))(ev)
  }

def toInt[As <: TypeList, A](e: Expr[As, A])(implicit ints: Instances[ToInt, As]): Int =
  e match {
    case Value(a, ev) => ints.withInstance((i: ToInt[A]) => i.toInt(a))(ev)
  }
```

Both `pretty` and `toInt` accept the same `Expr` value, because the only thing the tree promises is that every leaf type is an element of `As`. To build and run this example step by step, see [Getting Started](./getting-started.md); to see why the Hughes-style encoding cannot do this, see [Why Not the Hughes Schema?](./explanation/why-not-the-hughes-schema.md).
