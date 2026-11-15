package app.morphe.patches.instagram.misc.settings

import app.morphe.patcher.patch.mutableClassDefBy as globalMutableClassDefBy

import app.morphe.PatchContexts
import app.morphe.patches.instagram.FixtureDex
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class NativePopupPresentationTest {
    @Test fun scopedPresentationAndEveryPopulationRoute() {
        val path = System.getenv("HUSHGRAM_GESTURE_APK")
        assumeTrue("HUSHGRAM_GESTURE_APK is unset", !path.isNullOrBlank())
        // Original 454 fixture: presenters, shared gate and renderer only.
        val classes = FixtureDex.classes(File(path!!), setOf("LX/0GKB;", "LX/0Gjr;", "LX/0NSr;", "LX/01SB;", "LX/0Om0;", "LX/0I3u;")).values
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
        val primary = globalMutableClassDefBy(plan.primary.definingClass).methods.single { it.name == plan.primary.name }
        assertEquals(1, primary.implementation!!.instructions.count {
            ((it as? ReferenceInstruction)?.reference?.toString())?.contains("MessageMenuPosition;->remember(") == true
        })
        for (choice in plan.choices) {
            val method = globalMutableClassDefBy(choice.method.definingClass).methods.single { it.toString() == choice.method.toString() }
            assertEquals(choice.method.implementation!!.registerCount, method.implementation!!.registerCount)
            assertEquals(choice.method.code().size, method.code().size)
            for ((at, popup) in choice.results) {
                val instruction = method.implementation!!.instructions.toList()[at]
                assertEquals(Opcode.CONST_16, instruction.opcode)
                assertEquals(if (popup) 1 else 0, (instruction as NarrowLiteralInstruction).narrowLiteral)
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
}
