/*
 * Copyright 2026 HushGram contributors
 * https://github.com/SysAdminDoc/HushGram
 */
package app.morphe.patches.instagram.misc.theme

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
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

private fun BytecodePatchContext.inboxRenderers(): List<Method> = threadRows.mapNotNull { inboxRenderer(it) }

/** Supply an explicit indication only at the native thread rows' combined-click modifier calls. */
internal fun BytecodePatchContext.tintInboxRowPresses() {
    val string = "Ljava/lang/String;"
    val action = "Lkotlin/jvm/functions/Function0;"
    val sites = inboxRenderers().mapNotNull { row ->
        val code = row.implementation!!.instructions.toList()
        val at = code.indices.singleOrNull { index ->
            val call = code[index]
            if (call.opcode != Opcode.INVOKE_STATIC_RANGE) return@singleOrNull false
            val target = call.target() ?: return@singleOrNull false
            val args = target.parameterTypes.map(CharSequence::toString)
            args.size == 6 && args[0] == target.returnType && args[0].startsWith("L") &&
                args[1].startsWith("L") && args.drop(2) == listOf(string, action, action, "Z")
        } ?: return@mapNotNull null
        Triple(row, at, code[at])
    }
    if (sites.isEmpty()) return
    val original = sites.mapNotNull { it.third.target() }.distinct().singleOrNull() ?: return
    val owner = classDefByOrNull(original.definingClass) ?: return
    val wrapper = owner.methods.singleOrNull { it.toString() == original.toString() } ?: return
    val modifier = wrapper.returnType
    val role = wrapper.parameterTypes[1].toString()
    val delegate = wrapper.implementation?.instructions?.mapNotNull { it.target() }?.singleOrNull {
        it.definingClass == owner.type && it.returnType == modifier &&
            it.parameterTypes.drop(1).map(CharSequence::toString) == listOf(modifier, role, string, string, action, action, "Z", "Z")
    } ?: return
    val body = owner.methods.singleOrNull { it.toString() == delegate.toString() }?.implementation ?: return
    val element = body.instructions.mapNotNull { it.target() }.singleOrNull {
        it.name == "<init>" && it.parameterTypes.size == 11 &&
            it.parameterTypes.drop(1).map(CharSequence::toString) ==
                listOf(delegate.parameterTypes[0].toString(), role, string, string, action, action, action, "Z", "Z", "Z")
    } ?: return
    val append = body.instructions.singleOrNull {
        it.opcode == Opcode.INVOKE_INTERFACE && it.target()?.let { ref ->
            ref.definingClass == modifier && ref.returnType == modifier && ref.parameterTypes.map(CharSequence::toString) == listOf(modifier)
        } == true
    }?.target() ?: return
    val indication = classesLoadingString("HighlightIndication(highlightColor=").singleOrNull {
        AccessFlags.PUBLIC.isSet(it.accessFlags) && it.interfaces.contains(element.parameterTypes[0].toString())
    } ?: return
    val create = indication.methods.singleOrNull {
        it.name == "<init>" && AccessFlags.PUBLIC.isSet(it.accessFlags) && it.parameterTypes.map(CharSequence::toString) == listOf("J")
    } ?: return
    val name = "instaprimeInboxClickable"
    val scoped = ImmutableMethod(
        owner.type, name, wrapper.parameters, modifier, AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
        emptySet(), emptySet(), ImmutableMethodImplementation(18, emptyList(), null, null),
    ).toMutable().apply {
        addInstructions(0, """
            invoke-static {}, $ACCENT_THEME->inboxPressColor()J
            move-result-wide v2
            const-wide/16 v4, 0x0
            cmp-long v6, v2, v4
            if-eqz v6, :native
            new-instance v1, ${indication.type}
            invoke-direct { v1, v2, v3 }, $create
            new-instance v0, ${element.definingClass}
            const/4 v2, 0x0
            move-object/from16 v3, p1
            const/4 v4, 0x0
            move-object/from16 v5, p2
            move-object/from16 v6, p4
            move-object/from16 v7, p3
            const/4 v8, 0x0
            const/4 v9, 0x1
            move/from16 v10, p5
            const/4 v11, 0x1
            invoke-direct/range { v0 .. v11 }, $element
            invoke-interface { p0, v0 }, $append
            move-result-object v0
            return-object v0
            :native
            invoke-static/range { p0 .. p5 }, $original
            move-result-object v0
            return-object v0
        """)
    }
    mutableClassDefBy(owner.type).methods.add(scoped)
    for ((row, at, call) in sites) {
        val start = (call as RegisterRangeInstruction).startRegister
        mutableClassDefBy(row.definingClass).methods.single { it.toString() == row.toString() }
            .replaceInstruction(at, "invoke-static/range { v$start .. v${start + 5} }, $scoped")
    }
}

private fun Instruction.target(): MethodReference? = (this as? ReferenceInstruction)?.reference as? MethodReference

private fun Instruction.backgroundModifier(): MethodReference? {
    if (opcode != Opcode.INVOKE_STATIC && opcode != Opcode.INVOKE_STATIC_RANGE) return null
    return target()?.takeIf {
        it.returnType.startsWith("L") && it.parameterTypes.map(CharSequence::toString) == listOf(it.returnType, "J")
    }
}

/** The Activity row's body can be outlined from the lambda that holds its trace label. */
private fun BytecodePatchContext.activityRowRenderer(background: MethodReference): Method? =
    classesLoadingString(activityRowContent).flatMap { owner ->
        owner.methods.filter { method ->
            val code = method.implementation?.instructions?.toList().orEmpty()
            code.any { it.backgroundModifier() == background } &&
                (code.indices.any { stringLoadedAt(code, it) == activityRowContent } ||
                    activityRowContent in outlinedLabels(owner, method))
        }
    }.singleOrNull()

/** Most Activity rows have no opaque fill of their own and show the lazy list's backdrop. */
private fun BytecodePatchContext.tintActivityList(activityRow: Method, background: MethodReference) {
    val feed = inboxRenderer(activityFeedContent) ?: return
    val composer = feed.parameterTypes.firstOrNull()?.toString() ?: return
    val modifier = background.returnType
    val getter = activityRow.implementation!!.instructions.mapNotNull { instruction ->
        val call = instruction.target() ?: return@mapNotNull null
        if (call.returnType != "J" || call.parameterTypes.map(CharSequence::toString) != listOf(composer)) return@mapNotNull null
        val method = classDefByOrNull(call.definingClass)?.methods?.singleOrNull { it.toString() == call.toString() }
            ?: return@mapNotNull null
        call.takeIf { AccessFlags.STATIC.isSet(method.accessFlags) &&
            method.implementation?.instructions?.count { it.opcode == Opcode.IGET_WIDE } == 1 }
    }.distinct().singleOrNull() ?: return
    val code = feed.implementation!!.instructions.toList()
    val listIndex = code.indices.singleOrNull { index ->
        val instruction = code[index]
        if (instruction.opcode != Opcode.INVOKE_STATIC_RANGE) return@singleOrNull false
        val target = instruction.target() ?: return@singleOrNull false
        val args = target.parameterTypes.map(CharSequence::toString)
        target.returnType == "V" && args.size == 16 &&
            args[3] == "Landroidx/compose/foundation/lazy/LazyListState;" &&
            args[4] == composer && args[5].startsWith("L") && args[6] == modifier
    } ?: return
    val listCall = code[listIndex].target() ?: return
    val arguments = listCall.parameterTypes.subList(4, 7).map(CharSequence::toString)
    val bridge = ImmutableMethod(
        feed.definingClass, "instaprimeActivityListBackground",
        arguments.map { ImmutableMethodParameter(it, emptySet(), null) },
        modifier, AccessFlags.PRIVATE.value or AccessFlags.STATIC.value,
        emptySet(), emptySet(), ImmutableMethodImplementation(8, emptyList(), null, null),
    ).toMutable().apply {
        addInstructions(0, """
            invoke-static { p0 }, $getter
            move-result-wide v0
            invoke-static { v0, v1 }, $ACCENT_THEME->activityFeedBackground(J)J
            move-result-wide v0
            const-wide/16 v2, 0x0
            cmp-long v4, v0, v2
            if-eqz v4, :native
            invoke-static { p2, v0, v1 }, $background
            move-result-object p2
            :native
            return-object p2
        """)
    }
    val owner = mutableClassDefBy(feed.definingClass)
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
internal fun BytecodePatchContext.tintInboxRowBackgrounds() {
    val threads = inboxRenderers()
    val prompt = inboxRenderer(inboxPromptRow)
    // The prompt renderer also uses a different modifier of the same signature.
    val background = threads.mapNotNull { thread ->
        thread.implementation!!.instructions.mapNotNull { it.backgroundModifier() }.singleOrNull()
    }.distinct().singleOrNull() ?: return
    val activity = activityRowRenderer(background)
    if (activity != null) tintActivityList(activity, background)
    for (method in threads + listOfNotNull(prompt, activity)) {
        val code = method.implementation!!.instructions.toList()
        val fills = code.indices.filter { code[it].backgroundModifier() == background }
        // Thread rows have one fill. Prompts and Activity content can branch by layout.
        if (fills.isEmpty() || (method in threads && fills.size != 1)) continue
        val renderer = mutableClassDefBy(method.definingClass).methods.single { it.toString() == method.toString() }
        for (index in fills.asReversed()) {
            val register = when (val call = code[index]) {
                is FiveRegisterInstruction -> call.registerD.takeIf {
                    call.registerCount == 3 && call.registerE == it + 1 && call.registerC !in it..it + 1
                }
                is RegisterRangeInstruction -> (call.startRegister + 1).takeIf { call.registerCount == 3 }
                else -> null
            } ?: continue
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
