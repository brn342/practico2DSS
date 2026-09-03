package com.cinebuscador.config;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

/**
 * Servicio de cifrado AES-256 para contraseñas.
 *
 * VULNERABILIDAD CRÍTICA: La clave de cifrado está embebida directamente en el código fuente.
 * Cualquier persona con acceso al código puede descifrar todas las contraseñas almacenadas.
 */
public class EncryptionService {

    // ============================================================
    // VULNERABILIDAD #1: Clave estática embebida en el código
    // ============================================================
    // La clave AES-256 debe ser de exactamente 32 bytes.
    // Estática + en source code = cualquier atacante puede obtenerla.
    private static final String SECRET_KEY = "MySup3rS3cr3tK3y!2024CineBuscadorAES";

    private static final SecretKeySpec secretKey;

    static {
        byte[] keyBytes = SECRET_KEY.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            // Pad con ceros si la clave es más corta
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
            secretKey = new SecretKeySpec(padded, "AES");
        } else {
            secretKey = new SecretKeySpec(Arrays.copyOf(keyBytes, 32), "AES");
        }
    }

    // ============================================================
    // VULNERABILIDAD #2: ECB mode (determinístico)
    // ============================================================
    // ECB produce el mismo ciphertext para el mismo plaintext.
    // Sin IV aleatorio, permite análisis de patrones entre usuarios.
    private static final String CIPHER_ALGO = "AES/ECB/PKCS5Padding";

    /**
     * Cifra la contraseña con AES-256/ECB.
     */
    public static String encrypt(String plaintext) {
        try {
            Cipher cipher = Cipher.getInstance(CIPHER_ALGO);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("Error al cifrar la contraseña", e);
        }
    }

    /**
     * Descifra una contraseña cifrada con AES-256/ECB.
     */
    public static String decrypt(String encryptedBase64) {
        try {
            Cipher cipher = Cipher.getInstance(CIPHER_ALGO);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(encryptedBase64));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Error al descifrar la contraseña", e);
        }
    }

    /**
     * VULNERABILIDAD: Expone la clave de cifrado embebida.
     * Esta función no debería existir en producción.
     */
    public static String getStaticKey() {
        return SECRET_KEY;
    }

    /**
     * VULNERABILIDAD: Expone los bytes crudos de la clave.
     * Permite usar la clave con herramientas externas (openssl, etc.).
     */
    public static byte[] getKeyBytes() {
        return secretKey.getEncoded();
    }

    /**
     * Comando de ejemplo para descifrar con OpenSSL desde la terminal:
     * echo "BASE64_STRING" | base64 -d | openssl enc -aes-256-ecb -nosalt -in /dev/stdin -out decrypted.txt -K KEY_HEX -nopad
     */
    public static String getKeyHex() {
        StringBuilder sb = new StringBuilder();
        for (byte b : secretKey.getEncoded()) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString().toUpperCase();
    }
}
