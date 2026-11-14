/* Copyright 2026 InstaPrime contributors */
package app.morphe.patches.instagram.misc.settings

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patches.instagram.misc.extension.EXTENSION_PACKAGE
import app.morphe.util.addInstructionsAtControlFlowLabel
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation

internal val SNACKBAR_TYPES = listOf("IgdsSnackBar", "IgdsDualButtonSnackBar", "IgdsUploadSnackBar")
    .map { "Lcom/instagram/igds/components/snackbar/$it;" }
internal const val FEEDBACK_STYLE = "$EXTENSION_PACKAGE/settings/NativeSnackbar;->style(Landroid/view/View;)V"
private const val BRIDGE = "instaprimeStyleFeedback"
internal data class NativeSnackbarSite(val type: String, val surface: FieldReference, val methods: List<Method>)

/** Semantic view classes; the private surface field is discovered by its type and constructor write. */
internal fun BytecodePatchContext.nativeSnackbarSites(): List<NativeSnackbarSite> = SNACKBAR_TYPES.map { type ->
    val owner = classDefByOrNull(type) ?: throw PatchException("Native snackbar view missing: $type")
    val surface = owner.fields.singleOrNull { it.type == "Landroid/view/ViewGroup;" && !AccessFlags.STATIC.isSet(it.accessFlags) }
        ?: throw PatchException("No unique native snackbar surface in $type")
    val constructor = owner.methods.singleOrNull { it.name == "<init>" && it.parameterTypes.map(Any::toString) ==
        listOf("Landroid/content/Context;", "Landroid/util/AttributeSet;", "I") }
        ?: throw PatchException("No initialized native snackbar constructor in $type")
    if (constructor.implementation?.instructions?.none { ins -> ins.opcode == Opcode.IPUT_OBJECT &&
            ((ins as? ReferenceInstruction)?.reference as? FieldReference)?.toString() == surface.toString() } != false)
        throw PatchException("Native snackbar constructor does not initialize its surface: $type")
    val setters = setOf("setSnackBarBackgroundColor", "setTextColor", "setButtonTextColor", "setStatusText",
        "setExplanationText", "setButtonTextAndOnClickListener", "setMessageText", "setMessageDescriptionText",
        "setPrimaryButtonTextAndOnClickListener", "setSecondaryButtonTextAndOnClickListener")
    val methods = listOf(constructor) + owner.methods.filter { it.name in setters && it.returnType == "V" && it.implementation != null }
    if (owner.methods.any { it.name == BRIDGE } || methods.any { method -> method.implementation!!.instructions.none { it.opcode == Opcode.RETURN_VOID } })
        throw PatchException("Unexpected native snackbar hook shape: $type")
    NativeSnackbarSite(type, surface, methods)
}

internal fun BytecodePatchContext.styleNativeSnackbars(sites: List<NativeSnackbarSite>) {
    for (site in sites) {
        val owner = mutableClassDefBy(site.type)
        // The bridge reads its own private field. No native field needs to become public.
        owner.methods.add(ImmutableMethod(site.type, BRIDGE, emptyList(), "V",
            AccessFlags.PRIVATE.value or AccessFlags.FINAL.value, emptySet(), emptySet(),
            ImmutableMethodImplementation(1, emptyList(), null, null)).toMutable().apply {
            addInstructions(0, """
                iget-object p0, p0, ${site.surface}
                invoke-static {p0}, $FEEDBACK_STYLE
                return-void
            """)
        })
        for (original in site.methods) {
            val method = owner.methods.single { it.name == original.name && it.parameterTypes == original.parameterTypes }
            val code = method.implementation!!.instructions.toList()
            code.indices.filter { code[it].opcode == Opcode.RETURN_VOID }.asReversed().forEach { at ->
                method.addInstructionsAtControlFlowLabel(at, "invoke-direct/range {p0 .. p0}, ${site.type}->$BRIDGE()V")
            }
        }
    }
}
