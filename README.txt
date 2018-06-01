## AUTHOR
Noah Lerner 336322011


## FILES
Chunk.java - describes a chunk of data given in a byte array. This is the piece that FileWriter writes to the data file

DownloadableMetadata.java - this is the metadata object that tracks download progress

FileWriter.java - manages updating the data file and the metadata file

HTTPRangeGetter.java - downloads and packages a byte range of our download into chunks and sends them off to be written to the file

IdcDm.java - contains the main function. initializes all the objects we need for our download

Range.java - describes a range of data that we hand off to the HTTPRangeGetter

RateLimiter.java - updates the tokenbucket to contain a specific amount of tokens every second

TokenBucket.java - lets HTTPRangeGetters take tokens in order to continue their downloads, only if there are enough tokens in the bucket