import java.io.*;
import java.util.*;

/**
 * Inflates a given stream of compressed data.
 * @author Ridge Shrubsall (21112211)
 */
public class Inflater {
	/*
	 * Debug logging
	 */
	private static boolean DEBUG = false;
	
	/*
	 * Constant values
	 */
	private static final int END_OF_BLOCK = 256;
	private static final int[] LEN_ORDER = new int[] {
		16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15
	};
	private static final int N_LITERALS = 286;
	private static final int N_DISTANCES = 30;
	private static final int N_LENGTHS = 19;
	private static final int WINDOW_SIZE = 32768;
	
	
	/**
	 * The compressor instance.
	 */
	private GZCompressor gz;
	
	/**
	 * The base input stream.
	 */
	private BitInputStream in;
	
	/**
	 * The base output stream.
	 */
	private BitOutputStream out;
	
	/**
	 * The current checksum.
	 */
	private CRC32 crc;
	
	/**
	 * The sliding window dictionary.
	 */
	private LZWindow window;
	
	/**
	 * The current set of literal codes.
	 */
	private List<Integer> litCodes;
	private Map<Integer, List<Integer>> litCodeMap;
	
	/**
	 * The current set of distance codes.
	 */
	private List<Integer> distCodes;
	private Map<Integer, List<Integer>> distCodeMap;
	
	/**
	 * The current set of length codes.
	 */
	private List<Integer> lenCodes;
	private Map<Integer, List<Integer>> lenCodeMap;
	
	/**
	 * Create a new Inflater.
	 * @param gz The compressor instance
	 * @param in The input stream
	 * @param out The output stream
	 */
	public Inflater(GZCompressor gz, BitInputStream in, BitOutputStream out) {
		this.gz = gz;
		this.in = in;
		this.out = out;
		crc = new CRC32();
		window = new LZWindow(WINDOW_SIZE);
	}
	
	/**
	 * Process the current stream.
	 * @return The size of the uncompressed data
	 */
	public long process() throws IOException {
		// Start reading blocks
		while (true) {
			int bfinal = in.readBits(1);                         // Read final bit
			int btype = in.readBits(2);                          // Read block type
			
			// Process block
			if (btype == 0) {
				in.clearBits();
				processUncompressedBlock();
			} else if (btype == 1) {
				loadDefaultCodes();
				processHuffmanBlock();
			} else if (btype == 2) {
				readCodes();
				processHuffmanBlock();
			} else {
				throw new AssertionError("Invalid block type");
			}
			
			// Update progress
			gz.updateProgress(in.getCount());
			
			// Break on final block
			if (bfinal == 1) break;
		}
		
		// Finished
		return out.getCount();
	}
	
	/**
	 * Process a block of uncompressed data.
	 */
	private void processUncompressedBlock() throws IOException {
		// Read block length
		int len = in.readShort();
		int nlen = in.readShort() ^ 0xffff;
		if (nlen != len) {
			throw new AssertionError("Invalid block length");
		}
		
		// Read data
		byte[] b = new byte[len];
		in.read(b, 0, len);
		
		// Update checksum and window
		crc.update(b, 0, len);
		window.add(b, 0, len);
		
		// Write data to output file
		out.write(b, 0, len);
	}
	
	/**
	 * Load the default set of Huffman codes.
	 */
	private void loadDefaultCodes() {
		// Build literal codes
		litCodes = new ArrayList<Integer>();
		for (int i = 0; i < N_LITERALS; i++) {
			litCodes.add(HuffmanTable.LIT.code[i]);
		}
		litCodeMap = buildCodeMap(litCodes, HuffmanTable.LIT.codeLen);
		
		// Build distance codes
		distCodes = new ArrayList<Integer>();
		for (int i = 0; i < N_DISTANCES; i++) {
			distCodes.add(HuffmanTable.DIST.code[i]);
		}
		distCodeMap = buildCodeMap(distCodes, HuffmanTable.DIST.codeLen);
	}
	
	/**
	 * Read a set of Huffman codes from the input stream.
	 */
	private void readCodes() throws IOException {
		int numUsedLiterals = 257 + in.readBits(5);
		int numUsedDistances = 1 + in.readBits(5);
		int numUsedLengths = 4 + in.readBits(4);
		
		// Read length codelengths
		int[] lenCodeLen = new int[N_LENGTHS];
		for (int i = 0; i < numUsedLengths; i++) {
			lenCodeLen[LEN_ORDER[i]] = in.readBits(3);
		}
		
		// Build length codes
		lenCodes = buildCodes(lenCodeLen);
		lenCodeMap = buildCodeMap(lenCodes, lenCodeLen);
		
		// Unpack literal/distance codelengths
		int[] lengths = new int[numUsedLiterals + numUsedDistances];
		for (int i = 0; i < numUsedLiterals + numUsedDistances; i++) {
			int sym = readSymbol(lenCodes, lenCodeMap);
			if (sym == 16) {                                     // Repeat previous length 'n' times
				int n = 3 + in.readBits(2);                      // 16 -> 3 to 6 repeats
				for (int j = 0; j < n; j++) {
					 lengths[i + j] = lengths[i - 1];
				}
				i += (n - 1);
			} else if (sym == 17 || sym == 18) {                 // Repeat zero length 'n' times
				int n;
				if (sym == 17) {
					n = 3 + in.readBits(3);                      // 17 -> 3 to 10 repeats
				} else {
					n = 11 + in.readBits(7);                     // 18 -> 11 to 138 repeats
				}
				for (int j = 0; j < n; j++) {
					 lengths[i + j] = 0;
				}
				i += (n - 1);
			} else {
				lengths[i] = sym;
			}
		}
		
		// Get literal codelengths
		int[] litCodeLen = new int[N_LITERALS];
		System.arraycopy(lengths, 0, litCodeLen, 0, numUsedLiterals);
		
		// Build literal codes
		litCodes = buildCodes(litCodeLen);
		litCodeMap = buildCodeMap(litCodes, litCodeLen);
		
		// Get distance codelengths
		int[] distCodeLen = new int[N_DISTANCES];
		System.arraycopy(lengths, numUsedLiterals, distCodeLen, 0, numUsedDistances);
		
		// Build distance codes
		distCodes = buildCodes(distCodeLen);
		distCodeMap = buildCodeMap(distCodes, distCodeLen);
		
		// Print codes (for debugging)
		if (DEBUG) {
			System.out.println("Length codes:");
			for (int i = 0; i < N_LENGTHS; i++) {
				if (lenCodeLen[i] > 0) {
					String code = String.format("%" + lenCodeLen[i] + "s", Integer.toBinaryString(lenCodes.get(i)));
					code = code.replace(' ', '0');
					System.out.println(i + "\t" + code);
				}
			}
			System.out.println("Literal codes:");
			for (int i = 0; i < N_LITERALS; i++) {
				if (litCodeLen[i] > 0) {
					String code = String.format("%" + litCodeLen[i] + "s", Integer.toBinaryString(litCodes.get(i)));
					code = code.replace(' ', '0');
					System.out.println(i + "\t" + code);
				}
			}
			System.out.println("Distance codes:");
			for (int i = 0; i < N_DISTANCES; i++) {
				if (distCodeLen[i] > 0) {
					String code = String.format("%" + distCodeLen[i] + "s", Integer.toBinaryString(distCodes.get(i)));
					code = code.replace(' ', '0');
					System.out.println(i + "\t" + code);
				}
			}
		}
	}
	
	/**
	 * Process a block of Huffman-compressed data.
	 */
	private void processHuffmanBlock() throws IOException {
		while (true) {
			// Read a literal symbol
			int litSym = readSymbol(litCodes, litCodeMap);
			
			// Does this symbol represent a normal byte?
			if (litSym < END_OF_BLOCK) {
				// Get byte value
				byte b = (byte) litSym;
				
				// Update checksum and window
				crc.update(b);
				window.add(b);
				
				// Write byte to output file
				out.writeByte(b);
			} else if (litSym == END_OF_BLOCK) {
				// End of block reached
				break;
			} else {
				// Get length symbol and value
				int lenSym = litSym - 257;
				int len = LZPair.lenLower[lenSym] + in.readBits(LZPair.lenNBits[lenSym]);
				
				// Get distance symbol and value
				int distSym = readSymbol(distCodes, distCodeMap);
				int dist = LZPair.distLower[distSym] + in.readBits(LZPair.distNBits[distSym]);
				
				// Get bytes from window
				byte[] b = window.getBytes(dist, len);
				
				// Update checksum and window
				crc.update(b);
				window.add(b);
				
				// Write bytes to output file
				out.write(b);
			}
		}
	}
	
	/**
	 * Read a symbol from the input stream.
	 * @param codes The code list to use
	 * @param codeMap The code map to use
	 * @return The decoded symbol
	 */
	private int readSymbol(List<Integer> codes, Map<Integer, List<Integer>> codeMap) throws IOException {
		int code = 0;
		int codeLen = 0;
		int index = -1;
		
		// Start reading bits
		do {
			if (codeLen == 15) {
				throw new AssertionError("Couldn't find code");
			}
			
			// Read one bit
			code <<= 1;
			code |= in.readBits(1);
			codeLen++;
			
			// Check if we have a matching code
			List<Integer> codeList = codeMap.get(codeLen);
			if (codeList != null) {
				index = codeList.indexOf(code);
			}
		} while (index == -1);
		
		// Return the index of the code (which equals the symbol value)
		return codes.indexOf(code);
	}
	
	/**
	 * Build a list of canonical codes from the given codelengths.
	 * @param codeLen The codelength array
	 * @return The list of codes
	 */
	public List<Integer> buildCodes(int[] codeLen) {
		int n = codeLen.length;
		Integer[] codes = new Integer[n];
		
		// Find used codelengths
		Set<Integer> lengthSet = new TreeSet<Integer>();
		for (int i = 0; i < n; i++) {
			if (codeLen[i] > 0) {
				lengthSet.add(codeLen[i]);
			}
		}
		
		// Build the codes
		int nextCode = 0;
		int lastShift = 0;
		for (Integer length : lengthSet) {
			nextCode <<= (length - lastShift);
			lastShift = length;
			
			for (int i = 0; i < n; i++) {
				if (codeLen[i] == length) {
					codes[i] = nextCode++;
				}
			}
		}
		
		return Arrays.asList(codes);
	}
	
	/**
	 * Build a map of the codes grouped by codelength.
	 * @param codes The list of codes
	 * @param codeLen The codelength array
	 * @return The code map
	 */
	public Map<Integer, List<Integer>> buildCodeMap(List<Integer> codes, int[] codeLen) {
		int n = codeLen.length;
		Map<Integer, List<Integer>> codeMap = new TreeMap<Integer, List<Integer>>();
		
		// Build the codemap
		for (int i = 0; i < n; i++) {
			int len = codeLen[i];
			if (len > 0) {
				List<Integer> codeList = codeMap.get(len);
				if (codeList == null) {
					codeList = new ArrayList<Integer>();
					codeMap.put(codeLen[i], codeList);
				}
				codeList.add(codes.get(i));
			}
		}
		
		return codeMap;
	}
	
	/**
	 * Get the current value of the checksum.
	 * @return The current CRC value
	 */
	public int getCRCValue() {
		return crc.getValue();
	}
}
