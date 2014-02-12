package proxy;

import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.config.ConnectionConfig;
import org.apache.http.impl.nio.DefaultHttpClientIODispatch;
import org.apache.http.impl.nio.pool.BasicNIOConnPool;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.nio.protocol.BasicAsyncRequestProducer;
import org.apache.http.nio.protocol.BasicAsyncResponseConsumer;
import org.apache.http.nio.protocol.HttpAsyncRequestExecutor;
import org.apache.http.nio.protocol.HttpAsyncRequester;
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.apache.http.nio.reactor.IOEventDispatch;
import org.apache.http.nio.reactor.IOReactorException;
import org.apache.http.protocol.*;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

/**
 * Minimal asynchronous HTTP/1.1 client.
 */
public class MyTest {

	private HttpProcessor httpproc;

	private BasicNIOConnPool pool;
	private final ConnectingIOReactor ioReactor;

	public MyTest() {
		// Create HTTP protocol processing chain
		httpproc = HttpProcessorBuilder.create()
				// Use standard client-side protocol interceptors
				.add(new RequestContent())
				.add(new RequestTargetHost())
				.add(new RequestConnControl())
				.add(new RequestUserAgent("Test/1.1"))
				.add(new RequestExpectContinue(true)).build();

		SSLContext sslcontext = null;
		try {
			sslcontext = SSLContext.getInstance("Default");
			sslcontext.init(null, null, null);
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (KeyManagementException e) {
			e.printStackTrace();
		}
		// Plain I/O session
//		IOSession iosession = <...>
//		SSLNHttpClientConnectionFactory connfactory = new SSLNHttpClientConnectionFactory(
//				sslcontext, null, ConnectionConfig.DEFAULT);
//		NHttpClientConnection conn = connfactory.createConnection(iosession);

		// Create client-side HTTP protocol handler
		HttpAsyncRequestExecutor protocolHandler = new HttpAsyncRequestExecutor();
		// Create client-side I/O event dispatch
		final IOEventDispatch ioEventDispatch = new DefaultHttpClientIODispatch(protocolHandler,
				ConnectionConfig.DEFAULT);
		// Create client-side I/O reactor
		try {
			ioReactor = new DefaultConnectingIOReactor();
		} catch (IOReactorException e) {
			throw new RuntimeException(e);
		}
		// Create HTTP connection pool
		pool = new BasicNIOConnPool(ioReactor, ConnectionConfig.DEFAULT);
		// Limit total number of connections to just two
		pool.setDefaultMaxPerRoute(2);
		pool.setMaxTotal(2);
		// Run the I/O reactor in a separate thread
		Thread t = new Thread(new Runnable() {

			public void run() {
				try {
					// Ready to go!
					ioReactor.execute(ioEventDispatch);
				} catch (InterruptedIOException ex) {
					System.err.println("Interrupted");
				} catch (IOException e) {
					System.err.println("I/O error: " + e.getMessage());
				}
				System.out.println("Shutdown");
			}

		});
		// Start the client thread
		t.start();
	}

	public void stop() {
		System.out.println("Shutting down I/O reactor");
		try {
			ioReactor.shutdown();
			System.out.println("Done");
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private HttpHost target = new HttpHost("api.weibo.com", 443, "https");

	public void get() {
		// Create HTTP requester
		HttpAsyncRequester requester = new HttpAsyncRequester(httpproc);
		// Execute HTTP GETs to the following hosts and
		BasicHttpRequest request = new BasicHttpRequest("GET", "/2/users/show.json?access_token=2.00SlDQsDdcZIJC94e5308f67sRL13D&uid=3550148352");
		HttpCoreContext coreContext = HttpCoreContext.create();
		requester.execute(
				new BasicAsyncRequestProducer(target, request),
				new BasicAsyncResponseConsumer(),
				pool,
				coreContext,
				// Handle HTTP response from a callback
				new FutureCallback<HttpResponse>() {

					public void completed(final HttpResponse response) {

						System.out.println(target + "->" + response.getStatusLine());
					}

					public void failed(final Exception ex) {

						System.out.println(target + "->" + ex);
					}

					public void cancelled() {
						System.out.println(target + " cancelled");
					}

				});
	}


	public static void main(String[] args) throws Exception {
		MyTest client = new MyTest();
		client.get();
		System.in.read();
		client.stop();
	}

}