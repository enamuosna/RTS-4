// =============================================================
//  PATCH JwtService.java — accepter Base64 standard ET URL-safe
// =============================================================
//  Probleme : Decoders.BASE64.decode(secret) rejette les caracteres
//  '_' et '-' utilises par le Base64 URL-safe (RFC 4648 §5).
//  Cause classique : openssl rand -base64 64 produit du Base64
//  STANDARD, mais beaucoup d'outils PowerShell/Java/Node utilisent
//  par defaut le Base64 URL-safe (Convert.ToBase64UrlString,
//  Base64.getUrlEncoder...). Le secret fini par contenir '_' et
//  le decodeur explose au demarrage.
//
//  Fix : on detecte le format et on convertit URL-safe -> standard
//  AVANT de decoder. Le code accepte donc les DEUX formats.
//
//  ATTENTION : remplacer UNIQUEMENT le constructeur, le reste de
//  la classe (generateToken, validateToken...) ne change pas.
// =============================================================

package sn.rts.caisse.security;

// ... imports existants ...

import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationMs;
    private final String issuer;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiration-ms}") long expirationMs,
                      @Value("${app.jwt.issuer}") String issuer) {

        // -----------------------------------------------------
        //  Normalisation du secret
        //  Accepte les DEUX formats Base64 :
        //    - Standard (RFC 4648 §4) : caracteres + / =
        //    - URL-safe (RFC 4648 §5) : caracteres - _
        //
        //  La conversion URL-safe -> standard est triviale :
        //    '-' -> '+'  et  '_' -> '/'
        //
        //  Padding optionnel : Base64 URL-safe omet souvent les '='
        //  finaux. On les rajoute pour que la longueur soit un
        //  multiple de 4 (sinon Decoders.BASE64 echoue aussi).
        // -----------------------------------------------------
        String normalized = secret
                .trim()
                .replace('-', '+')
                .replace('_', '/');

        int padding = (4 - normalized.length() % 4) % 4;
        if (padding > 0) {
            normalized = normalized + "=".repeat(padding);
        }

        // La cle doit faire au moins 256 bits -> on attend du Base64.
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(normalized));
        this.expirationMs = expirationMs;
        this.issuer = issuer;
    }

    // ... le reste de la classe est INCHANGE ...
    // generateToken(), buildToken(), validateToken(),
    // extractUsername(), etc.
}
