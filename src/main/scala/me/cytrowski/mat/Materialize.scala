package me.cytrowski.mat

import scala.compiletime.summonFrom

/** Attempts to materialize type `A`.
  *
  * An existing `Materialize[A]` in scope is used first. If no evidence is
  * available, the built-in macro derivation is attempted. In case of failure a
  * compilation error with a diagnostic is raised.
  */
transparent inline def materialize[A]: Any =
  summonFrom {
    case mat: Materialize[A] => mat()
    case _                   => materializeError[A]
  }

private inline def materializeError[A]: Any =
  ${ MaterializeMacros.materializeErrorImpl[A] }

/** Safely attempts to materialize type `A`.
  *
  * Returns `Some` when the type can be materialized and `None` otherwise. A
  * failed derivation does not produce a compilation error.
  */
transparent inline def materializeOpt[A]: Any =
  ${ MaterializeMacros.materializeOptImpl[A] }

/** Evidence that type `A` can be materialized.
  *
  * Instances are derived by the materialization macro. The evidence can be used
  * independently when an API should require a materializable type.
  *
  * The precise materialized type is exposed through `Out`, which is a subtype
  * of `A`.
  */
sealed trait Materialize[A]:
  type Out <: A

  def apply(): Out

object Materialize:
  type Aux[A, O <: A] = Materialize[A] { type Out = O }

  /** Returns materialization evidence available in the current scope. */
  def apply[A](using materialize: Materialize[A]): Materialize[A] =
    materialize

  /** Creates materialization evidence backed by a by-name value. */
  def fromValue[A, O <: A](value: => O): Aux[A, O] =
    new MaterializeValue[A, O](value)

  /** Derives materialization evidence using the built-in macro rules. */
  transparent inline given derived[A]: Materialize[A] =
    ${ MaterializeMacros.materializeInstanceImpl[A] }

  private final class MaterializeValue[A, O <: A](value: => O)
      extends Materialize[A]:
    type Out = O
    def apply(): O = value
