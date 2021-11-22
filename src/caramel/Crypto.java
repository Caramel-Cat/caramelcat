package caramel;

import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.Signature;
import java.security.SignatureException;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.EllipticCurve;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

import org.bouncycastle.jcajce.provider.asymmetric.util.EC5Util;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.ECPointUtil;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.math.ec.ECCurve;

public class Crypto {

	private static final MessageDigest sha256;
	private static final ECNamedCurveParameterSpec ecExternalNamedCurveParameterSpec = ECNamedCurveTable.getParameterSpec("secp256r1");
	private static final ECCurve curve = ecExternalNamedCurveParameterSpec.getCurve();
	private static final EllipticCurve ellipticCurve = EC5Util.convertCurve(curve, ecExternalNamedCurveParameterSpec.getSeed());
	private static final KeyFactory externalKeyFactor;
	private static final ECParameterSpec ecParameterSpec = EC5Util.convertSpec(ellipticCurve, ecExternalNamedCurveParameterSpec);
	private static final Signature ecdsa;

	static {
		try {
			Security.addProvider(new BouncyCastleProvider());
			sha256 = MessageDigest.getInstance("SHA-256");
			externalKeyFactor = java.security.KeyFactory.getInstance("EC");
			ecdsa = Signature.getInstance("SHA256withECDSAinP1363format");
		} catch (final NoSuchAlgorithmException e) {
			throw new RuntimeException(e.getMessage());
		}
	}

	static KeyPair genKey() throws Exception {
		KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
		ECGenParameterSpec kpgparams = new ECGenParameterSpec("secp256r1");
		g.initialize(kpgparams);
		return g.generateKeyPair();
	}

	static PrivateKey getPrivateKey(String privateKeyBase64) throws Exception {
		byte[] encodedPriv = Base64.getUrlDecoder().decode(privateKeyBase64);
		PKCS8EncodedKeySpec pkcs8EncodedKeySpec = new PKCS8EncodedKeySpec(encodedPriv);
		return externalKeyFactor.generatePrivate(pkcs8EncodedKeySpec);
	}

	static PublicKey getPublicKey(String externalRawPublicKey) throws Exception {
		byte[] ecRawExternalPublicKey = Base64.getUrlDecoder().decode(externalRawPublicKey);
		ECPublicKey ecExternalPublicKey = null;
		ECPoint ecPoint = ECPointUtil.decodePoint(ellipticCurve, ecRawExternalPublicKey);
		ECPublicKeySpec externalPublicKeySpec = new ECPublicKeySpec(ecPoint, ecParameterSpec);
		ecExternalPublicKey = (ECPublicKey) externalKeyFactor.generatePublic(externalPublicKeySpec);
		return ecExternalPublicKey;
	}

	static String sha256d(String str) throws UnsupportedEncodingException {
		byte[] shaBytes = sha256.digest(sha256.digest(str.getBytes()));
		return Base64.getUrlEncoder().encodeToString(shaBytes).replace("=", "");
	}

	static String sign(PrivateKey privateKey, String txt) throws InvalidKeyException, SignatureException, UnsupportedEncodingException {
		String sign = "";
		synchronized (ecdsa) {
			ecdsa.initSign(privateKey);
			byte[] strByte = txt.getBytes("UTF-8");
			ecdsa.update(strByte);
			byte[] realSig = ecdsa.sign();
			sign = Base64.getUrlEncoder().encodeToString(realSig).replace("=", "");
		}
		return sign;
	}

	static boolean verify(final PublicKey publicKey, final String txt, final String signatureString) throws Exception {
		final byte[] signature = Base64.getUrlDecoder().decode(signatureString);
		synchronized (ecdsa) {
			ecdsa.initVerify(publicKey);
			byte[] txBytes = txt.getBytes("UTF-8");
			ecdsa.update(txBytes);
			return ecdsa.verify(signature);
		}
	}

}
