package life.wellnara.model;

/**
 * Immutable session balance of a service package: a fold over the session ledger.
 *
 * <p>{@code available} sessions can still be booked; {@code held} sessions are
 * reserved against pending appointments and not yet consumed.
 */
public final class SessionBalance {

    private final int available;
    private final int held;

    /**
     * Creates a session balance.
     *
     * @param available sessions still bookable
     * @param held sessions reserved against pending appointments
     */
    public SessionBalance(int available, int held) {
        this.available = available;
        this.held = held;
    }

    public int getAvailable() {
        return available;
    }

    public int getHeld() {
        return held;
    }

    /**
     * Returns the total sessions still owed to the client.
     *
     * @return {@code available + held}
     */
    public int getTotal() {
        return available + held;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SessionBalance that)) {
            return false;
        }
        return available == that.available && held == that.held;
    }

    @Override
    public int hashCode() {
        return 31 * available + held;
    }

    @Override
    public String toString() {
        return "SessionBalance{available=" + available + ", held=" + held + '}';
    }
}
