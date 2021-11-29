package caramel;

import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.json.JSONObject;

import webapp.BasicApp;
import webapp.Static;

public class CaramelCat extends BasicApp {

	private static Map<String, String> coins = new HashMap<>();
	private static Map<String, String> minePrefix = new HashMap<>();
	private static String mine_prefix;
	private static String mine_address;

	// start mining thread
	private static void prepare_miner() {
		new Thread() {
			@Override
			public void run() {
				long l = Long.MIN_VALUE;
				while (true) {
					try {
						if (mine_address != null && mine_prefix != null && mine_address.length() == 43) {
							Long nonce = new Random().nextLong();
							String text = nonce + "_" + mine_address;
							String hash = Crypto.sha256d(text);
							String upper = hash.toUpperCase();

							if (upper.startsWith("CAT" + mine_prefix)) {
								print("Yeah! New cat coin! " + hash.substring(0, 6));
								JSONObject json = new JSONObject();
								json.put("method", "insertcoin");
								json.put("address", mine_address);
								json.put("nonce", nonce);
								json = Static.request(json);
							}
							l++;
							if (l % 1_000_000 == 0) print("mining..");

						} else {
							Thread.sleep(10_000);
						}
					} catch (Exception e) {
						print(e.toString());
					}
				}
			}
		}.start();
	}

	public static void main(String[] args) throws Exception {
		CaramelCat c = (CaramelCat) new CaramelCat().init();

		// are you caramelcat.org?
		c.nodeName = Static.getSaltString(16);
		JSONObject json = new JSONObject();
		json.put("method", "isYou");
		json.put("nodeName", c.nodeName);
		json = Static.request(json);

		// if true disable wallet mode
		boolean isYou = json.getBoolean("status");
		if (isYou) {
			print("WARN: Wallet mode disabled");
			c.walletMode = false;
		}

		if (c.debug()) {
			print("DEBUG (Server mode)");
			c.walletMode = false;
		}

		if (c.walletMode) prepare_miner();

		// start mining
		if (args.length == 1 && args[0].length() == 43) {
			print("starting mining..");
			mine_address = args[0];
		}
	}

	private boolean walletMode = true;

	private String nodeName;

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
		if (supply >= 1_000_000) return response;

		// must has a prefix to mine (see ping)
		String prefix = minePrefix.get(user);
		if (prefix == null) return response;

		// check address size
		String address = request.getString("address");
		if (address.length() != 43) return response;

		// get hash
		Long nonce = request.getLong("nonce");
		String text = nonce + "_" + address;
		String hash = Crypto.sha256d(text);

		// was this coin already mined?
		if (coins.get(hash) != null) return response;

		String upper = hash.toUpperCase();

		if (upper.startsWith("CAT" + prefix)) {
			Long balance = getLong(address);

			put(address, ++balance);
			put("supply", ++supply);
			coins.put(hash, text);

			response.put("status", "success");
		} else throw new Exception("invalid coin");

		return response;
	}

	private JSONObject isYou(String user, JSONObject request) {
		JSONObject response = new JSONObject();
		response.put("status", false);
		if (request.getString("nodeName").equals(nodeName)) response.put("status", true);
		return response;
	}

	private JSONObject mine(String user, JSONObject request) {
		JSONObject response = new JSONObject();
		response.put("status", "error");

		String address = request.getString("address");
		if (address.length() == 43) {
			print("starting mining..");
			mine_address = address;
			response.put("status", "ok");
		} else {
			print("stoping mining..");
			mine_address = null;
			response.put("status", "ok");
		}
		return response;
	}

	private JSONObject ping(String user, JSONObject request) throws Exception {
		String address = request.getString("address");
		JSONObject response = new JSONObject();

		Long balance = getLong(address);

		response.put("balance", balance);
		response.put("supply", getLong("supply"));

		int prefixSize = 3;
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

		if (fromAddress.length() != 43 || to.length() != 43) return response;
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
	protected void each10Seconds() {
		try {
			if (walletMode && mine_address != null && mine_address.length() == 43) {
				JSONObject json = new JSONObject();
				json.put("method", "ping");
				json.put("address", mine_address);
				json = Static.request(json);
				mine_prefix = json.getString("prefix");
				print(mine_prefix);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

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

			case "mine":
				if (walletMode) return mine(user, request);
				break;

			case "isYou":
				return isYou(user, request);

			default:
				throw new IllegalArgumentException("Unexpected value: " + method);
			}
		}

		throw new IllegalArgumentException("Unexpected value");
	}

	@Override
	protected void prepareSnapshot() {
		if (!walletMode) {
			JSONObject data = get();
			String[] keys = JSONObject.getNames(data);
			Long supply = 0L;
			for (String k : keys) {
				Object value = data.get(k);
				// if invalid remove, else recalculate supply
				if (!k.equals("supply") && k.length() != 43 || !(value instanceof Number)) {
					data.remove(k);
				} else if (k.length() == 43 && value instanceof Number) {
					supply = supply + ((Number) value).longValue();
				}
			}
			if (supply > 0) data.put("supply", supply);
		}
	}
}
