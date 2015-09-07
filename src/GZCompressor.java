import java.io.*;
import java.text.NumberFormat;

/**
 * Implements the gzip file format for storing DEFLATE-compressed streams.
 * @author Ridge Shrubsall (21112211)
 */
public class GZCompressor implements Compressor {
	/*
	 * Compression methods
	 */
	public static final int M_DEFLATE = 8;
	
	/*
	 * Header flags
	 */
	public static final int F_TEXT    = 1;
	public static final int F_HCRC    = 2;
	public static final int F_EXTRA   = 4;
	public static final int F_NAME    = 8;
	public static final int F_COMMENT = 16;
	
	
	/**
	 * The output log.
	 */
	private StringBuffer log;
	
	/**
	 * A number formatter for the output log.
	 */
	private static NumberFormat fmt = NumberFormat.getNumberInstance();
	
	/**
	 * The name of the input file.
	 */
	private String fileName;
	
	/**
	 * The size of the input file.
	 */
	private long fileSize;
	
	/**
	 * Show percentage progress.
	 */
	private boolean showProgress;
	
	/**
	 * The last percentage calculated.
	 */
	private long lastPercent;
	
	/**
	 * Create a new GZCompressor.
	 * @param fileName The input filename
	 * @param fileSize The input filesize
	 * @param showProgress Show progress
	 */
	public GZCompressor(String fileName, long fileSize, boolean showProgress) {
		this.fileName = fileName;
		this.fileSize = fileSize;
		this.showProgress = showProgress;
		lastPercent = -1;
	}
	
	/**
	 * Update the progress of the current task.
	 * @param read The number of bytes processed so far
	 */
	public void updateProgress(long read) {
		if (showProgress) {
			long percent = read * 100 / fileSize;
			if (percent != lastPercent) {
				System.out.println(String.format("%d%%", percent));
				lastPercent = percent;
			}
		}
	}
	
	/**
	 * Reads a series of bytes from an input stream and
	 * executes a compression algorithm over those bytes, writing
	 * the compressed data to the specified output stream.
	 * @param in The input stream for the data
	 * @param out The output stream for writing the compressed data
	 * @return A string reporting information to be logged
	 */
	public String compress(InputStream in, OutputStream out) {
		log = new StringBuffer();
		System.out.println(String.format("Compressing %s (%s bytes)", fileName, fmt.format(fileSize)));
		
		BitInputStream input = new BitInputStream(in);
		BitOutputStream output = new BitOutputStream(out);
		try {
			// Write header
			output.writeByte(0x1f);
			output.writeByte(0x8b);
			output.writeByte(M_DEFLATE);
			output.writeByte(F_NAME);
			for (int i = 0; i < 6; i++) {
				output.writeByte(0);
			}
			
			// Write filename
			output.write(fileName.getBytes());
			output.writeByte(0);
			
			// Compress and write data
			Deflater deflater = new Deflater(this, input, output);
			long size = deflater.process() + 8;
			
			// Write footer
			output.writeInt(deflater.getCRCValue());
			output.writeUnsignedInt(fileSize);
			
			// Log statistics
			long diff = fileSize - size;
			double ratio = (1 - ((double) size / fileSize)) * 100;
			if (diff > 0) {
				log.append(String.format("Size reduced by %s bytes (%.1f%% total saving)", fmt.format(diff), ratio));
			} else {
				log.append(String.format("Size increased by %s bytes", fmt.format(-diff)));
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
		return log.toString();
	}
	
	/**
	 * Reads a series of bytes from a compressed stream and
	 * executes a decompression algorithm over those bytes, writing
	 * the decompressed data to the specified output stream.
	 * @param in The input stream for the compressed data
	 * @param out The output stream for writing the decompressed data
	 * @return A string reporting information to be logged
	 */
	public String decompress(InputStream in, OutputStream out) {
		log = new StringBuffer();
		System.out.println(String.format("Decompressing %s (%s bytes)", fileName, fmt.format(fileSize)));
		
		BitInputStream input = new BitInputStream(in);
		BitOutputStream output = new BitOutputStream(out);
		try {
			// Read header
			int id1 = input.readByte();
			int id2 = input.readByte();
			if (id1 != 0x1f || id2 != 0x8b) {
				throw new AssertionError("Invalid magic");
			}
			int method = input.readByte();
			if (method != M_DEFLATE) {
				throw new AssertionError("Unsupported compression method");
			}
			int flags = input.readByte();
			if ((flags & (F_HCRC | F_EXTRA | F_COMMENT)) != 0) {
				throw new AssertionError("Unsupported flags");
			}
			input.skipBytes(6);
			
			// Skip over filename if present
			if ((flags & F_NAME) != 0) {
				int b;
				do {
					b = input.readByte();
				} while (b != 0);
			}
			
			// Read and decompress data
			Inflater inflater = new Inflater(this, input, output);
			long size = inflater.process();
			
			// Read footer
			int f_crc = input.readInt();
			long f_size = input.readUnsignedInt();
			updateProgress(input.getCount());
			
			// Verify data
			if (size != f_size) {
				throw new AssertionError(String.format("Size mismatch, expected = %d, actual = %d", f_size, size));
			}
			int crc = inflater.getCRCValue();
			if (crc != f_crc) {
				throw new AssertionError(String.format("CRC mismatch, expected = %08X, actual = %08X", f_crc, crc));
			}
			
			// Log statistics
			long diff = size - fileSize;
			double ratio = (1 - ((double) fileSize / size)) * 100;
			if (diff > 0) {
				log.append(String.format("Size increased by %s bytes (%.1f%% space saving)", fmt.format(diff), ratio));
			} else {
				log.append(String.format("Size reduced by %s bytes", fmt.format(-diff)));
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
		return log.toString();
	}
	
	/**
	 * Main method to compress/decompress from command line.
	 * Options are 'c' to compress, 'd' to decompress, 'v' for verbose,
	 * followed by the names of the input and output files.
	 * e.g. java GZCompressor -vc file.txt file.gz
	 * will create a compressed file 'file.gz' corresponding to 'file.txt'
	 * and give verbose output.
	 */
	public static void main(String[] args) {
		// Check number of arguments
		if (args.length != 3) usage();
		
		// Parse flags
		boolean verbose = args[0].contains("v");
		boolean showProgress = args[0].contains("p");
		boolean compress = args[0].contains("c");
		boolean decompress = args[0].contains("d");
		if (!(compress ^ decompress)) usage();
		
		try {
			// Open streams
			File inFile = new File(args[1]);
			FileInputStream in = new FileInputStream(inFile);
			File outFile = new File(args[2]);
			FileOutputStream out = new FileOutputStream(outFile);
			
			// Create compressor instance
			GZCompressor gz = new GZCompressor(inFile.getName(), inFile.length(), showProgress);
			String info;
			
			// Perform compression/decompression
			long startTime = System.currentTimeMillis();
			if (compress) {
				info = gz.compress(in, out);
			} else {
				info = gz.decompress(in, out);
			}
			long endTime = System.currentTimeMillis();
			
			// Print statistics
			if (verbose) {
				System.out.println(info);
				System.out.println(String.format("Took %.3f seconds", (endTime - startTime) / 1000.0));
				System.out.println();
			}
			
			// Close streams
			in.close();
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Print usage and quit.
	 */
	private static void usage() {
		System.out.println("Usage:");
		System.out.println("\tjava GZCompressor -vpcd [in-file] [out-file]");
		System.out.println("Options:");
		System.out.println("\t-v  verbose mode");
		System.out.println("\t-p  show progress");
		System.out.println("\t-c  compress");
		System.out.println("\t-d  decompress");
		System.exit(1);
	}
}
