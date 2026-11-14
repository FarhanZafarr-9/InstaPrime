/* Copyright 2026 HushGram contributors */
package app.morphe.patches.instagram.direct.actions

import app.morphe.ExtensionDex
import app.morphe.Fixtures
import app.morphe.PatchContexts
import app.morphe.patcher.patch.PatchException
import app.morphe.patches.instagram.FixtureDex
import app.morphe.patches.shared.compat.AppCompatibilities
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class MessageActionsHookTest {
    @Test fun standaloneNativeApk() {
        val path = System.getenv("HUSHGRAM_MESSAGE_FIXTURE_APK")
        org.junit.Assume.assumeTrue("HUSHGRAM_MESSAGE_FIXTURE_APK is unset", !path.isNullOrBlank())
        builds(File(path!!))
    }
    @Test fun missingNativeModelRefusesBeforeMutating() {
        assertThrows(PatchException::class.java) { PatchContexts.of(emptyList()).findMessageActions() }
    }
    @Test fun nativeFixtureHasVerifiedReadersAndNativeMoreTemplate() {
        val versions = AppCompatibilities.instagram().single().targets.mapNotNull { it.version }.toSet()
        var checked = 0
        for (version in versions) for (bundle in Fixtures.files { it.extension == "apks" && it.name.contains("-$version-") }) {
            builds(bundle)
            checked++
        }
        assertTrue("no declared build was checked", checked > 0)
    }
    @Test fun otherNativeBuildsHaveVerifiedReadersAndTemplate() {
        for (bundle in Fixtures.otherBuilds()) builds(bundle)
    }
    private fun builds(bundle: File) {
            val classes = mutableListOf<ClassDef>()
            FixtureDex.forEach(bundle) { dex ->
                for (type in dex.classes) if (type.type == MODEL || type.type == ACTION || type.superclass == "Ljava/lang/Enum;") {
                    classes += ImmutableClassDef.of(type)
                }
            }
            val context = PatchContexts.of(classes + ExtensionDex.classDef(ACTIONS))
            val found = context.findMessageActions()
            val original = found.factory.implementation!!.instructions.map { it.opcode }.toList()
            assertEquals(3, found.fields.size)
            assertEquals(3, found.fields.map { it.toString() }.distinct().size)
            assertTrue(found.label > 0x7f000000)
            assertTrue(found.icon > 0x7f000000)
            assertTrue(found.alternateIcon > 0x7f000000)
            context.hookMessageActions(found)
            val patched = context.mutableClassDefBy(MODEL).methods.single { it.name == found.factory.name && it.parameterTypes == found.factory.parameterTypes }
            val hooks = patched.implementation!!.instructions.filter { (it as? ReferenceInstruction)?.reference.toString() == MENU_HOOK }
            assertEquals(found.returns.size, hooks.count())
            assertEquals(original.size + found.returns.size * 2, patched.implementation!!.instructions.count())
            assertEquals(original.count { it == Opcode.RETURN_OBJECT }, patched.implementation!!.instructions.count { it.opcode == Opcode.RETURN_OBJECT })
            val maker = context.mutableClassDefBy(ACTIONS).methods.single { it.name == "makeMore" }
            assertEquals(13, maker.parameterTypes.size)
            assertTrue(maker.implementation!!.registerCount >= 13)
            assertTrue(maker.implementation!!.instructions.any { it.opcode == Opcode.INVOKE_DIRECT_RANGE &&
                (it as? ReferenceInstruction)?.reference.toString() == found.constructor.toString() })
    }
}
