# Documentation Coverage Report

> Full re-scan of documentation coverage across every library module aggregated by the `root` project. Replaces the 2026-02-13 report, which predated most of the current `docs/reference` tree and covered only 12 modules.

**This revision fixes the scanner, so every number below has moved.** Earlier revisions classified a declaration as private only when the modifier sat on its own line, which counted 863 privately-enclosed declarations as public API — about a third of everything declared. Two items were mis-scoped as a result. The scanner now walks enclosing scopes, and the table carries an `internal` column so a low ratio can be told apart from a real gap. See *Methodology*.

**Work completed since this report was written:** `datastar`, split from one 346-line page into five (index, signals, attributes, events, sse), taking it from 39% to 98% with no absent types. Its previous page had 22 code blocks and no mdoc modifiers, so none of it had ever compiled.

Earlier: `config` (Tier 1 item 2, seven pages), the `http-model` typed header surface (items 1 and 7), `otel` (item 3), the `http-model-schema` codec layer (Tier 2 item 15), and — landed independently while this revision was in progress — `htmx` response headers (item 4, #1619), the `schema` search and traversal cluster (item 5, #1621), and `ReflectTransformer` (item 8, #1623).

Also landed independently: `telemetry/common/any-value.md` (#1622), documenting the `AnyValue` attribute ADT — Tier 1 item 6. That PR quoted 95% for `telemetry` against this table's 68%, because its figures predate the privacy-aware scanner; the work is the same, the measurement changed.

Every figure in this revision, including those four, is restated under the privacy-aware scanner. The notes those PRs added quoted the old scanner, which is why their numbers differ from the table: `htmx` reads 77% here rather than 85%, `schema` 55% rather than 77%, and `telemetry` 68% rather than 95%. The work is the same; the measurement changed. `docs/reference/telemetry/common/any-value.md` (90 lines, mdoc-verified) covers `AttributeValue`/`AttributeType` and their eight variants each, correcting the original Tier 1 item 6, which named types (`BoolValue`, `IntValue`, `ArrayValue`, several `*KV` types) that don't exist in source; under the privacy-aware scanner it resolves 8 of the module's absent types and 12 of its unexplained ones.

**What changed since the previous (2026-02-13) report:** every published module now has a reference page, and every page is linked from `docs/sidebars.js`. There are no longer any modules with zero documentation, and four of the six "critical missing pages" from the old report now exist (`media-type.md`, `schema/schema-expr.md`, `schema/schema-error.md`, `built-in-codecs/json/json-patch.md`). The remaining gaps are (a) whole subsystems inside otherwise-documented modules, (b) pages far too short for the surface they cover, and (c) an almost complete absence of task-oriented guides.

## Summary

| Metric | Count |
|--------|-------|
| Library modules aggregated by `root` | 38 |
| Modules with no reference page | **0** |
| Reference pages | 169 |
| Guides | 9 |
| Declarations found (`class` / `trait` / `object` / `enum`) | 2,662 |
| — of which public | 1,811 |
| — of which private or nested in a private scope | 864 |
| Public types never named anywhere in `docs/` | **325** |
| Public types with no prose or heading reference | **637** |
| Name-mention coverage | **82%** |
| Explained-type coverage | **65%** |

Two coverage numbers are reported because they answer different questions.

- **Name-mention coverage** counts a type as covered if its name appears anywhere in `docs/`, including inside an example code block. Its complement — 325 types — is entirely absent from the documentation.
- **Explained-type coverage** is stricter: it requires the name in prose (inline code) or in a heading. Its complement — 637 types — additionally captures the 312 types that appear only as tokens inside examples and are never explained.

Only **public** types are counted. A type is public when neither it nor any enclosing declaration is `private` or `protected` — 864 declarations fail that test and are excluded, which is roughly a third of everything declared. Earlier revisions of this report counted many of them as API and mis-scoped work as a result; see *Methodology*.

This report file is excluded from the scan, so listing a type here does not make it count as documented. Counts are per module, so a name defined in two modules is counted twice.

---

## Module Coverage Table

`public` = public types, per the rule above. `internal` = declarations excluded as private or privately-enclosed. `absent` = public types never named anywhere in `docs/`. `unexpl` = public types with no prose or heading reference. `ratio` = documentation lines / source lines.

| Module | public | internal | absent | unexpl | cov | srcLOC | docLOC | ratio |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| mediatype | 16 | 2 | 2 | 11 | 31% | 12,676 | 460 | 0.04 |
| **html** | 110 | 13 | 40 | 68 | **38%** | 5,591 | 1,300 | 0.23 |
| maybe | 10 | 0 | 6 | 6 | 40% | 595 | 943 | 1.58 |
| context | 2 | 7 | 1 | 1 | 50% | 865 | 553 | 0.64 |
| **typeid** | 90 | 14 | 26 | 43 | **52%** | 6,493 | 2,124 | 0.33 |
| schema-xml | 38 | 2 | 9 | 18 | 53% | 3,334 | 1,034 | 0.31 |
| **schema** | 466 | 226 | 156 | 208 | **55%** | 85,027 | 24,259 | 0.29 |
| http-model | 191 | 3 | 5 | 84 | 56% | 4,712 | 2,520 | 0.53 |
| schema-bson | 20 | 0 | 2 | 8 | 60% | 1,935 | 494 | 0.26 |
| scope | 23 | 54 | 6 | 9 | 61% | 7,085 | 3,579 | 0.51 |
| codegen | 46 | 0 | 6 | 17 | 63% | 2,100 | 3,024 | 1.44 |
| async | 9 | 58 | 2 | 3 | 67% | 6,540 | 1,291 | 0.20 |
| combinators | 6 | 14 | 2 | 2 | 67% | 1,132 | 524 | 0.46 |
| endpoint | 60 | 20 | 18 | 20 | 67% | 2,805 | 1,757 | 0.63 |
| schema-yaml | 27 | 10 | 6 | 9 | 67% | 2,753 | 552 | 0.20 |
| streams | 30 | 152 | 9 | 10 | 67% | 18,434 | 5,725 | 0.31 |
| telemetry | 139 | 72 | 9 | 44 | 68% | 7,509 | 2,948 | 0.39 |
| config (+ `-yaml`/`-json`/`-hocon`) | 82 | 25 | 7 | 24 | 71% | 3,914 | 2,212 | 0.57 |
| chunk | 20 | 35 | 2 | 5 | 75% | 5,069 | 3,140 | 0.62 |
| htmx | 88 | 2 | 6 | 20 | 77% | 1,548 | 2,750 | 1.78 |
| smithy | 42 | 4 | 0 | 4 | 90% | 2,585 | 882 | 0.34 |
| datastar | 57 | 11 | 0 | 1 | **98%** | 1,881 | 1,196 | 0.64 |
| markdown | 46 | 3 | 3 | 10 | 78% | 2,596 | 1,539 | 0.59 |
| schema-toon | 25 | 14 | 1 | 4 | 84% | 4,690 | 1,050 | 0.22 |
| openapi | 46 | 1 | 1 | 6 | 87% | 2,431 | 1,301 | 0.54 |
| schema-csv | 9 | 1 | 0 | 1 | 89% | 1,247 | 564 | 0.45 |
| sql | 53 | 14 | 1 | 3 | 94% | 4,234 | 4,047 | 0.96 |
| http-model-schema | 16 | 10 | 0 | 0 | **100%** | 1,249 | 1,073 | 0.86 |
| mux | 9 | 40 | 0 | 0 | **100%** | 1,222 | 823 | 0.67 |
| otel | 12 | 8 | 0 | 0 | **100%** | 1,325 | 422 | 0.32 |
| ringbuffer | 8 | 42 | 0 | 0 | **100%** | 2,360 | 989 | 0.42 |
| schema-avro | 3 | 3 | 0 | 0 | **100%** | 1,922 | 450 | 0.23 |
| schema-messagepack | 5 | 2 | 0 | 0 | **100%** | 1,933 | 507 | 0.26 |
| schema-thrift | 3 | 2 | 0 | 0 | **100%** | 868 | 432 | 0.50 |
| sql-zio | 1 | 0 | 0 | 0 | **100%** | 218 | 112 | 0.51 |

Notes on reading this table:

- **The `internal` column is the one that prevents mis-scoping.** A module whose declarations are mostly internal will show a low ratio without having a real gap. `streams` declares 152 internal types against 30 public ones, and `async` 58 against 9 — their low ratios are arithmetic, not neglect.
- **`maybe` and `async` are not real gaps** despite ranking high. `maybe`'s seven absent types are `MaybeCompat`, `MaybeOps`, `MaybeSyntax`, `MaybeSyntaxCompat`, `MaybeValue`, `MaybeWithFilter`, and `WithFilter` — syntax and compatibility shims matching the patterns in *Deliberately Undocumented*. `async`'s two are CPS-transform internals that happen to be public.
- `mediatype`'s 0.04 ratio is an artifact: 12,332 of its 12,676 source lines are the generated `MediaTypes.scala` lookup table.
- The four `config*` modules share one page directory, so their row is a hand-aggregate; the script emits them as four separate rows.
- Eight modules are fully covered: `http-model-schema`, `mux`, `otel`, `ringbuffer`, `schema-avro`, `schema-messagepack`, `schema-thrift`, `sql-zio`.
- **`html` moved the wrong way.** #1536 replaced the untyped element factories with a typed content model, adding 12 public types that no page names yet. Its absent count went from 34 to 46 while its documentation stood still — the clearest case in this table of code outrunning docs.

---

## Critical Gaps

Counts in these sections are from the privacy-aware scanner and match the table above. Historical figures — what a module looked like before its pages were written — are stated as such and were measured under the older scanner, so they overstate the public surface.

Type names below are **unexplained**: no prose reference, no heading. Names marked ✗ are **absent** — they never appear in `docs/` at all, not even inside an example.

### 1. `config` — RESOLVED

Was the worst gap in the repository: one 158-line page covering `config`, `config-yaml`, `config-json`, and `config-hocon` (3,914 source lines combined), with 53 of 77 public types unexplained and 43 absent.

`docs/reference/config.md` is now `docs/reference/config/`, seven pages totalling 2,212 lines with every code block mdoc-verified:

| Page                | Covers                                                                                     |
| ------------------- | ------------------------------------------------------------------------------------------ |
| `index.md`          | Module narrative, installation, data flow, the four `Config` entry points, integration points |
| `config-source.md`  | `ConfigSource`, `MapSource`, `EnvSource`, `SysPropSource`, composition, `KeyMapper`, `KeyFormat`, `SourceValue`, `Provenance`, `ProvenanceMap`, `Secret`, `Displayable` |
| `config-decoder.md` | `ConfigDecoder`, `ConfigDecoderDeriver`, one mapping rule per schema shape, the primitive parsing table, discriminators, error accumulation |
| `errors.md`         | `ConfigError` and its four category traits, every constructor, `ConfigLoadException`        |
| `flags.md`          | `FlagSource`, `Registry`, `StaticFlag`, `DynamicFlag`, `Flag.Reader`, `Flag.Source`, `FlagException`, `Flag.dump` |
| `rollout.md`        | Rollout grammar, `Choice`/`Selector`/`Segment`, bucketing, `Flag.ReloadResult`, `UpdateRecord`, counters |
| `formats.md`        | YAML, JSON, and HOCON adapters, flattening rules, substitutions, includes, `HoconValue`, JVM file loading |

The family is now at 70% explained coverage with 7 absent types, none of which is user-facing API. Writing the pages required adding the four config modules to the `docs` project in `build.sbt` — they were absent from its classpath, which is why no config code block had ever been compiled.

Three behaviours that the source made non-obvious and the new pages now state explicitly: a rollout selector must match a path's segment count **exactly** (the bucketing key is itself the first segment); flag durations use a `30s` suffix grammar while config durations require ISO-8601 `PT30S`; and a flattened `null` is indistinguishable from an absent key, so it cannot be used to unset a lower-priority layer.

What remains, all minor:

- [ ] Name `ConfigSourceHoconSyntax` and `ConfigSourceHoconPlatformSyntax` in `formats.md`, or make them private — the mechanism is described but the traits are not named
- [ ] `DisplayableLowPriority` is an implicit-priority helper and is deliberately skipped; consider tightening its visibility
- [ ] `ConfigError.DuplicateKey` and `ConfigError.Unauthorized` are documented as unused by the module; decide whether they should exist at all
- [ ] `ConfigValidationError` is sealed with zero implementations, so matching on it can never match; either give it a constructor or remove it

### 2. `http-model` — RESOLVED

`Header.scala` is 1,861 lines defining 76 header types, of which 75 are typed built-ins. The `## Headers` section of `model.md` was 36 lines showing only the untyped `String` API; 101 of the module's 191 public types were absent.

Two new pages, 956 lines together, with every code block mdoc-verified:

| Page                   | Covers                                                                                     |
| ---------------------- | ------------------------------------------------------------------------------------------ |
| `headers.md`           | `Header`, `Header.Codec`, `Header.Typed`, `Header.Custom`, all 75 built-ins catalogued in ten groups with wire names and ADT variants, the six read methods, the parse cache, write operations, `HeadersBuilder`, validation and injection safety, writing a custom codec |
| `server-sent-event.md` | `ServerSentEvent`, its constructors and metadata builders, validation, render order, `SseDataEncoder` and its instances, custom encoders |

The `## Headers` section of `model.md` was rewritten to cover the collection itself — creation, raw reads, append versus replace — and now links out for the typed model rather than omitting it. The module is at 56% explained coverage with 5 absent types.

Three behaviours the source made non-obvious, now stated with worked output:

- **`Headers#get` discards parse errors.** A malformed header is indistinguishable from an absent one, and a malformed entry followed by a well-formed one silently yields the latter. No collection read surfaces the error.
- **The parse cache is keyed by codec identity, compared by reference.** A codec constructed inline per request never reuses its cached values, and `Headers#add` drops the cache entirely.
- **`Headers#toString` prints credentials verbatim.** There is no redaction, so anything logging a `Request` logs its `authorization` and `cookie` values.

Writing the catalog also surfaced a genuine defect, documented in a warning admonition and worth fixing in the source:

- [ ] `Header.AcceptEncoding.parseSingle` ends with `case _ => GZip(weight)` (`Header.scala:1250`), so any unrecognized encoding name silently parses as `GZip` — `accept-encoding: bogus` reads as a gzip request. `Header.AcceptEncoding.parse` rejects only values with no non-empty comma-separated part, so `""`, `","`, and `"   "` are the only failing inputs. The sibling ADTs handle the same situation correctly: `Authorization` has an `Unparsed` case and `Connection` has `Other`. `AcceptEncoding` should either gain an equivalent case or return `Left`. Tracked as [zio/zio-blocks#1618](https://github.com/zio/zio-blocks/issues/1618).

What remains is the report's own items 4 and 5 rather than anything new:

- [ ] Document `PercentEncoder`, `QueryKey`, `QueryValue`, and `QueryParamsBuilder` in the URL and query sections of `model.md`
- [ ] Document `ComponentType`

Note that `http-model` still shows 84 unexplained types against only 5 absent. Almost all of that is the qualified-name artifact: `headers.md` writes `Header.ContentLength`, which the bare-name match never sees. It is a measurement limitation rather than 84 undocumented types.

### 3. `http-model-schema` — RESOLVED

`schema.md` (607 lines) documented the extension-class surface — `QueryParamsSchemaOps`, `HeadersSchemaOps`, `RequestSchemaOps`, `ResponseSchemaOps` — and nothing underneath it, so 14 of the module's 18 scanned types were absent.

The gap turned out to be different from what this entry described. It was not "the machinery under the extension classes": `HeadersSchemaOps` does not use `HeaderCodec` at all, it uses the private `StringDecoder`. The codec layer is a **separate, parallel API** — whole-value encoding and decoding via `Schema[A].derive(DefaultHeaderFormat)` — that the documentation never mentioned in either form.

Resolved by adding `schema-codecs.md` (463 lines, all code blocks mdoc-verified), covering `HeaderCodec`, `QueryCodec`, `HeaderFormat`, `QueryFormat`, `DefaultHeaderFormat`, `DefaultQueryFormat`, `HeaderCodecDeriver`, and `QueryCodecDeriver`, plus field-mapping rules, supported shapes, top-level codecs, custom formats, and single-type instance overrides. `schema.md` points at it from its opening, its custom-types section, and its See Also.

Three behaviours the source made non-obvious:

- **The two codecs name fields differently.** `QueryCodec` uses the field name verbatim; `HeaderCodec` converts camelCase to kebab-case. Neither is configurable.
- **Unsupported top-level shapes fail late.** `Schema[Option[A]]`, `Schema[Map[K, V]]`, and `Schema[DynamicValue]` all derive successfully and then throw on the first encode, so a codec built at startup can look healthy until the first request that uses it.
- **The convenience encoders share a thread-local builder.** `HeaderCodec#encodeToHeaders` resets it before filling, so a custom `Codec#encode` that calls it recursively corrupts the buffer the outer call was building.

**This module is what exposed the scanner bug.** Six of the types this entry listed as absent — `DecodeErrorFactory`, `FieldCodec`, `SinglePrimitive`, `OptionalValue`, `SequenceValue`, `WrappedValue` — are nested inside `private[schema] object ParamCodecSupport` and are not API at all. The old scanner counted them as public because it only checked modifiers on the declaration line. Fixing that is what produced this revision's numbers, and the row now reads 100% with 16 public types and 10 internal ones.

### 4. `otel` — RESOLVED, and this item was mis-scoped

The original entry called for splitting the 162-line page into four, including an `otlp-exporters.md`. That was wrong, and the reason is worth recording because it applies to other rows in the table.

`OtlpJsonTraceExporter`, `OtlpJsonLogExporter`, `OtlpJsonMetricExporter`, `BatchProcessor`, `OtlpJsonExporter`, and `JdkHttpSender` are all `private[otel]` — six of the module's eighteen declarations. An `otlp-exporters.md` page would have documented internals. The 0.12 ratio that flagged this row is misleading for the same reason `mediatype`'s 0.04 is: roughly 60% of the module's lines are not public surface, and the existing page already covered six of the ten public types well, including a paragraph explaining that the exporters are unreachable.

The real gap was four public types and one missing recipe:

- `OtlpJsonEncoder` (527 lines) and `NamedMetric` — the only public way to produce OTLP payloads, and therefore the answer to the dead end the old page described rather than resolved
- `ExportResult` and its `fromHttpResponse` classification
- `OtelContext`, which bridges `ContextStorage` with `Context[R]`

Resolved by adding `custom-exporter.md` (210 lines) covering the encoder, the encoding rules, `ExportResult`, and a worked flush function that assembles the public pieces into a working exporter; and by extending `index.md` with an `OtelContext` section, the `HttpResponse` shape, and a replacement for the dead-end paragraph. The module is now at 100% — 0 absent, 0 unexplained, all 12 public types documented.

Two findings from writing it:

- **`MetricData` carries no name.** `MetricReader#collectAllMetrics` returns `Seq[MetricData]` and `OtlpJsonEncoder.encodeMetrics` needs `Seq[NamedMetric]`, but nothing public recovers which instrument produced which element. Documented as a warning; worth an API fix.
- **`ExporterConfig`'s three sizing fields have no public reader.** `maxQueueSize`, `maxBatchSize`, and `flushIntervalMillis` are only consumed by the private `BatchProcessor`, so a hand-rolled exporter must implement queueing, chunking, and interval flushing itself. The new page lists what that means.

**Lesson for the remaining rows:** check the public/private split before trusting a low ratio. Rows where most lines may be internal should be verified the same way before being scoped as multi-page splits.

### 5. `schema` — 220 unexplained types clustered in seven subsystems

At 84,969 source lines and 23,842 documentation lines, `schema` is the best-documented module in absolute terms and still holds the largest absolute gap: 163 absent and 220 unexplained of 466 public types, with a further 226 declarations internal. The unexplained types are not scattered; they cluster.

**`Into` conversions** — the entire primitive conversion matrix is absent: `ByteToInt` ✗, `ByteToLong` ✗, `ByteToShort` ✗, `ByteToFloat` ✗, `ByteToDouble` ✗, `ByteToString` ✗, `IntToByte` ✗, `IntToChar` ✗, `IntToShort` ✗, `IntToLong` ✗, `IntToFloat` ✗, `IntToDouble` ✗, `IntToString` ✗, `LongTo*` ✗, `ShortTo*` ✗, `FloatTo*` ✗, `DoubleTo*` ✗, `CharToInt` ✗, `CharToString` ✗, `BooleanToString` ✗, `StringToBoolean` ✗, `StringToByte` ✗, `StringToShort` ✗, `StringToInt` ✗, `StringToLong` ✗, `StringToFloat` ✗, `StringToDouble` ✗, plus `ConversionType` ✗ and `DynamicConversionError` ✗.
- [ ] Add a conversion-matrix table — which conversions exist, which are lossy, which can fail and how

**`SchemaExpr` operators** — `BitwiseOperator` ✗, `LeftShift` ✗, `RightShift` ✗, `UnsignedRightShift` ✗, `Xor` ✗, `Pow` ✗, `Modulo` ✗, `IsIntegral` ✗, `NumericPrimitiveType` ✗, plus `Divide` and `NumericTypeTag` unexplained.
- [ ] Add an operator reference to `schema-expr.md`, including which operators require `IsIntegral` vs `IsNumeric`

**Migration** — `migration.md` and `schema-evolution/` exist, but the error model does not appear: `MigrationError` ✗, `MigrationErrorKind` ✗, `MissingDefault` ✗, `MandateFailed` ✗, `TransformFailed` ✗, `FieldName` ✗, `MigrationSelectorSyntax` ✗, plus `MigrationBuilderSyntax` (854 lines) unexplained.
- [ ] Document the migration error ADT and what each failure means for a migration run
- [ ] Document the selector syntax surface used to target fields

**Patch operations** — `DynamicPatchOp` ✗, `MapEdit` ✗, `MapOp` ✗, `SeqOp` ✗, `SequenceEdit` ✗, `BigIntDelta` ✗, `ForInstant` ✗, `ForLocalDate` ✗, `ForPeriod` ✗.
- [ ] Document the patch operation ADT, the map/sequence edit encodings, and the temporal delta types

**Search, traversal, and transformation — done for all three parts of this cluster.** `reference/schema/schema-search.md` (251 lines, mdoc-verified) documents `SchemaMatch`'s structural matching rules, `SearchTraversal`'s `fold`/`modify`/`modifyOption`/`modifyOrFail`/`check` and its composition with other optics, and `Reflect.Updater`/`Term.Updater` (including how `Term.Updater` deletes a field/case by returning `None`) — `TypeSearch`/`SchemaSearch` themselves were already covered by `dynamic-optic.md`'s `## Search Optics` section, which now cross-links to the new page. `reference/schema/reflect-transformer.md` (140 lines, mdoc-verified) documents `ReflectTransformer` (230 lines) and its `OnlyMetadata` base class, `RebindTransformer` (237 lines, `private[schema]` — documented through its public entry point `DynamicSchema#rebind`), and `RebindException`. `Frame` turned out to be unrelated to this cluster despite the grouping — it's an internal traversal-stack ADT used by `DynamicValue`/`Json` patch application, not by `ReflectTransformer` or the search/update surface. **`SchemaAspect`/`SchemaRepr` — done:** `schema.md`'s `## Schema Aspects` section was corrected — it showed a `recursive` method on the `SchemaAspect` trait that never existed in source — and expanded to cover the `Reflect#aspect` overloads `Schema#@@` delegates to and the silent no-op fallback when a path-targeted aspect's optic doesn't resolve. `dynamic-optic.md`'s `## Search Optics` section gained a new `### The SchemaRepr Pattern Type` subsection covering the 8-case ADT as a constructible value (not just interpolator sugar), its `render`/`toString`, and `SchemaParser`'s grammar and error reporting; its `Nominal` limitation note now covers the one exception (`Reflect`-tree search, which has real `TypeId`s to match against); and its pattern table now lists `set(...)`/`vector(...)` as synonyms for `list(...)`, which it previously omitted. Still absent: `Frame` ✗ and `SchemaParser` (344 lines) unexplained.
- [x] Write `reference/schema/schema-search.md` covering `SchemaSearch` / `SchemaMatch` / `TypeSearch` / `SearchTraversal` / `Updater` — **done**: 251 lines, mdoc-verified, wired into `sidebars.js` and cross-linked from `dynamic-optic.md` and `schema.md`
- [x] Write `reference/schema/reflect-transformer.md` covering `ReflectTransformer` and `RebindTransformer` — **done**: 140 lines, mdoc-verified, wired into `sidebars.js` and cross-linked from `binding.md` (which had a stale forward-reference promising this coverage) and `dynamic-schema.md`
- [x] Document `SchemaAspect` and `SchemaRepr` — **done**: `schema.md` and `dynamic-optic.md` sections corrected/expanded (see above), `path-interpolator.md` gained the `set`/`vector` synonym note

**Derivation overrides** — `type-class-derivation.md` never names the override subtypes: `InstanceOverrideByType` ✗, `InstanceOverrideByOptic` ✗, `InstanceOverrideByTypeAndTermName` ✗, `ModifierReflectOverrideByType` ✗, `ModifierReflectOverrideByOptic` ✗, `ModifierTermOverrideByType` ✗, `ModifierTermOverrideByOptic` ✗.
- [ ] Document each override form with the selection rule that distinguishes it

**Optic and rebuild errors** — `CaseNotFound` ✗, `FieldNotFound` ✗, `FieldAlreadyExists` ✗, `PathNotFound` ✗, `TypeMismatch` ✗, `InvalidValue` ✗, `EmptyRecord` ✗, `EmptyVariant` ✗, `RebuildRecord` ✗, `RebuildVariant` ✗, `RebuildSequence` ✗, `RebuildMap` ✗, `RebuildObject` ✗, `RebuildArray` ✗, plus `EmptySequence` unexplained.
- [ ] Add an error-case table to `schema-error.md` and `optics.md`

**JSON Schema and refinements** — `Anchor` ✗, `UriReference` ✗, `EvaluationResult` ✗, `JsonMatch` ✗ (153 lines), `FieldInfo` ✗, plus `ValidationOptions`, `RegexPattern`, `NonBlank`, `NonNegative`, `NonNegativeInt`, `Positive`, `PositiveNumber`, `Negative`, `NonPositive` unexplained.
- [ ] Document `$anchor` / `$ref` handling (`Anchor`, `UriReference`) and `ValidationOptions` in `built-in-codecs/json/json-schema.md`
- [ ] Document the refinement types — they appear in public signatures

**`comptime` grammar** — `GrammarNode` ✗, `GRecord` ✗, `GUnion` ✗, `GMap` ✗, `GOptional` ✗, `GPrimitive` ✗, `GSequence` ✗, `GSeqList` ✗, `GSeqVector` ✗, `GSeqSet` ✗, `GSeqChunk` ✗, `GSeqArray` ✗, `GDynamic` ✗, `GIsType` ✗, `GSelf` ✗, `GWrapped` ✗ back the `Allows` mechanism documented in `allows.md`.
- [ ] Decide whether the grammar ADT is public; if yes, document it in `allows.md`; if not, mark it `private[schema]`

Also absent: `DocsSchemas` (1,327 lines) and `DerivedOptics` (581 lines) — check whether either is meant to be public.

### 6. `telemetry` — the value ADT and the log-emitter layer

45 unexplained and 9 absent, of 139 public types, across two clusters. The `AnyValue`/attribute-type cluster below is now resolved (was part of the original 57/17); what remains is the log-emitter layer.

- **`AnyValue` / attribute types — done, with a correction:** this cluster's names didn't match the source. There is no `BoolValue`, `IntValue`, `ArrayValue`, or any `*KV` type (`StringStringKV` etc.) anywhere in the codebase or its history; the real, only value ADT is `AttributeValue` (`StringValue`, `BooleanValue`, `LongValue`, `DoubleValue`, `StringSeqValue`, `LongSeqValue`, `DoubleSeqValue`, `BooleanSeqValue`) alongside the separate discriminator ADT `AttributeType` (`StringType`, `BooleanType`, `LongType`, `DoubleType`, and four `*SeqType` variants). `reference/telemetry/common/any-value.md` (90 lines, mdoc-verified) now documents both ADTs, the `AttributeValue` → `AttributeType` → `AttributeKey` three-way correspondence, and the OTLP JSON mapping (`stringValue`/`boolValue`/`intValue`/`doubleValue`/`arrayValue`) the `otel` exporter uses.
- **Signal detail**: `LogState` ✗, `SourceLocation` ✗, `Templated` ✗, `AttributesKind` ✗, `EnrichmentKind` ✗, `FallbackKind` ✗, `SeverityKind` ✗, `StringBodyKind` ✗, `ThrowableKind` ✗, plus `SpanEvent`, `SpanLink`, `Measurement`, `GaugeDataPoint`, `HistogramDataPoint`, `SamplingDecision`, `LogMessage`, `LogRecordBuilder` unexplained, and the `Severity` numbered variants (`Trace2`–`Trace4`, `Debug2`–`Debug4`, `Info2`–`Info4`, `Warn2`–`Warn4`, `Error2`–`Error4`, `Fatal2`–`Fatal4`) unexplained

Absent implementation types with a public entry point: `LogEmitter` ✗ (101 lines), `FormattedLogEmitter` ✗, `FileLogWriter` ✗ (163 lines), `StdoutLogRecordProcessor` ✗ (134 lines), `SyncInstruments` ✗.

Actions:

- [x] Write `reference/telemetry/common/any-value.md` — **done**: 90 lines, mdoc-verified, wired into `sidebars.js` and cross-linked from `attributes.md` and `otel/index.md`. The `KV` shortcuts named in the original action item don't exist in source; documented `AttributeValue`/`AttributeType` instead (see above)
- [ ] Add `SpanEvent` and `SpanLink` sections to `tracing/span.md`
- [ ] Add `Measurement`, `GaugeDataPoint`, `HistogramDataPoint` to `metrics/metric-data.md`
- [ ] Add `SamplingDecision` to `tracing/sampler.md`
- [ ] Write `reference/telemetry/logging/log-emitter.md` — `LogEmitter`, `FormattedLogEmitter`, `FileLogWriter`, `StdoutLogRecordProcessor`
- [ ] Document the full `Severity` scale, including the numbered sub-levels
- [ ] Document the log-record `*Kind` classifiers or make them private

### 7. `endpoint` — combinators and segment shortcuts

20 unexplained types: `Alternator` ✗, `CanCombine` ✗, `PathVarsCombiner` ✗, `RoutePathVarsCombiner` ✗, `SegmentCodecOps` ✗, `SinglePathVarPathCodecOps` ✗, `WithStatus` ✗, `ErrorBuilder` ✗, `EndpointUnionErrorBuilder` ✗, `IntSeg` ✗, `LongSeg` ✗, `BoolSeg` ✗, `StringSeg` ✗, `UUIDSeg` ✗, plus `Ignored`, `PathVar`, and `PathCodecRuntime` unexplained.

`Alternator` and `CanCombine` are the type-level machinery that decides what `++` and `|` produce — without them the combinator signatures in `endpoint.md` and `http-codec.md` cannot be read.

Actions:

- [ ] Add a *Type-level combination* section covering `Alternator` and `CanCombine` with the resulting-type rules
- [ ] Document `PathVarsCombiner` / `RoutePathVarsCombiner` and how path variables accumulate into a tuple
- [ ] Document the `*Seg` shortcuts in `segment-codec.md`
- [ ] Document `WithStatus`, `ErrorBuilder`, and `EndpointUnionErrorBuilder` in the error section of `endpoint.md`

### 8. `htmx` — response headers

**Done.** The attribute DSL was well covered (2,521 doc lines, ratio 1.63), but the header side — `HtmxHeaders` (334 lines) and its 22 request/response header types — was absent. `reference/htmx/response-headers.md` (229 lines, mdoc-verified) now covers both directions: the request headers HTMX sends (`HxRequest`, `HxBoosted`, `HxCurrentUrl`, `HxTargetId`, `HxTriggerId`, `HxTriggerName`, `HxHistoryRestoreRequest`, `HxPrompt`), the response headers a handler sets (`HxLocation`, `HxPushUrl`, `HxReplaceUrl`, `HxRedirect`, `HxRefresh`, `HxReswap`, `HxRetarget`, `HxReselect`, `HxTriggerHeader`, `HxTriggerAfterSettle`, `HxTriggerAfterSwap`, `HxEventPayload`), and how each reuses `HxSwap`/`HxTarget`/`HxUrlUpdate`/`CssSelector` from the attribute DSL. `HxTriggerValue`, `HxOnKey`, `PartialHxOn`, `Changed`, and `Threshold` from the original absent-types list are attribute-DSL types (not headers) and remain covered by `hx-trigger.md`/`attribute-values.md`. The internal `HtmxHeaderSupport` parsing helper is `private[headers]` and intentionally left undocumented as an implementation detail, not a public integration point.

Actions:

- [x] Write `reference/htmx/response-headers.md` — **done**: 229 lines, mdoc-verified, wired into `sidebars.js` and cross-linked from `reference/htmx/index.md`

### 9. `smithy` — RESOLVED

`smithy.md` covered parsing, querying, building, and serializing, but its *Core Types* tree elided the shape ADT with "StringShape, BooleanShape, IntegerShape, etc." and "... (and other shape subtypes)". 16 of 42 public types were absent, and the module had the lowest coverage in the repository at 24%.

A `## Shape Catalog` section (348 lines, mdoc-verified) now covers all 20 `Shape` subtypes in the four families the source organizes them into: 13 simple shapes with a table of IDL keywords, `EnumShape`/`IntEnumShape` with their member types, the four aggregate shapes and the `MemberDefinition` they share, and the three service shapes including a field table for `ResourceShape`'s identifiers and five lifecycle operations. `ShapeRef`, `ShapeId`, `ShapeId.Member`, and reference resolution get their own subsections. The module is now at **90% with no absent types**.

The catalog uses evaluated `mdoc` blocks rather than the page's `compile-only` style, which surfaced two behaviours worth knowing and neither previously documented:

- **Parsed references carry an empty namespace.** An IDL target written without a namespace prefix becomes `ShapeId(namespace = "", name = "TagList")` — not the model's namespace, and not `smithy.api`. This holds for structure members, list/map members, resource identifiers, and lifecycle operations alike. Trait identifiers are the exception and do arrive qualified as `smithy.api`. This is why `SmithyModel#findShape` matches on name alone, and why comparing `ShapeId#namespace` on a parsed reference tells you nothing.
- **A parsed `ShapeId` need not round-trip through `ShapeId.parse`.** `ShapeId("", "String").toString` renders `"#String"`, which `ShapeId.parse` then rejects with "ShapeId namespace cannot be empty".

Also documented: `ListShape` and `MapShape` declare their defaulted `traits` parameter *before* their undefaulted `member`/`key`/`value` parameters, so only named-argument construction compiles.

Remaining: 4 unexplained types, none absent. Two are measurement artifacts rather than gaps — `SmithyModel` and `ShapeId.Member` are discussed throughout, but always in qualified form (`SmithyModel.parse`, `ShapeId.Member`), which the bare-name match does not see. The other two are real, small, and belong to the metadata surface rather than the shape ADT:

- [ ] `NodeValue` — the metadata value ADT, named in the *Core Types* tree and used in examples but never explained
- [ ] `ApplyStatement` — appears in the `SmithyModel` signature with no accompanying prose

### 10. `streams` — the I/O adapter surface

`sink.md` documents `NioSinks`, but the reader side and the queue primitives do not appear: `NioReaders` ✗, `NioWriters` ✗, `SinkError` ✗, `StreamState` ✗, `OpTag` ✗, `BlockingSpscQueue` ✗, `BlockingMpscQueue` ✗, `BlockingMpmcQueue` ✗, plus `ByteBufferReader` (554 lines), `ChannelReader`, and `ChannelWriter` unexplained.

Actions:

- [ ] Add a *JVM NIO Readers* section to `reader.md` mirroring the NIO section in `sink.md` (`NioReaders`, `ByteBufferReader`, `ChannelReader`)
- [ ] Add `NioWriters` / `ChannelWriter` to `writer.md`
- [ ] Document `SinkError`
- [ ] State the sentinel-based EOF design in `reader.md` — it is enforced in review (`AGENTS.md`, *Sentinel performance policy*) but never explained to users

### 11. `typeid` — `Member` subtypes and segment kinds

44 unexplained types, 26 of them absent. Absent: `Def` ✗, `Val` ✗, `Param` ✗, `TypeMember` ✗, `EnumCaseParam` ✗, `TupleElement` ✗, `PkgSegment` ✗, `TermSegment` ✗, `TypeSegment` ✗, `SegmentInfo` ✗, plus `TypeIdOps` ✗ (333 lines). Unexplained but present in examples: `TypeBounds`, `ThisType`, `TypeProjection`, `TypeSelect`, `ParamRef`, `Repeated`, `ByName`, `Annotated`, `ArrayArg`, `ClassOf`, `EnumValue`, `Covariant`, `Contravariant`, `Invariant`, and the `*Const` literal types.

Actions:

- [ ] Document the `Member` ADT (`Def`, `Val`, `Param`, `TypeMember`, `EnumCaseParam`) in `typeid.md`
- [ ] Document `Owner` segments (`PkgSegment`, `TermSegment`, `TypeSegment`) and `SegmentInfo`
- [ ] Document `TypeBounds` and `TupleElement`
- [ ] Document the `TypeIdOps` extension surface
- [ ] Add a `TypeRepr` pattern-matching reference covering the variance and literal variants

### 12. Smaller module gaps

- **`html`** (68 unexplained, 40 absent) — **the rank-1 justification in the table above was wrong and is corrected here.** An earlier revision of this entry claimed the typed content model was "entirely unnamed"; it is not. `html.md` has had a `## Typed Content Models` section since #1536 landed, naming every marker type (`Dom.Element.Li`, `Cell`, `Tr`, `SelectChild`, `Opt`, `Optgroup`), demonstrating compile-time rejection with an `mdoc:fail` block, and documenting structural equality across element classes and the `caption`/`thead` limitation. The 40 absent types break down as follows, and only two groups are conceptual gaps:
  - **Real: CSS values — resolved.** `CssLength`'s unit set and numeric extension methods, and four of `CssColor`'s five cases, had no mention anywhere. The page's one example used the verbose `CssLength(300.0, "px")` while `300.px` existed unmentioned, so the docs actively steered readers to the clunkier API. A `### Typed Values: Lengths and Colors` section now covers the 15 valid units, `CssLengthIntOps`/`CssLengthDoubleOps` and the second import they require, all five `CssColor` cases, and the `Hex` versus `Hex.unsafe` validation split.
  - **Real: the Scala 2 argument encoding.** `ListArg` ✗, `CellArg` ✗, `RowArg` ✗, `SelectArg` ✗, `OptgroupArg` ✗, `ScriptArg` ✗, and `StyleArg` ✗ are how the content model is expressed on 2.13 — sealed traits plus implicit conversions, where Scala 3 uses union types (`Dom.Attribute | Dom.Element.Li`). The content-model table writes the Scala 3 form only, and no page mentions that the encodings differ. This needs tabbed examples per the writing-style rule on version-specific syntax.
  - **Real: extension points.** `DomModifier` ✗ and its four cases (`AddAttr` ✗, `AddChild` ✗, `AddChildren` ✗, `AddEffects` ✗), plus the `ToDom` ✗ and `ToText` ✗ conversion type classes. `ToModifier` is named in the page; these are not.
  - **Naming only: selector ADT nodes.** `Descendant` ✗, `AdjacentSibling` ✗, `GeneralSibling` ✗, `AttributeMatch` ✗, `StartsWith` ✗, `EndsWith` ✗, `WhitespaceContains` ✗, `HyphenPrefix` ✗, `PseudoClass` ✗, `PseudoElement` ✗. Every one is reachable and demonstrated through the DSL (`div >> span`, `a.hover`, `input.withAttributeStarting(...)`); only the resulting node types are unnamed. Worth a short table for readers pattern matching on a built selector, not a section.
  - **Naming only: concrete element classes.** `LiElement` ✗, `ThElement` ✗, `TdElement` ✗, `TrElement` ✗, `OptElement` ✗, `OptgroupElement` ✗ — the implementations behind the documented marker traits.
  - **Skip: interpolator plumbing.** `CssStringContext` ✗, `HtmlStringContext` ✗, `JsStringContext` ✗, `SelectorStringContext` ✗, `TemplateInterpolators` ✗, `InterpolatorRuntime` ✗, `HtmlElements` ✗, `DomModifierConversions` ✗, `LowPriorityToJs` ✗, `JsValue` ✗. These match the naming patterns in *Deliberately Undocumented*; the interpolator syntax is documented, its machinery should not be.
  - [ ] Add an ADT reference section naming the selector, colour, and modifier types behind the DSL
- **`datastar`** (28 unexplained) — same shape: `EventModifier` ✗, `CaseModifier` ✗, `InitModifier` ✗, `IntersectModifier` ✗, `OnIntervalModifier` ✗, `OnSignalPatchModifier` ✗, `DataOn` ✗, `PatchSignals` ✗, `PatchElements` ✗, `DatastarAttributes` ✗, `DatastarAttrKey` ✗, `ToDatastarExpr` ✗, `DataSignalsBuilder` ✗, `EventType` ✗. The 0.18 ratio is the bigger problem: 346 lines for 1,881 source lines.
  - [ ] Expand `datastar.md` — each SSE event type needs a worked example; add an attribute-DSL type reference
- **`schema-xml`** (15 unexplained) — `XmlCodecError` ✗, `XmlWriter` ✗, `XmlCodecDeriver` ✗ (657 lines), `SetAttribute` ✗, `RemoveAttribute` ✗, `ElementBuilder` ✗
  - [ ] Add error-handling and deriver/customization sections to `built-in-codecs/xml.md`
- **`schema-yaml`** (8 unexplained) — `YamlCodecError` ✗, `YamlTag` ✗, `YamlSyntax` ✗, `YamlStringContext` ✗; 552 doc lines for 2,753 source lines
  - [ ] Document `YamlTag`, the `yaml""` interpolator, and the error type
- **`codegen`** (17 unexplained) — `ParamList` ✗, `ParamListModifier` ✗, `ExtensionBlock` ✗, `NestedType` ✗, `GroupImport` ✗, `RenameImport` ✗, plus `SingleImport`, `WildcardImport`, `SimpleCase`, `ParameterizedCase`, `CompanionObject`, `DefMember`, `ValMember` unexplained despite a 1.44 ratio
  - [ ] Add the import forms and `ExtensionBlock` / `NestedType` to `reference/codegen/`
- **`scope`** (12 unexplained) — `WireInfo` ✗, `WireKind` ✗ in `resource-management/wire.md`; `InStack`, `Destroyed`, `Uninitialized` unexplained
- **`mux`** — `HalfClosedLocal` ✗, `HalfClosedRemote` ✗ stream states
  - [ ] Complete the stream-state lifecycle in `mux.mdx`
- **`context`** — `ContextHas` ✗ (55 lines), `ContextEntries` ✗ (241 lines)
- **`combinators`** — `TuplesLowPriority` ✗, `TuplesLowPriority1` ✗ (implicit-priority helpers; safe to skip)
- **`maybe`** — `MaybeSyntax` ✗, `MaybeOps` ✗, `MaybeValue` ✗, `MaybeWithFilter` ✗, `WithFilter` ✗, `MaybeCompat` ✗, `MaybeSyntaxCompat` ✗. All seven are syntax or compatibility shims, and the page already exceeds the source in size; **skip permanently** rather than re-triaging each revision
- **`openapi`** — `OpenAPIGen` ✗ (32 lines) only
- **`sql`** — `PgCodec` ✗ (169 lines, PostgreSQL type mapping) only
- **`markdown`** — `MdInterpolator` ✗, `MdStringContext` ✗: the `md"..."` interpolator is documented; the runtime types are not. **Skip.**
- **`chunk`**, **`schema-bson`**, **`schema-csv`** — one or two unexplained internals each; effectively complete

---

## Conceptual and Guide Gaps

`docs/guides/` holds 9 files covering `async`, `scope`, `mux`, the SQL query DSL (4 files), `telemetry`, and migration *from* zio-schema. Every other module has reference documentation only.

No guide exists for:

| Module | src LOC | Suggested guide |
|---|---:|---|
| **schema** | 84,969 | Deriving your first schema; encode/decode round trip |
| **streams** | 18,434 | Building a streaming pipeline end to end |
| **http-model** + **endpoint** | 7,517 | Describing and consuming an HTTP API |
| **html** + **htmx** + **datastar** | 8,154 | Building a hypermedia page |
| **typeid** | 6,493 | Reflecting on types at compile time |
| **chunk** | 5,069 | Choosing `Chunk` over `Vector` / `Array` |
| **openapi** + **smithy** | 5,016 | Generating clients from a service description |
| **config** | 3,914 | Loading typed configuration and feature flags |
| **codegen** | 2,100 | Generating Scala sources |

Cross-cutting documents that do not exist:

- [ ] **Getting Started** — add dependencies, define a case class, derive a schema, encode to JSON. `docs/index.md` is a block catalog, not an on-ramp.
- [ ] **Architecture Overview** — module dependency graph, the register-based zero-allocation design, the `Reflect` → `Binding` → `Schema` layering, the `Deriver` pattern
- [ ] **Zero-dependency and cross-platform contract** — what is JVM-only, what is JS-safe, and what the `scala-2` / `scala-3` source splits mean for users
- [ ] **Performance guide** — `Chunk.materialize`, register allocation, derivation caching, the streams sentinel design, and the labeled-instrument allocation trade-off (currently explained only inside `labeled-instruments.md`)
- [ ] **Custom codec how-to** — implementing a `Format` and its `Deriver` end to end

---

## Deliberately Undocumented

These naming patterns are internal by construction. Do not write documentation for them; if any are public by accident, tighten their visibility instead.

| Pattern | Reason | Examples |
|---|---|---|
| `*Macros`, `*MacroOps`, `MacroUtils`, `MacroCore` | Compile-time implementation | `PathMacros`, `SelectorMacros`, `MigrationValidationMacros`, `CommonMacroOps`, `DbCodecOpaqueMacro` |
| `*VersionSpecific`, `*PlatformSpecific`, `Platform*`, `*Compat` | Scala 2/3 and JVM/JS source-split shims | `SchemaVersionSpecific`, `TypeIdPlatformSpecific`, `PlatformConfigSource`, `PlatformMux`, `MaybeCompat` |
| `*LowPriority`, `*LowPriority1` | Implicit-resolution priority helpers | `TuplesLowPriority`, `TypeIdLowPriority`, `PathVarsCombinerLowPriority`, `DisplayableLowPriority` |
| `*Impl`, `*Runtime`, `*CodeGen` | Private implementations behind a public façade | `ScopeImpl`, `PathCodecRuntime`, `InterpolatorRuntime`, `JsonInterpolatorRuntime`, `WireCodeGen` |
| `*StringContext` | Interpolator plumbing; document the interpolator syntax instead | `JsonStringContext`, `YamlStringContext`, `CssStringContext`, `MediaTypeStringContext` |
| Generated primitive-lane readers | Machine-generated specializations of one documented shape | `LongConcurrentMapParReader`, `IntConcurrentMergeReader`, `DoubleConcurrentMapParReader`, and siblings |
| `PathParser` error states | Internal parser states | `EmptyChar`, `InvalidEscape`, `UnexpectedChar`, `UnterminatedString`, `IntegerOverflow`, `MultiCharLiteral` |
| JSON interpolator states | Internal state machine | `TopLevel`, `InString`, `AfterValue`, `ExpectingKey`, `ExpectingColon`, `ExpectingValue` |
| `*Delta` / `*Dummy` in `patch` | Internal patch encodings | `ByteDelta`, `FloatDelta`, `PeriodDelta`, `DurationDummy`, `PeriodDummy` |
| `scope/internal/*` | Error-rendering internals | `Colors`, `DepNode`, `DepStatus`, `ErrorMessages` |
| async CPS internals | Direct-style transform machinery, not user-facing | `AsyncCpsMonad`, `AwaitCall`, `CollectAwaitCall`, `FoldLeftAwaitCall`, `HofAwaitCall`, `TypedHofMap`, `WaitingMarker`, `NullCauseMarker`, `WithFilterChain`, `PartialFunctionLiteral`, `SingleArgFunction`, `TwoArgFunction`, `AsyncDcaTransform`, `AsyncRunner`, `Parker` |
| `private[...]` parsers and printers | Not part of the public surface | `SmithyParser`, `SmithyPrinter`, `HoconParser`, `SchemaParser`, `ReflectPrinter`, `TypeIdPrinter` |

---

## Prioritized Action List

Ordered by user impact per unit of writing effort. The ranking below predates the scanner fix; by the corrected table, the largest *genuine* remaining gaps are, in order:

| Rank | Module | absent / public | cov | Why |
| ---- | ------ | --------------- | ---- | --- |
| 1 | `html` | 40 / 110 | **38%** | Selector and modifier ADTs unnamed, and the Scala 2 argument encoding is undocumented |
| 2 | `maybe` | 6 / 10 | 40% | Small surface, but more than half of it unnamed |
| 3 | `typeid` | 26 / 90 | 52% | `Member` ADT and owner segments |
| 4 | `schema-xml` | 9 / 38 | 53% | Error types and the deriver |
| 5 | `schema` | 156 / 466 | 56% | Largest absolute, but six separable subsystems remain |
| 6 | `endpoint` | 18 / 60 | 67% | `Alternator`, `CanCombine`, segment shortcuts |

`smithy` has left the list — the shape catalog took it from 24% to 86% with no absent types. `streams` and `async` drop out entirely once internal declarations are excluded. `htmx` and `datastar` left earlier: #1619 took `htmx` to 77%, and the five-page datastar split took it to 98%.

`html` grew from 100 public types to 110 between revisions while its documentation did not, which is the one row where waiting makes the work larger.

**Tier 1 — new pages for missing subsystems**

1. - [x] `reference/http-model/headers.md` — **done**: 660 lines cataloguing all 75 built-ins, mdoc-verified
2. - [x] Split `reference/config.md` into `reference/config/` — **done**: seven pages, 2,212 lines, mdoc-verified; family now at 70% with no user-facing type absent
3. - [x] ~~Split `reference/telemetry/otel/` into four pages~~ — **done differently**: the four-page split was mis-scoped (the exporters are `private[otel]`); resolved with one new page plus index additions, module now at 100%
4. - [x] `reference/htmx/response-headers.md` — **done**: 229 lines, mdoc-verified
5. - [x] `reference/schema/schema-search.md` (`SchemaSearch`, `SchemaMatch`, `TypeSearch`, `SearchTraversal`, `Updater`) — **done**: 251 lines, mdoc-verified
6. - [x] `reference/telemetry/common/any-value.md` — **done**: 90 lines, mdoc-verified
7. - [x] `reference/http-model/server-sent-event.md` — **done**: 296 lines
8. - [x] `reference/schema/reflect-transformer.md` — **done**: 140 lines, mdoc-verified
9. - [ ] `reference/telemetry/logging/log-emitter.md`

**Tier 2 — sections in existing pages**

10. - [ ] `Into` conversion matrix (schema)
11. - [ ] `SchemaExpr` operator reference (`schema-expr.md`)
12. - [ ] Migration error ADT (`migration.md`)
13. - [ ] Patch operation ADT (`patch.md`)
14. - [ ] Derivation overrides (`type-class-derivation.md`)
15. - [x] Codec layer — **done**: `http-model/schema-codecs.md`, 463 lines, mdoc-verified; it is a parallel whole-value API rather than machinery under the extension classes
16. - [ ] `Alternator` / `CanCombine` type-level rules (`endpoint/`)
17. - [x] Complete the Smithy shape catalog (`smithy.md`) — **done**: `## Shape Catalog`, 348 lines, mdoc-verified; module went from 24% to 90% with no absent types
18. - [ ] `SpanEvent` / `SpanLink` / `SamplingDecision` / data points (`telemetry/`)
19. - [ ] NIO readers and writers (`streams/reader.md`, `streams/writer.md`)
20. - [ ] `Member` ADT and owner segments (`typeid.md`)
21. - [ ] XML and YAML error types and derivers (`built-in-codecs/`)
22. - [ ] Optic and rebuild error tables (`schema-error.md`, `optics.md`)
23. - [ ] JSON Schema `$anchor` / `$ref` and the refinement types
24. - [ ] Codegen import forms, `ExtensionBlock`, `NestedType`

**Tier 3 — depth on thin pages**

25. - [ ] Expand `datastar.md` (ratio 0.18)
26. - [ ] Expand `async.md` (ratio 0.20) — the user-facing direct-style surface, not the CPS internals
27. - [x] Expand `smithy.md` — **done**: ratio 0.21 → 0.34 (533 → 881 lines)
28. - [ ] Expand `html.md` with the Scala 2 argument encoding, the `DomModifier`/`ToDom`/`ToText` extension points, and a selector-node table — CSS values done

**Tier 4 — conceptual documents**

29. - [ ] Getting Started
30. - [ ] Architecture Overview
31. - [ ] Zero-dependency / cross-platform contract
32. - [ ] Performance guide
33. - [ ] Guides for `schema`, `streams`, `http-model` + `endpoint`, hypermedia, `config`

**Visibility cleanups (instead of documentation)**

34. - [ ] Decide the public status of the `comptime` `G*` grammar ADT, `DocsSchemas`, `DerivedOptics`, `ContextEntries`, `HtmxHeaders`, and the telemetry log-record `*Kind` classifiers; tighten visibility where they are not public API

---

## Methodology

Reproducible with the script below. It walks every module's `src/main` sources, classifies each `class` / `trait` / `object` / `enum` declaration as public or internal, and diffs the public names against the identifiers found in `docs/`.

A declaration is **internal** when it is `private` or `protected`, *or when any enclosing declaration is*. That second clause matters: a type declared bare inside `private[schema] object ParamCodecSupport` is not API, and an earlier revision of this script counted six such types as public and scoped a page around them. The scanner tracks an indentation stack to get this right.

Two document sets are built: every identifier anywhere in `docs/` (yielding *absent*), and only identifiers in inline code or headings (yielding *unexplained*). The whole scanner:

```python
#!/usr/bin/env python3
"""Documentation coverage scan for zio-blocks.

Reports, per module, how much of the *public* type surface the documentation
names. A type counts as public only when neither it nor any enclosing
declaration is private or protected.
"""
import os, re, sys

DECL = re.compile(
    r'^(?P<indent>[ \t]*)'
    r'(?P<mods>(?:(?:final|sealed|abstract|implicit|case|transparent|inline|'
    r'private|protected)(?:\[[A-Za-z_]\w*\])?\s+)*)'
    r'(?:class|trait|object|enum)\s+(?P<name>[A-Za-z_]\w*)'
)
PRIVATE = re.compile(r'\b(private|protected)\b')

def scan_module(path):
    """Return (public type names, count of non-public declarations)."""
    public, nonpublic = set(), 0
    for root, _, files in os.walk(path):
        if '/src/main/' not in root + '/':
            continue
        for f in files:
            if not f.endswith('.scala'):
                continue
            # stack of (indent, enclosing_is_private) for open declarations
            stack = []
            for line in open(os.path.join(root, f), encoding='utf-8', errors='ignore'):
                m = DECL.match(line)
                if not m:
                    continue
                indent = len(m.group('indent').expandtabs(2))
                while stack and stack[-1][0] >= indent:
                    stack.pop()
                enclosed = any(p for _, p in stack)
                own = bool(PRIVATE.search(m.group('mods')))
                hidden = own or enclosed
                if hidden:
                    nonpublic += 1
                else:
                    public.add(m.group('name'))
                stack.append((indent, hidden))
    return public, nonpublic

def doc_identifiers(docs='docs', exclude=('undocumented-report.md',)):
    """All identifiers anywhere in the docs, and those in prose or headings."""
    anywhere, explained = set(), set()
    token = re.compile(r'[A-Za-z_]\w*')
    inline = re.compile(r'`([A-Za-z_]\w*)`')
    for root, _, files in os.walk(docs):
        for f in files:
            if not f.endswith(('.md', '.mdx', '.jsx')) or f in exclude:
                continue
            text = open(os.path.join(root, f), encoding='utf-8', errors='ignore').read()
            anywhere.update(token.findall(text))
            explained.update(inline.findall(text))
            for line in text.split('\n'):
                if line.startswith('#'):
                    explained.update(token.findall(line))
    return anywhere, explained

def main(modules):
    anywhere, explained = doc_identifiers()
    rows, tp = [], [0, 0, 0]
    for m in modules:
        if not os.path.isdir(m):
            continue
        public, nonpublic = scan_module(m)
        if not public and not nonpublic:
            continue
        absent = sorted(n for n in public if n not in anywhere)
        unexplained = sorted(n for n in public if n not in explained)
        rows.append((m, len(public), nonpublic, len(absent), len(unexplained), absent))
        tp[0] += len(public); tp[1] += len(absent); tp[2] += len(unexplained)
    rows.sort(key=lambda r: -r[3])
    print(f"{'module':<20}{'public':>7}{'internal':>9}{'absent':>7}{'unexpl':>7}{'cov':>6}")
    for m, p, np_, a, u, _ in rows:
        print(f"{m:<20}{p:>7}{np_:>9}{a:>7}{u:>7}{round((p-u)*100/p) if p else 0:>5}%")
    print(f"\nTOTAL public={tp[0]} absent={tp[1]} unexplained={tp[2]} "
          f"name-cov={round((tp[0]-tp[1])*100/tp[0])}% explained-cov={round((tp[0]-tp[2])*100/tp[0])}%")
    if '-v' in sys.argv:
        print()
        for m, _, _, _, _, absent in rows:
            if absent:
                print(f"{m}: {' '.join(absent)}")

MODULES = """async chunk codegen combinators config config-hocon config-json config-yaml context
datastar endpoint html htmx http-model http-model-schema markdown maybe mediatype mux openapi
otel ringbuffer schema schema-avro schema-bson schema-csv schema-messagepack schema-thrift
schema-toon schema-xml schema-yaml scope smithy sql sql-zio streams telemetry typeid""".split()

if __name__ == '__main__':
    main(MODULES)
```

Run it from the repository root:

```bash
python3 scan-coverage.py        # table
python3 scan-coverage.py -v     # table plus the absent names per module
```

Known limitations:

- **Matching is name-based.** A type whose name collides with an ordinary English word (`Default`, `Private`, `Public`, `Wildcard`, `Flag`, `Origin`, `Date`, `Host`) can be scored as covered when the page never discusses it. Both gap counts are lower bounds.
- **The `unexplained` column under-counts on well-written pages.** The writing-style rules require qualified method references (`ConfigSource#orElse`), and nested types read naturally as `Provenance.Resolved` or `KeyFormat.KebabCase` — none of which the bare-name match sees. A page that follows the style guide will show unexplained types it actually explains.
- **Indentation, not parsing, determines nesting.** The privacy stack assumes scalafmt-formatted sources, where a nested declaration is indented further than its enclosure. It would misclassify a declaration inside a Scala 3 brace-free block that was not indented, and it does not read `export` or type aliases that re-expose an internal type under a public name.
- **Public does not mean intended-as-API.** Syntax shims, compatibility layers, and macro bundles are public because they must be, not because anyone should read about them. `maybe` and `async` rank badly for exactly this reason; see *Deliberately Undocumented*.
- **Method-level coverage is not measured.** The `ratio` column is the proxy.
- **The `ratio` column is meaningless for generated code** — `mediatype` is the clearest case.

---

*Report regenerated 2026-08-26 against `main` at `01fc5099`, using the privacy-aware scanner above. 1,798 public types and 864 internal declarations across 38 modules. Earlier revisions reported 1,828 "public" types; that figure counted privately-enclosed declarations as API.*
