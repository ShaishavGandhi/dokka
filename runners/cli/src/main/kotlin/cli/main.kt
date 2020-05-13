package org.jetbrains.dokka

import kotlinx.cli.*
import org.jetbrains.dokka.DokkaConfiguration.ExternalDocumentationLink
import org.jetbrains.dokka.utilities.DokkaConsoleLogger
import org.jetbrains.kotlin.utils.addToStdlib.cast
import java.io.File
import java.io.FileNotFoundException
import java.net.MalformedURLException
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths

class GlobalArguments(args: Array<String>) : DokkaConfiguration {

    val parser = ArgParser("globalArguments")

    override val outputDir by parser.option(ArgType.String, description = "Output directory path").required()

    override val format by parser.option(
        ArgType.String,
        description = "Output format (text, html, gfm, jekyll, kotlin-website)"
    ).required()

    override val generateIndexPages by parser.option(ArgType.Boolean, description = "Generate index pages")
        .default(false)

    override val cacheRoot by parser.option(
        ArgType.String,
        description = "Path to cache folder, or 'default' to use ~/.cache/dokka, if not provided caching is disabled"
    )

    override val impliedPlatforms: List<String> = emptyList()

    override val passesConfigurations by parser.option(
        ArgTypeArgument,
        description = "Single dokka pass",
        fullName = "pass"
    ).multiple().

    override val pluginsConfiguration: Map<String, String> = mutableMapOf()

    override val pluginsClasspath by parser.option(ArgTypeFile, description = "List of jars with dokka plugins")
        .multiple().also {
            Paths.get("./dokka-base.jar").toAbsolutePath().normalize().run {
                if (Files.exists(this)) (it.value as MutableList<File>).add(this.toFile())
                else throw FileNotFoundException("Dokka base plugin is not found! Make sure you placed 'dokka-base.jar' containing base plugin along the cli jar file")
            }
        }

    private val globalPackageOptions by parser.option(
        ArgType.String,
        description = "List of package passConfiguration in format \"prefix,-deprecated,-privateApi,+warnUndocumented,+suppress;...\" "
    ).default("")

    private val globalLinks by parser.option(
        ArgType.String,
        description =  "External documentation links in format url^packageListUrl^^url2..."
    ).default("")

    private val globalSrcLink by parser.option(
        ArgType.String,
        description =  "Mapping between a source directory and a Web site for browsing the code"
    ).multiple()

    init {
        parser.parse(args)

        passesConfigurations.all {
            it.perPackageOptions.cast<MutableList<DokkaConfiguration.PackageOptions>>().addAll(parsePerPackageOptions(globalPackageOptions))
        }
        passesConfigurations.all {
            it.externalDocumentationLinks.cast<MutableList<ExternalDocumentationLink>>().addAll(parseLinks(globalLinks))
        }

        globalSrcLink.map {
            val newSourceLinks = if (it.isNotEmpty() && it.contains("="))
                listOf(SourceLinkDefinitionImpl.parseSourceLinkDefinition(it))
            else {
                if (it.isNotEmpty()) {
                    DokkaConsoleLogger.warn("Invalid -srcLink syntax. Expected: <path>=<url>[#lineSuffix]. No source links will be generated.")
                }
                listOf()
            }

        }.run {
            passesConfigurations.all {
                it.sourceLinks.toMutableList().addAll(this)
            }
        }

        passesConfigurations.forEach {
            it.externalDocumentationLinks.cast<MutableList<ExternalDocumentationLink>>().addAll(defaultLinks(it))
        }
    }

    fun defaultLinks(config: DokkaConfiguration.PassConfiguration): MutableList<ExternalDocumentationLink> =
        mutableListOf<ExternalDocumentationLink>().apply {
            if (!config.noJdkLink)
                this += DokkaConfiguration.ExternalDocumentationLink
                    .Builder("https://docs.oracle.com/javase/${config.jdkVersion}/docs/api/")
                    .build()

            if (!config.noStdlibLink)
                this += ExternalDocumentationLink
                    .Builder("https://kotlinlang.org/api/latest/jvm/stdlib/")
                    .build()
        }
}

class PassArguments : DokkaConfiguration.PassConfiguration {

    val parser = ArgParser("passConfiguration")

    override val moduleName by parser.option(
        ArgType.String,
        description = "Name of the documentation module",
        fullName = "module"
    ).default("")
    override val classpath by parser.option(ArgType.String, description = "Classpath for symbol resolution").multiple()
    override val sourceRoots by parser.option(
        ArgTypeSourceRoot,
        description = "Source file or directory (allows many paths separated by the system path separator)",
        fullName = "src"
    ).multiple()
    override val samples by parser.option(ArgType.String, description = "Source root for samples").multiple()
    override val includes by parser.option(
        ArgType.String,
        description = "Markdown files to load (allows many paths separated by the system path separator)"
    ).multiple()
    override val includeNonPublic: Boolean by parser.option(ArgType.Boolean, description = "Include non public")
        .default(false)
    override val includeRootPackage by parser.option(ArgType.Boolean, description = "Include non public").default(false)
    override val reportUndocumented by parser.option(ArgType.Boolean, description = "Report undocumented members")
        .default(false)
    override val skipEmptyPackages by parser.option(
        ArgType.Boolean,
        description = "Do not create index pages for empty packages"
    ).default(false)
    override val skipDeprecated by parser.option(ArgType.Boolean, description = "Do not output deprecated members")
        .default(false)
    override val jdkVersion by parser.option(
        ArgType.Int,
        description = "Version of JDK to use for linking to JDK JavaDoc"
    ).default(8)
    override val languageVersion by parser.option(
        ArgType.String,
        description = "Language Version to pass to Kotlin analysis"
    )
    override val apiVersion by parser.option(
        ArgType.String,
        description = "Kotlin Api Version to pass to Kotlin analysis"
    )
    override val noStdlibLink by parser.option(ArgType.Boolean, description = "Disable documentation link to stdlib")
        .default(false)
    override val noJdkLink by parser.option(ArgType.Boolean, description = "Disable documentation link to JDK")
        .default(false)
    override val suppressedFiles by parser.option(ArgType.String, description = "Paths to files to be supperessed")
        .multiple()
    override val sinceKotlin by parser.option(
        ArgType.String,
        description = "Kotlin Api version to use as base version, if none specified"
    )
    override val collectInheritedExtensionsFromLibraries by parser.option(
        ArgType.Boolean,
        description = "Search for applicable extensions in libraries"
    ).default(false)
    override val analysisPlatform: Platform by parser.option(
        ArgTypePlatform,
        description = "Platform for analysis"
    ).default(Platform.DEFAULT)
    override val targets by parser.option(ArgType.String, description = "Generation targets").multiple()
    override val perPackageOptions by parser.option(
        ArgTypePackageOptions,
        description = "List of package passConfiguration in format \"prefix,-deprecated,-privateApi,+warnUndocumented,+suppress;...\" "
    ).multiple().default(mutableListOf<DokkaConfiguration.PackageOptions>())


    override val externalDocumentationLinks by parser.option(
        ArgTypeExternalDocumentationLink,
        description = "External documentation links in format url^packageListUrl^^url2..."
    ).multiple().default(mutableListOf<DokkaConfiguration.ExternalDocumentationLink>())

    override val sourceLinks by parser.option(
        ArgTypeSourceLinkDefinition,
        description = "Mapping between a source directory and a Web site for browsing the code",
        fullName = "srcLink"
    ).multiple()
}

object ArgTypeFile : ArgType<File>(true) {
    override fun convert(value: kotlin.String, name: kotlin.String): File = File(value)
    override val description: kotlin.String
        get() = "File wrapper"
}

object ArgTypeSourceRoot : ArgType<DokkaConfiguration.SourceRoot>(true) {
    override fun convert(value: kotlin.String, name: kotlin.String): DokkaConfiguration.SourceRoot =
        SourceRootImpl(value)

    override val description: kotlin.String
        get() = "SourceRoot wrapper"
}

object ArgTypePlatform : ArgType<Platform>(true) {
    override fun convert(value: kotlin.String, name: kotlin.String): Platform = Platform.fromString(value)
    override val description: kotlin.String
        get() = "Platform wrapper"
}

object ArgTypePackageOptions : ArgType<DokkaConfiguration.PackageOptions>(true) {
    override fun convert(value: kotlin.String, name: kotlin.String): DokkaConfiguration.PackageOptions =
        parsePerPackageOptions(value)

    override val description: kotlin.String
        get() = "PackageOptions wrapper"
}

object ArgTypeExternalDocumentationLink : ArgType<ExternalDocumentationLink>(true) {
    override fun convert(value: kotlin.String, name: kotlin.String): ExternalDocumentationLink = parseLink(value)
    override val description: kotlin.String
        get() = "ExternalDocumentationLink wrapper"
}

object ArgTypeSourceLinkDefinition : ArgType<DokkaConfiguration.SourceLinkDefinition>(true) {
    override fun convert(value: kotlin.String, name: kotlin.String): DokkaConfiguration.SourceLinkDefinition =
        if (value.isNotEmpty() && value.contains("="))
            SourceLinkDefinitionImpl.parseSourceLinkDefinition(value)
        else {
            throw IllegalArgumentException("Warning: Invalid -srcLink syntax. Expected: <path>=<url>[#lineSuffix]. No source links will be generated.")
        }

    override val description: kotlin.String
        get() = "SourceLinkDefinition wrapper"
}

object ArgTypeArgument : ArgType<PassArguments>(true) {
    override fun convert(value: kotlin.String, name: kotlin.String): PassArguments = PassArguments(value.split(" "))

    override val description: kotlin.String
        get() = "PassArguments wrapper"
}

fun parseLinks(links: String): List<ExternalDocumentationLink> {
    val (parsedLinks, parsedOfflineLinks) = links.split("^^")
        .map { it.split("^").map { it.trim() }.filter { it.isNotBlank() } }
        .filter { it.isNotEmpty() }
        .partition { it.size == 1 }

    return parsedLinks.map { (root) -> ExternalDocumentationLink.Builder(root).build() } +
            parsedOfflineLinks.map { (root, packageList) ->
                val rootUrl = URL(root)
                val packageListUrl =
                    try {
                        URL(packageList)
                    } catch (ex: MalformedURLException) {
                        File(packageList).toURI().toURL()
                    }
                ExternalDocumentationLink.Builder(rootUrl, packageListUrl).build()
            }
}

fun main(args: Array<String>) {
    val configuration = GlobalArguments(args)
    val generator = DokkaGenerator(configuration, DokkaConsoleLogger)
    generator.generate()
    DokkaConsoleLogger.report()
}