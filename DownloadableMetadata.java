import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * Describes a file's metadata: URL, file name, size, and which parts already downloaded to disk.
 *
 * The metadata (or at least which parts already downloaded to disk) is constantly stored safely in disk.
 * When constructing a new metadata object, we first check the disk to load existing metadata.
 *
 * CHALLENGE: try to avoid metadata disk footprint of O(n) in the average case
 * HINT: avoid the obvious bitmap solution, and think about ranges...
 */
class DownloadableMetadata {
    private String metadataFilename;
    private String filename;
    private String url;
    private int content_length;
    private ArrayList<Range> rangeList;
    public ArrayBlockingQueue<Range> rangeQueue;
    
    // chunksPerRange is the number of chunks per range
    private int chunksPerRange;
    
    private static final int CHUNK_SIZE = 4096;
    private final int numBytesPerRange;
    private final int numRanges;
  

    DownloadableMetadata(String url) throws IOException {
        this.url = url;
        this.filename = getName(url);
        this.metadataFilename = getMetadataName(filename);
        
        // content_length is the expected filesize
        this.content_length = getContentLength(url);
       
        this.chunksPerRange = getN();
        
        this.numBytesPerRange = chunksPerRange*CHUNK_SIZE;
        
        this.numRanges = calcNumRanges();
        
        // ArrayList that tracks all the missing ranges
        this.rangeList = null;
        
        // ArrayBlockingQueue that feeds ranges to HTTPGetters
        this.rangeQueue = new ArrayBlockingQueue<Range>(numRanges);
    }

    /**
     * This method splits the rile into numRanges ranges and puts them into an ArrayList<Range>
     * @return ranges
     */
    private ArrayList<Range> initializeRanges() {

    	ArrayList<Range> ranges = new ArrayList<Range>(numRanges);
    	Range range = null;
    	
    	// add the ranges into the array list
    	for(int i = 0; i < numRanges; i++) {
			
			// in the first chunksPerRange-1 ranges, in each line we write:
			// Start_Long - End Long , which describes a single missing range
			if(i != numRanges - 1) {
				
				range = new Range((long)i*(numBytesPerRange), (long)(i+1)*(numBytesPerRange) - 1);
				ranges.add(range);
				
			// the last range gets the rest of the file
			} else {
				
				range = new Range((long)i*(numBytesPerRange), (long)this.content_length);
				ranges.add(range);

			}
		}
    	    	
		return ranges;
	}

	/**
     * method returns chunksPerRange which is the number of chunks in a range.
     * If the file is big enough, we define chunksPerRange so that there will be 100 ranges.
     * @return
     */
    private int getN() {
    	
    	int chunksPerRange = (this.content_length / CHUNK_SIZE) / 100;

    	if(chunksPerRange  == 0) {
    		// we put only 1 chunk per range, download file too small
    		return 1;
    	} else {
    		// file is big enough to make 100 ranges
    		return chunksPerRange;
    	}
	}

	private int calcNumRanges() {
    	// get the number of ranges for chunk sets of chunksPerRange chunks
		if(numBytesPerRange > this.content_length) {
			return 1;
		} else {
    		return this.content_length / numBytesPerRange;
		}
	}

	private static String getMetadataName(String filename) {
        return filename + ".metadata";
    }
	
	public String getMetadataFileName() {
		return this.metadataFilename;
	}

    private static String getName(String path) {
    	
    	return path.substring(path.lastIndexOf('/') + 1, path.length());
    }

    
    /**
     * Opens an HTTP connection and queries the connection for the content length
     * of the file to be downloaded.
     * @param url - the URL of the file to be downloaded
     * @return length - int
     * @throws IOException
     */
    private static int getContentLength(String url) throws IOException {
    	 // open the HTTP connection using the URL
    	
    	URL url_url;
		try {
			url_url = new URL(url);
		} catch (MalformedURLException e1) {
			System.err.println("Please check the URL and try again.");
			throw new IOException();
		}
    	
		HttpURLConnection conn = (HttpURLConnection)url_url.openConnection();
    	
    	try {
			conn.connect();
		} catch (IOException e) {
			System.err.println("Couldn't connect to the internet. Please check your connection.");
			throw new IOException();
		}
    	
    	// query the connection to find the file size
    	int length = conn.getContentLength();
    	
    	conn.disconnect();
    	
    	return length;
    	
    }
    
    String getFilename() {
   
    	return this.filename;
        
    }

    void delete(String filename) {
        File file = new File(filename);
        file.delete();
    }

    String getUrl() {
        return this.url;
    }
    
    /**
     * This returns the size of the expected file (content length)
     * NOT for the current size of the Data file
     * For size of the data file, see method getFileSize()
     * @return
     */
    int getSize() {
    	return this.content_length;
    }
    
    /**
     * write the missing ranges into a file given by filename
     * @param filename - the file in which to write the missing ranges
     * @throws IOException
     */
    void writeMissingRanges(String filename) throws IOException {
    	
    	// we write our rangeList to the metadata file
    	FileOutputStream fout = new FileOutputStream(filename);
    	ObjectOutputStream oos = new ObjectOutputStream(fout);
    	oos.writeObject(rangeList);
    	
    	fout.close();
    	oos.close();
    }

	
public boolean isEmptyRanges() {

		return rangeQueue.isEmpty();
	}
	

	public Range getMissingRange() {		
		return this.rangeQueue.poll();
	}

	public int getNumRangesLeft() {
		return this.rangeList.size();
	}

	public void removeFromRanges(Range range) {

		this.rangeList.remove(range);
		
	}

	// we suppress the unchecked warning for this exercise since we are sure that
	// the only object in our file is an ArrayList unless someone maliciously mishandled the file
	@SuppressWarnings("unchecked")
	public void openFile() throws IOException {
		
		File metadata = new File(this.metadataFilename);
		if(metadata.exists()) {
			
			// load the metadata from the file into the object
			FileInputStream fin = new FileInputStream(this.getMetadataFileName());
			ObjectInputStream ois = new ObjectInputStream(fin);
			try {
				this.rangeList = (ArrayList<Range>)ois.readObject();
			} catch (ClassNotFoundException e) {
				System.err.println("Well, shit.");
			}
			
			Iterator<Range> iter = this.rangeList.iterator();
			
			while(iter.hasNext()) {
				this.rangeQueue.add(iter.next());
			}
			
			fin.close();
			ois.close();
			
		} else {
			this.rangeList = initializeRanges();
			writeMissingRanges(this.metadataFilename);
			
			Iterator<Range> iter = this.rangeList.iterator();
			
			while(iter.hasNext()) {
				this.rangeQueue.add(iter.next());
			}
		}
		
	}

	public int numRanges() {
		return this.numRanges;
	}	
}