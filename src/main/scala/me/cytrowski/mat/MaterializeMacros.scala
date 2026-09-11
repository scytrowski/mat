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
  private final case class MaterializeError(message: String):
    def prepend(context: String): MaterializeError =
      MaterializeError(s"$context cannot be materialized: $message")

  def materializeErrorImpl[A: Type](using Quotes): Expr[Any] =
    quotes.reflect.report.errorAndAbort(
      derive[A] match
        case Left(error) => error.message
        case Right(_)    => unsupportedType[A].message
    )

  def materializeInstanceImpl[A: Type](using Quotes): Expr[Materialize[A]] =
    derive[A] match
      case Right((outType, value)) =>
        outType.asType match
          case '[type out <: A; out] =>
            '{
              Materialize.fromValue[A, out](${ value }.asInstanceOf[out])
            }
      case Left(error) => quotes.reflect.report.errorAndAbort(error.message)

  def materializeOptImpl[A: Type](using Quotes): Expr[Any] =
    derive[A] match
      case Right((_, value)) => '{ Some($value) }
      case Left(_)           => '{ None }

  private def derive[A: Type](using
      Quotes
  ): Either[MaterializeError, (quotes.reflect.TypeRepr, Expr[Any])] =
    deriveAttempt[A].getOrElse(Left(unsupportedType[A]))

  private def deriveAttempt[A: Type](using
      Quotes
  ): Option[
    Either[MaterializeError, (quotes.reflect.TypeRepr, Expr[Any])]
  ] =
    Expr
      .summon[CustomMaterialize[A]]
      .map(custom =>
        Right((quotes.reflect.TypeRepr.of[A], '{ $custom.apply() }))
      )
      .orElse(deriveValueOf[A])
      .orElse(deriveNamedTuple[A])
      .orElse(deriveTuple[A])
      .orElse(deriveProduct[A])
      .orElse(deriveSingletonSum[A])

  private def unsupportedType[A: Type](using Quotes): MaterializeError =
    unsupportedType(quotes.reflect.TypeRepr.of[A].dealias)

  private def unsupportedType(using
      Quotes
  )(
      tpe: quotes.reflect.TypeRepr
  ): MaterializeError =
    MaterializeError(
      s"Type ${tpe.show} cannot be materialized. Supported forms are literal types, tuples, products, single-variant sums, or CustomMaterialize."
    )

  private def deriveValueOf[A: Type](using
      Quotes
  ): Option[
    Either[MaterializeError, (quotes.reflect.TypeRepr, Expr[Any])]
  ] =
    Expr
      .summon[ValueOf[A]]
      .map(valueOf =>
        Right((quotes.reflect.TypeRepr.of[A], '{ $valueOf.value }))
      )

  private def deriveNamedTuple[A: Type](using
      Quotes
  ): Option[
    Either[MaterializeError, (quotes.reflect.TypeRepr, Expr[Any])]
  ] =
    import quotes.reflect.*

    TypeRepr.of[A].asType match
      case '[type namedTuple <: AnyNamedTuple; namedTuple] =>
        val names = TypeRepr.of[Names[namedTuple]].dealias
        if names =:= TypeRepr.of[EmptyTuple] then None
        else
          TypeRepr.of[DropNames[namedTuple]].asType match
            case '[type values <: Tuple; values] =>
              Some(
                deriveTupleElements(
                  TypeRepr.of[values],
                  TypeRepr.of[A].show,
                  0
                ).map { case (_, value) =>
                  (
                    TypeRepr.of[A],
                    '{
                      ${ value }
                        .asInstanceOf[values]
                        .withNames[Names[namedTuple]]
                    }
                  )
                }
              )
      case _ => None

  private def deriveTuple[A: Type](using
      Quotes
  ): Option[
    Either[MaterializeError, (quotes.reflect.TypeRepr, Expr[Any])]
  ] =
    import quotes.reflect.*

    TypeRepr.of[A].dealias.asType match
      case '[EmptyTuple] =>
        Some(Right((TypeRepr.of[EmptyTuple], '{ EmptyTuple })))
      case '[head *: tail] =>
        Some(
          deriveTupleElements(TypeRepr.of[A].dealias, TypeRepr.of[A].show, 0)
        )
      case _ => None

  private def deriveTupleElements(using
      Quotes
  )(
      tpe: quotes.reflect.TypeRepr,
      owner: String,
      index: Int
  ): Either[MaterializeError, (quotes.reflect.TypeRepr, Expr[Any])] =
    import quotes.reflect.*

    tpe.asType match
      case '[EmptyTuple] =>
        Right((TypeRepr.of[EmptyTuple], '{ EmptyTuple }))
      case '[head *: tail] =>
        derive[head] match
          case Left(error) =>
            Left(
              error.prepend(
                s"Element $index of tuple $owner (${TypeRepr.of[head].show})"
              )
            )
          case Right((headType, headValue)) =>
            deriveTupleElements(
              TypeRepr.of[tail],
              owner,
              index + 1
            ).map { case (tailType, tailValue) =>
              headType.asType match
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
            }
      case _ => Left(unsupportedType(tpe))

  private def deriveProduct[A: Type](using
      Quotes
  ): Option[
    Either[MaterializeError, (quotes.reflect.TypeRepr, Expr[Any])]
  ] =
    import quotes.reflect.*

    val symbol = TypeRepr.of[A].typeSymbol
    if !symbol.flags.is(Flags.Case) then None
    else
      Some(
        Expr.summon[Mirror.ProductOf[A]] match
          case None =>
            Left(
              MaterializeError(
                s"Product type ${TypeRepr.of[A].show} has no usable Mirror.ProductOf instance."
              )
            )
          case Some(mirror) =>
            val fieldValues = symbol.caseFields.foldLeft[
              Either[MaterializeError, List[Expr[Any]]]
            ](Right(List.empty)) {
              case (result @ Left(_), _)  => result
              case (Right(values), field) =>
                TypeRepr.of[A].memberType(field).asType match
                  case '[fieldType] =>
                    derive[fieldType] match
                      case Left(error) =>
                        Left(
                          error.prepend(
                            s"Field '${field.name}' of ${TypeRepr.of[A].show} (${TypeRepr.of[fieldType].show})"
                          )
                        )
                      case Right((_, value)) => Right(values :+ value)
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
      )

  private def deriveSingletonSum[A: Type](using
      Quotes
  ): Option[
    Either[MaterializeError, (quotes.reflect.TypeRepr, Expr[Any])]
  ] =
    import quotes.reflect.*

    val symbol = TypeRepr.of[A].typeSymbol
    if symbol.children.isEmpty then None
    else if symbol.children.size > 1 then
      Some(
        Left(
          MaterializeError(
            s"Sum type ${TypeRepr.of[A].show} has ${symbol.children.size} variants; only sums with exactly one variant can be materialized."
          )
        )
      )
    else
      Some(
        Implicits.search(TypeRepr.of[SingletonSum[A]]) match
          case success: ImplicitSearchSuccess =>
            success.tree.tpe.widen.asType match
              case '[SingletonSum[A] { type Repr = repr }] =>
                derive[repr].left.map { error =>
                  error.prepend(
                    s"The only variant of ${TypeRepr.of[A].show} (${TypeRepr.of[repr].show})"
                  )
                }
              case _ => Left(unsupportedType[A])
          case _ => Left(unsupportedType[A])
      )

  private def buildTuple(values: List[Expr[Any]])(using Quotes): Expr[Any] =
    values match
      case Nil          => '{ EmptyTuple }
      case head :: tail =>
        val tailValue = buildTuple(tail)
        '{
          ${ head }.asInstanceOf[Any] *:
            $tailValue.asInstanceOf[Tuple]
        }
