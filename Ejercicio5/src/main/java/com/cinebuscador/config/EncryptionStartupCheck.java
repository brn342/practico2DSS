package com.cinebuscador.config;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/**
 * Verifica al arranque que la clave de cifrado este configurada
 * (variable de entorno CINE_ENCRYPTION_KEY). Si falta, la aplicacion
 * no levanta, en lugar de fallar recien en el primer login/registro.
 */
@Component
public class EncryptionStartupCheck {

    @PostConstruct
    public void check() {
        EncryptionService.ensureConfigured();
    }
}
