package design.inherent.skyweavecompat.presentation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Close excess gaps from the bottom without moving a row down or crossing the title logo. */
public final class FooterLayout {
    private FooterLayout() {}

    public record Row(int top, int height) {}

    public static Optional<List<Integer>> reserve(List<Row> rows, int minimumTop, int maximumBottom) {
        var positions = new ArrayList<Integer>(rows.size());
        for (var row : rows) {
            if (row.height() <= 0 || !positions.isEmpty() && row.top() <= positions.getLast())
                throw new IllegalArgumentException("Expected distinct ordered menu rows with positive heights");
            positions.add(row.top());
        }
        int nextBottom = maximumBottom;
        for (int i = rows.size() - 1; i >= 0; i--) {
            int top = Math.min(rows.get(i).top(), nextBottom - rows.get(i).height());
            positions.set(i, top);
            nextBottom = top - 2;
        }
        return positions.isEmpty() || positions.getFirst() >= minimumTop
                ? Optional.of(List.copyOf(positions)) : Optional.empty();
    }
}
