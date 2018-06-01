import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class IdcDm {

    /**
     * Receive arguments from the command-line, provide some feedback and start the download.
     *
     * @param args command-line arguments
     * @throws Exception 
     */
    public static void main(String[] args) throws Exception {
        int numberOfWorkers = 1;
        Long maxBytesPerSecond = null;

        if (args.length < 1 || args.length > 3) {
            System.err.printf("usage:\n\tjava IdcDm URL [MAX-CONCURRENT-CONNECTIONS] [MAX-DOWNLOAD-LIMIT]\n");
            System.exit(1);
        } else if (args.length >= 2) {
            numberOfWorkers = Integer.parseInt(args[1]);
            if (args.length == 3)
                maxBytesPerSecond = Long.parseLong(args[2]);
        }

        String url = args[0];

        System.err.printf("Downloading");
        if (numberOfWorkers > 1)
            System.err.printf(" using %d connections", numberOfWorkers);
        if (maxBytesPerSecond != null)
            System.err.printf(" limited to %d Bps", maxBytesPerSecond);
        System.err.printf("...\n");

        DownloadURL(url, numberOfWorkers, maxBytesPerSecond);
    }

    /**
     * Initiate the file's metadata, and iterate over missing ranges. For each:
     * 1. Hand a range to a getter thread and submit it to the threadpool for execution
     * 2. All along ensure that internet connection is good
     * 3. When download finishes, close all threads and begin shutdown
     *
     * Finally, print "Download succeeded/failed" and delete the metadata as needed.
     *
     * @param url URL to download
     * @param numberOfWorkers number of concurrent connections
     * @param maxBytesPerSecond limit on download bytes-per-second
     */
    private static void DownloadURL(String url, int numberOfWorkers, Long maxBytesPerSecond) {
    	
    	// Our thread pool will include all the HTTP Range getters
    	ExecutorService threadPool = Executors.newFixedThreadPool(numberOfWorkers+1);
    	// Second thread pool will be for rate limiter & FileWriter
    	ExecutorService peripheralPool = Executors.newFixedThreadPool(1);

    	
    	// ignore TokenBucket for now
    	TokenBucket tokenBuck = new TokenBucket();
    	
    	
    	//set up the RateLimiter
    	Callable<Void> limiter = new RateLimiter(tokenBuck,maxBytesPerSecond);
    	peripheralPool.submit(limiter);
    	
    	//-----------------------------------------------//
    	
    	// open the metadata file and get the content-length
    	DownloadableMetadata metafile = null;
		try {
			metafile = new DownloadableMetadata(url);
			metafile.openFile();
			
		} catch (IOException e) {
			threadPool.shutdownNow();
			peripheralPool.shutdownNow();
			System.err.println("Download failed");
			System.exit(1);
		}
    	
    	int length = metafile.getSize() / 4096;
    	
    	// instantiate the Chunk Queue
    	BlockingQueue<Chunk> outQueue = new ArrayBlockingQueue<Chunk>(length);

    	// fair semaphore with numberOfWorkers permits
    	Semaphore numChunks = new Semaphore(0, true);
    	
    	// write the data to a file  
    	// blocks until the chunkQueue starts getting chunks
    	Callable<Void> file = new FileWriter(metafile, outQueue, numChunks);
    	threadPool.submit(file);
    	    	
    	while(!metafile.isEmptyRanges()) {
    		    		
    		Future<Void> res;
    		Callable<Void> getter = new HTTPRangeGetter(url, metafile.getMissingRange(), 
					outQueue, tokenBuck, numChunks);
			res = threadPool.submit(getter);
			try {
				res.get();
			} catch (InterruptedException | ExecutionException e) {
				threadPool.shutdownNow();
				peripheralPool.shutdownNow();
				System.err.println("Lost internet connection. Please reconnect and try again.");
				System.err.println("Download failed");
				File temp = new File("temp." + metafile.getMetadataFileName());
				if(temp.exists()) {
					temp.delete();
				}
				System.exit(1);
			}
    	}
    	
    	threadPool.shutdown();
    	try {
			threadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			threadPool.shutdownNow();
		}
    	peripheralPool.shutdownNow();
    	
    	Path metadata = Paths.get(metafile.getMetadataFileName());
    	File temp = new File("temp." + metafile.getMetadataFileName());
    	
    	try {
			Files.delete(metadata);
			temp.delete();
			
		} catch (IOException e) {
			System.err.println(e);
			System.err.println("Couldn't delete Metadata files. Please do so manually.");
		}
    	System.err.println("Download succeeded");
    	System.exit(0);
    }
}
