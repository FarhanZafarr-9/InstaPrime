/* Copyright 2026 HushGram contributors */
package app.morphe.patches.instagram.direct.actions

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.instagram.misc.extension.EXTENSION_PACKAGE
import app.morphe.patches.instagram.misc.extension.enableStatus
import app.morphe.patches.instagram.misc.extension.instagramExtensionPatch
import app.morphe.patches.instagram.misc.extension.localRegisterCount
import app.morphe.patches.instagram.misc.extension.requireStatusMethod
import app.morphe.patches.instagram.misc.settings.settingsPatch
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.util.RegisterLiveness
import app.morphe.util.addInstructionsAtControlFlowLabel
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

internal const val ACTIONS = "$EXTENSION_PACKAGE/direct/MessageActions;"
internal const val MODEL = "Lcom/instagram/direct/messagethread/messageactions/model/MessageActionsViewModel;"
internal const val ACTION = "Lcom/instagram/direct/messagethread/interaction/longpressaction/LongPressActionData;"
internal const val MENU_HOOK = "$ACTIONS->menu(Ljava/lang/Object;Landroid/content/Context;)V"
private const val LIST = "Ljava/util/List;"

@Suppress("unused")
val messageActionsPatch = bytecodePatch(
    name = "Message action layout",
    description = "Hide selected message actions or move them into More, preserving Instagram's native availability and handlers.",
) {
    category("Messages")
    dependsOn(settingsPatch, instagramExtensionPatch)
    compatibleWith(*AppCompatibilities.instagram())
    execute {
        requireStatusMethod("messageActions")
        hookMessageActions(findMessageActions())
        enableStatus("messageActions")
    }
}

internal data class ActionSites(
    val factory: Method,
    val fields: List<FieldReference>,
    val action: FieldReference,
    val more: FieldReference,
    val category: FieldReference,
    val constructor: MethodReference,
    val label: Int,
    val icon: Int,
    val alternateIcon: Int,
    val returns: List<Pair<Int, Int>>,
)

private fun refuse(reason: String): Nothing = throw PatchException("Message action layout: $reason")

/** Validate all readers and the native More template before changing any method. */
internal fun BytecodePatchContext.findMessageActions(): ActionSites {
    val model = classDefByOrNull(MODEL) ?: refuse("no native message action model")
    val factory = model.methods.singleOrNull { m ->
        AccessFlags.STATIC.isSet(m.accessFlags) && m.returnType == MODEL &&
            m.parameterTypes.firstOrNull()?.toString() == "Landroid/content/Context;" &&
            m.code().count { it.opcode == Opcode.IPUT_OBJECT && it.field()?.type == LIST && it.field()?.definingClass == MODEL } == 5
    } ?: refuse("no unique native model factory with five list assignments")
    val code = factory.code()
    if (code.any { it.method()?.definingClass == ACTIONS }) refuse("model factory already calls the extension")
    val listWrites = code.filter { it.opcode == Opcode.IPUT_OBJECT && it.field()?.type == LIST && it.field()?.definingClass == MODEL }
    // The factory stores its three local action lists first, second and last. Reactions and other
    // supplied lists are moved from parameters immediately before the intervening two stores.
    val localLists = listOf(listWrites[0], listWrites[1], listWrites[4])
    if (localLists.map { (it as TwoRegisterInstruction).registerA }.distinct().size != 3) refuse("action list registers overlap")
    for (write in listOf(listWrites[2], listWrites[3])) {
        val at = code.indexOf(write)
        val move = code.getOrNull(at - 1) as? TwoRegisterInstruction
        if (code.getOrNull(at - 1)?.opcode !in setOf(Opcode.MOVE_OBJECT, Opcode.MOVE_OBJECT_FROM16, Opcode.MOVE_OBJECT_16) ||
            move == null || move.registerA != (write as TwoRegisterInstruction).registerA || move.registerB < factory.localRegisterCount()) {
            refuse("intervening lists do not come directly from factory parameters")
        }
    }
    val fields = localLists.map { it.field()!! }
    val action = code.filter { it.opcode == Opcode.IGET_OBJECT && it.field()?.definingClass == ACTION }
        .mapNotNull { it.field() }.distinctBy { it.toString() }.filter { field ->
            val type = classDefByOrNull(field.type)
            type?.superclass == "Ljava/lang/Enum;" && constants(type).keys.containsAll(listOf("REPLY", "FORWARD", "MORE"))
        }.singleOrNull() ?: refuse("no unique selected action reader")
    val enum = classDefByOrNull(action.type) ?: refuse("no action enum")
    if (enum.superclass != "Ljava/lang/Enum;") refuse("selected action is not an enum")
    val constants = constants(enum)
    if (!constants.keys.containsAll(listOf("REPLY", "FORWARD", "COPY_TEXT", "TRANSLATE_MESSAGE", "MORE", "META_AI"))) refuse("incomplete action enum")
    val more = constants.getValue("MORE")
    val start = code.indexOfFirst { it.opcode == Opcode.SGET_OBJECT && it.field()?.toString() == more.toString() }
    val end = (start + 1 until minOf(code.size, start + 30)).firstOrNull {
        code[it].method()?.let { m -> m.definingClass == ACTION && m.name == "<init>" } == true
    } ?: refuse("no native More constructor after the enum read")
    val template = code.subList(start, end + 1)
    val literals = template.mapNotNull { (it as? NarrowLiteralInstruction)?.narrowLiteral }.filter { it > 0x7f000000 }
    if (literals.size != 3) refuse("native More template has ${literals.size} resources, expected label and two icons")
    val category = template.mapNotNull { if (it.opcode == Opcode.SGET_OBJECT) it.field() else null }
        .singleOrNull { it.type != enum.type } ?: refuse("no unique More category")
    val constructor = code[end].method()!!
    if (constructor.parameterTypes.size != 11 || constructor.parameterTypes[2].toString() != enum.type ||
        constructor.parameterTypes[1].toString() != category.type) refuse("More constructor changed")
    for (type in listOf(MODEL, ACTION, action.type, category.type)) {
        val native = classDefByOrNull(type) ?: refuse("missing native bridge type $type")
        if (!AccessFlags.PUBLIC.isSet(native.accessFlags)) refuse("native bridge type is not public: $type")
    }
    for (field in fields + listOf(action, more, category)) {
        val declared = classDefByOrNull(field.definingClass)!!.fields.single { it.name == field.name }
        if (!AccessFlags.PUBLIC.isSet(declared.accessFlags)) refuse("native bridge field is not public: $field")
    }
    val nativeConstructor = classDefByOrNull(ACTION)!!.methods.single { it.name == "<init>" && it.parameterTypes == constructor.parameterTypes }
    if (!AccessFlags.PUBLIC.isSet(nativeConstructor.accessFlags)) refuse("native More constructor is not public")
    val returns = code.indices.filter { code[it].opcode == Opcode.RETURN_OBJECT }.map { at ->
        val register = (code[at] as OneRegisterInstruction).registerA
        if (register >= 16) refuse("model return is not in a low register")
        val scratch = (0 until minOf(factory.localRegisterCount(), 16)).firstOrNull {
            it != register && it !in RegisterLiveness.of(factory).liveInto(at)
        } ?: refuse("no free register at model return")
        at to scratch
    }
    if (returns.isEmpty()) refuse("no model return")
    return ActionSites(factory, fields, action, more, category, constructor, literals[0], literals[1], literals[2], returns)
}

internal fun BytecodePatchContext.hookMessageActions(site: ActionSites) {
    for (reference in site.fields) {
        val field = mutableClassDefBy(MODEL).fields.single { it.name == reference.name && it.type == reference.type }
        field.accessFlags = field.accessFlags and AccessFlags.FINAL.value.inv()
    }
    val extension = mutableClassDefBy(ACTIONS)
    fun stub(name: String) = extension.methods.single { it.name == name }
    for ((index, name) in listOf("primary", "more", "ai").withIndex()) {
        stub(name).addInstructions(0, "check-cast p0, $MODEL\niget-object p0, p0, ${site.fields[index]}\nreturn-object p0")
    }
    stub("action").addInstructions(0, "check-cast p0, $ACTION\niget-object p0, p0, ${site.action}\nreturn-object p0")
    stub("replace").addInstructions(0, """
        check-cast p0, $MODEL
        iput-object p1, p0, ${site.fields[0]}
        iput-object p2, p0, ${site.fields[1]}
        iput-object p3, p0, ${site.fields[2]}
        return-void
    """)
    stub("makeMore").addInstructions(0, """
        new-instance p1, $ACTION
        const/4 p2, 0x0
        sget-object p3, ${site.category}
        sget-object p4, ${site.more}
        const/4 p5, 0x0
        const/4 p6, 0x0
        const p7, ${site.icon}
        invoke-static {p7}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;
        move-result-object p7
        const p8, ${site.alternateIcon}
        invoke-static {p8}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;
        move-result-object p8
        const p9, ${site.label}
        invoke-virtual {p0, p9}, Landroid/content/Context;->getString(I)Ljava/lang/String;
        move-result-object p9
        const/4 p10, 0x0
        const/4 p11, 0x0
        const/4 p12, 0x0
        invoke-direct/range {p1 .. p12}, ${site.constructor}
        return-object p1
    """)
    val factory = mutableClassDefBy(MODEL).methods.single {
        it.name == site.factory.name && it.parameterTypes == site.factory.parameterTypes
    }
    for ((at, scratch) in site.returns.asReversed()) {
        val model = (factory.code()[at] as OneRegisterInstruction).registerA
        factory.addInstructionsAtControlFlowLabel(at, """
            move-object/from16 v$scratch, p0
            invoke-static {v$model, v$scratch}, $MENU_HOOK
        """)
    }
}

private fun constants(type: ClassDef): Map<String, FieldReference> {
    val result = linkedMapOf<String, FieldReference>()
    var name: String? = null
    for (i in type.methods.single { it.name == "<clinit>" }.code()) {
        val value = (i as? ReferenceInstruction)?.reference
        if (value is StringReference && value.string.matches(Regex("[A-Z][A-Z_]+"))) name = value.string
        if (i.opcode == Opcode.SPUT_OBJECT && value is FieldReference && value.type == type.type && name != null) result[name] = value
    }
    return result
}
private fun Method.code() = implementation?.instructions?.toList().orEmpty()
private fun Instruction.field() = (this as? ReferenceInstruction)?.reference as? FieldReference
private fun Instruction.method() = (this as? ReferenceInstruction)?.reference as? MethodReference
