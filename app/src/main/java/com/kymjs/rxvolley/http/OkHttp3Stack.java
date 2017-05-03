package com.kymjs.rxvolley.http;

/**
 * Author: Jifeng Zhang
 * Email : jifengzhang.barlow@gmail.com
 * Date  : 2017/5/2
 * Desc  :
 */

import com.facebook.stetho.Stetho;
import com.facebook.stetho.okhttp3.StethoInterceptor;
import com.kymjs.rxvolley.RxVolley;
import com.kymjs.rxvolley.interf.IHttpStack;
import com.kymjs.rxvolley.toolbox.HttpParamsEntry;
import com.kymjs.rxvolley.toolbox.Loger;

import org.apache.http.HttpEntity;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class OkHttp3Stack implements IHttpStack {

    public OkHttp3Stack() {
    }

    private static HttpEntity entityFromOkHttpResponse(Response r) throws IOException {
        BasicHttpEntity entity = new BasicHttpEntity();
        ResponseBody body = r.body();

        entity.setContent(body.byteStream());
        entity.setContentLength(body.contentLength());
        entity.setContentEncoding(r.header("Content-Encoding"));

        if (body.contentType() != null) {
            entity.setContentType(body.contentType().type());
        }
        return entity;
    }

    @SuppressWarnings("deprecation")
    private static void setConnectionParametersForRequest(okhttp3.Request.Builder builder, Request<?> request)
            throws IOException {
        switch (request.getMethod()) {
            case RxVolley.Method.GET:
                builder.get();
                break;
            case RxVolley.Method.DELETE:
                builder.delete();
                break;
            case RxVolley.Method.POST:
                builder.post(createRequestBody(request));
                break;
            case RxVolley.Method.PUT:
                builder.put(createRequestBody(request));
                break;
            case RxVolley.Method.HEAD:
                builder.head();
                break;
            case RxVolley.Method.OPTIONS:
                builder.method("OPTIONS", null);
                break;
            case RxVolley.Method.TRACE:
                builder.method("TRACE", null);
                break;
            case RxVolley.Method.PATCH:
                builder.patch(createRequestBody(request));
                break;
            default:
                throw new IllegalStateException("Unknown method type.");
        }
    }

    private static ProtocolVersion parseProtocol(final Protocol p) {
        switch (p) {
            case HTTP_1_0:
                return new ProtocolVersion("HTTP", 1, 0);
            case HTTP_1_1:
                return new ProtocolVersion("HTTP", 1, 1);
            case SPDY_3:
                return new ProtocolVersion("SPDY", 3, 1);
            case HTTP_2:
                return new ProtocolVersion("HTTP", 2, 0);
        }

        throw new IllegalAccessError("Unkwown protocol");
    }

    private static RequestBody createRequestBody(Request r){
        final byte[] body = r.getBody();
        if (body == null) {
            return null;
        }

        return RequestBody.create(MediaType.parse(r.getBodyContentType()), body);
    }

    private volatile OkHttpClient client;
    private OkHttpClient getClient(Request<?> request) {
        if (client==null) {
            synchronized (OkHttp3Stack.class) {
                if (client==null) {
                    OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder();
                    int timeoutMs = request.getTimeoutMs();

                    clientBuilder.connectTimeout(timeoutMs, TimeUnit.MILLISECONDS);
                    clientBuilder.readTimeout(timeoutMs, TimeUnit.MILLISECONDS);
                    clientBuilder.writeTimeout(timeoutMs, TimeUnit.MILLISECONDS);
                    if (request.getConfig().mUseStetho) {
                        clientBuilder.addNetworkInterceptor(new StethoInterceptor());
                    }
                    client = clientBuilder.build();
                }
            }
        }
        return client;
    }
    @Override
    public URLHttpResponse performRequest(Request<?> request, ArrayList<HttpParamsEntry> additionalHeaders) throws IOException {
        Loger.debug("OKHttpClient performRequest");

        okhttp3.Request.Builder okHttpRequestBuilder = new okhttp3.Request.Builder();
        okHttpRequestBuilder.url(request.getUrl());

        ArrayList<HttpParamsEntry> headers = request.getHeaders();
        for (final HttpParamsEntry header : headers) {
            okHttpRequestBuilder.addHeader(header.k, header.v);
        }
        for (final HttpParamsEntry header1 : additionalHeaders) {
            okHttpRequestBuilder.addHeader(header1.k, header1.v);
        }

        setConnectionParametersForRequest(okHttpRequestBuilder, request);

        okhttp3.Request okHttpRequest = okHttpRequestBuilder.build();
        Call okHttpCall = getClient(request).newCall(okHttpRequest);
        Response okHttpResponse = okHttpCall.execute();

        URLHttpResponse urlHttpResponse = new URLHttpResponse();
        urlHttpResponse.setResponseCode(okHttpResponse.code());
        ResponseBody body = okHttpResponse.body();
        urlHttpResponse.setContentStream(body.byteStream());
        urlHttpResponse.setContentLength(body.contentLength());
        urlHttpResponse.setContentEncoding(okHttpResponse.header("Content-Encoding"));
        if (body.contentType() != null) {
            urlHttpResponse.setContentType(body.contentType().type());
        }
        HashMap<String, String> headerMap = new HashMap<String, String>();
        Headers responseHeaders = okHttpResponse.headers();
        for (int i = 0, len = responseHeaders.size(); i < len; i++) {
            final String name = responseHeaders.name(i), value = responseHeaders.value(i);
            if (name != null) {
                headerMap.put(name, value);
            }
        }
        urlHttpResponse.setHeaders(headerMap);

        return urlHttpResponse;
    }
}
