/**
 * Implements a Lempel-Ziv sliding window dictionary.
 * @author Ridge Shrubsall (21112211)
 */
public class LZWindow {
	/**
	 * The minimum match length.
	 */
	private static final int MIN_MATCH = 3;
	
	/**
	 * The maximum match length.
	 */
	private static final int MAX_MATCH = 258;
	
	
	/**
	 * The maximum size of the window.
	 */
	private int maxSize;
	
	/**
	 * The mask for the window position.
	 */
	private int mask;
	
	/**
	 * The window buffer.
	 */
	private byte[] dict;
	
	/**
	 * The current position in the window.
	 */
	private int pos;
	
	/**
	 * The current size of the window.
	 */
	private int size;
	
	/**
	 * Create a new window.
	 */
	public LZWindow(int s) {
		if (Integer.bitCount(s) != 1) {
			throw new AssertionError("Window size must be a power of 2");
		}
		maxSize = s;
		mask = maxSize - 1;
		dict = new byte[maxSize];
		pos = 0;
		size = 0;
	}
	
	/**
	 * Add a byte to the window.
	 * @param b The byte to be added
	 */
	public void add(byte b) {
		dict[pos] = b;
		pos = (pos + 1) & mask;
		if (size < maxSize) size++;
	}
	
	/**
	 * Add an array of bytes to the window.
	 * @param b The bytes to be added
	 */
	public void add(byte[] b) {
		add(b, 0, b.length);
	}
	
	/**
	 * Add an array of bytes to the window.
	 * @param b The bytes to be added
	 * @param off The starting offset
	 * @param len The number of bytes
	 */
	public void add(byte[] b, int off, int len) {
		for (int i = off; i < off + len; i++) {
			add(b[i]);
		}
	}
	
	/**
	 * Find a previous match for the given bytes.
	 * @param b The data array
	 * @param off The starting offset
	 * @param len The number of bytes
	 * @return A distance/length pair
	 */
	public LZPair find(byte[] buffer, int off, int len) {
		// Check if the window is empty
		if (size == 0) return null;
		
		// Look backwards through the window
		for (int i = 1; i <= size; i++) {
			int start = (pos - i) & mask;
			int matchLength = 0;
			int x = start;                                  // Offsets into window and data buffers
			int y = off;
			while (matchLength < MAX_MATCH && y < len) {    // Determine the match length at distance 'i'
				if (dict[x] != buffer[y]) break;
				matchLength++;
				x = (x + 1) & mask;
				if (x == pos) x = start;                    // Window wraps around past current position
				y++;
			}
			if (matchLength >= MIN_MATCH) {                 // Return first match
				return new LZPair(i, matchLength);
			}
		}
		return null;
	}
	
	/**
	 * Copy a sequence of bytes from the window.
	 * @param dist The distance to go back
	 * @param len The number of bytes to copy
	 * @return The byte sequence
	 */
	public byte[] getBytes(int dist, int len) {
		byte[] b = new byte[len];
		
		int start = (pos - dist) & mask;
		int x = start;                                      // Offset into window buffer
		for (int i = 0; i < len; i++) {
			b[i] = dict[x];                                 // Copy bytes from window
			x = (x + 1) & mask;
			if (x == pos) x = start;                        // Window wraps around past current position
		}
		
		return b;
	}
}
