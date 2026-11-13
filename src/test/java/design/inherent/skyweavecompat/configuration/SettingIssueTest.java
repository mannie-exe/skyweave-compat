package design.inherent.skyweavecompat.configuration;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SettingIssueTest {
    @Test void wrappedIssuesPreserveExplicitRoutingWithoutGuessingFromMessages() {
        var issue = new SettingIssue("range", "minimum", List.of("maximum"), "A shared constraint failed");
        assertSame(issue, SettingIssue.find(new RuntimeException("wrapper", new SettingIssue.InvalidValue(issue))));
        assertNull(SettingIssue.find(new IllegalArgumentException("minimum: invalid")));
        var first = new RuntimeException("first"); var second = new RuntimeException("second",first);
        first.initCause(second); assertNull(SettingIssue.find(first));
    }
}
