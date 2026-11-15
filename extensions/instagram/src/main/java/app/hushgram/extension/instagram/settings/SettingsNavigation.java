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
import app.hushgram.extension.shared.settings.BooleanSetting;
import app.hushgram.extension.shared.settings.Setting;

/** Navigation owns placement; the category builders remain the single source of setting rows. */
final class SettingsNavigation {
    private static final Set<Preference> sectionStarts = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    static boolean beginsSection(Preference row) { return sectionStarts.contains(row); }
    static final class Node {
        final String id;
        final Node parent;
        final PreferenceCategory category;
        final CharSequence title;
        final String icon;
        final List<Preference> rows = new ArrayList<>();
        final List<Node> children = new ArrayList<>();
        final Map<Preference, Preference> sectionHeadings = new HashMap<>();
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
                if (savedToggle(row) != null && !Settings.SHOW_ENABLE_ALL.key.equals(row.getKey())) {
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
    final List<Preference> overviewRows = new ArrayList<>();
    private final Map<String, Node> nodes = new LinkedHashMap<>();
    private final Map<Preference, Node> destinations = new HashMap<>();

    static BooleanSetting savedToggle(Preference row) {
        if (!(row instanceof TwoStatePreference) || !row.isPersistent()) return null;
        Setting<?> setting = Setting.getSettingFromPath(row.getKey());
        return setting instanceof BooleanSetting ? (BooleanSetting) setting : null;
    }

    void build(Context context, Map<PreferenceCategory, List<Preference>> source,
               Consumer<Node> open, Consumer<Node> overview) {
        categories.clear();
        overviewRows.clear();
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
            register(context, node, open, overview);
        }
        List<Node> remaining = new ArrayList<>(categories);
        takeOverviewGroup(context, remaining, "privacy", L10n.t("Privacy and alerts"),
                SettingsIcons.PRIVACY, SettingsIcons.AI, SettingsIcons.NOTIFICATIONS);
        takeOverviewGroup(context, remaining, "content", L10n.t("Content"),
                SettingsIcons.FEED, SettingsIcons.EXPLORE, SettingsIcons.REELS, SettingsIcons.STORIES, SettingsIcons.UPDATES);
        takeOverviewGroup(context, remaining, "people", L10n.t("People and chats"),
                SettingsIcons.CHATS, SettingsIcons.COMMENTS, SettingsIcons.PERSON);
        takeOverviewGroup(context, remaining, "experience", L10n.t("App experience"),
                SettingsIcons.LAYOUT, SettingsIcons.TAB_BAR, SettingsIcons.PLAYBACK, SettingsIcons.NETWORK);
        takeOverviewGroup(context, remaining, "tools", L10n.t("App tools"),
                SettingsIcons.DOWNLOADS, SettingsIcons.STORAGE, SettingsIcons.BATTERY,
                SettingsIcons.SETTINGS, SettingsIcons.TOOLS);
        addOverviewGroup(context, "support", L10n.t("Support and diagnostics"), remaining);
    }

    private void takeOverviewGroup(Context context, List<Node> remaining, String id, String title, String... icons) {
        List<Node> group = new ArrayList<>();
        for (String icon : icons) {
            for (Node node : remaining) {
                if (icon.equals(node.icon) && (!icon.equals(SettingsIcons.UPDATES)
                        || ViewingHistoryCategory.KEY.equals(node.id))) group.add(node);
            }
        }
        remaining.removeAll(group);
        addOverviewGroup(context, id, title, group);
    }

    private void addOverviewGroup(Context context, String id, String title, List<Node> group) {
        if (group.isEmpty()) return;
        overviewRows.add(plainHeading(context, "hushgram_overview_heading_" + id, title));
        for (Node node : group) overviewRows.add(node.opener);
    }

    private static Preference plainHeading(Context context, String key, String title) {
        HushgramPreferenceFragment.Heading heading = new HushgramPreferenceFragment.Heading(context);
        heading.setKey(key);
        heading.setTitle(title);
        return heading;
    }

    private static void groupPage(Context context, Node node, String firstTitle, String secondTitle,
                                  Set<String> secondKeys) {
        List<Preference> second = new ArrayList<>();
        for (Preference row : node.rows) {
            if (secondKeys.contains(row.getKey())) second.add(row);
        }
        if (second.isEmpty() || second.size() == node.rows.size()) return;
        node.rows.removeAll(second);
        node.sectionHeadings.put(node.rows.get(0), plainHeading(context, node.id + "::heading-first", firstTitle));
        node.sectionHeadings.put(second.get(0), plainHeading(context, node.id + "::heading-second", secondTitle));
        node.rows.addAll(second);
    }

    Node find(String id) { return nodes.get(id); }
    Node destination(Preference row) { return destinations.get(row); }

    private void register(Context context, Node node, Consumer<Node> open, Consumer<Node> overview) {
        if (node.icon.equals(SettingsIcons.LAYOUT) && node.id.endsWith("::appearance")) {
            groupPage(context, node, L10n.t("App layout"), L10n.t("Branding"), keys(
                    Settings.LAUNCH_SCREEN_ACCENT.key, Settings.LAUNCH_SCREEN.key, Settings.HEADER_TEXT.key, Settings.APP_ICON.key,
                    HushgramPreferenceFragment.HEADER_PICTURE_KEY, HushgramPreferenceFragment.REMOVE_HEADER_PICTURE_KEY,
                    HushgramPreferenceFragment.CUSTOM_ICON_KEY));
        }
        if (node.icon.equals(SettingsIcons.COMMENTS)) {
            groupPage(context, node, L10n.t("Copy and media"), L10n.t("Display and input"),
                    keys(Settings.HIDE_COMMENTS.key, Settings.HIDE_COMMENT_EMOJI_ROW.key, Settings.COMMENT_KEYBOARD_DOWN.key));
        }
        if (node.icon.equals(SettingsIcons.REELS) && node.id.endsWith("::content")) {
            groupPage(context, node, L10n.t("Recommendations and thresholds"), L10n.t("Words, languages, and sounds"),
                    keys(Settings.HIDE_CAPTION_WORD_REELS.key, Settings.HIDE_CAPTION_WORD_HOME.key,
                            Settings.REELS_BLOCKED_WORDS.key, Settings.HIDE_OTHER_LANGUAGE_REELS.key,
                            Settings.HIDE_OTHER_LANGUAGE_HOME.key, Settings.REELS_KEPT_LANGUAGES.key,
                            Settings.HIDE_SOUND_REELS.key, HushgramPreferenceFragment.HIDDEN_SOUNDS_ROW));
        }
        untitledSections(node);
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
        row.overview = () -> overview.accept(node);
        NewRowDots.opens(row, node);
        for (Preference setting : node.rows) destinations.put(setting, node);
        for (Node child : node.children) register(context, child, open, overview);
        if (node.icon.equals(SettingsIcons.FEED)) {
            childSections(node, keys("filters", "content", "home"),
                    keys("video", "video-controls", "interaction"),
                    keys("carousels", "post-actions", "post-style", "appearance"));
        } else if (node.icon.equals(SettingsIcons.CHATS)) {
            childSections(node, keys("inbox", "controls", "composer", "media-history"),
                    keys("presence", "calls", "security", "appearance"));
        } else if (node.icon.equals(SettingsIcons.PERSON)) {
            childSections(node, keys("banners", "tabs-grid", "appearance"),
                    keys("relationships", "suggestions"), keys("profile-history", "saved-profiles"));
        } else if (node.icon.equals(SettingsIcons.REELS)) {
            childSections(node, keys("content", "words-sounds", "actions"),
                    keys("playback", "skip-speed", "likes", "gestures"),
                    keys("reels-header", "player", "buttons", "overlays"),
                    keys("clear-mode", "appearance"));
        } else if (node.icon.equals(SettingsIcons.STORIES)) {
            childSections(node, keys("tray", "suggestions"),
                    keys("playback", "story-buttons", "viewer-controls"),
                    keys("fonts", "appearance"), keys("uploads", "privacy"));
        }
    }

    /** Group navigation rows too, using the same untitled card gaps as settings rows. */
    @SafeVarargs
    private static void childSections(Node node, Set<String>... groups) {
        if (node.children.isEmpty()) return;
        List<Node> remaining = new ArrayList<>(node.children);
        List<Node> ordered = new ArrayList<>();
        for (Node child : remaining) sectionStarts.remove(child.opener);
        for (Set<String> group : groups) {
            List<Node> band = new ArrayList<>();
            for (Node child : remaining) {
                if (group.contains(child.id.substring(child.id.lastIndexOf("::") + 2))) band.add(child);
            }
            if (band.isEmpty()) continue;
            if (!ordered.isEmpty()) sectionStarts.add(band.get(0).opener);
            ordered.addAll(band);
            remaining.removeAll(band);
        }
        if (!remaining.isEmpty()) {
            if (!ordered.isEmpty()) sectionStarts.add(remaining.get(0).opener);
            ordered.addAll(remaining);
        }
        node.children.clear();
        node.children.addAll(ordered);
    }

    static CharSequence preview(List<Preference> rows) {
        if (rows.isEmpty()) return "";
        CharSequence first = rows.get(0).getTitle();
        return rows.size() == 1 ? first : first + ", " + rows.get(1).getTitle();
    }

    private static Set<String> keys(String... keys) { return new HashSet<>(Arrays.asList(keys)); }

    /** Real rows form visual sections: no blank preferences, extra focus stops, or count changes. */
    @SafeVarargs
    private static void bands(Node node, Set<String>... groups) {
        List<Preference> remaining = new ArrayList<>(node.rows);
        List<Preference> ordered = new ArrayList<>();
        for (Set<String> group : groups) {
            List<Preference> band = new ArrayList<>();
            for (Preference row : remaining) if (group.contains(row.getKey())) band.add(row);
            if (band.isEmpty()) continue;
            if (!ordered.isEmpty()) sectionStarts.add(band.get(0));
            ordered.addAll(band);
            remaining.removeAll(band);
        }
        if (!remaining.isEmpty()) {
            if (!ordered.isEmpty()) sectionStarts.add(remaining.get(0));
            ordered.addAll(remaining);
        }
        node.rows.clear();
        node.rows.addAll(ordered);
    }

    private static void untitledSections(Node node) {
        sectionStarts.removeAll(node.rows);
        if (!node.children.isEmpty() || node.rows.size() < 6) return;
        String icon = node.icon, id = node.id;
        if (icon.equals(SettingsIcons.NOTIFICATIONS)) {
            bands(node, keys(Settings.GROUP_NOTIFICATIONS.key, Settings.GROUP_NOTIFICATIONS_BY_TYPE.key,
                            Settings.MUTED_NOTIFICATION_CHANNELS.key),
                    keys(Settings.HIDE_FOLLOW_BACK_SUGGESTIONS.key, Settings.HIDE_NOTIFICATION_SUGGESTIONS.key,
                            Settings.HIDE_NOTIFICATION_CHANNEL_INVITES.key),
                    keys(Settings.HIDE_NOTIFICATION_FEATURED.key, Settings.HIDE_FEATURED_DOT.key));
        } else if (icon.equals(SettingsIcons.AI)) {
            bands(node, keys(Settings.HIDE_META_AI_SEARCH.key, Settings.HIDE_META_AI_POSTS.key, Settings.HIDE_META_AI_SHARE_TARGET.key),
                    keys(Settings.HIDE_ABOUT_THIS_REEL.key, Settings.HIDE_ASK_META_AI.key, Settings.HIDE_META_AI_BLEND.key),
                    keys(Settings.HIDE_META_AI_PROFILE_BANNER.key, Settings.HIDE_META_AI_PROFILE_BUTTON.key));
        } else if (icon.equals(SettingsIcons.SETTINGS)) {
            bands(node, keys(Settings.NAVIGATION_SETTINGS_TARGET.key, Settings.PROFILE_MENU_LONG_PRESS.key,
                    Settings.SETTINGS_SHORTCUT.key, Settings.SETTINGS_LOGO_PRESS.key, Settings.HIDE_MENU_ROW.key));
        } else if (icon.equals(SettingsIcons.FEED) && id.endsWith("::appearance")) {
            bands(node, keys(Settings.FULL_RESOLUTION_PHOTOS.key, Settings.ASK_FOR_LARGER_PHOTOS.key, Settings.FULL_SIZE_COVERS.key),
                    keys(Settings.SHOW_POST_TIME.key, Settings.SHOW_HIDDEN_LIKE_COUNTS.key, Settings.HIDE_CAPTIONS.key,
                            Settings.SKIP_COLLECTIONS_TRAY.key, Settings.HIDE_ACTIVITY_BUBBLES.key),
                    keys(Settings.FULL_SCREEN_HOME.key, Settings.KEEP_HOME_TOP_BAR.key, Settings.SEE_THROUGH_HOME_TOP_BAR.key));
        } else if (icon.equals(SettingsIcons.FEED) && id.endsWith("::home")) {
            bands(node, keys(Settings.START_ON_FOLLOWING.key, Settings.ONLY_FOLLOWING.key, Settings.LOGO_ON_FOLLOWING.key,
                            Settings.FOLLOWING_FALLBACK.key, Settings.FOLLOWING_MIX.key),
                    keys(Settings.FAVORITES_TAB_BAR.key, Settings.FAVORITES_TAB_TARGET.key),
                    keys(Settings.BACK_LEAVES_HOME.key, Settings.KEEP_HOME_PLACE.key));
        } else if (icon.equals(SettingsIcons.FEED) && id.endsWith("::post-style")) {
            bands(node, keys(Settings.ROUNDED_POST_CORNERS.key, Settings.ROUNDED_REEL_CORNERS.key,
                    Settings.ROUNDED_STORY_CORNERS.key, Settings.ROUNDED_CORNER_SIZE.key));
        } else if (icon.equals(SettingsIcons.FEED) && id.endsWith("::interaction")) {
            bands(node, keys(Settings.STOP_SWIPE_TO_CREATE.key, Settings.HOME_SWIPE_OPENS.key, Settings.STOP_TAB_SWIPING.key),
                    keys(Settings.ASK_BEFORE_LIKE.key, Settings.ASK_BEFORE_REFRESH.key));
        } else if (icon.equals(SettingsIcons.FEED) && id.endsWith("::video")) {
            bands(node, keys(Settings.START_FEED_VIDEOS_WITH_SOUND.key, Settings.TAP_FEED_VIDEO_TO_PAUSE.key,
                    Settings.HIDE_FEED_AUDIO_ICON.key));
        } else if (icon.equals(SettingsIcons.FEED) && id.endsWith("::video-controls")) {
            bands(node, keys(Settings.HOLD_FEED_VIDEO_FAST.key, Settings.FEED_DOUBLE_TAP_SKIP.key, Settings.FEED_DOUBLE_TAP_SKIP_SECONDS.key),
                    keys(Settings.FEED_SEEK_BAR_ON_EDGE.key, Settings.SEEK_BAR_SHAPE.key, Settings.SEEK_PREVIEW.key));
        } else if (icon.equals(SettingsIcons.CHATS) && id.endsWith("::inbox")) {
            bands(node, keys(Settings.HIDE_NOTES_ROW.key, Settings.SPLIT_NOTES_ROW.key, Settings.HIDE_NOTES_MAP.key),
                    keys(Settings.HIDE_INBOX_SEARCH.key, Settings.HIDE_INBOX_SUGGESTIONS.key));
        } else if (icon.equals(SettingsIcons.CHATS) && id.endsWith("::controls")) {
            bands(node, keys(Settings.MESSAGE_TAP_ACTIONS.key, Settings.MESSAGE_TAP_REACTIONS.key,
                            Settings.HIDE_MESSAGE_ACTIONS.key, Settings.MORE_MESSAGE_ACTIONS.key),
                    keys(Settings.KEEP_IN_CHAT.key, Settings.HIDE_KINDNESS_REMINDER.key, Settings.GLASS_CHAT.key,
                            Settings.HIDE_BLEND_BUTTON.key));
        } else if (icon.equals(SettingsIcons.PERSON) && id.endsWith("::relationships")) {
            bands(node, keys(Settings.SHOW_FRIENDSHIP_STATUS.key, Settings.FRIENDSHIP_STATUS_CHIP.key, Settings.FOLLOW_LABEL_PLACE.key),
                    keys(Settings.ASK_BEFORE_FOLLOW.key, Settings.MARK_FOLLOWING_LIST.key, Settings.FOLLOW_MARK_ON_PICTURE.key,
                            Settings.FOLLOWING_NOT_BACK_FIRST.key));
        } else if (icon.equals(SettingsIcons.REELS) && id.endsWith("::likes")) {
            bands(node, keys(Settings.TURN_OFF_DOUBLE_TAP_LIKE.key, Settings.TURN_OFF_DOUBLE_TAP_LIKE_ON_POSTS.key,
                    Settings.TURN_OFF_DOUBLE_TAP_LIKE_ON_REELS.key, Settings.TURN_OFF_DOUBLE_TAP_LIKE_ON_COMMENTS.key,
                    Settings.TURN_OFF_DOUBLE_TAP_LIKE_ON_MESSAGES.key));
        } else if (icon.equals(SettingsIcons.REELS) && id.endsWith("::gestures")) {
            bands(node, keys(Settings.STOP_REELS_SCROLLING.key, Settings.REEL_SCROLL_FROM_PROFILE.key,
                    Settings.REEL_SCROLL_FROM_SEARCH.key, Settings.REEL_SCROLL_FROM_AUDIO.key, Settings.REEL_SWIPE_TO_PROFILE.key));
        } else if (icon.equals(SettingsIcons.REELS) && id.endsWith("::overlays")) {
            bands(node, keys(Settings.HIDE_REEL_CHIPS.key, Settings.HIDE_REEL_SEARCH_PILL.key,
                            Settings.HIDE_REEL_PRODUCT_PILLS.key, Settings.HIDE_REEL_POLL_PILLS.key),
                    keys(Settings.HIDE_REEL_SOCIAL_FOOTER.key, Settings.HIDE_REEL_COMMENT_BAR.key,
                            Settings.HIDE_REEL_COMMENT_BAR_EVERYWHERE.key, Settings.KEEP_REEL_COMMENT_BAR_SPACE.key,
                            Settings.HIDE_REEL_REPLY_BAR.key));
        } else if (icon.equals(SettingsIcons.REELS) && id.endsWith("::buttons")) {
            bands(node, keys(Settings.HIDE_REEL_FOLLOW_BUTTON.key, Settings.HIDE_REEL_SAVE_BUTTON.key,
                            Settings.HIDE_REEL_LIKE_BUTTON.key, Settings.HIDE_REEL_COMMENT_BUTTON.key),
                    keys(Settings.MUTE_BUTTON_ON_REELS.key, Settings.MUTE_BUTTON_IN_CLEAR_MODE.key));
        } else {
            // Preserve source order and existing headings. Break only at a feature boundary,
            // and never between a control and any of its dependent controls further down the page.
            int start = 0;
            for (int at = 1; at < node.rows.size(); at++) {
                if (node.sectionHeadings.containsKey(node.rows.get(at))) { start = at; continue; }
                if (Setting.getSettingFromPath(node.rows.get(at).getKey()) == null) continue;
                if (at - start < 3 || sameFeature(node.rows.get(at - 1), node.rows.get(at))) continue;
                if (dependencyCrosses(node.rows, at)) continue;
                sectionStarts.add(node.rows.get(at));
                start = at;
            }
        }
        // Explicit bands respect dependencies too, including sparse patch selections.
        for (int at = 1; at < node.rows.size(); at++) {
            if (dependencyCrosses(node.rows, at)) sectionStarts.remove(node.rows.get(at));
        }
    }

    private static boolean sameFeature(Preference left, Preference right) {
        for (PatchFamily family : PatchFamily.values()) {
            boolean a = false, b = false;
            for (Setting<?> setting : family.switches) {
                a |= setting.key.equals(left.getKey()); b |= setting.key.equals(right.getKey());
            }
            for (Setting<?> setting : family.choices()) {
                a |= setting.key.equals(left.getKey()); b |= setting.key.equals(right.getKey());
            }
            if (a && b) return true;
        }
        return false;
    }

    private static boolean dependencyCrosses(List<Preference> rows, int boundary) {
        Map<String, Integer> positions = new HashMap<>();
        for (int at = 0; at < rows.size(); at++) positions.put(rows.get(at).getKey(), at);
        for (int at = 0; at < rows.size(); at++) {
            Setting<?> setting = Setting.getSettingFromPath(rows.get(at).getKey());
            if (setting == null) continue;
            for (Setting<?> parent : setting.getParentSettings()) {
                Integer parentAt = positions.get(parent.key);
                if (parentAt != null && (at < boundary) != (parentAt < boundary)) return true;
            }
        }
        return false;
    }

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
            split(node, "filters", L10n.t("Post and account filters"), keys(Settings.HIDE_FEED_VIDEOS.key,
                    Settings.HIDE_FEED_PHOTOS.key, Settings.HIDE_FEED_CAROUSELS.key, Settings.HIDE_FEED_LIKED.key,
                    Settings.HIDE_HOME_FEED.key, Settings.HIDE_FEED_PROFESSIONAL.key, Settings.HIDDEN_IN_FAVORITES.key,
                    HushgramPreferenceFragment.HIDDEN_ACCOUNTS_ROW));
            split(node, "carousels", L10n.t("Carousels"), keys(Settings.PEEK_CAROUSEL_PAGES.key,
                    Settings.CAROUSEL_ARROWS.key, Settings.CAROUSEL_DOTS_PILL.key, Settings.CAROUSEL_PILL_LOOK.key,
                    Settings.CAROUSEL_HIDE_NUMBER.key));
            split(node, "video-controls", L10n.t("Seeking and replay"), keys(Settings.HOLD_FEED_VIDEO_FAST.key,
                    Settings.FEED_DOUBLE_TAP_SKIP.key, Settings.FEED_DOUBLE_TAP_SKIP_SECONDS.key,
                    Settings.FEED_SEEK_BAR_ON_EDGE.key, Settings.SEEK_BAR_SHAPE.key, Settings.SEEK_PREVIEW.key,
                    Settings.WATCH_AGAIN_IN_FEED.key, Settings.HIDE_WATCH_MORE_REELS.key));
            split(node, "post-actions", L10n.t("Post buttons and labels"), keys(Settings.HIDE_FEED_FOLLOW.key,
                    Settings.HIDE_FEED_SUBSCRIBE.key, Settings.HIDE_EDITS_LABEL.key, Settings.SAVE_IN_POST_MENU.key,
                    Settings.HIDE_FEED_SAVE_BUTTON.key, Settings.HIDE_FEED_LIKE_BUTTON.key));
            split(node, "content", L10n.t("Content and suggestions"), keys(Settings.HIDE_FEED_STORY_ROWS.key, Settings.HIDE_CHANNEL_JOIN_BAR.key, Settings.HIDE_SUGGESTED_ACCOUNTS.key, Settings.HIDE_SUGGESTED_POSTS.key, Settings.HIDE_THREADS_POSTS.key, Settings.HIDE_FEED_SURVEYS.key, Settings.HIDE_FEED_SHOPPING.key, Settings.HIDE_FEED_INTEREST.key, Settings.HIDE_CAUGHT_UP_CARD.key, Settings.HIDE_FEED_VIDEOS.key, Settings.HIDE_FEED_PHOTOS.key, Settings.HIDE_FEED_CAROUSELS.key, Settings.HIDE_FEED_LIKED.key, Settings.HIDE_HOME_FEED.key, HushgramPreferenceFragment.HIDDEN_ACCOUNTS_ROW));
            split(node, "home", L10n.t("Home and Following"), keys(Settings.START_ON_FOLLOWING.key, Settings.ONLY_FOLLOWING.key,
                    Settings.LOGO_ON_FOLLOWING.key, Settings.BACK_LEAVES_HOME.key, Settings.KEEP_HOME_PLACE.key,
                    Settings.FOLLOWING_FALLBACK.key, Settings.FOLLOWING_MIX.key, Settings.FAVORITES_TAB_BAR.key, Settings.FAVORITES_TAB_TARGET.key));
            split(node, "interaction", L10n.t("Gestures and confirmations"), keys(Settings.STOP_SWIPE_TO_CREATE.key,
                    Settings.STOP_TAB_SWIPING.key, Settings.HOME_SWIPE_OPENS.key, Settings.ASK_BEFORE_LIKE.key, Settings.ASK_BEFORE_REFRESH.key,
                    Settings.LONG_PRESS_TO_ZOOM.key, Settings.LONG_PRESS_ZOOM_STAYS_OPEN.key, Settings.LONG_PRESS_FULL_SCREEN.key));
            split(node, "video", L10n.t("Video playback"), keys(Settings.START_FEED_VIDEOS_WITH_SOUND.key,
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
            split(node, "words-sounds", L10n.t("Words, languages, and sounds"), keys(Settings.HIDE_CAPTION_WORD_REELS.key,
                    Settings.HIDE_CAPTION_WORD_HOME.key, Settings.REELS_BLOCKED_WORDS.key,
                    Settings.HIDE_OTHER_LANGUAGE_REELS.key, Settings.HIDE_OTHER_LANGUAGE_HOME.key,
                    Settings.REELS_KEPT_LANGUAGES.key, Settings.HIDE_SOUND_REELS.key,
                    HushgramPreferenceFragment.HIDDEN_SOUNDS_ROW));
            split(node, "reels-header", L10n.t("Reels header and tabs"), keys(Settings.KEEP_REELS_TOP_BAR.key,
                    Settings.HIDE_REELS_TOP_BAR.key, Settings.HIDE_REELS_CREATE_BUTTON.key,
                    Settings.HIDE_SAVED_REELS_CREATE_BUTTON.key, Settings.HIDE_REELS_YOUR_ALGORITHM.key,
                    Settings.HIDE_REELS_FRIENDS_TAB.key, Settings.SHOW_REELS_FRIENDS_TAB.key, Settings.PICK_FRIENDS_TAB.key));
            split(node, "likes", L10n.t("Likes and double taps"), keys(Settings.TURN_OFF_DOUBLE_TAP_LIKE.key,
                    Settings.TURN_OFF_DOUBLE_TAP_LIKE_ON_POSTS.key, Settings.TURN_OFF_DOUBLE_TAP_LIKE_ON_REELS.key,
                    Settings.TURN_OFF_DOUBLE_TAP_LIKE_ON_COMMENTS.key, Settings.TURN_OFF_DOUBLE_TAP_LIKE_ON_MESSAGES.key,
                    Settings.CHANGE_LIKE_ANIMATION.key, Settings.LIKE_ANIMATION.key, Settings.GROW_LIKE_HEART.key,
                    Settings.HEART_COUNT.key));
            split(node, "skip-speed", L10n.t("Skipping and speed"), keys(Settings.DOUBLE_TAP_SKIP_REELS.key,
                    Settings.DOUBLE_TAP_SKIP_REELS_SECONDS.key, Settings.HIDE_REEL_2X_POPUP.key,
                    Settings.SPEED_BUTTON_ON_REELS.key, Settings.SPEED_IN_REEL_MENU.key, Settings.KEEP_REEL_SPEED.key));
            split(node, "content", L10n.t("Content filters"), keys(Settings.HIDE_FEED_REELS.key, Settings.HIDE_REELS_SUGGESTIONS.key,
                    Settings.HIDE_LIVE_REELS.key, Settings.HIDE_PAID_PARTNERSHIP_REELS.key, Settings.HIDE_AI_LABELED_REELS.key, Settings.HIDE_CAPTION_WORD_REELS.key,
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
                    Settings.REEL_CAP_BREAK.key, Settings.REEL_SWIPE_TO_PROFILE.key, Settings.SCROLL_BREAK.key, Settings.SCROLL_BREAK_LENGTH.key));
            split(node, "actions", L10n.t("Downloads and actions"), keys(Settings.REEL_DOWNLOAD_BUTTON_ASKS.key, Settings.OPEN_REEL_LINKS_FIRST.key, Settings.DOWNLOAD_REELS.key, Settings.DOWNLOAD_REEL_COVER.key, Settings.DOWNLOAD_REEL_AUDIO.key, Settings.FIND_REEL_ORIGIN.key, Settings.FIND_REEL_ORIGIN_ON_TAP.key, Settings.REEL_NOT_INTERESTED_BUTTON.key, Settings.REEL_BLOCK_BUTTON.key, Settings.DONT_SEND_REEL_WATCH_HISTORY.key));
            split(node, "player", L10n.t("Player layout"), keys(Settings.FULL_SCREEN_REELS.key, Settings.FULL_SCREEN_HIDES.key,
                    Settings.FILL_REEL_SCREEN.key, Settings.FIT_REEL_SCREEN.key, Settings.BLUR_REEL_BARS.key,
                    Settings.PINCH_ZOOM_REELS.key, Settings.KEEP_REELS_TOP_BAR.key, Settings.HIDE_REELS_TOP_BAR.key,
                    Settings.HIDE_REELS_CREATE_BUTTON.key, Settings.HIDE_SAVED_REELS_CREATE_BUTTON.key, Settings.REEL_PROFILE_TAB_BAR.key, Settings.HIDE_REELS_YOUR_ALGORITHM.key,
                    Settings.HIDE_REELS_FRIENDS_TAB.key, Settings.SHOW_REELS_FRIENDS_TAB.key, Settings.PICK_FRIENDS_TAB.key));
            split(node, "buttons", L10n.t("Buttons and controls"), keys(Settings.HIDE_REEL_LIKE_BUTTON.key, Settings.HIDE_REEL_COMMENT_BUTTON.key, Settings.MUTE_BUTTON_ON_REELS.key, Settings.MUTE_BUTTON_IN_CLEAR_MODE.key, Settings.HIDE_REEL_FOLLOW_BUTTON.key,
                    Settings.HIDE_REEL_SAVE_BUTTON.key, Settings.TEXT_BUTTON_ON_REELS.key, Settings.SPEED_BUTTON_ON_REELS.key,
                    Settings.FULL_SCREEN_BUTTON_ON_REELS.key));
            split(node, "overlays", L10n.t("Overlays and details"), keys(
                    Settings.HIDE_REEL_CHIPS.key, Settings.HIDE_REEL_SEARCH_PILL.key, Settings.HIDE_REEL_PRODUCT_PILLS.key,
                    Settings.HIDE_REEL_POLL_PILLS.key, Settings.HIDE_REEL_SOCIAL_FOOTER.key, Settings.HIDE_REEL_COMMENT_BAR.key,
                    Settings.HIDE_REEL_COMMENT_BAR_EVERYWHERE.key, Settings.KEEP_REEL_COMMENT_BAR_SPACE.key, Settings.HIDE_REEL_REPLY_BAR.key,
                    Settings.SHOW_REEL_POST_TIME.key,
                    Settings.REEL_POST_TIME_SECONDS.key, Settings.REEL_POST_TIME_AGO.key));
            split(node, "clear-mode", L10n.t("Clear mode and burn-in"), keys(Settings.KEEP_REEL_CLEAR_MODE.key,
                    Settings.START_REEL_CLEAR_MODE.key, Settings.QUIET_REEL_CLEAR_MODE.key, Settings.CLEAR_MODE_SEEK_BAR.key,
                    Settings.BURN_IN_GUARD.key, Settings.BURN_IN_WAIT.key, Settings.BURN_IN_STAY_DIM.key,
                    Settings.BURN_IN_LIGHT_WHEN_PAUSED.key, Settings.BURN_IN_STORIES.key));
            remainder(node, L10n.t("Appearance"));
        }
        if (icon.equals(SettingsIcons.STORIES)) {
            split(node, "uploads", L10n.t("Upload quality"), keys(Settings.UPLOAD_STORIES_AT_FULL_QUALITY.key,
                    Settings.UPLOAD_VIDEO_BITRATE.key, Settings.UPLOAD_VIDEO_RESOLUTION.key, Settings.UPLOAD_FRAME_RATE.key,
                    Settings.UPLOAD_PHOTO_QUALITY.key, Settings.UPLOAD_PHOTO_SIZE.key));
            split(node, "story-buttons", L10n.t("Buttons and reply box"), keys(Settings.HIDE_STORY_LIKE_BUTTON.key,
                    Settings.HIDE_STORY_COMMENT_BUTTON.key, Settings.HIDE_STORY_SHARE_BUTTON.key,
                    Settings.KEEP_STORY_BUTTONS_BELOW.key, Settings.HIDE_STORY_REPLY_BOX.key,
                    Settings.GLASS_STORY_REPLY_BOX.key, Settings.HIDE_STORY_FOLLOW.key));
            split(node, "suggestions", L10n.t("Suggestions and content"), keys(Settings.HIDE_SUGGESTED_STORIES.key, Settings.HIDE_STORY_REWINDS.key, Settings.HIDE_STORY_RECAPS.key, Settings.STOP_LOADING_STORIES.key, Settings.HIDE_MUSIC_CARD.key, Settings.HIDE_STORY_SUGGESTIONS.key, Settings.HIDE_ADD_YOURS_CARDS.key));
            split(node, "playback", L10n.t("Playback and gestures"), keys(Settings.STORY_SEEK_BAR_POSITION.key, Settings.STORY_SEEK_BAR.key, Settings.STORY_SEEK_BAR_LOOK.key,
                    Settings.BLOCK_STORY_AUTO_ADVANCE.key, Settings.LOOP_STORIES.key, Settings.START_STORIES_WITH_SOUND.key,
                    Settings.STOP_STORY_SWIPE_REPLY.key, Settings.KEEP_STORIES_PLAYING_ON_ZOOM.key, Settings.SMOOTH_STORY_OPEN.key));
            split(node, "privacy", L10n.t("Privacy and actions"), keys(Settings.VIEW_STORIES_ANONYMOUSLY.key, Settings.MARK_STORIES_SEEN.key, Settings.GRAY_OUT_WATCHED_STORIES.key, Settings.VIEW_LIVE_ANONYMOUSLY.key, Settings.SHOW_STORY_MENTIONS.key, Settings.DOWNLOAD_STORIES.key, Settings.UPLOAD_STORIES_AT_FULL_QUALITY.key));
            split(node, "fonts", L10n.t("Fonts and emoji"), keys(Settings.STORY_FONTS.key, Settings.STORY_EMOJI.key, HushgramPreferenceFragment.STORY_FONTS_ROW));
            split(node, "tray", L10n.t("Story tray"), keys(Settings.HIDE_STORIES_TRAY.key, Settings.KEEP_TRAY_IN_FEED.key,
                    Settings.FILTER_TRAY_BY_BADGE.key, Settings.TRAY_BADGE.key, Settings.STORY_RING.key,
                    Settings.STORY_RING_SCALE.key, Settings.STORY_RING_COLOR.key, Settings.ACCENT_RINGS_SOLID.key, Settings.STORY_PRIORITY.key,
                    Settings.STORY_PRIORITY_ACCOUNTS.key));
            split(node, "viewer-controls", L10n.t("Viewer controls"), keys(Settings.STORY_SPEED_IN_MENU.key, Settings.STORY_SPEED_BUTTON.key,
                    Settings.HIDE_STORY_REPLY_BOX.key,
                    Settings.HIDE_STORY_FOLLOW.key, Settings.SHOW_STORY_TIME.key, Settings.STORY_TIME_MODE.key,
                    Settings.DOWNLOAD_BUTTON_ON_STORIES.key, Settings.TEXT_BUTTON_ON_STORIES.key, Settings.MARK_LIVE_SEEN.key));
            remainder(node, L10n.t("Viewer appearance"));
        }
        if (icon.equals(SettingsIcons.TAB_BAR)) {
            split(node, "titles", L10n.t("Header titles and alignment"), keys(Settings.CENTER_HOME_LOGO.key,
                    Settings.CENTER_PROFILE_NAME.key, Settings.CENTER_SCREEN_TITLES.key, Settings.TITLES_AT_START.key));
            split(node, "tab-layout", L10n.t("Tab order and size"), keys(Settings.TAB_ORDER.key,
                    Settings.START_TAB.key, Settings.TAB_BAR_LAYOUT.key, Settings.TAB_ICON_SIZE.key));
            split(node, "glass-shape", L10n.t("Pill size and position"), keys(Settings.GLASS_TAB_BAR_HEIGHT.key,
                    Settings.GLASS_TAB_BAR_LIFT.key, Settings.GLASS_TAB_BAR_CORNERS.key,
                    Settings.GLASS_TAB_BAR_KEEP_SIZE.key));
            split(node, "feedback", L10n.t("Touch feedback and sound"), keys(Settings.GLASS_TAB_BAR_HAPTICS.key,
                    Settings.GLASS_TAB_BAR_HAPTIC_STYLE.key, Settings.GLASS_TAB_BAR_HAPTIC_STRENGTH.key,
                    Settings.GLASS_TAB_BAR_TAP_SOUND.key, Settings.GLASS_TAB_BAR_TAP_SOUND_VOLUME.key));
            split(node, "tabs", L10n.t("Tabs and order"), keys(Settings.HIDE_HOME_TAB.key, Settings.HIDE_REELS_TAB.key, Settings.SHOW_REELS_TAB.key, Settings.HIDE_SEARCH_TAB.key, Settings.HIDE_CREATE_TAB.key, Settings.HIDE_PROFILE_TAB.key, Settings.SHOW_MESSAGES_TAB.key, Settings.HIDE_MESSAGES_TAB.key, Settings.SHOW_ACTIVITY_TAB.key, Settings.TAB_ICON_SIZE.key, Settings.TAB_ORDER.key, Settings.START_TAB.key, Settings.TAB_BAR_LAYOUT.key));
            split(node, "header", L10n.t("Header buttons and titles"), keys(Settings.HIDE_HOME_CREATE_BUTTON.key, Settings.HIDE_HOME_NOTIFICATIONS_BUTTON.key, Settings.SEARCH_BUTTON_ON_HOME.key, Settings.MESSAGES_BUTTON_ON_HOME.key, Settings.HIDE_HOME_MESSAGES_BUTTON.key, Settings.GHOST_BUTTON_ON_HOME.key, Settings.SETTINGS_BUTTON_ON_HOME.key, Settings.MENU_BUTTON_ON_HOME.key, Settings.HISTORY_BUTTON_ON_HOME.key, Settings.HEART_COUNT.key, Settings.CENTER_HOME_LOGO.key, Settings.CENTER_PROFILE_NAME.key, Settings.CENTER_SCREEN_TITLES.key, Settings.TITLES_AT_START.key));
            split(node, "behavior", L10n.t("Scrolling and visibility"), keys(Settings.GLASS_TAB_BAR_SHRINK_ON_SCROLL.key, Settings.GLASS_TAB_BAR_SLIDE.key, Settings.GLASS_TAB_BAR_HAPTICS.key, Settings.GLASS_TAB_BAR_HAPTIC_STYLE.key, Settings.GLASS_TAB_BAR_HIDE_ON_SCROLL.key, Settings.GLASS_TAB_BAR_REELS_HIDE.key));
            remainder(node, L10n.t("Glass style"));
        }
        if (icon.equals(SettingsIcons.DOWNLOADS)) {
            split(node, "save-actions", L10n.t("Download buttons and gestures"), keys(Settings.FEED_DOWNLOAD_BUTTON.key,
                    Settings.FEED_DOWNLOAD_ALL_PAGES.key, Settings.DOWNLOAD_CHOOSE_PAGES.key, Settings.HOLD_TO_DOWNLOAD.key,
                    Settings.HOLD_FINGERS.key, Settings.HOLD_TIME.key, Settings.SEND_DOWNLOADS_TO_APP.key,
                    Settings.OPEN_IN_PLAYER.key));
            split(node, "media", L10n.t("Media types"), keys(Settings.DOWNLOAD_VIDEOS.key, Settings.DOWNLOAD_FEED_COVER.key, Settings.DOWNLOAD_PHOTOS.key, Settings.POST_DETAILS.key, Settings.DOWNLOAD_CHOOSE_PAGES.key, Settings.FEED_DOWNLOAD_BUTTON.key, Settings.FEED_DOWNLOAD_ALL_PAGES.key, Settings.HOLD_TO_DOWNLOAD.key, Settings.HOLD_FINGERS.key, Settings.HOLD_TIME.key, Settings.DOWNLOAD_VOICE_MESSAGES.key, Settings.OPEN_IN_PLAYER.key, Settings.SEND_DOWNLOADS_TO_APP.key));
            split(node, "profiles", L10n.t("Profile actions"), keys(Settings.SAVE_PROFILE_PICTURES.key, Settings.VIEW_PROFILE_PICTURES.key, Settings.COPY_PROFILE_TEXT.key, Settings.PROFILE_EXTRAS_BUTTON.key));
            split(node, "folders", L10n.t("Folders and file names"), keys(Settings.SAVE_FOLDER.key,
                    Settings.SAVE_FOLDER_PER_ACCOUNT.key, Settings.SAVE_NAME_BY_POST.key, Settings.SAVE_PHOTO_METADATA.key,
                    Settings.FILENAME_TEMPLATE.key, Settings.SAVE_TREE.key, Settings.SAVE_CHAT_MEDIA_TO_TREE.key,
                    HushgramPreferenceFragment.GALLERY_FOLDERS));
            remainder(node, L10n.t("Quality and saving"));
        }
        if (icon.equals(SettingsIcons.CHATS)) {
            split(node, "presence", L10n.t("Activity and typing"), keys(Settings.HIDE_ACTIVE_STATUS.key,
                    Settings.SHOW_LAST_ACTIVE.key, Settings.HIDE_TYPING.key, Settings.HIDE_BLEND_VISITS.key,
                    Settings.HIDE_SCREENSHOTS.key, Settings.ALLOW_SCREENSHOTS.key));
            split(node, "calls", L10n.t("Calls"), keys(Settings.ASK_BEFORE_CALL.key, Settings.HIDE_CALL_BUTTONS.key,
                    Settings.HIDE_VIDEO_CALL_BUTTON.key, Settings.HIDE_AUDIO_CALL_BUTTON.key,
                    Settings.CALL_BLOCK_MODE.key, Settings.CALL_BLOCK_ACCOUNTS.key));
            split(node, "inbox", L10n.t("Inbox appearance"), keys(Settings.SPLIT_NOTES_ROW.key, Settings.HIDE_NOTES_ROW.key, Settings.HIDE_NOTES_MAP.key,
                    Settings.HIDE_DM_COMPOSE_BUTTON.key, Settings.SETTINGS_BUTTON_IN_DM_HEADER.key,
                    Settings.HIDE_INBOX_SEARCH.key, Settings.HIDE_INBOX_SUGGESTIONS.key, Settings.HIDE_INSTANTS.key,
                    Settings.FULL_SCREEN_MESSAGES.key, HushgramPreferenceFragment.PINNED_CHATS_ROW));
            split(node, "controls", L10n.t("Chat appearance and behavior"), keys(Settings.KEEP_IN_CHAT.key, Settings.HIDE_KINDNESS_REMINDER.key, Settings.GLASS_CHAT.key,
                    Settings.MESSAGE_TAP_ACTIONS.key, Settings.MESSAGE_TAP_REACTIONS.key, Settings.HIDE_MESSAGE_ACTIONS.key, Settings.MORE_MESSAGE_ACTIONS.key,
                    Settings.ASK_BEFORE_CALL.key, Settings.HIDE_CALL_BUTTONS.key, Settings.HIDE_VIDEO_CALL_BUTTON.key,
                    Settings.HIDE_AUDIO_CALL_BUTTON.key, Settings.HIDE_BLEND_BUTTON.key));
            split(node, "security", L10n.t("App and chat locks"), keys(Settings.LOCK_SAVED.key, Settings.LOCK_EACH_CHAT.key, Settings.LOCK_MESSAGES.key, Settings.LOCK_APP.key, Settings.DOUBLE_TAP_HIDDEN_CHATS.key, Settings.LOCK_AGAIN.key, Settings.UNLOCK_WITH.key, HushgramPreferenceFragment.CHANGE_PIN_ROW, HushgramPreferenceFragment.LOCKED_CHATS_ROW, HushgramPreferenceFragment.HIDDEN_CHATS_ROW));
            split(node, "composer", L10n.t("Composer and More menu"), keys(Settings.HIDDEN_MESSAGE_BOX_BUTTONS.key,
                    Settings.HIDE_PLUS_BUTTON.key, Settings.HIDE_EMPTY_PLUS_MENU.key,
                    Settings.HIDE_PLUS_SAVED.key, Settings.HIDE_PLUS_AI_IMAGES.key, Settings.HIDE_PLUS_DRAW.key,
                    Settings.HIDE_PLUS_META_AI.key, Settings.HIDE_PLUS_LOCATION.key, Settings.MOVE_TO_PLUS_MENU.key));
            split(node, "media-history", L10n.t("Shared media and history"), keys(Settings.SWIPE_CHAT_MEDIA.key, Settings.SWIPE_CHAT_REELS.key,
                    Settings.STOP_AFTER_CHAT_REELS.key, Settings.ONLY_RECEIVED_CHAT_REELS.key, Settings.MESSAGES_AFTER_CHAT_REELS.key,
                    Settings.JUMP_IN_CHAT.key, Settings.EXPORT_CHAT.key, Settings.MARK_VIEW_ONCE.key));
            remainder(node, L10n.t("Privacy and receipts"));
        }
        if (icon.equals(SettingsIcons.LAYOUT)) {
            split(node, "accent", L10n.t("Accent colors"), keys(Settings.ACCENT_CUSTOM.key, Settings.ACCENT_COLOR.key, Settings.ACCENT_HUE.key,
                    Settings.ACCENT_BADGES.key, Settings.ACCENT_ICONS.key, Settings.ACCENT_RINGS.key,
                    Settings.ACCENT_BACKGROUNDS.key, Settings.ACCENT_RINGS_SOLID.key, Settings.THEME_NATIVE_POPUPS.key, LayoutCategory.PURE_BLACK_HINT_ROW));
            split(node, "fonts", L10n.t("Fonts and emoji"), keys(Settings.FONT.key, Settings.NOTO_EMOJI.key, Settings.EMOJI_FONT.key));
            remainder(node, L10n.t("App layout and branding"));
        }
        if (icon.equals(SettingsIcons.PLAYBACK)) {
            split(node, "background", L10n.t("Background and picture in picture"), keys(Settings.KEEP_PLAYING_IN_BACKGROUND.key,
                    Settings.REELS_PICTURE_IN_PICTURE.key, Settings.STORIES_PICTURE_IN_PICTURE.key));
            split(node, "quality", L10n.t("Quality and data"), keys(Settings.DEFAULT_PLAYBACK_QUALITY.key,
                    Settings.PLAYBACK_QUALITY.key, Settings.QUALITY_TAB_TARGET.key, Settings.DATA_SAVER.key,
                    Settings.DATA_SAVER_MOBILE_DATA_ONLY.key, Settings.TURN_OFF_HDR_BOOSTS.key));
            remainder(node, L10n.t("Playback behavior"));
        }
        if (icon.equals(SettingsIcons.PRIVACY)) {
            split(node, "data", L10n.t("Personal data and prompts"), keys(Settings.KEEP_PHOTO_LOCATIONS.key,
                    Settings.HIDE_PLUS_UPSELL.key));
            split(node, "ads-tracking", L10n.t("Ads and link tracking"), keys(Settings.HIDE_ADS.key,
                    Settings.SKIP_AD_DATA_REMINDER.key, Settings.SANITIZE_SHARING_LINKS.key, Settings.SHARING_DOMAIN.key,
                    Settings.OPEN_LINKS_EXTERNALLY.key));
            remainder(node, L10n.t("Privacy and connection"));
        }
        if (icon.equals(SettingsIcons.PERSON)) {
            split(node, "profile-history", L10n.t("Profile history"), keys(Settings.PROFILE_HISTORY.key,
                    HushgramPreferenceFragment.PROFILE_HISTORY_ROW, HushgramPreferenceFragment.CLEAR_PROFILE_HISTORY));
            split(node, "saved-profiles", L10n.t("Saved profiles"), keys(Settings.SAVED_PROFILES.key,
                    HushgramPreferenceFragment.SAVED_PROFILES_ROW, Settings.SAVED_PROFILES_TAB_TARGET.key));
            split(node, "banners", L10n.t("Links and banners"), keys(Settings.HIDE_PROFILE_THREADS_BADGE.key,
                    Settings.HIDE_PROFILE_FACEBOOK_LINKS.key, Settings.HIDE_PROFILE_WHATSAPP_LINK.key,
                    Settings.HIDE_PROFILE_SONG.key, Settings.HIDE_PROFILE_INTERESTS.key, Settings.HIDE_PROFILE_CHANNEL_BANNER.key,
                    Settings.HIDE_OTHER_PROFILE_BANNERS.key));
            split(node, "relationships", L10n.t("Followers and relationships"), keys(Settings.SHOW_FRIENDSHIP_STATUS.key, Settings.FRIENDSHIP_STATUS_CHIP.key, Settings.FOLLOW_LABEL_PLACE.key, Settings.ASK_BEFORE_FOLLOW.key, Settings.FOLLOW_MARK_ON_PICTURE.key, Settings.MARK_FOLLOWING_LIST.key, Settings.FOLLOWING_NOT_BACK_FIRST.key, Settings.TRACK_UNFOLLOWERS.key, Settings.NOTIFY_UNFOLLOWS.key, HushgramPreferenceFragment.FOLLOWER_HISTORY_ROW, HushgramPreferenceFragment.UNFOLLOWERS_ROW, HushgramPreferenceFragment.CLEAR_UNFOLLOWERS));
            split(node, "suggestions", L10n.t("Suggestions and prompts"), keys(Settings.HIDE_PROFILE_SUGGESTIONS.key, Settings.HIDE_FOLLOW_LIST_SUGGESTIONS.key, Settings.HIDE_PROFILE_COMPLETION.key));
            split(node, "tabs-grid", L10n.t("Tabs and grid"), keys(Settings.HIDDEN_OWN_PROFILE_TABS.key,
                    Settings.HIDDEN_PROFILE_TABS.key, Settings.SAVED_ON_PROFILE.key,
                    Settings.SAVED_FAST_SCROLL.key, Settings.PROFILE_POSTS_LIST.key, Settings.PROFILE_GRID_JUMP.key));
            remainder(node, L10n.t("Profile header"));
        }
    }
}
