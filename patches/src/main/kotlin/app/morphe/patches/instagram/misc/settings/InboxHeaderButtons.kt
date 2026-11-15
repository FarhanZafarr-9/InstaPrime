package app.morphe.patches.instagram.misc.settings

import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patches.instagram.direct.ghost.findGhostAction
import app.morphe.patches.instagram.direct.ghost.findGhostEntries
import app.morphe.patches.instagram.misc.extension.EXTENSION_PACKAGE
import app.morphe.patches.instagram.misc.extension.classesAccessing
import app.morphe.patches.instagram.misc.extension.freeLocalsAt
import app.morphe.patches.instagram.misc.extension.parameterRegisterNumber
import app.morphe.patches.instagram.misc.extension.requireParameterIntact
import app.morphe.util.addInstructionsAtControlFlowLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val INBOX_BUTTONS = "$EXTENSION_PACKAGE/settings/InboxHeaderButtons;"

/** Reuse the exact New message identification used by Ghost mode, without changing its listeners. */
internal fun BytecodePatchContext.installInboxHeaderButtons() {
    val newer = findGhostAction()
    val older = findGhostEntries()
    val maker = mutableClassDefBy(newer.makerType).methods.single {
        it.name == newer.makerName && it.parameterTypes.map(Any::toString) == newer.makerParameters
    }
    maker.implementation!!.instructions.toList().withIndex().filter { it.value.opcode == Opcode.RETURN_OBJECT }
        .asReversed().forEach { (at, ins) ->
            val register = (ins as OneRegisterInstruction).registerA
            maker.addInstructionsAtControlFlowLabel(at,
                "invoke-static/range {v$register .. v$register}, $INBOX_BUTTONS->mark(Ljava/lang/Object;)V")
        }
    val binder = mutableClassDefBy("Lcom/instagram/igds/components/actionbar/IgdsActionBar;").methods.single {
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
        val method = mutableClassDefBy(entry.type).methods.single {
            it.name == entry.name && it.parameterTypes.map(Any::toString) == entry.parameters
        }
        method.addInstructionsAtControlFlowLabel(entry.insertAt,
            "invoke-static/range {v${entry.config} .. v${entry.config}}, $INBOX_BUTTONS->mark(Ljava/lang/Object;)V")
    }
    for (field in older.map { it.longPress }.distinct()) {
        val type = field.substringBefore("->")
        val name = field.substringAfter("->").substringBefore(':')
        val methods = classesAccessing(type, name, Opcode.IGET_OBJECT).flatMap { it.methods }.filter {
            type in it.parameterTypes.map(Any::toString) && it.implementation != null
        }
        var bound = false
        for (original in methods) {
            val method = mutableClassDefBy(original.definingClass).methods.single {
                it.name == original.name && it.parameterTypes == original.parameterTypes
            }
            val code = method.implementation!!.instructions.toList()
            val setters = code.indices.filter { at ->
                val call = (code[at] as? ReferenceInstruction)?.reference as? MethodReference
                call?.name == "setOnClickListener" && call.parameterTypes.map(Any::toString) == listOf("Landroid/view/View\$OnClickListener;")
            }
            for (at in setters.asReversed()) {
                val register = when (val ins = code[at]) {
                    is FiveRegisterInstruction -> ins.registerC
                    is RegisterRangeInstruction -> ins.startRegister
                    else -> continue
                }
                val model = method.parameterRegisterNumber(method.parameterTypes.indexOfFirst { it.toString() == type })
                method.requireParameterIntact("Inbox header buttons", method.parameterTypes.indexOfFirst { it.toString() == type }, listOf(at + 1))
                if (register <= 15 && model <= 15) {
                    method.addInstructionsAtControlFlowLabel(at + 1,
                        "invoke-static {v$register, v$model}, $INBOX_BUTTONS->bind(Landroid/view/View;Ljava/lang/Object;)V")
                } else {
                    val locals = method.freeLocalsAt("Inbox header buttons", at + 1, 2, except = listOf(register, model))
                    method.addInstructionsAtControlFlowLabel(at + 1, """
                        move-object/from16 v${locals[0]}, v$register
                        move-object/from16 v${locals[1]}, v$model
                        invoke-static {v${locals[0]}, v${locals[1]}}, $INBOX_BUTTONS->bind(Landroid/view/View;Ljava/lang/Object;)V
                    """)
                }
                bound = true
            }
        }
        if (!bound) throw PatchException("Inbox header buttons: legacy New message binder missing")
    }
}
