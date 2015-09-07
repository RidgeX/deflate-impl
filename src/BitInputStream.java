import java.io.*;

/**
 * A utility class for reading bit streams.
 * @author Ridge Shrubsall (21112211)
 */
public class BitInputStream {
	/**
	 * The base input stream.
	 */
	private DataInputStream in;
	
	/**
	 * The number of bytes read.
	 */
	private long count;
	
	/**
	 * Create a new bit input stream.
	 * @param in The input stream
	 */
	public BitInputStream(InputStream in) {
		this.in = new DataInputStream(in);
		count = 0;
	}
	
	/**
	 * Return the number of bytes read.
	 * @return The byte count
	 */
	public long getCount() {
		return count;
	}
	
	/**
	 * Read an array of bytes.
	 * @param b The byte array
	 */
	public int read(byte[] b) throws IOException {
		return read(b, 0, b.length);
	}
	
	/**
	 * Read an array of bytes.
	 * @param b The byte array
	 * @param off The starting offset
	 * @param len The number of bytes to read
	 * @return The number of bytes read
	 */
	public int read(byte[] b, int off, int len) throws IOException {
		int c = in.read(b, off, len);
		if (c > 0) count += c;
		return c;
	}
	
	/**
	 * Read a single byte.
	 * @return The byte value
	 */
	public int readByte() throws IOException {
		count++;
		return in.readUnsignedByte();
	}
	
	/**
	 * Read a two-byte short.
	 * @return The short value
	 */
	public int readShort() throws IOException {
		return (readByte() | readByte() << 8);
	}
	
	/**
	 * Read a four-byte integer.
	 * @return The integer value
	 */
	public int readInt() throws IOException {
		return (readByte() | readByte() << 8 | readByte() << 16 | readByte() << 24);
	}
	
	/**
	 * Read a four-byte unsigned integer.
	 * @return The long value
	 */
	public long readUnsignedInt() throws IOException {
		return (readInt() & 0xffffffffL);
	}
	
	/**
	 * Skip the next n bytes.
	 * @param n The number of bytes to be skipped
	 * @return The number of bytes skipped
	 */
	public int skipBytes(int n) throws IOException {
		count += n;
		return in.skipBytes(n);
	}
	
	
	/**
	 * A queue of bits.
	 */
	public int bitVal = 0;
	
	/**
	 * The current queue position.
	 */
	public int bitPos = 0;
	
	/**
	 * Read a sequence of bits.
	 * @param n The number of bits
	 * @return The value
	 */
	public int readBits(int n) throws IOException {
		int v = 0;
		for (int m = 0; m < n; m++) {
			if (bitPos == 0) {
				bitVal = readByte();
			}
			v |= ((bitVal >>> bitPos) & 1) << m;
			bitPos = (bitPos + 1) & 7;
		}
		return v;
	}
	
	/**
	 * Clear the bit queue.
	 */
	public void clearBits() {
		bitVal = 0;
		bitPos = 0;
	}
}
