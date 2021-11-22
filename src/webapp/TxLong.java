package webapp;

public class TxLong extends Tx {

	private static final long serialVersionUID = 1L;

	private Long value;

	public TxLong(String cmd, String key, Long value) {
		super(cmd, key);
		this.value = value;
	}

	@Override
	protected Object getValue() {
		return value;
	}

}
