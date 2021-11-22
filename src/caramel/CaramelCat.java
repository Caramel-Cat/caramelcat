package caramel;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONObject;

import webapp.BasicApp;
import webapp.Static;

public class CaramelCat extends BasicApp {

	private static Map<String, String> coins = new HashMap<>();
	private static Map<String, String> minePrefix = new HashMap<>();
	private static Map<String, Long> coinsPerDay = new HashMap<>();

	public static void main(String[] args) throws Exception {
		CaramelCat c = (CaramelCat) new CaramelCat(args).init();
		if (args.length == 0) c.walletMode = true;
	}

	private boolean walletMode = false;

	public CaramelCat(String[] args) {
		super(args);
	}

	private JSONObject all(String user, JSONObject request) throws Exception {
		return get();
	}

	private synchronized JSONObject getbalance(String user, JSONObject request) throws Exception {
		request.put("method", "ping");
		JSONObject response = Static.request(request);
		response.remove("supply");
		response.remove("prefix");
		response.remove("debug");
		return response;
	}

	private synchronized JSONObject getnewaddress(String user, JSONObject request) throws Exception {
		String address = null;
		String pubkey64 = null;
		KeyPair key = null;

		while (true) {
			key = Crypto.genKey();
			ECPublicKey ecpub = (ECPublicKey) key.getPublic();
			BigInteger pointx = ecpub.getW().getAffineX();
			BigInteger pointy = ecpub.getW().getAffineY();
			String pubkeyHex = ("04" + pointx.toString(16) + pointy.toString(16)).toUpperCase();

			if (pubkeyHex.length() != 130) continue;
			pubkey64 = Base64.getUrlEncoder().encodeToString(Static.hexStringToByteArray(pubkeyHex));
			address = Crypto.sha256d(pubkey64);
			if (!address.contains("-") && !address.contains("_")) break;
		}

		String privkey64 = Base64.getUrlEncoder().encodeToString(key.getPrivate().getEncoded());
		put(address, pubkey64);
		put(pubkey64, privkey64);

		JSONObject response = new JSONObject();
		response.put("address", address);
		return response;
	}

	private synchronized JSONObject insertcoin(String user, JSONObject request) throws Exception {
		JSONObject response = new JSONObject();
		response.put("status", "error");

		// should we have max supply?
		Long supply = getLong("supply");
		if (supply >= 512_000) return response;

		// must has a prefix to mine (see ping)
		String prefix = minePrefix.get(user);
		if (prefix == null) return response;

		// max 100 coins per user (http session)
		Long count = coinsPerDay.get(user);
		if (count != null && count > 100) return response;

		// get hash
		Long nonce = request.getLong("nonce");
		String address = request.getString("address");
		String text = nonce + "_" + address;
		String hash = Crypto.sha256d(nonce + "_" + address);

		// was this coin already mined?
		if (coins.get(hash) != null) return response;

		String upper = hash.toUpperCase();

		if (upper.startsWith(prefix) || (debug() && upper.startsWith("C"))) {
			Long balance = getLong(address);

			put(address, ++balance);
			put("supply", ++supply);
			coins.put(hash, text);

			if (count == null) count = 0L;
			else count++;
			coinsPerDay.put(user, count);

			response.put("status", "success");
		} else throw new Exception("invalid coin");

		return response;
	}

	private JSONObject ping(String user, JSONObject request) throws Exception {
		String address = request.getString("address");
		JSONObject response = new JSONObject();

		response.put("balance", getLong(address));
		response.put("supply", getLong("supply"));

		int prefixSize = 3;
		if (debug()) prefixSize = 2;
		String prefix = Static.getSaltString(prefixSize);

		minePrefix.put(user, prefix);
		response.put("prefix", prefix);
		if (debug()) response.put("debug", true);

		return response;
	}

	private synchronized JSONObject sendtoaddress(String user, JSONObject request) throws Exception {
		String from = request.getString("from");
		String to = request.getString("to");
		Long amount = request.getLong("amount");

		String pubkey64 = getString(from);
		String privkey64 = getString(pubkey64);

		PrivateKey privateKey = Crypto.getPrivateKey(privkey64);
		String sign = Crypto.sign(privateKey, from + to + amount);

		request.put("method", "transfer");
		request.put("from", pubkey64);
		request.put("sign", sign);

		return Static.request(request);
	}

	private synchronized JSONObject transfer(String user, JSONObject request) throws Exception {
		JSONObject response = new JSONObject();
		response.put("status", "error");

		String fromStrPubKey = request.getString("from");
		String to = request.getString("to");
		Long amount = request.getLong("amount");
		String sign = request.getString("sign");

		String fromAddress = Crypto.sha256d(fromStrPubKey);
		Long balance = getLong(fromAddress);

		if (fromAddress.equals(to)) return response;
		if (sign.length() < 10) return response;
		if (amount <= 0) return response;

		if (balance >= amount) {
			PublicKey fromPubKey = Crypto.getPublicKey(fromStrPubKey);
			if (Crypto.verify(fromPubKey, fromAddress + to + amount, sign)) {
				Long change = balance - amount;
				Long update = getLong(to) + amount;

				put(to, update);
				put(fromAddress, change);
				response.put("status", "success");
			}
		}
		return response;
	}

	@Override
	protected boolean avoidDoS() {
		if (walletMode) return false;
		else return true;
	}

	@Override
	protected boolean debug() {
		return false;
	}

	@Override
	protected void each2Minutes() {
		minePrefix.clear();
	}

	@Override
	protected String getPasswordSSL() {
		return "123456";
	}

	@Override
	protected String getServletName() {
		return "meow";
	}

	@Override
	protected JSONObject my_exec(String user, JSONObject request) throws Exception {

		if (request.has("method")) {
			String method = request.getString("method");
			switch (method) {

			case "insertcoin":
				if (!walletMode) return insertcoin(user, request);
				break;

			case "transfer":
				if (!walletMode) return transfer(user, request);
				break;

			case "ping":
				if (!walletMode) return ping(user, request);
				break;

			case "all":
				if (!walletMode) return all(user, request);
				break;

			case "getnewaddress":
				if (walletMode) return getnewaddress(user, request);
				break;

			case "sendtoaddress":
				if (walletMode) return sendtoaddress(user, request);
				break;

			case "getbalance":
				if (walletMode) return getbalance(user, request);
				break;

			case "snapshot":
				if (validPassword(request)) takeSnapshot();
				break;

			default:
				throw new IllegalArgumentException("Unexpected value: " + method);
			}
		}

		throw new IllegalArgumentException("Unexpected value");
	}

	@Override
	protected void prepareSnapshot() {
		coinsPerDay.clear();
		JSONObject data = get();
		String[] keys = JSONObject.getNames(data);
		for (String k : keys) {
			Object value = data.get(k);
			if (!k.equals("supply") && k.length() != 43 || !(value instanceof Number)) {
				data.remove(k);
			}
		}
	}
}
