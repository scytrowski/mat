# mat

[![Scala](https://img.shields.io/badge/Scala-3.8.x%20%7C%203.9.x-red.svg)](https://www.scala-lang.org)
[![Maven Central](https://img.shields.io/maven-central/v/me.cytrowski/mat_3?label=Maven%20Central&color=green)](https://central.sonatype.com/artifact/me.cytrowski/mat_3/1.0.0)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

⚠️ Artifact moved from `io.github.scytrowski.mat` to `me.cytrowski.mat`

See [CHANGELOG.md](CHANGELOG.md) for the release history.

**`mat`** is a lightweight Scala 3 library for materializing types into values at compile time.

It provides a type-directed, macro-based way to turn types such as literal
types, tuples, case classes and closed ADTs into values using `inline` and
`Mirror`.

This is useful when a domain is described in the type system and each type has
one meaningful value that can be constructed from that description. Examples
include typed configuration fragments, protocol values, small schema objects
and compile-time test data.

`mat` does not inspect arbitrary runtime classes, deserialize runtime input or
choose a value based on a runtime condition. The requested type must contain
enough compile-time information for the macro to derive one unambiguous value.

## ⚠️ Compile-time and macro trade-offs

Materialization is performed by a Scala 3 quoted macro at the call site:

- `materialize[A]` reports a compilation error when derivation fails.
- `materializeOpt[A]` turns a failed derivation into `None`, but the type is
  still analyzed during compilation.
- `materializeOpt[A]` avoids the library's derivation error, but unusual types
  can still produce compiler warnings during type reduction.
- The generated code uses ordinary Scala expressions and does not use runtime
  reflection. The resulting constructors or mirror operations still execute
  normally when the compiled program runs.
- Deeply nested products, tuples and sums can increase compilation time and
  generate larger code. This cost is paid during compilation, not as a runtime
  search for a value.
- Macro behavior depends on the Scala compiler. This project currently tests
  Scala 3.8.x and 3.9.x; compiler diagnostics and edge-case behavior should be
  verified when upgrading Scala.
- Generic methods need a concrete `A` at the call site or an available
  `Materialize[A]` context bound. Materialization is not a replacement for
  constructing values that depend on runtime data.

The library currently supports Scala 3.8.x and 3.9.x. Both versions are tested
and packaged in CI. Since these Scala versions share the `_3` binary artifact
suffix, a release publishes one artifact built with the oldest supported
compiler, Scala 3.8.4; both Scala versions resolve that artifact.

---

## ✨ Features

- Materialize literal types like `5`, `"hello"`, `true`
- Recursively materialize tuples: `(1, "abc", true)`
- Recursively materialize named tuples: `(a = 1, b = "abc", c = true)`
- Materialize case classes via `Mirror.ProductOf`
- Materialize nested and parameterized sealed-trait ADTs with exactly one
  materializable variant
- Materialize named tuples while preserving their labels
- Materialize supported intersection types such as `5 & Int`
- Override built-in rules with explicit `Materialize[A]` evidence
- Require materializable types through `Materialize[A]`
- Safe fallback with `materializeOpt[A]` returning `Option`

## ⚙️ Installation

Add `mat` to an SBT project using the cross-building `%%` operator:

```scala
libraryDependencies += "me.cytrowski" %% "mat" % "<version>"
```

The `%%` operator selects the Scala 3 binary artifact for both Scala 3.8.x and
3.9.x projects.

---

## 🧭 API at a glance

| API | Use it for | Behavior when it fails |
| --- | --- | --- |
| `materialize[A]` | Return the materialized value with its most precise available type | Compilation error with a diagnostic |
| `materializeOpt[A]` | Try materialization without failing compilation | Returns `None` without a derivation error |
| `Materialize[A]` | Reuse evidence, or supply an application-specific value and type | Evidence cannot be derived |

Both inline methods are transparent. Their public signature is `Any` so the
macro can preserve a more precise type at each call site; normal usage should
assign the result to the expected type or let Scala infer it.

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

`fromValue` accepts its argument by name, so the expression is evaluated each
time the returned evidence is applied. This is useful for explicit evidence,
but it does not make the value a compile-time constant.

An explicit `Materialize[A]` in scope is used before the macro tries to derive
a new instance, including while deriving nested products, tuples and sums.

When the materialized value has a more precise type than `A`, provide that
type through `Materialize.Aux`:

```scala
import me.cytrowski.mat.*

given Materialize.Aux[Int, 5] =
  Materialize.fromValue[Int, 5](5)

val port: 5 = materialize[Int]
```

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

The built-in derivation supports the following forms:

- literal types with a `ValueOf` instance, including `5`, `"hello"`, `true`
  and `'d`, as well as the unit value `()`;
- intersections when one component produces a value that satisfies the full
  intersection, such as `5 & Int`;
- unions when exactly one distinct branch can be materialized;
- tuples and named tuples whose elements can all be materialized;
- case-class products whose fields can all be materialized;
- enums and sealed-trait sums with exactly one materializable candidate,
  including nested and parameterized ADTs;
- explicitly supplied `Materialize[A]` evidence.

The rules are intentionally conservative:

- An intersection is accepted only when the resulting value satisfies every
  component. A value that satisfies only one side is not enough.
- Repeated union branches are deduplicated, so `5 | 5` is equivalent to `5`.
  If multiple distinct branches succeed, the union is ambiguous and rejected.
  Unsupported branches do not block a union when exactly one distinct branch
  succeeds.
- For a sum, multiple successful candidates are ambiguous. A concrete
  rejected variant, or a nested branch with known children that fails, also
  rejects the whole sum instead of being silently ignored. Empty sealed
  branches can be ignored while searching for a candidate.
- Parameterized sums are filtered by compatibility with the requested type.
  This enables GADT-like definitions such as `Expr[Int]`, but the result must
  still have exactly one candidate.

Types without a supported representation are rejected by `materialize[A]` and
return `None` from `materializeOpt[A]`. This includes abstract types, `Any`,
`Nothing`, refined types, opaque types outside their defining scope, recursive
products or sums, ambiguous sums and ambiguous unions. For example,
`Option[5]` has both `Some[5]` and `None` as candidates, while
`Option[Nothing]` contains an unsupported `Some[Nothing]` branch; both cases
are rejected.

### Cross-building and tests

The project compiles and packages the library for both supported Scala
versions. The release publishes the single shared `_3` artifact described
above. During development, the complete test suite can be run for every
configured Scala version with:

```shell
sbt --batch +test
```

Scaladoc is generated only by the release workflow, after the library has been
published. The workflow uses Scala 3.8.4, which is the compiler used for the
published artifact, and adds the generated HTML documentation to a directory
named after the release tag in the `scaladoc` branch:

```text
scaladoc/
├── v1.0.0/
├── v1.1.0/
└── v1.2.0/
```

Existing version directories are preserved, so downstream websites or other
documentation tooling can fetch documentation for a specific release. The
`scaladoc` branch is an artifact store only; the release workflow does not
deploy GitHub Pages. A separate documentation project can consume the branch,
generate `/projects/mat/docs/versions.json`, and deploy the documentation to
the target hosting provider. Generated Scaladoc pages are configured to load
that dictionary from this fixed path.

To preview the documentation locally, run:

```shell
sbt --batch ++3.8.4 vendorScaladocAssets
```

The local HTML documentation is written to `target/scala-3.8.4/api`.
The task also scans the generated HTML and CSS for external assets, downloads
them into the generated `assets/` directory and rewrites the files to use
local copies. This includes JavaScript, stylesheets, images, fonts and other
resources referenced by asset-bearing HTML tags. The published documentation
is therefore independent of external CDNs.

The repository also contains isolated compile-time stress benchmarks for the
macro. They derive tuples, products and unions with 16, 24 or 32 elements.

The release workflow runs all nine benchmark cases three times on Scala 3.8.4
and compares their median times with the latest successful release. A 25% and
1-second regression threshold is used. The first release creates the baseline;
a later release that exceeds the threshold is stopped before publishing library
artifacts.
Reports are retained as workflow artifacts and on the `benchmarks` branch:

```text
benchmarks/
├── latest/results.tsv
├── v1.0.0/results.tsv
└── v1.1.0/results.tsv
```

The benchmark measures the incremental compiler task for each isolated
benchmark. The three repetitions for each case run in one SBT process to avoid
repeating SBT startup overhead; the recorded value is their median. It is
intended to detect significant compiler regressions, not to provide
laboratory-grade performance measurements.

CI runs the coverage check with Scala 3.8.4, the compiler used for the
published artifact. It enforces statement and branch thresholds:

```shell
sbt --batch ';++3.8.4;coverage;test;coverageReport'
```

The HTML report is generated under
`target/out/jvm/scala-3.8.4/mat/coverage-report`.
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
