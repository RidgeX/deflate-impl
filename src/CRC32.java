/**
 * Implements a 32-bit cyclic redundancy checker.
 * @author Ridge Shrubsall (21112211)
 */
public class CRC32 {
	/**
	 * A precomputed CRC table of all 8-bit messages.
	 */
	private static final int[] CRC_TABLE = new int[256];
	static {
		for (int n = 0; n < 256; n++) {
			int c = n;
			for (int k = 0; k < 8; k++) {
				if ((c & 1) == 1) {
					c = (c >>> 1) ^ 0xedb88320;
				} else {
					c >>>= 1;
				}
			}
			CRC_TABLE[n] = c;
		}
	}
	
	
	/**
	 * The current value of the checksum.
	 */
	private int crc;
	
	/**
	 * Create a new checksum.
	 */
	public CRC32() {
		crc = 0xffffffff;
	}
	
	/**
	 * Return the current value of the checksum.
	 * @return The current CRC value
	 */
	public int getValue() {
		return ~crc;
	}
	
	/**
	 * Update the current checksum with the given byte.
	 * @param b The byte
	 */
	public void update(byte b) {
		crc = (crc >>> 8) ^ CRC_TABLE[(crc ^ b) & 0xff];
	}
	
	/**
	 * Update the current checksum with the given bytes.
	 * @param b The byte array
	 */
	public void update(byte[] b) {
		update(b, 0, b.length);
	}
	
	/**
	 * Update the current checksum with the given bytes.
	 * @param b The byte array
	 * @param off The starting offset
	 * @param len The number of bytes
	 */
	public void update(byte[] b, int off, int len) {
		for (int i = off; i < off + len; i++) {
			crc = (crc >>> 8) ^ CRC_TABLE[(crc ^ b[i]) & 0xff];
		}
	}
}
