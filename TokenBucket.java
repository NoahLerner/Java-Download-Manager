import java.util.concurrent.atomic.AtomicLong;

/**
 * A Token Bucket (https://en.wikipedia.org/wiki/Token_bucket)
 *
 * This thread-safe bucket should support the following methods:
 *
 * - take(n): remove n tokens from the bucket (blocks until n tokens are available and taken)
 * - set(n): set the bucket to contain n tokens (to allow "hard" rate limiting)
 * - add(n): add n tokens to the bucket (to allow "soft" rate limiting)
 *
 */
class TokenBucket {

    private long numTokens;
    private static TokenBucket instance;
    private TokenBucket() {
        this.numTokens = 0;
    }

    // singleton
    synchronized static TokenBucket getInstance() {
        if(instance == null){
            instance = new TokenBucket();
        }

        return instance;
    }

    synchronized void take(long tokens) throws InterruptedException {
    	
        while(this.numTokens < tokens) {
        	
        	// wait for enough tokens to be available
        	this.wait();
        }
        
        // if we reach here, then there are enough tokens to be taken
        this.numTokens -= tokens;
    }

    
    synchronized void add(long tokens) {
    	
    	this.numTokens += tokens;
    	this.notify();
    }

    synchronized void set(long tokens) {
    	
    	this.numTokens = tokens;
    	this.notify();
    }
}
