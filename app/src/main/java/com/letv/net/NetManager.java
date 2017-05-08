package com.letv.net;

import java.io.File;
import java.net.URLEncoder;
import java.util.Iterator;
import java.util.Map;

import android.content.Context;
import android.text.TextUtils;

import com.facebook.stetho.Stetho;
import com.google.gson.Gson;
import com.kymjs.rxvolley.RxVolley;
import com.kymjs.rxvolley.client.FileRequest;
import com.kymjs.rxvolley.client.HttpParams;
import com.kymjs.rxvolley.client.RequestConfig;
import com.kymjs.rxvolley.http.DefaultRetryPolicy;
import com.kymjs.rxvolley.http.HttpConnectStack;
import com.kymjs.rxvolley.http.OkHttp3Stack;
import com.kymjs.rxvolley.http.RequestQueue;
import com.kymjs.rxvolley.interf.IHttpStack;
import com.kymjs.rxvolley.toolbox.Loger;
import com.kymjs.rxvolley.toolbox.SPUtils;

/**
 * Created by jczhang on 2016/3/13.
 */
public class NetManager<T> {
	@SuppressWarnings("rawtypes")
	private static volatile NetManager sInstance;
	private final static int TIME_OUT = 10 * 1000;
	private NetManager() {
	}
	private NetManager(Context context) {
		SPUtils.init(context);
		setDataCache(context,null);
	}

	public void setDataCache(Context context,String cache) {
		initRequestQueue(context, cache);
	}
	private void initRequestQueue(Context context, String cachePath) {
		RequestQueue requestQueue = null;
		//初始化HttpStack
		IHttpStack httpStack = null;
		if (SPUtils.getBoolean(SPUtils.KEY_OKHTTP)) {
			httpStack = new OkHttp3Stack();
		} else {
			httpStack = new HttpConnectStack();
		}

		if (TextUtils.isEmpty(cachePath)) {
			requestQueue = RxVolley.getRequestQueue(context, httpStack);
		} else {
			requestQueue = RequestQueue.newRequestQueue(new File(cachePath), httpStack);
		}
		RxVolley.setRequestQueue(requestQueue);
	}

	@SuppressWarnings("unchecked")
	public static <T> NetManager<T> getInstance(Context context) {
		if(sInstance ==null){
			synchronized (NetManager.class) {
				if (sInstance ==null) {
					sInstance = new NetManager(context);
				}
			}
		}
		return sInstance;
	}
	public static NetManager get() {
		if(sInstance ==null) {
			throw new IllegalStateException("initHttp must call before get");
		}
		return sInstance;
	}

	private boolean inited = false;

	/**
	 * 在Application中调用，初始化参数
	 * @param applicationContext  context
	 * @param stetho 是否使用Facebook的stetho库来查看网络请求细节
	 * @param okHttpStack 是否使用OkHttp
	 */
	public static void initHttp(Context applicationContext, boolean stetho, boolean okHttpStack) {
		if (sInstance == null) {
			synchronized (NetManager.class) {
				if (sInstance ==null) {
					sInstance = new NetManager();
				}
			}
		}
		if (sInstance.inited) {
			return;
		}
		SPUtils.init(applicationContext);
		SPUtils.putBoolean(SPUtils.KEY_STETHO, stetho);
		SPUtils.putBoolean(SPUtils.KEY_OKHTTP, okHttpStack);
		sInstance.initRequestQueue(applicationContext,null);
		if (stetho) {
			Stetho.initializeWithDefaults(applicationContext);
		}
	}


	public void doGet(Context context,String url, Map<String, String> headParams, RequestHttpCallback<T> callback, boolean shouldCache) {
		getDefaultBuilder()
				.url(url)
				.httpMethod(RxVolley.Method.GET)
				.shouldCache(shouldCache)
				.params(getParams(headParams,true))
				.callback(callback)
				.doTask(context);
	}
	public void doGet(Context context,String url, Map<String, String> headParams, RequestHttpCallback<T> callback) {
		doGet(context, url, headParams, callback, false);
	}

	public void doPost(Context context,String url, Map<String, String> headParams, Map<String, String> bodyParams,
			RequestHttpCallback<T> callback) {
		getDefaultBuilder()
				.url(url)
				.httpMethod(RxVolley.Method.POST)
				.params(getFormParams(headParams, bodyParams))
				.contentType(RxVolley.ContentType.JSON)
				.callback(callback).doTask(context);
	}
	
	@SuppressWarnings("unchecked")
	public void doPost(Context context,String url, Map<String, String> headParams, Object bodyParams,
			RequestHttpCallback<T> callback, boolean isJsonPost/**body参数是否json处理**/,boolean isNeedEncode/**是否需要encode编码**/){
		if(isJsonPost){
			getDefaultBuilder()
			.url(url)
			.httpMethod(RxVolley.Method.POST)
			.params(getJsonParams(headParams, bodyParams, isNeedEncode))
			.contentType(RxVolley.ContentType.JSON)
			.callback(callback).doTask(context);
		}else{
			if(bodyParams != null && bodyParams instanceof Map){
				doPost(context,url, headParams, (Map<String,String>)bodyParams, callback);
			}
		}
		
	}

	public void download(Context context,String storeDir, String url, RequestHttpCallback<T> callback) {
		if (!TextUtils.isEmpty(storeDir) && !TextUtils.isEmpty(url) && null != callback) {
			RequestConfig config = new RequestConfig();
			config.mUrl = url;
			config.mRetryPolicy = new DefaultRetryPolicy(DefaultRetryPolicy.DEFAULT_TIMEOUT_MS, 20,
					DefaultRetryPolicy.DEFAULT_BACKOFF_MULT);
			FileRequest request = new FileRequest(storeDir, config, callback);
			request.setTag(url);
			request.setOnProgressListener(callback);
			getDefaultBuilder().setRequest(request).doTask(context);
		}
	}

	public void doRequest(Context context,ObjectRequest<T> request) {
		new RxVolley.Builder().setRequest(request).doTask(context);
	}

	private RxVolley.Builder getDefaultBuilder() {
		return new RxVolley.Builder().timeout(TIME_OUT).encoding("utf-8");
	}

	private HttpParams getParams(HttpParams httpParams, Map<String, String> parms,boolean isHead) {
		if (null == httpParams) {
			httpParams = new HttpParams();
		}
		if (null != parms && parms.size() > 0) {
			Iterator<String> iterator = parms.keySet().iterator();
			if (null != iterator) {
				String key;
				String value;
				while (iterator.hasNext()) {
					key = iterator.next();
					value = parms.get(key);
					if (isHead) {
						httpParams.putHeaders(key, value);
					} else {
						httpParams.put(key, value);
					}
				}
			}
		}
		return httpParams;
	}

	private HttpParams getParams(Map<String, String> parms,boolean isHeadParams) {
		return getParams(null, parms,isHeadParams);
	}

	private HttpParams getFormParams(Map<String, String> headParams, Map<String, String> bodyParams) {
		return getParams(getParams(headParams,true), bodyParams,false);
	}

	private HttpParams getJsonParams(Map<String, String> headParams, Object bodyParams, boolean isNeedEncode) {
		HttpParams httpParams = getParams(headParams,true);
		if (null != bodyParams) {
			String jsonString = new Gson().toJson(bodyParams);
			if(isNeedEncode){
				jsonString = URLEncoder.encode(jsonString);
			}
			httpParams.putJsonParams(jsonString);
			Loger.d("Response:" + jsonString);
		}
		return httpParams;
	}

}
