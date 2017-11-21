/*
 * James Buncle 2017
 */
package uk.co.jbuncle.hnapclient.http;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Map;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;

/**
 *
 * @author James Buncle <jbuncle@hotmail.com>
 */
public class HttpClient {

    public String post(final String url, final Map<String, String> headers, final String body)
            throws UnsupportedEncodingException, IOException, UnsupportedOperationException {
        final HttpPost post = new HttpPost(url);
        headers.entrySet().forEach((Map.Entry<String, String> header) -> {
            post.addHeader(header.getKey(), header.getValue());
        });
        StringEntity entity = new StringEntity(body);
        post.setEntity(entity);
        org.apache.http.client.HttpClient client = new DefaultHttpClient();
        HttpResponse response = client.execute(post);
        if (response.getStatusLine().getStatusCode() != 200) {
            throw new HttpResponseException(response.getStatusLine().getStatusCode(), response.getStatusLine().getReasonPhrase());
        }
        return IOUtils.toString(response.getEntity().getContent());
    }

    public String get(final String url, final Map<String, String> headers)
            throws UnsupportedEncodingException, IOException, UnsupportedOperationException {
        final HttpGet get = new HttpGet(url);

        return performRequest(headers, get);
    }

    private String performRequest(
            final Map<String, String> headers,
            final HttpRequestBase httpRequestBase
    ) throws IOException, UnsupportedOperationException, HttpResponseException {
        addHeaders(headers, httpRequestBase);
        final HttpResponse httpResponse = getResponse(httpRequestBase);
        checkResponseCode(httpResponse);
        return getResponseBody(httpResponse);
    }

    private void setRequestBody(final String body, final HttpPost httpPost) throws UnsupportedEncodingException {
        final StringEntity entity = new StringEntity(body);
        httpPost.setEntity(entity);
    }

    private static String getResponseBody(final HttpResponse response) throws UnsupportedOperationException, IOException {
        return IOUtils.toString(response.getEntity().getContent());
    }

    private void checkResponseCode(final HttpResponse httpResponse) throws HttpResponseException {
        if (httpResponse.getStatusLine().getStatusCode() != 200) {
            throw new HttpResponseException(httpResponse.getStatusLine().getStatusCode(), httpResponse.getStatusLine().getReasonPhrase());
        }
    }

    private HttpResponse getResponse(final HttpRequestBase httpRequestBase) throws IOException {
        org.apache.http.client.HttpClient client = HttpClientBuilder.create().build();
        final HttpResponse response = client.execute(httpRequestBase);
        return response;
    }

    private void addHeaders(final Map<String, String> headers, final HttpRequestBase httpRequestBase) {
        headers.entrySet().forEach((Map.Entry<String, String> header) -> {
            httpRequestBase.addHeader(header.getKey(), header.getValue());
        });
    }

}
