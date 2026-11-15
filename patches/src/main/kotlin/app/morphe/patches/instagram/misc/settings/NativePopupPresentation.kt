/* Copyright 2026 InstaPrime contributors */
package app.morphe.patches.instagram.misc.settings

import app.morphe.patcher.patch.mutableClassDefBy as globalMutableClassDefBy

import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patches.instagram.misc.extension.EXTENSION_PACKAGE
import app.morphe.patches.instagram.misc.extension.classesCallingInto
import app.morphe.patches.instagram.misc.extension.classesHolding
import app.morphe.patches.instagram.misc.extension.classesLoading
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

private const val POPUP_FLAG = 36329998780490487L
private const val PROFILE_SHEET_FLAG = 36322151875231018L
private const val WINDOW = "Landroid/widget/PopupWindow;"
private const val SESSION = "Lcom/instagram/common/session/UserSession;"
internal data class NativePopupChoice(val method: Method, val results: Map<Int, Boolean>)
internal data class NativePopupPlan(val choices: List<NativePopupChoice>, val populations: List<Method>,
    val primary: Method, val primaryAt: Int, val more: Method)

@Suppress("DEPRECATION") // Legacy shared-helper boundary; lookups use the global API.
internal fun BytecodePatchContext.nativePopupPlan(): NativePopupPlan {
    fun refuse(why: String): Nothing = throw PatchException("Native popup presentation: $why")
    val gate = classesLoading(POPUP_FLAG).flatMap { it.methods }.singleOrNull { method ->
        method.returnType == "Z" && method.parameterTypes.map(Any::toString) == listOf(SESSION) &&
            method.popupCode().any { (it as? WideLiteralInstruction)?.wideLiteral == POPUP_FLAG }
    } ?: refuse("no unique shared native popup gate")
    val profiles = classesLoading(PROFILE_SHEET_FLAG).flatMap { it.methods }.filter {
        it.returnType == "V" && it.parameterTypes.map(Any::toString) == listOf("Landroid/view/View;") &&
            it.popupCode().any { ins -> ins.popupCall()?.toString() == gate.toString() }
    }
    val profile = profiles.singleOrNull() ?: refuse("no unique profile presenter")
    val more = classesHolding("MessageActionsHelper").flatMap { it.methods }.singleOrNull {
        it.returnType == "V" && it.popupCode().any { ins -> ins.popupCall()?.toString() == gate.toString() }
    } ?: refuse("no unique message More presenter")
    val inbox = classesHolding("DirectInboxThreadDialogController").flatMap { it.methods }.singleOrNull {
        it.returnType == "V" && "Lcom/instagram/model/direct/DirectThreadKey;" in it.parameterTypes.map(Any::toString) &&
            it.popupCode().any { ins -> ins.popupCall()?.toString() == gate.toString() }
    } ?: refuse("no unique inbox chat-hold presenter")
    val choices = listOf(profile, more, inbox).map { method ->
        val code = method.popupCode()
        val results = code.indices.filter { code[it].popupCall()?.toString() == gate.toString() }.associate { at ->
            if (code.getOrNull(at + 1)?.opcode != Opcode.MOVE_RESULT) refuse("native popup gate result changed")
            at + 1 to true
        }.toMutableMap()
        if (method == profile) {
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
    return NativePopupPlan(choices, populations, primary, primaryAt, more)
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
}

private fun Method.popupCode(): List<Instruction> = implementation?.instructions?.toList().orEmpty()
private fun Instruction.popupCall() = (this as? ReferenceInstruction)?.reference as? MethodReference
