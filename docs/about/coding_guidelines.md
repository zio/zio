---
id: about_coding_guidelines
title:  "ZIO Coding Guidelines"
---

These are coding guidelines strictly for ZIO contributors for ZIO projects and 
not general conventions to be applied by the Scala community.

Additionally, bear in mind that, although we try to enforce these rules to the 
best of our ability, both via automated rules (scalafix) and strict reviewing 
processes, it is both possible to find existing code that does not comply to 
these rules. If that is the case, we would be extremely grateful if you could 
make a contribution, by proving a fix to said issue.

Last, but not least, these rules are continuously evolving and as such, 
refer to them once in a while when in doubt.


### Defining classes and traits

1. Value classes must be final and extend AnyVal.
2. Method extension classes must be final and extend `AnyVal`.
3. Sealed traits that are ADTs should extend `Product` and `Serializable`.
4. Regular traits and sealed trait that do not form ADTS should extend `Serializable` but not `Product`.
5. Multiple parameter classes that are ADT should be case class and not extend AnyVal
6. Traits should always extends `Serializable`. (i.e. `ZIO`)

### Final and private modifiers 

1. All methods on classes / traits are declared `final`, by default.
2. No methods on objects declared `final`.
3. No methods on final classes declared `final`.
4. All classes inside objects should be defined `final`.
5. All final classes have private constructors & constructor parameters.
6. All `vals` declared `final`, either in objects or `final classes`, if they are constant expressions and without type annotations.
7. Private `methods` and `vals` are not declared `final` since they are already final.
8. Package-private `vals` and `methods` declared `final`.

### Refactoring

1. If a class has all `final` members, the class should be declared `final` and `final` member annotations should be removed except constant expressions.
2. All type annotations should use the least powerful type alias. This means, that, let us say, a `ZIO` effect that has no dependencies but throws an arbitrary error, should be defined as `IO`.

### Naming of parameters in methods and classes

1. Partial functions are called `pf`
2. Evidences are called `ev`
3. Promise are called `p` (unless in its own class methods, in that case it is called `that`)
4. Functions are called `fn`, `fn1`, unless they bear specific meaning: `use`, `release`, ..
4. Consider _ method having more meaningful names
5. Iterable are called `in`
6. When a parameter type equals own (in a method of a trait) call it `that`
7. Never use by-name parameter unless you really know what you are doing and it makes sense. Mind the `Function[0]` extra allocation and loss of clean syntax when invoking the method.
8. Folds initial value are called `zero`

### Misc

1. GADTS should always have type annotation.
2. Type alias should always have type annotation.
3. Methods that lift pure values to effects are dangerous. Dangerous in the sense that they can potentially have dangerous side-effects. 
Such methods should have a default lazy variant and an eager variant for advanced users that are aware they absolutely do not have side-effects in their code, 
having slight gains in performance. The lazy variant should have a normal name (succeed, fail, lift, potato) and the eager variant should have a now suffix 
(succeedNow, liftNow, potatoNow) which makes it clear of its eager behaviour.      

### Code structure

1. Method alphabetization - add lazy `vals` to fix forward references.
2. Sorting of imports                            