package es.hugoalvarezajenjo.sproutstudio.domain.plantuml

/** "Go to Declaration", "Find Usages" and the symbol under the caret, on plain text. */
object Navigation {

    /** Asked in order: the first one with an anchor at the offset decides. */
    val providers: List<DefinitionProvider> = listOf(IncludeDefinitions, SymbolDefinitions, IncludedDeclarations)

    /**
     * Where ⌘B at [offset] goes; null when there is nothing to go to. [file] (the text's absolute
     * path) and [includes] let it follow `!include`s to names declared in other files.
     */
    fun definitionAt(text: String, offset: Int, file: String? = null, includes: IncludeReader = IncludeReader.NONE): NavigationTarget? {
        val ctx = NavigationContext(SymbolIndex.of(text), file, includes)
        val p = providers.firstOrNull { it.anchorAt(ctx, offset) != null } ?: return null
        return p.targetAt(ctx, offset)
    }

    /** The span ⌘-click at [offset] would follow, for the hover underline; null if it leads nowhere. */
    fun linkAt(text: String, offset: Int, file: String? = null, includes: IncludeReader = IncludeReader.NONE): IntRange? {
        val ctx = NavigationContext(SymbolIndex.of(text), file, includes)
        val p = providers.firstOrNull { it.anchorAt(ctx, offset) != null } ?: return null
        return if (p.targetAt(ctx, offset) != null) p.anchorAt(ctx, offset) else null
    }

    /** Every occurrence of the symbol at [offset] (empty when it isn't on one). */
    fun occurrencesAt(text: String, offset: Int): List<Occurrence> {
        val index = SymbolIndex.of(text)
        return index.occurrenceAt(offset)?.let(index::usagesOf).orEmpty()
    }

    /** "Find Usages" at [offset]: the symbol's other occurrences, declaration included; null off a symbol. */
    fun usagesAt(text: String, offset: Int): NavigationTarget.Usages? {
        val index = SymbolIndex.of(text)
        val at = index.occurrenceAt(offset) ?: return null
        val others = index.usagesOf(at).filter { it.range != at.range }
        return if (others.isEmpty()) null else NavigationTarget.Usages(at.name, others)
    }
}

/** A name: a reference goes to its declaration; on the declaration, to its usages (as IntelliJ). */
private object SymbolDefinitions : DefinitionProvider {
    override fun anchorAt(ctx: NavigationContext, offset: Int) = ctx.index.occurrenceAt(offset)?.range

    override fun targetAt(ctx: NavigationContext, offset: Int): NavigationTarget? {
        val index = ctx.index
        val at = index.occurrenceAt(offset) ?: return null
        val decl = index.declarationOf(at)
        if (decl.range != at.range) return NavigationTarget.InText(decl.range)
        val others = index.usagesOf(at).filter { it.range != at.range }
        return when (others.size) {
            0 -> null
            1 -> NavigationTarget.InText(others.single().range)
            else -> NavigationTarget.Usages(at.name, others)
        }
    }
}

/** `!include file.puml`: the included file. Stdlib (`<C4/C4>`) and URLs have no file to open. */
private object IncludeDefinitions : DefinitionProvider {
    override fun anchorAt(ctx: NavigationContext, offset: Int) =
        ctx.index.includeAt(offset)?.takeIf { it.isLocal }?.range

    override fun targetAt(ctx: NavigationContext, offset: Int): NavigationTarget? =
        anchorAt(ctx, offset)?.let { NavigationTarget.IncludedFile(ctx.index.includeAt(offset)!!.path) }
}

/**
 * A procedure, function, macro or `$variable` this text uses but doesn't declare: its declaration
 * in a local file it includes, directly or through other includes. Files are searched depth-first
 * in include order and the first declaration wins; a file is visited once, so cycles end.
 */
private object IncludedDeclarations : DefinitionProvider {
    private const val MAX_FILES = 64

    override fun anchorAt(ctx: NavigationContext, offset: Int) =
        ctx.index.unknownAt(offset)?.takeIf { find(ctx, it.name) != null }?.range

    override fun targetAt(ctx: NavigationContext, offset: Int): NavigationTarget? {
        val name = ctx.index.unknownAt(offset)?.name ?: return null
        val (file, decl) = find(ctx, name) ?: return null
        return NavigationTarget.InFile(file.path, decl.range)
    }

    private fun find(ctx: NavigationContext, name: String): Pair<SourceFile, Occurrence>? {
        if (ctx.includes === IncludeReader.NONE) return null
        val seen = HashSet<String>()
        ctx.file?.let(seen::add)

        fun visit(from: String?, includes: List<IncludeRef>): Pair<SourceFile, Occurrence>? {
            for (inc in includes) {
                if (!inc.isLocal || seen.size >= MAX_FILES) continue
                val f = ctx.includes.read(from, inc.path) ?: continue
                if (!seen.add(f.path)) continue
                f.index.globalDeclaration(name)?.let { return f to it }
                visit(f.path, f.index.includes)?.let { return it }
            }
            return null
        }
        return visit(ctx.file, ctx.index.includes)
    }
}

/** A file of the user's own (not the stdlib, not a URL) that navigation can open. */
private val IncludeRef.isLocal: Boolean get() = !stdlib && !isUrl && path.isNotEmpty()
