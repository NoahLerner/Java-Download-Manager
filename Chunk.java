/**
 * A chunk of data file
 *
 * Contains an offset, bytes of data, size, and a stamp 
 * which is equal to the end of the range if it is the last chunk in the range
 */
class Chunk {
    private byte[] data;
    private long offset;
    private int size_in_bytes;
    private Range range;
    private boolean stamp;

    Chunk(byte[] data, long offset, int size_in_bytes, Range range, boolean stamp) {
        this.data = data != null ? data.clone() : null;
        this.offset = offset;
        this.size_in_bytes = size_in_bytes;
        this.range = range;
        this.stamp = stamp;
    }

    byte[] getData() {
        return data;
    }

    long getOffset() {
        return offset;
    }

    int getSize_in_bytes() {
        return size_in_bytes;
    }
    
    boolean getStamp() {
    	return stamp;
    }
    
    Range getRange() {
    	return this.range;
    }
}
