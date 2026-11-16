# Scala 2 vs Scala 3

> How izumi-reflect differs between Scala 2 and Scala 3: higher-kinded tags, Tag.auto, union types and macro settings.

`izumi-reflect` has one API for Scala 2.12, 2.13 and 3. The macros that create tags are separate implementations for Scala 2 and Scala 3, though, so some details differ. This page lists the differences that affect code you write; everything not listed here works the same way on all versions.

## Summary

The differences fall into three groups: how tags for higher-kinded types are declared, which types and constructors exist, and which build settings the macros read.

| Area                                       | Scala 2                                                   | Scala 3                           |
| ------------------------------------------ | --------------------------------------------------------- | --------------------------------- |
| Kinds accepted by `Tag`                    | Proper types only                                         | Any kind (`Tag[T <: AnyKind]`)    |
| `TagK`, `TagKK`, ... `TagK22`, `TagT`, ... | Aliases of `HKTag` with a structural type                 | Aliases of `Tag`                  |
| `HKTag`                                    | A separate trait                                          | An alias of `Tag`                 |
| `Tag.auto.T`                               | Macro that infers the `HKTag` type for any kind           | An alias of `Tag`                 |
| `LTag.StrongHK`, `LTag.WeakHK`             | Separate case classes                                     | Aliases of `LTag` and `LTag.Weak` |
| Union types                                | Not in the language                                       | Supported, with `Tag.unionTag`    |
| `appliedTagNonPos`                         | On `HKTag`                                                | On `Tag` and `HKTag`              |
| Compile-time debug output                  | Enabled by `-Dizumi.reflect.debug.macro.rtti=true`        | Not available                     |
| Compile-time tag cache setting             | `-Xmacro-settings:izumi.reflect.rtti.cache.compile=false` | Not available                     |
| Tags from `scala-reflect` types at runtime | `LightTypeTagImpl.makeLightTypeTag` (JVM only)            | Not available                     |

## Tags for Higher-Kinded Types

The biggest difference is how a tag for a type constructor is declared. Code that only uses context bounds such as `F[_]: TagK` compiles unchanged on both versions, so most applications never notice it.

On Scala 3, `Tag` is poly-kinded, and `TagK` is just another name for it. On Scala 2, `Tag` only accepts proper types, so each kind needs its own type, encoded as `HKTag` of a structural type that applies the type constructor to fresh parameters:

  

**Scala 2**

```scala
// from the izumi.reflect package object
type TagK[K[_]] = HKTag[{ type Arg[A] = K[A] }]
type TagKK[K[_, _]] = HKTag[{ type Arg[A, B] = K[A, B] }]
type TagT[K[_[_]]] = HKTag[{ type Arg[A[_]] = K[A] }]
```

  
  

**Scala 3**

```scala
// from the izumi.reflect package
type TagK[K[_]] = Tag[K]
type TagKK[K[_, _]] = Tag[K]
type TagT[K[_[_]]] = Tag[K]
```

  

This has two visible effects. First, on Scala 3 `Tag[Option]` compiles and equals `TagK[Option]`, while on Scala 2 you must write `TagK[Option]`. Second, for a kind that has no predefined alias, Scala 3 code writes `Tag[K]` directly, while Scala 2 code uses the `Tag.auto.T` macro or declares its own `HKTag` alias:

  

**Scala 2**

```scala
import izumi.reflect.{HKTag, Tag}

// let the macro pick the HKTag type for the kind of K
def viaAuto[K[_, _, _, _, _]: Tag.auto.T]: Tag.auto.T[K] = implicitly[Tag.auto.T[K]]

// or spell it out with HKTag
type TagK5[K[_, _, _, _, _]] = HKTag[{ type Arg[A, B, C, D, E] = K[A, B, C, D, E] }]
```

  
  

**Scala 3**

```scala
import izumi.reflect.Tag

def direct[K[_, _, _, _, _]: Tag]: Tag[K] = Tag[K]
```

  

On Scala 3, `Tag.auto.T[X]` is an alias of `Tag[X]`, so code that cross-builds for both versions can use `Tag.auto.T` everywhere. On Scala 2, prefer `Tag.auto.T` as a context bound over your own alias of it: a known issue makes the macro fail for some kinds when `Tag.auto.T` is hidden behind a type alias.

## Build Settings Read by the Macros

On Scala 2, the system property `izumi.reflect.debug.macro.rtti` prints what the macro does while it creates each tag, and the compiler option `-Xmacro-settings:izumi.reflect.rtti.cache.compile=false` turns off the cache of tags computed during compilation. The Scala 3 macros read neither: their debug output is switched off in the library code, and they have no cache setting. The runtime debug output of subtype checks works the same way on both versions. See [Debugging](debugging.md) for the details.

## Interoperating with scala-reflect

On Scala 2, `izumi-reflect` can build a `LightTypeTag` from a `scala-reflect` type at runtime with `izumi.reflect.macrortti.LightTypeTagImpl.makeLightTypeTag`. This helps when migrating code that has a `TypeTag` but no `Tag`. It needs the runtime universe of `scala-reflect`, which exists only on the JVM, and `scala-reflect` on the classpath, which `izumi-reflect` itself only declares as a `Provided` dependency. Scala 3 has no `scala-reflect` and no such method. The [migration guide](../guides/migrate-from-typetag.md) shows how to use it.

## Known Version-Specific Gaps

The library's test suite tracks a few cases where one version gives a less precise result than the other. You are unlikely to meet them in application code:

- On Scala 3, `AnyTag#closestClass` is not correct for a value class whose field is a vararg parameter, such as `case class Args(args: String*) extends AnyVal` ([#224](https://github.com/zio/izumi-reflect/issues/224)).
- On Scala 3, the compiler simplifies an intersection whose member is a supertype of another member, so `Tag[Child with Parent]` equals `Tag[Child]`. The same type built in generic code, as `Tag[T with Parent]` with `T = Child`, keeps both members, so the two tags are not equal, although each is a subtype of the other. Call `LightTypeTag#removeIntersectionTautologies` on both before comparing them.
- On Scala 2, tags for types with a singleton-type prefix, such as projections from `x.type`, are not always correct.
- On Scala 2, an intersection of two path-dependent abstract types, each with its own tag, is not supported: `Tag[this.T with that.T]` does not compile, and `Tag[T with that.T]` compiles but gives a tag that is not equal to the tag of the concrete intersection.

The [Limitations](../index.md#limitations) section of the introduction lists the limits that apply to all versions.
