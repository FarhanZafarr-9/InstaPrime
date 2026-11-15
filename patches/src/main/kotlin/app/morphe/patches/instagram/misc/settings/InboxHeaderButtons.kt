package app.morphe.patches.instagram.misc.settings

import app.morphe.patcher.patch.mutableClassDefBy as globalMutableClassDefBy

import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patches.instagram.direct.ghost.findGhostAction
import app.morphe.patches.instagram.direct.ghost.findGhostEntries
import app.morphe.patches.instagram.misc.extension.EXTENSION_PACKAGE
import app.morphe.patches.instagram.misc.extension.classesAccessing
import app.morphe.patches.instagram.misc.extension.parameterRegisterNumber
import app.morphe.patches.instagram.misc.extension.requireParameterIntact
import app.morphe.util.addInstructionsAtControlFlowLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val INBOX_BUTTONS = "$EXTENSION_PACKAGE/settings/InboxHeaderButtons;"

/** Reuse the exact New message identification used by Ghost mode, without changing its listeners. */
@Suppress("DEPRECATION") // Legacy shared-helper boundary; lookups use the global API.
internal fun BytecodePatchContext.installInboxHeaderButtons() {
    val newer = findGhostAction()
    val older = findGhostEntries()
    val maker = globalMutableClassDefBy(newer.makerType).methods.single {
        it.name == newer.makerName && it.parameterTypes.map(Any::toString) == newer.makerParameters
    }
    maker.implementation!!.instructions.toList().withIndex().filter { it.value.opcode == Opcode.RETURN_OBJECT }
        .asReversed().forEach { (at, ins) ->
            val register = (ins as OneRegisterInstruction).registerA
            maker.addInstructionsAtControlFlowLabel(at,
                "invoke-static/range {v$register .. v$register}, $INBOX_BUTTONS->mark(Ljava/lang/Object;)V")
        }
    val binder = globalMutableClassDefBy("Lcom/instagram/igds/components/actionbar/IgdsActionBar;").methods.single {
        it.name == newer.binderName && it.parameterTypes.map(Any::toString) == newer.binderParameters
    }
    binder.implementation!!.instructions.toList().withIndex().filter {
        ((it.value as? ReferenceInstruction)?.reference as? MethodReference)?.name == "setOnClickListener"
    }.asReversed().forEach { (at, _) ->
            binder.requireParameterIntact("Inbox header buttons", 0, listOf(at + 1))
            binder.requireParameterIntact("Inbox header buttons", 1, listOf(at + 1))
            binder.addInstructionsAtControlFlowLabel(at + 1,
                "invoke-static/range {p1 .. p2}, $INBOX_BUTTONS->bind(Landroid/view/View;Ljava/lang/Object;)V")
        }

    for (entry in older.sortedByDescending { it.insertAt }) {
        val method = globalMutableClassDefBy(entry.type).methods.single {
            it.name == entry.name && it.parameterTypes.map(Any::toString) == entry.parameters
        }
        method.addInstructionsAtControlFlowLabel(entry.insertAt,
            "invoke-static/range {v${entry.config} .. v${entry.config}}, $INBOX_BUTTONS->mark(Ljava/lang/Object;)V")
    }
    for (field in older.map { it.longPress }.distinct()) {
        val type = field.substringBefore("->")
        val name = field.substringAfter("->").substringBefore(':')
        val readers = classesAccessing(type, name, Opcode.IGET_OBJECT).flatMap { it.methods }
        val renderedFields = mutableSetOf(field)
        for (copy in readers.filter { it.name == "<init>" && it.parameterTypes.map(Any::toString) == listOf(type) }) {
            val code = copy.implementation?.instructions?.toList().orEmpty()
            val copiedFields = code.indices.mapNotNull { at ->
                if ((code[at] as? ReferenceInstruction)?.reference?.toString() != field || code[at].opcode != Opcode.IGET_OBJECT) return@mapNotNull null
                val write = code.getOrNull(at + 1)
                if (write?.opcode != Opcode.IPUT_OBJECT || (write as TwoRegisterInstruction).registerA != (code[at] as TwoRegisterInstruction).registerA)
                    return@mapNotNull null
                ((write as ReferenceInstruction).reference as? FieldReference)?.takeIf { it.definingClass == copy.definingClass }
            }
            if (copiedFields.isEmpty()) continue
            renderedFields += copiedFields.map { it.toString() }
            val constructor = globalMutableClassDefBy(copy.definingClass).methods.single {
                it.name == copy.name && it.parameterTypes == copy.parameterTypes
            }
            constructor.implementation!!.instructions.toList().withIndex().filter { it.value.opcode == Opcode.RETURN_VOID }
                .asReversed().forEach { (at, _) ->
                    constructor.requireParameterIntact("Inbox header buttons", 0, listOf(at))
                    constructor.addInstructionsAtControlFlowLabel(at,
                        "invoke-static/range {p0 .. p1}, $INBOX_BUTTONS->copied(Ljava/lang/Object;Ljava/lang/Object;)V")
                }
        }
        var bound = false
        for (rendered in renderedFields) {
            val owner = rendered.substringBefore("->")
            val member = rendered.substringAfter("->").substringBefore(':')
            val methods = classesAccessing(owner, member, Opcode.IGET_OBJECT).flatMap { it.methods }.filter {
                !AccessFlags.STATIC.isSet(it.accessFlags) && it.returnType == "V" &&
                    it.parameterTypes.map(Any::toString) == listOf("Landroid/view/View;", owner)
            }.distinctBy { it.toString() }
            for (original in methods) {
                val method = globalMutableClassDefBy(original.definingClass).methods.single {
                    it.name == original.name && it.parameterTypes == original.parameterTypes
                }
                val first = method.parameterRegisterNumber(0)
                method.addInstructionsAtControlFlowLabel(0,
                    "invoke-static/range {v$first .. v${first + 1}}, $INBOX_BUTTONS->bind(Landroid/view/View;Ljava/lang/Object;)V")
                bound = true
            }
        }
        if (!bound) throw PatchException("Inbox header buttons: legacy New message binder missing")
    }
}
