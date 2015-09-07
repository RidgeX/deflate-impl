## Simple DEFLATE implementation in Java

  * Ridge Shrubsall (21112211)

## Usage
    java GZCompressor -vpcd [in-file] [out-file]

|Option|Description|
|------|-----------|
|-v|verbose mode|
|-p|show progress|
|-c|compress|
|-d|decompress|

## Configurable values in Deflater.java
  * `MODE` (0 = no compression, 1 = fixed Huffman, 2 = dynamic Huffman)
  * `ENABLE_LZ77` (true or false)
  * `BUFFER_SIZE` (1 to Integer.MAX_VALUE)
  * `WINDOW_SIZE` (2^0 to 2^15)
