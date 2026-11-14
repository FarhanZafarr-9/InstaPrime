package app.morphe.patches.instagram.direct.plusmenu

import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patches.instagram.misc.extension.classesHolding
import app.morphe.patches.instagram.misc.extension.classesAccessing
import app.morphe.patches.instagram.misc.extension.localRegisterCount
import app.morphe.util.RegisterLiveness
import app.morphe.util.addInstructionsAtControlFlowLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

internal data class EmptyPlusSite(val owner: String, val method: String, val parameters: List<String>,
    val at: Int, val receiver: Int, val scratch: Int, val field: String)

/** Discover MORE's native item and the field receiving its constructor's overflow list. */
internal fun BytecodePatchContext.findEmptyPlusMenu(): EmptyPlusSite {
    fun refuse(why: String): Nothing = throw PatchException("Chat plus menu: $why")
    val enum = classesHolding(*PLUS_BUTTON_NAMES.toTypedArray()).singleOrNull {
        it.superclass == "Ljava/lang/Enum;" && "MORE" in constantFields(it)
    } ?: refuse("no unique composer MORE enum")
    val more = constantFields(enum).getValue("MORE")
    val items = classesAccessing(enum.type, more, Opcode.SGET_OBJECT).filter { candidate ->
        candidate.methods.any { method -> method.name == "<init>" &&
            method.parameterTypes.count { it.toString() == "Ljava/util/List;" } == 1 }
    }
    val item = items.singleOrNull() ?: refuse("${items.size} MORE item classes")
    val ctor = item.methods.single { it.name == "<init>" && it.parameterTypes.any { p -> p.toString() == "Ljava/util/List;" } }
    val impl = ctor.implementation ?: refuse("no MORE constructor code")
    val parameters = ctor.parameterTypes.map(Any::toString)
    val first = impl.registerCount - 1 - parameters.sumOf { if (it == "J" || it == "D") 2 else 1 }
    val listParameter = first + 1 + parameters.take(parameters.indexOf("Ljava/util/List;")).sumOf { if (it == "J" || it == "D") 2 else 1 }
    val aliases = mutableSetOf(listParameter)
    val fields = mutableListOf<FieldReference>()
    for (ins in impl.instructions) {
        val ref = (ins as? ReferenceInstruction)?.reference
        if (ins.opcode == Opcode.IPUT_OBJECT && ref is FieldReference && ref.type == "Ljava/util/List;" &&
            (ins as TwoRegisterInstruction).registerA in aliases) fields += ref
        if (ins is TwoRegisterInstruction && ins.opcode in setOf(Opcode.MOVE_OBJECT, Opcode.MOVE_OBJECT_FROM16, Opcode.MOVE_OBJECT_16)) {
            if (ins.registerB in aliases) aliases += ins.registerA else aliases -= ins.registerA
        } else if (ins.opcode.setsRegister() && ins is OneRegisterInstruction) aliases -= ins.registerA
    }
    val field = fields.singleOrNull() ?: refuse("no unique MORE overflow field")
    val base = classDefBy(item.superclass ?: refuse("MORE item has no base"))
    val methods = base.methods.filter { it.returnType == "Z" && it.parameterTypes.size == 5 &&
        it.parameterTypes.takeLast(3).all { p -> p.toString() == "Z" } }
    val sites = methods.flatMap { method ->
        val code = method.implementation?.instructions?.toList().orEmpty()
        code.indices.filter { i -> code[i].opcode == Opcode.CHECK_CAST &&
            ((code[i] as? ReferenceInstruction)?.reference as? TypeReference)?.type == item.type }.map { at ->
            val receiver = (code[at] as OneRegisterInstruction).registerA
            val scratch = (0 until minOf(16, method.localRegisterCount())).firstOrNull { it != receiver && it !in RegisterLiveness.of(method).liveInto(at + 1) }
                ?: refuse("no spare register in MORE availability")
            if (receiver > 15) refuse("MORE receiver exceeds iget register range")
            EmptyPlusSite(base.type, method.name, method.parameterTypes.map(Any::toString), at + 1,
                receiver, scratch, "${field.definingClass}->${field.name}:${field.type}")
        }
    }
    return sites.singleOrNull() ?: refuse("${sites.size} MORE availability branches")
}

internal fun BytecodePatchContext.hideEmptyPlusMenu(site: EmptyPlusSite) {
    val method = mutableClassDefBy(site.owner).methods.single { it.name == site.method &&
        it.parameterTypes.map(Any::toString) == site.parameters }
    method.addInstructionsAtControlFlowLabel(site.at, """
        iget-object v${site.scratch}, v${site.receiver}, ${site.field}
        invoke-static {v${site.scratch}}, $PLUS_MENU->empty(Ljava/util/List;)Z
        move-result v${site.scratch}
        if-eqz v${site.scratch}, :native_more
        const/4 v${site.scratch}, 0x0
        return v${site.scratch}
        :native_more
        nop
    """)
}
