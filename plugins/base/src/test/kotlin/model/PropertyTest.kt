package model

import org.jetbrains.dokka.model.*
import org.junit.jupiter.api.Test
import utils.AbstractModelTest
import utils.assertNotNull
import utils.name

class PropertyTest : AbstractModelTest("/src/main/kotlin/property/Test.kt", "property") {

    @Test
    fun valueProperty() {
        inlineModelTest(
            """
            |val property = "test""""
        ) {
            with((this / "property" / "property").cast<DProperty>()) {
                name equals "property"
                children counts 0
                with(getter.assertNotNull("Getter")) {
                    type.name equals "String"
                }
                 type.name equals "String"
            }
        }
    }

    @Test
    fun variableProperty() {
        inlineModelTest(
            """
            |var property = "test"
            """
        ) {
            with((this / "property" / "property").cast<DProperty>()) {
                name equals "property"
                children counts 0
                setter.assertNotNull("Setter")
                with(getter.assertNotNull("Getter")) {
                    type.name equals "String"
                }
                 type.name equals "String"
            }
        }
    }

    @Test
    fun valuePropertyWithGetter() {
        inlineModelTest(
            """
            |val property: String
            |    get() = "test"
            """
        ) {
            with((this / "property" / "property").cast<DProperty>()) {
                name equals "property"
                children counts 0
                with(getter.assertNotNull("Getter")) {
                    type.name equals "String"
                }
                 type.name equals "String"
            }
        }
    }

    @Test
    fun variablePropertyWithAccessors() {
        inlineModelTest(
            """
            |var property: String
            |    get() = "test"
            |    set(value) {}
            """
        ) {
            with((this / "property" / "property").cast<DProperty>()) {
                name equals "property"
                children counts 0
                setter.assertNotNull("Setter")
                with(getter.assertNotNull("Getter")) {
                    type.name equals "String"
                }
                visibility.values allEquals KotlinVisibility.Public
            }
        }
    }

    @Test
    fun propertyWithReceiver() {
        inlineModelTest(
            """
            |val String.property: Int
            |    get() = size() * 2
            """
        ) {
            with((this / "property" / "property").cast<DProperty>()) {
                name equals "property"
                children counts 0
                with(receiver.assertNotNull("property receiver")) {
                    name equals null
                    type.name equals "String"
                }
                with(getter.assertNotNull("Getter")) {
                    type.name equals "Int"
                }
                visibility.values allEquals KotlinVisibility.Public
            }
        }
    }

    @Test
    fun propertyOverride() {
        inlineModelTest(
            """
            |open class Foo() {
            |    open val property: Int get() = 0
            |}
            |class Bar(): Foo() {
            |    override val property: Int get() = 1
            |}
            """
        ) {
            with((this / "property").cast<DPackage>()) {
                with((this / "Foo" / "property").cast<DProperty>()) {
                    name equals "property"
                    children counts 0
                    with(getter.assertNotNull("Getter")) {
                        type.name equals "Int"
                    }
                }
                with((this / "Bar" / "property").cast<DProperty>()) {
                    name equals "property"
                    children counts 0
                    with(getter.assertNotNull("Getter")) {
                        type.name equals "Int"
                    }
                }
            }
        }
    }

    @Test
    fun sinceKotlin() {
        inlineModelTest(
            """
                |/**
                | * Quite useful [String]
                | */
                |@SinceKotlin("1.1")
                |val prop: String = "1.1 rulezz"
                """
        ) {
            with((this / "property" / "prop").cast<DProperty>()) {
                with(extra[Annotations].assertNotNull("Annotations")) {
                    this.content counts 1
                    with(content.first()) {
                        dri.classNames equals "SinceKotlin"
                        params.entries counts 1
                        params["version"].assertNotNull("version") equals "1.1"
                    }
                }
            }
        }
    }

    @Test
    fun annotatedProperty() {
        inlineModelTest(
            """
                |@Strictfp var property = "test"
                """,
            configuration = dokkaConfiguration {
                passes {
                    pass {
                        sourceRoots = listOf("src/")
                        classpath = listOfNotNull(jvmStdlibPath)
                    }
                }
            }
        ) {
            with((this / "property" / "property").cast<DProperty>()) {
                with(extra[Annotations].assertNotNull("Annotations")) {
                    this.content counts 1
                    with(content.first()) {
                        dri.classNames equals "Strictfp" // todo fix
                        params.entries counts 0
                    }
                }
            }
        }
    }
//    @Test
//    fun annotatedProperty() {
//        checkSourceExistsAndVerifyModel(
//            "testdata/properties/annotatedProperty.kt",
//            modelConfig = ModelConfig(
//                analysisPlatform = analysisPlatform,
//                withKotlinRuntime = true
//            )
//        ) { model ->
//            with(model.members.single().members.single()) {
//                Assert.assertEquals(1, annotations.count())
//                with(annotations[0]) {
//                    Assert.assertEquals("Strictfp", name)
//                    Assert.assertEquals(Content.Empty, content)
//                    Assert.assertEquals(NodeKind.Annotation, kind)
//                }
//            }
//        }
//    }
//
//}
}