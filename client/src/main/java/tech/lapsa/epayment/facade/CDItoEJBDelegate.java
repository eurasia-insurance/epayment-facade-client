package tech.lapsa.epayment.facade;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.*;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import javax.inject.Qualifier;

@Qualifier
@Retention(RUNTIME)
@Target({ FIELD, TYPE, METHOD })
public @interface CDItoEJBDelegate {

    public static final Annotation QUALIFIER_INSTANCE = new CDItoEJBDelegate() {

	@Override
	public Class<? extends Annotation> annotationType() {
	    return CDItoEJBDelegate.class;
	}
    };

}
