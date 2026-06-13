import com.google.crypto.tink.subtle.AesGcmHkdfStreaming;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Mirrors BackupManager.import: reads the header, decrypts via Tink, walks the ZIP, sanity-checks. */
public final class Verify {
    private static final byte[] MAGIC = "TRYSTBK1".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] AAD = "tryst-backup-v1".getBytes(StandardCharsets.UTF_8);

    public static void main(String[] args) throws Exception {
        File in = new File(args[0]);
        String password = args[1];
        try (InputStream raw = new BufferedInputStream(new FileInputStream(in))) {
            byte[] magic = readN(raw, MAGIC.length);
            if (!java.util.Arrays.equals(magic, MAGIC)) throw new IllegalStateException("bad magic");
            int version = raw.read();
            byte[] salt = readN(raw, 16);
            int iterations = ByteBuffer.wrap(readN(raw, 4)).getInt();
            byte[] key = pbkdf2(password, salt, iterations);

            AesGcmHkdfStreaming streaming = new AesGcmHkdfStreaming(key, "HmacSha256", 32, 1 << 20, 0);
            int dataJsonLen = 0, mediaCount = 0;
            try (ZipInputStream zip = new ZipInputStream(streaming.newDecryptingStream(raw, AAD))) {
                ZipEntry e;
                while ((e = zip.getNextEntry()) != null) {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = zip.read(buf)) > 0) bos.write(buf, 0, n);
                    if (e.getName().equals("data.json")) dataJsonLen = bos.size();
                    else if (e.getName().startsWith("media/")) mediaCount++;
                    zip.closeEntry();
                }
            }
            System.out.println("VERIFY OK  version=" + version + " iterations=" + iterations
                    + " data.json=" + dataJsonLen + "B media=" + mediaCount);
        }
    }

    private static byte[] readN(InputStream in, int n) throws IOException {
        byte[] b = new byte[n];
        int off = 0;
        while (off < n) {
            int r = in.read(b, off, n - off);
            if (r < 0) throw new IOException("truncated");
            off += r;
        }
        return b;
    }

    private static byte[] pbkdf2(String pw, byte[] salt, int iters) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(pw.toCharArray(), salt, iters, 256);
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
    }
}
