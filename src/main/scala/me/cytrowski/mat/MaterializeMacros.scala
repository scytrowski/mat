package me.cytrowski.mat

import scala.NamedTuple.{AnyNamedTuple, DropNames, Names, withNames}
import scala.deriving.Mirror
import scala.quoted.*

/** Implementation of the materialization macro.
  *
  * Values are built using ordinary expressions, `ValueOf` and
  * `Mirror.fromTuple`. The implementation deliberately does not emit
  * constructor calls or `Ref`s to user-defined symbols.
  */
private[mat] object MaterializeMacros:
  def materializeInstanceImpl[A: Type](using Quotes): Expr[Materialize[A]] =
    derive[A] match
      case Some((outType, value)) =>
        outType.asType match
          case '[type out <: A; out] =>
            '{
              Materialize.fromValue[A, out](${ value }.asInstanceOf[out])
            }
      case None =>
        quotes.reflect.report.errorAndAbort(
          s"Type ${quotes.reflect.TypeRepr.of[A].show} cannot be materialized"
        )

  def materializeOptImpl[A: Type](using Quotes): Expr[Any] =
    derive[A] match
      case Some((_, value)) => '{ Some($value) }
      case None             => '{ None }

  private def derive[A: Type](using
      Quotes
  ): Option[(quotes.reflect.TypeRepr, Expr[Any])] =
    Expr
      .summon[CustomMaterialize[A]]
      .map(custom => (quotes.reflect.TypeRepr.of[A], '{ $custom.apply() }))
      .orElse(deriveValueOf[A])
      .orElse(deriveNamedTuple[A])
      .orElse(deriveTuple[A])
      .orElse(deriveProduct[A])
      .orElse(deriveSingletonSum[A])

  private def deriveValueOf[A: Type](using
      Quotes
  ): Option[(quotes.reflect.TypeRepr, Expr[Any])] =
    Expr
      .summon[ValueOf[A]]
      .map(valueOf => (quotes.reflect.TypeRepr.of[A], '{ $valueOf.value }))

  private def deriveNamedTuple[A: Type](using
      Quotes
  ): Option[(quotes.reflect.TypeRepr, Expr[Any])] =
    import quotes.reflect.*

    TypeRepr.of[A].asType match
      case '[type namedTuple <: AnyNamedTuple; namedTuple] =>
        val names = TypeRepr.of[Names[namedTuple]].dealias
        if names =:= TypeRepr.of[EmptyTuple] then None
        else
          TypeRepr.of[DropNames[namedTuple]].asType match
            case '[type values <: Tuple; values] =>
              derive[values].map { case (_, value) =>
                (
                  TypeRepr.of[A],
                  '{
                    ${ value }.asInstanceOf[values].withNames[Names[namedTuple]]
                  }
                )
              }
      case _ => None

  private def deriveTuple[A: Type](using
      Quotes
  ): Option[(quotes.reflect.TypeRepr, Expr[Any])] =
    import quotes.reflect.*

    TypeRepr.of[A].dealias.asType match
      case '[EmptyTuple]   => Some((TypeRepr.of[EmptyTuple], '{ EmptyTuple }))
      case '[head *: tail] =>
        for
          (headType, headValue) <- derive[head]
          (tailType, tailValue) <- derive[tail]
        yield headType.asType match
          case '[headOut] =>
            tailType.asType match
              case '[tailOut] =>
                (
                  TypeRepr.of[headOut *: (tailOut & Tuple)],
                  '{
                    ${ headValue }.asInstanceOf[headOut] *:
                      ${ tailValue }.asInstanceOf[tailOut & Tuple]
                  }
                )
      case _ => None

  private def deriveProduct[A: Type](using
      Quotes
  ): Option[(quotes.reflect.TypeRepr, Expr[Any])] =
    import quotes.reflect.*

    val symbol = TypeRepr.of[A].typeSymbol
    if !symbol.flags.is(Flags.Case) then None
    else
      Expr.summon[Mirror.ProductOf[A]].flatMap { mirror =>
        val fieldValues = symbol.caseFields.foldLeft(
          Option(List.empty[Expr[Any]])
        ) {
          case (Some(values), field) =>
            TypeRepr.of[A].memberType(field).asType match
              case '[fieldType] =>
                derive[fieldType].map { case (_, value) => values :+ value }
          case (None, _) => None
        }

        fieldValues.map { values =>
          val tuple = buildTuple(values)
          (
            TypeRepr.of[A],
            '{
              val product = $mirror
              product.fromTuple(
                $tuple.asInstanceOf[product.MirroredElemTypes]
              )
            }
          )
        }
      }

  private def deriveSingletonSum[A: Type](using
      Quotes
  ): Option[(quotes.reflect.TypeRepr, Expr[Any])] =
    import quotes.reflect.*

    val symbol = TypeRepr.of[A].typeSymbol
    if symbol.children.size != 1 then None
    else
      Implicits.search(TypeRepr.of[SingletonSum[A]]) match
        case success: ImplicitSearchSuccess =>
          success.tree.tpe.widen.asType match
            case '[SingletonSum[A] { type Repr = repr }] =>
              derive[repr]
            case _ => None
        case _ => None

  private def buildTuple(values: List[Expr[Any]])(using Quotes): Expr[Any] =
    values match
      case Nil          => '{ EmptyTuple }
      case head :: tail =>
        val tailValue = buildTuple(tail)
        '{
          ${ head }.asInstanceOf[Any] *:
            $tailValue.asInstanceOf[Tuple]
        }
