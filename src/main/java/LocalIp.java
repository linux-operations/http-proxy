import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Created by chenwei on 14-1-23.
 */
public class LocalIp {

	public static void main(String[] args) throws SocketException {
		System.out.println(getAllIp(args[0]));
	}

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

}
