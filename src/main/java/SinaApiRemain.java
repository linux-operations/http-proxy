import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.impl.DefaultBHttpClientConnection;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.protocol.*;
import org.apache.http.util.EntityUtils;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by chenwei on 14-2-9.
 */
public class SinaApiRemain {

	private static final Pattern pattern = Pattern.compile(".*\"remaining_ip_hits\":(\\d+),.*");

	public static void main(String[] args) throws IOException, HttpException, NoSuchAlgorithmException {
		if (args.length < 2) {
			System.out.println("Usage: SinaApiRemain <interface> <token>");
			System.exit(1);
		}
		List<InetAddress> ips = LocalIp.getAllIp(args[0]);
		System.out.println("interface" + args[0] + ", ips=" + ips);
		String target = "/account/rate_limit_status.json?access_token=" + args[1];
		final HttpRequestExecutor httpexecutor = new HttpRequestExecutor();
		HttpProcessor httpproc = HttpProcessorBuilder.create()
				.add(new RequestContent())
				.add(new RequestTargetHost())
				.add(new RequestConnControl())
				.add(new RequestExpectContinue(true)).build();

		HttpCoreContext coreContext = HttpCoreContext.create();
		HttpHost host = new HttpHost("api.weibo.com", 443, "https");
		coreContext.setTargetHost(host);


		for (InetAddress ip : ips) {
			String json = request(httpproc, httpexecutor, coreContext, target, ip);
			Matcher matcher = pattern.matcher(json);
			if (matcher.matches()) {
				System.out.println(ip.getHostAddress() + ": " + matcher.group(1));
			} else {
				System.out.println(ip.getHostAddress() + ": error");
			}
		}
	}

	private static String request(HttpProcessor httpproc, HttpRequestExecutor httpexecutor, HttpCoreContext coreContext, String target, InetAddress localinetAddress) throws NoSuchAlgorithmException, IOException, HttpException {
		DefaultBHttpClientConnection conn = new DefaultBHttpClientConnection(8 * 1024);
		try {
			HttpHost targetHost = coreContext.getTargetHost();
			Socket socket;
			if (!conn.isOpen()) {
				if ("https".equals(targetHost.getSchemeName())) {
					SSLContext sslcontext = SSLContext.getInstance("Default");
					SocketFactory sf = sslcontext.getSocketFactory();

					SSLSocket sslSocket = (SSLSocket) sf.createSocket(targetHost.getHostName(), targetHost.getPort(), localinetAddress, 0);
					sslSocket.setEnabledCipherSuites(new String[]{
							"TLS_RSA_WITH_AES_256_CBC_SHA",
							"TLS_DHE_RSA_WITH_AES_256_CBC_SHA",
							"TLS_DHE_DSS_WITH_AES_256_CBC_SHA"});
					socket = sslSocket;
				} else {
					socket = new Socket(targetHost.getHostName(), targetHost.getPort());
				}
				conn.bind(socket);
			}
			BasicHttpRequest request = new BasicHttpRequest("GET", target);
			httpexecutor.preProcess(request, httpproc, coreContext);
			HttpResponse response = httpexecutor.execute(request, conn, coreContext);
			httpexecutor.postProcess(response, httpproc, coreContext);
			return EntityUtils.toString(response.getEntity());
		} finally {
			conn.close();
		}
	}

}
