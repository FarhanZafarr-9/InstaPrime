package app.morphe.patches.instagram.misc.settings

import app.morphe.patcher.patch.mutableClassDefBy as globalMutableClassDefBy

import app.morphe.PatchContexts
import app.morphe.patches.instagram.FixtureDex
import app.morphe.patches.instagram.misc.extension.EXTENSION_PACKAGE
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class NativePopupPresentationTest {
    @Test fun scopedPresentationAndEveryPopulationRoute() {
        val path = System.getenv("HUSHGRAM_GESTURE_APK")
        assumeTrue("HUSHGRAM_GESTURE_APK is unset", !path.isNullOrBlank())
        // Original 454 fixture: presenters, shared gate and renderer only.
        val classes = FixtureDex.classes(File(path!!), setOf("LX/0GKB;", "LX/0Gjr;", "LX/0NSr;", "LX/0Iof;", "LX/0LJp;", "LX/01SB;", "LX/01SD;", "LX/0hgO;", "LX/0Om0;", "LX/0I3u;")).values
        val context = PatchContexts.of(classes)
        val plan = context.nativePopupPlan()
        assertEquals(3, plan.choices.size)
        val inbox = plan.choices.single { it.method.definingClass == "LX/01SB;" }
        val nativeGateReads = inbox.method.implementation!!.instructions.count {
            ((it as? ReferenceInstruction)?.reference as? com.android.tools.smali.dexlib2.iface.reference.MethodReference)
                ?.definingClass == "LX/0Om0;"
        }
        assertTrue("inbox has a native presentation gate", nativeGateReads > 0)
        assertEquals("every native inbox gate chooses popups", nativeGateReads, inbox.results.size)
        assertTrue(inbox.results.values.all { it })
        assertEquals(4, plan.populations.size)
        val gate = classes.single { it.type == "LX/0Om0;" }.methods.associate { it.toString() to it.code() }
        context.applyNativePopupPlan(plan)
        val positioned = globalMutableClassDefBy(plan.primaryX.definingClass).methods.single { it.toString() == plan.primaryX.toString() }
        assertEquals(plan.primaryX.implementation!!.registerCount, positioned.implementation!!.registerCount)
        assertEquals(plan.primaryX.implementation!!.instructions.count { it.opcode == Opcode.RETURN },
            positioned.implementation!!.instructions.count {
                ((it as? ReferenceInstruction)?.reference?.toString())?.endsWith("MessageMenuPosition;->primaryX(I)I") == true
            })
        val anchorBridge = globalMutableClassDefBy("LX/0hgO;").methods.single { it.name == "instaprimeInboxAnchor" }
        assertEquals(listOf("LX/0hgO;->A00:I", "LX/0hgO;->A01:I"),
            anchorBridge.implementation!!.instructions.filter { it.opcode == Opcode.IPUT }.map {
                ((it as ReferenceInstruction).reference).toString()
            })
        assertTrue(anchorBridge.code().any { it.contains("RectF;->centerX()F") })
        assertTrue(anchorBridge.code().any { it.contains("RectF;->centerY()F") })
        val primary = globalMutableClassDefBy(plan.primary.definingClass).methods.single { it.name == plan.primary.name }
        assertEquals(1, primary.implementation!!.instructions.count {
            ((it as? ReferenceInstruction)?.reference?.toString())?.contains("MessageMenuPosition;->remember(") == true
        })
        for (choice in plan.choices) {
            val method = globalMutableClassDefBy(choice.method.definingClass).methods.single { it.toString() == choice.method.toString() }
            assertEquals(choice.method.implementation!!.registerCount, method.implementation!!.registerCount)
            val original = choice.method.implementation!!.instructions.toList()
            val patched = method.implementation!!.instructions.filter { it.opcode != Opcode.NOP }.toMutableList()
            val anchorCalls = patched.indices.filter {
                ((patched[it] as? ReferenceInstruction)?.reference?.toString())?.contains("->instaprimeInboxAnchor(") == true
            }
            assertEquals(if (choice.method == plan.inbox) 1 else 0, anchorCalls.size)
            for (callAt in anchorCalls.asReversed()) {
                assertEquals(Opcode.MOVE_OBJECT_FROM16, patched[callAt - 3].opcode)
                assertEquals(Opcode.MOVE_OBJECT_FROM16, patched[callAt - 2].opcode)
                assertEquals(Opcode.IF_EQZ, patched[callAt - 1].opcode)
                repeat(4) { patched.removeAt(callAt - 3) }
            }
            // dexlib may change payload alignment NOPs and widen jumps after insertion.
            // Compare meaningful operations independently of their encoding width.
            val expected = original.withIndex().filter { it.value.opcode != Opcode.NOP }.map { (at, instruction) ->
                val reference = (instruction as? ReferenceInstruction)?.reference
                when {
                    at in choice.results -> "${Opcode.CONST_16}:null"
                    choice.method == plan.more && (reference as? com.android.tools.smali.dexlib2.iface.reference.MethodReference)?.name == "showAtLocation" -> {
                        val opcode = if (instruction.opcode == Opcode.INVOKE_VIRTUAL_RANGE) Opcode.INVOKE_STATIC_RANGE else Opcode.INVOKE_STATIC
                        "$opcode:$EXTENSION_PACKAGE/direct/MessageMenuPosition;->showMore(Landroid/widget/PopupWindow;Landroid/view/View;III)V"
                    }
                    else -> "${instruction.semanticOpcode()}:$reference"
                }
            }
            assertEquals("only gate results and More placement calls change", expected,
                patched.map { "${it.semanticOpcode()}:${(it as? ReferenceInstruction)?.reference}" })
            for ((at, popup) in choice.results) {
                val instruction = patched[original.take(at).count { it.opcode != Opcode.NOP }]
                assertEquals(Opcode.CONST_16, instruction.opcode)
                assertEquals(if (popup) 1 else 0, (instruction as NarrowLiteralInstruction).narrowLiteral)
                assertEquals((original[at] as OneRegisterInstruction).registerA,
                    (instruction as OneRegisterInstruction).registerA)
            }
        }
        for (method in globalMutableClassDefBy("LX/0Om0;").methods) assertEquals(gate[method.toString()], method.code())
        for (original in plan.populations) {
            val method = globalMutableClassDefBy(original.definingClass).methods.single { it.toString() == original.toString() }
            assertEquals(original.implementation!!.registerCount, method.implementation!!.registerCount)
            assertEquals(original.implementation!!.instructions.count { it.opcode == Opcode.RETURN_VOID },
                method.implementation!!.instructions.count { ((it as? ReferenceInstruction)?.reference?.toString())?.contains("->styleNative(") == true })
        }
    }

    private fun Method.code() = implementation?.instructions?.map { "${it.opcode}:${(it as? ReferenceInstruction)?.reference}" }.orEmpty()

    private fun Instruction.semanticOpcode() = when (opcode) {
        Opcode.GOTO_16, Opcode.GOTO_32 -> Opcode.GOTO
        else -> opcode
    }
}
