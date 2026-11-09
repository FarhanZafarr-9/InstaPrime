/*
 * Copyright 2026 HushGram contributors
 * https://github.com/SysAdminDoc/HushGram
 */
package app.hushgram.extension.instagram.settings;

import android.content.Context;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.TwoStatePreference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import app.hushgram.extension.shared.L10n;

/** Navigation owns placement; the category builders remain the single source of setting rows. */
final class SettingsNavigation {
    static final class Node {
        final String id;
        final Node parent;
        final PreferenceCategory category;
        final CharSequence title;
        final String icon;
        final List<Preference> rows = new ArrayList<>();
        final List<Node> children = new ArrayList<>();
        HushgramPreferenceFragment.Row opener;

        Node(String id, Node parent, PreferenceCategory category, CharSequence title, String icon) {
            this.id = id;
            this.parent = parent;
            this.category = category;
            this.title = title;
            this.icon = icon;
        }

        List<TwoStatePreference> toggles() {
            List<TwoStatePreference> toggles = new ArrayList<>();
            for (Preference row : rows) {
                if (row instanceof TwoStatePreference && row.isPersistent()
                        && app.hushgram.extension.shared.settings.Setting.getSettingFromPath(row.getKey())
                        instanceof app.hushgram.extension.shared.settings.BooleanSetting) {
                    toggles.add((TwoStatePreference) row);
                }
            }
            for (Node child : children) toggles.addAll(child.toggles());
            return toggles;
        }

        int count() {
            int count = rows.size();
            for (Node child : children) count += child.count();
            return count;
        }
    }

    final List<Node> categories = new ArrayList<>();
    private final Map<String, Node> nodes = new LinkedHashMap<>();
    private final Map<Preference, Node> destinations = new HashMap<>();

    void build(Context context, Map<PreferenceCategory, List<Preference>> source, Consumer<Node> open) {
        categories.clear();
        nodes.clear();
        destinations.clear();
        for (Map.Entry<PreferenceCategory, List<Preference>> entry : source.entrySet()) {
            PreferenceCategory category = entry.getKey();
            String icon = ((HushgramPreferenceFragment.Heading) category).icon;
            Node node = new Node(category.getKey(), null, category, category.getTitle(), icon);
            node.rows.addAll(entry.getValue());
            // Sparse patch selections should open straight into their few settings.
            int splitThreshold = icon.equals(SettingsIcons.PRIVACY) ? 10 : 16;
            if (node.rows.size() > splitThreshold) subdivide(node, icon);
            categories.add(node);
            register(context, node, open);
        }
    }

    Node find(String id) { return nodes.get(id); }
    Node destination(Preference row) { return destinations.get(row); }

    private void register(Context context, Node node, Consumer<Node> open) {
        nodes.put(node.id, node);
        HushgramPreferenceFragment.Row row = node.opener = new HushgramPreferenceFragment.Row(context);
        row.setKey(HushgramPreferenceFragment.CATEGORY_ROW_KEY + "navigate:" + node.id);
        row.setPersistent(false);
        row.setTitle(node.title);
        CharSequence detail = node.parent == null
                ? ((HushgramPreferenceFragment.Heading) node.category).intro : preview(node.rows);
        row.setSummary(L10n.f("%1$d settings · %2$s", node.count(), detail == null ? "" : detail));
        HushgramPreferenceFragment.mark(row, node.icon);
        row.setOnPreferenceClickListener(clicked -> { open.accept(node); return true; });
        NewRowDots.opens(row, node);
        for (Preference setting : node.rows) destinations.put(setting, node);
        for (Node child : node.children) register(context, child, open);
    }

    private static CharSequence preview(List<Preference> rows) {
        if (rows.isEmpty()) return "";
        CharSequence first = rows.get(0).getTitle();
        return rows.size() == 1 ? first : first + ", " + rows.get(1).getTitle();
    }

    private static Set<String> keys(String... keys) { return new HashSet<>(Arrays.asList(keys)); }

    private static void split(Node parent, String id, String title, Set<String> keys) {
        Node child = new Node(parent.id + "::" + id, parent, parent.category, title, parent.icon);
        for (Preference row : new ArrayList<>(parent.rows)) {
            if (keys.contains(row.getKey())) { child.rows.add(row); parent.rows.remove(row); }
        }
        if (!child.rows.isEmpty()) parent.children.add(child);
    }

    private static void remainder(Node parent, String title) {
        if (parent.children.isEmpty()) return;
        if (!parent.rows.isEmpty()) {
            Node other = new Node(parent.id + "::appearance", parent, parent.category, title, parent.icon);
            other.rows.addAll(parent.rows);
            parent.rows.clear();
            parent.children.add(other);
        }
        if (parent.children.size() == 1) {
            parent.rows.addAll(parent.children.get(0).rows);
            parent.children.clear();
        }
    }

    private static void subdivide(Node node, String icon) {
        if (icon.equals(SettingsIcons.FEED)) {
            split(node, "content", L10n.t("Content and suggestions"), keys(Settings.HIDE_SUGGESTED_ACCOUNTS.key, Settings.HIDE_SUGGESTED_POSTS.key, Settings.HIDE_THREADS_POSTS.key, Settings.HIDE_FEED_SURVEYS.key, Settings.HIDE_FEED_SHOPPING.key, Settings.HIDE_FEED_INTEREST.key, Settings.HIDE_CAUGHT_UP_CARD.key, Settings.HIDE_FEED_VIDEOS.key, Settings.HIDE_FEED_PHOTOS.key, Settings.HIDE_FEED_CAROUSELS.key, Settings.HIDE_FEED_LIKED.key, Settings.HIDE_HOME_FEED.key, HushgramPreferenceFragment.HIDDEN_ACCOUNTS_ROW));
            split(node, "home", L10n.t("Home and Following"), keys(Settings.START_ON_FOLLOWING.key, Settings.ONLY_FOLLOWING.key,
                    Settings.LOGO_ON_FOLLOWING.key, Settings.BACK_LEAVES_HOME.key, Settings.KEEP_HOME_PLACE.key,
                    Settings.FOLLOWING_FALLBACK.key, Settings.FOLLOWING_MIX.key));
            split(node, "interaction", L10n.t("Gestures and confirmations"), keys(Settings.STOP_SWIPE_TO_CREATE.key,
                    Settings.STOP_TAB_SWIPING.key, Settings.ASK_BEFORE_LIKE.key, Settings.ASK_BEFORE_REFRESH.key,
                    Settings.LONG_PRESS_TO_ZOOM.key, Settings.LONG_PRESS_ZOOM_STAYS_OPEN.key, Settings.LONG_PRESS_FULL_SCREEN.key));
            split(node, "video", L10n.t("Video and carousels"), keys(Settings.START_FEED_VIDEOS_WITH_SOUND.key,
                    Settings.TAP_FEED_VIDEO_TO_PAUSE.key, Settings.FEED_SEEK_BAR.key, Settings.FEED_SEEK_BUTTON.key,
                    Settings.FEED_SEEK_BAR_LOOK.key, Settings.PEEK_CAROUSEL_PAGES.key, Settings.CAROUSEL_ARROWS.key,
                    Settings.CAROUSEL_DOTS_PILL.key, Settings.CAROUSEL_PILL_LOOK.key, Settings.HIDE_FEED_AUDIO_ICON.key));
            split(node, "post-style", L10n.t("Post styling"), keys(Settings.ROUNDED_POST_CORNERS.key,
                    Settings.ROUNDED_REEL_CORNERS.key, Settings.ROUNDED_STORY_CORNERS.key, Settings.ROUNDED_CORNER_SIZE.key,
                    Settings.AMBIENT_LIGHT.key, Settings.AMBIENT_LIGHT_SPREAD.key, Settings.AMBIENT_LIGHT_SPEED.key,
                    Settings.AMBIENT_LIGHT_BLEND.key));
            remainder(node, L10n.t("Appearance"));
        }
        if (icon.equals(SettingsIcons.REELS)) {
            split(node, "content", L10n.t("Content filters"), keys(Settings.HIDE_FEED_REELS.key, Settings.HIDE_REELS_SUGGESTIONS.key,
                    Settings.HIDE_PAID_PARTNERSHIP_REELS.key, Settings.HIDE_AI_LABELED_REELS.key, Settings.HIDE_CAPTION_WORD_REELS.key,
                    Settings.HIDE_CAPTION_WORD_HOME.key, Settings.HIDE_SOUND_REELS.key, Settings.REELS_BLOCKED_WORDS.key,
                    Settings.HIDE_OTHER_LANGUAGE_REELS.key, Settings.HIDE_OTHER_LANGUAGE_HOME.key, Settings.REELS_KEPT_LANGUAGES.key,
                    Settings.HIDE_LOW_VIEW_REELS.key, Settings.MIN_REEL_VIEWS.key, Settings.MIN_REEL_LIKES.key,
                    HushgramPreferenceFragment.HIDDEN_SOUNDS_ROW));
            split(node, "playback", L10n.t("Playback and progress"), keys(Settings.KEEP_REEL_SPEED.key, Settings.REEL_SEEK_BAR.key,
                    Settings.REEL_SEEK_THUMB.key, Settings.BIG_REEL_SEEK_BAR.key, Settings.DM_REEL_SEEK_BAR.key,
                    Settings.PHOTO_REEL_SEEK_BAR.key, Settings.REEL_AUTO_ADVANCE.key, Settings.KEEP_REEL_AUTO_SCROLL.key));
            split(node, "gestures", L10n.t("Gestures and scrolling"), keys(Settings.TURN_OFF_DOUBLE_TAP_LIKE.key,
                    Settings.TURN_OFF_DOUBLE_TAP_LIKE_ON_POSTS.key, Settings.TURN_OFF_DOUBLE_TAP_LIKE_ON_REELS.key,
                    Settings.TURN_OFF_DOUBLE_TAP_LIKE_ON_COMMENTS.key, Settings.TURN_OFF_DOUBLE_TAP_LIKE_ON_MESSAGES.key,
                    Settings.CHANGE_LIKE_ANIMATION.key, Settings.LIKE_ANIMATION.key, Settings.GROW_LIKE_HEART.key,
                    Settings.STOP_REELS_SCROLLING.key, Settings.REEL_SCROLL_FROM_PROFILE.key, Settings.REEL_SCROLL_FROM_SEARCH.key,
                    Settings.REEL_SCROLL_FROM_AUDIO.key, Settings.REEL_CAP.key, Settings.REEL_CAP_COUNT.key,
                    Settings.REEL_CAP_BREAK.key, Settings.REEL_SWIPE_TO_PROFILE.key, Settings.SCROLL_BREAK.key));
            split(node, "actions", L10n.t("Downloads and actions"), keys(Settings.DOWNLOAD_REELS.key, Settings.DOWNLOAD_REEL_COVER.key, Settings.DOWNLOAD_REEL_AUDIO.key, Settings.FIND_REEL_ORIGIN.key, Settings.FIND_REEL_ORIGIN_ON_TAP.key, Settings.REEL_NOT_INTERESTED_BUTTON.key, Settings.REEL_BLOCK_BUTTON.key, Settings.DONT_SEND_REEL_WATCH_HISTORY.key));
            split(node, "player", L10n.t("Player layout"), keys(Settings.FULL_SCREEN_REELS.key, Settings.FULL_SCREEN_HIDES.key,
                    Settings.FILL_REEL_SCREEN.key, Settings.FIT_REEL_SCREEN.key, Settings.BLUR_REEL_BARS.key,
                    Settings.PINCH_ZOOM_REELS.key, Settings.KEEP_REELS_TOP_BAR.key, Settings.HIDE_REELS_TOP_BAR.key,
                    Settings.HIDE_REELS_CREATE_BUTTON.key, Settings.HIDE_REELS_YOUR_ALGORITHM.key,
                    Settings.HIDE_REELS_FRIENDS_TAB.key, Settings.SHOW_REELS_FRIENDS_TAB.key, Settings.PICK_FRIENDS_TAB.key));
            split(node, "overlays", L10n.t("Buttons and overlays"), keys(Settings.HIDE_REEL_FOLLOW_BUTTON.key,
                    Settings.HIDE_REEL_CHIPS.key, Settings.HIDE_REEL_SEARCH_PILL.key, Settings.HIDE_REEL_PRODUCT_PILLS.key,
                    Settings.HIDE_REEL_POLL_PILLS.key, Settings.HIDE_REEL_SOCIAL_FOOTER.key, Settings.HIDE_REEL_COMMENT_BAR.key,
                    Settings.HIDE_REEL_COMMENT_BAR_EVERYWHERE.key, Settings.HIDE_REEL_REPLY_BAR.key,
                    Settings.HIDE_REEL_SAVE_BUTTON.key, Settings.TEXT_BUTTON_ON_REELS.key, Settings.SPEED_BUTTON_ON_REELS.key,
                    Settings.FULL_SCREEN_BUTTON_ON_REELS.key, Settings.SHOW_REEL_POST_TIME.key,
                    Settings.REEL_POST_TIME_SECONDS.key, Settings.REEL_POST_TIME_AGO.key));
            split(node, "clear-mode", L10n.t("Clear mode and burn-in"), keys(Settings.KEEP_REEL_CLEAR_MODE.key,
                    Settings.START_REEL_CLEAR_MODE.key, Settings.QUIET_REEL_CLEAR_MODE.key, Settings.CLEAR_MODE_SEEK_BAR.key,
                    Settings.BURN_IN_GUARD.key, Settings.BURN_IN_WAIT.key, Settings.BURN_IN_STAY_DIM.key,
                    Settings.BURN_IN_LIGHT_WHEN_PAUSED.key, Settings.BURN_IN_STORIES.key));
            remainder(node, L10n.t("Appearance"));
        }
        if (icon.equals(SettingsIcons.STORIES)) {
            split(node, "suggestions", L10n.t("Suggestions and content"), keys(Settings.HIDE_SUGGESTED_STORIES.key, Settings.HIDE_STORY_REWINDS.key, Settings.HIDE_STORY_RECAPS.key, Settings.STOP_LOADING_STORIES.key, Settings.HIDE_MUSIC_CARD.key, Settings.HIDE_STORY_SUGGESTIONS.key, Settings.HIDE_ADD_YOURS_CARDS.key));
            split(node, "playback", L10n.t("Playback and gestures"), keys(Settings.STORY_SEEK_BAR.key, Settings.STORY_SEEK_BAR_LOOK.key,
                    Settings.BLOCK_STORY_AUTO_ADVANCE.key, Settings.LOOP_STORIES.key, Settings.START_STORIES_WITH_SOUND.key,
                    Settings.STOP_STORY_SWIPE_REPLY.key, Settings.KEEP_STORIES_PLAYING_ON_ZOOM.key, Settings.SMOOTH_STORY_OPEN.key));
            split(node, "privacy", L10n.t("Privacy and actions"), keys(Settings.VIEW_STORIES_ANONYMOUSLY.key, Settings.MARK_STORIES_SEEN.key, Settings.GRAY_OUT_WATCHED_STORIES.key, Settings.VIEW_LIVE_ANONYMOUSLY.key, Settings.SHOW_STORY_MENTIONS.key, Settings.DOWNLOAD_STORIES.key, Settings.UPLOAD_STORIES_AT_FULL_QUALITY.key));
            split(node, "fonts", L10n.t("Fonts and emoji"), keys(Settings.STORY_FONTS.key, Settings.STORY_EMOJI.key, HushgramPreferenceFragment.STORY_FONTS_ROW));
            split(node, "tray", L10n.t("Story tray"), keys(Settings.HIDE_STORIES_TRAY.key, Settings.KEEP_TRAY_IN_FEED.key,
                    Settings.FILTER_TRAY_BY_BADGE.key, Settings.TRAY_BADGE.key, Settings.STORY_RING.key,
                    Settings.STORY_RING_SCALE.key, Settings.STORY_RING_COLOR.key, Settings.STORY_PRIORITY.key,
                    Settings.STORY_PRIORITY_ACCOUNTS.key));
            split(node, "viewer-controls", L10n.t("Viewer controls"), keys(Settings.STORY_SPEED_BUTTON.key,
                    Settings.HIDE_STORY_REPLY_BOX.key,
                    Settings.HIDE_STORY_FOLLOW.key, Settings.SHOW_STORY_TIME.key, Settings.STORY_TIME_MODE.key,
                    Settings.DOWNLOAD_BUTTON_ON_STORIES.key, Settings.TEXT_BUTTON_ON_STORIES.key, Settings.MARK_LIVE_SEEN.key));
            remainder(node, L10n.t("Viewer appearance"));
        }
        if (icon.equals(SettingsIcons.TAB_BAR)) {
            split(node, "tabs", L10n.t("Tabs and order"), keys(Settings.HIDE_HOME_TAB.key, Settings.HIDE_REELS_TAB.key, Settings.SHOW_REELS_TAB.key, Settings.HIDE_SEARCH_TAB.key, Settings.HIDE_CREATE_TAB.key, Settings.HIDE_PROFILE_TAB.key, Settings.SHOW_MESSAGES_TAB.key, Settings.HIDE_MESSAGES_TAB.key, Settings.SHOW_ACTIVITY_TAB.key, Settings.TAB_ORDER.key, Settings.START_TAB.key, Settings.TAB_BAR_LAYOUT.key));
            split(node, "header", L10n.t("Header buttons and titles"), keys(Settings.HIDE_HOME_CREATE_BUTTON.key, Settings.HIDE_HOME_NOTIFICATIONS_BUTTON.key, Settings.SEARCH_BUTTON_ON_HOME.key, Settings.MESSAGES_BUTTON_ON_HOME.key, Settings.HIDE_HOME_MESSAGES_BUTTON.key, Settings.GHOST_BUTTON_ON_HOME.key, Settings.CENTER_HOME_LOGO.key, Settings.CENTER_PROFILE_NAME.key, Settings.CENTER_SCREEN_TITLES.key, Settings.TITLES_AT_START.key));
            split(node, "behavior", L10n.t("Scrolling and haptics"), keys(Settings.GLASS_TAB_BAR_SLIDE.key, Settings.GLASS_TAB_BAR_HAPTICS.key, Settings.GLASS_TAB_BAR_HAPTIC_STYLE.key, Settings.GLASS_TAB_BAR_HIDE_ON_SCROLL.key, Settings.GLASS_TAB_BAR_REELS_HIDE.key));
            remainder(node, L10n.t("Glass style"));
        }
        if (icon.equals(SettingsIcons.DOWNLOADS)) {
            split(node, "media", L10n.t("Media and download actions"), keys(Settings.DOWNLOAD_VIDEOS.key, Settings.DOWNLOAD_FEED_COVER.key, Settings.DOWNLOAD_PHOTOS.key, Settings.POST_DETAILS.key, Settings.DOWNLOAD_CHOOSE_PAGES.key, Settings.FEED_DOWNLOAD_BUTTON.key, Settings.FEED_DOWNLOAD_ALL_PAGES.key, Settings.HOLD_TO_DOWNLOAD.key, Settings.HOLD_FINGERS.key, Settings.HOLD_TIME.key, Settings.DOWNLOAD_VOICE_MESSAGES.key, Settings.OPEN_IN_PLAYER.key, Settings.SEND_DOWNLOADS_TO_APP.key));
            split(node, "profiles", L10n.t("Profile actions"), keys(Settings.SAVE_PROFILE_PICTURES.key, Settings.VIEW_PROFILE_PICTURES.key, Settings.COPY_PROFILE_TEXT.key, Settings.PROFILE_EXTRAS_BUTTON.key));
            split(node, "folders", L10n.t("Folders and file names"), keys(Settings.SAVE_FOLDER.key,
                    Settings.SAVE_FOLDER_PER_ACCOUNT.key, Settings.SAVE_NAME_BY_POST.key, Settings.SAVE_PHOTO_METADATA.key,
                    Settings.FILENAME_TEMPLATE.key, Settings.SAVE_TREE.key, Settings.SAVE_CHAT_MEDIA_TO_TREE.key,
                    HushgramPreferenceFragment.GALLERY_FOLDERS));
            remainder(node, L10n.t("Quality and saving"));
        }
        if (icon.equals(SettingsIcons.CHATS)) {
            split(node, "inbox", L10n.t("Inbox appearance"), keys(Settings.HIDE_NOTES_ROW.key, Settings.HIDE_NOTES_MAP.key,
                    Settings.HIDE_INBOX_SEARCH.key, Settings.HIDE_INBOX_SUGGESTIONS.key, Settings.HIDE_INSTANTS.key,
                    Settings.FULL_SCREEN_MESSAGES.key, HushgramPreferenceFragment.PINNED_CHATS_ROW));
            split(node, "controls", L10n.t("Chat and call controls"), keys(Settings.KEEP_IN_CHAT.key, Settings.GLASS_CHAT.key,
                    Settings.ASK_BEFORE_CALL.key, Settings.HIDE_CALL_BUTTONS.key, Settings.HIDE_VIDEO_CALL_BUTTON.key,
                    Settings.HIDE_AUDIO_CALL_BUTTON.key, Settings.HIDE_BLEND_BUTTON.key));
            split(node, "security", L10n.t("App and chat locks"), keys(Settings.LOCK_MESSAGES.key, Settings.LOCK_APP.key, Settings.DOUBLE_TAP_HIDDEN_CHATS.key, Settings.LOCK_AGAIN.key, Settings.UNLOCK_WITH.key, HushgramPreferenceFragment.CHANGE_PIN_ROW, HushgramPreferenceFragment.LOCKED_CHATS_ROW, HushgramPreferenceFragment.HIDDEN_CHATS_ROW));
            split(node, "composer", L10n.t("Composer and More menu"), keys(Settings.HIDDEN_MESSAGE_BOX_BUTTONS.key,
                    Settings.HIDE_PLUS_SAVED.key, Settings.HIDE_PLUS_AI_IMAGES.key, Settings.HIDE_PLUS_DRAW.key,
                    Settings.HIDE_PLUS_META_AI.key, Settings.HIDE_PLUS_LOCATION.key, Settings.MOVE_TO_PLUS_MENU.key));
            split(node, "media-history", L10n.t("Shared media and history"), keys(Settings.SWIPE_CHAT_MEDIA.key, Settings.SWIPE_CHAT_REELS.key,
                    Settings.STOP_AFTER_CHAT_REELS.key, Settings.ONLY_RECEIVED_CHAT_REELS.key, Settings.MESSAGES_AFTER_CHAT_REELS.key,
                    Settings.JUMP_IN_CHAT.key, Settings.EXPORT_CHAT.key, Settings.MARK_VIEW_ONCE.key));
            remainder(node, L10n.t("Privacy and receipts"));
        }
        if (icon.equals(SettingsIcons.LAYOUT)) {
            split(node, "accent", L10n.t("Accent colors"), keys(Settings.ACCENT_COLOR.key, Settings.ACCENT_HUE.key,
                    Settings.ACCENT_BADGES.key, Settings.ACCENT_ICONS.key, Settings.ACCENT_RINGS.key,
                    Settings.ACCENT_BACKGROUNDS.key, LayoutCategory.PURE_BLACK_HINT_ROW));
            split(node, "fonts", L10n.t("Fonts and emoji"), keys(Settings.FONT.key, Settings.NOTO_EMOJI.key, Settings.EMOJI_FONT.key));
            remainder(node, L10n.t("App layout and branding"));
        }
        if (icon.equals(SettingsIcons.PLAYBACK)) {
            split(node, "quality", L10n.t("Quality and data"), keys(Settings.DEFAULT_PLAYBACK_QUALITY.key,
                    Settings.PLAYBACK_QUALITY.key, Settings.QUALITY_TAB_TARGET.key, Settings.DATA_SAVER.key,
                    Settings.DATA_SAVER_MOBILE_DATA_ONLY.key, Settings.TURN_OFF_HDR_BOOSTS.key));
            remainder(node, L10n.t("Playback behavior"));
        }
        if (icon.equals(SettingsIcons.PRIVACY)) {
            split(node, "ads-tracking", L10n.t("Ads and link tracking"), keys(Settings.HIDE_ADS.key,
                    Settings.SKIP_AD_DATA_REMINDER.key, Settings.SANITIZE_SHARING_LINKS.key, Settings.SHARING_DOMAIN.key,
                    Settings.OPEN_LINKS_EXTERNALLY.key));
            remainder(node, L10n.t("Privacy and connection"));
        }
        if (icon.equals(SettingsIcons.PERSON)) {
            split(node, "relationships", L10n.t("Followers and relationships"), keys(Settings.SHOW_FRIENDSHIP_STATUS.key, Settings.FRIENDSHIP_STATUS_CHIP.key, Settings.FOLLOW_LABEL_PLACE.key, Settings.MARK_FOLLOWING_LIST.key, Settings.FOLLOWING_NOT_BACK_FIRST.key, Settings.TRACK_UNFOLLOWERS.key, HushgramPreferenceFragment.UNFOLLOWERS_ROW, HushgramPreferenceFragment.CLEAR_UNFOLLOWERS));
            split(node, "suggestions", L10n.t("Suggestions and prompts"), keys(Settings.HIDE_PROFILE_SUGGESTIONS.key, Settings.HIDE_FOLLOW_LIST_SUGGESTIONS.key, Settings.HIDE_PROFILE_COMPLETION.key));
            remainder(node, L10n.t("Appearance"));
        }
    }
}
