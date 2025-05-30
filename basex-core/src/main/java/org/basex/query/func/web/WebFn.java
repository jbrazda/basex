package org.basex.query.func.web;

import java.util.*;
import java.util.concurrent.atomic.*;

import org.basex.io.serial.*;
import org.basex.query.*;
import org.basex.query.func.*;
import org.basex.query.value.item.*;
import org.basex.query.value.map.*;
import org.basex.query.value.node.*;
import org.basex.util.*;
import org.basex.util.Token.*;
import org.basex.util.http.*;
import org.basex.util.options.*;

/**
 * Function implementation.
 *
 * @author BaseX Team, BSD License
 * @author Christian Gruen
 */
public abstract class WebFn extends StandardFunc {
  /** Response options. */
  public static class ResponseOptions extends Options {
    /** Status. */
    public static final NumberOption STATUS = new NumberOption("status");
    /** Message. */
    public static final StringOption MESSAGE = new StringOption("message");
  }

  /**
   * Creates a URL from the function arguments.
   * @param qc query context
   * @return generated url
   * @throws QueryException query exception
   */
  final String createUrl(final QueryContext qc) throws QueryException {
    final byte[] href = toToken(arg(0), qc);
    final Item params = arg(1).item(qc, info);
    final byte[] anchor = toZeroToken(arg(2), qc);

    final XQMap map = params.isEmpty() ? XQMap.empty() : toMap(params);
    final TokenBuilder url = createUrl(href, map, '&', info);
    if(anchor.length > 0) url.add('#').add(Token.encodeUri(anchor, UriEncoder.URI));
    return url.toString();
  }

  /**
   * Creates a URL from the function arguments.
   * @param href host and path
   * @param params query parameters
   * @param info input info
   * @param sep separator for query parameters
   * @return supplied URL builder
   * @throws QueryException query exception
   */
  public static TokenBuilder createUrl(final byte[] href, final XQMap params, final char sep,
      final InputInfo info) throws QueryException {
    final TokenBuilder url = new TokenBuilder().add(href);
    final AtomicInteger c = new AtomicInteger();
    params.forEach((key, value) -> {
      final byte[] name = key.string(info);
      for(final Item item : value) {
        url.add(c.getAndIncrement() == 0 ? '?' : sep).add(Token.encodeUri(name, UriEncoder.URI));
        url.add('=').add(Token.encodeUri(item.string(info), UriEncoder.URI));
      }
    });
    return url;
  }

  /**
   * Creates a REST response.
   * @param response status and message
   * @param headers response headers
   * @param output serialization parameters (can be {@code null})
   * @return response
   * @throws QueryException query exception
   */
  final FNode createResponse(final ResponseOptions response, final HashMap<String, String> headers,
      final HashMap<String, String> output) throws QueryException {

    // root element
    final FBuilder rrest = FElem.build(HTTPText.Q_REST_RESPONSE).declareNS();

    // HTTP response
    final FBuilder hresp = FElem.build(HTTPText.Q_HTTP_RESPONSE).declareNS();
    for(final Option<?> o : response) {
      if(response.contains(o)) hresp.add(new QNm(o.name()), response.get(o));
    }
    headers.forEach((name, value) -> {
      if(!value.isEmpty()) {
        hresp.add(FElem.build(HTTPText.Q_HTTP_HEADER).add(HTTPText.Q_NAME, name).
            add(HTTPText.Q_VALUE, value));
      }
    });
    rrest.add(hresp);

    // serialization parameters
    if(output != null) {
      final SerializerOptions sopts = SerializerMode.DEFAULT.get();
      for(final String entry : output.keySet())
        if(sopts.option(entry) == null) throw QueryError.UNKNOWNOPTION_X.get(info, entry);

      final FBuilder param = FElem.build(SerializerOptions.Q_ROOT).declareNS();
      output.forEach((name, value) -> {
        if(!value.isEmpty()) {
          final QNm qnm = new QNm(QueryText.OUTPUT_PREFIX, name, QueryText.OUTPUT_URI);
          param.add(FElem.build(qnm).add(HTTPText.Q_VALUE, value));
        }
      });
      rrest.add(param);
    }
    return rrest.finish();
  }
}
