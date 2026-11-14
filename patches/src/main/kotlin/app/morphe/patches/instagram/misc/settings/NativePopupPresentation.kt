/* Copyright 2026 InstaPrime contributors */
package app.morphe.patches.instagram.misc.settings

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
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val POPUP_FLAG = 36329998780490487L
private const val PROFILE_SHEET_FLAG = 36322151875231018L
private const val WINDOW = "Landroid/widget/PopupWindow;"
private const val SESSION = "Lcom/instagram/common/session/UserSession;"
internal data class NativePopupChoice(val method: Method, val results: Map<Int, Boolean>)
internal data class NativePopupPlan(val choices: List<NativePopupChoice>, val populations: List<Method>)

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
    val choices = listOf(profile, more).map { method ->
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
    return NativePopupPlan(choices, populations)
}

internal fun BytecodePatchContext.applyNativePopupPlan(plan: NativePopupPlan) {
    for (choice in plan.choices) {
        val method = mutableClassDefBy(choice.method.definingClass).methods.single {
            it.name == choice.method.name && it.parameterTypes == choice.method.parameterTypes && it.returnType == choice.method.returnType
        }
        for ((at, popup) in choice.results) {
            val register = (method.popupCode()[at] as OneRegisterInstruction).registerA
            method.replaceInstruction(at, "const/16 v$register, ${if (popup) "0x1" else "0x0"}")
        }
    }
    for (original in plan.populations) {
        val method = mutableClassDefBy(original.definingClass).methods.single {
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
