package me.cytrowski.mat

import scala.compiletime.{error, summonFrom}

/** Attempts to materialize type `A`.
  *
  * In case of failure a compilation error is raised.
  */
transparent inline def materialize[A]: Any =
  summonFrom {
    case mat: Materialize[A] => mat()
    case _                   => error("Type cannot be materialized")
  }

/** Safely attempts to materialize type `A`. */
transparent inline def materializeOpt[A]: Any =
  ${ MaterializeMacros.materializeOptImpl[A] }

/** Evidence that type `A` can be materialized.
  *
  * Instances are derived by the materialization macro. The evidence can be used
  * independently when an API should require a materializable type.
  */
sealed trait Materialize[A]:
  type Out <: A

  def apply(): Out

object Materialize:
  type Aux[A, O <: A] = Materialize[A] { type Out = O }

  def apply[A](using materialize: Materialize[A]): Materialize[A] =
    materialize

  def fromValue[A, O <: A](value: => O): Aux[A, O] =
    new MaterializeValue[A, O](value)

  transparent inline given derived[A]: Materialize[A] =
    ${ MaterializeMacros.materializeInstanceImpl[A] }

  private final class MaterializeValue[A, O <: A](value: => O)
      extends Materialize[A]:
    type Out = O
    def apply(): O = value
