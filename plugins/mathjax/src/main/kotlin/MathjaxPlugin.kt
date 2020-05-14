package  org.jetbrains.dokka.mathjax


import org.jetbrains.dokka.CoreExtensions
import org.jetbrains.dokka.model.doc.CustomWrapperTag
import org.jetbrains.dokka.pages.ContentPage
import org.jetbrains.dokka.pages.RootPageNode
import org.jetbrains.dokka.plugability.*
import org.jetbrains.dokka.transformers.pages.PageTransformer

class MathjaxPluginConfig : ConfigurableBlock {
    var property1: Int = 0
    var property2: String = "param"
    var property3: List<Int> = listOf(1, 2)
}

class MathjaxPlugin : DokkaPlugin() {
    val transformer by extending {
        CoreExtensions.pageTransformer providing ::MathjaxTransformer
    }
}

private const val ANNOTATION = "usesMathJax"
private const val LIB_PATH = "https://cdnjs.cloudflare.com/ajax/libs/mathjax/2.7.6/MathJax.js?config=TeX-AMS_SVG&latest"

class MathjaxTransformer(val ctx: DokkaContext) : PageTransformer {
    override fun invoke(input: RootPageNode) = input.transformContentPagesTree {
        val configInstance by configuration<MathjaxPlugin, MathjaxPluginConfig>(ctx)
        println(configInstance.property1)
        println(configInstance.property2)
        println(configInstance.property3)
        it.modified(
            embeddedResources = it.embeddedResources + if (it.isNeedingMathjax) listOf(LIB_PATH) else emptyList()
        )
    }

    private val ContentPage.isNeedingMathjax
        get() = documentable?.documentation?.values
            ?.flatMap { it.children }
            .orEmpty()
            .any { (it as? CustomWrapperTag)?.name == ANNOTATION }
}