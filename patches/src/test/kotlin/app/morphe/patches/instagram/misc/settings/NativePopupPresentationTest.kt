package app.morphe.patches.instagram.misc.settings

import app.morphe.patcher.patch.mutableClassDefBy as globalMutableClassDefBy

import app.morphe.PatchContexts
import app.morphe.patches.instagram.FixtureDex
import app.morphe.patches.instagram.misc.extension.EXTENSION_PACKAGE
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class NativePopupPresentationTest {
    @Test fun scopedPresentationAndEveryPopulationRoute() {
        val path = System.getenv("HUSHGRAM_GESTURE_APK")
        assumeTrue("HUSHGRAM_GESTURE_APK is unset", !path.isNullOrBlank())
        val classes = popupSlice(File(path!!))
        val context = PatchContexts.of(classes)
        val plan = context.nativePopupPlan()
        assertEquals(3, plan.choices.size)
        val inbox = plan.choices.single { it.method == plan.inbox }
        val gateMethod = classes.flatMap { it.methods }.single { method ->
            method.returnType == "Z" && method.parameterTypes.map(Any::toString) ==
                listOf("Lcom/instagram/common/session/UserSession;") && method.implementation?.instructions?.any {
                (it as? WideLiteralInstruction)?.wideLiteral == 36329998780490487L
            } == true
        }
        val gateType = gateMethod.definingClass
        val nativeGateReads = inbox.method.implementation!!.instructions.count {
            ((it as? ReferenceInstruction)?.reference as? com.android.tools.smali.dexlib2.iface.reference.MethodReference)
                ?.toString() == gateMethod.toString()
        }
        assertTrue("inbox has a native presentation gate", nativeGateReads > 0)
        assertEquals("every native inbox gate chooses popups", nativeGateReads, inbox.results.size)
        assertTrue(inbox.results.values.all { it })
        assertTrue("all native population variants are checked", plan.populations.size >= 2)
        val gate = classes.single { it.type == gateType }.methods.associate { it.toString() to it.code() }
        val factory = plan.inbox.implementation!!.instructions.mapNotNull {
            (it as? ReferenceInstruction)?.reference as? MethodReference
        }.single { call -> call.parameterTypes.take(2).map(Any::toString) == listOf("Landroid/view/View;", "Landroid/view/View;") &&
            classes.singleOrNull { it.type == call.definingClass }?.methods?.any { method ->
                method.toString() == call.toString() && method.code().any { it.contains("->getLocationOnScreen(") }
            } == true }
        context.applyNativePopupPlan(plan)
        val positioned = globalMutableClassDefBy(plan.primaryX.definingClass).methods.single { it.toString() == plan.primaryX.toString() }
        assertEquals(plan.primaryX.implementation!!.registerCount, positioned.implementation!!.registerCount)
        assertEquals(plan.primaryX.implementation!!.instructions.count { it.opcode == Opcode.RETURN },
            positioned.implementation!!.instructions.count {
                ((it as? ReferenceInstruction)?.reference?.toString())?.endsWith("MessageMenuPosition;->primaryX(I)I") == true
            })
        val popupBridge = globalMutableClassDefBy(factory.definingClass).methods.single { it.name == "instaprimeInboxPopup" }
        assertTrue(popupBridge.code().any { it.contains("InboxPopup;->show(Landroid/view/View;Landroid/view/View;)Z") })
        assertTrue("original overlay remains available as fallback", popupBridge.code().any {
            it.endsWith(":$factory")
        })
        val primary = globalMutableClassDefBy(plan.primary.definingClass).methods.single { it.name == plan.primary.name }
        assertEquals(1, primary.implementation!!.instructions.count {
            ((it as? ReferenceInstruction)?.reference?.toString())?.contains("MessageMenuPosition;->remember(") == true
        })
        for (choice in plan.choices) {
            val method = globalMutableClassDefBy(choice.method.definingClass).methods.single { it.toString() == choice.method.toString() }
            assertEquals(choice.method.implementation!!.registerCount, method.implementation!!.registerCount)
            val original = choice.method.implementation!!.instructions.toList()
            val patched = method.implementation!!.instructions.filter { it.opcode != Opcode.NOP }.toMutableList()
            val preparations = patched.indices.filter {
                ((patched[it] as? ReferenceInstruction)?.reference?.toString())?.contains("InboxPopup;->prepare(") == true
            }
            val nativePopulations = original.count {
                ((it as? ReferenceInstruction)?.reference as? com.android.tools.smali.dexlib2.iface.reference.MethodReference)?.let { call ->
                    call.definingClass == plan.populations.first().definingClass && call.returnType == "V" &&
                        call.parameterTypes.map(Any::toString) == listOf("Ljava/util/List;")
                } == true
            }
            assertEquals(if (choice.method == plan.inbox) nativePopulations else 0, preparations.size)
            for (callAt in preparations.asReversed()) {
                assertEquals(Opcode.MOVE_OBJECT_FROM16, patched[callAt - 2].opcode)
                assertEquals(Opcode.MOVE_OBJECT_FROM16, patched[callAt - 1].opcode)
                repeat(3) { patched.removeAt(callAt - 2) }
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
                    choice.method == plan.inbox && reference?.toString() == factory.toString() ->
                        "${instruction.semanticOpcode()}:${reference.toString().replace("->${factory.name}(", "->instaprimeInboxPopup(")}"
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
        for (method in globalMutableClassDefBy(gateType).methods) assertEquals(gate[method.toString()], method.code())
        for (original in plan.populations) {
            val method = globalMutableClassDefBy(original.definingClass).methods.single { it.toString() == original.toString() }
            assertEquals(original.implementation!!.registerCount, method.implementation!!.registerCount)
            assertEquals(original.implementation!!.instructions.count { it.opcode == Opcode.RETURN_VOID },
                method.implementation!!.instructions.count { ((it as? ReferenceInstruction)?.reference?.toString())?.contains("->styleNative(") == true })
        }
    }

    private fun Method.code() = implementation?.instructions?.map { "${it.opcode}:${(it as? ReferenceInstruction)?.reference}" }.orEmpty()

    /** Semantic anchors survive Redex renaming between supported Instagram versions. */
    private fun popupSlice(apk: File): Collection<ClassDef> {
        val flags = setOf(36329998780490487L, 36322151875231018L)
        val kept = FixtureDex.classesLoadingAny(apk, setOf("DirectInboxThreadDialogController",
            "MessageActionsHelper", "reactionsCreationController must be initialized")) { owner ->
            owner.superclass == "Landroid/widget/PopupWindow;" || owner.methods.any { method ->
                method.implementation?.instructions?.any { (it as? WideLiteralInstruction)?.wideLiteral in flags } == true
            }
        }.associateBy { it.type }.toMutableMap()
        val renderer = kept.values.single { it.superclass == "Landroid/widget/PopupWindow;" && it.methods.any { method ->
            method.name == "<init>" && method.parameterTypes.map(Any::toString) == listOf("Landroid/content/Context;",
                "Lcom/instagram/common/session/UserSession;", "Ljava/lang/Integer;", "Z")
        } }.type
        val presenters = kept.values.filter { owner -> owner.methods.any { method ->
            method.name == "onViewCreated" && method.implementation?.instructions?.any {
                ((it as? ReferenceInstruction)?.reference as? MethodReference)?.definingClass == renderer
            } == true
        } }
        val created = presenters.flatMap { it.methods }.flatMap { method ->
            method.implementation?.instructions?.filter { it.opcode == Opcode.NEW_INSTANCE }?.mapNotNull {
                ((it as? ReferenceInstruction)?.reference as? TypeReference)?.type
            }.orEmpty()
        }.toSet()
        val parents = mutableSetOf<String>()
        FixtureDex.forEach(apk) { dex -> for (owner in dex.classes) {
            if (owner.type in created && owner.fields.any { it.type == renderer }) {
                kept[owner.type] = ImmutableClassDef.of(owner)
                owner.superclass?.let(parents::add)
            }
        } }
        val factories = kept.values.flatMap { it.methods }.filter {
            "Lcom/instagram/model/direct/DirectThreadKey;" in it.parameterTypes.map(Any::toString)
        }.flatMap { method -> method.implementation?.instructions?.mapNotNull {
            ((it as? ReferenceInstruction)?.reference as? MethodReference)?.takeIf { call ->
                call.parameterTypes.take(2).map(Any::toString) == listOf("Landroid/view/View;", "Landroid/view/View;") &&
                    call.returnType.startsWith("L")
            }
        }.orEmpty() }.flatMap { listOf(it.definingClass, it.returnType) }.toSet()
        kept.putAll(FixtureDex.classes(apk, parents + factories))
        return kept.values
    }

    private fun Instruction.semanticOpcode() = when (opcode) {
        Opcode.GOTO_16, Opcode.GOTO_32 -> Opcode.GOTO
        else -> opcode
    }
}
