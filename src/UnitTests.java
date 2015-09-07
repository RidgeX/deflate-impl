import static org.junit.Assert.*;
import org.junit.Test;

/**
 * Test cases for CRC32, HuffmanTree and LZWindow.
 * @author Ridge Shrubsall (21112211)
 */
public class UnitTests {
	/**
	 * Test cases for 32-bit CRC hashing.
	 */
	@Test
	public void testCRC32() {
		testCRC32Case("", 0);
		testCRC32Case("a", 0xe8b7be43);
		testCRC32Case("abc", 0x352441c2);
		testCRC32Case("message digest", 0x20159d7f);
		testCRC32Case("abcdefghijklmnopqrstuvwxyz", 0x4c2750bd);
		testCRC32Case("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789", 0x1fc2e6d2);
		StringBuffer buffer = new StringBuffer();
		for (int i = 0; i < 8; i++) {
			buffer.append("1234567890");
		}
		testCRC32Case(buffer.toString(), 0x7ca94a72);
		testCRC32Case("-", 0x97ddb3f8);
		testCRC32Case("--", 0x242c1465);
	}
	
	private void testCRC32Case(String input, int expected) {
		CRC32 crc = new CRC32();
		crc.update(input.getBytes());
		assertEquals(expected, crc.getValue());
	}
	
	/**
	 * Test Huffman tree balancing for length-limited codes.
	 */
	@Test
	public void testHuffmanBalancing() {
		int n = 21;
		
		// The worst case for tree height occurs when the frequencies
		// are in a Fibonacci-like sequence
		int[] fib = new int[n];
		fib[0] = 1;
		fib[1] = 1;
		for (int i = 2; i < fib.length; i++) {
			fib[i] = fib[i-1] + fib[i-2];
		}
		
		try {
			// Try creating a Huffman tree with a length limit of 15 bits
			HuffmanTree tree = new HuffmanTree(fib, 15);
			
			// Generate a code table for the tree
			HuffmanTable table = tree.getTable();
			int[] litCode = table.code;
			int[] litCodeLen = table.codeLen;
			
			// Print the codes
			for (int i = 0; i < n; i++) {
				if (litCodeLen[i] > 0) {
					String code = String.format("%" + litCodeLen[i] + "s", Integer.toBinaryString(litCode[i]));
					code = code.replace(' ', '0');
					System.out.println(i + "\t" + code);
				}
			}
		} catch (AssertionError e) {
			// Balancing failed (length limit not high enough)
			fail();
		}
	}
	
	/**
	 * Test cases for LZ77 window searching.
	 */
	@Test
	public void testLZ77() {
		testLZ77Case(
				"abcdefghijAabcdefBCDdefEFG",
				"abcdefghijA<11,6>BCD<6,3>EFG"
		);
		testLZ77Case(
				"abcde bcde bcde bcde bcde 123",
				"abcde <5,20>123"
		);
		testLZ77Case(
				"abcdebcdef",
				"abcde<4,4>f"
		);
		testLZ77Case(
				"Blah blah blah blah blah!",
				"Blah b<5,18>!"
		);
		testLZ77Case(
				"This is a string with multiple strings within it",
				"This <3,3>a string with multiple<21,7>s<22,5>in it"
		);
		testLZ77Case(
				"This is a string of text, whereherehereherehe parts of the string have text that is in other parts of the string",
				"This <3,3>a string of text, where<4,14> parts<35,5><13,3><49,7>have<51,5><21,3>at<76,4>in o<33,3>r<47,20>"
		);
		StringBuffer buffer = new StringBuffer();
		for (int i = 0; i < 25; i++) {
			buffer.append("0123456789");
		}
		testLZ77Case(
				"abcdefghij" + buffer.toString() + "0123abcdefg",
				"abcdefghij0123456789<10,244><264,7>"
		);
		testLZ77Case(
				"These blah is blah blah blah!",
				"These blah is<8,6><5,9>!"
		);
	}
	
	private void testLZ77Case(String input, String expected) {
		byte[] buffer = input.getBytes();
		StringBuffer output = new StringBuffer();
		LZWindow window = new LZWindow(32768);
		
		for (int i = 0; i < buffer.length; i++) {
			LZPair pair = window.find(buffer, i, buffer.length);
			if (pair != null) {
				window.add(buffer, i, pair.len);
				i += (pair.len - 1);
				output.append(String.format("<%d,%d>", pair.dist, pair.len));
			} else {
				window.add(buffer[i]);
				output.append((char) buffer[i]);
			}
		}
		
		assertEquals(expected, output.toString());
	}
}
