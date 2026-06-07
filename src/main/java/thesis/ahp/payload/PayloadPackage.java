package thesis.ahp.payload;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

public final class PayloadPackage {
    private static final byte[] SYNC = new byte[]{'A', 'H', 'P', '1'};
    private static final int NONCE_LEN = 12;
    private static final int TAG_LEN = 16;
    private static final SecureRandom RNG = new SecureRandom();

    private PayloadPackage() {}

    public static int headerBytes() {
        return 4 + 1 + 4 + NONCE_LEN + TAG_LEN;
    }

    public static byte[] pack(byte[] message, String password, int qf) throws Exception {
        byte[] prepared = preparePlainPayload(message, password);

        byte[] nonce = new byte[NONCE_LEN];
        RNG.nextBytes(nonce);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(
                Cipher.ENCRYPT_MODE,
                new SecretKeySpec(key(password), "AES"),
                new GCMParameterSpec(TAG_LEN * 8, nonce)
        );

        byte[] encryptedWithTag = cipher.doFinal(prepared);

        int cipherLen = encryptedWithTag.length - TAG_LEN;

        byte[] cipherText = Arrays.copyOfRange(encryptedWithTag, 0, cipherLen);
        byte[] tag = Arrays.copyOfRange(encryptedWithTag, cipherLen, encryptedWithTag.length);

        ByteBuffer bb = ByteBuffer.allocate(headerBytes() + cipherLen)
                .order(ByteOrder.BIG_ENDIAN);

        bb.put(SYNC);
        bb.put((byte) qf);
        bb.putInt(cipherLen);
        bb.put(nonce);
        bb.put(tag);
        bb.put(cipherText);

        return bb.array();
    }

    public static byte[] unpack(byte[] packed, String password) throws Exception {
        ByteBuffer bb = ByteBuffer.wrap(packed).order(ByteOrder.BIG_ENDIAN);

        byte[] sync = new byte[4];
        bb.get(sync);

        if (!Arrays.equals(sync, SYNC)) {
            throw new IllegalStateException("Invalid payload sync marker.");
        }

        int qf = bb.get() & 0xFF;

        if (qf < 75 || qf > 100) {
            throw new IllegalStateException("Invalid adaptive QF in payload header: " + qf);
        }

        int cipherLen = bb.getInt();

        byte[] nonce = new byte[NONCE_LEN];
        bb.get(nonce);

        byte[] tag = new byte[TAG_LEN];
        bb.get(tag);

        byte[] cipherText = new byte[cipherLen];
        bb.get(cipherText);

        byte[] encryptedWithTag = new byte[cipherLen + TAG_LEN];

        System.arraycopy(cipherText, 0, encryptedWithTag, 0, cipherLen);
        System.arraycopy(tag, 0, encryptedWithTag, cipherLen, TAG_LEN);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(
                Cipher.DECRYPT_MODE,
                new SecretKeySpec(key(password), "AES"),
                new GCMParameterSpec(TAG_LEN * 8, nonce)
        );

        byte[] prepared = cipher.doFinal(encryptedWithTag);

        return recoverPlainPayload(prepared, password);
    }

    public static boolean validSync(byte[] header) {
        if (header == null || header.length < 4) {
            return false;
        }

        for (int i = 0; i < 4; i++) {
            if (header[i] != SYNC[i]) {
                return false;
            }
        }

        return true;
    }

    public static int qfFromHeader(byte[] header) {
        if (header.length < 5) {
            throw new IllegalArgumentException("Header too short for QF.");
        }

        return header[4] & 0xFF;
    }

    public static int ciphertextLengthFromHeader(byte[] header) {
        if (header.length < 9) {
            throw new IllegalArgumentException("Header too short for ciphertext length.");
        }

        return ByteBuffer.wrap(header, 5, 4)
                .order(ByteOrder.BIG_ENDIAN)
                .getInt();
    }

    private static byte[] preparePlainPayload(byte[] message, String password) throws Exception {
        byte[] huff = HuffmanCodec.encode(message);

        boolean useHuffman = huff.length < message.length;
        byte mode = useHuffman ? (byte) 1 : (byte) 0;
        byte[] content = useHuffman ? huff : message;

        ByteBuffer meta = ByteBuffer.allocate(1 + 4 + 4)
                .order(ByteOrder.BIG_ENDIAN);

        meta.put(mode);
        meta.putInt(message.length);
        meta.putInt(content.length);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(meta.array());
        out.write(content);

        byte[] prepared = out.toByteArray();

        byte[] xorKey = MessageDigest.getInstance("SHA-256")
                .digest(("xor:" + password).getBytes(java.nio.charset.StandardCharsets.UTF_8));

        for (int i = 0; i < prepared.length; i++) {
            prepared[i] = (byte) (prepared[i] ^ xorKey[i % xorKey.length]);
        }

        return prepared;
    }

    private static byte[] recoverPlainPayload(byte[] prepared, String password) throws Exception {
        byte[] xorKey = MessageDigest.getInstance("SHA-256")
                .digest(("xor:" + password).getBytes(java.nio.charset.StandardCharsets.UTF_8));

        for (int i = 0; i < prepared.length; i++) {
            prepared[i] = (byte) (prepared[i] ^ xorKey[i % xorKey.length]);
        }

        ByteBuffer bb = ByteBuffer.wrap(prepared).order(ByteOrder.BIG_ENDIAN);

        byte mode = bb.get();
        int rawLength = bb.getInt();
        int contentLength = bb.getInt();

        byte[] content = new byte[contentLength];
        bb.get(content);

        byte[] plain;

        if (mode == 1) {
            plain = HuffmanCodec.decode(content);
        } else if (mode == 0) {
            plain = content;
        } else {
            throw new IllegalStateException("Unknown payload preprocessing mode: " + mode);
        }

        if (plain.length != rawLength) {
            throw new IllegalStateException(
                    "Recovered message length mismatch. expected="
                            + rawLength + ", got=" + plain.length
            );
        }

        return plain;
    }

    private static byte[] key(String password) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");

        return Arrays.copyOf(
                md.digest(password.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                16
        );
    }
}
