import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;

/**
 * This class takes chunks from the queue, writes them to disk and updates the file's metadata.
 *
 * NOTE: make sure that the file interface you choose writes every update to the file's content or metadata
 *       synchronously to the underlying storage device.
 */
public class FileWriter implements Callable<Void> {

    private final BlockingQueue<Chunk> chunkQueue;
    private DownloadableMetadata downloadableMetadata;
    private Semaphore numChunks;
    private RandomAccessFile data;
    
    FileWriter(DownloadableMetadata downloadableMetadata, BlockingQueue<Chunk> chunkQueue, Semaphore numChunks) {
        this.chunkQueue = chunkQueue;
        this.downloadableMetadata = downloadableMetadata;
        this.numChunks = numChunks;
        this.data = null;
    }

    private void writeChunks() throws InterruptedException, IOException {
    	
    	// get access to the data file
    	data = new RandomAccessFile(downloadableMetadata.getFilename(), "rwd");
    	
    	// setting up our variables for the writing loop
    	Chunk chunk;
    	int percent = (int)(((double)downloadableMetadata.numRanges() - (double)downloadableMetadata.getNumRangesLeft())*100 
    			/ (double)downloadableMetadata.numRanges());;
    	System.err.println("Downloaded " + percent + "%");
    	
    	// While metadata object indicates that I'm still downloading....
    	while(downloadableMetadata.getNumRangesLeft() != 0) {
    		
    		// writes to file
    		numChunks.acquire();
    		chunk = chunkQueue.take();
    		data.seek(chunk.getOffset());
    		data.write(chunk.getData(), 0, chunk.getSize_in_bytes());
    		// update the metadata file when we completed a range
    		if(chunk.getStamp()) {
    			// remove the range from the rangeList
    			downloadableMetadata.removeFromRanges(chunk.getRange());
    			// copy the rangeList into a temporary file
    			downloadableMetadata.writeMissingRanges("temp." + downloadableMetadata.getMetadataFileName());

    			// move the temp file to be the proper metadata file
    			// atomically
    			Path metadatafile = Paths.get(downloadableMetadata.getMetadataFileName());
    			Path tempfile = Paths.get("temp." + downloadableMetadata.getMetadataFileName());
    			
    			try {
					Files.move(tempfile, metadatafile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);

				} catch (IOException e) {
					
					System.err.println("Couldn't update metadata file.");
					System.exit(1);
				}
    			
    			percent = (int)(((double)downloadableMetadata.numRanges() - (double)downloadableMetadata.getNumRangesLeft())*100 / (double)downloadableMetadata.numRanges());
    			System.err.println("Downloaded " + percent + "%");
    		}	
    	}
    }


	@Override
    public Void call() throws IOException, InterruptedException {

		try {
			this.writeChunks();
		} catch (InterruptedException e) {
			// exit gracefully from the program
		}
		
	return null;
    }
}
