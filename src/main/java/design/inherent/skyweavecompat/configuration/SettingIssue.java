package design.inherent.skyweavecompat.configuration;

import java.util.List;

/** An expected validation failure with stable routing separate from its readable explanation. */
public record SettingIssue(String code, String field, List<String> related, String message) {
    public SettingIssue { related = List.copyOf(related); }
    public static final class InvalidValue extends IllegalArgumentException {
        private final SettingIssue issue;
        public InvalidValue(SettingIssue issue) { super(issue.message()); this.issue = issue; }
        public SettingIssue issue() { return issue; }
    }
}
