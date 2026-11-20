/* Copyright 2026 InstaPrime contributors */
package app.morphe.patches.instagram.misc.settings

import app.morphe.patcher.patch.mutableClassDefBy as globalMutableClassDefBy
import app.morphe.patcher.patch.classDefByOrNull as globalClassDefByOrNull

import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patches.instagram.misc.extension.EXTENSION_PACKAGE
import app.morphe.patches.instagram.misc.extension.classesCallingInto
import app.morphe.patches.instagram.misc.extension.classesHolding
import app.morphe.patches.instagram.misc.extension.freeLocalsAt
import app.morphe.patches.instagram.misc.extension.parameterRegisterNumber
import app.morphe.patches.instagram.misc.extension.requireParameterIntact
import app.morphe.util.addInstructionsAtControlFlowLabel
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation

private const val PROFILE_SHEET_FLAG = 36322151875231018L
private const val WINDOW = "Landroid/widget/PopupWindow;"
private const val SESSION = "Lcom/instagram/common/session/UserSession;"
internal data class NativePopupChoice(val method: Method, val results: Map<Int, Boolean>)
internal data class NativePopupPlan(val choices: List<NativePopupChoice>, val populations: List<Method>,
    val primary: Method, val primaryAt: Int, val more: Method, val inbox: Method, val primaryX: Method,
    val gate: MethodReference)

@Suppress("DEPRECATION") // Legacy shared-helper boundary; lookups use the global API.
internal fun BytecodePatchContext.nativePopupPlan(): NativePopupPlan {
    fun refuse(why: String): Nothing = throw PatchException("Native popup presentation: $why")
    fun sessionGates(method: Method) = method.popupCode().mapNotNull { it.popupCall() }.filter {
        it.returnType == "Z" && it.parameterTypes.map(Any::toString) == listOf(SESSION)
    }.associateBy { it.toString() }
    val moreCandidates = classesHolding("MessageActionsHelper").flatMap { it.methods }.filter { it.returnType == "V" }
    val inboxCandidates = classesHolding("DirectInboxThreadDialogController").flatMap { it.methods }.filter {
        it.returnType == "V" && "Lcom/instagram/model/direct/DirectThreadKey;" in it.parameterTypes.map(Any::toString)
    }
    val shared = moreCandidates.flatMap { more ->
        val calls = sessionGates(more)
        inboxCandidates.flatMap { inbox -> sessionGates(inbox).keys.intersect(calls.keys).map { gate ->
            Triple(more, inbox, calls.getValue(gate))
        } }
    }.distinctBy { "${it.first}|${it.second}|${it.third}" }
    val (more, inbox, gate) = shared.singleOrNull() ?: refuse("no unique shared native popup gate")
    val profile = classesCallingInto(gate.definingClass).filter { owner -> owner.fields.any {
        it.type == "Lcom/instagram/profile/fragment/UserDetailFragment;"
    } }.flatMap { it.methods }.singleOrNull {
        it.returnType == "V" && it.parameterTypes.map(Any::toString) == listOf("Landroid/view/View;") &&
            it.popupCode().any { ins -> ins.popupCall()?.toString() == gate.toString() }
    } ?: refuse("no unique profile presenter")
    val choices = listOf(profile, more, inbox).map { method ->
        val code = method.popupCode()
        val results = code.indices.filter { code[it].popupCall()?.toString() == gate.toString() }.associate { at ->
            if (code.getOrNull(at + 1)?.opcode != Opcode.MOVE_RESULT) refuse("native popup gate result changed")
            at + 1 to true
        }.toMutableMap()
        if (method == profile && code.any { (it as? WideLiteralInstruction)?.wideLiteral == PROFILE_SHEET_FLAG }) {
            val flag = code.indices.single { (code[it] as? WideLiteralInstruction)?.wideLiteral == PROFILE_SHEET_FLAG }
            val call = (flag + 1 until minOf(code.size, flag + 8)).firstOrNull {
                code[it].popupCall()?.returnType == "Z" && code.getOrNull(it + 1)?.opcode == Opcode.MOVE_RESULT
            } ?: refuse("profile sheet gate result changed")
            results[call + 1] = false
        }
        NativePopupChoice(method, results)
    }
    val renderer = classesCallingInto(WINDOW).singleOrNull { owner -> owner.superclass == WINDOW &&
        owner.methods.any { it.name == "<init>" && it.parameterTypes.map(Any::toString) ==
            listOf("Landroid/content/Context;", SESSION, "Ljava/lang/Integer;", "Z") } }
        ?: refuse("no unique native popup renderer")
    val populations = renderer.methods.filter { !AccessFlags.STATIC.isSet(it.accessFlags) && it.returnType == "V" &&
        it.parameterTypes.map(Any::toString) == listOf("Ljava/util/List;") && it.implementation != null }
    if (populations.size < 2) refuse("native popup population variants missing")
    val primary = classesHolding("reactionsCreationController must be initialized", "vibrator")
        .flatMap { it.methods }.singleOrNull { it.name == "onViewCreated" && it.popupCode().any { ins ->
            ins.popupCall()?.let { call -> call.definingClass == renderer.type &&
                call.parameterTypes.map(Any::toString) == listOf("Ljava/util/List;") } == true
        } } ?: refuse("no unique primary message popup")
    val primaryAt = primary.popupCode().indices.singleOrNull { at -> primary.popupCode()[at].popupCall()?.let {
        it.definingClass == renderer.type && it.parameterTypes.map(Any::toString) == listOf("Ljava/util/List;")
    } == true } ?: refuse("primary message popup population changed")
    val primaryX = primary.popupCode().filter { it.opcode == Opcode.NEW_INSTANCE }.mapNotNull {
        val type = ((it as? ReferenceInstruction)?.reference as? TypeReference)?.type ?: return@mapNotNull null
        val owner = globalClassDefByOrNull(type) ?: return@mapNotNull null
        if (owner.fields.none { field -> field.type == renderer.type }) return@mapNotNull null
        owner.superclass?.let { parent -> globalClassDefByOrNull(parent) }
    }.flatMap { it.methods }.filter {
        it.returnType == "I" && it.parameterTypes.map(Any::toString) ==
            listOf("Landroid/view/View;", "Landroidx/slidingpanelayout/widget/SlidingPaneLayout;")
    }.distinctBy { it.toString() }.singleOrNull() ?: refuse("no unique primary horizontal position")
    return NativePopupPlan(choices, populations, primary, primaryAt, more, inbox, primaryX, gate)
}

@Suppress("DEPRECATION") // Legacy shared-helper boundary; lookups use the global API.
internal fun BytecodePatchContext.applyNativePopupPlan(plan: NativePopupPlan) {
    val position = "$EXTENSION_PACKAGE/direct/MessageMenuPosition;"
    val primary = globalMutableClassDefBy(plan.primary.definingClass).methods.single { it.name == plan.primary.name && it.parameterTypes == plan.primary.parameterTypes }
    val population = primary.popupCode()[plan.primaryAt]
    val popupRegister = when (population) {
        is FiveRegisterInstruction -> population.registerC
        is RegisterRangeInstruction -> population.startRegister
        else -> throw PatchException("Native popup presentation: unsupported primary call")
    }
    primary.addInstructionsAtControlFlowLabel(plan.primaryAt + 1,
        "invoke-static/range {v$popupRegister .. v$popupRegister}, $position->remember(Landroid/widget/PopupWindow;)V")
    val primaryX = globalMutableClassDefBy(plan.primaryX.definingClass).methods.single { it.toString() == plan.primaryX.toString() }
    primaryX.popupCode().withIndex().filter { it.value.opcode == Opcode.RETURN }.asReversed().forEach { (at, instruction) ->
        val result = (instruction as OneRegisterInstruction).registerA
        primaryX.addInstructionsAtControlFlowLabel(at, """
            invoke-static/range {v$result .. v$result}, $position->primaryX(I)I
            move-result v$result
        """)
    }
    for (choice in plan.choices) {
        val method = globalMutableClassDefBy(choice.method.definingClass).methods.single {
            it.name == choice.method.name && it.parameterTypes == choice.method.parameterTypes && it.returnType == choice.method.returnType
        }
        // A wider constant can change switch-payload alignment padding. Work backwards
        // so any removed padding cannot shift a result that still needs replacing.
        for ((at, popup) in choice.results.entries.sortedByDescending { it.key }) {
            val register = (method.popupCode()[at] as OneRegisterInstruction).registerA
            method.replaceInstruction(at, "const/16 v$register, ${if (popup) "0x1" else "0x0"}")
        }
    }
    val more = globalMutableClassDefBy(plan.more.definingClass).methods.single { it.name == plan.more.name && it.parameterTypes == plan.more.parameterTypes }
    for ((at, ins) in more.popupCode().withIndex()) {
        if (ins.popupCall()?.name != "showAtLocation") continue
        val target = "$position->showMore(Landroid/widget/PopupWindow;Landroid/view/View;III)V"
        val call = when (ins) {
            is FiveRegisterInstruction -> "invoke-static {v${ins.registerC}, v${ins.registerD}, v${ins.registerE}, v${ins.registerF}, v${ins.registerG}}, $target"
            is RegisterRangeInstruction -> "invoke-static/range {v${ins.startRegister} .. v${ins.startRegister + ins.registerCount - 1}}, $target"
            else -> throw PatchException("Native popup presentation: unsupported More placement call")
        }
        more.replaceInstruction(at, call)
    }
    for (original in plan.populations) {
        val method = globalMutableClassDefBy(original.definingClass).methods.single {
            it.name == original.name && it.parameterTypes == original.parameterTypes
        }
        method.popupCode().indices.filter { method.popupCode()[it].opcode == Opcode.RETURN_VOID }.asReversed().forEach { at ->
            method.addInstructionsAtControlFlowLabel(at,
                "invoke-static/range {p0 .. p0}, $EXTENSION_PACKAGE/settings/StyledPopupMenu;->styleNative(Landroid/widget/PopupWindow;)V")
        }
    }
    val inbox = globalMutableClassDefBy(plan.inbox.definingClass).methods.single { it.toString() == plan.inbox.toString() }
    if (inbox.parameterTypes.firstOrNull()?.toString() != "Landroid/graphics/RectF;")
        throw PatchException("Inbox popup: row bounds parameter changed")
    val candidates = inbox.popupCode().withIndex().mapNotNull { (at, instruction) ->
        val call = instruction.popupCall() ?: return@mapNotNull null
        if (call.parameterTypes.take(2).map(Any::toString) != listOf("Landroid/view/View;", "Landroid/view/View;") ||
            !call.returnType.startsWith("L")) return@mapNotNull null
        val factory = globalMutableClassDefBy(call.definingClass).methods.singleOrNull { it.toString() == call.toString() }
            ?: return@mapNotNull null
        if (!AccessFlags.STATIC.isSet(factory.accessFlags) ||
            factory.popupCode().none { it.popupCall()?.name == "getLocationOnScreen" }) return@mapNotNull null
        Triple(at, call, factory)
    }
    val (at, factoryCall, factory) = candidates.singleOrNull()
        ?: throw PatchException("Inbox popup: no unique native anchor factory")
    val helper = "$EXTENSION_PACKAGE/direct/InboxPopup;"
    val bridge = "instaprimeInboxPopup"
    val parameters = factory.parameterTypes.joinToString("")
    val words = factory.parameterTypes.map { if (it.toString() in listOf("J", "D")) 2 else 1 }.sum()
    globalMutableClassDefBy(factory.definingClass).methods.add(ImmutableMethod(factory.definingClass, bridge,
        factory.parameters, factory.returnType, AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
        emptySet(), emptySet(), ImmutableMethodImplementation(words + 1, emptyList(), null, null)).toMutable().apply {
        addInstructions(0, """
            invoke-static {p0, p1}, $helper->show(Landroid/view/View;Landroid/view/View;)Z
            move-result v0
            if-eqz v0, :native_overlay
            const/4 v0, 0x0
            return-object v0
            :native_overlay
            invoke-static/range {p0 .. p${words - 1}}, $factoryCall
            move-result-object v0
            return-object v0
        """)
    })
    val target = "${factory.definingClass}->$bridge($parameters)${factory.returnType}"
    val call = when (val instruction = inbox.popupCode()[at]) {
        is RegisterRangeInstruction -> "invoke-static/range {v${instruction.startRegister} .. v${instruction.startRegister + instruction.registerCount - 1}}, $target"
        is FiveRegisterInstruction -> "invoke-static {${listOf(instruction.registerC, instruction.registerD, instruction.registerE, instruction.registerF, instruction.registerG).take(instruction.registerCount).joinToString { "v$it" }}}, $target"
        else -> throw PatchException("Inbox popup: unsupported factory call")
    }
    inbox.replaceInstruction(at, call)
    val renderer = plan.populations.first().definingClass
    val preparations = inbox.popupCode().withIndex().filter { (_, instruction) ->
        instruction.popupCall()?.let { it.definingClass == renderer && it.returnType == "V" &&
            it.parameterTypes.map(Any::toString) == listOf("Ljava/util/List;") } == true
    }
    if (preparations.isEmpty()) throw PatchException("Inbox popup: no native menu population")
    for ((index, instruction) in preparations.asReversed()) {
        val popup = when (instruction) {
            is FiveRegisterInstruction -> instruction.registerC
            is RegisterRangeInstruction -> instruction.startRegister
            else -> throw PatchException("Inbox popup: unsupported population call")
        }
        inbox.requireParameterIntact("Inbox popup", 0, listOf(index + 1))
        val locals = inbox.freeLocalsAt("Inbox popup", index + 1, 2, except = listOf(popup))
        inbox.addInstructionsAtControlFlowLabel(index + 1, """
            move-object/from16 v${locals[0]}, v$popup
            move-object/from16 v${locals[1]}, v${inbox.parameterRegisterNumber(0)}
            invoke-static {v${locals[0]}, v${locals[1]}}, $helper->prepare(Landroid/widget/PopupWindow;Landroid/graphics/RectF;)V
        """)
    }
}

private fun Method.popupCode(): List<Instruction> = implementation?.instructions?.toList().orEmpty()
private fun Instruction.popupCall() = (this as? ReferenceInstruction)?.reference as? MethodReference
