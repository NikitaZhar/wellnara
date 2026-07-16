package life.wellnara.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory guard against password brute-forcing on the login endpoint.
 *
 * <p>Failed attempts are counted per login key within a rolling window; once the
 * count reaches {@code wellnara.login.max-attempts} the key is blocked for
 * {@code wellnara.login.lockout}. A successful login clears the key. Counters
 * decay after the window, so isolated typos never accumulate into a lockout.
 *
 * <p>State is process-local and non-persistent, which is sufficient for a
 * single-instance MVP: it is lost on restart and counted per replica. When the
 * application scales to several replicas (phase 5) this is replaced by a shared
 * store (database/Redis). The map is bounded — entries are removed on success
 * and expired entries are purged — so it cannot grow without limit.
 *
 * <p>The key is the login name (normalised). This trades off a possible
 * account-lockout annoyance for simplicity; an IP-based dimension can be added
 * later without changing callers.
 */
@Service
public class LoginAttemptService {

    private static final int MAX_TRACKED_KEYS = 10_000;

    private final int maxAttempts;
    private final Duration lockoutDuration;
    private final Clock clock;
    private final Map<String, Attempt> attemptsByKey = new ConcurrentHashMap<>();

    /**
     * @param clock           application clock (UTC bean), injected for testability
     * @param maxAttempts     failed attempts allowed before a key is blocked
     * @param lockoutMinutes  how long (in minutes) a blocked key stays blocked;
     *                        also the rolling window for counting failures
     */
    public LoginAttemptService(Clock clock,
                               @Value("${wellnara.login.max-attempts:5}") int maxAttempts,
                               @Value("${wellnara.login.lockout-minutes:15}") long lockoutMinutes) {
        this.clock = clock;
        this.maxAttempts = maxAttempts;
        this.lockoutDuration = Duration.ofMinutes(lockoutMinutes);
    }

    /**
     * @param key login key (typically the username)
     * @return true if the key is currently locked out
     */
    public boolean isBlocked(String key) {
        Attempt attempt = attemptsByKey.get(normalize(key));
        return attempt != null && attempt.blockedAt(clock.instant());
    }

    /**
     * Registers a failed login for the key and blocks it once the threshold is
     * reached.
     *
     * @param key login key (typically the username)
     */
    public void recordFailure(String key) {
        Instant now = clock.instant();
        attemptsByKey.compute(normalize(key), (ignoredKey, current) -> registerFailure(current, now));
        purgeExpiredIfNeeded(now);
    }

    /**
     * Clears any tracked failures for the key after a successful login.
     *
     * @param key login key (typically the username)
     */
    public void reset(String key) {
        attemptsByKey.remove(normalize(key));
    }

    private Attempt registerFailure(Attempt current, Instant now) {
        int carriedFailures = (current == null || current.expiredAt(now, lockoutDuration))
                ? 0
                : current.failures();
        int failures = carriedFailures + 1;
        Instant blockedUntil = failures >= maxAttempts ? now.plus(lockoutDuration) : null;

        return new Attempt(failures, now, blockedUntil);
    }

    private void purgeExpiredIfNeeded(Instant now) {
        if (attemptsByKey.size() <= MAX_TRACKED_KEYS) {
            return;
        }

        attemptsByKey.values().removeIf(attempt ->
                !attempt.blockedAt(now) && attempt.expiredAt(now, lockoutDuration));
    }

    private String normalize(String key) {
        return key == null ? "" : key.strip().toLowerCase(Locale.ROOT);
    }

    /**
     * Immutable snapshot of the failure state for one key.
     *
     * @param failures     consecutive failures counted within the window
     * @param lastFailureAt instant of the most recent failure
     * @param blockedUntil  instant until which the key is blocked, or {@code null}
     */
    private record Attempt(int failures, Instant lastFailureAt, Instant blockedUntil) {

        boolean blockedAt(Instant now) {
            return blockedUntil != null && now.isBefore(blockedUntil);
        }

        /**
         * Whether the tracked state no longer counts at {@code now}: a served
         * lockout has ended, or the rolling window has elapsed since the last
         * failure. In both cases the next failure starts a fresh count.
         */
        boolean expiredAt(Instant now, Duration window) {
            if (blockedUntil != null) {
                return !now.isBefore(blockedUntil);
            }
            return !now.isBefore(lastFailureAt.plus(window));
        }
    }
}
