package com.github.scytrowski.mat

import scala.compiletime.{error, summonFrom}
import scala.deriving.Mirror

transparent inline def materialize[A]: Any =
  inline materializeOpt[A] match
    case Some(a) => a
    case None => error("Type cannot be materialized")

transparent inline def materializeOpt[A]: Any =
  summonFrom {
    case mat: Materialize[A] => Some(mat())
    case _ => None
  }

sealed trait Materialize[A]:
  type Out <: A
  def apply(): Out

object Materialize extends LowPriorityMaterialize:
  type Aux[A, O <: A] = Materialize[A] { type Out = O }

  def apply[A](using mat: Materialize[A]): Materialize[A] = mat

  given materializeConstValue: [A : ValueOf] => Materialize[A]:
    override type Out = A
    override def apply(): A = valueOf[A]

  given materializeEmptyTuple: Materialize[EmptyTuple]:
    override type Out = EmptyTuple
    override def apply(): EmptyTuple = EmptyTuple

  given materializeTuple: [H, HMat <: H, T <: Tuple, TMat <: T] => (matHead: Materialize.Aux[H, HMat]) => (matTail: Materialize.Aux[T, TMat]) => Materialize[HMat *: TMat]:
    override type Out = HMat *: TMat
    override def apply(): HMat *: TMat = matHead() *: matTail()

sealed trait LowPriorityMaterialize:
  given materializeProduct: [A] => (productOf: Mirror.ProductOf[A]) => Materialize[productOf.MirroredElemTypes] => Materialize[A]:
    override type Out = A
    override def apply(): A = productOf.fromTuple(Materialize[productOf.MirroredElemTypes]())

  given materializeSingletonSum: [A, S <: A] => SingletonSum.Aux[A, S] => (matSingleton: Materialize[S]) => Materialize[A]:
    override type Out = S
    override def apply(): S = matSingleton()