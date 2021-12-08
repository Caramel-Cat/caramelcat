package webapp;

import java.io.IOException;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Hashtable;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ShutdownHandler;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.json.JSONObject;
import org.prevayler.Prevayler;
import org.prevayler.PrevaylerFactory;
import org.prevayler.foundation.serialization.XStreamSerializer;

public abstract class WebApp {

	public static class MyHandler extends DefaultServlet {

		private static final long serialVersionUID = 1L;

		private JSONObject doIt(HttpSession user, JSONObject requestJSON) {
			JSONObject responseJSON = new JSONObject();

			if (!requestJSON.has("method")) return null;

			try {
				String userID = user.getId();
				responseJSON = webapp.exec(userID, requestJSON);

			} catch (Exception e) {
				responseJSON.put("status", "error");
				responseJSON.put("error", e.getMessage());
			}

			return responseJSON;
		}

		@Override
		protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

			String strResponse = null;
			try {
				JSONObject requestJSON;

				requestJSON = Static.getJSON(request.getReader());

				// try avoid denial of service attack
				// floodIps is cleaned each 10 seconds
				if (webapp.avoidDoS()) {
					String ip = request.getRemoteAddr();
					Long l = floodIps.get(ip);
					if (l == null) l = 0L;
					floodIps.put(ip, ++l);
					if (l >= 20) {
						// show to decide block this ip or not
						print("doGet-floodIps:" + ip);
						return;
					}
				}

				JSONObject responseJSON = null;

				if (requestJSON != null) {

					responseJSON = doIt(request.getSession(), requestJSON);
					if (responseJSON == null) return;

					strResponse = (responseJSON == null ? "" : responseJSON.toString());
					response.addHeader("Access-Control-Allow-Origin", "*");
					response.setContentType("application/json");
					response.setCharacterEncoding("UTF-8");
					response.getWriter().print(strResponse);
				}

			} catch (Exception e) {
				print(e.getMessage());
			}
		}
	}

	private static Map<String, Long> floodIps = new Hashtable<String, Long>();

	private static WebApp webapp;

	private static Prevayler<PersistentData> prevayler;

	private static boolean stop = false;

	private static void startJettyHttpsServer() throws MalformedURLException, IOException, URISyntaxException {
		System.setProperty("org.eclipse.jetty.LEVEL", "INFO");

		Server server = new Server();

		HttpConfiguration https = new HttpConfiguration();
		https.addCustomizer(new SecureRequestCustomizer());

		if (WebApp.class.getResource("/key.jks") != null) {
			SslContextFactory sslContextFactory = new SslContextFactory();
			sslContextFactory.setKeyStorePath(WebApp.class.getResource("/key.jks").toExternalForm());
			sslContextFactory.setKeyStorePassword(webapp.getPasswordSSL());
			sslContextFactory.setKeyManagerPassword(webapp.getPasswordSSL());

			ServerConnector sslConnector = new ServerConnector(server, new SslConnectionFactory(sslContextFactory, "http/1.1"), new HttpConnectionFactory(https));
			sslConnector.setPort(443);
			server.addConnector(sslConnector);
		}

		ServerConnector connector = new ServerConnector(server);
		connector.setPort(9443);

		server.addConnector(connector);

		ClassLoader cl = WebApp.class.getClassLoader();
		URL f = cl.getResource("html");
		if (f == null) {
			throw new RuntimeException("Unable to find resource directory");
		}

		URI webRootUri = f.toURI();

		ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
		context.setBaseResource(Resource.newResource(webRootUri));
		context.setContextPath("/");

		ServletHolder holderDynamic = new ServletHolder("dynamic", MyHandler.class);
		context.addServlet(holderDynamic, "/" + WebApp.webapp.getServletName() + "/*");

		ServletHolder holderPwd = new ServletHolder("default", DefaultServlet.class);
		holderPwd.setInitParameter("dirAllowed", "true");
		context.addServlet(holderPwd, "/");

		ShutdownHandler shutdown = new ShutdownHandler(WebApp.webapp.password, false, true) {
			@Override
			protected void doShutdown(Request baseRequest, HttpServletResponse response) throws IOException {
				WebApp.webapp.exit();
				super.doShutdown(baseRequest, response);
			}
		};

		HandlerList handlers = new HandlerList();
		handlers.setHandlers(new Handler[] { shutdown, context });
		server.setHandler(handlers);

		try {
			server.start();
			server.dump(System.err);
			// server.join();
		} catch (Throwable t) {
			t.printStackTrace(System.err);
		}
	}

	protected static void print(String s) {
		System.out.println(s);
	}

	private final String password;

	protected WebApp() {
		CookieManager cookieManager = new CookieManager();
		CookieHandler.setDefault(cookieManager);
		password = Static.getSaltString(16);
		print("Your Password: " + password);
	}

	private void startCron() {
		new Thread() {

			@Override
			public void run() {
				print("starting cron..");
				long l = 0L;
				while (true) {
					try {
						Thread.sleep(1_000); // 1s
						if (WebApp.stop) {
							print("stopping cron..");
							return;
						}
						l++;
						if (l % 86400 == 0) {
							eachDay();
							takeSnapshot();
						}
						if (l % 3600 == 0) eachHour();
						if (l % 120 == 0) each2Minutes();
						if (l % 60 == 0) eachMinute();
						if (l % 10 == 0) {
							each10Seconds();
							floodIps.clear();
						}
						if (l % 3 == 0) each3Seconds();
						eachSecond();

						if (l == Long.MAX_VALUE) l = 0L;

					} catch (Exception e) {
						print(e.toString());
					}
				}
			}

		}.start();
	}

	protected boolean avoidDoS() {
		return true;
	}

	protected boolean debug() {
		return false;
	}

	protected void each10Seconds() {
	}

	protected void each2Minutes() {
	}

	protected void each3Seconds() {
	}

	protected void eachDay() {
	}

	protected void eachHour() {
	}

	protected void eachMinute() {
	}

	protected void eachSecond() {
	}

	protected abstract JSONObject exec(String user, JSONObject request) throws Exception;

	protected void exit() {
		print("exiting..");
		stop = true;
	}

	protected JSONObject get() {
		return prevayler.prevalentSystem().data;
	}

	protected Long getLong(String key) {
		JSONObject data = prevayler.prevalentSystem().data;
		if (data.has(key) && data.get(key) instanceof Number) return data.getLong(key);
		else return 0L;
	}

	protected abstract String getPasswordSSL();

	protected String getServletName() {
		return "servlet";
	}

	protected String getString(String key) {
		JSONObject data = prevayler.prevalentSystem().data;
		if (data.has(key)) return data.getString(key);
		else return null;
	}

	protected WebApp init() throws Exception {
		print("system starting..");

		if (WebApp.webapp != null) throw new Exception("Only one WebApp, please!");

		WebApp.webapp = this;
		PrevaylerFactory<PersistentData> factory = new PrevaylerFactory<>();
		factory.configurePrevalentSystem(new PersistentData());
		factory.configureJournalSerializer("journal", new XStreamSerializer());
		factory.configureSnapshotSerializer("snapshot", new XStreamSerializer());
		// if (WebApp.webapp.debug()) factory.configureTransientMode(true);
		prevayler = factory.create();

		startJettyHttpsServer();
		startCron();

		print("system started!");
		return this;
	}

	protected abstract void prepareSnapshot();

	protected void put(String key, Long value) {
		prevayler.execute(new TxLong("put", key, value));
	}

	protected void put(String key, String value) {
		prevayler.execute(new TxString("put", key, value));
	}

	protected void remove(String key) {
		prevayler.execute(new Tx("remove", key));
	}

	protected void takeSnapshot() throws Exception {
		prepareSnapshot();
		prevayler.takeSnapshot();
	}

	protected boolean validPassword(JSONObject request) {
		if (password == null || (request.has("password") && password.equals(request.getString("password")))) return true;
		else return false;
	}

}