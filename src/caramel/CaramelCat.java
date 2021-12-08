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

	private static long supply = 0;

	// server control new coins
	private static Map<String, String> coins = new HashMap<>();
	private static Map<String, String> minePrefix = new HashMap<>();

	// wallet mining
	private static String mine_prefix;
	private static String mine_address;

	// are we running in server or wallet mode?
	private static boolean walletMode = true;
	private static String nodeName;

	// wallet mode + valid mine address = mining mode
	private static boolean isMiningMode() {
		if (walletMode && mine_address != null && mine_address.length() == 43) return true;
		else return false;
	}

	private static boolean isServerMode() {
		return !walletMode;
	}

	// start mining thread. sleep each 10s or mine if ready
	private static void prepare_miner() {
		new Thread() {
			@Override
			public void run() {
				long l = Long.MIN_VALUE;
				Random random = new Random();
				while (true) {
					try {
						if (readyToMine()) {
							Long nonce = random.nextLong();
							String text = nonce + "_" + mine_address;
							String hash = Crypto.sha256d(text);
							String upper = hash.toUpperCase();

							if (upper.startsWith(mine_prefix)) {
								JSONObject json = new JSONObject();
								json.put("method", "insertcoin");
								json.put("address", mine_address);
								json.put("nonce", nonce);
								json = Static.request(json);
								if (json.has("status") && json.getString("status").equals("success")) {
									print("Yeah! New caca coin!" + hash.substring(0, 6) + "..");
								}
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

	// mining mode + prefix = you are ready to mine
	private static boolean readyToMine() {
		return mine_prefix != null && isMiningMode();
	}

	public static void main(String[] args) throws Exception {
		if (nodeName != null) return;
		nodeName = Static.getSaltString(16);

		// start http server and load database
		CaramelCat c = (CaramelCat) new CaramelCat().init();

		// debug = server mode
		if (c.debug()) {
			print("DEBUG (Server mode on)");
			walletMode = false;
			return;
		}

		// are you caramelcat.org?
		JSONObject json = new JSONObject();
		json.put("method", "isYou");
		json.put("nodeName", nodeName);
		json = Static.request(json);

		// if you are caramelcat.org then disable wallet mode
		boolean isYou = json.getBoolean("status");
		if (isYou) {
			print("WARN: Wallet mode disabled. Server mode on.");
			walletMode = false;
			c.prepareSnapshot();
		}

		if (walletMode) prepare_miner();

		// start mining
		if (args.length == 1 && args[0].length() == 43) {
			print("starting mining..");
			mine_address = args[0];
		}
	}

	private JSONObject all(String user, JSONObject request) throws Exception {
		return get();
	}

	private JSONObject getbalance(String user, JSONObject request) throws Exception {
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
		if (supply >= 1_000_000_0000L) return response;

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

		if (upper.startsWith(prefix)) {
			Long balance = getLong(address);

			put(address, ++balance);
			supply++;
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
		response.put("supply", supply);

		int prefixSize = 2;
		String prefix = Static.getSaltString(prefixSize);

		minePrefix.put(user, prefix);
		response.put("prefix", prefix);
		if (debug()) response.put("debug", true);

		return response;
	}

	private JSONObject sendtoaddress(String user, JSONObject request) throws Exception {
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

		if (balance >= (amount + 1)) { // +1 (tx fee)
			PublicKey fromPubKey = Crypto.getPublicKey(fromStrPubKey);
			if (Crypto.verify(fromPubKey, fromAddress + to + amount, sign)) {
				Long change = balance - (amount + 1);
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
		if (isServerMode()) return true;
		else return false;
	}

	@Override
	protected boolean debug() {
		return false;
	}

	@Override
	protected void each10Seconds() {
		minePrefix.clear();
		coins.clear();
	}

	@Override
	protected void each3Seconds() {
		try {
			// if mining mode then get mine_prefix to start mine
			if (isMiningMode()) {
				JSONObject json = new JSONObject();
				json.put("method", "ping");
				json.put("address", mine_address);
				json = Static.request(json);
				if (!json.has("prefix")) mine_prefix = null;
				else {
					mine_prefix = json.getString("prefix");
					print(mine_prefix);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

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
	protected JSONObject my_exec(String method, String user, JSONObject request) throws Exception {

		switch (method) {

		case "insertcoin":
			if (isServerMode()) return insertcoin(user, request);
			break;

		case "transfer":
			if (isServerMode()) return transfer(user, request);
			break;

		case "ping":
			if (isServerMode()) return ping(user, request);
			break;

		case "all":
			if (isServerMode()) return all(user, request);
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

		throw new IllegalArgumentException("Unexpected value");
	}

	@Override
	protected void prepareSnapshot() {
		if (isServerMode()) {
			JSONObject data = get();
			String[] keys = JSONObject.getNames(data);
			long balance = 0;
			for (String k : keys) {
				Object value = data.get(k);
				// if invalid remove, else recalculate supply
				if (k.length() != 43 || !(value instanceof Number)) {
					data.remove(k);
				} else if (k.length() == 43 && value instanceof Number) {
					balance = ((Number) value).longValue();
					if (balance == 0) data.remove(k);
					else supply = supply + balance;
				}
			}
		}
	}
}
