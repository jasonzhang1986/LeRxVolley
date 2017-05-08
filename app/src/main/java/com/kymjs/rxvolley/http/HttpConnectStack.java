/*
 * Copyright (C) 2011 The Android Open Source Project, 张涛
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kymjs.rxvolley.http;


import com.facebook.stetho.urlconnection.ByteArrayRequestEntity;
import com.facebook.stetho.urlconnection.SimpleRequestEntity;
import com.facebook.stetho.urlconnection.StethoURLConnectionManager;
import com.kymjs.rxvolley.RxVolley;
import com.kymjs.rxvolley.interf.IHttpStack;
import com.kymjs.rxvolley.toolbox.HTTPSTrustManager;
import com.kymjs.rxvolley.toolbox.HttpParamsEntry;
import com.kymjs.rxvolley.toolbox.SPUtils;
import com.letv.net.NetManager;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.zip.GZIPInputStream;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

/**
 * HttpUrlConnection方式实现,添加的默认认可全部https证书
 *
 * @author kymjs (http://www.kymjs.com/) .
 */
public class HttpConnectStack implements IHttpStack {

    private static final String HEADER_CONTENT_TYPE = "Content-Type";

    private final UrlRewriter mUrlRewriter;
    private final SSLSocketFactory mSslSocketFactory;
    private static final String HEADER_ACCEPT_ENCODING = "Accept-Encoding";
    private static final String GZIP_ENCODING = "gzip";

    public interface UrlRewriter {
        /**
         * 重写用于请求的URL
         */
        String rewriteUrl(String originalUrl);
    }

    public HttpConnectStack() {
        this(null);
    }

    public HttpConnectStack(UrlRewriter urlRewriter) {
        this(urlRewriter, null);
    }

    public HttpConnectStack(UrlRewriter urlRewriter, SSLSocketFactory sslSocketFactory) {
        mUrlRewriter = urlRewriter;
        mSslSocketFactory = sslSocketFactory;
    }

    @Override
    public URLHttpResponse performRequest(Request<?> request,
                                          ArrayList<HttpParamsEntry> additionalHeaders)
            throws IOException {
        String url = request.getUrl();
        ArrayList<HttpParamsEntry> header = new ArrayList<HttpParamsEntry>();
        header.addAll(request.getHeaders());
        header.addAll(additionalHeaders);

        if (mUrlRewriter != null) {
            String rewritten = mUrlRewriter.rewriteUrl(url);
            if (rewritten == null) {
                throw new IOException("URL blocked by rewriter: " + url);
            }
            url = rewritten;
        }
        URL parsedUrl = new URL(url);
        StethoURLConnectionManager stethoURLConnectionManager = null;
        if (SPUtils.getBoolean(SPUtils.KEY_STETHO)) {
            stethoURLConnectionManager = new StethoURLConnectionManager(url);
        }
        HttpURLConnection connection = configureAndConnectRequest(parsedUrl, request, header, stethoURLConnectionManager);
        return responseFromConnection(connection, stethoURLConnectionManager);
    }

    private URLHttpResponse responseFromConnection(HttpURLConnection connection, StethoURLConnectionManager stethoURLConnectionManager)
            throws IOException {
        URLHttpResponse response = new URLHttpResponse();
        //contentStream
        InputStream inputStream;
        InputStream decompressedStream;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            inputStream = connection.getInputStream();
        } catch (IOException ioe) {
            inputStream = connection.getErrorStream();
        }
        if (stethoURLConnectionManager!=null) {
            try {
                // Let Stetho see the raw, possibly compressed stream.
                inputStream = stethoURLConnectionManager.interpretResponseStream(inputStream);
                decompressedStream = applyDecompressionIfApplicable(connection, inputStream);
                if (decompressedStream != null) {
                    copy(decompressedStream, out, new byte[1024]);
                }
            } finally {
                if (inputStream != null) {
                    inputStream.close();
                }
            }
        }
        int responseCode = connection.getResponseCode();
        if (responseCode == -1) {
            throw new IOException(
                    "Could not retrieve response code from HttpUrlConnection.");
        }
        response.setResponseCode(responseCode);
        response.setResponseMessage(connection.getResponseMessage());

        if (stethoURLConnectionManager!=null) {
            response.setContentStream(new ByteArrayInputStream(out.toByteArray()));
        } else {
            response.setContentStream(inputStream);
        }

        response.setContentLength(connection.getContentLength());
        response.setContentEncoding(connection.getContentEncoding());
        response.setContentType(connection.getContentType());
        //header
        HashMap<String, String> headerMap = new HashMap<String, String>();
        for (Entry<String, List<String>> header : connection.getHeaderFields()
                .entrySet()) {
            if (header.getKey() != null) {
                StringBuilder value = new StringBuilder();
                for (String v : header.getValue()) {
                    value.append(v).append(";");
                }
                headerMap.put(header.getKey(), value.toString());
            }
        }
        response.setHeaders(headerMap);
        return response;
    }

    private HttpURLConnection configureAndConnectRequest(URL url, Request<?> request, List<HttpParamsEntry> header, StethoURLConnectionManager stethoURLConnectionManager)
            throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        int timeoutMs = request.getTimeoutMs();

        try {
            connection.setConnectTimeout(timeoutMs);
            connection.setReadTimeout(timeoutMs);
            connection.setUseCaches(false);
            connection.setDoInput(true);

            setConnectionParametersForRequest(connection,request);

            // use caller-provided custom SslSocketFactory, if any, for HTTPS
            if ("https".equals(url.getProtocol())) {
                if (mSslSocketFactory != null) {
                    ((HttpsURLConnection) connection)
                            .setSSLSocketFactory(mSslSocketFactory);
                } else {
                    //信任所有证书
                    HTTPSTrustManager.allowAllSSL();
                }
            }
            try {
                if (stethoURLConnectionManager!=null) {
                    // Adding this disables transparent gzip compression so that we can intercept
                    // the raw stream and display the correct response body size.
                    connection.setRequestProperty(HEADER_ACCEPT_ENCODING, GZIP_ENCODING);
                    SimpleRequestEntity requestEntity = null;
                    if (request.getBody() != null) {
                        requestEntity = new ByteArrayRequestEntity(request.getBody());
                    }
                    stethoURLConnectionManager.preConnect(connection, requestEntity);
                    if (request.getMethod() == RxVolley.Method.POST) {
                        if (requestEntity == null) {
                            throw new IllegalStateException("POST requires an entity");
                        }
                        requestEntity.writeTo(connection.getOutputStream());
                    }
                }

                for (HttpParamsEntry entry : header) {
                    connection.addRequestProperty(entry.k, entry.v);
                }
                // Ensure that we are connected after this point.  Note that getOutputStream above will
                // also connect and exchange HTTP messages.
                connection.connect();
                if (stethoURLConnectionManager!=null) {
                    stethoURLConnectionManager.postConnect();
                }
                return connection;
            }catch (IOException inner) {
                if (stethoURLConnectionManager!=null) {
                    // This must only be called after preConnect.  Failures before that cannot be
                    // represented since the request has not yet begun according to Stetho.
                    stethoURLConnectionManager.httpExchangeFailed(inner);
                }
                throw inner;
            }
        }catch (IOException outer) {
            connection.disconnect();
            throw outer;
        }
    }

    /* package */
    static void setConnectionParametersForRequest(HttpURLConnection connection, Request<?> request)
            throws IOException {
        switch (request.getMethod()) {
            case RxVolley.Method.GET:
                connection.setRequestMethod("GET");
                break;
            case RxVolley.Method.DELETE:
                connection.setRequestMethod("DELETE");
                break;
            case RxVolley.Method.POST:
                connection.setRequestMethod("POST");
                addBodyIfExists(connection, request);
                break;
            case RxVolley.Method.PUT:
                connection.setRequestMethod("PUT");
                addBodyIfExists(connection, request);
                break;
            case RxVolley.Method.HEAD:
                connection.setRequestMethod("HEAD");
                break;
            case RxVolley.Method.OPTIONS:
                connection.setRequestMethod("OPTIONS");
                break;
            case RxVolley.Method.TRACE:
                connection.setRequestMethod("TRACE");
                break;
            case RxVolley.Method.PATCH:
                connection.setRequestMethod("PATCH");
                addBodyIfExists(connection, request);
                break;
            default:
                throw new IllegalStateException("Unknown method type.");
        }
    }

    /**
     * 如果有body则添加
     */
    private static void addBodyIfExists(HttpURLConnection connection, Request<?> request)
            throws IOException {
        byte[] body = request.getBody();
        if (body != null) {
            connection.setDoOutput(true);
            connection.addRequestProperty(HEADER_CONTENT_TYPE,
                    request.getBodyContentType());
            DataOutputStream out = new DataOutputStream(
                    connection.getOutputStream());
            out.write(body);
            out.close();
        }
    }

    private static InputStream applyDecompressionIfApplicable(
            HttpURLConnection conn, InputStream in) throws IOException {
        if (in != null && GZIP_ENCODING.equals(conn.getContentEncoding())) {
            return new GZIPInputStream(in);
        }
        return in;
    }

    private static void copy(InputStream in, OutputStream out, byte[] buf) throws IOException {
        if (in == null) {
            return;
        }
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
    }
}
