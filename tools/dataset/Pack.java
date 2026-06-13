import com.google.crypto.tink.subtle.AesGcmHkdfStreaming;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Packs a generated dataset (data.json + media/<id>) into a Tryst .tryst backup, byte-for-byte
 * compatible with app BackupManager.export / BackupCrypto:
 *
 *   header(29) = MAGIC "TRYSTBK1" | version(1)=1 | salt(16) | iterations(4 BE)=600000
 *   then AES-256-GCM-HKDF streaming (Tink, "HmacSha256", 32-byte key, 1 MiB segments,
 *        associated data = "tryst-backup-v1"), key = PBKDF2-HMAC-SHA256(password, salt, 600000)
 *   stream plaintext = ZIP { data.json, media/<id>... }
 *
 * Usage: java -cp <tink-jars>:. Pack <in-dir> <out.tryst> <password>
 */
public final class Pack {
    private static final byte[] MAGIC = "TRYSTBK1".getBytes(StandardCharsets.US_ASCII); // 8 bytes
    private static final int FORMAT_VERSION = 1;
    private static final int SALT_BYTES = 16;
    private static final int ITERATIONS = 600_000; // Pbkdf2.DEFAULT_ITERATIONS
    private static final int KEY_SIZE_BYTES = 32;
    private static final int SEGMENT_BYTES = 1 << 20;
    private static final byte[] AAD = "tryst-backup-v1".getBytes(StandardCharsets.UTF_8);

    public static void main(String[] args) throws Exception {
        File inDir = new File(args[0]);
        File outFile = new File(args[1]);
        String password = args[2];

        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        byte[] key = pbkdf2(password, salt, ITERATIONS);

        try (OutputStream raw = new BufferedOutputStream(new FileOutputStream(outFile))) {
            raw.write(MAGIC);
            raw.write(FORMAT_VERSION);
            raw.write(salt);
            raw.write(ByteBuffer.allocate(4).putInt(ITERATIONS).array());
            raw.flush();

            AesGcmHkdfStreaming streaming =
                    new AesGcmHkdfStreaming(key, "HmacSha256", KEY_SIZE_BYTES, SEGMENT_BYTES, 0);
            try (ZipOutputStream zip = new ZipOutputStream(streaming.newEncryptingStream(raw, AAD))) {
                zip.putNextEntry(new ZipEntry("data.json"));
                zip.write(Files.readAllBytes(new File(inDir, "data.json").toPath()));
                zip.closeEntry();

                File mediaDir = new File(inDir, "media");
                File[] blobs = mediaDir.listFiles();
                if (blobs != null) {
                    Arrays.sort(blobs);
                    for (File blob : blobs) {
                        zip.putNextEntry(new ZipEntry("media/" + blob.getName()));
                        zip.write(Files.readAllBytes(blob.toPath()));
                        zip.closeEntry();
                    }
                }
            }
        }
        System.out.println("Wrote " + outFile + " (" + outFile.length() + " bytes)");
    }

    private static byte[] pbkdf2(String pw, byte[] salt, int iters) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(pw.toCharArray(), salt, iters, KEY_SIZE_BYTES * 8);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }
}
