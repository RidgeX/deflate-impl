/**
 * Implements a Lempel-Ziv distance / length pair.
 * @author Ridge Shrubsall (21112211)
 */
public class LZPair {
	/**
	 * The distance value.
	 */
	public int dist;
	/**
	 * The distance symbol.
	 */
	public int distSymbol;
	/**
	 * The distance bits.
	 */
	public int distBits;
	/**
	 * The number of distance bits.
	 */
	public int distNumBits;
	
	
	/**
	 * The length value.
	 */
	public int len;
	/**
	 * The length symbol.
	 */
	public int lenSymbol;
	/**
	 * The length bits.
	 */
	public int lenBits;
	/**
	 * The number of length bits.
	 */
	public int lenNumBits;
	
	
	/**
	 * Create a new distance / length pair.
	 * @param dist The distance value
	 * @param len The length value
	 */
	public LZPair(int dist, int len) {
		this.dist = dist;
		this.len = len;
		
		distSymbol = -1;
		lenSymbol = -1;
		for (int i = 0; i < 29; i++) {
			if (len <= lenUpper[i]) {
				lenSymbol = 257 + i;
				lenBits = len - lenLower[i];
				lenNumBits = lenNBits[i];
				break;
			}
		}
		for (int i = 0; i < 30; i++) {
			if (dist <= distUpper[i]) {
				distSymbol = i;
				distBits = dist - distLower[i];
				distNumBits = distNBits[i];
				break;
			}
		}
		
		if (distSymbol == -1 || lenSymbol == -1) {
			throw new AssertionError("Couldn't find distance/length symbol");
		}
	}
	
	
	/*
	 * Ranges for Length/distance symbols
	 * (see RFC 1951, section 3.2.5)
	 */
	public static final int[] lenLower;
	public static final int[] lenUpper;
	public static final int[] lenNBits;
	public static final int[] distLower;
	public static final int[] distUpper;
	public static final int[] distNBits;
	static {
		// Generate length ranges
		lenLower = new int[29];
		lenUpper = new int[29];
		lenNBits = new int[29];
		for (int i = 0; i <= 7; i++) {
			lenLower[i] = 3 + i;
			lenUpper[i] = lenLower[i];
			lenNBits[i] = 0;
		}
		for (int i = 8; i <= 27; i++) {
			int j = (i - 8) % 4;
			int k = (i - 8) / 4;
			lenLower[i] = ((4 + j) << (k + 1)) + 3;
			lenUpper[i] = lenLower[i] + (1 << (k + 1)) - 1;
			lenNBits[i] = k + 1;
		}
		lenUpper[27]--;
		lenLower[28] = 258;
		lenUpper[28] = 258;
		lenNBits[28] = 0;
		
		// Generate distance ranges
		distLower = new int[30];
		distUpper = new int[30];
		distNBits = new int[30];
		for (int i = 0; i <= 3; i++) {
			distLower[i] = 1 + i;
			distUpper[i] = distLower[i];
			distNBits[i] = 0;
		}
		for (int i = 4; i <= 29; i++) {
			int j = (i - 4) % 2;
			int k = (i - 4) / 2;
			distLower[i] = ((2 + j) << (k + 1)) + 1;
			distUpper[i] = distLower[i] + (1 << (k + 1)) - 1;
			distNBits[i] = k + 1;
		}
	}
	
	/**
	 * Main method for printing symbol information.
	 */
	public static void main(String[] args) {
		System.out.println("Code\tBits\tLen");
		for (int i = 0; i < 29; i++) {
			if (lenLower[i] == lenUpper[i]) {
				System.out.println(String.format("%d\t%d\t%d", 257 + i, lenNBits[i], lenLower[i]));
			} else {
				System.out.println(String.format("%d\t%d\t%d - %d", 257 + i, lenNBits[i], lenLower[i], lenUpper[i]));
			}
		}
		System.out.println();
		System.out.println("Code\tBits\tDist");
		for (int i = 0; i < 30; i++) {
			if (distLower[i] == distUpper[i]) {
				System.out.println(String.format("%d\t%d\t%d", i, distNBits[i], distLower[i]));
			} else {
				System.out.println(String.format("%d\t%d\t%d - %d", i, distNBits[i], distLower[i], distUpper[i]));
			}
		}
	}
}
