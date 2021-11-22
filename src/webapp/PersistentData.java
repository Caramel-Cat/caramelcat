package webapp;

import java.io.Serializable;

import org.json.JSONObject;

public class PersistentData implements Serializable {
	private static final long serialVersionUID = 1L;
	protected JSONObject data = new JSONObject();
}
