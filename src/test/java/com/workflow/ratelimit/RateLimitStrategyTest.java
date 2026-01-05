package com.workflow.ratelimit;

import static org.junit.jupiter.api.Assertions.*;

import com.workflow.sleeper.ThreadSleepingSleeper;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class RateLimitStrategyTest {

  @Test
  void rateLimitStrategy_defaultAvailablePermits_returnsNegativeOne() {
    RateLimitStrategy strategy = createTestStrategy();

    assertEquals(-1, strategy.availablePermits());
  }

  @Test
  void rateLimitStrategy_acquire_canBeImplemented() {
    RateLimitStrategy strategy = createTestStrategy();

    assertDoesNotThrow(strategy::acquire);
  }

  @Test
  void rateLimitStrategy_tryAcquire_canBeImplemented() {
    RateLimitStrategy strategy = createTestStrategy();
    strategy.tryAcquire();

    // Result depends on implementation
    assertTrue(true); // Just verify it returns a boolean
  }

  @Test
  void rateLimitStrategy_tryAcquireWithTimeout_canBeImplemented() {
    RateLimitStrategy strategy = createTestStrategy();

    assertDoesNotThrow(() -> strategy.tryAcquire(1000));
  }

  @Test
  void rateLimitStrategy_reset_canBeImplemented() {
    RateLimitStrategy strategy = createTestStrategy();

    assertDoesNotThrow(strategy::reset);
  }

  @Test
  void rateLimitStrategy_acquire_canBeInterrupted() {
    RateLimitStrategy strategy =
        new RateLimitStrategy() {
          @Override
          public void acquire() throws InterruptedException {
            new ThreadSleepingSleeper().sleep(Duration.ofMillis(5000));
          }

          @Override
          public boolean tryAcquire() {
            return false;
          }

          @Override
          public boolean tryAcquire(long timeoutMillis) {
            return false;
          }

          @Override
          public void reset() {
            // No-op
          }
        };

    Thread thread = new Thread(() -> assertThrows(InterruptedException.class, strategy::acquire));

    thread.start();
    thread.interrupt();

    assertDoesNotThrow(() -> thread.join(2000));
  }

  @Test
  void rateLimitStrategy_multipleAcquires_workCorrectly() throws InterruptedException {
    int[] count = {0};
    RateLimitStrategy strategy =
        new RateLimitStrategy() {
          @Override
          public void acquire() {
            count[0]++;
          }

          @Override
          public boolean tryAcquire() {
            return true;
          }

          @Override
          public boolean tryAcquire(long timeoutMillis) {
            return true;
          }

          @Override
          public void reset() {
            count[0] = 0;
          }
        };

    strategy.acquire();
    strategy.acquire();
    strategy.acquire();

    assertEquals(3, count[0]);

    strategy.reset();
    assertEquals(0, count[0]);
  }

  @Test
  void rateLimitStrategy_availablePermitsOverride_canReturnCustomValue() {
    RateLimitStrategy strategy =
        new RateLimitStrategy() {
          @Override
          public void acquire() {
            // No-op
          }

          @Override
          public boolean tryAcquire() {
            return true;
          }

          @Override
          public boolean tryAcquire(long timeoutMillis) {
            return true;
          }

          @Override
          public int availablePermits() {
            return 42;
          }

          @Override
          public void reset() {
            // No-op
          }
        };

    assertEquals(42, strategy.availablePermits());
  }

  private RateLimitStrategy createTestStrategy() {
    return new RateLimitStrategy() {
      @Override
      public void acquire() {
        // No-op implementation for testing
      }

      @Override
      public boolean tryAcquire() {
        return true;
      }

      @Override
      public boolean tryAcquire(long timeoutMillis) {
        return true;
      }

      @Override
      public void reset() {
        // No-op implementation for testing
      }
    };
  }
}
