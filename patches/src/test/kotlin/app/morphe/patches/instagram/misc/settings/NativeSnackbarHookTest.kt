/* Copyright 2026 InstaPrime contributors */
package app.morphe.patches.instagram.misc.settings

import app.morphe.ExtensionDex
import app.morphe.PatchContexts
import app.morphe.patcher.patch.PatchException
import app.morphe.patches.instagram.FixtureDex
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class NativeSnackbarHookTest {
    @Test fun missingNativeViewsRefuseBeforeMutation() {
        assertThrows(PatchException::class.java) { PatchContexts.of(emptyList()).nativeSnackbarSites() }
    }

    @Test fun sharedVariantsKeepTheirNativeFieldsCallbacksAndRegisters() {
        val path = System.getenv("HUSHGRAM_GESTURE_APK")
        assumeTrue("HUSHGRAM_GESTURE_APK is unset", !path.isNullOrBlank())
        val classes = FixtureDex.classes(File(path!!), SNACKBAR_TYPES.toSet()).values
        val context = PatchContexts.of(classes)
        val sites = context.nativeSnackbarSites()
        assertEquals(3, sites.size)
        val nativeMethods = classes.flatMap { it.methods }.associate { it.toString() to it.code() }
        val fields = classes.flatMap { it.fields }.associate { it.toString() to it.accessFlags }
        context.styleNativeSnackbars(sites)
        val extension = ExtensionDex.classDef(FEEDBACK_STYLE.substringBefore("->"))
        assertTrue(extension.methods.any { it.name == "style" && it.parameterTypes.map(Any::toString) == listOf("Landroid/view/View;") })
        for (site in sites) {
            val owner = context.mutableClassDefBy(site.type)
            for (field in owner.fields) assertEquals(fields[field.toString()], field.accessFlags)
            val targeted = site.methods.map { it.toString() }.toSet()
            for (method in owner.methods.filter { it.toString() in nativeMethods }) {
                val original = nativeMethods.getValue(method.toString())
                if (method.toString() !in targeted) assertEquals("native actions and gesture handlers stay unchanged", original, method.code())
                else {
                    val hooks = method.implementation!!.instructions.filter { (it as? ReferenceInstruction)?.reference?.toString() == "${site.type}->instaprimeStyleFeedback()V" }
                    val old = site.methods.single { it.toString() == method.toString() }
                    assertEquals(old.implementation!!.instructions.count { it.opcode == Opcode.RETURN_VOID }, hooks.count())
                    assertEquals(old.implementation!!.registerCount, method.implementation!!.registerCount)
                }
            }
        }
    }

    private fun Method.code() = implementation?.instructions?.map { "${it.opcode}:${(it as? ReferenceInstruction)?.reference}" }.orEmpty()
}
