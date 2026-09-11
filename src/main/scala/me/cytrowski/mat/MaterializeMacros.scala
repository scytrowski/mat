package me.cytrowski.mat

import scala.NamedTuple.{AnyNamedTuple, DropNames, NamedTuple, Names, withNames}
import scala.deriving.Mirror
import scala.quoted.*

/** Implementation of the materialization macro.
  *
  * Values are built using ordinary expressions, `ValueOf` and
  * `Mirror.fromTuple`. Product values are constructed through their mirrors;
  * singleton variants use references to their companion modules.
  */
private[mat] object MaterializeMacros:
  private final case class DerivationCacheKey(
      tpe: String,
      activeTypes: List[String]
  )

  private final class DerivationContext:
    val activeTypes = scala.collection.mutable.Set.empty[String]
    val cache = scala.collection.mutable.Map.empty[DerivationCacheKey, Any]

  private enum MaterializeError:
    case UnsupportedType(tpe: String)
    case TupleElement(
        owner: String,
        index: Int,
        elementType: String,
        cause: MaterializeError
    )
    case ProductField(
        owner: String,
        name: String,
        fieldType: String,
        cause: MaterializeError
    )
    case UnsupportedSum(owner: String, variants: Int)
    case UnsupportedSumVariants(
        owner: String,
        variants: List[(String, MaterializeError)]
    )
    case MissingProductMirror(owner: String)
    case SingleVariant(
        owner: String,
        variantType: String,
        cause: MaterializeError
    )
    case UnsupportedIntersection(owner: String, left: String, right: String)
    case UnsupportedUnion(owner: String)
    case AmbiguousUnion(owner: String, variants: List[String])

    def message: String = this match
      case UnsupportedType(tpe) =>
        s"Type $tpe cannot be materialized. Supported forms are literal types, tuples, products, single-variant sums, or CustomMaterialize."
      case TupleElement(owner, index, elementType, cause) =>
        s"Element $index of tuple $owner ($elementType) cannot be materialized: ${cause.message}"
      case ProductField(owner, name, fieldType, cause) =>
        s"Field '$name' of $owner ($fieldType) cannot be materialized: ${cause.message}"
      case UnsupportedSum(owner, variants) =>
        s"Sum type $owner has $variants materializable variants; only sums with exactly one materializable variant can be materialized."
      case UnsupportedSumVariants(owner, variants) =>
        variants match
          case (variantType, cause) :: Nil =>
            s"Sum type $owner cannot be materialized because variant $variantType cannot be materialized: ${cause.message}"
          case _ =>
            val details = variants
              .map { case (variantType, cause) =>
                s"$variantType (${cause.message})"
              }
              .mkString(", ")
            s"Sum type $owner cannot be materialized because these variants cannot be materialized: $details."
      case MissingProductMirror(owner) =>
        s"Product type $owner has no usable Mirror.ProductOf instance."
      case SingleVariant(owner, variantType, cause) =>
        s"The only variant of $owner ($variantType) cannot be materialized: ${cause.message}"
      case UnsupportedIntersection(owner, left, right) =>
        s"Intersection type $owner cannot be materialized from either component ($left or $right)."
      case UnsupportedUnion(owner) =>
        s"Union type $owner cannot be materialized because none of its variants can be materialized."
      case AmbiguousUnion(owner, variants) =>
        s"Union type $owner is ambiguous because multiple variants can be materialized: ${variants.mkString(", ")}."

  def materializeImpl[A: Type](using Quotes): Expr[Any] =
    import quotes.reflect.*
    given DerivationContext = new DerivationContext

    Expr.summon[Materialize[A]] match
      case Some(materialize) =>
        '{ $materialize.apply() }
      case None =>
        derive[A] match
          case Right((_, value)) => value
          case Left(error)       => report.errorAndAbort(error.message)

  def materializeInstanceImpl[A: Type](using Quotes): Expr[Materialize[A]] =
    given DerivationContext = new DerivationContext

    derive[A] match
      case Right((outType, value)) =>
        outType.asType match
          case '[type out <: A; out] =>
            '{
              Materialize.fromValue[A, out](${ value }.asInstanceOf[out])
            }
      case Left(error) => quotes.reflect.report.errorAndAbort(error.message)

  def materializeOptImpl[A: Type](using Quotes): Expr[Any] =
    given DerivationContext = new DerivationContext

    derive[A] match
      case Right((_, value)) => '{ Some($value) }
      case Left(_)           => '{ None }

  private def derive[A: Type](using
      quotes: Quotes,
      context: DerivationContext
  ): Either[MaterializeError, (quotes.reflect.TypeRepr, Expr[Any])] =
    val typeKey = quotes.reflect.TypeRepr.of[A].dealias.show
    val cacheKey = DerivationCacheKey(
      typeKey,
      context.activeTypes.toList.sorted
    )

    if context.activeTypes.contains(typeKey) then Left(unsupportedType[A])
    else
      context.cache.get(cacheKey) match
        case Some(cached) =>
          cached.asInstanceOf[
            Either[MaterializeError, (quotes.reflect.TypeRepr, Expr[Any])]
          ]
        case None =>
          context.activeTypes += typeKey
          try
            val result = deriveAttempt[A].getOrElse(Left(unsupportedType[A]))
            context.cache.update(cacheKey, result)
            result
          finally context.activeTypes -= typeKey

  private def deriveAttempt[A: Type](using
      Quotes,
      DerivationContext
  ): Option[
    Either[MaterializeError, (quotes.reflect.TypeRepr, Expr[Any])]
  ] =
    Expr
      .summon[CustomMaterialize[A]]
      .map(custom =>
        Right((quotes.reflect.TypeRepr.of[A], '{ $custom.apply() }))
      )
      .orElse(deriveValueOf[A])
      .orElse(deriveIntersection[A])
      .orElse(deriveUnion[A])
      .orElse(deriveNamedTuple[A])
      .orElse(deriveTuple[A])
      .orElse(deriveProduct[A])
      .orElse(deriveSingletonSum[A])

  private def unsupportedType[A: Type](using Quotes): MaterializeError =
    unsupportedType(quotes.reflect.TypeRepr.of[A].dealias)

  private def displayType(using
      quotes: Quotes
  )(tpe: quotes.reflect.TypeRepr): String =
    val shown = tpe.dealias.show
    val prefix = "scala.NamedTupleDecomposition.DropNames["
    if shown.startsWith(prefix) && shown.endsWith("]") then
      shown.substring(prefix.length, shown.length - 1)
    else shown

  private def unsupportedType(using
      Quotes
  )(
      tpe: quotes.reflect.TypeRepr
  ): MaterializeError =
    MaterializeError.UnsupportedType(displayType(tpe))

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

  private def deriveIntersection[A: Type](using
      Quotes,
      DerivationContext
  ): Option[
    Either[MaterializeError, (quotes.reflect.TypeRepr, Expr[Any])]
  ] =
    import quotes.reflect.*

    TypeRepr.of[A].dealias match
      case AndType(left, right) =>
        val owner = TypeRepr.of[A].dealias
        val result = List(left, right).iterator
          .flatMap { candidate =>
            candidate.asType match
              case '[candidateType] =>
                derive[candidateType] match
                  case Right(result @ (outType, _)) if outType <:< owner =>
                    Iterator(result)
                  case _ => Iterator.empty
          }
          .take(1)
          .toList
          .headOption

        Some(
          result.toRight(
            MaterializeError.UnsupportedIntersection(
              owner.show,
              left.show,
              right.show
            )
          )
        )
      case _ => None

  private def deriveUnion[A: Type](using
      Quotes,
      DerivationContext
  ): Option[
    Either[MaterializeError, (quotes.reflect.TypeRepr, Expr[Any])]
  ] =
    import quotes.reflect.*

    val owner = TypeRepr.of[A].dealias

    def variants(tpe: TypeRepr): List[TypeRepr] =
      tpe.dealias match
        case OrType(left, right) => variants(left) ++ variants(right)
        case variant             => List(variant)

    owner match
      case OrType(_, _) =>
        val successful = variants(owner).flatMap { variant =>
          variant.asType match
            case '[variantType] =>
              derive[variantType] match
                case Right((_, value)) =>
                  Some(
                    (
                      TypeRepr.of[variantType],
                      '{ ${ value }.asInstanceOf[variantType] }
                    )
                  )
                case _ => None
        }

        val distinctSuccessful = successful.foldLeft(
          List.empty[(TypeRepr, Expr[Any])]
        ) { (results, candidate) =>
          val (candidateType, _) = candidate
          if results.exists { case (resultType, _) =>
              resultType =:= candidateType
            }
          then results
          else results :+ candidate
        }

        distinctSuccessful match
          case result :: Nil => Some(Right(result))
          case Nil => Some(Left(MaterializeError.UnsupportedUnion(owner.show)))
          case results =>
            Some(
              Left(
                MaterializeError.AmbiguousUnion(
                  owner.show,
                  results.map(_._1.show)
                )
              )
            )
      case _ => None

  private def deriveNamedTuple[A: Type](using
      Quotes,
      DerivationContext
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
                ).flatMap { case (valuesType, value) =>
                  valuesType.asType match
                    case '[type outputValues <: Tuple; outputValues] =>
                      val outputType =
                        TypeRepr.of[NamedTuple[Names[namedTuple], outputValues]]
                      Right(
                        (
                          outputType,
                          '{
                            ${ value }
                              .asInstanceOf[outputValues]
                              .withNames[Names[namedTuple]]
                          }
                        )
                      )
                }
              )
      case _ => None

  private def deriveTuple[A: Type](using
      Quotes,
      DerivationContext
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
      Quotes,
      DerivationContext
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
              MaterializeError.TupleElement(
                owner,
                index,
                TypeRepr.of[head].show,
                error
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
      Quotes,
      DerivationContext
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
            Left(MaterializeError.MissingProductMirror(TypeRepr.of[A].show))
          case Some(mirror) =>
            val fieldValues = symbol.caseFields.foldLeft[
              Either[
                MaterializeError,
                List[(TypeRepr, TypeRepr, Expr[Any])]
              ]
            ](Right(List.empty)) {
              case (result @ Left(_), _)  => result
              case (Right(values), field) =>
                TypeRepr.of[A].memberType(field).asType match
                  case '[fieldType] =>
                    derive[fieldType] match
                      case Left(error) =>
                        Left(
                          MaterializeError.ProductField(
                            TypeRepr.of[A].show,
                            field.name,
                            displayType(TypeRepr.of[fieldType]),
                            error
                          )
                        )
                      case Right((outputType, value)) =>
                        Right(
                          values :+
                            (TypeRepr.of[fieldType], outputType, value)
                        )
            }

            fieldValues.map { fields =>
              val owner = TypeRepr.of[A].dealias
              val preciseProduct = owner match
                case AppliedType(typeConstructor, typeArguments) =>
                  val preciseArguments = typeArguments.map { typeArgument =>
                    fields
                      .collectFirst {
                        case (fieldType, outputType, _)
                            if fieldType =:= typeArgument =>
                          outputType
                      }
                      .getOrElse(typeArgument)
                  }
                  AppliedType(typeConstructor, preciseArguments)
                case _ => owner
              val outputType =
                if preciseProduct =:= owner then owner
                else AndType(owner, preciseProduct)
              val tuple = buildTuple(fields.map(_._3))
              val value =
                '{
                  val product = $mirror
                  product.fromTuple(
                    $tuple.asInstanceOf[product.MirroredElemTypes]
                  )
                }

              outputType.asType match
                case '[output] =>
                  (
                    outputType,
                    '{ ${ value }.asInstanceOf[output] }
                  )
            }
      )

  private def deriveSingletonSum[A: Type](using
      Quotes,
      DerivationContext
  ): Option[
    Either[MaterializeError, (quotes.reflect.TypeRepr, Expr[Any])]
  ] =
    import quotes.reflect.*

    val tpe = TypeRepr.of[A]
    val owner = tpe.dealias
    val symbol = tpe.typeSymbol
    if owner =:= TypeRepr.of[Tuple] then Some(Left(unsupportedType[A]))
    else if symbol.children.isEmpty then None
    else if symbol.children.size == 1 then
      Some(
        Implicits.search(TypeRepr.of[SingletonSum[A]]) match
          case success: ImplicitSearchSuccess =>
            success.tree.tpe.widen.asType match
              case '[SingletonSum[A] { type Repr = repr }] =>
                derive[repr].left.map { error =>
                  MaterializeError.SingleVariant(
                    owner.show,
                    TypeRepr.of[repr].show,
                    error
                  )
                }
              case _ => Left(unsupportedType[A])
          case _ => Left(unsupportedType[A])
      )
    else
      def tupleTypes(tpe: TypeRepr): List[TypeRepr] =
        tpe.asType match
          case '[EmptyTuple]   => Nil
          case '[head *: tail] =>
            TypeRepr.of[head] :: tupleTypes(TypeRepr.of[tail])
          case _ => Nil

      val variants = Expr
        .summon[Mirror.SumOf[A]]
        .flatMap { sum =>
          sum.asTerm.tpe.widen.asType match
            case '[Mirror.SumOf[A] { type MirroredElemTypes = elems }] =>
              Some(tupleTypes(TypeRepr.of[elems]))
            case _ => None
        }
        .getOrElse(symbol.children.map(_.typeRef))

      val childResults = variants.filter(_ <:< owner).map { variantType =>
        variantType.asType match
          case '[variant] =>
            if variantType.typeSymbol.flags.is(Flags.Module) then
              (
                variantType,
                Right(
                  (
                    variantType,
                    Ref(variantType.typeSymbol.companionModule).asExpr
                  )
                )
              )
            else (variantType, derive[variant])
      }
      val successful = childResults.collect { case (_, Right(result)) =>
        result
      }
      val blockingFailures = childResults.collect {
        case (childType, Left(error))
            if !childType.typeSymbol.flags.is(Flags.Sealed) ||
              childType.typeSymbol.children.nonEmpty ||
              childType.typeSymbol.flags.is(Flags.Case) =>
          (childType, error)
      }

      def rejectedSum(
          failures: List[(TypeRepr, MaterializeError)]
      ): Option[
        Either[MaterializeError, (TypeRepr, Expr[Any])]
      ] =
        Some(
          Left(
            MaterializeError.UnsupportedSumVariants(
              owner.show,
              failures.map { case (variantType, error) =>
                (variantType.show, error)
              }
            )
          )
        )

      successful match
        case result :: Nil if blockingFailures.isEmpty => Some(Right(result))
        case result :: Nil                             =>
          rejectedSum(blockingFailures)
        case Nil if blockingFailures.nonEmpty =>
          rejectedSum(blockingFailures)
        case Nil =>
          Some(
            Left(
              MaterializeError.UnsupportedSum(
                owner.show,
                childResults.size
              )
            )
          )
        case results =>
          Some(
            Left(
              MaterializeError.UnsupportedSum(
                owner.show,
                results.size
              )
            )
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
