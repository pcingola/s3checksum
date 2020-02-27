package com.astrazeneca.s3checksum;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Calculate ETag checksum as performed by Amazon
 *
 * References:
 * 		https://docs.aws.amazon.com/cli/latest/topic/s3-faq.html
 * 		https://stackoverflow.com/questions/1775816/how-to-get-the-md5sum-of-a-file-on-amazons-s3
 * 		https://www.savjee.be/2015/10/Verifying-Amazon-S3-multi-part-uploads-with-ETag-hash/
 *
 * @author Pablo Cingolani
 */
public class S3Checksum {

	public static int M = 1 * 1024 * 1024;
	public static int CHUNK_SIZE_M = 8;

	/**
	 * Create a directory walker / fineder. We cannot use Java's walkers because they fail on read errors
	 */
	static List<File> listFiles(File file, int minFileSize) {
		List<File> files = new ArrayList<>();
		for (File f : file.listFiles()) {
			// Ignore if we cannot read
			if (!f.canRead()) continue;
			// Ignore links
			if (Files.isSymbolicLink(f.toPath())) continue;

			if (f.isDirectory()) {
				// Recurse directories
				files.addAll(listFiles(f, minFileSize));
			} else if (f.isFile()) {
				// Add file
				if (f.length() >= minFileSize) files.add(f);
			}
		}
		return files;
	}

	public static void main(String[] args) throws Exception {
		int chunkSize = CHUNK_SIZE_M * M;
		String fileList = null;
		List<String> files = new LinkedList<>();
		int minFileSize = -1;

		if (args.length <= 0) {
			System.err.println("Usage: s3Checksum [-chunkSize size_in_MB] [-f files.txt] [file1 ... fileN]");
			System.err.println("Command line options:");
			System.err.println("	-chunkSize <size>   : Size in MB of each 'chunk' of MD5. Default " + CHUNK_SIZE_M);
			System.err.println("	-minFileSize <size> : Size in MB for a file to be checked. Default " + 0);
			System.err.println("	-f <files.txt>      : A txt file containing a list of files (one file per line)");
			System.exit(0);
		}

		// Parse command line options
		for (int i = 0; i < args.length; i++) {
			String arg = args[i];
			if (arg.equals("-chunkSize")) {
				chunkSize = Integer.parseInt(args[++i]) * M;
				// System.err.println("Setting chunk size to: " + chunkSize);
			} else if (arg.equals("-minFileSize")) {
				minFileSize = Integer.parseInt(args[++i]) * M;
				// System.err.println("Setting minimum file size to: " + minFileSize);
			} else if (arg.equals("-f")) {
				fileList = args[++i];
			} else {
				files.add(arg);
			}
		}

		// Is there a file list?
		if (fileList != null) {
			Files.lines(Paths.get(fileList)).forEach(l -> files.add(l));
		}

		// Calculate ETag for every file
		for (String fileName : files) {
			File file = new File(fileName);

			if (file.isFile()) {
				S3ChecksumFile s3Checksum = new S3ChecksumFile(file, chunkSize);
				s3Checksum.checksum();
				System.out.println(s3Checksum);
			} else if (file.isDirectory()) {
				List<File> dirFiles = listFiles(file, minFileSize);

				for (File f : dirFiles) {
					S3ChecksumFile s3Checksum = new S3ChecksumFile(f, chunkSize);
					s3Checksum.checksum();
					System.out.println(s3Checksum);
				}
			}

		}
	}

}

/**
 * Calculate e-tag for a file
 */
class S3ChecksumFile {

	int chunkSize;
	String etag;
	File file;

	public S3ChecksumFile(File file, int chunkSize) {
		this.file = file;
		this.chunkSize = chunkSize;
	}

	/**
	 * Read file and calculate MD5 sum
	 */
	public void checksum() {
		try {
			byte digests[] = new byte[0];
			byte[] contents = new byte[chunkSize];
			InputStream inStream = new FileInputStream(file);

			// One digest for each "chunk"
			int countChunks;
			for (countChunks = 0; true; countChunks++) {
				MessageDigest md = newMd5();
				int read = 0, pos = 0;
				while ((read = inStream.read(contents, pos, contents.length - pos)) >= 1)
					pos += read;

				if (pos > 0) md.update(contents, 0, pos);

				byte[] digest = md.digest();
				digests = concatenate(digests, digest);

				if (read < 0) break;
			}
			inStream.close();

			// Create Etag
			etag = etag(digests, countChunks + 1);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Concatenate byte arrays
	 */
	byte[] concatenate(byte[] b1, byte[] b2) {
		byte[] b = Arrays.copyOf(b1, b1.length + b2.length);
		for (int i = b1.length, j = 0; j < b2.length; i++, j++)
			b[i] = b2[j];
		return b;
	}

	/**
	 * Convert the digest to hex string
	 */
	String digest2hex(byte[] digest) {
		StringBuffer sb = new StringBuffer();
		for (byte b : digest) {
			sb.append(String.format("%02x", b & 0xff));
		}
		return sb.toString();
	}

	/**
	 * Create an ETag from all checksums
	 * @return Etag string as shown in S3 header
	 */
	String etag(byte[] digests, int countChunks) {
		MessageDigest md = newMd5();
		md.update(digests);
		byte[] digest = md.digest();
		String etagChecksum = digest2hex(digest);
		String etagDash = (countChunks > 1 ? "-" + countChunks : "");
		return etagChecksum + etagDash;
	}

	public String getEtag() {
		return etag;
	}

	MessageDigest newMd5() {
		try {
			return MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public String toString() {
		return etag + "\t" + file.length() + "\t" + file.getAbsolutePath();
	}

}
