import java.io.*;

/**
 * This interface describes the operations to compress and decompress
 * a file using a variety of algorithms.
 * @author Tim French
 */
public interface Compressor {
	/**
	 * Reads a series of bytes from an input stream and
	 * executes a compression algorithm over those bytes, writing
	 * the compressed data to the specified output stream.
	 * @param in The input stream for the data
	 * @param out The output stream for writing the compressed data
	 * @return A string reporting information to be logged
	 */
	public abstract String compress(InputStream in, OutputStream out);
	
	/**
	 * Reads a series of bytes from a compressed stream and
	 * executes a decompression algorithm over those bytes, writing
	 * the decompressed data to the specified output stream.
	 * @param in The input stream for the compressed data
	 * @param out The output stream for writing the decompressed data
	 * @return A string reporting information to be logged
	 */
	public abstract String decompress(InputStream in, OutputStream out);
}
