import java.util.PriorityQueue;


/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 * 
 * @author Owen Astrachan
 * @author Christopher Warren and Samuel Zhang
 */
public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); 
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;

	private final int myDebugLevel;
	
	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;
	
	public HuffProcessor() {
		this(0);
	}
	
	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out){

		int[] counts = readForCounts(in);
		HuffNode root = makeTreeFromCounts(counts);
		String[] codings = makeCodingsFromTree(root);

		out.writeBits(BITS_PER_INT,HUFF_TREE);
		writeHeader(root,out);

		in.reset();

		writeCompressedBits(codings,in,out);
		String hack = codings[PSEUDO_EOF];
		out.writeBits(hack.length(), Integer.parseInt(hack, 2));

		out.close();
	}


	private HuffNode makeTreeFromCounts(int[] counts) {
		PriorityQueue<HuffNode> pq = new PriorityQueue<>();

		for(int i = 0; i < counts.length; i++) {
			if (counts[i] > 0) {
			pq.add(new HuffNode(i,counts[i],null,null));
			}
		}

		pq.add(new HuffNode(PSEUDO_EOF,0));

		while (pq.size() > 1) {
			HuffNode left = pq.remove();
			HuffNode right = pq.remove();
			HuffNode t = new HuffNode(0,left.myWeight+right.myWeight, left, right);
			// create new HuffNode t with weight from
			// left.weight+right.weight and left, right subtrees
			pq.add(t);
		}

		HuffNode root = pq.remove();
		return root;
	}

	private String[] makeCodingsFromTree(HuffNode root) {
		String[] code = new String[ALPH_SIZE+1];
		codingHelper(code, root, "");
		return code;
	}

	private void codingHelper(String[] code, HuffNode root, String string) {
		if (root.myLeft == null && root.myRight == null) {
			code[root.myValue] = string;
			return;
		}
		
		codingHelper(code, root.myLeft, string + "0");
		codingHelper(code, root.myRight, string + "1");
	}

	private void writeHeader (HuffNode root, BitOutputStream out) {
		if (root.myLeft == null && root.myRight == null) {
			out.writeBits(1,1);
			out.writeBits(BITS_PER_WORD+1, root.myValue);
		}
		else {
			out.writeBits(1, root.myValue);
			writeHeader(root.myLeft, out);
			writeHeader(root.myRight, out);
		}
	}

	private void writeCompressedBits(String[] codings, BitInputStream in, BitOutputStream out) {
		while (true) {

			int bit = in.readBits(BITS_PER_WORD);

			if (bit == -1) {
				break;
			}

			String output = codings[bit];
			out.writeBits(output.length(), Integer.parseInt(output, 2));
		}
	}

	private int[] readForCounts(BitInputStream in) {
		int [] data = new int[ALPH_SIZE+1];
		while (true) {

			int value = in.readBits(BITS_PER_WORD);
			if (value == -1) {
				break;
			}
			data[value]++;
		}

		data[PSEUDO_EOF] = 1;

		return data;
	}



	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out){
		int bits = in.readBits(BITS_PER_INT);
		if (bits != HUFF_TREE)
			throw new HuffException("Illegal header starts with "+ bits);

		HuffNode root = readTreeHeader(in);
		readCompressedBits(root,in,out);

		out.close();
	}

	/**
	 * Recursive method
	 * @param in
	 * 		
	 * @return HuffNode
	 */
	private HuffNode readTreeHeader(BitInputStream in) {
		int bit = in.readBits(1);

		if (bit == -1) 
			throw new HuffException("Illegal header starts with " + bit);
		
		return (bit == 0)  // first readTreeHeader(in) represents left, next is right
				? new HuffNode(0, 0, readTreeHeader(in), readTreeHeader(in))
				: new HuffNode(in.readBits(BITS_PER_WORD + 1), 0, null, null); // reached leaf
	}

	/**
	 * Helper method that traverses tree from root going
	 * left or right depending on bit's of 0 or 1 respectively.
	 * @param root
	 * @param in
	 * @param out
	 * @return void
	 */
	private void readCompressedBits(HuffNode root, BitInputStream in, BitOutputStream out) {
		HuffNode current = root;

		while (true) {
			int bits = in.readBits(1);

			if (bits == -1) throw new HuffException("bad input, no PSEUDO_EOF");
			if (bits == 0) current = current.myLeft;
			else 		   current = current.myRight;
			
			if (current.myLeft == null && current.myRight == null) {
				if (current.myValue == PSEUDO_EOF) break; // value is PSEUDO_EOF so we move on
				else {
					out.writeBits(BITS_PER_WORD, current.myValue);
					current = root; // start back after leaf
				}
			}
		}
	}
}