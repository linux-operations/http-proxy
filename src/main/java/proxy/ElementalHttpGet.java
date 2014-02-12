package proxy;/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.impl.DefaultBHttpClientConnection;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.protocol.*;
import org.apache.http.util.EntityUtils;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Random;

/**
 * Elemental example for executing multiple GET requests sequentially.
 */
public class ElementalHttpGet {

	static List<InetAddress> getAllIp(String networkInterface) {
		try {
			NetworkInterface interfaces = NetworkInterface.getByName(networkInterface);
			Enumeration<InetAddress> inetAddresses = interfaces.getInetAddresses();
			List<InetAddress> result = new ArrayList<InetAddress>();
			while (inetAddresses.hasMoreElements()) {
				InetAddress inetAddress = inetAddresses.nextElement();
				if (inetAddress instanceof Inet4Address) {
					result.add(inetAddress);
				}
			}
			return result;
		} catch (SocketException e) {
			throw new RuntimeException(e);
		}
	}

	private static Random random = new Random();

	public static void main(String[] args) throws Exception {
//		String localIp = args[0];
//		System.out.println("localIp: " + localIp);
		HttpProcessor httpproc = HttpProcessorBuilder.create()
				.add(new RequestContent())
				.add(new RequestTargetHost())
				.add(new RequestConnControl())
				.add(new RequestUserAgent("Test/1.1"))
				.add(new RequestExpectContinue(true)).build();

		HttpRequestExecutor httpexecutor = new HttpRequestExecutor();

		HttpCoreContext coreContext = HttpCoreContext.create();
		HttpHost host = new HttpHost("api.weibo.com", 443, "https");
		coreContext.setTargetHost(host);
//		for (InetAddress localinetAddress : getAllIp("eth0")) {
//			System.out.println(localinetAddress.getHostAddress());
		request(httpproc, httpexecutor, coreContext, host, InetAddress.getByName("60.169.74.152"));
//		}

	}

	private static void request(HttpProcessor httpproc, HttpRequestExecutor httpexecutor, HttpCoreContext coreContext, HttpHost host, InetAddress localinetAddress) throws NoSuchAlgorithmException, IOException, HttpException {
		DefaultBHttpClientConnection conn = new DefaultBHttpClientConnection(8 * 1024);
		ConnectionReuseStrategy connStrategy = DefaultConnectionReuseStrategy.INSTANCE;
		try {

			String[] targets = {
					"/2/users/show.json?access_token=2.00SlDQsDdcZIJC94e5308f67sRL13D&uid=3550148352"
					, "/account/rate_limit_status.json?access_token=2.00SlDQsDdcZIJC94e5308f67sRL13D"
			};

			for (int i = 0; i < targets.length; i++) {
				if (!conn.isOpen()) {
					SSLContext sslcontext = SSLContext.getInstance("Default");
//					sslcontext.init(null, null, null);
					SocketFactory sf = sslcontext.getSocketFactory();
					SSLSocket socket = (SSLSocket) sf.createSocket(host.getHostName(), host.getPort(), localinetAddress, 0);
					socket.setEnabledCipherSuites(new String[]{
							"TLS_RSA_WITH_AES_256_CBC_SHA",
							"TLS_DHE_RSA_WITH_AES_256_CBC_SHA",
							"TLS_DHE_DSS_WITH_AES_256_CBC_SHA"});
					conn.bind(socket);
//					Socket socket = new Socket(host.getHostName(), host.getPort());
//					conn.bind(socket);
				}
				BasicHttpRequest request = new BasicHttpRequest("GET", targets[i]);
				System.out.println(">> Request URI: " + request.getRequestLine().getUri());

				httpexecutor.preProcess(request, httpproc, coreContext);
				HttpResponse response = httpexecutor.execute(request, conn, coreContext);
				httpexecutor.postProcess(response, httpproc, coreContext);

				System.out.println("<< Response: " + response.getStatusLine());
				System.out.println(EntityUtils.toString(response.getEntity()));
				System.out.println("==============");
				if (!connStrategy.keepAlive(response, coreContext)) {
					conn.close();
				} else {
					System.out.println("Connection kept alive...");
				}
			}
		} finally {
			conn.close();
		}
	}

}