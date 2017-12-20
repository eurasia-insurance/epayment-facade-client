package tech.lapsa.epayment.facade;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.ejb.Local;
import javax.ejb.Remote;

import tech.lapsa.epayment.domain.Invoice;
import tech.lapsa.java.commons.exceptions.IllegalArgument;
import tech.lapsa.java.commons.function.MyObjects;
import tech.lapsa.java.commons.function.MyStrings;

public interface NotificationFacade extends EJBConstants {

    public static final String BEAN_NAME = "NotificationFacadeBean";

    @Local
    public interface NotificationFacadeLocal extends NotificationFacade {
    }

    @Remote
    public interface NotificationFacadeRemote extends NotificationFacade {
    }

    void send(Notification notification) throws IllegalArgument;

    public static final class Notification implements Serializable {

	private static final long serialVersionUID = 1L;

	public static enum NotificationChannel {
	    EMAIL;
	}

	public static enum NotificationRecipientType {
	    COMPANY, REQUESTER;
	}

	public static enum NotificationEventType {
	    PAYMENT_LINK, PAYMENT_SUCCESS;
	}

	public static NotificationBuilder builder() {
	    return new NotificationBuilder();
	}

	public static final class NotificationBuilder implements Serializable {

	    private static final long serialVersionUID = 1L;

	    private NotificationChannel channel;
	    private NotificationRecipientType recipientType;
	    private NotificationEventType event;
	    private Invoice entity;
	    private final Map<String, String> properties = new HashMap<>();

	    private NotificationBuilder() {
	    }

	    public NotificationBuilder withChannel(final NotificationChannel channel) throws IllegalArgument {
		this.channel = MyObjects.requireNonNull(IllegalArgument::new, channel, "channel");
		return this;
	    }

	    public NotificationBuilder withRecipient(final NotificationRecipientType recipientType)
		    throws IllegalArgument {
		this.recipientType = MyObjects.requireNonNull(IllegalArgument::new, recipientType, "recipientType");
		return this;
	    }

	    public NotificationBuilder withEvent(final NotificationEventType event) throws IllegalArgument {
		this.event = MyObjects.requireNonNull(IllegalArgument::new, event, "event");
		return this;
	    }

	    public NotificationBuilder forEntity(final Invoice entity) throws IllegalArgument {
		this.entity = MyObjects.requireNonNull(IllegalArgument::new, entity, "entity");
		return this;
	    }

	    public NotificationBuilder withProperty(final String key, final String value)
		    throws IllegalArgument {
		properties.put(MyStrings.requireNonEmpty(IllegalArgument::new, key, "key"),
			MyStrings.requireNonEmpty(IllegalArgument::new, value, "value"));
		return this;
	    }

	    public Notification build() throws IllegalArgument {
		return new Notification(channel, recipientType, event, entity, properties);
	    }

	}

	private final NotificationChannel channel;
	private final NotificationRecipientType recipientType;
	private final NotificationEventType event;
	private final Invoice entity;
	private final Map<String, String> propsMap;

	private Notification(final NotificationChannel channel, final NotificationRecipientType recipientType,
		final NotificationEventType event, final Invoice entity, final Map<String, String> propsMap)
		throws IllegalArgument {
	    this.channel = MyObjects.requireNonNull(IllegalArgument::new, channel, "channel");
	    this.recipientType = MyObjects.requireNonNull(IllegalArgument::new, recipientType, "recipientType");
	    this.event = MyObjects.requireNonNull(IllegalArgument::new, event, "event");
	    this.entity = MyObjects.requireNonNull(IllegalArgument::new, entity, "entity");
	    this.propsMap = Collections
		    .unmodifiableMap(MyObjects.requireNonNull(IllegalArgument::new, propsMap, "propsMap"));
	}

	public NotificationChannel getChannel() {
	    return channel;
	}

	public NotificationRecipientType getRecipientType() {
	    return recipientType;
	}

	public NotificationEventType getEvent() {
	    return event;
	}

	public Invoice getEntity() {
	    return entity;
	}

	public Properties getProperties() {
	    if (propsMap.isEmpty())
		return null;
	    final Properties p = new Properties();
	    propsMap.entrySet().stream() //
		    .forEach(x -> p.setProperty(x.getKey(), x.getValue()));
	    return p;
	}
    }
}
