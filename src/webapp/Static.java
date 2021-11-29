package webapp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Stack;

import org.eclipse.jetty.util.URIUtil;
import org.json.JSONException;
import org.json.JSONObject;

// Boilerplate code. All static.
public class Static {

	// use setTime(methodName) at method beggining and endTime() at method ending to check if that method is slow
	private static Stack<Long> times = new Stack<>();
	private static Stack<String> timeName = new Stack<>();

	private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

	public static String bytesToHex(byte[] bytes) {
		char[] hexChars = new char[bytes.length * 2];
		for (int j = 0; j < bytes.length; j++) {
			int v = bytes[j] & 0xFF;
			hexChars[j * 2] = HEX_ARRAY[v >>> 4];
			hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
		}
		return new String(hexChars);
	}

	public static void endTime() {
		String name = timeName.pop();
		Long start = times.pop();
		Long end = System.currentTimeMillis();
		System.out.println(name + ":" + (end - start));
	}

	public static JSONObject getJSON(BufferedReader reader) throws Exception {
		JSONObject jsonObject = null;
		StringBuffer jb = new StringBuffer();
		String line = null;

		try {
			while ((line = reader.readLine()) != null)
				jb.append(line);
		} catch (Exception e) {
		}

		try {
			line = jb.toString();
			line = line.replaceAll("json=&", "");
			if (line.endsWith("&json=")) line = line.substring(0, line.length() - 6);
			// if form post then get json form field
			if (line.contains("json=")) {
				Map<String, String> m = splitToMap(line, "&", "=");
				line = m.get("json");
				line = URIUtil.decodePath(line);
				line = line.replace("+", " ");
				// line = URLDecoder.decode(line, "US-ASCII");

				jsonObject = new JSONObject(line);
				jsonObject.remove("json");
			} else {
				if ("".equals(line)) line = "{}";
				jsonObject = new JSONObject(line);
			}
		} catch (JSONException e) {
		} finally {
		}
		return jsonObject;
	}

	public static String getSaltString(int size) {
		String SALTCHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890";
		StringBuilder salt = new StringBuilder();
		Random rnd = new Random();
		while (salt.length() < size) { // length of the random string.
			int index = (int) (rnd.nextFloat() * SALTCHARS.length());
			salt.append(SALTCHARS.charAt(index));
		}
		String saltStr = salt.toString();
		return saltStr;

	}

	public static byte[] hexStringToByteArray(String s) {
		int len = s.length();
		byte[] data = new byte[len / 2];
		for (int i = 0; i < len; i += 2) {
			data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
		}
		return data;
	}

	public static boolean isNumeric(String strNum) {
		if (strNum == null) {
			return false;
		}
		try {
			long l = Long.parseLong(strNum);
		} catch (NumberFormatException nfe) {
			return false;
		}
		return true;
	}

	public static synchronized JSONObject request(JSONObject json) throws IOException {
		String ret = null;
		URL url = new URL("https://caramelcat.org/meow");
		HttpURLConnection con = (HttpURLConnection) url.openConnection();
		con.setRequestMethod("POST");
		con.setRequestProperty("Content-Type", "application/json; utf-8");
		con.setRequestProperty("Accept", "application/json");
		con.setDoOutput(true);
		try (OutputStream os = con.getOutputStream()) {
			byte[] input = json.toString().getBytes("utf-8");
			os.write(input, 0, input.length);
		}

		try (BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream(), "utf-8"))) {
			StringBuilder response = new StringBuilder();
			String responseLine = null;
			while ((responseLine = br.readLine()) != null) {
				response.append(responseLine.trim());
			}
			ret = response.toString();
		}
		return new JSONObject(ret);
	}

	public static void setTime(String name) {
		times.push(System.currentTimeMillis());
		timeName.push(name);
	}

	public static Map<String, String> splitToMap(String source, String entriesSeparator, String keyValueSeparator) {
		Map<String, String> map = new HashMap<String, String>();
		String[] entries = source.split(entriesSeparator);
		for (String entry : entries) {
			if (entry != null && entry.contains(keyValueSeparator)) {
				String[] keyValue = entry.split(keyValueSeparator);
				map.put(keyValue[0], keyValue[1]);
			}
		}
		return map;
	}

}
