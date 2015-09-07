import java.io.*;

/**
 * A utility class for writing bit streams.
 * @author Ridge Shrubsall (21112211)
 */
public class BitOutputStream {
	/**
	 * The base output stream.
	 */
	private DataOutputStream out;
	
	/**
	 * The number of bytes written.
	 */
	private long count;
	
	/**
	 * Create a new bit output stream.
	 * @param out The output stream
	 */
	public BitOutputStream(OutputStream out) {
		this.out = new DataOutputStream(out);
		count = 0;
	}
	
	/**
	 * Return the number of bytes written.
	 * @return The byte count
	 */
	public long getCount() {
		return count;
	}
	
	/**
	 * Write an array of bytes.
	 * @param b The byte array
	 */
	public void write(byte[] b) throws IOException {
		write(b, 0, b.length);
	}
	
	/**
	 * Write an array of bytes.
	 * @param b The byte array
	 * @param off The starting offset
	 * @param len The number of bytes to write
	 */
	public void write(byte[] b, int off, int len) throws IOException {
		out.write(b, off, len);
		count += len;
	}
	
	/**
	 * Write a single byte.
	 * @param v The byte value
	 */
	public void writeByte(int v) throws IOException {
		out.writeByte(v);
		count++;
	}
	
	/**
	 * Write a two-byte short.
	 * @param v The short value
	 */
	public void writeShort(int v) throws IOException {
		writeByte((byte) (v));
		writeByte((byte) (v >> 8));
	}
	
	/**
	 * Write a four-byte integer.
	 * @param v The integer value
	 */
	public void writeInt(int v) throws IOException {
		writeByte((byte) (v));
		writeByte((byte) (v >> 8));
		writeByte((byte) (v >> 16));
		writeByte((byte) (v >> 24));
	}
	
	/**
	 * Write a four-byte unsigned integer.
	 * @param v The long value
	 */
	public void writeUnsignedInt(long v) throws IOException {
		writeByte((byte) (v));
		writeByte((byte) (v >> 8));
		writeByte((byte) (v >> 16));
		writeByte((byte) (v >> 24));
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
	 * Write the given bit sequence.
	 * @param v The value
	 * @param n The number of bits
	 */
	public void writeBits(int v, int n) throws IOException {
		for (int m = 0; m < n; m++) {
			bitVal |= ((v >>> m) & 1) << bitPos;
			bitPos++;
			if (bitPos > 7) {
				writeByte(bitVal);
				bitVal = 0;
				bitPos = 0;
			}
		}
	}
	
	/**
	 * Write the reverse of the given bit sequence.
	 * @param v The value
	 * @param n The number of bits
	 */
	public void writeBitsR(int v, int n) throws IOException {
		for (int m = n - 1; m >= 0; m--) {
			bitVal |= ((v >>> m) & 1) << bitPos;
			bitPos++;
			if (bitPos > 7) {
				writeByte(bitVal);
				bitVal = 0;
				bitPos = 0;
			}
		}
	}
	
	/**
	 * Flush the bit queue.
	 */
	public void flushBits() throws IOException {
		if (bitPos > 0) {
			writeBits(0xff, 8 - bitPos);
		}
	}
}
