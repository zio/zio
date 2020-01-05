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


### Extensions:

1. value classes must be final and extend AnyVal.
2. method extension classes must be final and extend AnyVal
3. Sealed traits that are ADTs should extend from Product with Serializable.
4. Regular traits and sealed trait that do not form ADTS should extend from Serializable but not from Product.
5. Multiple parameter classes that are ADT should be case class and not extend AnyVal
6. Traits should always extends serializable

### Final definitions

1. All methods on classes / traits declared final, by default
2. No methods on objects declared final
3. No methods on final classes declared final
4. All classes inside objects should be defined final
5. All final classes have private constructors & constructor parameters
6. All vals declared final, either in objects or final classes, if they are constant expressions and without type annotations.
7. Private methods/vals not declared final since they are already final
8. Package-private vals/methods declared final

### Refactor

1. If a class has all final members, class declared final & final member annotations removed
2. use least powerful type alias

### Naming of parameters

1. partial function are called `pf`
2. evidences are called `ev`
3. promise are called `p` (unless in its own class methods, in that case it is called `that`)
4. functions are called `fn`, `fn1`, unless they bear specific meaning: `use`, `release`, ..
4. consider _ method having more meaningful names
5. iterable are called `in`
6. when a parameter type equals own (in a method of a trait) call it `that`
7. please, PLEASE, only use  by name parameters if it makes sense. mind the Function[0] extra allocation and loss of clean syntax
8. folds initial value are called `zero`

### Misc

1. GADTS should have type annotation.
2. Type alias should have type annotation.
3. Methods that lift pure values to effects are dangerous. Dangerous in the sense that they can potentially have dangerous side-effects. Such methods should have a default lazy variant and an eager variant for advanced users that are aware they absolutely do not have side-effects in their code, having slight gains in performance. The lazy variant should have a normal name (succeed, fail, lift, potato) and the eager variant should have a now suffix (succeedNow, liftNow, potatoNow) which makes it clear of its eager behaviour.      

### Code structure

1. Method alphabetization - add lazy vals to fix forward references.
2. Sorting of imports                            