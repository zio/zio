# Documentation Coverage Report

> Comprehensive analysis of documentation gaps in ZIO Blocks, combining automated scanning with manual source-code review.

# Documentation Coverage Report

Comprehensive analysis of documentation gaps in ZIO Blocks, combining automated scanning with manual source-code review.

## Summary

| Metric | Count |
|--------|-------|
| Total public types found | 552 |
| Types with documentation | 302 |
| Types lacking documentation | 250 |
| Documentation coverage | 54% |
| Existing reference pages | 24 |
| Missing methods in existing pages | ~44 |
| Missing examples in existing pages | ~39 |
| Conceptual docs (guides, how-tos) | 0 |

---

## Critical: Missing Reference Pages

These are core public API types that users interact with directly. Each needs a dedicated reference page or a substantial new section in an existing page.

- [ ] **`MediaType`** (module `mediatype`) — Public API for media type parsing and matching; essential for content negotiation. The entire `zio.blocks.mediatype` package has zero documentation. **Scope: new page**. Source: `mediatype/shared/src/main/scala/zio/blocks/mediatype/MediaType.scala`
- [ ] **`MediaTypes`** (module `mediatype`) — Predefined media type instances (application/json, text/html, etc.). Should be part of the `MediaType` page. **Scope: new section**. Source: `mediatype/shared/src/main/scala/zio/blocks/mediatype/MediaTypes.scala`
- [ ] **`SchemaExpr`** (module `schema`) — Core trait for expression evaluation on schema-described types; central to the validation DSL. **Scope: new page**. Source: `schema/shared/src/main/scala/zio/blocks/schema/SchemaExpr.scala`
- [ ] **`SchemaError`** (module `schema`) — Primary error type returned from schema operations (ConversionFailed, MissingField, DuplicatedField, ExpectationMismatch, UnknownCase). Users handle these constantly. **Scope: new page or major section in schema.md**. Source: `schema/shared/src/main/scala/zio/blocks/schema/SchemaError.scala`
- [ ] **`Into`** (module `schema`) — Core conversion type class appearing in all cross-type transformations. **Scope: new page**. Source: `schema/shared/src/main/scala/zio/blocks/schema/Into.scala`
- [ ] **`JsonPatch`** (module `schema`) — Main public API for JSON patching and diffing operations. **Scope: new section in json.md or patch.md**. Source: `schema/shared/src/main/scala/zio/blocks/schema/json/JsonPatch.scala`

---

## High Priority: Incomplete Coverage

### Types needing at least a dedicated section in an existing page

**schema module — Binding subsystem:**
- [ ] **`BindingResolver`** — Essential for type binding resolution; critical for schema rebinding. Referenced in 6 source files. **Scope: new section in binding.md**. Source: `schema/shared/src/main/scala/zio/blocks/schema/binding/BindingResolver.scala`
- [ ] **`MapConstructor` / `MapDeconstructor`** — Public traits for customizing map handling in bindings. Referenced in 7 files each. **Scope: new section in binding.md**. Source: `schema/shared/src/main/scala/zio/blocks/schema/binding/MapConstructor.scala`
- [ ] **`ConstantConstructor` / `ConstantDeconstructor`** — Binding helpers for constant values. Referenced in 11 files each. **Scope: brief section in binding.md**. Source: `schema/shared/src/main/scala/zio/blocks/schema/binding/Constructor.scala`
- [ ] **`RegisterType`** — Type-safe register representation. Referenced in 5 files. **Scope: brief section in registers.md**. Source: `schema/shared/src/main/scala/zio/blocks/schema/binding/RegisterType.scala`

**schema module — Derivation subsystem:**
- [ ] **`InstanceOverride`** — Used for customizing schema derivation. Referenced in 8 files. **Scope: new section in type-class-derivation.md**. Source: `schema/shared/src/main/scala/zio/blocks/schema/derive/InstanceOverride.scala`
- [ ] **`ModifierOverride`** — Used for overriding modifiers during derivation. Referenced in 3 files. **Scope: new section in type-class-derivation.md**. Source: `schema/shared/src/main/scala/zio/blocks/schema/derive/ModifierOverride.scala`

**schema module — Type classes:**
- [ ] **`IsNumeric`** — Type class for arithmetic operations. Referenced in 3 files. **Scope: brief section in schema.md**. Source: `schema/shared/src/main/scala/zio/blocks/schema/IsNumeric.scala`
- [ ] **`IsCollection`** — Type class for collection operations. **Scope: brief section in schema.md**. Source: `schema/shared/src/main/scala/zio/blocks/schema/IsCollection.scala`
- [ ] **`IsMap`** — Type class for map operations. **Scope: brief section in schema.md**. Source: `schema/shared/src/main/scala/zio/blocks/schema/IsMap.scala`
- [ ] **`Reflectable`** — Trait for types with reflectable modifiers. Referenced in 3 files. **Scope: brief section in reflect.md**. Source: `schema/shared/src/main/scala/zio/blocks/schema/Reflectable.scala`
- [ ] **`ToStructural`** — Converts nominal to structural schemas. **Scope: brief section in reflect.md**. Source: `schema/shared/src/main/scala/zio/blocks/schema/ToStructural.scala`

**schema module — JSON subsystem:**
- [ ] **`Keyable`** — Type class for JSON key support. Referenced in 5 files. **Scope: new section in json.md**. Source: `schema/shared/src/main/scala/zio/blocks/schema/json/Keyable.scala`
- [ ] **`NameMapper`** (`CamelCase`, `KebabCase`, `PascalCase`) — Public field name transformations. **Scope: new section in json.md**. Source: `schema/shared/src/main/scala/zio/blocks/schema/json/NameMapper.scala`
- [ ] **`JsonCodecError`** — Custom exception for JSON codec errors. Referenced in 7 files. **Scope: brief section in json.md**. Source: `schema/shared/src/main/scala/zio/blocks/schema/json/JsonCodecError.scala`

**typeid module:**
- [ ] **`TypeRepr`** — Central type representation (24 subtypes: ThisType, TypeLambda, Singleton, Repeated, etc.). Referenced across 4-8 files each. **Scope: new section in typeid.md**. Source: `typeid/shared/src/main/scala/zio/blocks/typeid/TypeRepr.scala`
- [ ] **`TypeDefKind`** — Type definition classifier (AbstractType, EnumCase, TypeAlias, etc.). **Scope: new section in typeid.md**. Source: `typeid/shared/src/main/scala/zio/blocks/typeid/TypeDefKind.scala`
- [ ] **`Kind`** and **`Arrow`** — Higher-kinded type expressions. **Scope: new section in typeid.md**. Source: `typeid/shared/src/main/scala/zio/blocks/typeid/Kind.scala`
- [ ] **`Annotation`** — Type annotation metadata (ArrayArg, ClassOf, EnumValue). **Scope: new section in typeid.md**. Source: `typeid/shared/src/main/scala/zio/blocks/typeid/Annotation.scala`
- [ ] **`Owner`** and **`Segment`** — Type ownership path. **Scope: new section in typeid.md**. Source: `typeid/shared/src/main/scala/zio/blocks/typeid/Owner.scala`

**context module:**
- [ ] **`IsNominalType`** — Type class for nominal type extraction. Referenced in 6 files. **Scope: new section in context.md**. Source: `context/shared/src/main/scala/zio/blocks/context/IsNominalType.scala`
- [ ] **`IsNominalIntersection`** — Type class for intersection type handling. Referenced in 4 files. **Scope: new section in context.md**. Source: `context/shared/src/main/scala/zio/blocks/context/IsNominalIntersection.scala`

**Format codec modules:**
- [ ] **`BsonEncoder` / `BsonDecoder` / `BsonCodec`** (schema-bson) — Core encoding/decoding types for BSON. **Scope: expand formats.md BSON section**. Source: `schema-bson/src/main/scala/zio/blocks/schema/bson/BsonTypes.scala`
- [ ] **`MessagePackCodec`** (schema-messagepack) — Public codec for MessagePack. **Scope: expand formats.md MessagePack section**. Source: `schema-messagepack/src/main/scala/zio/blocks/schema/msgpack/MessagePackCodec.scala`
- [ ] **`ThriftCodec`** (schema-thrift) — Public codec for Thrift. **Scope: expand formats.md Thrift section**. Source: `schema-thrift/src/main/scala/zio/blocks/schema/thrift/ThriftCodec.scala`
- [ ] **`ToonReader` / `ToonWriter`** (schema-toon) — Public codec for TOON. Referenced in 9-10 files. **Scope: expand formats.md TOON section**. Source: `schema-toon/src/main/scala/zio/blocks/schema/toon/`

**markdown module:**
- [ ] **`Block`** subtypes (`BlockQuote`, `BulletList`, `CodeBlock`, `HtmlBlock`, `OrderedList`, `ThematicBreak`) — Core markdown AST elements. **Scope: new section in docs.md**. Source: `markdown/shared/src/main/scala/zio/blocks/docs/Block.scala`
- [ ] **`Inline`** subtypes (`Autolink`, `HtmlInline`, `Image`, `HardBreak`, `SoftBreak`) — Core markdown AST elements. **Scope: new section in docs.md**. Source: `markdown/shared/src/main/scala/zio/blocks/docs/Inline.scala`

---

## Medium Priority: Brief Mentions Needed

Types that should be mentioned in related pages but don't need dedicated sections.

**schema module:**
- [ ] `SchemaMetadata` / `Folder` — metadata traversal infrastructure. **Mention in: schema.md**
- [ ] `FromBinding` — type class for binding conversion. **Mention in: binding.md**
- [ ] `UnapplySeq` / `UnapplyMap` — implicit evidence for seq/map operations. **Mention in: binding.md**
- [ ] `OpticCheck` subtypes (`EmptyMap`, `MissingKey`, `SequenceIndexOutOfBounds`, `WrappingError`) — validation results from optic operations. **Mention in: optics.md**
- [ ] `RebindException` — thrown during schema rebinding. **Mention in: binding.md**
- [ ] `JsonDiffer` — JSON diffing utility. **Mention in: json.md**
- [ ] `JsonSchemaType` / `SchemaType` — JSON Schema type system. **Mention in: json-schema.md**
- [ ] `ContextDetector` parsing states — JSON interpolator internals. **Mention in: json.md**
- [ ] `TypeIdSchemas` — hand-rolled schema instances for TypeId. **Mention in: typeid.md**

**chunk module:**
- [ ] `ChunkIterator` — streaming iterator for chunks. **Mention in: chunk.md**
- [ ] `IsText` — type class for chunk-to-string conversion. **Mention in: chunk.md**

**scope module:**
- [ ] `InStack` — trait for stack-like containment. **Mention in: scope.md**

**markdown module:**
- [ ] `Alignment` / `Center` — table column alignment. **Mention in: docs.md**
- [ ] `HeadingLevel` (`H4`, `H5`) — heading levels. **Mention in: docs.md**
- [ ] `TerminalRenderer` — ANSI terminal rendering. **Mention in: docs.md**
- [ ] `MdInterpolatorRuntime` — runtime support for `md"..."`. **Mention in: docs.md**

**schema-toon module:**
- [ ] `Delimiter` / `Comma` / `Pipe` / `Tab` — array delimiters. **Mention in: formats.md**
- [ ] `ArrayFormat` — array encoding strategy. **Mention in: formats.md**
- [ ] `KeyFolding` / `PathExpansion` — TOON reader/writer config. **Mention in: formats.md**

**schema-bson module:**
- [ ] `DiscriminatorField` / `NoDiscriminator` / `WrapperWithClassNameField` — sum type encoding strategies. **Mention in: formats.md**

---

## Documentation Depth Issues

Existing pages that need updates — missing methods, examples, or cross-references.

### Schema (`schema.md`)
- [ ] Missing examples of schema serialization to JSON Schema and back
- [ ] Missing example of caching behavior with `derive(format)`
- [ ] Missing example of using modifiers on schemas
- [ ] No mention of `TypeId` integration in schema derivation
- [ ] Missing reference to schema validation with `DynamicSchema`

### Reflect (`reflect.md`)
- [ ] `Reflect#noBinding` — critical for understanding serialization, not documented
- [ ] `Reflect#transform` — used extensively but not documented
- [ ] `Reflect.Extractors` — pattern matching helpers not documented
- [ ] Missing example of working with `Reflect.Unbound` for serialization
- [ ] Missing example of recursive type handling with `Deferred`
- [ ] Should reference `Binding` more clearly for each reflect type

### Binding (`binding.md`)
- [ ] Missing documentation on `Register` and `RegisterOffset` API — critical for zero-allocation architecture
- [ ] Missing `SpecializedIndexed` documentation for array-based performance
- [ ] No detailed example of `RegisterOffset` calculation
- [ ] No example of implementing custom `SeqConstructor` for new collection types
- [ ] No example of `MapConstructor` / `MapDeconstructor` implementation
- [ ] Missing explanation of why specialized constructors exist

### Chunk (`chunk.md`)
- [ ] `Chunk.BitChunk` and bit operations barely documented
- [ ] No example of the `BitChunk` operations with endianness
- [ ] Missing performance comparison with `Vector` for various operations
- [ ] `Chunk.materialize` automatic triggering conditions are vague
- [ ] Missing `NonEmptyChunk#flatMap` documentation

### JSON (`json.md`)
- [ ] Missing `Keyable` typeclass documentation
- [ ] `Json#diff` operation types not documented
- [ ] No example of complex querying with predicates
- [ ] No example of recursive transformation with `transformDown` vs `transformUp`
- [ ] No example of merging strategies in detail
- [ ] Should reference `JsonSchema` more thoroughly

### Codec (`codec.md`)
- [ ] Missing explanation of `Format` trait interface
- [ ] Missing documentation of `Deriver` pattern for custom codecs
- [ ] No example of implementing a custom `Format`
- [ ] No example of `DerivationBuilder` advanced usage
- [ ] Codec instance caching mechanism not explained
- [ ] JSON codec configuration incomplete — missing validation options

### Patch (`patch.md`)
- [ ] `DynamicPatch` operations not fully documented
- [ ] No example of `modifyKey` on maps with type parameters
- [ ] No example of composing patches across different types
- [ ] Missing example of serialization and storage of patches
- [ ] Should explain relationship to `DynamicValue.diff`

### TypeId (`typeid.md`)
- [ ] `TypeRepr` subtypes barely documented (24 variants)
- [ ] `TypeDefKind` variants incomplete
- [ ] `Member` API — parameter modifiers underdocumented
- [ ] `TermPath` and singleton types barely covered
- [ ] No mention of Scala 2 vs Scala 3 differences in TypeId derivation

### Context (`context.md`)
- [ ] `IsNominalType` typeclass not explained
- [ ] No mention of performance characteristics
- [ ] Missing explanation of why only nominal types are supported
- [ ] Missing example of context composition in larger applications

### Validation (`validation.md`)
- [ ] Composition/chaining methods not documented
- [ ] No example of using validations directly on `PrimitiveType`
- [ ] No example of validation in format derivers
- [ ] Missing guidance on validation composition workarounds
- [ ] Missing integration examples with JSON Schema

---

## Conceptual Gaps

Missing guides, overviews, and tutorials — none of these exist today.

- [ ] **Getting Started Guide** — No quick-start for new users. Should cover: adding dependencies, defining a case class, deriving a schema, encoding/decoding JSON. **Scope: new page `docs/getting-started.md`**
- [ ] **Architecture Overview** — No high-level design document. Should cover: module dependency graph, register-based zero-allocation architecture, the Reflect/Binding/Schema layering, and the Deriver pattern. **Scope: new page `docs/architecture.md`**
- [ ] **How-To: Custom Codec** — No guide for implementing a custom `Format` and its `Deriver`. **Scope: new page `docs/how-to-custom-codec.md`**
- [ ] **How-To: Schema Derivation** — No guide walking through `Schema.derived` vs manual schema construction. **Scope: new section in schema.md or new page**
- [ ] **How-To: Working with DynamicValue** — No guide on converting between typed and dynamic representations. **Scope: new section in dynamic-value.md**
- [ ] **End-to-End Pipeline Example** — No documentation showing the full Schema -> Codec -> Encoding -> Decoding -> Validation pipeline. **Scope: new page or section in index.md**
- [ ] **Migration Guide** — No version migration docs (may not be needed yet if pre-1.0, but placeholder is useful)
- [ ] **Performance Guide** — No guidance on when to use `materialize` on Chunks, binding register allocation strategies, caching behavior in schema derivation, or JSON encoder/decoder performance characteristics

---

## Low Priority / Skip

Internal types that don't need documentation.

| Type | Module | Reason |
|------|--------|--------|
| `LittleEndian` | chunk | Endianness marker for bit operations; specialized internal |
| `ChunkMapBuilder` | chunk | Internal builder behind `ChunkMap.newBuilder` |
| `PlatformSpecific` | schema | Platform-specific trait for JVM/JS split |
| `Extractors` | schema | Internal pattern matching helpers on `Reflect` |
| `AsLowPriorityImplicits` | schema | Implicit resolution priority helper |
| `IntoPrimitiveInstances` / `IntoContainerInstances` | schema | Implicit instance providers (infrastructure) |
| `HasInstances` | schema | Derivation infrastructure |
| `UnapplySeqLowPriority` | schema | Implicit priority helper |
| `Leaf` (in Doc) | schema | Internal documentation node type |
| `Folder` | schema | Internal metadata traversal helper |
| All `*Delta` / `*Dummy` types in `DynamicPatch` | schema | Internal patch operation representations (BigDecimalDelta, ByteDelta, DoubleDelta, DurationDelta, DurationDummy, FloatDelta, InstantDelta, IntDelta, LocalDateDelta, LocalDateTimeDelta, LongDelta, PeriodDelta, PeriodDummy, ShortDelta) |
| All `PathParser` error types | schema | Internal parser errors (EmptyChar, InvalidEscape, InvalidIdentifier, InvalidSyntax, IntegerOverflow, MultiCharLiteral, UnexpectedChar, UnexpectedEnd, UnterminatedChar, UnterminatedString) |
| `ContextDetector` / parsing states | schema | Internal JSON interpolator state machine (AfterValue, ExpectingColon, ExpectingKey, ExpectingValue, InString, TopLevel) |
| `JsonSchemaToReflect` helpers | schema | Internal conversion types (FieldVariant, KeyVariant, MapShape, OptionOf, PrimKind) |
| `CaseInfo` / `EnumInfo` | schema | Internal JSON codec deriver helpers |
| `DynamicValueMergeStrategy` / `KeepLeft` | schema | Internal merge strategy implementation |
| `DynamicValueSelection` | schema | Internal selection helper |
| `NoBinding` | schema | Internal marker type |
| `ReflectPrinter` | schema | Internal debug printing |
| `Registry` / `Registry.Entry` | schema | Internal binding resolver storage |
| `ObjectIdSupport` | schema-bson | Internal BSON ObjectId helper |
| `BsonBuilder` / `BsonTrace` / `EncoderContext` / `BsonDecoderContext` | schema-bson | Internal codec implementation |
| `MessagePackCodecDeriver` | schema-messagepack | Internal deriver |
| `MessagePackReader` / `MessagePackWriter` | schema-messagepack | Internal binary readers/writers |
| `Mixed` / `UniformRecords` | schema-toon | Internal codec strategy types |
| `ArrayHeader` | schema-toon | Internal reader state |
| `Off` | schema-toon | Internal config value |
| All `scope/internal/*` types | scope | Internal error rendering (Colors, DepNode, DepStatus, ErrorMessages, Found, Missing, Pending, ProviderInfo) |
| `Destroyed` / `Uninitialized` | scope | Internal resource lifecycle states |
| `FlatMap` / `ZSink` / `ZSinkFiber` / `ZSource` / `ZSourceFiber` | streams | Experimental/WIP module |
| `TypeIdInstances` | typeid | Internal implicit provider trait |
| `TypeIdPrinter` | typeid | Internal rendering utility |
| `Owners` | typeid | Internal namespace helper |
| All `*Const` types in `TypeRepr` | typeid | Internal literal type representations (BooleanConst, CharConst, ClassOfConst, DoubleConst, FloatConst, IntConst, LongConst, NullConst, StringConst, UnitConst) |
| `AnyKindType` / `AnyType` / `NothingType` / `NullType` / `UnitType` | typeid | Internal special type representations |

---

## Suggested Actions

Ordered TODO checklist grouped by module, with estimated scope.

### Conceptual Documentation (highest impact)

1. - [ ] Write **Getting Started Guide** — `docs/getting-started.md` — *new page*
2. - [ ] Write **Architecture Overview** — `docs/architecture.md` — *new page*
3. - [ ] Write **End-to-End Pipeline Example** — Schema -> Codec -> Encode -> Decode -> Validate — *new page or section*

### Module: `mediatype` (entirely undocumented)

4. - [ ] Write **MediaType reference page** — `docs/reference/media-type.md` — *new page*

### Module: `schema` (core gaps)

5. - [ ] Write **SchemaError reference** — error types, handling patterns — *new page or new section in schema.md*
6. - [ ] Write **SchemaExpr reference** — expression DSL, validation expressions — *new page*
7. - [ ] Write **Into reference** — cross-type conversions — *new page*
8. - [ ] Add **BindingResolver, MapConstructor, MapDeconstructor** sections to `binding.md` — *update existing*
9. - [ ] Add **InstanceOverride, ModifierOverride** sections to `type-class-derivation.md` — *update existing*
10. - [ ] Add **IsNumeric, IsCollection, IsMap** section to `schema.md` — *update existing*
11. - [ ] Add **Keyable, NameMapper** section to `json.md` — *update existing*
12. - [ ] Add **JsonPatch operations** section to `json.md` or `patch.md` — *update existing*
13. - [ ] Add **Reflectable, ToStructural** sections to `reflect.md` — *update existing*
14. - [ ] Expand **Register/RegisterOffset** explanation in `binding.md` and `registers.md` — *update existing*

### Module: `typeid` (many subtypes undocumented)

15. - [ ] Add **TypeRepr** section with key subtypes to `typeid.md` — *update existing*
16. - [ ] Add **TypeDefKind** section to `typeid.md` — *update existing*
17. - [ ] Add **Kind/Arrow** section to `typeid.md` — *update existing*
18. - [ ] Add **Annotation** subtypes section to `typeid.md` — *update existing*
19. - [ ] Add **Owner/Segment** section to `typeid.md` — *update existing*

### Module: `context`

20. - [ ] Add **IsNominalType, IsNominalIntersection** section to `context.md` — *update existing*

### Module: `markdown`

21. - [ ] Add **Block subtypes** reference section to `docs.md` — *update existing*
22. - [ ] Add **Inline subtypes** reference section to `docs.md` — *update existing*
23. - [ ] Add **Alignment, HeadingLevel, TerminalRenderer** mentions to `docs.md` — *update existing*

### Module: `chunk`

24. - [ ] Add **BitChunk operations** section to `chunk.md` — *update existing*
25. - [ ] Add **ChunkIterator** mention to `chunk.md` — *update existing*

### Format modules (expand `formats.md`)

26. - [ ] Expand **BSON section** in `formats.md` with BsonEncoder/BsonDecoder API — *update existing*
27. - [ ] Expand **MessagePack section** in `formats.md` with MessagePackCodec API — *update existing*
28. - [ ] Expand **Thrift section** in `formats.md` with ThriftCodec API — *update existing*
29. - [ ] Expand **TOON section** in `formats.md` with ToonReader/ToonWriter, Delimiter, config — *update existing*

### Depth improvements (existing pages)

30. - [ ] Add missing **Reflect#transform** and **Reflect#noBinding** to `reflect.md`
31. - [ ] Add missing **Format trait interface** and **custom Format** example to `codec.md`
32. - [ ] Add missing **DynamicPatch** operations and **patch serialization** example to `patch.md`
33. - [ ] Add missing **Validation composition** guidance and **JSON Schema integration** to `validation.md`
34. - [ ] Add missing **TypeRepr pattern matching** and **Scala 2/3 differences** to `typeid.md`
35. - [ ] Add missing **Cache behavior** and **intersection type** examples to `context.md`

---

*Report generated on 2026-02-13. Scan performed by `scan-undocumented.sh`; manual enrichment by AI review of 250 undocumented types across 12 modules and 10 existing reference pages.*
