package tw.com.exceptions;

@SuppressWarnings("serial")
public class CfnAssistException extends Exception {

	public CfnAssistException(String msg) {
		super(msg);
	}

	public CfnAssistException(String message, Throwable throwable) {
        super(message, throwable);
    }

}
