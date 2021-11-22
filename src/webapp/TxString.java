package webapp;

public class TxString extends Tx {

	private static final long serialVersionUID = 1L;

	private String value;

	public TxString(String cmd, String key, String value) {
		super(cmd, key);
		this.value = value;
	}

	@Override
	protected Object getValue() {
		return value;
	}

}
