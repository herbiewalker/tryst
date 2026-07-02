import com.google.crypto.tink.subtle.AesGcmHkdfStreaming;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Decrypts a .tryst backup and writes its data.json to the given path. Mirrors Verify/BackupManager. */
public final class Dump {
    private static final byte[] MAGIC = "TRYSTBK1".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] AAD = "tryst-backup-v1".getBytes(StandardCharsets.UTF_8);

    public static void main(String[] args) throws Exception {
        File in = new File(args[0]);
        String password = args[1];
        File out = new File(args[2]);
        try (InputStream raw = new BufferedInputStream(new FileInputStream(in))) {
            byte[] magic = readN(raw, MAGIC.length);
            if (!java.util.Arrays.equals(magic, MAGIC)) throw new IllegalStateException("bad magic");
            int version = raw.read();
            byte[] salt = readN(raw, 16);
            int iterations = ByteBuffer.wrap(readN(raw, 4)).getInt();
            byte[] key = pbkdf2(password, salt, iterations);

            AesGcmHkdfStreaming streaming = new AesGcmHkdfStreaming(key, "HmacSha256", 32, 1 << 20, 0);
            try (ZipInputStream zip = new ZipInputStream(streaming.newDecryptingStream(raw, AAD))) {
                ZipEntry e;
                while ((e = zip.getNextEntry()) != null) {
                    if (e.getName().equals("data.json")) {
                        ByteArrayOutputStream bos = new ByteArrayOutputStream();
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = zip.read(buf)) > 0) bos.write(buf, 0, n);
                        try (FileOutputStream fos = new FileOutputStream(out)) { bos.writeTo(fos); }
                        System.out.println("wrote " + out + " (" + bos.size() + "B), format v" + version);
                    }
                    zip.closeEntry();
                }
            }
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
