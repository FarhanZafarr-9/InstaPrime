package app.morphe.patches.instagram.direct.plusmenu

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Field

/** Identify the native plus view by constructor shapes, never by obfuscated field names. */
internal fun BytecodePatchContext.plusButtonFields(builder: PlusMenuSite): List<Field> {
    fun refuse(): Nothing = throw PatchException("Chat plus menu: no unique native plus view field chain")
    fun type(field: Field): ClassDef? = runCatching { classDefBy(field.type) }.getOrNull()
    fun ClassDef.hasConstructor(test: (List<String>) -> Boolean) = methods.any {
        it.name == "<init>" && test(it.parameterTypes.map(Any::toString))
    }
    val controller = classDefBy(builder.type).fields.singleOrNull { field -> type(field)?.hasConstructor {
        it.take(4) == listOf("Landroid/app/Activity;", "Landroid/view/View;", "Landroid/widget/FrameLayout;",
            "Lcom/instagram/common/session/UserSession;")
    } == true } ?: refuse()
    val plus = type(controller)!!.fields.singleOrNull { field -> type(field)?.hasConstructor {
        it.size == 7 && it.take(2) == listOf("Landroid/app/Activity;", "Landroid/view/View;") &&
            it.takeLast(3) == listOf("Lkotlin/jvm/functions/Function0;", "Z", "Z")
    } == true } ?: refuse()
    val view = type(plus)!!.fields.singleOrNull {
        it.type == "Lcom/instagram/common/ui/colorfilter/ColorFilterAlphaImageView;"
    } ?: refuse()
    val fields = listOf(controller, plus, view)
    if (fields.any { AccessFlags.STATIC.isSet(it.accessFlags) }) refuse()
    return fields
}

internal fun BytecodePatchContext.readPlusButton(fields: List<Field>) {
    for (field in fields) {
        val owner = mutableClassDefBy(field.definingClass)
        owner.accessFlags = (owner.accessFlags and (AccessFlags.PRIVATE.value or AccessFlags.PROTECTED.value).inv()) or AccessFlags.PUBLIC.value
        owner.fields.single { it.name == field.name }.apply {
            accessFlags = (accessFlags and (AccessFlags.PRIVATE.value or AccessFlags.PROTECTED.value).inv()) or AccessFlags.PUBLIC.value
        }
    }
    val reads = fields.joinToString("\n") {
        "iget-object p0, p0, ${it.definingClass}->${it.name}:${it.type}\nif-eqz p0, :no_button"
    }
    mutableClassDefBy(PLUS_MENU).methods.single { it.name == "button" }.addInstructions(0, """
        check-cast p0, ${fields.first().definingClass}
        $reads
        return-object p0
        :no_button
        const/4 p0, 0x0
        return-object p0
    """)
}
