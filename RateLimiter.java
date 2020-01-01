import java.util.concurrent.Callable;

/**
 * A token bucket based rate-limiter.
 *
 * This class should implement a "soft" rate limiter by adding maxBytesPerSecond tokens to the bucket every second,
 * or a "hard" rate limiter by resetting the bucket to maxBytesPerSecond tokens every second.
 */
public class RateLimiter implements Callable<Void> {
    private final TokenBucket tokenBucket;
    private final Long maxBytesPerSecond;
    private final long PER_SECOND = 1000;

    RateLimiter(Long maxBytesPerSecond) {
        this.tokenBucket = new TokenBucket();
		this.maxBytesPerSecond = (maxBytesPerSecond == null) ? Long.MAX_VALUE : maxBytesPerSecond;
	}

    public Void call() {
    	
    	// rate limiter resets the TokenBucket to maxBytesPerSecond every second
    	try {
    		while(!Thread.interrupted()) {
    			
    			this.tokenBucket.set(this.maxBytesPerSecond);
    			Thread.sleep(PER_SECOND);
    		}
    	} catch (InterruptedException e) {
    		// do nothing with the exception
    		// exits the program cleanly
    	}
    	
    	return null;
    }
}
