package app.morphe.patches.instagram.direct.gestures

import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patches.instagram.misc.extension.classesCalling
import app.morphe.patches.instagram.misc.extension.classesCallingInto
import app.morphe.util.addInstructionsAtControlFlowLabel
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation

internal data class MessageTiming(val method: Method, val at: Int, val flag: FieldReference, val delegate: FieldReference)

internal fun BytecodePatchContext.messageTiming(site: MessageGestureSite): MessageTiming {
    fun refuse(): Nothing = throw PatchException("Message tap menus: no unique message double-tap recognition flag")
    val interfaces = classDefBy(site.tap.definingClass).interfaces
    val delegates = interfaces.flatMap { type -> classesCalling(type, site.tap.name).flatMap { it.methods } }
        .flatMap { it.implementation?.instructions?.toList().orEmpty() }
        .mapNotNull { (it as? ReferenceInstruction)?.reference as? FieldReference }
        .filter { field -> field.type in interfaces && classDefByOrNull(field.definingClass)?.methods?.any {
            it.name == "<init>" && it.parameterTypes.map(Any::toString) ==
                listOf(field.type, "Ljava/lang/Object;", "Z", "Z")
        } == true }.distinctBy { it.toString() }
    val delegate = delegates.singleOrNull() ?: refuse()
    val candidates = classesCallingInto("Landroidx/compose/foundation/gestures/TapGestureDetectorKt;")
        .flatMap { owner -> owner.methods }.filter { method ->
            method.implementation?.instructions?.any { ins ->
                val call = (ins as? ReferenceInstruction)?.reference as? MethodReference
                call?.definingClass == "Landroidx/compose/foundation/gestures/TapGestureDetectorKt;" &&
                    call.parameterTypes.map(Any::toString).takeLast(4) == listOf(
                    "Lkotlin/jvm/functions/Function1;", "Lkotlin/jvm/functions/Function1;",
                    "Lkotlin/jvm/functions/Function1;", "Lkotlin/jvm/functions/Function3;")
            } == true
        }.flatMap { method -> method.implementation!!.instructions.toList().mapIndexedNotNull { at, ins ->
            val field = (ins as? ReferenceInstruction)?.reference as? FieldReference
            if (ins.opcode == Opcode.IGET_BOOLEAN && field?.definingClass == delegate.definingClass)
                MessageTiming(method, at, field, delegate) else null
        } }
    return candidates.singleOrNull() ?: refuse()
}

internal fun BytecodePatchContext.applyMessageTiming(site: MessageGestureSite, timing: MessageTiming) {
    val name = "instaprimeDoubleTapEnabled"
    val node = mutableClassDefBy(timing.flag.definingClass)
    if (node.methods.any { it.name == name }) throw PatchException("Message tap menus: duplicate timing bridge")
    node.methods.add(ImmutableMethod(node.type, name, emptyList(), "Z", AccessFlags.PUBLIC.value,
        emptySet(), emptySet(), ImmutableMethodImplementation(3, emptyList(), null, null)).toMutable().apply {
        addInstructions(0, """
            iget-boolean v1, p0, ${timing.flag}
            iget-object v0, p0, ${timing.delegate}
            instance-of p0, v0, ${site.tap.definingClass}
            if-eqz p0, :native_flag
            invoke-static {v1, v0}, $GESTURES->doubleTapEnabled(ZLjava/lang/Object;)Z
            move-result v1
            :native_flag
            return v1
        """)
    })
    val method = mutableClassDefBy(timing.method.definingClass).methods.single {
        it.name == timing.method.name && it.parameterTypes == timing.method.parameterTypes
    }
    val read = method.implementation!!.instructions.toList()[timing.at] as TwoRegisterInstruction
    method.replaceInstruction(timing.at, "move-result v${read.registerA}")
    method.addInstructionsAtControlFlowLabel(timing.at,
        "invoke-virtual/range {v${read.registerB} .. v${read.registerB}}, ${node.type}->$name()Z")
}
