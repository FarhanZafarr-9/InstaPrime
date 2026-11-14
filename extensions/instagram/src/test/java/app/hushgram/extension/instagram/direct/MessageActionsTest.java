/* Copyright 2026 HushGram contributors */
package app.hushgram.extension.instagram.direct;

import static org.junit.Assert.*;
import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class MessageActionsTest {
    @Test public void moreCannotKeepAnOtherwiseEmptyOverflowAlive() {
        MessageActions.Lists result = MessageActions.rearrange(Arrays.asList(Action.REPLY, Action.MORE),
                Arrays.asList(Action.MORE), Collections.emptyList(), set(), set(), KIND);
        assertEquals(Collections.singletonList(Action.REPLY), result.primary);
        assertTrue(result.more.isEmpty());
    }
    enum Action { REPLY, COPY_TEXT, FORWARD, REPORT, MORE, META_AI, RESTYLE, IMAGINE_THIS, FUTURE_ACTION }
    static final MessageActions.Kind KIND = row -> (Action) row;
    private static Set<String> set(String... names) { return new LinkedHashSet<>(Arrays.asList(names)); }

    @Test public void movesNativeObjectsWithoutLosingUnavailableOrNewActionRules() {
        List<Action> first = Collections.unmodifiableList(Arrays.asList(Action.REPLY, Action.COPY_TEXT, Action.MORE, Action.FUTURE_ACTION));
        List<Action> overflow = Collections.singletonList(Action.REPORT);
        MessageActions.Lists result = MessageActions.rearrange(first, overflow, Collections.emptyList(),
                set("REPORT"), set("COPY_TEXT", "FORWARD"), KIND);
        assertEquals(Arrays.asList(Action.REPLY, Action.MORE, Action.FUTURE_ACTION), result.primary);
        assertEquals(Collections.singletonList(Action.COPY_TEXT), result.more);
        assertSame(first.get(1), result.more.get(0));
        assertEquals(4, first.size());
        assertEquals(Collections.singletonList(Action.REPORT), overflow);
    }
    @Test public void emptyMoreDisappearsAndEmptyAiContainerDoesNotKeepItAlive() {
        MessageActions.Lists result = MessageActions.rearrange(Arrays.asList(Action.REPLY, Action.MORE),
                Collections.singletonList(Action.META_AI), Collections.singletonList(Action.RESTYLE),
                set("RESTYLE"), Collections.emptySet(), KIND);
        assertEquals(Collections.singletonList(Action.REPLY), result.primary);
        assertTrue(result.more.isEmpty());
        assertTrue(result.ai.isEmpty());
    }
    @Test public void actionsCanMoveOutOfTheAiSubmenuWithoutChangingTheRest() {
        MessageActions.Lists result = MessageActions.rearrange(Arrays.asList(Action.REPLY, Action.META_AI),
                Collections.emptyList(), Arrays.asList(Action.RESTYLE, Action.IMAGINE_THIS),
                Collections.emptySet(), set("RESTYLE"), KIND);
        assertEquals(Arrays.asList(Action.REPLY, Action.META_AI), result.primary);
        assertEquals(Collections.singletonList(Action.RESTYLE), result.more);
        assertEquals(Collections.singletonList(Action.IMAGINE_THIS), result.ai);
    }
    @Test public void hideTakesPrecedenceAndExplicitMoreHideIsRetained() {
        MessageActions.Lists result = MessageActions.rearrange(Arrays.asList(Action.REPLY, Action.COPY_TEXT, Action.MORE),
                Collections.singletonList(Action.REPORT), Collections.emptyList(),
                set("COPY_TEXT", "MORE"), set("COPY_TEXT"), KIND);
        assertEquals(Collections.singletonList(Action.REPLY), result.primary);
        assertEquals(Collections.singletonList(Action.REPORT), result.more);
    }
    @Test public void parserRejectsUnknownSettingsButNativeUnknownActionsStay() {
        assertEquals(set("REPLY", "COPY_TEXT"), MessageActions.parse(" REPLY,COPY_TEXT,REPLY,new,,"));
        assertEquals("REPLY,COPY_TEXT", MessageActions.join(set("COPY_TEXT", "REPLY", "unknown")));
        MessageActions.Lists result = MessageActions.rearrange(Collections.singletonList(Action.FUTURE_ACTION),
                Collections.emptyList(), Collections.emptyList(), Collections.emptySet(), Collections.emptySet(), KIND);
        assertEquals(Collections.singletonList(Action.FUTURE_ACTION), result.primary);
    }
}
