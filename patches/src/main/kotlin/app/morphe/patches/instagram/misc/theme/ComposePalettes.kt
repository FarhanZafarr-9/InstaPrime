/*
 * Copyright 2026 HushGram contributors
 * https://github.com/SysAdminDoc/HushGram
 */
package app.morphe.patches.instagram.misc.theme

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.classDefByOrNull as globalClassDefByOrNull
import app.morphe.patcher.patch.mutableClassDefBy as globalMutableClassDefBy
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patches.instagram.misc.extension.classesLoading
import app.morphe.patches.instagram.misc.extension.classesLoadingString
import app.morphe.patches.instagram.misc.extension.stringLoadedAt
import app.morphe.util.addInstructionsAtControlFlowLabel
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

private const val PATCH = "Accent color"

internal const val SWAP_COMPOSE = "$ACCENT_COLOR->composeColor(J)J"

/** The blues a palette has to store for the patch to know it found 451's: the dark link and Prism's indigo fill (#8). */
internal val NEEDED_PALETTE_BLUES = listOf(0x85a1ff, 0x4a5df9)

internal const val SWAP_SURFACE = "$ACCENT_THEME->composeSurface(J)J"

/**
 * The fields holding the grays a dark Compose screen paints itself with (#577), then the white page and
 * the search fields' gray a light one does (#639), by palette. The dark screens' text is in neither.
 */
internal val PALETTE_SURFACES = mapOf(
    "Lcom/instagram/compose/core/theme/BasePrismColors;" to
        listOf("BLACK", "GRAY_8", "GRAY_9", "GRAY_10", "GRAY_1100", "WHITE", "GRAY_1"),
    "Lcom/instagram/compose/core/theme/BasePrismColorsV2;" to
        listOf("GRAY_1300", "GRAY_1400", "GRAY_1500", "GRAY_1600", "WHITE", "GRAY_0200", "GRAY_0300"),
)

private val WIDE_CONSTS = setOf(Opcode.CONST_WIDE, Opcode.CONST_WIDE_32, Opcode.CONST_WIDE_16)

private fun refuse(why: String): Nothing = throw PatchException("$PATCH: $why")

/** An accent blue a Compose palette's set-up stores: in [method], the `sput-wide` at [store] of the pair from [register]. */
internal class PaletteStore(val method: Method, val store: Int, val register: Int, val argb: Int)

/**
 * Every accent blue Instagram's Compose palettes store as they're set up. Compose screens such as
 * the Activity tab and a profile's bio take their colors from palettes written into the code, not
 * from the theme, so no theme read or style reaches them (#8). A palette's static initializer writes
 * each color to a static long: its ARGB as a `const-wide`, made a Compose color by shifting it up 32
 * bits (`shl-long/2addr` by a register last set to 32) or by a static (J)J, then `sput-wide` to a
 * field of its own class. The classes read are the ones the literal index names for one of the
 * blues at full alpha, and in each every blue stored counts, see-through ones too.
 *
 * 451 keeps them in BasePrismColors, BasePrismColorsV2 and a holder of the dark link alone. Refuses
 * unless [NEEDED_PALETTE_BLUES] are among them, or without the extension's hook.
 */
internal fun BytecodePatchContext.findPaletteStores(): List<PaletteStore> {
    val classes = ACCENT_BLUES.keys.flatMap { classesLoading(0xff000000L or it.toLong()) }.distinctBy { it.type }
    val stores = classes.flatMap { classDef -> classDef.methods.filter { it.name == "<clinit>" }.flatMap { it.paletteStores() } }
    for (needed in NEEDED_PALETTE_BLUES) {
        if (stores.none { it.argb and 0xffffff == needed }) refuse("no Compose palette stores #%06x".format(needed))
    }
    if (classDefByOrNull(ACCENT_COLOR)?.methods?.any {
            "${it.definingClass}->${it.name}(${it.parameterTypes.joinToString("")})${it.returnType}" == SWAP_COMPOSE &&
                AccessFlags.STATIC.isSet(it.accessFlags) && AccessFlags.PUBLIC.isSet(it.accessFlags)
        } != true
    ) {
        refuse("the extension has no public static $SWAP_COMPOSE")
    }
    return stores
}

/**
 * Each blue [stores] names goes through AccentColor.composeColor right before it's stored, at the
 * store's own label, so the palette holds the picked color's shade from the start. Each palette is
 * read again as it stands now, in case another patch changed it after it was found.
 */
internal fun BytecodePatchContext.swapComposePalettes(stores: List<PaletteStore> = findPaletteStores()) {
    for (type in stores.map { it.method.definingClass }.distinct()) {
        val setUp = mutableClassDefBy(type).methods.single { it.name == "<clinit>" }
        val now = setUp.paletteStores()
        if (now.isEmpty()) refuse("$type's set-up no longer stores a blue")
        for (store in now.asReversed()) {
            setUp.addInstructionsAtControlFlowLabel(
                store.store,
                """
                    invoke-static/range { v${store.register} .. v${store.register + 1} }, $SWAP_COMPOSE
                    move-result-wide v${store.register}
                """,
            )
        }
    }
}

/** Refuses unless every palette holds its [PALETTE_SURFACES] and a set-up to repaint them in, and the extension has the hook. */
internal fun BytecodePatchContext.requirePaletteSurfaces() {
    for ((type, fields) in PALETTE_SURFACES) {
        val palette = classDefByOrNull(type) ?: refuse("$type is gone")
        val held = palette.fields.map { it.name }.toSet()
        fields.firstOrNull { it !in held }?.let { refuse("$type has no $it") }
        if (palette.methods.none { it.name == "<clinit>" }) refuse("$type has no set-up")
    }
    if (classDefByOrNull(ACCENT_THEME)?.methods?.any {
            "${it.definingClass}->${it.name}(${it.parameterTypes.joinToString("")})${it.returnType}" == SWAP_SURFACE &&
                AccessFlags.STATIC.isSet(it.accessFlags) && AccessFlags.PUBLIC.isSet(it.accessFlags)
        } != true
    ) {
        refuse("the extension has no public static $SWAP_SURFACE")
    }
}

/**
 * Backgrounds in the accent color's and Classic colors' Compose half (#577, #639): the message list and
 * other Compose screens paint their rows from these palettes, so the theme styles never reach them. Each surface is read
 * back through AccentTheme.composeSurface and stored again as the set-up ends, after every store and
 * Pure black's rewrite. The registers are free there, right before the return.
 */
internal fun BytecodePatchContext.swapComposeSurfaces() {
    for ((type, fields) in PALETTE_SURFACES) {
        val setUp = mutableClassDefBy(type).methods.single { it.name == "<clinit>" }
        val returns = setUp.implementation!!.instructions.withIndex().filter { it.value.opcode == Opcode.RETURN_VOID }.map { it.index }
        if (returns.isEmpty()) refuse("$type's set-up has no return")
        val repaint = fields.joinToString("\n") {
            """
                sget-wide v0, $type->$it:J
                invoke-static/range { v0 .. v1 }, $SWAP_SURFACE
                move-result-wide v0
                sput-wide v0, $type->$it:J
            """
        }
        for (at in returns.asReversed()) setUp.addInstructionsAtControlFlowLabel(at, repaint)
    }
}

/** The accent blues this method stores into its own class's static longs as Compose colors, in order. */
internal fun Method.paletteStores(): List<PaletteStore> {
    val code = implementation?.instructions?.toList().orEmpty()
    val stores = ArrayList<PaletteStore>()
    for ((at, instruction) in code.withIndex()) {
        if (instruction.opcode !in WIDE_CONSTS) continue
        val value = (instruction as WideLiteralInstruction).wideLiteral
        if (value ushr 32 != 0L) continue
        val argb = value.toInt()
        if (argb ushr 24 == 0 || ACCENT_BLUES[argb and 0xffffff] == null) continue
        val register = (instruction as OneRegisterInstruction).registerA
        val next = code.getOrNull(at + 1) ?: continue
        val store = when {
            next.opcode == Opcode.SHL_LONG_2ADDR && (next as TwoRegisterInstruction).registerA == register &&
                next.registerB != register && next.registerB != register + 1 &&
                code.lastWriteBefore(at, next.registerB)?.let { it is NarrowLiteralInstruction && it.narrowLiteral == 32 } == true -> at + 2
            next.convertsPair(register) && code.getOrNull(at + 2)?.let {
                it.opcode == Opcode.MOVE_RESULT_WIDE && (it as OneRegisterInstruction).registerA == register
            } == true -> at + 3
            else -> continue
        }
        val put = code.getOrNull(store) ?: continue
        if (put.opcode != Opcode.SPUT_WIDE || (put as OneRegisterInstruction).registerA != register) continue
        val field = (put as ReferenceInstruction).reference as FieldReference
        if (field.definingClass != definingClass || field.type != "J") continue
        stores += PaletteStore(this, store, register, argb)
    }
    return stores
}

/** Whether this is `invoke-static {vN, vN+1}` of a static (J)J, the pair being [register]'s. */
private fun Instruction.convertsPair(register: Int): Boolean {
    if (opcode != Opcode.INVOKE_STATIC) return false
    val called = (this as ReferenceInstruction).reference as MethodReference
    if (called.parameterTypes.map(Any::toString) != listOf("J") || called.returnType != "J") return false
    val passed = this as FiveRegisterInstruction
    return passed.registerCount == 2 && passed.registerC == register && passed.registerD == register + 1
}

/** The last instruction before [at] writing [register], as one register or half of a pair, or null for none. */
private fun List<Instruction>.lastWriteBefore(at: Int, register: Int): Instruction? =
    subList(0, at).lastOrNull { instruction ->
        val written = (instruction as? OneRegisterInstruction)?.registerA
        instruction.opcode.setsRegister() && written != null &&
            (written == register || instruction.opcode.setsWideRegister() && written + 1 == register)
    }

internal val threadRows = listOf(
    "com.instagram.direct.inbox.feature.threadrow.ui.ThreadRow (ThreadRow.kt:77)",
    "com.instagram.direct.inbox.feature.threadrow.ui.ThreadRowV2 (ThreadRowV2.kt:105)",
)

internal const val inboxPromptRow =
    "instagram.features.direct.inbox.feature.emptystate.ui.InboxEmptyStateRow (InboxEmptyStateRow.kt:69)"
internal const val activityRowContent =
    "com.instagram.newsfeed.fragment.NewsfeedRow.<anonymous> (NewsfeedYouComposeFragment.kt:3633)"
internal const val activityFeedContent =
    "com.instagram.newsfeed.fragment.ActivityFeed (NewsfeedYouComposeFragment.kt:2809)"

private fun BytecodePatchContext.inboxRenderer(label: String): Method? =
    classesLoadingString(label).flatMap { it.methods }.singleOrNull { candidate ->
        val code = candidate.implementation?.instructions?.toList().orEmpty()
        candidate.returnType == "V" && code.indices.any { stringLoadedAt(code, it) == label }
    }

/** UI tags survive release builds that strip Compose's source-location trace strings. */
@Suppress("DEPRECATION") // Shared index helpers still take the legacy context.
internal fun BytecodePatchContext.rowRenderers(tag: String): List<Method> =
    classesLoadingString(tag).flatMap { owner -> owner.methods.filter { method ->
        val code = method.implementation?.instructions?.toList().orEmpty()
        code.indices.any { stringLoadedAt(code, it) == tag } || tag in outlinedLabels(owner, method)
    } }

@Suppress("DEPRECATION")
internal fun BytecodePatchContext.inboxRenderers(): List<Method> =
    rowRenderers("row_inbox_container").filter { method ->
        method.returnType == "V" && method.implementation!!.instructions.any { it.backgroundModifier() != null }
    }.ifEmpty { threadRows.mapNotNull { inboxRenderer(it) } }

/** Clone only the inbox click paths, retaining native handlers, labels and interaction sources. */
@Suppress("DEPRECATION") // Shared index helpers still take the legacy context.
internal fun BytecodePatchContext.tintInboxRowPresses() {
    val action = "Lkotlin/jvm/functions/Function0;"
    val string = "Ljava/lang/String;"
    fun MethodReference.isDelegate() = parameterTypes.size == 9 && returnType == parameterTypes[1].toString() &&
        parameterTypes.drop(3).map(CharSequence::toString) == listOf(string, string, action, action, "Z", "Z")
    fun MethodReference.isWrapper() = parameterTypes.size == 6 && returnType == parameterTypes[0].toString() &&
        parameterTypes.drop(2).map(CharSequence::toString) == listOf(string, action, action, "Z")
    val rows = inboxRenderers()
    if (rows.size != 2) refuse("expected two inbox row layouts, found ${rows.size}")
    val sites = rows.map { row ->
        val code = row.implementation!!.instructions.toList()
        val at = code.indices.singleOrNull { index ->
            code[index].opcode == Opcode.INVOKE_STATIC_RANGE &&
                code[index].target()?.let { it.isWrapper() || it.isDelegate() } == true
        } ?: refuse("inbox combined-click call changed in $row")
        Triple(row, at, code[at])
    }
    val replacements = sites.map { it.third.target()!! }.distinctBy { it.toString() }.associate { original ->
        val owner = globalClassDefByOrNull(original.definingClass) ?: refuse("missing click owner")
        val entry = owner.methods.single { it.toString() == original.toString() }
        val delegate = if (original.isDelegate()) original else entry.implementation!!.instructions.mapNotNull { it.target() }
            .singleOrNull { it.definingClass == owner.type && it.isDelegate() }
            ?: refuse("inbox click delegate changed")
        val body = owner.methods.single { it.toString() == delegate.toString() }
        val code = body.implementation!!.instructions.toList()
        val ctorAt = code.indices.singleOrNull { index -> code[index].target()?.let {
            it.name == "<init>" && it.parameterTypes.size == 11 &&
                it.parameterTypes.drop(1).map(CharSequence::toString) ==
                    listOf(delegate.parameterTypes[0].toString(), delegate.parameterTypes[2].toString(),
                        string, string, action, action, action, "Z", "Z", "Z")
        } == true } ?: refuse("inbox indication constructor changed")
        val ctor = code[ctorAt].target()!!
        val call = code[ctorAt] as? RegisterRangeInstruction ?: refuse("inbox indication registers changed")
        val indicationType = ctor.parameterTypes[0].toString()
        val indication = classesLoadingString("HighlightIndication(highlightColor=").singleOrNull {
            AccessFlags.PUBLIC.isSet(it.accessFlags) && indicationType in it.interfaces
        } ?: refuse("inbox highlight indication changed")
        val create = indication.methods.singleOrNull {
            it.name == "<init>" && AccessFlags.PUBLIC.isSet(it.accessFlags) &&
                it.parameterTypes.map(CharSequence::toString) == listOf("J")
        } ?: refuse("inbox highlight constructor changed")
        val mutableOwner = globalMutableClassDefBy(owner.type)
        val helper = mutableOwner.methods.singleOrNull { it.name == "instaprimeInboxIndication" } ?: ImmutableMethod(
            owner.type, "instaprimeInboxIndication", listOf(ImmutableMethodParameter(indicationType, emptySet(), null)),
            indicationType, AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
            emptySet(), emptySet(), ImmutableMethodImplementation(7, emptyList(), null, null),
        ).toMutable().apply {
            addInstructions(0, """
                invoke-static {}, $ACCENT_THEME->inboxPressColor()J
                move-result-wide v0
                const-wide/16 v2, 0x0
                cmp-long v4, v0, v2
                if-eqz v4, :native
                new-instance v5, ${indication.type}
                invoke-direct { v5, v0, v1 }, $create
                return-object v5
                :native
                return-object p0
            """)
            mutableOwner.methods.add(this)
        }
        fun copy(method: Method, name: String) = ImmutableMethod(
            method.definingClass, name, method.parameters, method.returnType, method.accessFlags,
            method.annotations, method.hiddenApiRestrictions, method.implementation,
        ).toMutable()
        val scopedBody = mutableOwner.methods.singleOrNull { it.name == "instaprimeInboxClickDelegate" }
            ?: copy(body, "instaprimeInboxClickDelegate").apply {
                val register = call.startRegister + 1
                addInstructionsAtControlFlowLabel(ctorAt, """
                    invoke-static/range { v$register .. v$register }, $helper
                    move-result-object v$register
                """)
                mutableOwner.methods.add(this)
            }
        val scoped = if (entry.toString() == body.toString()) scopedBody else copy(entry, "instaprimeInboxClickable").apply {
            val index = entry.implementation!!.instructions.indexOfFirst { it.target()?.toString() == delegate.toString() }
            val nativeCall = entry.implementation!!.instructions.elementAt(index) as? RegisterRangeInstruction
                ?: refuse("inbox click wrapper registers changed")
            replaceInstruction(index, "invoke-static/range { v${nativeCall.startRegister} .. v${nativeCall.startRegister + nativeCall.registerCount - 1} }, $scopedBody")
            mutableOwner.methods.add(this)
        }
        original.toString() to scoped
    }
    for ((row, at, call) in sites) {
        val range = call as RegisterRangeInstruction
        val scoped = replacements.getValue(call.target().toString())
        globalMutableClassDefBy(row.definingClass).methods.single { it.toString() == row.toString() }
            .replaceInstruction(at, "invoke-static/range { v${range.startRegister} .. v${range.startRegister + range.registerCount - 1} }, $scoped")
    }
}

private fun Instruction.target(): MethodReference? = (this as? ReferenceInstruction)?.reference as? MethodReference

internal fun Instruction.backgroundModifier(): MethodReference? {
    if (opcode != Opcode.INVOKE_STATIC && opcode != Opcode.INVOKE_STATIC_RANGE) return null
    return target()?.takeIf {
        it.returnType.startsWith("L") && it.parameterTypes.map(CharSequence::toString).let { args ->
            args == listOf(it.returnType, "J") ||
                args.size == 3 && args[0] == it.returnType && args[1].startsWith("L") && args[2] == "J"
        }
    }
}

/** Packed color is the final argument; 456 supplies the native shape explicitly. */
private fun Instruction.colorRegister(background: MethodReference): Int? {
    val words = background.parameterTypes.size + 1
    return when (this) {
        is RegisterRangeInstruction -> (startRegister + words - 2).takeIf { registerCount == words }
        is FiveRegisterInstruction -> {
            val registers = listOf(registerC, registerD, registerE, registerF, registerG).take(registerCount)
            registers.getOrNull(words - 2)?.takeIf { low ->
                registerCount == words && registers.last() == low + 1 &&
                    registers.dropLast(2).none { it == low || it == low + 1 }
            }
        }
        else -> null
    }
}

/** The Activity row's body can be outlined from the lambda that holds its trace label. */
@Suppress("DEPRECATION")
internal fun BytecodePatchContext.activityRowRenderer(background: MethodReference): Method? =
    rowRenderers("bnf_friend_request_pinned_row").filter { method ->
        method.implementation!!.instructions.any { it.backgroundModifier() == background }
    }.singleOrNull() ?: classesLoadingString(activityRowContent).flatMap { owner ->
        owner.methods.filter { method ->
            val code = method.implementation?.instructions?.toList().orEmpty()
            code.any { it.backgroundModifier() == background } &&
                (code.indices.any { stringLoadedAt(code, it) == activityRowContent } ||
                    activityRowContent in outlinedLabels(owner, method))
        }
    }.singleOrNull()

/** Most Activity rows have no opaque fill of their own and show the lazy list's backdrop. */
@Suppress("DEPRECATION")
private fun BytecodePatchContext.tintActivityList(activityRow: Method, background: MethodReference) {
    val feed = rowRenderers("activity_feed_list").singleOrNull { it.returnType == "V" }
        ?: inboxRenderer(activityFeedContent) ?: refuse("Activity list renderer changed")
    val composer = feed.parameterTypes.firstOrNull()?.toString() ?: return
    val modifier = background.returnType
    val rowCode = activityRow.implementation!!.instructions.toList()
    val fillAt = rowCode.indices.singleOrNull { rowCode[it].backgroundModifier() == background }
        ?: refuse("Activity row background call changed")
    val fill = rowCode[fillAt]
    val colorRegister = fill.colorRegister(background) ?: refuse("Activity row color registers changed")
    val paletteField = (rowCode.lastWriteBefore(fillAt, colorRegister) as? ReferenceInstruction)
        ?.reference as? FieldReference
    val paletteGetter = paletteField?.takeIf { it.type == "J" }?.let { field ->
        rowCode.take(fillAt).mapNotNull { it.target() }.distinctBy { it.toString() }.singleOrNull {
            it.returnType == field.definingClass && it.parameterTypes.map(CharSequence::toString) == listOf(composer)
        }
    }
    val getter = if (paletteGetter != null) null else rowCode.mapNotNull { instruction ->
        val call = instruction.target() ?: return@mapNotNull null
        if (call.returnType != "J" || call.parameterTypes.map(CharSequence::toString) != listOf(composer)) return@mapNotNull null
        val method = globalClassDefByOrNull(call.definingClass)?.methods?.singleOrNull { it.toString() == call.toString() }
            ?: return@mapNotNull null
        call.takeIf { AccessFlags.STATIC.isSet(method.accessFlags) &&
            method.implementation?.instructions?.count { it.opcode == Opcode.IGET_WIDE } == 1 }
    }.distinct().singleOrNull()
    if (paletteGetter == null && getter == null) refuse("Activity background palette read changed")
    val colorRead = if (paletteGetter != null) """
        invoke-static { p0 }, $paletteGetter
        move-result-object v2
        iget-wide v0, v2, $paletteField
    """ else """
        invoke-static { p0 }, $getter
        move-result-wide v0
    """
    val applyBackground = if (background.parameterTypes.size == 2) """
        invoke-static { p2, v0, v1 }, $background
    """ else {
        val shapeRegister = when (fill) {
            is FiveRegisterInstruction -> fill.registerD
            is RegisterRangeInstruction -> fill.startRegister + 1
            else -> refuse("Activity background shape registers changed")
        }
        val shapeLoad = rowCode.lastWriteBefore(fillAt, shapeRegister)
        val shape = (shapeLoad as? ReferenceInstruction)?.reference as? FieldReference
        if (shapeLoad?.opcode != Opcode.SGET_OBJECT || shape?.type != background.parameterTypes[1].toString()) {
            refuse("Activity background shape changed")
        }
        """
            sget-object v2, $shape
            invoke-static { p2, v2, v0, v1 }, $background
        """
    }
    val code = feed.implementation!!.instructions.toList()
    val listIndex = code.indices.singleOrNull { index ->
        val instruction = code[index]
        if (instruction.opcode != Opcode.INVOKE_STATIC_RANGE) return@singleOrNull false
        val target = instruction.target() ?: return@singleOrNull false
        val args = target.parameterTypes.map(CharSequence::toString)
        target.returnType == "V" && args.size == 16 &&
            args[3] == "Landroidx/compose/foundation/lazy/LazyListState;" &&
            args[4] == composer && args[5].startsWith("L") && args[6] == modifier
    } ?: refuse("Activity lazy-list call changed")
    val listCall = code[listIndex].target() ?: return
    val arguments = listCall.parameterTypes.subList(4, 7).map(CharSequence::toString)
    val bridge = ImmutableMethod(
        feed.definingClass, "instaprimeActivityListBackground",
        arguments.map { ImmutableMethodParameter(it, emptySet(), null) },
        modifier, AccessFlags.PRIVATE.value or AccessFlags.STATIC.value,
        emptySet(), emptySet(), ImmutableMethodImplementation(8, emptyList(), null, null),
    ).toMutable().apply {
        addInstructions(0, """
            $colorRead
            invoke-static { v0, v1 }, $ACCENT_THEME->activityFeedBackground(J)J
            move-result-wide v0
            const-wide/16 v2, 0x0
            cmp-long v4, v0, v2
            if-eqz v4, :native
            $applyBackground
            move-result-object p2
            :native
            return-object p2
        """)
    }
    val owner = globalMutableClassDefBy(feed.definingClass)
    owner.methods.add(bridge)
    val first = (code[listIndex] as RegisterRangeInstruction).startRegister
    owner.methods.single { it.toString() == feed.toString() }.addInstructionsAtControlFlowLabel(
        listIndex,
        """
            invoke-static/range { v${first + 4} .. v${first + 6} }, $bridge
            move-result-object v${first + 6}
        """,
    )
}

/** Inbox and Activity rows paint their Compose palette's background over the screen surface. */
@Suppress("DEPRECATION") // Shared index helpers still take the legacy context.
internal fun BytecodePatchContext.tintInboxRowBackgrounds() {
    val threads = inboxRenderers()
    val prompt = inboxRenderer(inboxPromptRow)
    // The prompt renderer also uses a different modifier of the same signature.
    val background = threads.mapNotNull { thread ->
        thread.implementation!!.instructions.mapNotNull { it.backgroundModifier() }.singleOrNull()
    }.distinct().singleOrNull() ?: refuse("inbox row background helper changed")
    val activity = activityRowRenderer(background) ?: refuse("Activity row renderer changed")
    tintActivityList(activity, background)
    for (method in threads + listOfNotNull(prompt, activity)) {
        val code = method.implementation!!.instructions.toList()
        val fills = code.indices.filter { code[it].backgroundModifier() == background }
        // Thread rows have one fill. Prompts and Activity content can branch by layout.
        if (fills.isEmpty() || (method in threads && fills.size != 1)) refuse("row background fill changed in $method")
        val renderer = globalMutableClassDefBy(method.definingClass).methods.single { it.toString() == method.toString() }
        for (index in fills.asReversed()) {
            val register = code[index].colorRegister(background) ?: refuse("row background color registers changed in $method")
            renderer.addInstructionsAtControlFlowLabel(
                index,
                """
                    invoke-static/range { v$register .. v${register + 1} }, $ACCENT_THEME->inboxRowBackground(J)J
                    move-result-wide v$register
                """,
            )
        }
    }
}
