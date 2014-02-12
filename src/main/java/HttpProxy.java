import org.apache.http.*;
import org.apache.http.impl.DefaultBHttpClientConnection;
import org.apache.http.impl.DefaultBHttpServerConnection;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.protocol.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class HttpProxy {

	private static final Logger logger = LoggerFactory.getLogger(HttpProxy.class);

	private static final String HTTP_IN_CONN = "http.proxy.in-conn";
	private static final String HTTP_OUT_CONN = "http.proxy.out-conn";
	private static final String HTTP_CONN_KEEPALIVE = "http.proxy.conn-keepalive";
	private static List<InetAddress> ips;
	private static AtomicInteger ipIndex = new AtomicInteger();

	public static void main(final String[] args) throws Exception {
		if (args.length < 1) {
			System.out.println("Usage: HttpProxy <interface> <targetHostName> [localListenerPort]");
			System.exit(1);
		}
		ips = LocalIp.getAllIp(args[0]);
		logger.info("local ips: " + ips);

		URI uri = new URI(args[1]);
		// Target host
		HttpHost targetHost = new HttpHost(
				uri.getHost(),
				uri.getPort() > 0 ? uri.getPort() : 80,
				uri.getScheme() != null ? uri.getScheme() : "http");

		logger.info("Reverse proxy to " + targetHost);
		int localListenerPort = 8080;
		if (args.length > 2) {
			localListenerPort = Integer.valueOf(args[2]);
		}

		final Thread t = new RequestListenerThread(localListenerPort, targetHost);
		t.setDaemon(false);
		t.start();
	}

	static class ProxyHandler implements HttpRequestHandler {

		private final HttpHost target;
		private final HttpProcessor httpproc;
		private final HttpRequestExecutor httpexecutor;
		private final ConnectionReuseStrategy connStrategy;

		public ProxyHandler(
				final HttpHost target,
				final HttpProcessor httpproc,
				final HttpRequestExecutor httpexecutor) {
			super();
			this.target = target;
			this.httpproc = httpproc;
			this.httpexecutor = httpexecutor;
			this.connStrategy = DefaultConnectionReuseStrategy.INSTANCE;
		}

		public void handle(
				final HttpRequest request,
				final HttpResponse response,
				final HttpContext context) throws HttpException, IOException {

			final HttpClientConnection conn = (HttpClientConnection) context.getAttribute(
					HTTP_OUT_CONN);

			context.setAttribute(HttpCoreContext.HTTP_CONNECTION, conn);
			context.setAttribute(HttpCoreContext.HTTP_TARGET_HOST, this.target);

			logger.debug(">> Request URI: {}", request.getRequestLine().getUri());

			// Remove hop-by-hop headers
			request.removeHeaders(HTTP.CONTENT_LEN);
			request.removeHeaders(HTTP.TRANSFER_ENCODING);
			request.removeHeaders(HTTP.CONN_DIRECTIVE);
			request.removeHeaders("Keep-Alive");
			request.removeHeaders("Proxy-Authenticate");
			request.removeHeaders("TE");
			request.removeHeaders("Trailers");
			request.removeHeaders("Upgrade");

			this.httpexecutor.preProcess(request, this.httpproc, context);
			final HttpResponse targetResponse = this.httpexecutor.execute(request, conn, context);
			this.httpexecutor.postProcess(response, this.httpproc, context);

			// Remove hop-by-hop headers
			targetResponse.removeHeaders(HTTP.CONTENT_LEN);
			targetResponse.removeHeaders(HTTP.TRANSFER_ENCODING);
			targetResponse.removeHeaders(HTTP.CONN_DIRECTIVE);
			targetResponse.removeHeaders("Keep-Alive");
			targetResponse.removeHeaders("TE");
			targetResponse.removeHeaders("Trailers");
			targetResponse.removeHeaders("Upgrade");

			response.setStatusLine(targetResponse.getStatusLine());
			response.setHeaders(targetResponse.getAllHeaders());
			response.setEntity(targetResponse.getEntity());

			logger.debug(">> Response {}", response.getStatusLine());

			final boolean keepalive = this.connStrategy.keepAlive(response, context);
			context.setAttribute(HTTP_CONN_KEEPALIVE, keepalive);
		}

	}

	static class RequestListenerThread extends Thread {

		private final HttpHost target;
		private final ServerSocket serversocket;
		private final HttpService httpService;

		public RequestListenerThread(final int port, final HttpHost target) throws IOException {
			this.target = target;
			this.serversocket = new ServerSocket(port);

			// Set up HTTP protocol processor for incoming connections
			final HttpProcessor inhttpproc = new ImmutableHttpProcessor(
					new HttpRequestInterceptor[]{
							new RequestContent(),
							new RequestTargetHost(),
							new RequestConnControl(),
							new RequestExpectContinue(true)
					});

			// Set up HTTP protocol processor for outgoing connections
			final HttpProcessor outhttpproc = new ImmutableHttpProcessor(
					new HttpResponseInterceptor[]{
							new ResponseDate(),
							new ResponseContent(),
							new ResponseConnControl()
					});

			// Set up outgoing request executor
			final HttpRequestExecutor httpexecutor = new HttpRequestExecutor();

			// Set up incoming request handler
			final UriHttpRequestHandlerMapper reqistry = new UriHttpRequestHandlerMapper();
			reqistry.register("*", new ProxyHandler(
					this.target,
					outhttpproc,
					httpexecutor));

			// Set up the HTTP service
			this.httpService = new HttpService(inhttpproc, reqistry);
		}

		@Override
		public void run() {
			logger.info("Listening on port " + this.serversocket.getLocalPort());
			while (!Thread.interrupted()) {
				try {
					final int bufsize = 8 * 1024;
					// Set up incoming HTTP connection
					final Socket insocket = this.serversocket.accept();
					final DefaultBHttpServerConnection inconn = new DefaultBHttpServerConnection(bufsize);
					logger.debug("Incoming connection from {}", insocket.getInetAddress());
					inconn.bind(insocket);

					// Set up outgoing HTTP connection
					final DefaultBHttpClientConnection outconn = new DefaultBHttpClientConnection(bufsize);
					Socket outsocket;
					if ("https".equals(target.getSchemeName())) {
						try {
							SSLContext sslcontext = SSLContext.getInstance("Default");
							SocketFactory sf = sslcontext.getSocketFactory();
							SSLSocket socket = (SSLSocket) sf.createSocket(target.getHostName(), 443, pollLocalIp(), 0);
							socket.setEnabledCipherSuites(new String[]{
									"TLS_RSA_WITH_AES_256_CBC_SHA",
									"TLS_DHE_RSA_WITH_AES_256_CBC_SHA",
									"TLS_DHE_DSS_WITH_AES_256_CBC_SHA"});
							outsocket = socket;
						} catch (Exception e) {
							throw new RuntimeException(e);
						}
					} else {
						outsocket = new Socket(this.target.getHostName(), this.target.getPort());
					}
					logger.debug("Outgoing connection to {}", outsocket.getInetAddress());
					outconn.bind(outsocket);

					// Start worker thread
					final Thread t = new ProxyThread(this.httpService, inconn, outconn);
					t.setDaemon(true);
					t.start();
				} catch (final InterruptedIOException ex) {
					break;
				} catch (final IOException e) {
					logger.warn("I/O error initialising connection thread: "
							+ e.getMessage());
					break;
				}
			}
		}

		private InetAddress pollLocalIp() {
			return ips.get(ipIndex.getAndAdd(1) % ips.size());
		}
	}

	static class ProxyThread extends Thread {

		private final HttpService httpservice;
		private final HttpServerConnection inconn;
		private final HttpClientConnection outconn;

		public ProxyThread(
				final HttpService httpservice,
				final HttpServerConnection inconn,
				final HttpClientConnection outconn) {
			super();
			this.httpservice = httpservice;
			this.inconn = inconn;
			this.outconn = outconn;
		}

		@Override
		public void run() {
			logger.debug("New connection thread");
			final HttpContext context = new BasicHttpContext(null);

			// Bind connection objects to the execution context
			context.setAttribute(HTTP_IN_CONN, this.inconn);
			context.setAttribute(HTTP_OUT_CONN, this.outconn);

			try {
				while (!Thread.interrupted()) {
					if (!this.inconn.isOpen()) {
						this.outconn.close();
						break;
					}

					this.httpservice.handleRequest(this.inconn, context);

					final Boolean keepalive = (Boolean) context.getAttribute(HTTP_CONN_KEEPALIVE);
					if (!Boolean.TRUE.equals(keepalive)) {
						this.outconn.close();
						this.inconn.close();
						break;
					}
				}
			} catch (final ConnectionClosedException ex) {
				logger.debug("Client closed connection");
			} catch (final IOException ex) {
				logger.warn("I/O error: " + ex.getMessage());
			} catch (final HttpException ex) {
				logger.warn("Unrecoverable HTTP protocol violation: " + ex.getMessage());
			} finally {
				try {
					this.inconn.shutdown();
				} catch (final IOException ignore) {
				}
				try {
					this.outconn.shutdown();
				} catch (final IOException ignore) {
				}
			}
		}

	}

}