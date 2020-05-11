package org.jetbrains.dokka.kotlinAsJava.signatures

import javaslang.Tuple2
import org.jetbrains.dokka.base.signatures.All
import org.jetbrains.dokka.base.signatures.OnlyOnce
import org.jetbrains.dokka.base.signatures.SignatureProvider
import org.jetbrains.dokka.base.transformers.pages.comments.CommentsToContentConverter
import org.jetbrains.dokka.base.translators.documentables.PageContentBuilder
import org.jetbrains.dokka.links.DRI
import org.jetbrains.dokka.links.sureClassNames
import org.jetbrains.dokka.model.*
import org.jetbrains.dokka.model.properties.WithExtraProperties
import org.jetbrains.dokka.pages.ContentKind
import org.jetbrains.dokka.pages.ContentNode
import org.jetbrains.dokka.pages.TextStyle
import org.jetbrains.dokka.utilities.DokkaLogger
import kotlin.text.Typography.nbsp

class JavaSignatureProvider(ctcc: CommentsToContentConverter, logger: DokkaLogger) : SignatureProvider {
    private val contentBuilder = PageContentBuilder(ctcc, this, logger)

    private val ignoredVisibilities = setOf(JavaVisibility.Default)
    private val ignoredModifiers =
        setOf(KotlinModifier.Open, JavaModifier.Empty, KotlinModifier.Empty, KotlinModifier.Sealed)
    private val ignoredAnnotations = setOf(
        Annotations.Annotation(DRI("kotlin.jvm", "Transient"), emptyMap()),
        Annotations.Annotation(DRI("kotlin.jvm", "Volatile"), emptyMap()),
        Annotations.Annotation(DRI("kotlin.jvm", "Transitive"), emptyMap()),
        Annotations.Annotation(DRI("kotlin.jvm", "Strictfp"), emptyMap()),
        Annotations.Annotation(DRI("kotlin.jvm", "JvmStatic"), emptyMap())
    )

    override fun signature(documentable: Documentable): ContentNode = when (documentable) {
        is DFunction -> signature(documentable)
        is DProperty -> signature(documentable)
        is DClasslike -> signature(documentable)
        is DEnumEntry -> signature(documentable)
        is DTypeParameter -> signature(documentable)
        else -> throw NotImplementedError(
            "Cannot generate signature for ${documentable::class.qualifiedName} ${documentable.name}"
        )
    }

    private fun signature(e: DEnumEntry)= contentBuilder.contentFor(e, ContentKind.Symbol, setOf(TextStyle.Monospace))

    private fun signature(c: DClasslike) = contentBuilder.contentFor(c, ContentKind.Symbol, setOf(TextStyle.Monospace)) {
        platformText(c.visibility) { (it.takeIf { it !in ignoredVisibilities }?.name ?: "") + " " }

        if (c is DClass) {
            platformText(c.modifier) { it.takeIf { it !in ignoredModifiers }?.name.orEmpty() + " " }
            text(c.javaAdditionalModifiers())
        }

        when (c) {
            is DClass -> text("class ")
            is DInterface -> text("interface ")
            is DEnum -> text("enum ")
            is DObject -> text("class ")
            is DAnnotation -> text("@interface ")
        }
        link(c.name!!, c.dri)
        if (c is WithGenerics) {
            list(c.generics, prefix = "<", suffix = ">") {
                +buildSignature(it)
            }
        }
        if (c is WithSupertypes) {
            c.supertypes.map { (p, dris) ->
                list(dris, prefix = " extends ", platformData = setOf(p)) {
                    link(it.sureClassNames, it, platformData = setOf(p))
                }
            }
        }
    }

    private fun signature(p: DProperty) = contentBuilder.contentFor(p, ContentKind.Symbol, setOf(TextStyle.Monospace)) {
        group(styles = setOf(TextStyle.Block)) {
            javaAnnotationsBlock(p)
            platformText(p.visibility) { (it.takeIf { it !in ignoredVisibilities }?.name ?: "") + " " }
            platformText(p.modifier) { it.name + " " }
            text(p.javaAdditionalModifiers())
            signatureForProjection(p.type)
            text(nbsp.toString())
            link(p.name, p.dri)
        }
    }

    private fun signature(f: DFunction) = contentBuilder.contentFor(f, ContentKind.Symbol, setOf(TextStyle.Monospace)) {
        group(styles = setOf(TextStyle.Block)) {
            javaAnnotationsBlock(f)
            platformText(f.modifier) { it.takeIf { it !in ignoredModifiers }?.name.orEmpty() + " " }
            text(f.javaAdditionalModifiers())
            val returnType = f.type
            signatureForProjection(returnType)
            text(nbsp.toString())
            link(f.name, f.dri)
            list(f.generics, prefix = "<", suffix = ">") {
                +buildSignature(it)
            }
            list(f.parameters, "(", ")") {
                javaAnnotationsInline(it)
                text(it.javaAdditionalModifiers())
                signatureForProjection(it.type)
                text(nbsp.toString())
                link(it.name!!, it.dri)
            }
        }
    }

    private fun signature(t: DTypeParameter) = contentBuilder.contentFor(t) {
        text(t.name.substringAfterLast("."))
        list(t.bounds, prefix = " extends ") {
            signatureForProjection(it)
        }
    }

    private fun PageContentBuilder.DocumentableContentBuilder.signatureForProjection(p: Projection): Unit = when (p) {
        is OtherParameter -> text(p.name)

        is TypeConstructor -> group(styles = emptySet()) {
            link(p.dri.classNames.orEmpty(), p.dri)
            list(p.projections, prefix = "<", suffix = ">") {
                signatureForProjection(it)
            }
        }

        is Variance -> group(styles = emptySet()) {
            text(p.kind.toString() + " ") // TODO: "super" && "extends"
            signatureForProjection(p.inner)
        }

        is Star -> text("?")

        is Nullable -> signatureForProjection(p.inner)

        is JavaObject, is Dynamic -> link("Object", DRI("java.lang", "Object"))
        is Void -> text("void")
        is PrimitiveJavaType -> text(p.name)
    }

    private val strategy = All
    private val listBrackets = Tuple2('{', '}')
    private val classExtension = ".class"

    fun PageContentBuilder.DocumentableContentBuilder.javaAnnotationsBlock(d: Documentable) =
        annotationsBlock(d, ignoredAnnotations, strategy, listBrackets, classExtension)

    fun PageContentBuilder.DocumentableContentBuilder.javaAnnotationsInline(d: Documentable) =
        annotationsInline(d, ignoredAnnotations, strategy, listBrackets, classExtension)

    private fun <T : Documentable> WithExtraProperties<T>.javaAdditionalModifiers() =
        this.modifiers(ExtraModifiers.javaOnlyModifiers).toSignatureString()
}