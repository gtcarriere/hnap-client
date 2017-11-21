/*
 * James Buncle 2017
 */
package uk.co.jbuncle.hnapclient;

import java.net.MalformedURLException;
import uk.co.jbuncle.hnapclient.exceptions.HnapRequestException;
import uk.co.jbuncle.hnapclient.exceptions.HnapClientException;
import uk.co.jbuncle.hnapclient.exceptions.HnapAuthenticationException;
import uk.co.jbuncle.hnapclient.soap.BasicSoapClient;
import java.util.HashMap;
import java.util.Map;
import java.net.URL;
import uk.co.jbuncle.hnapclient.response.DeviceSettingsI;
import uk.co.jbuncle.hnapclient.response.DeviceSettingsParser;
import uk.co.jbuncle.hnapclient.util.xml.XMLException;
import uk.co.jbuncle.hnapclient.util.xml.XMLUtility;
import uk.co.jbuncle.hnapclient.util.xml.XmlToObject;

/**
 *
 * @author James Buncle <jbuncle@hotmail.com>
 */
public class HnapClient {

    private static final String LOGINRESULT_FAILED = "failed";
    private static final String LOGINRESULT_SUCCESS = "success";
    private static final String HNAP1_XMLNS = "http://purenetworks.com/HNAP1/";
    private static final String HNAP_LOGIN_METHOD = "Login";
    private static final String LOGINRESULT = "LoginResult";

    private static final String LOGINREQUEST_ACTION = "Action";
    private static final String LOGINREQUEST_USERNAME = "Username";
    private static final String LOGINREQUEST_LOGINPASSWORD = "LoginPassword";
    private static final String LOGINREQUEST_CAPTCHA = "Captcha";

    private static final String LOGINRESPONSE_CHALLENGE = "Challenge";
    private static final String LOGINRESPONSE_PUBLICKEY = "PublicKey";
    private static final String LOGINRESPONSE_COOKIE = "Cookie";

    private final BasicSoapClient soapClient;
    private final URL url;
    private final String username;
    private final String password;

    public HnapClient(
            final BasicSoapClient soapClient,
            final URL url,
            final String username,
            final String password
    ) {
        this.soapClient = soapClient;
        this.url = url;
        this.username = username;
        this.password = password;
    }

    public DeviceSettingsI discover() throws HnapClientException {
        final Map<String, String> headers = new HashMap<>();
        final String response = this.soapClient.soapGet(url, headers);
        try {
            final Map<String, Object> responseProperties = XmlToObject.fromXml(response);
            return DeviceSettingsParser.createFromResponse(responseProperties);
        }
        catch (XMLException | MalformedURLException ex) {
            throw new HnapClientException(ex);
        }
    }

    public HnapSession login() throws HnapClientException {
        try {
            final Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "text/xml; charset=utf-8");
            headers.put("SOAPAction", "\"" + HnapClient.HNAP1_XMLNS + HnapClient.HNAP_LOGIN_METHOD + "\"");

            final String loginInitProperties = XmlToObject.toXml(this.loginRequest());
            final String response = this.soapRequest(url, headers, HnapClient.HNAP_LOGIN_METHOD, loginInitProperties);
            final HnapSession session = this.parseSession(response);

            final Map<String, Object> loginProperties = this.loginParameters(session);
            final Map<String, Object> responseProperties = this.request(session, HnapClient.HNAP_LOGIN_METHOD, loginProperties);

            final String loginResult = (String) responseProperties.get(HnapClient.LOGINRESULT);

            switch (loginResult) {
                case HnapClient.LOGINRESULT_SUCCESS:
                    return session;
                case HnapClient.LOGINRESULT_FAILED:
                    throw new HnapAuthenticationException("Login failed");
                default:
                    throw new HnapAuthenticationException("Unexpected login result " + loginResult);
            }
        }
        catch (XMLException ex) {
            throw new HnapClientException(ex);
        }
    }

    public Map<String, Object> request(
            final HnapSession hnapSession,
            final String method,
            final Map<String, Object> body
    ) throws HnapClientException {
        try {
            final String bodyXml = XmlToObject.toXml(body);
            final String response = this.hnapRequest(hnapSession, method, bodyXml);
            return XmlToObject.fromXml(response);
        }
        catch (XMLException ex) {
            throw new HnapClientException(ex);
        }
    }

    private HnapSession parseSession(
            final String body
    ) throws XMLException {
        final Map<String, Object> properties = XmlToObject.fromXml(body);
        final String challenge = (String) properties.get(LOGINRESPONSE_CHALLENGE);
        final String publicKey = (String) properties.get(LOGINRESPONSE_PUBLICKEY);
        final String cookie = (String) properties.get(LOGINRESPONSE_COOKIE);

        return new HnapSession(this.password, challenge, publicKey, cookie);
    }

    /**
     * Wrap the given body string in a HNAP SOAP Action XML wrapper.
     *
     * @param action The HNAP Action
     * @param body The body to wrap
     *
     * @return The wrapped string.
     */
    private String addMethodWrapper(
            final String action,
            final String body
    ) {
        return "<" + action + " xmlns=\"" + HnapClient.HNAP1_XMLNS + "\">"
                + body
                + "</" + action + ">";
    }

    private String hnapRequest(
            final HnapSession hnapSession,
            final String method,
            final String body
    ) throws HnapClientException {
        final Map<String, String> headers = new HashMap<>();
        headers.put("HNAP_AUTH", hnapSession.getAuth('"' + HnapClient.HNAP1_XMLNS + method + '"'));
        headers.put("Cookie", "uid=" + hnapSession.getCookie());
        return this.soapRequest(url, headers, method, body);
    }

    private Map<String, Object> loginRequest() {
        final Map<String, Object> properties = new HashMap<>();
        properties.put(LOGINREQUEST_ACTION, "login");
        properties.put(LOGINREQUEST_USERNAME, this.username);
        properties.put(LOGINREQUEST_LOGINPASSWORD, "");
        properties.put(LOGINREQUEST_CAPTCHA, "");
        return properties;
    }

    private Map<String, Object> loginParameters(
            final HnapSession hnapSession
    ) {
        final String loginPassword = hnapSession.getLoginPassword();
        final Map<String, Object> loginParams = new HashMap<>();
        loginParams.put("Action", "login");
        loginParams.put("Username", this.username);
        loginParams.put("LoginPassword", loginPassword.toUpperCase());
        loginParams.put("Captcha", "");
        return loginParams;
    }

    private String soapRequest(
            final URL url,
            final Map<String, String> headers,
            final String method,
            final String requestBody
    ) throws HnapClientException {
        String responseBody = null;

        final String wrappedBody = this.addMethodWrapper(method, requestBody);
        try {
            // Check response is valid XML
            XMLUtility.loadXML(wrappedBody);
        }
        catch (XMLException ex) {
            throw new HnapRequestException("Request contains invalid XML", ex, requestBody, responseBody);
        }

        responseBody = this.soapClient.soapPost(url, HnapClient.HNAP1_XMLNS + method, headers, wrappedBody);

        try {
            // Check response is valid XML
            XMLUtility.loadXML(responseBody);
        }
        catch (XMLException ex) {
            throw new HnapRequestException("Response contained invalid XML", ex, requestBody, responseBody);
        }
        return responseBody;
    }
}
