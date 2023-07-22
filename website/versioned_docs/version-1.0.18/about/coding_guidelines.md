---
id: about_coding_guidelines
title: "ZIO Coding Guidelines"
---

These are coding guidelines strictly for ZIO contributors for ZIO projects and 
not general conventions to be applied by the Scala community at large.

Additionally, bear in mind that, although we try to enforce these rules to the 
best of our ability, both via automated rules (scalafmt) and strict reviewing 
processes, it is both possible to find existing code that does not comply to 
these rules. If that is the case, we would be extremely grateful if you could 
make a contribution, by providing a fix to said issue.

Last, but not least, these rules are continuously evolving and as such, 
refer to them once in a while when in doubt.

### Defining classes and traits

1. Value classes must be final and extend `AnyVal`. 
This is done to avoid allocating runtime objects; 

2. Method extension classes must be final and extend `AnyVal`;

3. Avoid overloading standard interfaces. When creating services avoid using the same names as well known standard interfaces.
Example: Instead of having a service `Random` with methods `nextLong(n)` and `nextInt(n)` consider choosing something like 
`nextLongBounded(n)` and `nextIntBounded(n)`.

4. Sealed traits that are ADTs (Algebraic data types) should extend `Product` and `Serializable`. 
This is done to help the compiler infer types;

5. Regular traits and sealed trait that do not form ADTs should extend `Serializable` but not `Product`;

6. Traits should always extend `Serializable`. (e.g. `ZIO`).

### Final and private modifiers 

1. All methods on classes / traits are declared `final`, by default;

2. No methods on objects declared `final`, because they are `final` by default;

3. No methods on final classes declared `final`, because they are `final` by default;

4. All classes inside objects should be defined `final`, because otherwise they could still be extended;

5. In general, classes that are not case classes have their constructors & constructor parameters private. 
   Typically, it is not good practice to expose constructors and constructor parameters but exceptions apply (i.e. `Assertion` and `TestAnnotation`);

6. All `vals` declared `final`, even in objects or `final classes`, if they are constant expressions and without type annotations;

7. Package-private `vals` and methods should be declared `final`.

### Refactoring

1. If a class has all its members `final`, the class should be declared `final` and `final` member annotations should be removed except constant expressions;

2. All type annotations should use the least powerful type alias. This means, that, let us say, a `ZIO` effect that has 
   no dependencies but throws an arbitrary error, should be defined as `IO`.
3. Use `def` in place of `val` for an abstract data member to avoid `NullPointerException` risk.

### Understanding naming of parameters or values

ZIO code often uses the following naming conventions, and you might be asked to change method parameters to follow these conventions. This guide can help you understand where the names come from. 
Naming expectations can be helpful in understanding the role of certain parameters without even glancing at its type signature when reading code or class/method signatures.

1. Partial functions have a shortened name `pf`;

2. In ZIO implicit parameters are often used as compiler evidences;
   These evidences help you, as a developer, prove something to the compiler (at compile time), and they have the ability to add constraints to a method;
   They are typically called `ev` if there is only one. Or `ev1`, `ev2`... if more than one;
   
3. Promises are called `p` (unless in its own class methods, in that case it is called `that`, like point 8 defines);

4. Functions are called `fn`, `fn1`, unless they bear specific meaning: `use`, `release`;

5. ZIO effects are called `f`, unless they bear specific meaning like partially providing environment: `r0`;

6. Consider methods ending with `_` having more meaningful names;

7. Iterable are called `in`;

8. When a parameter type equals own (in a method of a trait) call it `that`;

9. Be mindful of using by-name parameters. Mind the `Function[0]` extra allocation and loss of clean syntax when invoking the method.
   Loss of syntax means that instead of being able to do something like `f.flatMap(ZIO.success)` you require to explicitly do `f.flatMap(ZIO.success(_))`;
   
10. Fold or fold variants initial values are called `zero`.

### Understanding naming of methods

ZIO goes to great lengths to define method names that are intuitive to the library user. Naming is hard!!! 
This section will attempt to provide some guidelines and examples to document, guide and explain naming of methods in ZIO.

1. Methods that lift pure values to effects are dangerous. Dangerous in the sense that they can potentially have dangerous side-effects. 
   Such methods should have a default lazy variant and an eager variant for advanced users that are aware they absolutely do not have side-effects in their code, 
   having slight gains in performance. The lazy variant should have a normal name (succeed, fail, die, lift) and the eager variant should have a `Now` suffix 
   (succeedNow, failNow, dieNow, liftNow) which makes it clear of its eager behaviour;

2. Methods that have the form of `List#zip` are called `zip`, and have an alias called `<*>`. The parallel version, if applicable, has the name `zipPar`, with an alias called `<&>`;

3. Methods that are intended to capture side-effects, convert them into functional effects, should be prefixed by effect*. For example, `ZIO.effect`;

4. The dual of zip, which is trying either a left or right side, producing an Either of the result, should be called `orElseEither`, with alias `<+>`. 
   The simplified variant where both left and right have the same type should be called `orElse`, with alias `<>`;
    
5. Constructors for a data type `X` that are based on another data type `Y` should be placed in the companion object `X` and named `fromY`. 
   For example, `ZIO.fromOption`, `ZStream.fromEffect`;
   
6. Parallel versions of methods should be named the same, but with a `Par` suffix. Parallel versions with a bound on parallelism should use a `ParN` suffix;

7. `Foreach` should be used as the default traverse operation, with `traverse` retained as an alias for programmers with an FP background. For example, `ZIO.foreach`.
   
### Type annotations

ZIO goes to great lengths to take advantage of the scala compiler in varied ways. Type variance is one of them. 
The following rules are good to have in mind when adding new `types`, `traits` or `classes` that have either covariant or contravariant types.

1. Generalized ADTs should always have type annotation. (i.e. `final case class Fail[+E](value: E) extends Cause[E]`);
   
2. Type alias should always have type annotation. Much like in Generalized ADTs defining type aliases should carry the type annotations 
   (i.e. `type IO[+E, +A] = ZIO[Any, E, A]`).

When defining new methods, keep in mind the following rules:

1. Accept the most general type possible. For example, if a method accepts a collection, prefer `Iterable[A]` to `List[A]`.
2. Return the most specific type possible, e.g., prefer `UIO[Unit]` to `UIO[Any]`.

### Method alphabetization

In general the following rules should be applied regarding method alphabetization. 
To fix forward references of values we recommend the programmer to make them lazy (`lazy val`).
Operators are any methods that only have non-letter characters (i.e. `<*>` , `<>`, `*>`).

1. Public abstract defs / vals listed first, and alphabetized, with operators appearing before names.

2. Public concrete defs / vals listed second, and alphabetized, with operators appearing before names.

3. Private implementation details listed third, and alphabetized, with operators appearing before names.

### Scala documentation

It is strongly recommended to use scala doc links when referring to other members. 
This both makes it easier for users to navigate the documentation and enforces that the references are accurate.
A good example of this are `ZIO` type aliases that are extremely pervasive in the codebase: `Task`, `RIO`, `URIO` and `UIO`.
To make it easy for developers to see the implementation scala doc links are used, for example:

```
  /**
   * @see See [[zio.ZIO.absolve]]
   */
  def absolve[R, A](v: RIO[R, Either[Throwable, A]]): RIO[R, A] =
    ZIO.absolve(v)
```

