package proxy;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;

/**
 * Created by chenwei on 14-1-23.
 */
public class SocketTest {

	public static void main(String[] args) throws IOException, InterruptedException {
		Socket socket = new Socket("weibo.com", 80, InetAddress.getByName(args[0]), 0);
		OutputStream os = socket.getOutputStream();
		PrintWriter pw = new PrintWriter(os);
		InputStream is = socket.getInputStream();
		BufferedReader br = new BufferedReader(new InputStreamReader(is));

		pw.println("test");
		pw.flush();
		Thread.sleep(500);
		System.out.println(br.readLine());
		socket.close();
	}

}
