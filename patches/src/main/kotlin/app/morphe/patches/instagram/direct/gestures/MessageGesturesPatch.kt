/*
 * Copyright 2026 HushGram contributors
 * https://github.com/SysAdminDoc/HushGram
 */
package app.morphe.patches.instagram.direct.gestures

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.instagram.misc.extension.EXTENSION_PACKAGE
import app.morphe.patches.instagram.misc.extension.classesHolding
import app.morphe.patches.instagram.misc.extension.classesCallingInto
import app.morphe.patches.instagram.misc.extension.enableStatus
import app.morphe.patches.instagram.misc.extension.instagramExtensionPatch
import app.morphe.patches.instagram.misc.extension.localRegisterCount
import app.morphe.patches.instagram.misc.extension.requireStatusMethod
import app.morphe.patches.instagram.misc.settings.settingsPatch
import app.morphe.patches.instagram.media.taptoplay.FRAGMENT_ACTIVITY
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.util.RegisterLiveness
import app.morphe.util.ControlFlow
import app.morphe.util.addInstructionsAtControlFlowLabel
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation

internal const val GESTURES = "$EXTENSION_PACKAGE/direct/MessageGestures;"
private const val MODEL = "Lcom/instagram/direct/messagethread/messageactions/model/MessageActionsViewModel;"
private const val LIST = "Ljava/util/List;"
private const val FUNCTION = "Lkotlin/jvm/functions/Function0;"

@Suppress("unused")
val messageGesturesPatch = bytecodePatch(
    name = "Message tap menus",
    description = "Lets a single tap on a chat message open its native actions, reactions, or both. " +
        "Starts off. Existing double-tap behavior is preserved.",
) {
    category("Messages")
    dependsOn(settingsPatch, instagramExtensionPatch)
    compatibleWith(*AppCompatibilities.instagram())
    execute {
        requireStatusMethod("messageGestures")
        val site = findMessageGestures()
        val timing = messageTiming(site)
        applyMessageGestures(site)
        applyMessageTiming(site, timing)
        enableStatus("messageGestures")
    }
}

internal class MessageGestureSite(
    val tap: Method,
    val hold: Method,
    val provider: FieldReference,
    val data: String,
    val eligible: MethodReference,
    val controller: FieldReference,
    val blocked: MethodReference,
    val messageType: MethodReference,
    val typeName: FieldReference,
    val touch: Method,
    val factory: Method,
    val actionWrites: List<Int>,
    val reactionWrite: Int,
    val floatingWrite: Int,
    val scratch: Map<Int, Int>,
)

/** Discover the delegate from its call to the native double-tap helper, never by obfuscated names. */
internal fun BytecodePatchContext.findMessageGestures(): MessageGestureSite {
    val helpers = classesHolding("should_show_like_direct_message_count").flatMap { it.methods }
        .filter { method -> method.code().any { it.string() == "double_tap" } &&
            method.returnType == "V" && method.parameterTypes.any { it.toString() == "Lcom/instagram/model/direct/messageid/MessageIdentifier;" }.not() }
    val helper = helpers.singleOrNull { method -> method.code().any { instruction ->
        instruction.call()?.parameterTypes?.any { it.toString() == "Lcom/instagram/model/direct/messageid/MessageIdentifier;" } == true
    } } ?: refuse("expected one message double-tap helper")
    val delegates = mutableListOf<Pair<Method, Method>>()
    classesCallingInto(helper.definingClass).forEach { owner ->
        if (owner.methods.any { method -> method.parameterTypes.isEmpty() && method.returnType == "V" &&
                    method.code().any { it.call()?.text() == helper.text() } }) {
            val holds = owner.methods.filter { method -> method.types() == listOf("F", "F") && method.returnType == "V" &&
                method.code().any { it.call()?.definingClass == "Landroid/graphics/PointF;" } }
            val taps = owner.methods.filter { method -> method.parameterTypes.isEmpty() && method.returnType == "V" &&
                method.code().any { instruction -> instruction.opcode == Opcode.INVOKE_INTERFACE &&
                    instruction.call()?.types() == listOf("Ljava/lang/Object;") && instruction.call()?.returnType == "V" } }
            if (holds.size == 1 && taps.size == 1) delegates += taps.single() to holds.single()
        }
    }
    val (tap, hold) = delegates.singleOrNull() ?: refuse("expected one Compose message tap/hold delegate, found ${delegates.size}")
    if (tap.localRegisterCount() < 1 || !AccessFlags.PUBLIC.isSet(hold.accessFlags)) refuse("native delegate is not callable")
    val code = hold.code()
    val provider = code.mapNotNull { it.field() }.filter { it.type == FUNCTION }.distinctBy { it.toString() }.singleOrNull()
        ?: refuse("expected one message data provider")
    val data = code.firstOrNull { it.opcode == Opcode.CHECK_CAST }?.let { (it as ReferenceInstruction).reference as TypeReference }?.type
        ?: refuse("no message data cast")
    val guards = code.indices.mapNotNull { at ->
        val call = code[at].call() ?: return@mapNotNull null
        if (call.returnType == "Z" && call.parameterTypes.isEmpty() &&
            code.getOrNull(at + 1)?.opcode == Opcode.MOVE_RESULT &&
            code.getOrNull(at + 2)?.opcode in setOf(Opcode.IF_EQZ, Opcode.IF_NEZ)) call else null
    }
    val eligible = guards.singleOrNull { it.definingClass == data } ?: refuse("no unique message menu eligibility guard")
    val blocked = guards.singleOrNull { it.definingClass != data } ?: refuse("no unique controller guard")
    val controller = code.mapNotNull { it.field() }.filter { it.type == blocked.definingClass }.distinctBy { it.toString() }.singleOrNull()
        ?: refuse("no controller for menu availability")
    val messageType = classDefBy(data).methods.singleOrNull { method ->
        method.parameterTypes.isEmpty() && method.returnType in helper.types() &&
            classDefByOrNull(method.returnType)?.methods?.any { it.name == "<clinit>" && it.code().any { ins -> ins.string() == "text" } } == true
    } ?: refuse("no unique native message type reader")
    val typeName = classDefBy(messageType.returnType).fields.singleOrNull {
        !AccessFlags.STATIC.isSet(it.accessFlags) && it.type == "Ljava/lang/String;"
    } ?: refuse("no unique native message type name")
    for (type in listOf(tap.definingClass, data, blocked.definingClass, messageType.returnType)) {
        if (!AccessFlags.PUBLIC.isSet(classDefBy(type).accessFlags)) refuse("native bridge type $type is not public")
    }
    for (field in listOf(provider, controller, typeName)) {
        val declaration = classDefBy(field.definingClass).fields.singleOrNull { it.name == field.name && it.type == field.type }
            ?: refuse("native bridge field $field is absent")
        if (!AccessFlags.PUBLIC.isSet(declaration.accessFlags)) refuse("native bridge field $field is not public")
    }
    for (call in listOf(eligible, blocked, messageType)) {
        val declaration = classDefBy(call.definingClass).methods.singleOrNull {
            it.name == call.name && it.types() == call.types() && it.returnType == call.returnType
        } ?: refuse("native bridge method ${call.text()} is absent")
        if (!AccessFlags.PUBLIC.isSet(declaration.accessFlags)) refuse("native bridge method ${call.text()} is not public")
    }

    val factories = classDefBy(MODEL).methods.filter { method ->
        AccessFlags.STATIC.isSet(method.accessFlags) && method.returnType == MODEL &&
            method.code().any { it.opcode == Opcode.NEW_INSTANCE && ((it as ReferenceInstruction).reference as TypeReference).type == MODEL }
    }
    val factory = factories.singleOrNull() ?: refuse("expected one native message menu factory")
    val writes = factory.code().indices.filter { at -> val ins = factory.code()[at]
        ins.opcode == Opcode.IPUT_OBJECT && ins.field()?.definingClass == MODEL && ins.field()?.type == LIST }
    if (writes.size != 5) refuse("expected five native model lists, found ${writes.size}")
    val fragment = classesHolding("reactionsCreationController must be initialized", "vibrator")
        .flatMap { it.methods }.singleOrNull { it.name == "onViewCreated" && it.code().any { ins -> ins.string() == "vibrator" } }
        ?: refuse("no unique native message menu fragment")
    val reaction = findReactionTrayGate(fragment)
    val reactionWrite = factory.code().indices.singleOrNull { at ->
        factory.code()[at].opcode == Opcode.IPUT_BOOLEAN && factory.code()[at].field()?.toString() == reaction.toString()
    } ?: refuse("reaction tray availability is not set once by factory")
    val fragmentCode = fragment.code()
    val primaryRead = fragmentCode.indices.firstOrNull { at ->
        fragmentCode[at].field()?.toString() == factory.code()[writes.first()].field()?.toString() &&
            fragmentCode.getOrNull(at + 1)?.call()?.name == "isEmpty"
    } ?: refuse("no primary action list visibility branch")
    val floatingField = fragmentCode.drop(primaryRead + 1).take(7).firstOrNull {
        it.opcode == Opcode.IGET_BOOLEAN && it.field()?.definingClass == MODEL
    }?.field() ?: refuse("no native floating action layout flag")
    val floatingWrite = factory.code().indices.singleOrNull { at -> factory.code()[at].opcode == Opcode.IPUT_BOOLEAN &&
        factory.code()[at].field()?.toString() == floatingField.toString()
    } ?: refuse("floating action layout flag is not set once")
    val extension = classDefBy(GESTURES)
    if (extension.methods.none { it.name == "openNative" && it.types() == listOf("Ljava/lang/Object;") && it.returnType == "Z" }) refuse("native bridge absent")
    val touch = classDefBy(FRAGMENT_ACTIVITY).methods.singleOrNull {
        it.name == "dispatchTouchEvent" && it.types() == listOf("Landroid/view/MotionEvent;") && it.returnType == "Z" && it.implementation != null
    } ?: refuse("no native activity touch dispatcher")
    val actionWrites = listOf(writes[0], writes[1], writes[4])
    val liveness = RegisterLiveness.of(factory)
    val scratch = (actionWrites + reactionWrite + floatingWrite).associateWith { at ->
        val live = liveness.liveInto(at)
        (0 until minOf(16, factory.localRegisterCount())).firstOrNull { it !in live }
            ?: refuse("no free low register at native menu field write $at")
    }
    return MessageGestureSite(tap, hold, provider, data, eligible, controller, blocked, messageType, typeName, touch, factory,
        actionWrites, reactionWrite, floatingWrite, scratch)
}

/** A forward branch can loop back into setup; its destination alone does not identify the tray. */
internal fun findReactionTrayGate(fragment: Method): FieldReference {
    val flow = ControlFlow.of(fragment)
    val fragmentCode = flow.instructions
    val offsets = IntArray(fragmentCode.size)
    var units = 0
    fragmentCode.forEachIndexed { i, ins -> offsets[i] = units; units += ins.codeUnits }
    val vibrator = fragmentCode.indexOfFirst { it.string() == "vibrator" }
    if (vibrator < 0) refuse("no reaction tray vibrator setup")
    fun reachesSetup(start: Int, gate: Int): Boolean {
        val seen = BooleanArray(fragmentCode.size)
        val pending = ArrayDeque<Int>()
        pending.add(start)
        while (pending.isNotEmpty()) {
            val at = pending.removeFirst()
            if (at == gate || seen[at]) continue
            if (at == vibrator) return true
            seen[at] = true
            pending.addAll(flow.normal[at])
        }
        return false
    }
    val gates = fragmentCode.indices.mapNotNull { at ->
        val ins = fragmentCode[at]
        val field = ins.field()?.takeIf { ins.opcode == Opcode.IGET_BOOLEAN && it.definingClass == MODEL } ?: return@mapNotNull null
        val branch = fragmentCode.getOrNull(at + 1)
        if (branch?.opcode != Opcode.IF_EQZ || branch !is OffsetInstruction) return@mapNotNull null
        if ((branch as OneRegisterInstruction).registerA != (ins as TwoRegisterInstruction).registerA) return@mapNotNull null
        val target = offsets[at + 1] + branch.codeOffset
        val targetIndex = offsets.indexOf(target)
        if (at < vibrator && target > offsets[vibrator] && targetIndex >= 0 &&
            reachesSetup(at + 2, at) && !reachesSetup(targetIndex, at)) Triple(at, target, field) else null
    }
    val innermost = gates.filter { candidate -> gates.all { other ->
        candidate.first >= other.first && candidate.second <= other.second
    } }
    return innermost.singleOrNull()?.third
        ?: refuse("expected one native reaction tray control-flow gate, found ${innermost.size} among ${gates.size}")
}

internal fun BytecodePatchContext.applyMessageGestures(site: MessageGestureSite) {
    val extension = mutableClassDefBy(GESTURES)
    extension.methods.single { it.name == "textDelegate" }.addInstructions(0, """
        check-cast p0, ${site.tap.definingClass}
        iget-object p0, p0, ${site.provider}
        invoke-interface {p0}, $FUNCTION->invoke()Ljava/lang/Object;
        move-result-object p0
        check-cast p0, ${site.data}
        invoke-interface {p0}, ${site.messageType.text()}
        move-result-object p0
        if-eqz p0, :not_text
        iget-object p0, p0, ${site.typeName}
        invoke-static {p0}, $GESTURES->intercepts(Ljava/lang/String;)Z
        move-result p0
        return p0
        :not_text
        const/4 p0, 0x0
        return p0
    """)
    val old = extension.methods.single { it.name == "openNative" }
    extension.methods.remove(old)
    extension.methods.add(ImmutableMethod(old.definingClass, old.name, old.parameters, old.returnType,
        old.accessFlags, old.annotations, old.hiddenApiRestrictions, ImmutableMethodImplementation(3, emptyList(), null, null))
        .toMutable().apply { addInstructions(0, """
            check-cast p0, ${site.tap.definingClass}
            iget-object v0, p0, ${site.provider}
            invoke-interface { v0 }, $FUNCTION->invoke()Ljava/lang/Object;
            move-result-object v0
            check-cast v0, ${site.data}
            invoke-interface { v0 }, ${site.messageType.text()}
            move-result-object v1
            if-eqz v1, :unavailable
            iget-object v1, v1, ${site.typeName}
            invoke-static { v1 }, $GESTURES->intercepts(Ljava/lang/String;)Z
            move-result v1
            if-eqz v1, :unavailable
            invoke-interface { v0 }, ${site.eligible.text()}
            move-result v0
            if-eqz v0, :unavailable
            iget-object v0, p0, ${site.controller}
            invoke-virtual { v0 }, ${site.blocked.text()}
            move-result v0
            if-nez v0, :unavailable
            invoke-static {}, $GESTURES->x()F
            move-result v0
            invoke-static {}, $GESTURES->y()F
            move-result v1
            invoke-virtual { p0, v0, v1 }, ${site.hold.text()}
            const/4 v0, 0x1
            return v0
            :unavailable
            const/4 v0, 0x0
            return v0
        """) })
    mutable(site.tap).apply {
        addInstructionsWithLabels(0, """
            invoke-static/range { p0 .. p0 }, $GESTURES->tap(Ljava/lang/Object;)Z
            move-result v0
            if-eqz v0, :native_tap
            return-void
        """, ExternalLabel("native_tap", getInstruction(0)))
    }
    mutable(site.touch).addInstructions(0,
        "invoke-static/range { p1 .. p1 }, $GESTURES->observe(Landroid/view/MotionEvent;)V")
    val factory = mutable(site.factory)
    val hooks = site.actionWrites.map { it to "actions($LIST)$LIST" } +
        (site.reactionWrite to "reactions(Z)Z") + (site.floatingWrite to "floatingActions(Z)Z")
    for ((at, method) in hooks.sortedByDescending { it.first }) {
        val instruction = factory.getInstruction(at)
        val register = (instruction as TwoRegisterInstruction).registerA
        val owner = instruction.registerB
        val scratch = site.scratch.getValue(at)
        val objectResult = if (method.startsWith("actions")) "-object" else ""
        val write = if (method.startsWith("actions")) "iput-object" else "iput-boolean"
        factory.replaceInstruction(at, "$write v$scratch, v$owner, ${instruction.field()}")
        factory.addInstructionsAtControlFlowLabel(at, """
            invoke-static/range { v$register .. v$register }, $GESTURES->$method
            move-result$objectResult v$scratch
        """)
    }
    factory.code().indices.filter { factory.code()[it].opcode == Opcode.RETURN_OBJECT }.asReversed().forEach { at ->
        factory.addInstructionsAtControlFlowLabel(at, "invoke-static {}, $GESTURES->requireVisible()V")
    }
}

private fun BytecodePatchContext.mutable(method: Method): MutableMethod = mutableClassDefBy(method.definingClass).methods.single {
    it.name == method.name && it.types() == method.types() && it.returnType == method.returnType
}
private fun Method.code(): List<Instruction> = implementation?.instructions?.toList().orEmpty()
private fun MethodReference.types() = parameterTypes.map(Any::toString)
private fun Instruction.call() = (this as? ReferenceInstruction)?.reference as? MethodReference
private fun Instruction.field() = (this as? ReferenceInstruction)?.reference as? FieldReference
private fun Instruction.string() = ((this as? ReferenceInstruction)?.reference as? StringReference)?.string
private fun MethodReference.text() = "$definingClass->$name(${parameterTypes.joinToString("")})$returnType"
private fun refuse(detail: String): Nothing = throw PatchException("Message tap menus: $detail")
