package app.morphe.patches.instagram.direct.ghost

import app.morphe.PatchContexts
import app.morphe.patcher.patch.mutableClassDefBy
import app.morphe.patches.instagram.FixtureDex
import app.morphe.patches.instagram.misc.settings.installInboxHeaderButtons
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class InboxHeaderButtonsHookTest {
    @Test fun bothInboxHeadersBindOnlyMarkedComposeActions() {
        val path = System.getenv("HUSHGRAM_GESTURE_APK")
        assumeTrue("HUSHGRAM_GESTURE_APK is unset", !path.isNullOrBlank())
        val apk = File(path!!)
        val kept = (GhostModeEntryHookTest.actionSlice(apk) + GhostModeEntryHookTest.slice(apk))
            .associateBy { it.type }.toMutableMap()
        val old = PatchContexts.of(kept.values).findGhostEntries()
        val types = old.map { it.longPress.substringBefore("->") }.toSet()
        FixtureDex.forEach(apk) { dex -> for (owner in dex.classes) {
            if (owner.type !in kept && owner.methods.any { it.parameterTypes.any { parameter -> parameter.toString() in types } })
                kept[owner.type] = ImmutableClassDef.of(owner)
        } }
        val context = PatchContexts.of(kept.values)
        assertTrue(context.addGhostModeEntry())
        context.installInboxHeaderButtons()
        val hook = "Lapp/hushgram/extension/instagram/settings/InboxHeaderButtons;"
        val references = kept.keys.flatMap { type -> mutableClassDefBy(type).methods.flatMap { method ->
            method.implementation?.instructions?.mapNotNull { (it as? ReferenceInstruction)?.reference?.toString() }.orEmpty()
        } }
        assertTrue("both action models and legacy configurations are marked", references.count { it == "$hook->mark(Ljava/lang/Object;)V" } >= 2)
        assertTrue("both header binders receive the native view and action", references.count {
            it == "$hook->bind(Landroid/view/View;Ljava/lang/Object;)V"
        } >= 2)
    }
}
