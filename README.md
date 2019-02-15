
# S3Checksum

A simple Java program to calculate AWS S3 'ETag' checksums.

*References:*
- https://docs.aws.amazon.com/cli/latest/topic/s3-faq.html
- https://docs.aws.amazon.com/cli/latest/reference/s3api/head-object.html


### Usage

```
Usage: s3Checksum [-chunkSize size_in_MB] file1 ... fileN

Command line options:");
    chunkSize <size> : Size in MB of each 'chunk' of MD5. Default 8 MB
```
The defailt chunk size is set to 8MB which seems to be the default size in `aws s3 cp` and `aws s3 sync` commands.


Tipical use: Run the jar file using a list of file as command line, e.g.:
````
java -jar s3checksum.jar file1 file2 file3
````



