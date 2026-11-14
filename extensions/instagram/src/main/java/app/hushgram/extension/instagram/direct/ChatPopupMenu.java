package app.hushgram.extension.instagram.direct;

import android.content.Context;
import android.view.View;
import app.hushgram.extension.instagram.settings.StyledPopupMenu;

/** Compatibility name; every visual and behavior lives in the shared menu. */
final class ChatPopupMenu extends StyledPopupMenu {
    ChatPopupMenu(Context context, View anchor) { super(context, anchor); }
    ChatPopupMenu(Context context, View anchor, int gravity) { super(context, anchor, gravity); }
}