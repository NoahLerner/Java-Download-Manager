# Java-Download-Manager
A download manager written in Java

# Build
From top level folder run ```javac IdcDm.java```

# Run/Usage
```java IdcDm URL [MAX-CONCURRENT-CONNECTIONS] [MAX-DOWNLOAD-LIMIT]```

# Implementation Notes
## Classes
```IdcDm``` - main entry point into the application.
````Chunk.java``` - one piece of the file that the FileWriter will write to the file.
```DownloadableMetadata```- metadata object that tracks download progress.
```FileWriter``` - manages updating the data file and the metadata file
```HTTPRangeGetter``` - downloads and packages a byte range of our download into chunks and queues them up for the ```FileWriter```.
```Range``` - describes a range of data that we hand off to the HTTPRangeGetter.

### Rate limiting
```RateLimiter``` - updates the tokenbucket to contain a specific amount of tokens every second
```TokenBucket``` - lets HTTPRangeGetters take tokens in order to continue their downloads, only if there are enough tokens in the bucket. Can implement "soft" or "hard" limits.

# Author
Noah Lerner