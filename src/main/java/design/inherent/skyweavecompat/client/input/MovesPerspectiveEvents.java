package design.inherent.skyweavecompat.client.input;

/** Client-thread scope distinguishes the raw producer from the later accepted vanilla action. */
public final class MovesPerspectiveEvents {
    private static boolean keyboardDispatch;
    private MovesPerspectiveEvents() {}
    public static boolean keyboardDispatch() { return keyboardDispatch; }
    public static boolean enterKeyboard() { boolean previous = keyboardDispatch; keyboardDispatch = true; return previous; }
    public static void leaveKeyboard(boolean previous) { keyboardDispatch = previous; }
}
