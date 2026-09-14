package me.cytrowski.mat

import org.scalatest.OptionValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers
import scala.annotation.nowarn
import scala.compiletime.testing.typeCheckErrors

class MaterializeSpec extends AnyFlatSpec with Matchers with OptionValues {

  private inline def diagnostics(inline code: String): List[String] =
    typeCheckErrors(code).map(_.message)

  private def assertExactType[A](value: A): A = value

  behavior of "constants"

  it should "materialize unit" in {
    assertExactType[Unit](materializeOpt[Unit].value) mustBe ()
  }

  it should "materialize constant boolean" in {
    assertExactType[true](materializeOpt[true].value) mustBe true
  }

  it should "materialize constant integer" in {
    assertExactType[1337](materializeOpt[1337].value) mustBe 1337
  }

  it should "materialize constant float" in {
    assertExactType[123.456](materializeOpt[123.456].value) mustBe 123.456
  }

  it should "materialize constant char" in {
    assertExactType['t'](materializeOpt['t'].value) mustBe 't'
  }

  it should "materialize constant string" in {
    assertExactType["abcdef"](materializeOpt["abcdef"].value) mustBe "abcdef"
  }

  behavior of "intersection types"

  it should "materialize an intersection with a literal type on the left" in {
    assertExactType[5](materializeOpt[5 & Int].value) mustBe 5
  }

  it should "materialize an intersection with a literal type on the right" in {
    assertExactType[5](materializeOpt[Int & 5].value) mustBe 5
  }

  it should "preserve the precise type of an intersection result" in {
    val left: 5 = materialize[5 & Int]
    val right: 5 = materialize[Int & 5]

    left mustBe 5
    right mustBe 5
  }

  it should "materialize an intersection with more than two components" in {
    val threeComponents: 5 = materialize[5 & Int & AnyVal]
    val fourComponents: 5 = materialize[5 & Int & AnyVal & Matchable]

    threeComponents mustBe 5
    fourComponents mustBe 5
  }

  it should "materialize an intersection of the same literal type" in {
    assertExactType[5](materializeOpt[5 & 5].value) mustBe 5
    assertExactType[5](materializeOpt[5 & 5 & 5].value) mustBe 5
  }

  it should "preserve a narrowed output type through an intersection" in {
    given Materialize.Aux[IntersectionBase, IntersectionLeaf.type] =
      Materialize.fromValue[IntersectionBase, IntersectionLeaf.type](
        IntersectionLeaf
      )

    val value: IntersectionLeaf.type =
      materialize[IntersectionBase & IntersectionMarker]
    val optional: Some[IntersectionLeaf.type] =
      materializeOpt[IntersectionBase & IntersectionMarker]

    value mustBe IntersectionLeaf
    optional mustBe Some(IntersectionLeaf)
  }

  it should "reject an explicit value that does not satisfy the full intersection" in {
    given Materialize.Aux[IntersectionBase, IntersectionBaseOnly.type] =
      Materialize.fromValue[IntersectionBase, IntersectionBaseOnly.type](
        IntersectionBaseOnly
      )

    materializeOpt[IntersectionBase & IntersectionMarker] mustBe empty
  }

  it should "materialize an intersection containing a tuple" in {
    val left: (5, "abc", 'd') =
      materialize[(5, "abc", 'd') & Tuple]
    val right: (5, "abc", 'd') =
      materialize[Tuple & (5, "abc", 'd')]

    left mustBe (5, "abc", 'd')
    right mustBe (5, "abc", 'd')
  }

  it should "reject an intersection of incompatible literal types" in {
    diagnostics("materialize[5 & \"abc\"]") mustBe List(
      """Found:    scala.quoted.Type[Nothing]
        |Required: scala.quoted.Type[(5 : Int) & ("abc" : String)]""".stripMargin
    )
  }

  it should "not materialize an unsupported intersection" in {
    materializeOpt[String & Int] mustBe empty
  }

  it should "not materialize an unsupported intersection with more than two components" in {
    materializeOpt[String & Int & AnyVal] mustBe empty
  }

  behavior of "union types"

  it should "materialize a union with one supported variant" in {
    val left: 5 = materialize[5 | String]
    val right: 5 = materialize[String | 5]

    left mustBe 5
    right mustBe 5
  }

  it should "materialize a nested union with one supported variant" in {
    val value: 5 = materialize[(5 | String) | Boolean]

    value mustBe 5
  }

  it should "materialize a flat union with more than two variants" in {
    val value: 5 = materialize[5 | String | Boolean]

    value mustBe 5
  }

  it should "materialize a union containing a tuple" in {
    val left: (5, "abc", 'd') =
      materialize[(5, "abc", 'd') | String]
    val right: (5, "abc", 'd') =
      materialize[String | (5, "abc", 'd')]

    left mustBe (5, "abc", 'd')
    right mustBe (5, "abc", 'd')
  }

  it should "materialize a union containing a tuple supertype" in {
    val left: (5, "abc", 'd') =
      materialize[(5, "abc", 'd') | Tuple]
    val right: (5, "abc", 'd') =
      materialize[Tuple | (5, "abc", 'd')]

    left mustBe (5, "abc", 'd')
    right mustBe (5, "abc", 'd')
  }

  it should "preserve a narrowed output type from a union variant" in {
    given Materialize.Aux[Int, 5] = Materialize.fromValue[Int, 5](5)

    val value: SingleElementProduct[5] =
      materialize[SingleElementProduct[Int] | String]
    val optional: Some[SingleElementProduct[5]] =
      materializeOpt[SingleElementProduct[Int] | String]

    value mustBe SingleElementProduct(5)
    optional mustBe Some(SingleElementProduct(5))
  }

  it should "deduplicate repeated union variants" in {
    assertExactType[5](materializeOpt[5 | 5].value) mustBe 5
  }

  it should "not materialize an ambiguous union" in {
    materializeOpt[5 | "abc"] mustBe empty
  }

  it should "not materialize an ambiguous flat union with more than two variants" in {
    materializeOpt[5 | "abc" | true] mustBe empty
  }

  it should "not materialize a union without a supported variant" in {
    materializeOpt[String | Int] mustBe empty
  }

  behavior of "mixed union and intersection types"

  it should "materialize a union containing an intersection" in {
    val left: 5 = materialize[(5 & Int) | String]
    val right: 5 = materialize[String | (5 & Int)]

    left mustBe 5
    right mustBe 5
  }

  it should "materialize an intersection containing a union" in {
    val left: 5 = materialize[(5 | String) & Int]
    val right: 5 = materialize[Int & (5 | String)]

    left mustBe 5
    right mustBe 5
  }

  it should "materialize a tuple containing a union and an intersection" in {
    val value: (5, "abc", 'd') =
      materialize[(5 | String, "abc" & String, 'd')]

    value mustBe (5, "abc", 'd')
  }

  it should "materialize a deeply nested union and intersection" in {
    val left: 5 = materialize[(5 & Int & AnyVal) | String]
    val right: 5 = materialize[(5 | String | Boolean) & AnyVal]

    left mustBe 5
    right mustBe 5
  }

  it should "reject an ambiguous union of intersections" in {
    materializeOpt[(5 & Int) | ("abc" & String)] mustBe empty
  }

  it should "reject a union and intersection without a materializable branch" in {
    materializeOpt[(String & Int) | Boolean] mustBe empty
  }

  behavior of "tuples"

  it should "materialize empty tuple" in {
    assertExactType[EmptyTuple](
      materializeOpt[EmptyTuple].value
    ) mustBe EmptyTuple
  }

  it should "materialize tuple with single element" in {
    assertExactType[5 *: EmptyTuple](
      materializeOpt[5 *: EmptyTuple].value
    ) mustBe 5 *: EmptyTuple
  }

  it should "materialize tuple with multiple elements" in {
    assertExactType[(false, "test", 9, 'd')](
      materializeOpt[(false, "test", 9, 'd')].value
    ) mustBe (false, "test", 9, 'd')
  }

  it should "materialize nested empty tuple" in {
    assertExactType[("abc", EmptyTuple, 'c')](
      materializeOpt[("abc", EmptyTuple, 'c')].value
    ) mustBe (
      "abc",
      EmptyTuple,
      'c'
    )
  }

  it should "materialize nested tuple" in {
    assertExactType[(15, 13.15, ('a', true, "d"), false)](
      materializeOpt[(15, 13.15, ('a', true, "d"), false)].value
    ) mustBe (
      15,
      13.15,
      ('a', true, "d"),
      false
    )
  }

  it should "not materialize tuple with non materializable element" in {
    materializeOpt[(23, String, 'a')] mustBe empty
  }

  behavior of "named tuples"

  it should "materialize named tuple with single element" in {
    assertExactType[(a: 5)](materializeOpt[(a: 5)].value) mustBe ((a = 5))
  }

  it should "materialize named tuple with multiple elements" in {
    assertExactType[(a: false, b: 'v', c: "ghi")](
      materializeOpt[(a: false, b: 'v', c: "ghi")].value
    ) mustBe ((
      a = false,
      b = 'v',
      c = "ghi"
    ))
  }

  it should "materialize named tuple with mixed union and intersection elements" in {
    val value: (a: 5, b: "abc", c: 'd') =
      materialize[(a: 5 | String, b: "abc" & String, c: 'd')]

    value mustBe ((a = 5, b = "abc", c = 'd'))
  }

  it should "materialize nested named tuple" in {
    assertExactType[(a: 'u', b: 37, c: (d: 98.76, e: false))](
      materializeOpt[(a: 'u', b: 37, c: (d: 98.76, e: false))].value
    ) mustBe ((
      a = 'u',
      b = 37,
      c = (d = 98.76, e = false)
    ))
  }

  it should "preserve narrowed output types in named tuples" in {
    given Materialize.Aux[Int, 5] = Materialize.fromValue[Int, 5](5)

    val value: (a: 5, b: "ok") =
      materialize[(a: Int, b: "ok")]
    val optional: Some[(a: 5, b: "ok")] =
      materializeOpt[(a: Int, b: "ok")]

    value mustBe ((a = 5, b = "ok"))
    optional mustBe Some((a = 5, b = "ok"))
  }

  it should "not materialize named tuple with non materializable element" in {
    materializeOpt[(a: 3, b: "aaa", c: Int)] mustBe empty
  }

  behavior of "products"

  it should "materialize empty product" in {
    assertExactType[EmptyProduct.type](
      materializeOpt[EmptyProduct.type].value
    ) mustBe EmptyProduct
  }

  it should "materialize product with single element" in {
    assertExactType[SingleElementProduct[5]](
      materializeOpt[SingleElementProduct[5]].value
    ) mustBe SingleElementProduct(5)
  }

  it should "allow assigning a narrowed invariant product" in {
    given Materialize.Aux[Int, 5] = Materialize.fromValue[Int, 5](5)

    val value: SingleElementProduct[5] =
      materialize[SingleElementProduct[Int]]
    val optional: Some[SingleElementProduct[5]] =
      materializeOpt[SingleElementProduct[Int]]

    value mustBe SingleElementProduct(5)
    optional mustBe Some(SingleElementProduct(5))
  }

  it should "materialize product with multiple elements" in {
    assertExactType[MultipleElementsProduct[false, 'z', "test"]](
      materializeOpt[
        MultipleElementsProduct[false, 'z', "test"]
      ].value
    ) mustBe MultipleElementsProduct(false, 'z', "test")
  }

  it should "materialize nested empty product" in {
    assertExactType[
      MultipleElementsProduct[25, EmptyProduct.type, 'p']
    ](
      materializeOpt[
        MultipleElementsProduct[25, EmptyProduct.type, 'p']
      ].value
    ) mustBe MultipleElementsProduct(25, EmptyProduct, 'p')
  }

  it should "materialize nested product" in {
    assertExactType[
      MultipleElementsProduct[
        "abc",
        MultipleElementsProduct[true, 98.32, 'p'],
        19
      ]
    ](
      materializeOpt[MultipleElementsProduct[
        "abc",
        MultipleElementsProduct[true, 98.32, 'p'],
        19
      ]].value
    ) mustBe MultipleElementsProduct(
      "abc",
      MultipleElementsProduct(true, 98.32, 'p'),
      19
    )
  }

  it should "preserve narrowed output types in nested products" in {
    given Materialize.Aux[Int, 5] = Materialize.fromValue[Int, 5](5)

    type Expected = MultipleElementsProduct[
      SingleElementProduct[Int] & SingleElementProduct[5],
      5,
      "ok"
    ]

    val value: Expected =
      materialize[
        MultipleElementsProduct[SingleElementProduct[Int], Int, "ok"]
      ]
    val optional: Some[Expected] = materializeOpt[
      MultipleElementsProduct[SingleElementProduct[Int], Int, "ok"]
    ]

    value mustBe MultipleElementsProduct(SingleElementProduct(5), 5, "ok")
    optional mustBe Some(
      MultipleElementsProduct(SingleElementProduct(5), 5, "ok")
    )
  }

  it should "materialize product with mixed union and intersection fields" in {
    val value: MultipleElementsProduct[5, "abc", 'd'] =
      materialize[MultipleElementsProduct[5 | String, "abc" & String, 'd']]

    value mustBe MultipleElementsProduct(5, "abc", 'd')
  }

  it should "materialize a local product without generated symbol references" in {
    case class LocalProduct[A](value: A)

    assertExactType[LocalProduct[5]](
      materializeOpt[LocalProduct[5]].value
    ) mustBe LocalProduct(5)
  }

  it should "not materialize product with non materializable element" in {
    materializeOpt[MultipleElementsProduct["def", false, Int]] mustBe empty
  }

  behavior of "sums"

  it should "materialize singleton sum" in {
    assertExactType[SingletonSumVariant.type](
      materializeOpt[SingletonSum].value
    ) mustBe SingletonSumVariant
  }

  it should "materialize a nested singleton sum" in {
    assertExactType[NestedSingletonLeaf.type](
      materializeOpt[NestedSingletonRoot].value
    ) mustBe NestedSingletonLeaf
  }

  it should "materialize a deeply nested singleton sum" in {
    val value: DeepSingletonLeaf.type = materialize[DeepSingletonRoot]

    value mustBe DeepSingletonLeaf
  }

  it should "materialize a nested singleton sum containing a product" in {
    assertExactType[NestedProductLeaf](
      materializeOpt[NestedProductRoot].value
    ) mustBe NestedProductLeaf(5)
  }

  it should "not materialize a nested sum with multiple variants" in {
    materializeOpt[NestedMultipleRoot] mustBe empty
  }

  it should "not materialize a root with a singleton and nested branch" in {
    materializeOpt[MixedNestedRoot] mustBe empty
  }

  it should "materialize the only candidate among an empty nested branch" in {
    assertExactType[MixedNestedWithEmptyBranchSingleton.type](
      materializeOpt[MixedNestedWithEmptyBranchRoot].value
    ) mustBe
      MixedNestedWithEmptyBranchSingleton
  }

  it should "not materialize a sum with only empty branches" in {
    materializeOpt[OnlyEmptyBranchesRoot] mustBe empty
  }

  it should "reject multiple candidates even with an empty branch" in {
    materializeOpt[MixedMultipleRoot] mustBe empty
  }

  it should "reject ambiguous parameterized ADTs" in {
    materializeOpt[ParameterizedResult[5]] mustBe None
    materializeOpt[ParameterizedResult[String]] mustBe None
  }

  it should "filter GADT variants by the requested result type" in {
    val intExpr: TypedInt.type = materialize[TypedExpr[Int]]

    val intResult: Some[TypedInt.type] = materializeOpt[TypedExpr[Int]]
    val booleanResult: Some[TypedBoolean.type] =
      materializeOpt[TypedExpr[Boolean]]

    intResult mustBe Some(TypedInt)
    booleanResult mustBe Some(TypedBoolean)
    materializeOpt[TypedExpr[String]] mustBe None
    intExpr mustBe TypedInt
  }

  it should "instantiate compatible generic product variants" in {
    assertExactType[InvariantValue[5]](
      materializeOpt[InvariantResult[5]].value
    ) mustBe InvariantValue(5)
    materializeOpt[InvariantResult[String]] mustBe None
  }

  it should "reject ambiguous and bottom-typed Option variants" in {
    materializeOpt[Option[5]] mustBe None
    @nowarn("msg=Match type reduction failed")
    val nothingResult: Any = materializeOpt[Option[Nothing]]
    nothingResult mustBe None
  }

  it should "reject a directly recursive product without overflowing" in {
    materializeOpt[RecursiveProduct] mustBe None
  }

  it should "reject a recursive sum with a base singleton" in {
    materializeOpt[RecursiveRoot] mustBe None
  }

  it should "reject a generic recursive sum" in {
    materializeOpt[GenericRecursiveRoot[5]] mustBe None
  }

  it should "reject a sum with multiple unsupported variants" in {
    materializeOpt[MultipleRejectedRoot] mustBe None
  }

  it should "not materialize sum with multiple variants" in {
    materializeOpt[SumWithMultipleVariants] mustBe empty
  }

  behavior of "enums"

  it should "materialize enum with single variant" in {
    assertExactType[SingleVariantEnum.Variant.type](
      materializeOpt[SingleVariantEnum].value
    ) mustBe SingleVariantEnum.Variant
  }

  it should "materialize enum variant" in {
    assertExactType[SingleVariantEnum.Variant.type](
      materializeOpt[
        SingleVariantEnum.Variant.type
      ].value
    ) mustBe SingleVariantEnum.Variant
  }

  it should "not materialize enum with multiple variants" in {
    materializeOpt[MultipleVariantsEnum] mustBe empty
  }

  it should "materialize compatible variants of a parameterized product enum" in {
    assertExactType[ParameterizedProductEnum.Five](
      materializeOpt[ParameterizedProductEnum[5]].value
    ) mustBe
      ParameterizedProductEnum.Five(5)
    assertExactType[ParameterizedProductEnum.Text](
      materializeOpt[ParameterizedProductEnum["text"]].value
    ) mustBe
      ParameterizedProductEnum.Text("text")
    materializeOpt[ParameterizedProductEnum[Boolean]] mustBe None
    materializeOpt[ParameterizedProductEnum[Any]] mustBe None
  }

  it should "materialize a generic product enum variant" in {
    assertExactType[GenericProductEnum.Value[5]](
      materializeOpt[GenericProductEnum[5]].value
    ) mustBe
      GenericProductEnum.Value(5)
    materializeOpt[GenericProductEnum[String]] mustBe None
  }

  behavior of "explicit materialization"

  it should "materialize type using an explicit implementation" in {
    assertExactType[ClassWithExplicitMaterialization](
      materializeOpt[ClassWithExplicitMaterialization].value
    ) mustBe ClassWithExplicitMaterialization.instance
  }

  it should "prefer explicit materialization over built-in implementation" in {
    var explicitMaterializationUsed = false

    given Materialize[Unit] = Materialize.fromValue {
      explicitMaterializationUsed = true
      ()
    }

    assertExactType[Unit](materializeOpt[Unit].value) mustBe ()
    explicitMaterializationUsed mustBe true
  }

  behavior of "compile-time contract"

  it should "preserve precise result types" in {
    val constant: 1337 = materialize[1337]
    val tuple: (true, "test") = materialize[(true, "test")]
    val product: SingleElementProduct[5] = materialize[SingleElementProduct[5]]
    val singleton: SingletonSumVariant.type = materialize[SingletonSum]

    constant mustBe 1337
    tuple mustBe (true, "test")
    product mustBe SingleElementProduct(5)
    singleton mustBe SingletonSumVariant
  }

  it should "support Materialize as a context bound" in {
    def requiresMaterialize[A: Materialize]: A = materialize[A]

    val value: SingleElementProduct[5] =
      requiresMaterialize[SingleElementProduct[5]]

    value mustBe SingleElementProduct(5)
  }

  it should "support materializeOpt with a Materialize context bound" in {
    def optionallyMaterialize[A: Materialize]: Option[A] =
      materializeOpt[A]

    optionallyMaterialize[SingleElementProduct[5]] mustBe
      Some(SingleElementProduct(5))
  }

  it should "prefer explicit Materialize evidence over derivation" in {
    case class ExplicitMaterialize(value: String)

    given Materialize[ExplicitMaterialize] =
      Materialize.fromValue[ExplicitMaterialize, ExplicitMaterialize](
        ExplicitMaterialize("explicit")
      )

    materialize[ExplicitMaterialize] mustBe ExplicitMaterialize("explicit")
  }

  it should "use explicit Materialize evidence in materializeOpt" in {
    case class ExplicitMaterializeOpt(value: String)

    given Materialize[ExplicitMaterializeOpt] =
      Materialize.fromValue(ExplicitMaterializeOpt("explicit"))
    given Materialize.Aux[IntersectionBase, IntersectionLeaf.type] =
      Materialize.fromValue[IntersectionBase, IntersectionLeaf.type](
        IntersectionLeaf
      )

    val value: Some[ExplicitMaterializeOpt] =
      materializeOpt[ExplicitMaterializeOpt]
    val precise: Some[IntersectionLeaf.type] =
      materializeOpt[IntersectionBase]

    value mustBe Some(ExplicitMaterializeOpt("explicit"))
    precise mustBe Some(IntersectionLeaf)
  }

  it should "use precise explicit Materialize evidence" in {
    given Materialize.Aux[IntersectionBase, ExplicitMaterializeLeaf.type] =
      Materialize.fromValue[IntersectionBase, ExplicitMaterializeLeaf.type](
        ExplicitMaterializeLeaf
      )

    val value: ExplicitMaterializeLeaf.type =
      materialize[IntersectionBase]
    val optional: Some[ExplicitMaterializeLeaf.type] =
      materializeOpt[IntersectionBase]

    value mustBe ExplicitMaterializeLeaf
    optional mustBe Some(ExplicitMaterializeLeaf)
  }

  it should "use imported explicit Materialize evidence in materializeOpt" in {
    object ImportedEvidence:
      given Materialize.Aux[IntersectionBase, IntersectionLeaf.type] =
        Materialize.fromValue[IntersectionBase, IntersectionLeaf.type](
          IntersectionLeaf
        )

    import ImportedEvidence.given

    val value: Some[IntersectionLeaf.type] =
      materializeOpt[IntersectionBase]

    value mustBe Some(IntersectionLeaf)
  }

  it should "use inline explicit Materialize evidence in materializeOpt" in {
    inline given Materialize.Aux[IntersectionBase, IntersectionLeaf.type] =
      Materialize.fromValue[IntersectionBase, IntersectionLeaf.type](
        IntersectionLeaf
      )

    val value: Some[IntersectionLeaf.type] =
      materializeOpt[IntersectionBase]

    value mustBe Some(IntersectionLeaf)
  }

  it should "prefer explicit Materialize evidence over union derivation" in {
    given Materialize.Aux[5 | String, String] =
      Materialize.fromValue[5 | String, String]("explicit")

    val value: String = materialize[5 | String]

    value mustBe "explicit"
  }

  it should "use explicit Materialize evidence through a context bound" in {
    case class ContextOnlyMaterialize(value: String)

    given Materialize[ContextOnlyMaterialize] =
      Materialize.fromValue[ContextOnlyMaterialize, ContextOnlyMaterialize](
        ContextOnlyMaterialize("context")
      )

    def requiresMaterialize[A: Materialize]: A = materialize[A]

    requiresMaterialize[ContextOnlyMaterialize] mustBe
      ContextOnlyMaterialize("context")
  }

  it should "evaluate Materialize.fromValue on every application" in {
    var applications = 0
    val evidence: Materialize.Aux[Int, Int] =
      Materialize.fromValue[Int, Int] {
        applications += 1
        applications
      }

    evidence() mustBe 1
    evidence() mustBe 2
    applications mustBe 2
  }

  it should "preserve the precise output type in Materialize evidence" in {
    val evidence: Materialize.Aux[SingletonSum, SingletonSumVariant.type] =
      summon[Materialize[SingletonSum]]

    evidence() mustBe SingletonSumVariant
  }

  it should "retrieve evidence through Materialize.apply" in {
    val evidence: Materialize[SingletonSum] = Materialize[SingletonSum]

    evidence() mustBe SingletonSumVariant
  }

  it should "expose None as an Option for unsupported types" in {
    val result: Option[String] = materializeOpt[String]

    result mustBe empty
  }

  it should "preserve precise materializeOpt result types" in {
    val constant: Some[1337] = materializeOpt[1337]
    val product: Some[SingleElementProduct[5]] =
      materializeOpt[SingleElementProduct[5]]
    val union: None.type = materializeOpt[5 | "abc"]
    val gadt: Some[TypedInt.type] = materializeOpt[TypedExpr[Int]]

    constant mustBe Some(1337)
    product mustBe Some(SingleElementProduct(5))
    union mustBe None
    gadt mustBe Some(TypedInt)
  }

  it should "expose None for nested unsupported types" in {
    val result: Option[MultipleElementsProduct[
      "ok",
      SingleElementProduct[String],
      false
    ]] = materializeOpt[
      MultipleElementsProduct["ok", SingleElementProduct[String], false]
    ]

    result mustBe empty
  }

  it should "explain why an unsupported type cannot be materialized" in {
    diagnostics("materialize[String]") mustBe List(
      "Type java.lang.String cannot be materialized. Supported forms are literal types, tuples, products, single-variant sums, or explicitly supplied Materialize evidence."
    )
  }

  it should "explain why unsupported Materialize evidence cannot be derived" in {
    diagnostics("Materialize.derived[String]") mustBe List(
      "Type java.lang.String cannot be materialized. Supported forms are literal types, tuples, products, single-variant sums, or explicitly supplied Materialize evidence."
    )
  }

  it should "identify an unsupported tuple element" in {
    diagnostics("materialize[(5, String, true)]") mustBe List(
      "Element 1 of tuple scala.Tuple3[5, scala.Predef.String, true] (java.lang.String) cannot be materialized: Type java.lang.String cannot be materialized. Supported forms are literal types, tuples, products, single-variant sums, or explicitly supplied Materialize evidence."
    )
  }

  it should "identify an unsupported intersection" in {
    diagnostics("materialize[String & Int]") mustBe List(
      "Intersection type scala.Predef.String & scala.Int cannot be materialized from either component (scala.Predef.String or scala.Int)."
    )
  }

  it should "identify an ambiguous union" in {
    diagnostics("materialize[5 | \"abc\"]") mustBe List(
      "Union type 5 | \"abc\" is ambiguous because multiple variants can be materialized: 5, \"abc\"."
    )
  }

  it should "identify a union without a supported variant" in {
    diagnostics("materialize[String | Int]") mustBe List(
      "Union type scala.Predef.String | scala.Int cannot be materialized because none of its variants can be materialized."
    )
  }

  it should "preserve context for nested mixed union and intersection errors" in {
    diagnostics(
      "materialize[MultipleElementsProduct[\"ok\", 5 | \"abc\", false]]"
    ) mustBe List(
      "Field 'b' of MaterializeSpec.this.MultipleElementsProduct[\"ok\", 5 | \"abc\", false] (5 | \"abc\") cannot be materialized: Union type 5 | \"abc\" is ambiguous because multiple variants can be materialized: 5, \"abc\"."
    )
  }

  it should "identify an unsupported product field" in {
    diagnostics(
      "materialize[MultipleElementsProduct[\"ok\", false, String]]"
    ) mustBe List(
      "Field 'c' of MaterializeSpec.this.MultipleElementsProduct[\"ok\", false, scala.Predef.String] (java.lang.String) cannot be materialized: Type java.lang.String cannot be materialized. Supported forms are literal types, tuples, products, single-variant sums, or explicitly supplied Materialize evidence."
    )
  }

  it should "preserve context for nested tuple errors" in {
    diagnostics("materialize[(5, (true, String))]") mustBe List(
      "Element 1 of tuple scala.Tuple2[5, scala.Tuple2[true, scala.Predef.String]] (scala.Tuple2[true, scala.Predef.String]) cannot be materialized: Element 1 of tuple scala.Tuple2[true, scala.Predef.String] (java.lang.String) cannot be materialized: Type java.lang.String cannot be materialized. Supported forms are literal types, tuples, products, single-variant sums, or explicitly supplied Materialize evidence."
    )
  }

  it should "preserve context for nested product errors" in {
    diagnostics(
      "materialize[MultipleElementsProduct[\"ok\", SingleElementProduct[String], false]]"
    ) mustBe List(
      "Field 'b' of MaterializeSpec.this.MultipleElementsProduct[\"ok\", MaterializeSpec.this.SingleElementProduct[scala.Predef.String], false] (MaterializeSpec.this.SingleElementProduct[scala.Predef.String]) cannot be materialized: Field 'a' of MaterializeSpec.this.SingleElementProduct[scala.Predef.String] (java.lang.String) cannot be materialized: Type java.lang.String cannot be materialized. Supported forms are literal types, tuples, products, single-variant sums, or explicitly supplied Materialize evidence."
    )
  }

  it should "preserve context for named tuple errors" in {
    diagnostics("materialize[(a: 5, b: String)]") mustBe List(
      "Element 1 of tuple scala.NamedTuple.NamedTuple[scala.Tuple2[\"a\", \"b\"], scala.Tuple2[5, scala.Predef.String]] (java.lang.String) cannot be materialized: Type java.lang.String cannot be materialized. Supported forms are literal types, tuples, products, single-variant sums, or explicitly supplied Materialize evidence."
    )
  }

  it should "identify unsupported sums" in {
    diagnostics("materialize[SumWithMultipleVariants]") mustBe List(
      "Sum type MaterializeSpec.this.SumWithMultipleVariants has 3 materializable variants; only sums with exactly one materializable variant can be materialized."
    )
  }

  it should "identify nested sum ambiguity" in {
    diagnostics("materialize[MixedNestedRoot]") mustBe List(
      "Sum type MaterializeSpec.this.MixedNestedRoot has 2 materializable variants; only sums with exactly one materializable variant can be materialized."
    )
  }

  it should "identify Option[5] ambiguity" in {
    diagnostics("materialize[Option[5]]") mustBe List(
      "Sum type scala.Option[5] has 2 materializable variants; only sums with exactly one materializable variant can be materialized."
    )
  }

  it should "identify an unsupported Option[Nothing] variant" in {
    diagnostics("materialize[Option[Nothing]]") mustBe List(
      "Sum type scala.Option[scala.Nothing] cannot be materialized because variant scala.Some[scala.Nothing] cannot be materialized: Field 'value' of scala.Some[scala.Nothing] (scala.Nothing) cannot be materialized: Type scala.Nothing cannot be materialized. Supported forms are literal types, tuples, products, single-variant sums, or explicitly supplied Materialize evidence."
    )
  }

  it should "explain recursive sum branch failures" in {
    diagnostics("materialize[RecursiveRoot]") mustBe List(
      "Sum type MaterializeSpec.this.RecursiveRoot cannot be materialized because variant MaterializeSpec.this.RecursiveNode cannot be materialized: Field 'next' of MaterializeSpec.this.RecursiveNode (MaterializeSpec.this.RecursiveRoot) cannot be materialized: Type MaterializeSpec.this.RecursiveRoot cannot be materialized. Supported forms are literal types, tuples, products, single-variant sums, or explicitly supplied Materialize evidence."
    )
  }

  it should "explain directly recursive product failures" in {
    diagnostics("materialize[RecursiveProduct]") mustBe List(
      "Field 'next' of MaterializeSpec.this.RecursiveProduct (MaterializeSpec.this.RecursiveProduct) cannot be materialized: Type MaterializeSpec.this.RecursiveProduct cannot be materialized. Supported forms are literal types, tuples, products, single-variant sums, or explicitly supplied Materialize evidence."
    )
  }

  it should "explain generic recursive sum branch failures" in {
    diagnostics("materialize[GenericRecursiveRoot[5]]") mustBe List(
      "Sum type MaterializeSpec.this.GenericRecursiveRoot[5] cannot be materialized because variant MaterializeSpec.this.GenericRecursiveNode[5] cannot be materialized: Field 'next' of MaterializeSpec.this.GenericRecursiveNode[5] (MaterializeSpec.this.GenericRecursiveRoot[5]) cannot be materialized: Type MaterializeSpec.this.GenericRecursiveRoot[5] cannot be materialized. Supported forms are literal types, tuples, products, single-variant sums, or explicitly supplied Materialize evidence."
    )
  }

  it should "explain all unsupported sum variants" in {
    diagnostics("materialize[MultipleRejectedRoot]") mustBe List(
      "Sum type MaterializeSpec.this.MultipleRejectedRoot cannot be materialized because these variants cannot be materialized: MaterializeSpec.this.FirstRejected (Field 'value' of MaterializeSpec.this.FirstRejected (java.lang.String) cannot be materialized: Type java.lang.String cannot be materialized. Supported forms are literal types, tuples, products, single-variant sums, or explicitly supplied Materialize evidence.), MaterializeSpec.this.SecondRejected (Field 'value' of MaterializeSpec.this.SecondRejected (scala.Int) cannot be materialized: Type scala.Int cannot be materialized. Supported forms are literal types, tuples, products, single-variant sums, or explicitly supplied Materialize evidence.)."
    )
  }

  behavior of "other types"

  it should "materialize type lambda resulting in constant type" in {
    assertExactType[Some["test"]](
      materializeOpt[TypeLambda[true]].value
    ) mustBe Some("test")
    assertExactType[None.type](
      materializeOpt[TypeLambda[false]].value
    ) mustBe None
  }

  it should "materialize a literal type alias" in {
    assertExactType[5](materializeOpt[LiteralAlias].value) mustBe 5
  }

  it should "reuse materialization of repeated nested types" in {
    assertExactType[
      MultipleElementsProduct[
        SingleElementProduct[5],
        SingleElementProduct[5],
        SingleElementProduct[5]
      ]
    ](
      materializeOpt[
        MultipleElementsProduct[
          SingleElementProduct[5],
          SingleElementProduct[5],
          SingleElementProduct[5]
        ]
      ].value
    ) mustBe MultipleElementsProduct(
      SingleElementProduct(5),
      SingleElementProduct(5),
      SingleElementProduct(5)
    )
  }

  it should "not materialize an opaque type outside its defining scope" in {
    materializeOpt[OpaqueTypes.Value] mustBe None
  }

  it should "not materialize a refined type" in {
    materializeOpt[RefinedString] mustBe None
  }

  it should "not materialize Nothing" in {
    @nowarn("msg=Match type reduction failed")
    val nothingResult: Any = materializeOpt[Nothing]
    nothingResult mustBe None
  }

  it should "not materialize Any" in {
    materializeOpt[Any] mustBe None
  }

  it should "materialize compatible variants of a generic enum" in {
    assertExactType[GenericEnum.IntValue.type](
      materializeOpt[GenericEnum[Int]].value
    ) mustBe GenericEnum.IntValue
    assertExactType[GenericEnum.BooleanValue.type](
      materializeOpt[GenericEnum[Boolean]].value
    ) mustBe GenericEnum.BooleanValue
    materializeOpt[GenericEnum[String]] mustBe None
  }

  it should "explain why an opaque type cannot be materialized" in {
    diagnostics("materialize[OpaqueTypes.Value]") mustBe List(
      "Type MaterializeSpec.this.OpaqueTypes.Value cannot be materialized. Supported forms are literal types, tuples, products, single-variant sums, or explicitly supplied Materialize evidence."
    )
  }

  it should "explain why a refined type cannot be materialized" in {
    diagnostics("materialize[RefinedString]") mustBe List(
      """Type scala.Predef.String {
          |  type Marker >: true <: true
          |} cannot be materialized. Supported forms are literal types, tuples, products, single-variant sums, or explicitly supplied Materialize evidence.""".stripMargin
    )
  }

  it should "explain why Nothing cannot be materialized" in {
    diagnostics("materialize[Nothing]") mustBe List(
      "Type scala.Nothing cannot be materialized. Supported forms are literal types, tuples, products, single-variant sums, or explicitly supplied Materialize evidence."
    )
  }

  it should "explain why Any cannot be materialized" in {
    diagnostics("materialize[Any]") mustBe List(
      "Type scala.Any cannot be materialized. Supported forms are literal types, tuples, products, single-variant sums, or explicitly supplied Materialize evidence."
    )
  }

  it should "explain a parameterized enum without a compatible variant" in {
    diagnostics("materialize[ParameterizedProductEnum[Boolean]]") mustBe List(
      "Sum type MaterializeSpec.this.ParameterizedProductEnum[scala.Boolean] has 0 materializable variants; only sums with exactly one materializable variant can be materialized."
    )
  }

  it should "explain an ambiguous parameterized product enum" in {
    diagnostics("materialize[ParameterizedProductEnum[Any]]") mustBe List(
      "Sum type MaterializeSpec.this.ParameterizedProductEnum[scala.Any] has 2 materializable variants; only sums with exactly one materializable variant can be materialized."
    )
  }

  it should "explain an unsupported generic enum field" in {
    diagnostics("materialize[GenericProductEnum[String]]") mustBe List(
      "The only variant of MaterializeSpec.this.GenericProductEnum[scala.Predef.String] (MaterializeSpec.this.GenericProductEnum.Value[java.lang.String]) cannot be materialized: Field 'value' of MaterializeSpec.this.GenericProductEnum.Value[java.lang.String] (java.lang.String) cannot be materialized: Type java.lang.String cannot be materialized. Supported forms are literal types, tuples, products, single-variant sums, or explicitly supplied Materialize evidence."
    )
  }

  it should "not materialize abstract type" in {
    materializeOpt[String] mustBe empty
  }

  it should "not materialize abstract generic type with abstract type parameter" in {
    materializeOpt[Option[Int]] mustBe empty
  }

  it should "not materialize abstract generic type with constant type parameter" in {
    materializeOpt[List[5]] mustBe empty
  }

  private case object EmptyProduct
  private case class SingleElementProduct[A](a: A)
  private case class MultipleElementsProduct[A, B, C](a: A, b: B, c: C)

  private sealed trait IntersectionBase
  private trait IntersectionMarker
  private case object IntersectionLeaf
      extends IntersectionBase
      with IntersectionMarker
  private case object IntersectionBaseOnly extends IntersectionBase
  private case object ExplicitMaterializeLeaf extends IntersectionBase

  private sealed trait SingletonSum
  private case object SingletonSumVariant extends SingletonSum

  private sealed trait NestedSingletonRoot
  private sealed trait NestedSingletonBranch extends NestedSingletonRoot
  private case object NestedSingletonLeaf extends NestedSingletonBranch

  private sealed trait DeepSingletonRoot
  private sealed trait DeepSingletonBranch extends DeepSingletonRoot
  private sealed trait DeepSingletonInner extends DeepSingletonBranch
  private case object DeepSingletonLeaf extends DeepSingletonInner

  private sealed trait NestedProductRoot
  private sealed trait NestedProductBranch extends NestedProductRoot
  private case class NestedProductLeaf(value: 5) extends NestedProductBranch

  private sealed trait NestedMultipleRoot
  private sealed trait NestedMultipleBranch extends NestedMultipleRoot
  private case object NestedFirstLeaf extends NestedMultipleBranch
  private case object NestedSecondLeaf extends NestedMultipleBranch

  private sealed trait MixedNestedRoot
  private case object MixedNestedSingleton extends MixedNestedRoot
  private sealed trait MixedNestedBranch extends MixedNestedRoot
  private case object MixedNestedLeaf extends MixedNestedBranch

  private sealed trait MixedNestedWithEmptyBranchRoot
  private case object MixedNestedWithEmptyBranchSingleton
      extends MixedNestedWithEmptyBranchRoot
  private sealed trait MixedNestedWithEmptyBranch
      extends MixedNestedWithEmptyBranchRoot

  private sealed trait OnlyEmptyBranchesRoot
  private sealed trait OnlyEmptyBranchesLeft extends OnlyEmptyBranchesRoot
  private sealed trait OnlyEmptyBranchesRight extends OnlyEmptyBranchesRoot

  private sealed trait MixedMultipleRoot
  private case object MixedMultipleSingleton extends MixedMultipleRoot
  private sealed trait MixedMultipleEmptyBranch extends MixedMultipleRoot
  private case object MixedMultipleLeaf extends MixedMultipleRoot

  private case class RecursiveProduct(next: RecursiveProduct)

  private sealed trait RecursiveRoot
  private case object RecursiveBase extends RecursiveRoot
  private case class RecursiveNode(next: RecursiveRoot) extends RecursiveRoot

  private sealed trait GenericRecursiveRoot[+A]
  private case object GenericRecursiveBase extends GenericRecursiveRoot[Nothing]
  private case class GenericRecursiveNode[A](
      value: A,
      next: GenericRecursiveRoot[A]
  ) extends GenericRecursiveRoot[A]

  private sealed trait MultipleRejectedRoot
  private case class FirstRejected(value: String) extends MultipleRejectedRoot
  private case class SecondRejected(value: Int) extends MultipleRejectedRoot

  private sealed trait ParameterizedResult[+A]
  private case object ParameterizedEmpty extends ParameterizedResult[Nothing]
  private case class ParameterizedValue[A](value: A)
      extends ParameterizedResult[A]

  private sealed trait TypedExpr[A]
  private case object TypedInt extends TypedExpr[Int]
  private case object TypedBoolean extends TypedExpr[Boolean]

  private sealed trait InvariantResult[A]
  private case object InvariantEmpty extends InvariantResult[Nothing]
  private case class InvariantValue[A](value: A) extends InvariantResult[A]

  private sealed trait SumWithMultipleVariants
  private case object FirstVariant extends SumWithMultipleVariants
  private case object SecondVariant extends SumWithMultipleVariants
  private case object ThirdVariant extends SumWithMultipleVariants

  private enum SingleVariantEnum {
    case Variant
  }

  private enum MultipleVariantsEnum {
    case FirstVariant
    case SecondVariant
    case ThirdVariant
  }

  private enum GenericEnum[+A] {
    case IntValue extends GenericEnum[Int]
    case BooleanValue extends GenericEnum[Boolean]
  }

  private enum ParameterizedProductEnum[+A] {
    case Five(value: 5) extends ParameterizedProductEnum[5]
    case Text(value: "text") extends ParameterizedProductEnum["text"]
  }

  private enum GenericProductEnum[A] {
    case Value(value: A) extends GenericProductEnum[A]
  }

  private sealed abstract class ClassWithExplicitMaterialization

  private object ClassWithExplicitMaterialization:
    val instance: ClassWithExplicitMaterialization =
      new ClassWithExplicitMaterialization {}

  private given Materialize[ClassWithExplicitMaterialization] =
    Materialize.fromValue(ClassWithExplicitMaterialization.instance)

  private type TypeLambda[C <: Boolean] <: Option[String] =
    C match
      case true  => Some["test"]
      case false => None.type

  private type LiteralAlias = 5
  private type RefinedString = String { type Marker = true }

  private object OpaqueTypes:
    opaque type Value = 5

}
