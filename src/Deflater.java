import java.io.*;
import java.util.*;

/**
 * Deflates a given stream of data.
 * @author Ridge Shrubsall (21112211)
 */
public class Deflater {
	/*
	 * Debug logging
	 */
	private static boolean DEBUG = false;
	
	/*
	 * Compression mode (0 = none, 1 = fixed Huffman, 2 = dynamic Huffman)
	 */
	private static int MODE = 2;
	private static boolean ENABLE_LZ77 = true;
	
	/*
	 * Buffer and window sizes
	 */
	private static int BUFFER_SIZE = 32768;
	private static int WINDOW_SIZE = 256;
	
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
	 * The number of bits that are over the byte boundary
	 * for the current block.
	 */
	private int rem;
	
	/**
	 * Create a new Deflater.
	 * @param gz The compressor instance
	 * @param in The input stream
	 * @param out The output stream
	 */
	public Deflater(GZCompressor gz, BitInputStream in, BitOutputStream out) {
		this.gz = gz;
		this.in = in;
		this.out = out;
		crc = new CRC32();
		rem = 0;
	}
	
	/**
	 * Process the current stream.
	 * @return The size of the compressed data
	 */
	public long process() throws IOException {
		// Create block streams
		ByteArrayOutputStream blockOut = new ByteArrayOutputStream();
		BitOutputStream block = new BitOutputStream(blockOut);
		
		// Create buffer and sliding window
		byte[] buffer = new byte[BUFFER_SIZE];
		LZWindow window = new LZWindow(WINDOW_SIZE);
		
		// Start writing blocks
		int len;
		while ((len = in.read(buffer, 0, BUFFER_SIZE)) > 0) {
			// Log progress
			gz.updateProgress(in.getCount());
			
			// Write previous block to output file
			if (block.getCount() > 0) {
				byte[] b = blockOut.toByteArray();
				out.writeBits(0, 1);                                 // Write final bit
				out.writeBits(MODE, 2);                              // Write block type
				if (MODE == 0) {
					out.flushBits();
				}
				if (out.bitPos == 0 && rem == 0) {                   // Check if byte boundaries are aligned
					out.write(b);
				} else {
					for (int i = 0; i < b.length; i++) {             // Write 8 bits at a time
						if (i == b.length - 1 && rem > 0) {
							out.writeBits(b[i], rem);                // Ignore unused bits on last byte
						} else {
							out.writeBits(b[i], 8);
						}
					}
				}
				blockOut.reset();                                    // Clear block stream
			}
			
			// Update checksum
			crc.update(buffer, 0, len);
			
			if (MODE == 0) {
				// Write uncompressed data
				block.writeShort(len);
				block.writeShort(len ^ 0xffff);
				block.write(buffer, 0, len);
				window.add(buffer, 0, len);
				rem = 0;
				continue;
			}
			
			// Initialise pair and frequency arrays
			LZPair[] pairs = new LZPair[len];
			int[] litFreq = new int[N_LITERALS];
			int[] distFreq = new int[N_DISTANCES];
			int[] lenFreq = new int[N_LENGTHS];
			
			// Find distance/length pairs and symbol frequencies
			for (int i = 0; i < len; i++) {
				LZPair pair = null;
				if (ENABLE_LZ77) {
					pair = window.find(buffer, i, len);              // Try and find a pair
				}
				if (pair != null) {
					pairs[i] = pair;
					window.add(buffer, i, pair.len);                 // Add 'len' bytes to window
					i += (pair.len - 1);
					distFreq[pair.distSymbol]++;                     // Update dist/len frequencies
					litFreq[pair.lenSymbol]++;
				} else {
					window.add(buffer[i]);                           // Add one byte to window
					litFreq[buffer[i] & 0xff]++;                     // Update byte frequencies
				}
			}
			litFreq[END_OF_BLOCK]++;                                 // Add EOB marker
			
			// Start generating Huffman codes
			int[] litCode, litCodeLen, distCode, distCodeLen, lenCode, lenCodeLen;
			List<Integer> lengths;
			
			if (MODE == 2) {
				// Generate literal codes
				HuffmanTree litTree = new HuffmanTree(litFreq, 15);
				HuffmanTable litTable = litTree.getTable();
				litCode = litTable.code;
				litCodeLen = litTable.codeLen;
				
				// Generate distance codes
				HuffmanTree distTree = new HuffmanTree(distFreq, 15);
				HuffmanTable distTable = distTree.getTable();
				distCode = distTable.code;
				distCodeLen = distTable.codeLen;
				
				// Pack codelengths
				lengths = HuffmanTable.packCodeLengths(litCodeLen, distCodeLen);
				
				// Find length symbol frequencies
				Iterator<Integer> iter = lengths.iterator();
				while (iter.hasNext()) {
					int s = iter.next();
					lenFreq[s]++;
					if (s == 16 || s == 17 || s == 18) {             // Skip run-length bits
						iter.next();
					}
				}
				
				// Generate length codes
				HuffmanTree lenTree = new HuffmanTree(lenFreq, 7);
				HuffmanTable lenTable = lenTree.getTable();
				lenCode = lenTable.code;
				lenCodeLen = lenTable.codeLen;
			} else {
				// Use default codes
				litCode = HuffmanTable.LIT.code;
				litCodeLen = HuffmanTable.LIT.codeLen;
				
				distCode = HuffmanTable.DIST.code;
				distCodeLen = HuffmanTable.DIST.codeLen;
				
				lengths = null;
				lenCode = null;
				lenCodeLen = null;
			}
			
			// Print codes (for debugging)
			if (DEBUG) {
				System.out.println("Length codes:");
				for (int i = 0; i < N_LENGTHS; i++) {
					if (lenCodeLen[i] > 0) {
						String code = String.format("%" + lenCodeLen[i] + "s", Integer.toBinaryString(lenCode[i]));
						code = code.replace(' ', '0');
						System.out.println(i + "\t" + code);
					}
				}
				System.out.println("Literal codes:");
				for (int i = 0; i < N_LITERALS; i++) {
					if (litCodeLen[i] > 0) {
						String code = String.format("%" + litCodeLen[i] + "s", Integer.toBinaryString(litCode[i]));
						code = code.replace(' ', '0');
						System.out.println(i + "\t" + code);
					}
				}
				System.out.println("Distance codes:");
				for (int i = 0; i < N_DISTANCES; i++) {
					if (distCodeLen[i] > 0) {
						String code = String.format("%" + distCodeLen[i] + "s", Integer.toBinaryString(distCode[i]));
						code = code.replace(' ', '0');
						System.out.println(i + "\t" + code);
					}
				}
			}
			
			// Write compressed data
			if (MODE == 2) {
				block.writeBits(N_LITERALS - 257, 5);                // Number of literal codes
				block.writeBits(N_DISTANCES - 1, 5);                 // Number of distance codes
				block.writeBits(N_LENGTHS - 4, 4);                   // Number of length codes
				for (int i = 0; i < N_LENGTHS; i++) {                // Length codelengths
					block.writeBits(lenCodeLen[LEN_ORDER[i]], 3);
				}
				Iterator<Integer> iter = lengths.iterator();
				while (iter.hasNext()) {                             // Literal/distance codelengths (as length codes)
					int s = iter.next();
					block.writeBitsR(lenCode[s], lenCodeLen[s]);
					if (s == 16) {                                   // Extra bits for run-length
						block.writeBits(iter.next(), 2);
					} else if (s == 17) {
						block.writeBits(iter.next(), 3);
					} else if (s == 18) {
						block.writeBits(iter.next(), 7);
					}
				}
			}
			for (int i = 0; i < len; i++) {                          // Data (as literal/distance codes)
				LZPair pair = pairs[i];
				if (pair != null) {
					int s = pair.lenSymbol;
					block.writeBitsR(litCode[s], litCodeLen[s]);
					block.writeBits(pair.lenBits, pair.lenNumBits);
					int t = pair.distSymbol;
					block.writeBitsR(distCode[t], distCodeLen[t]);
					block.writeBits(pair.distBits, pair.distNumBits);
					i += (pair.len - 1);
				} else {
					int s = buffer[i] & 0xff;
					block.writeBitsR(litCode[s], litCodeLen[s]);
				}
			}
			block.writeBitsR(litCode[END_OF_BLOCK], litCodeLen[END_OF_BLOCK]);
			rem = block.bitPos;
			block.flushBits();                                       // Flush block stream
		}
		
		// Write final block to output file
		byte[] b = blockOut.toByteArray();
		out.writeBits(1, 1);                                         // Write final bit
		out.writeBits(MODE, 2);                                      // Write block type
		if (MODE == 0) {
			out.flushBits();
		}
		if (out.bitPos == 0 && rem == 0) {                           // Check if byte boundaries are aligned
			out.write(b);
		} else {
			for (int i = 0; i < b.length; i++) {                     // Write 8 bits at a time
				if (i == b.length - 1 && rem > 0) {
					out.writeBits(b[i], rem);                        // Ignore unused bits on last byte
				} else {
					out.writeBits(b[i], 8);
				}
			}
		}
		out.flushBits();                                             // Flush output stream
		
		// Finished
		return out.getCount();
	}
	
	/**
	 * Get the current value of the checksum.
	 * @return The current CRC value
	 */
	public int getCRCValue() {
		return crc.getValue();
	}
}
