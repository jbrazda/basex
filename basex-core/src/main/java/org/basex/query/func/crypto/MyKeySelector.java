package org.basex.query.func.crypto;

import java.security.*;
import java.util.*;

import java.security.cert.X509Certificate;
import javax.xml.crypto.*;
import javax.xml.crypto.dsig.*;
import javax.xml.crypto.dsig.keyinfo.*;

/**
 * Extracts a key from a given {@link KeyInfo} object.
 *
 * @author BaseX Team, BSD License
 * @author Lukas Kircher
 */
final class MyKeySelector extends KeySelector {
  /**
   * Wrapper for KeySelector results.
   *
   * @author BaseX Team, BSD License
   * @author Lukas Kircher
   */
  private static final class MyKeySelectorResult implements KeySelectorResult {
    /** Key. */
    private final Key pk;

    @Override
    public Key getKey() {
      return pk;
    }

    /**
     * Constructor.
     * @param key key
     */
    MyKeySelectorResult(final PublicKey key) {
      pk = key;
    }
  }

  @Override
  public KeySelectorResult select(final KeyInfo ki, final Purpose p, final AlgorithmMethod m,
      final XMLCryptoContext c) throws KeySelectorException {

    if(ki == null) throw new KeySelectorException("KeyInfo is null");

    final SignatureMethod sm = (SignatureMethod) m;
    final List<?> list = ki.getContent();

    for(final Object l : list) {
      final XMLStructure s = (XMLStructure) l;
      PublicKey pk = null;
      if(s instanceof final KeyValue kv) {
        try {
          pk = kv.getPublicKey();
        } catch(final KeyException ke) {
          throw new KeySelectorException(ke);
        }
      } else if(s instanceof final X509Data xd) {
        for(final Object d : xd.getContent()) {
          if(d instanceof final X509Certificate xc) {
            pk = xc.getPublicKey();
          }
        }
      }

      if(pk != null) {
        final String sa = sm.getAlgorithm();
        final String ka = pk.getAlgorithm();
        if("DSA".equalsIgnoreCase(ka) && "http://www.w3.org/2000/09/xmldsig#dsa-sha1".equals(sa) ||
          "RSA".equalsIgnoreCase(ka) && "http://www.w3.org/2000/09/xmldsig#rsa-sha1".equals(sa)) {
          return new MyKeySelectorResult(pk);
        }
      }
    }

    throw new KeySelectorException("No KeyValue element found");
  }
}