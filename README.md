# mat

[![Scala](https://img.shields.io/badge/Scala-3.8.x%20%7C%203.9.x-red.svg)](https://www.scala-lang.org)
[![MvnRepository](https://badges.mvnrepository.com/badge/me.cytrowski/mat/badge.svg?label=MvnRepository&color=green)](https://mvnrepository.com/artifact/me.cytrowski/mat)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

⚠️ Artifact moved from `io.github.scytrowski.mat` to `me.cytrowski.mat`

**`mat`** is a lightweight Scala 3 library for materializing types into values at compile time.

It provides a macro-based approach for turning types like tuples, literal types, or case classes into values using `inline` and `Mirror`.

The library currently cross-builds for Scala 3.8.x and 3.9.x.

---

## ✨ Features

- Materialize literal types like `5`, `"hello"`, `true`
- Recursively materialize tuples: `(1, "abc", true)`
- Recursively materialize named tuples: `(a = 1, b = "abc", c = true)`
- Materialize case classes via `Mirror.ProductOf`
- Materialize sealed trait based ADTs with exactly one materializable variant
- Materialize named tuples while preserving their labels
- Materialize supported intersection types such as `5 & Int`
- Override built-in rules with `CustomMaterialize[A]`
- Require materializable types through `Materialize[A]`
- Safe fallback with `materializeOpt[A]` returning `Option`

## ⚙️ Installation

Add `mat` to an SBT project using the cross-building `%%` operator:

```scala
libraryDependencies += "me.cytrowski" %% "mat" % "<version>"
```

The selected artifact matches the Scala version used by the project: Scala
3.8.x projects resolve the 3.8.x artifact and Scala 3.9.x projects resolve the
3.9.x artifact.

---

## 💡 Examples

### Materialize a literal value

```scala
import me.cytrowski.mat.*

val x: 42 = materialize[42]
// x: 42
```

### Materialize a tuple

```scala
import me.cytrowski.mat.*

val x: (true, 'd', "abc") = materialize[(true, 'd', "abc")]
// x: (true, 'd', "abc")
```

### Materialize a named tuple

```scala
import me.cytrowski.mat.*

val x: (a: true, b: 'd', c: "abc") = materialize[(a: true, b: 'd', c: "abc")]
// x: (a = true, b = 'd', c = "abc")
```

### Materialize a case object

```scala
import me.cytrowski.mat.*

case object SomeObject

val x: SomeObject.type = materialize[SomeObject.type]
// x: SomeObject
```

### Materialize a case class

```scala
import me.cytrowski.mat.*

case class SomeClass[A](a: A)

val x: SomeClass[15] = materialize[SomeClass[15]]
// x: SomeClass(15)
```

### Try materialization without a compilation error

`materializeOpt[A]` uses the same derivation rules as `materialize[A]`, but returns
`None` when `A` cannot be materialized.

Its public return type is intentionally `Any` so that the transparent inline
macro can preserve the most precise result type at the call site, such as
`Some[42]` or `None.type`.

```scala
import me.cytrowski.mat.*

val supported: Option[42] = materializeOpt[42]
// supported: Some(42)

val unsupported: Option[String] = materializeOpt[String]
// unsupported: None
```

### Materialize a singleton ADT variant

```scala
import me.cytrowski.mat.*

sealed trait SomeADT

case object SingletonVariant extends SomeADT

val x: SingletonVariant.type = materialize[SomeADT]
// x: SingletonVariant
```

### Materialize a parameterized ADT

For parameterized ADTs, the macro considers only variants compatible with the
requested result type. This makes GADT-like definitions possible:

```scala
import me.cytrowski.mat.*

sealed trait Expr[A]
case object IntExpr extends Expr[Int]
case object BooleanExpr extends Expr[Boolean]

val intExpr: IntExpr.type = materialize[Expr[Int]]
// intExpr: IntExpr
```

The requested type must still have exactly one materializable candidate. If
multiple variants match, materialization is rejected as ambiguous. A concrete
variant that cannot be materialized also blocks the sum instead of being
silently ignored.

The same rule applies to recursive branches. They are detected and reported as
unsupported rather than expanded indefinitely:

```scala
import me.cytrowski.mat.*

sealed trait Tree
case object EmptyTree extends Tree
case class Branch(next: Tree) extends Tree

val tree: Option[Tree] = materializeOpt[Tree]
// tree: None
```

### Provide custom materialization logic

`CustomMaterialize[A]` takes precedence over the built-in materialization rules used by the macro.

```scala
import me.cytrowski.mat.*

sealed abstract class SomeClass

object SomeClass:
  val instance: SomeClass = new SomeClass {}

given CustomMaterialize[SomeClass]:
  override type Out = SomeClass
  override def apply(): SomeClass = SomeClass.instance

val x: SomeClass = materialize[SomeClass]
// x: SomeClass.instance
```

`CustomMaterialize[A]` has priority over all built-in derivation rules. This is
useful when a type has a built-in representation but the application needs a
different value.

### Provide `Materialize[A]` explicitly

`Materialize` is sealed, but external code can provide evidence using
`Materialize.fromValue`:

```scala
import me.cytrowski.mat.*

case class Configuration(name: String)

given Materialize[Configuration] =
  Materialize.fromValue(Configuration("default"))

val configuration: Configuration = materialize[Configuration]
```

An explicit `Materialize[A]` in scope is used before the macro tries to derive
a new instance.

### Require a materializable type

`Materialize[A]` is derived by the macro and can be used as a context bound.
Inside such a method, `materialize[A]` uses the evidence supplied by the
caller.

```scala
import me.cytrowski.mat.*

def materializeValue[A: Materialize]: A =
  materialize[A]

def doSomethingWithMaterializableType[A: Materialize](value: A): A =
  value
```

The evidence preserves a more precise output type when it is available:

```scala
val evidence: Materialize.Aux[SomeADT, SingletonVariant.type] =
  summon[Materialize[SomeADT]]
```

### Diagnostics

When `materialize[A]` cannot derive a value, compilation fails with a diagnostic
that explains where derivation stopped. For example, an unsupported field in a
product is reported together with its field name and type. Nested products and
tuples preserve the complete path to the unsupported value. Sum diagnostics
also identify the rejected variant and continue with the reason for its
failure.

Recursive products and recursive ADT branches are detected during derivation.
They return `None` from `materializeOpt[A]` and produce a regular diagnostic
from `materialize[A]`; derivation does not recurse indefinitely.

Use `materializeOpt[A]` when failure is expected and should be represented as
`None` instead of a compilation error.

### Supported forms

The built-in derivation supports:

- literal types with a `ValueOf` instance,
- intersections when one component produces a value satisfying the full intersection,
- unions with exactly one materializable variant,
- tuples and named tuples whose elements are supported,
- case-class products whose fields are supported,
- sums with exactly one materializable candidate, including nested and
  parameterized ADTs,
- custom values supplied through `CustomMaterialize[A]`.

Types outside these forms, such as ordinary abstract types, sums with multiple
materializable candidates, sums with a concrete rejected variant, recursive
branches, or ambiguous unions, are rejected by `materialize[A]` and return
`None` from `materializeOpt[A]`.

### Cross-building and tests

The project produces artifacts for both supported Scala versions. During
development, the complete test suite can be run for every configured Scala
version with:

```shell
sbt +test
```

The repository also contains isolated compile-time stress benchmarks for the
macro. They derive tuples, products and unions with 16, 24 or 32 variants.
Run one case and one size independently from the regular tests with:

```shell
sbt --batch -Dmat.benchmark=tuple -Dmat.size=16 \
  "Benchmark / clean" "Benchmark / compile"
```

Use `tuple`, `product` or `union` for `mat.benchmark` and `16`, `24` or `32`
for `mat.size`. To measure another supported Scala version explicitly, prepend
its version:

```shell
sbt --batch -Dmat.benchmark=union -Dmat.size=24 "++3.9.0" \
  "Benchmark / clean" "Benchmark / compile"
```

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).

You are free to use, copy, modify, and distribute it with attribution.
