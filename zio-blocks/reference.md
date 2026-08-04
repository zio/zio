# Code Generation

> `zio-blocks-codegen` is a **generic, domain-agnostic Scala code generation library**. It provides an intermediate representation (IR) for building type-safe models of Scala code structures, and a pure emitter that generates well-formatted Scala source files from those models.

`zio-blocks-codegen` is a **generic, domain-agnostic Scala code generation library**. It provides an intermediate representation (IR) for building type-safe models of Scala code structures, and a pure emitter that generates well-formatted Scala source files from those models.

Core types: `ScalaFile`, `TypeDefinition`, `CaseClass`, `SealedTrait`, `Enum`, `Field`, `TypeRef`, `Method`, `Annotation`.

Here's the structure of two core types:

```scala
// IR models the structure of Scala code
case class ScalaFile(
  packageDecl: PackageDecl,
  imports: List[Import] = Nil,
  types: List[TypeDefinition] = Nil
)

case class CaseClass(
  name: String,
  fields: List[Field],
  typeParams: List[TypeParam] = Nil,
  derives: List[String] = Nil,
  // ...
)
```

## Introduction

This module is designed to be a **reusable building block** for any tool that needs to generate Scala code—whether from OpenAPI specifications, Smithy models, Protocol Buffers, JSON Schema, or any other source format.

Rather than embedding code generation logic into domain-specific tools, you model your source data in the codegen IR, then emit clean, formatted Scala. This separation enables single source of truth, consistency, and reuse across all generators without cross-coupling.

## Motivation

Codegen IR exists to solve a specific problem: **many generators produce the same output (Scala code) but reinvent the emission logic.** Before `zio-blocks-codegen`, each generator (OpenAPI, Smithy, Protobuf, etc.) had its own IR and emitter, leading to duplication, bugs, and inconsistency. By extracting IR and emitter into `zio-blocks-codegen`, all generators share one implementation.

Before `zio-blocks-codegen`, each generator had its own IR and emitter:

```
OpenAPI → Scala        Smithy → Scala         Protobuf → Scala
    ↓                      ↓                        ↓
[Custom IR]            [Custom IR]             [Custom IR]
    ↓                      ↓                        ↓
[Custom Emitter]       [Custom Emitter]        [Custom Emitter]
    ↓                      ↓                        ↓
Scala Code             Scala Code              Scala Code

❌ Duplication: Same problem solved 3 times with different code
❌ Bugs: Fixes in one place don't help others
❌ Inconsistency: Different styles, formatting, edge cases
```

Now with `zio-blocks-codegen`, all generators converge on a single implementation:

```
OpenAPI → Scala    Smithy → Scala    Protobuf → Scala    JSON Schema → Scala
    ↓                  ↓                   ↓                    ↓
    └──────────────────┬───────────────────┘────────────────────┘
                       ↓
              [zio-blocks-codegen]
                       ↓
            ┌──────────┴──────────┐
            ↓                     ↓
      [IR: Type-safe]     [Emitter: Pure function]
      representation      (no side effects)
      of Scala code               |
            ↓                     ↓
            └──────────┬──────────┘
                       ↓
                  Scala Code

✅ Single source of truth: One well-tested implementation
✅ Consistency: All generators use the same emitter
✅ Reusability: Zero coupling between domain-specific tools
```

## Installation

Add the library to your project:

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-codegen" % "0.0.51"
```

Supported Scala versions: 2.13.x and 3.x

## Overview

The module has two key layers:
1. **IR Layer**: Immutable, strongly-typed models of Scala code structures (files, types, members)
2. **Emit Layer**: Pure functions that convert IR models to formatted Scala source code

### IR Layer (`zio.blocks.codegen.ir`)

The intermediate representation captures the essential structure of Scala code:

- **File structure**: `ScalaFile`, `PackageDecl`, `Import`
- **Type definitions**: `CaseClass`, `SealedTrait`, `Trait`, `AbstractClass`, `Enum`, `ObjectDef`, `OpaqueType`, `Newtype`, `TypeAlias`
- **Members**: `Field`, `Method`, `MethodParam`, `Annotation`, `TypeParam`, `TypeRef`
- **Composition**: Sealed traits like `TypeDefinition`, `SealedTraitCase`, `EnumCase`, `ObjectMember` for modular type safety

Each type is **immutable and strongly typed**. You build the IR by composing these types, then hand off to the emitter.

### Emit Layer (`zio.blocks.codegen.emit`)

The emitter converts IR to Scala source code:

- **`ScalaEmitter`**: Methods to emit any IR node as a formatted string (imports, type definitions, methods, etc.)
- **`EmitterConfig`**: Configurable formatting (indent width, import sorting, trailing commas, Scala 2 vs. Scala 3 syntax)
- **No side effects**: Returns strings; your code writes files (or does anything else)

## How They Work Together

Here's the typical workflow for a code generator:

```
1. Parse source format (OpenAPI, Smithy, etc.)
   ↓
2. Build IR models (ScalaFile, CaseClass, SealedTrait, etc.)
   ↓
3. Call ScalaEmitter.emit(file, config)
   ↓
4. ScalaEmitter returns formatted Scala source as String
   ↓
5. Write string to file (or further process it)
```

Here's an example of building a Scala file with a case class:

```
ScalaFile
├─ packageDecl: PackageDecl("com.example")
├─ imports: [Import.WildcardImport("zio")]
└─ types: [
     CaseClass(
       name = "User",
       fields = [
         Field("id", TypeRef.Long),
         Field("name", TypeRef.String),
         Field("email", TypeRef("Option", List(TypeRef.String)))
       ]
     )
   ]
```

When you call `ScalaEmitter.emit(file, config)`, it walks this tree and produces:

```scala
package com.example

import zio._

case class User(
  id: Long,
  name: String,
  email: Option[String],
)
```

The architecture flows through three layers:

```
┌──────────────────────────────────────────────────────────┐
│ Your Generator (OpenAPI, Smithy, Protobuf, JSON Schema)  │
│ - Parse input format                                     │
│ - Build IR models (CaseClass, SealedTrait, etc.)         │
└────────────────┬─────────────────────────────────────────┘
                 │ ScalaFile (root IR node)
                 ▼
┌──────────────────────────────────────────────────────────┐
│ zio-blocks-codegen IR Layer                              │
│ - TypeDefinition (sealed trait)                          │
│ - CaseClass, SealedTrait, Enum, ObjectDef, ...           │
│ - Field, Method, TypeRef, Annotation, TypeParam          │
│ - Strongly typed, immutable, composable                  │
└────────────────┬─────────────────────────────────────────┘
                 │ IR models
                 ▼
┌─────────────────────────────────────────────────────────┐
│ zio-blocks-codegen Emit Layer                           │
│ - ScalaEmitter.emit(file, config) → String              │
│ - Supports Scala 3 (enums, derives, * imports)          │
│ - Supports Scala 2 (sealed traits, _ imports, fallback) │
│ - EmitterConfig: indent, imports, commas, version       │
└────────────────┬────────────────────────────────────────┘
                 │ Formatted Scala source
                 ▼
         (Write to file or further process)
```

## Common Patterns

Here are the most common usage patterns:

### Pattern 1: Building a Simple Case Class

Build a case class with fields and derive clauses:

```scala
import zio.blocks.codegen.ir._

val user = CaseClass(
  name = "User",
  fields = List(
    Field("id", TypeRef.Long),
    Field("name", TypeRef.String)
  ),
  derives = List("Schema")
)
```

### Pattern 2: Sealed Trait Hierarchies

Model ADTs (algebraic data types) as sealed traits with cases:

```scala
import zio.blocks.codegen.ir._

val payment = SealedTrait(
  name = "Payment",
  cases = List(
    SealedTraitCase.CaseClassCase(
      CaseClass(
        "CreditCard",
        List(
          Field("number", TypeRef.String),
          Field("cvv", TypeRef.String)
        )
      )
    ),
    SealedTraitCase.CaseObjectCase("Cash"),
    SealedTraitCase.CaseObjectCase("Check")
  )
)
```

### Pattern 3: Generic Types with Type Parameters

Define polymorphic types:

```scala
import zio.blocks.codegen.ir._

val container = CaseClass(
  name = "Container",
  fields = List(
    Field("value", TypeRef("A"))
  ),
  typeParams = List(TypeParam("A"))
)
```

### Pattern 4: Complete File Structure

Assemble a complete Scala file ready for emission:

```scala
import zio.blocks.codegen.ir._

val user = CaseClass(
  name = "User",
  fields = List(
    Field("id", TypeRef.Long),
    Field("name", TypeRef.String)
  ),
  derives = List("Schema")
)

val payment = SealedTrait(
  name = "Payment",
  cases = List(
    SealedTraitCase.CaseObjectCase("Cash"),
    SealedTraitCase.CaseObjectCase("Card")
  )
)

val file = ScalaFile(
  packageDecl = PackageDecl("com.example"),
  imports = List(
    Import.WildcardImport("zio"),
    Import.SingleImport("zio.blocks.schema", "Schema")
  ),
  types = List(user, payment)
)
```

## Cross-Scala Compatibility

The emitter handles both **Scala 3** and **Scala 2** natively:

- **Scala 3 features**: Enums, derives clauses, `*` imports, `as` renames, opaque types
- **Scala 2 fallback**: Sealed traits, `_` imports, `=>` renames, type aliases

You configure the output via `EmitterConfig`:

```scala
import zio.blocks.codegen.emit._

val config = EmitterConfig(
  scala3Syntax = true,  // true for Scala 3, false for Scala 2
  indentWidth = 2,
  sortImports = true,
  trailingCommas = true
)
```

## Design Philosophy

Three principles guide codegen IR:

1. **Generic**: No domain-specific logic (OpenAPI-specific stuff stays in `zio-blocks-openapi`)
2. **Pure**: No side effects—just IR models and string emission
3. **Self-contained**: Zero external dependencies, works everywhere

This makes it a safe, reliable foundation for any Scala code generator.
