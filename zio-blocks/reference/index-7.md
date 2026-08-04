# ZIO Blocks Schema

> ZIO Blocks Schema is the core type system and serialization framework that provides reified structural metadata for Scala data types. It enables type-safe schema definition, validation, optics-based data access, multi-format serialization, and runtime introspection — all derived from a single `Schema` definition.

## Introduction

ZIO Blocks Schema is the core type system and serialization framework that provides reified structural metadata for Scala data types. It enables type-safe schema definition, validation, optics-based data access, multi-format serialization, and runtime introspection — all derived from a single `Schema` definition.

**Core Type System:**
- [`Schema`](./schema.md) — Primary data type containing reified structure of a Scala data type
- [`Reflect`](./reflect.md) — Foundational data structure containing reified structural information
- [`Binding`](./binding.md) — Operational machinery for constructing and deconstructing values
- [`Registers`](./registers.md) — Register-based design for zero-allocation construction and deconstruction
- [`Structural Types`](./structural-types.md) — Duck typing with ZIO Blocks schemas

**Dynamic & Runtime Data:**
- [`DynamicValue`](./dynamic-value.md) — Schema-less, dynamically-typed representation of any structured value
- [`DynamicSchema`](./dynamic-schema.md) — Type-erased schema container for serialization and transport
- [`Lazy`](./lazy.md) — Deferred computation with monadic abstraction, memoization, and stack-safe evaluation

**Navigation & Transformation:**
- [`Optics`](./optics.md) — Reflective optics for type-safe, composable access to nested data structures
- [`DynamicOptic`](./dynamic-optic.md) — Runtime path through nested data structures
- [`Path Interpolator`](./path-interpolator.md) — Compile-time string interpolator `p"..."` for constructing `DynamicOptic` instances
- [`SchemaExpr`](./schema-expr.md) — Schema-aware expressions for evaluation and query language translation
- [`Patch`](./patch.md) — Type-safe, serializable transformations of data structures
- [`Modifier`](./modifier.md) — Mechanism to attach metadata and configuration to schema elements

**Serialization:**
- [`Codec`](./codec.md) — Base abstraction for encoding and decoding values between formats
- [`Format`](./format.md) — Unified abstraction bundling serialization and deserialization for a specific format
- [`Type Class Derivation`](./type-class-derivation.md) — Automatic generation of type class instances from schemas
- [`Syntax`](./syntax.md) — Extension methods for fluent JSON encoding/decoding and patching

**Formats:**
- [`JSON Codec`](./built-in-codecs/json/) — Complete JSON support: ADT for JSON values, fluent navigation, encoding/decoding, diffs, patches, and JSON Schema 2020-12 validation
- [`Xml`](./built-in-codecs/xml.md) — Type-safe, immutable representation of XML document structures

**Validation & Errors:**
- [`Validation`](./validation.md) — Declarative constraints on primitive values
- [`SchemaError`](./schema-error.md) — Structured error type for schema operations
- [`Allows`](./allows.md) — Compile-time capability token proving a type satisfies a structural grammar

## Overview

The ZIO Blocks Schema module revolves around the `Schema` and `Reflect` types, which capture the complete structural description of Scala data types at runtime. From a single schema definition, you get automatic derivation of codecs (`Codec`, `Formats`), type-safe data access (`Optics`, `DynamicOptic`), structural validation (`Validation`, `SchemaError`), patching (`Patch`, `JsonPatch`), and serialization to multiple formats (`Json`, `Xml`, `JSON Schema`).

The type system is powered by a register-based architecture (`Registers`) that eliminates boxing overhead, and supports both compile-time (`Allows`) and runtime (`DynamicValue`, `DynamicSchema`) type manipulation. The `Deriver` system (`Type Class Derivation`) enables automatic generation of any type class instance from schema metadata.
