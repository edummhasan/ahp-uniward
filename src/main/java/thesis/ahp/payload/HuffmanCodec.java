package thesis.ahp.payload;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.PriorityQueue;

public final class HuffmanCodec {
    private HuffmanCodec() {}

    private static final class Node implements Comparable<Node> {
        final int symbol;
        final int freq;
        final Node left;
        final Node right;

        Node(int symbol, int freq, Node left, Node right) {
            this.symbol = symbol;
            this.freq = freq;
            this.left = left;
            this.right = right;
        }

        boolean leaf() {
            return left == null && right == null;
        }

        @Override
        public int compareTo(Node o) {
            return Integer.compare(this.freq, o.freq);
        }
    }

    public static byte[] encode(byte[] input) {
        if (input.length == 0) {
            return ByteBuffer.allocate(4 + 256 * 4 + 4)
                    .order(ByteOrder.BIG_ENDIAN)
                    .putInt(0)
                    .array();
        }

        int[] freq = new int[256];

        for (byte b : input) {
            freq[b & 0xFF]++;
        }

        Node root = buildTree(freq);
        String[] codes = new String[256];
        buildCodes(root, "", codes);

        ByteArrayOutputStream bitOut = new ByteArrayOutputStream();
        int current = 0;
        int bitCount = 0;
        int totalBits = 0;

        for (byte b : input) {
            String code = codes[b & 0xFF];

            for (int i = 0; i < code.length(); i++) {
                current <<= 1;

                if (code.charAt(i) == '1') {
                    current |= 1;
                }

                bitCount++;
                totalBits++;

                if (bitCount == 8) {
                    bitOut.write(current);
                    current = 0;
                    bitCount = 0;
                }
            }
        }

        if (bitCount > 0) {
            current <<= (8 - bitCount);
            bitOut.write(current);
        }

        byte[] encodedBits = bitOut.toByteArray();

        ByteBuffer header = ByteBuffer.allocate(4 + 256 * 4 + 4)
                .order(ByteOrder.BIG_ENDIAN);

        header.putInt(input.length);

        for (int f : freq) {
            header.putInt(f);
        }

        header.putInt(totalBits);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(header.array());
        out.writeBytes(encodedBits);

        return out.toByteArray();
    }

    public static byte[] decode(byte[] packed) {
        ByteBuffer bb = ByteBuffer.wrap(packed).order(ByteOrder.BIG_ENDIAN);

        int originalLength = bb.getInt();

        if (originalLength == 0) {
            return new byte[0];
        }

        int[] freq = new int[256];

        for (int i = 0; i < 256; i++) {
            freq[i] = bb.getInt();
        }

        int totalBits = bb.getInt();

        byte[] bitData = new byte[bb.remaining()];
        bb.get(bitData);

        Node root = buildTree(freq);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Node node = root;

        for (int i = 0; i < totalBits; i++) {
            int byteIndex = i / 8;
            int bitIndex = 7 - (i % 8);
            int bit = (bitData[byteIndex] >> bitIndex) & 1;

            node = bit == 0 ? node.left : node.right;

            if (node.leaf()) {
                out.write(node.symbol);
                node = root;

                if (out.size() == originalLength) {
                    break;
                }
            }
        }

        return out.toByteArray();
    }

    private static Node buildTree(int[] freq) {
        PriorityQueue<Node> pq = new PriorityQueue<>();

        for (int i = 0; i < 256; i++) {
            if (freq[i] > 0) {
                pq.add(new Node(i, freq[i], null, null));
            }
        }

        if (pq.isEmpty()) {
            pq.add(new Node(0, 1, null, null));
        }

        if (pq.size() == 1) {
            Node only = pq.poll();
            return new Node(-1, only.freq, only, new Node(0, 0, null, null));
        }

        while (pq.size() > 1) {
            Node a = pq.poll();
            Node b = pq.poll();
            pq.add(new Node(-1, a.freq + b.freq, a, b));
        }

        return pq.poll();
    }

    private static void buildCodes(Node node, String prefix, String[] codes) {
        if (node.leaf()) {
            codes[node.symbol] = prefix.isEmpty() ? "0" : prefix;
            return;
        }

        buildCodes(node.left, prefix + "0", codes);
        buildCodes(node.right, prefix + "1", codes);
    }
}
