package life.wellnara;

import life.wellnara.service.LoginAttemptService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link LoginAttemptService} using a controllable clock so that
 * lockout expiry and window decay are exercised deterministically.
 */
class LoginAttemptServiceTest {

    private static final int MAX_ATTEMPTS = 3;
    private static final long LOCKOUT_MINUTES = 15;
    private static final Duration LOCKOUT = Duration.ofMinutes(LOCKOUT_MINUTES);
    private static final String KEY = "user";

    @Test
    @DisplayName("Should not block a key with no failures")
    void shouldNotBlockKeyWithoutFailures() {
        LoginAttemptService service = newService(new MutableClock(Instant.EPOCH));

        assertThat(service.isBlocked(KEY)).isFalse();
    }

    @Test
    @DisplayName("Should not block before reaching the attempt threshold")
    void shouldNotBlockBeforeThreshold() {
        LoginAttemptService service = newService(new MutableClock(Instant.EPOCH));

        service.recordFailure(KEY);
        service.recordFailure(KEY);

        assertThat(service.isBlocked(KEY)).isFalse();
    }

    @Test
    @DisplayName("Should block once the attempt threshold is reached")
    void shouldBlockAtThreshold() {
        LoginAttemptService service = newService(new MutableClock(Instant.EPOCH));

        recordFailures(service, MAX_ATTEMPTS);

        assertThat(service.isBlocked(KEY)).isTrue();
    }

    @Test
    @DisplayName("Should clear the block after a successful login is reset")
    void shouldClearBlockOnReset() {
        LoginAttemptService service = newService(new MutableClock(Instant.EPOCH));
        recordFailures(service, MAX_ATTEMPTS);

        service.reset(KEY);

        assertThat(service.isBlocked(KEY)).isFalse();
    }

    @Test
    @DisplayName("Should lift the block once the lockout window has elapsed")
    void shouldUnblockAfterLockoutExpires() {
        MutableClock clock = new MutableClock(Instant.EPOCH);
        LoginAttemptService service = newService(clock);
        recordFailures(service, MAX_ATTEMPTS);

        clock.advance(LOCKOUT.plusSeconds(1));

        assertThat(service.isBlocked(KEY)).isFalse();
    }

    @Test
    @DisplayName("Should start counting from scratch after the lockout expires")
    void shouldRestartCountAfterLockoutExpires() {
        MutableClock clock = new MutableClock(Instant.EPOCH);
        LoginAttemptService service = newService(clock);
        recordFailures(service, MAX_ATTEMPTS);
        clock.advance(LOCKOUT.plusSeconds(1));

        service.recordFailure(KEY);

        assertThat(service.isBlocked(KEY)).isFalse();
    }

    @Test
    @DisplayName("Should decay stale failures so isolated typos do not accumulate")
    void shouldDecayStaleFailures() {
        MutableClock clock = new MutableClock(Instant.EPOCH);
        LoginAttemptService service = newService(clock);

        service.recordFailure(KEY);
        service.recordFailure(KEY);
        clock.advance(LOCKOUT.plusSeconds(1));
        service.recordFailure(KEY);

        assertThat(service.isBlocked(KEY)).isFalse();
    }

    @Test
    @DisplayName("Should treat keys case-insensitively and ignore surrounding whitespace")
    void shouldNormalizeKeys() {
        LoginAttemptService service = newService(new MutableClock(Instant.EPOCH));

        service.recordFailure("User");
        service.recordFailure("  user ");
        service.recordFailure("USER");

        assertThat(service.isBlocked("user")).isTrue();
    }

    private LoginAttemptService newService(Clock clock) {
        return new LoginAttemptService(clock, MAX_ATTEMPTS, LOCKOUT_MINUTES);
    }

    private void recordFailures(LoginAttemptService service, int times) {
        for (int i = 0; i < times; i++) {
            service.recordFailure(KEY);
        }
    }

    /**
     * Minimal advanceable clock for exercising time-dependent behaviour.
     */
    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant start) {
            this.instant = start;
        }

        private void advance(Duration amount) {
            this.instant = this.instant.plus(amount);
        }

        @Override
        public Instant instant() {
            return instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
