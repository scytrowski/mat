package io.github.scytrowski.mat

import scala.compiletime.{error, summonFrom}
import scala.deriving.Mirror

/** Attempts to materialize type `A`
  *
  * In case of failure a compilation error is raised
  */
transparent inline def materialize[A]: Any =
  inline materializeOpt[A] match
    case Some(a) => a
    case None    => error("Type cannot be materialized")

/** Safely attempts to materialize type `A`
  */
transparent inline def materializeOpt[A]: Any =
  summonFrom {
    case mat: Materialize[A] => Some(mat())
    case _                   => None
  }

/** Evidence that a type `A` can be materialized into a value at compile time.
  *
  * A type `A` is considered materializable if it satisfies one of the following
  * conditions:
  *
  *   - It is a constant type (i.e., there exists a `ValueOf[A]`)
  *   - It is a tuple (`Tuple`) composed entirely of materializable elements
  *   - It is a product type (e.g., a case class) whose fields are all
  *     materializable
  *   - It is a sum type (e.g., a sealed trait or enum) with exactly one
  *     materializable variant
  *   - There exists a
  *     {@link io.github.scytrowski.mat.CustomMaterialize CustomMaterialize[A]}
  *
  * The `apply()` method returns a value of type `Out`, which is guaranteed to
  * be a subtype of `A`.
  */
sealed trait Materialize[A]:
  /** The resulting type of the materialized value. */
  type Out <: A

  /** Materializes a value of type `Out`, satisfying the structure of `A`. */
  def apply(): Out

object Materialize extends LowPriorityMaterialize:
  type Aux[A, O <: A] = Materialize[A] { type Out = O }

  def apply[A](using mat: Materialize[A]): Materialize[A] = mat

  given customMaterialize
      : [A, O <: A] => (mat: CustomMaterialize.Aux[A, O]) => Materialize[A]:
    override type Out = O
    override def apply(): O = mat()

sealed trait LowPriorityMaterialize extends LowerPriorityMaterialize:
  given materializeConstValue: [A: ValueOf] => Materialize[A]:
    override type Out = A

    override def apply(): A = valueOf[A]

  given materializeEmptyTuple: Materialize[EmptyTuple]:
    override type Out = EmptyTuple

    override def apply(): EmptyTuple = EmptyTuple

  given materializeTuple: [H, HMat <: H, T <: Tuple, TMat <: T]
    => (matHead: Materialize.Aux[H, HMat])
    => (matTail: Materialize.Aux[T, TMat]) => Materialize[HMat *: TMat]:
    override type Out = HMat *: TMat

    override def apply(): HMat *: TMat = matHead() *: matTail()

sealed trait LowerPriorityMaterialize:
  given materializeProduct: [A] => (productOf: Mirror.ProductOf[A])
    => Materialize[productOf.MirroredElemTypes] => Materialize[A]:
    override type Out = A

    override def apply(): A =
      productOf.fromTuple(Materialize[productOf.MirroredElemTypes]())

  given materializeSingletonSum
      : [A, S <: A, SMat <: S] => SingletonSum.Aux[A, S]
        => (matSingleton: Materialize.Aux[S, SMat]) => Materialize[A]:
    override type Out = SMat

    override def apply(): SMat = matSingleton()
