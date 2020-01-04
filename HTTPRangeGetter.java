import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;

/**
 * A runnable class which downloads a given url.
 * It reads CHUNK_SIZE at a time and writes it into a BlockingQueue.
 * It supports downloading a range of data, and limiting the download rate using a token bucket.
 */
public class HTTPRangeGetter implements Callable<Void> {
    static final int CHUNK_SIZE = 4096;
    private static final int CONNECT_TIMEOUT = 500;
    private static final int READ_TIMEOUT = 5000;
    private final String url;
    private final Range range;
    private final BlockingQueue<Chunk> outQueue;
    private TokenBucket tokenBucket;
    private Semaphore numChunks;

    HTTPRangeGetter(
            String url,
            Range range,
            BlockingQueue<Chunk> outQueue,
            Semaphore numChunks) {
        this.url = url;
        this.range = range;
        this.outQueue = outQueue;
        this.tokenBucket = TokenBucket.getInstance();
        this.numChunks = numChunks;
    }

    private void downloadRange() throws IOException, InterruptedException {
        
    	// convert the string url to type URL for opening the connection
    	URL url = new URL(this.url);
    	
    	// open the HTTP connection using the URL
    	HttpURLConnection conn = (HttpURLConnection)url.openConnection();
    	
    	// set the connect timeout
    	conn.setConnectTimeout(CONNECT_TIMEOUT);
    	// set the request timeout
    	conn.setReadTimeout(READ_TIMEOUT);
    	
    	// set to download the specific range
    	String range = "bytes=" + this.range.getStart() + "-" + this.range.getEnd();
    	conn.setRequestProperty("Range", range);
    	
    	// complete the connection to the server
    	conn.connect();
    	
    	int responseCode = conn.getResponseCode();
    	
    	// make sure that the response code is valid
    	if(!(responseCode < 300 && responseCode > 199)) {
    		
    		// if there is an error in the response code, it is likely due to a
    		// corrupted Metadata file. We handle the exception in main.
    		System.err.println("Response Code Error: " + responseCode);
    		throw new IOException();
    	}
    	
    	// start reading data from the input stream
    	BufferedInputStream in = null;
    	//RandomAccessFile buf = null;
    	
    	in = new BufferedInputStream(conn.getInputStream());
    	
    	// put data from the inputstream to a byte array
    	byte data[] = new byte[CHUNK_SIZE];
    	
    	// in this range, we want to start reading bytes at index 0 and read until
    	// we have enough data for a Chunk, or we reach the end of a range
    	int dat = in.read(data, 0, CHUNK_SIZE);
    	
    	// we will need to keep track of the offset for writing to the file
    	Long offset = this.range.getStart();
    	
    	// we create a stamp which tells the chunk if it's the last in a range
    	// initialize to 0
    	boolean stamp = false;
    	
    	// loop through the input stream and put chunks into the chunkQueue
    	// when we finish reading from our range, dat = -1
    	while(dat != -1) {
    		stamp = false;
    		
    		// if this chunk is the last, then offset+dat should be equal to the end of the range
    		if((offset+dat) >= this.range.getEnd()) {
    			stamp = true;
    		}
    		
    		// Package the dataArray in a Chunk and send it to the ChunkQueue
    		Chunk chunk = new Chunk(data, offset, dat, this.range, stamp);
    		
    		// take 1 token for each byte read from the input stream
    		tokenBucket.take(dat);
    		
    		outQueue.put(chunk);
    		numChunks.release();
    		
    		// increase the offset for the next read
    		// note that offset is useful for the FileWriter
    		offset += dat;
    		dat = in.read(data, 0, CHUNK_SIZE);
    		
    	}
    	
    	// close the input stream
    	in.close();
    	// if we reach this code, it means that our HTTPRangeGetter has read & bundled
    	// all the data in our range (sent it to the Chunk Queue)

    	
    }

    @Override
    public Void call() throws IOException {
    	
    	try {
			this.downloadRange();
		} catch (InterruptedException e) {
			
			// catch and do nothing, print in main
		}
    	return null;
    }
    
}

