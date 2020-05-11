package org.jetbrains.dokka.base.signatures

import javaslang.Tuple2
import org.jetbrains.dokka.base.translators.documentables.PageContentBuilder
import org.jetbrains.dokka.model.*
import org.jetbrains.dokka.model.properties.WithExtraProperties
import org.jetbrains.dokka.pages.ContentNode

interface SignatureProvider {
    fun signature(documentable: Documentable): ContentNode

    fun <T : Documentable> WithExtraProperties<T>.modifiers(
        filter: Set<ExtraModifiers> = ExtraModifiers.values().toSet()
    ): Set<ExtraModifiers> =
        extra[AdditionalModifiers]?.content?.filter { it in filter }?.toSet() ?: emptySet()

    fun Set<ExtraModifiers>.toSignatureString(): String =
        joinToString("") { it.name.toLowerCase() + " " }

    fun <T : Documentable> WithExtraProperties<T>.annotations(): List<Annotations.Annotation> =
        extra[Annotations]?.content ?: emptyList()

    fun PageContentBuilder.DocumentableContentBuilder.toSignatureString(
        a: Annotations.Annotation,
        renderAtStrategy: AtStrategy,
        listBrackets: Tuple2<Char, Char>,
        classExtension: String
    ) {
        when (renderAtStrategy) {
            is All, is OnlyOnce -> text("@")
            is Never -> Unit
        }
        link(a.dri.classNames!!, a.dri)
        text("(")
        a.params.entries.forEachIndexed { i, it ->
            text(it.key + " = ")
            when (renderAtStrategy) {
                is All -> All
                is Never, is OnlyOnce -> Never
            }.let { strategy ->
                valueToSignature(it.value, strategy, listBrackets, classExtension)
            }
            if (i != a.params.entries.size - 1) text(", ")
        }
        text(")")
    }

    private fun PageContentBuilder.DocumentableContentBuilder.valueToSignature(
        a: AnnotationParameterValue,
        renderAtStrategy: AtStrategy,
        listBrackets: Tuple2<Char, Char>,
        classExtension: String
    ): Unit = when (a) {
        is AnnotationValue -> toSignatureString(a.annotation, renderAtStrategy, listBrackets, classExtension)
        is ArrayValue -> {
            text(listBrackets._1.toString())
            a.value.forEachIndexed { i, it ->
                valueToSignature(it, renderAtStrategy, listBrackets, classExtension)
                if (i != a.value.size - 1) text(", ")
            }
            text(listBrackets._2.toString())
        }
        is EnumValue -> link(a.enumName, a.enumDri)
        is ClassValue -> link(a.className + classExtension, a.classDRI)
        is StringValue -> text(a.value)
    }

    private fun PageContentBuilder.DocumentableContentBuilder.annotations(
        d: Documentable,
        ignored: Set<Annotations.Annotation>,
        operation: (Annotations.Annotation) -> Unit
    ): Unit = when (d) {
        is DFunction -> d.annotations()
        is DProperty -> d.annotations()
        is DClass -> d.annotations()
        is DInterface -> d.annotations()
        is DObject -> d.annotations()
        is DEnum -> d.annotations()
        is DAnnotation -> d.annotations()
        is DTypeParameter -> d.annotations()
        is DEnumEntry -> d.annotations()
        is DTypeAlias -> d.annotations()
        is DParameter -> d.annotations()
        else -> null
    }?.let {
        it.filter { it !in ignored }.forEach {
            operation(it)
        }
    } ?: Unit

    fun PageContentBuilder.DocumentableContentBuilder.annotationsBlock(
        d: Documentable,
        ignored: Set<Annotations.Annotation>,
        renderAtStrategy: AtStrategy,
        listBrackets: Tuple2<Char, Char>,
        classExtension: String
    ) {
        annotations(d, ignored) {
            group {
                toSignatureString(it, renderAtStrategy, listBrackets, classExtension)
            }
        }
    }

    fun PageContentBuilder.DocumentableContentBuilder.annotationsInline(
        d: Documentable,
        ignored: Set<Annotations.Annotation>,
        renderAtStrategy: AtStrategy,
        listBrackets: Tuple2<Char, Char>,
        classExtension: String
    ) {
        annotations(d, ignored) {
            toSignatureString(it, renderAtStrategy, listBrackets, classExtension)
            text(Typography.nbsp.toString())
        }
    }
}

sealed class AtStrategy
object All : AtStrategy()
object OnlyOnce : AtStrategy()
object Never : AtStrategy()