package tech.lapsa.epayment.facade.beans;

public final class QazkomConstants {

    private QazkomConstants() {
    }

    public static final String JNDI_QAZKOM_CONFIG = "epayment/resource/qazkom/Configuration";

    public static final String PROPERTY_SIGNATURE_ALGORITHM = "signature.algorithm";

    public static final String PROPERTY_MERCHANT_ID = "merchant.id";
    public static final String PROPERTY_MERCHANT_NAME = "merchant.name";

    public static final String PROPERTY_MERCHANT_KEYSTORE_FILE = "merchant.key.keystore";
    public static final String PROPERTY_MERCHANT_KEYSTORE_TYPE = "merchant.key.storetype";
    public static final String PROPERTY_MERCHANT_KEYSTORE_PASSWORD = "merchant.key.storepass";
    public static final String PROPERTY_MERCHANT_KEYSTORE_KEYALIAS = "merchant.key.alias";

    public static final String PROPERTY_MERCHANT_CERTSTORE_FILE = "merchant.cert.keystore";
    public static final String PROPERTY_MERCHANT_CERTSTORE_TYPE = "merchant.cert.storetype";
    public static final String PROPERTY_MERCHANT_CERTSTORE_PASSWORD = "merchant.cert.storepass";
    public static final String PROPERTY_MERCHANT_CERTSTORE_CERTALIAS = "merchant.cert.alias";

    public static final String PROPERTY_BANK_EPAY_URL = "bank.epay.url";
    public static final String PROPERTY_BANK_EPAY_TEMPLATE = "bank.epay.template";

    public static final String PROPERTY_BANK_CERTSTORE_FILE = "bank.cert.keystore";
    public static final String PROPERTY_BANK_CERTSTORE_TYPE = "bank.cert.storetype";
    public static final String PROPERTY_BANK_CERTSTORE_PASSWORD = "bank.cert.storepass";
    public static final String PROPERTY_BANK_CERTSTORE_CERTALIAS = "bank.cert.alias";
}
