package com.letv.net;

import com.kymjs.rxvolley.client.HttpParams;
import com.kymjs.rxvolley.client.JsonRequest;
import com.kymjs.rxvolley.client.RequestConfig;

/**
 * Created by jczhang on 16-3-10.
 */
public abstract class ObjectRequest<T> extends JsonRequest {

    public ObjectRequest(RequestConfig config, HttpParams params, RequestHttpCallback<T> callback) {
        super(config, params, callback);
    }
}
