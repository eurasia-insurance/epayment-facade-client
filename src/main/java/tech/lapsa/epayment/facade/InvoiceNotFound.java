package tech.lapsa.epayment.facade;

public class InvoiceNotFound extends Exception {

    private static final long serialVersionUID = 1L;

    public InvoiceNotFound() {
	super();
    }

    public InvoiceNotFound(final String message, final Throwable cause) {
	super(message, cause);
    }

    public InvoiceNotFound(final String s) {
	super(s);
    }

    public InvoiceNotFound(final Throwable cause) {
	super(cause);
    }

}
