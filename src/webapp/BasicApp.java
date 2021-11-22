package webapp;

import org.json.JSONObject;

public abstract class BasicApp extends WebApp {

	public BasicApp(String[] args) {
		super(args);
	}

	@Override
	protected JSONObject exec(String user, JSONObject request) throws Exception {
		JSONObject response = new JSONObject();
		String method = request.getString("method");

		switch (method) {

		case "exit":
			if (validPassword(request)) exit();
			break;

		case "hello":
			response.put("hello", "world");
			break;

		case "error": // for testing
			if (validPassword(request)) throw new RuntimeException("something wrong");

		default:
			response = my_exec(user, request);
			break;

		}
		return response;
	}

	protected abstract JSONObject my_exec(String user, JSONObject request) throws Exception;

}
