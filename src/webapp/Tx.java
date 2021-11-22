package webapp;

import java.util.Date;

class Tx implements org.prevayler.Transaction<PersistentData> {

	private static final long serialVersionUID = 1L;

	private String cmd;
	private String key;

	public Tx(String cmd, String key) {
		super();
		this.cmd = cmd;
		this.key = key;
	}

	protected Object getValue() {
		return null;
	}

	@Override
	public void executeOn(PersistentData prevalentSystem, Date executionTime) {
		if ("put".equals(cmd)) {
			prevalentSystem.data.put(key, getValue());
		} else if ("remove".equals(cmd)) {
			prevalentSystem.data.remove(key);
		}
	}
}
