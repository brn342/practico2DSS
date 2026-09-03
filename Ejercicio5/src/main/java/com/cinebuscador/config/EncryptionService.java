package com.cinebuscador.config;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Servicio de cifrado simetrico para contrasenas.
 *
 * Mitigaciones respecto de la version vulnerable (CWE-312 / CWE-321 / CWE-327):
 *  - La clave NO esta en el codigo: se lee de la variable de entorno
 *    CINE_ENCRYPTION_KEY (o la propiedad de sistema cine.encryption.key),
 *    32 bytes en Base64. Sin clave configurada, la aplicacion no arranca.
 *  - Se reemplaza AES/ECB por AES-256/GCM con IV aleatorio por operacion:
 *    cifrado NO determinista (dos usuarios con la misma contrasena obtienen
 *    ciphertext distinto) y con autenticacion (detecta manipulacion).
 *  - Se eliminan los metodos que exponian la clave (getStaticKey / getKeyBytes
 *    / getKeyHex).
 *
 * Nota: almacenar contrasenas de forma reversible sigue siendo desaconsejado;
 * lo recomendado es un hash lento con salt (BCrypt/Argon2/PBKDF2). Aqui se
 * mantiene el cifrado reversible por decision del ejercicio.
 */
public class EncryptionService {

    private static final String CIPHER_ALGO = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int GCM_IV_BYTES = 12;

    private static final SecureRandom RNG = new SecureRandom();
    private static final SecretKeySpec SECRET_KEY = loadKey();

    private static SecretKeySpec loadKey() {
        String b64 = System.getenv("CINE_ENCRYPTION_KEY");
        if (b64 == null || b64.isBlank()) {
            b64 = System.getProperty("cine.encryption.key");
        }
        if (b64 == null || b64.isBlank()) {
            throw new IllegalStateException(
                "Falta la clave de cifrado. Defina la variable de entorno "
                + "CINE_ENCRYPTION_KEY con 32 bytes en Base64. "
                + "Puede generarla con: openssl rand -base64 32");
        }
        byte[] key;
        try {
            key = Base64.getDecoder().decode(b64.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("CINE_ENCRYPTION_KEY no es Base64 valido.", e);
        }
        if (key.length != 32) {
            throw new IllegalStateException(
                "CINE_ENCRYPTION_KEY debe decodificar exactamente 32 bytes (AES-256); "
                + "tiene " + key.length + ".");
        }
        return new SecretKeySpec(key, "AES");
    }

    /** Cifra con AES-256/GCM. Formato de salida: Base64( IV(12) || ciphertext+tag ). */
    public static String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[GCM_IV_BYTES];
            RNG.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER_ALGO);
            cipher.init(Cipher.ENCRYPT_MODE, SECRET_KEY, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] out = ByteBuffer.allocate(iv.length + ct.length).put(iv).put(ct).array();
            return Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Error al cifrar la contrasena", e);
        }
    }

    /** Descifra un valor generado por {@link #encrypt(String)}. */
    public static String decrypt(String encryptedBase64) {
        try {
            byte[] in = Base64.getDecoder().decode(encryptedBase64);
            if (in.length <= GCM_IV_BYTES) {
                throw new IllegalArgumentException("Ciphertext demasiado corto");
            }
            byte[] iv = Arrays.copyOfRange(in, 0, GCM_IV_BYTES);
            byte[] ct = Arrays.copyOfRange(in, GCM_IV_BYTES, in.length);

            Cipher cipher = Cipher.getInstance(CIPHER_ALGO);
            cipher.init(Cipher.DECRYPT_MODE, SECRET_KEY, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Error al descifrar la contrasena", e);
        }
    }

    /** Fuerza la carga/validacion de la clave (para fallar al arranque, no en el primer login). */
    public static void ensureConfigured() {
        if (SECRET_KEY == null) {
            throw new IllegalStateException("Clave de cifrado no inicializada");
        }
    }

    /** Comparacion en tiempo constante de la contrasena ingresada contra la almacenada. */
    public static boolean matches(String rawPassword, String storedEncrypted) {
        try {
            byte[] a = decrypt(storedEncrypted).getBytes(StandardCharsets.UTF_8);
            byte[] b = rawPassword.getBytes(StandardCharsets.UTF_8);
            return MessageDigest.isEqual(a, b);
        } catch (RuntimeException e) {
            return false;
        }
    }
}
