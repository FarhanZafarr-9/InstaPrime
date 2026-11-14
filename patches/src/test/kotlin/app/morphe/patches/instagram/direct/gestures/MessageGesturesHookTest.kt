/*
 * Copyright 2026 HushGram contributors
 * https://github.com/SysAdminDoc/HushGram
 */
package app.morphe.patches.instagram.direct.gestures

import app.morphe.ExtensionDex
import app.morphe.Fixtures
import app.morphe.PatchContexts
import app.morphe.patcher.patch.PatchException
import app.morphe.patches.instagram.FixtureDex
import app.morphe.patches.instagram.media.taptoplay.FRAGMENT_ACTIVITY
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.instruction.*
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableFieldReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class MessageGesturesHookTest {
    @Test fun reactionGateIgnoresAnEnclosingModelCondition() {
        assertEquals("tray", findReactionTrayGate(method(gates(10, 5))).name)
    }

    @Test fun crossingReactionGateCandidatesRefuse() {
        assertThrows(PatchException::class.java) { findReactionTrayGate(method(gates(9, 6))) }
    }

    @Test fun aBranchOnAnotherRegisterIsNotAReactionGate() {
        val code = gates(10, 5).toMutableList()
        code[3] = ImmutableInstruction21t(Opcode.IF_EQZ, 1, 5)
        assertEquals("outer", findReactionTrayGate(method(code)).name)
    }

    @Test fun forwardBranchThatLoopsBackIntoSetupIsNotTheTrayGate() {
        val code = gates(9, 6).toMutableList()
        // Outer branch lands at 11, then loops to the tray read at 4.
        code[6] = ImmutableInstruction10t(Opcode.GOTO, -7)
        assertEquals("tray", findReactionTrayGate(method(code)).name)
    }

    private fun method(code: List<Instruction>): Method = ImmutableMethod(
        "Lfixture/Tray;", "setup", emptyList(), "V", 0, emptySet(), emptySet(),
        ImmutableMethodImplementation(3, code, emptyList(), emptyList()))

    // Offsets: outer read 0, branch 2; tray read 4, branch 6; vibrator 8;
    // three trailing instruction boundaries at 10, 11, 12.
    private fun gates(outerJump: Int, trayJump: Int): List<Instruction> = listOf(
        ImmutableInstruction22c(Opcode.IGET_BOOLEAN, 0, 2,
            ImmutableFieldReference("Lcom/instagram/direct/messagethread/messageactions/model/MessageActionsViewModel;", "outer", "Z")),
        ImmutableInstruction21t(Opcode.IF_EQZ, 0, outerJump),
        ImmutableInstruction22c(Opcode.IGET_BOOLEAN, 0, 2,
            ImmutableFieldReference("Lcom/instagram/direct/messagethread/messageactions/model/MessageActionsViewModel;", "tray", "Z")),
        ImmutableInstruction21t(Opcode.IF_EQZ, 0, trayJump),
        ImmutableInstruction21c(Opcode.CONST_STRING, 1, ImmutableStringReference("vibrator")),
        ImmutableInstruction10x(Opcode.NOP),
        ImmutableInstruction10x(Opcode.NOP),
        ImmutableInstruction10x(Opcode.RETURN_VOID),
    )

    @Test fun absentNativeRoutesRefuseBeforeMutation() {
        assertThrows(PatchException::class.java) {
            PatchContexts.of(listOf(ExtensionDex.classDef(GESTURES))).findMessageGestures()
        }
    }

    @Test fun extensionExposesTheThreeHooks() {
        val methods = ExtensionDex.classDef(GESTURES).methods.map { "${it.name}(${it.parameterTypes.joinToString("")})${it.returnType}" }
        for (method in listOf("tap(Ljava/lang/Object;)Z", "actions(Ljava/util/List;)Ljava/util/List;", "reactions(Z)Z"))
            assertTrue("missing $method", method in methods)
    }

    @Test fun nativeTapHooksLeaveDoubleTapUntouched() {
        val direct = System.getenv("HUSHGRAM_GESTURE_APK")?.let(::File)
        val bundles = if (direct != null) listOf(direct) else Fixtures.files { it.extension in setOf("apk", "apks") }
        for (bundle in bundles) {
            val context = PatchContexts.of(load(bundle) + ExtensionDex.classDef(GESTURES))
            val site = context.findMessageGestures()
            val originalTap = site.tap.code()
            val originalHold = site.hold.code().map(::reference)
            val owner = context.classDefBy(site.tap.definingClass)
            val otherMethods = owner.methods.filter { it.name != site.tap.name }.associate { it.name to it.code().map(::reference) }
            context.applyMessageGestures(site)
            val tap = context.mutableClassDefBy(site.tap.definingClass).methods.single { it.name == site.tap.name }.code()
            assertEquals(originalTap.size + 4, tap.size)
            assertEquals("$GESTURES->tap(Ljava/lang/Object;)Z", reference(tap.first()))
            val after = context.mutableClassDefBy(site.tap.definingClass)
            for ((name, body) in otherMethods) assertEquals("${bundle.name}: native $name changed", body,
                after.methods.single { it.name == name }.code().map(::reference))
            assertEquals(originalHold, after.methods.single { it.name == site.hold.name }.code().map(::reference))
            val factory = context.mutableClassDefBy(site.factory.definingClass).methods.single { it.name == site.factory.name }.code()
            assertEquals(3, factory.count { reference(it) == "$GESTURES->actions(Ljava/util/List;)Ljava/util/List;" })
            assertEquals(1, factory.count { reference(it) == "$GESTURES->reactions(Z)Z" })
            val bridge = context.mutableClassDefBy(GESTURES).methods.single { it.name == "openNative" }.code()
            assertTrue(bridge.any { reference(it) == site.hold.text() })
            assertTrue(bridge.any { reference(it) == site.eligible.text() })
            assertTrue(bridge.any { reference(it) == site.blocked.text() })
            assertTrue(bridge.any { reference(it) == site.messageType.text() })
            assertTrue(bridge.any { reference(it) == "$GESTURES->x()F" })
            assertTrue(bridge.any { reference(it) == "$GESTURES->y()F" })
            assertEquals(1, factory.count { reference(it) == "$GESTURES->requireVisible()V" })
            assertEquals(Opcode.RETURN, bridge.last().opcode)
        }
    }

    private fun load(bundle: File): List<ClassDef> {
        val kept = mutableListOf<ClassDef>()
        val helperTypes = mutableSetOf<String>()
        FixtureDex.forEach(bundle) { dex -> for (owner in dex.classes) {
            if (owner.methods.any { method -> method.code().any { reference(it) == "should_show_like_direct_message_count" } }) {
                helperTypes += owner.type
                kept += ImmutableClassDef.of(owner)
            }
        } }
        FixtureDex.forEach(bundle) { dex -> for (owner in dex.classes) {
            if (owner.type in helperTypes) continue
            val keep = owner.type == FRAGMENT_ACTIVITY || owner.type == "Lcom/instagram/direct/messagethread/messageactions/model/MessageActionsViewModel;" ||
                owner.methods.any { method -> method.code().any { instruction ->
                    (instruction as? ReferenceInstruction)?.reference.let { ref ->
                        ref is MethodReference && ref.definingClass in helperTypes ||
                            ref is StringReference && ref.string == "reactionsCreationController must be initialized"
                    }
                } }
            if (keep) kept += ImmutableClassDef.of(owner)
        } }
        val dependencies = kept.flatMap { owner -> owner.methods.filter { method ->
            method.parameterTypes.map(Any::toString) == listOf("F", "F") && method.returnType == "V"
        }.flatMap { method -> method.code().mapNotNull { instruction ->
            when (val ref = (instruction as? ReferenceInstruction)?.reference) {
                is MethodReference -> ref.definingClass.takeIf { it.startsWith("LX/") }
                is FieldReference -> ref.type.takeIf { it.startsWith("LX/") }
                else -> null
            }
        } } }.filter { dependency -> kept.none { it.type == dependency } }.toSet()
        val messageTypes = kept.filter { it.type in helperTypes }.flatMap { owner -> owner.methods
            .filter { it.code().any { ins -> reference(ins) == "double_tap" } }
            .flatMap { it.parameterTypes.map(Any::toString) } }.filter { it.startsWith("LX/") }
        kept += FixtureDex.classes(bundle, (dependencies + messageTypes).filter { type -> kept.none { it.type == type } }.toSet()).values
        return kept
    }

    private fun Method.code(): List<Instruction> = implementation?.instructions?.toList().orEmpty()
    private fun reference(instruction: Instruction): String? = (instruction as? ReferenceInstruction)?.reference?.let {
        if (it is StringReference) it.string else it.toString()
    }
    private fun MethodReference.text() = "$definingClass->$name(${parameterTypes.joinToString("")})$returnType"
}
