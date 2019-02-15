
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

### Scripts

`scripts/s3checksum.bds` takes a file with a list of local/remote paths, calculates the ETag from the local file, retrieves the ETag from the file in S3 and compares them.

Usage: `s3checksum.bds -bucket bucket_name -fileList files.txt`

**Input:** File list format (`files.txt`):
- One entry per line
- Each line consists of `localFile` and `s3Path` sperated by tab
- 's3path' does not include the `s3://bucket` part (i.e. only the key)

**Output:** One line for each file in the following format
```
CHECK \t $ok \t $etagExpected \t $etagCalc \t $fileLocal \t $fileS3 \n
```
Meaning:
- `$ok`: Whether ETag expected and ETag calculated match
- `$etagExpected`: Expected ETag (i.e. the one retrieved from AWS S3
- `$etagCalc`: Calculated ETag using `java -jar s3checksum.jar file`
- `$fileLocal`: Path to local file
- `$fileS3`: URL for S3 file (includes bucket name)

