package me.cytrowski.mat

/** Materializes type `A` at compile time.
  *
  * Resolution uses the following order:
  *
  *   1. an explicit `Materialize[A]` instance in scope,
  *   2. a `CustomMaterialize[A]` instance in scope,
  *   3. the built-in derivation rules.
  *
  * If no rule succeeds, compilation fails with a diagnostic. The method is
  * transparent and inline, so the expression keeps the most precise result type
  * produced by the selected rule even though its public signature is `Any`.
  */
transparent inline def materialize[A]: Any =
  ${ MaterializeMacros.materializeImpl[A] }

/** Safely attempts to materialize type `A` at compile time.
  *
  * It follows the same resolution order as [[materialize]]. It returns `Some`
  * when materialization succeeds and `None` when no rule can produce a value; a
  * failed derivation does not produce a compilation error.
  *
  * The method is transparent and inline, so successful calls retain the precise
  * materialized type (for example, `Some[42]`) and failed calls have type
  * `None.type` at the call site. The public signature is intentionally `Any` to
  * make that precision possible.
  */
transparent inline def materializeOpt[A]: Any =
  ${ MaterializeMacros.materializeOptImpl[A] }

/** Evidence that type `A` can be materialized.
  *
  * Instances can be derived by the materialization macro or supplied explicitly
  * with [[Materialize.fromValue]]. The evidence can be used independently when
  * an API should require a materializable type.
  *
  * The precise materialized type is exposed through `Out`, which is a subtype
  * of `A`. Calling [[apply]] creates the materialized value.
  *
  * @tparam A
  *   the type to materialize
  */
sealed trait Materialize[A]:
  /** The precise type returned by [[apply]]. */
  type Out <: A

  /** Produces a materialized value. */
  def apply(): Out

/** Built-in operations and evidence for [[Materialize]]. */
object Materialize:
  /** Type alias that fixes the precise output type of materialization evidence.
    */
  type Aux[A, O <: A] = Materialize[A] { type Out = O }

  /** Returns materialization evidence available through implicit search. */
  def apply[A](using materialize: Materialize[A]): Materialize[A] =
    materialize

  /** Creates materialization evidence backed by a by-name value.
    *
    * The argument is evaluated each time the returned evidence is applied.
    */
  def fromValue[A, O <: A](value: => O): Aux[A, O] =
    new MaterializeValue[A, O](value)

  /** Derives materialization evidence using the built-in macro rules. */
  transparent inline given derived[A]: Materialize[A] =
    ${ MaterializeMacros.materializeInstanceImpl[A] }

  private final class MaterializeValue[A, O <: A](value: => O)
      extends Materialize[A]:
    type Out = O
    def apply(): O = value
