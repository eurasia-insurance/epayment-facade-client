package tech.lapsa.epayment.facade.beans;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Properties;

import tech.lapsa.java.commons.function.MyObjects;
import tech.lapsa.java.commons.function.MyStrings;
import tech.lapsa.java.commons.io.MyFiles;
import tech.lapsa.java.commons.security.MyCertificates;
import tech.lapsa.java.commons.security.MyKeyStores;
import tech.lapsa.java.commons.security.MyKeyStores.StoreType;
import tech.lapsa.java.commons.security.MyPrivateKeys;

class QazkomSettings {

    static final String JNDI_QAZKOM_CONFIG = "epayment/resource/qazkom/Configuration";

    static final String PROPERTY_SIGNATURE_ALGORITHM = "signature.algorithm";

    static final String PROPERTY_MERCHANT_ID = "merchant.id";
    static final String PROPERTY_MERCHANT_NAME = "merchant.name";

    static final String PROPERTY_MERCHANT_KEYSTORE_FILE = "merchant.key.keystore";
    static final String PROPERTY_MERCHANT_KEYSTORE_TYPE = "merchant.key.storetype";
    static final String PROPERTY_MERCHANT_KEYSTORE_PASSWORD = "merchant.key.storepass";
    static final String PROPERTY_MERCHANT_KEYSTORE_KEYALIAS = "merchant.key.alias";

    static final String PROPERTY_MERCHANT_CERTSTORE_FILE = "merchant.cert.keystore";
    static final String PROPERTY_MERCHANT_CERTSTORE_TYPE = "merchant.cert.storetype";
    static final String PROPERTY_MERCHANT_CERTSTORE_PASSWORD = "merchant.cert.storepass";
    static final String PROPERTY_MERCHANT_CERTSTORE_CERTALIAS = "merchant.cert.alias";

    static final String PROPERTY_BANK_EPAY_URL = "bank.epay.url";
    static final String PROPERTY_BANK_EPAY_TEMPLATE = "bank.epay.template";

    static final String PROPERTY_BANK_CERTSTORE_FILE = "bank.cert.keystore";
    static final String PROPERTY_BANK_CERTSTORE_TYPE = "bank.cert.storetype";
    static final String PROPERTY_BANK_CERTSTORE_PASSWORD = "bank.cert.storepass";
    static final String PROPERTY_BANK_CERTSTORE_CERTALIAS = "bank.cert.alias";

    final URI QAZKOM_EPAY_URI;
    final String QAZKOM_EPAY_HTTP_METHOD;

    final String QAZKOM_EPAY_TEMPLATE;

    final String QAZKOM_MERCHANT_ID;
    final String QAZKOM_MERCHANT_NAME;
    final PrivateKey QAZKOM_MERCHANT_key;
    final X509Certificate QAZKOM_MERCHANT_CERTIFICATE;

    final X509Certificate QAZKOM_BANK_CERTIFICATE;

    QazkomSettings(final Properties qazkomConfig) throws IllegalArgumentException {

	try {
	    QAZKOM_EPAY_URI = new URI(qazkomConfig.getProperty(PROPERTY_BANK_EPAY_URL));
	    MyObjects.requireNonNull(QAZKOM_EPAY_URI, "QAZKOM_EPAY_URI");
	} catch (final URISyntaxException e) {
	    throw new RuntimeException("Qazkom Epay URI process failed", e);
	}

	{
	    QAZKOM_EPAY_HTTP_METHOD = "POST";
	}

	{
	    QAZKOM_EPAY_TEMPLATE = qazkomConfig.getProperty(PROPERTY_BANK_EPAY_TEMPLATE);
	    MyStrings.requireNonEmpty(QAZKOM_EPAY_TEMPLATE, "QAZKOM_EPAY_TEMPLATE");
	}

	{
	    QAZKOM_MERCHANT_ID = qazkomConfig.getProperty(PROPERTY_MERCHANT_ID);
	    MyStrings.requireNonEmpty(QAZKOM_MERCHANT_ID, "QAZKOM_MERCHANT_ID");

	    QAZKOM_MERCHANT_NAME = qazkomConfig.getProperty(PROPERTY_MERCHANT_NAME);
	    MyStrings.requireNonEmpty(QAZKOM_MERCHANT_NAME, "QAZKOM_MERCHANT_NAME");

	}

	{
	    final String KEYSTORE_FILE = qazkomConfig.getProperty(PROPERTY_MERCHANT_KEYSTORE_FILE);
	    MyStrings.requireNonEmpty(KEYSTORE_FILE, "KEYSTORE_FILE");

	    try (final InputStream KEYSTORE_FILE_STREAM = MyFiles.optionalAsStream(KEYSTORE_FILE)//
		    .orElseThrow(() -> new RuntimeException("Keystore not found"))) {

		final String KEYSTORE_TYPE_S = qazkomConfig.getProperty(PROPERTY_MERCHANT_KEYSTORE_TYPE);
		MyStrings.requireNonEmpty(KEYSTORE_TYPE_S, "KEYSTORE_TYPE_S");
		final StoreType KEYSTORE_TYPE = StoreType.valueOf(KEYSTORE_TYPE_S);
		MyObjects.requireNonNull(KEYSTORE_TYPE, "KEYSTORE_TYPE");

		final String KEYSTORE_PASSWORD = qazkomConfig.getProperty(PROPERTY_MERCHANT_KEYSTORE_PASSWORD);
		MyStrings.requireNonEmpty(KEYSTORE_PASSWORD, "KEYSTORE_PASSWORD");

		final KeyStore KEYSTORE = MyKeyStores.from(KEYSTORE_FILE_STREAM, KEYSTORE_TYPE, KEYSTORE_PASSWORD) //
			.orElseThrow(() -> new RuntimeException("Can not load keystore"));

		final String MERCHANT_ALIAS = qazkomConfig.getProperty(PROPERTY_MERCHANT_KEYSTORE_KEYALIAS);
		MyStrings.requireNonEmpty(MERCHANT_ALIAS, "MERCHANT_ALIAS");

		QAZKOM_MERCHANT_key = MyPrivateKeys.from(KEYSTORE, MERCHANT_ALIAS, KEYSTORE_PASSWORD) //
			.orElseThrow(() -> new RuntimeException("Can't find key entry"));

		QAZKOM_MERCHANT_CERTIFICATE = MyCertificates.from(KEYSTORE, MERCHANT_ALIAS) //
			.orElseThrow(() -> new RuntimeException("Can find key entry"));
	    } catch (final IOException e) {
		throw new RuntimeException(e);
	    }
	}

	{
	    final String KEYSTORE_FILE = qazkomConfig.getProperty(PROPERTY_BANK_CERTSTORE_FILE);
	    MyStrings.requireNonEmpty(KEYSTORE_FILE, "KEYSTORE_FILE");

	    try (final InputStream KEYSTORE_FILE_STREAM = MyFiles.optionalAsStream(KEYSTORE_FILE) //
		    .orElseThrow(() -> new RuntimeException("Keystore not found"))) {

		final String KEYSTORE_TYPE_S = qazkomConfig.getProperty(PROPERTY_BANK_CERTSTORE_TYPE);
		MyStrings.requireNonEmpty(KEYSTORE_TYPE_S, "KEYSTORE_TYPE_S");
		final StoreType KEYSTORE_TYPE = StoreType.valueOf(KEYSTORE_TYPE_S);
		MyObjects.requireNonNull(KEYSTORE_TYPE, "KEYSTORE_TYPE");

		final String KEYSTORE_PASSWORD = qazkomConfig.getProperty(PROPERTY_BANK_CERTSTORE_PASSWORD);
		MyStrings.requireNonEmpty(KEYSTORE_PASSWORD, "KEYSTORE_PASSWORD");

		final KeyStore KEYSTORE = MyKeyStores.from(KEYSTORE_FILE_STREAM, KEYSTORE_TYPE, KEYSTORE_PASSWORD) //
			.orElseThrow(() -> new RuntimeException("Can not load keystore"));

		final String BANK_ALIAS = qazkomConfig.getProperty(PROPERTY_BANK_CERTSTORE_CERTALIAS);
		MyStrings.requireNonEmpty(BANK_ALIAS, "BANK_ALIAS");

		QAZKOM_BANK_CERTIFICATE = MyCertificates.from(KEYSTORE, BANK_ALIAS) //
			.orElseThrow(() -> new RuntimeException("Can find cert entry"));
	    } catch (final IOException e) {
		throw new RuntimeException(e);
	    }
	}
    }
}