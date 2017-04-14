package com.letv.net;

import java.lang.reflect.Type;

import android.text.TextUtils;

import com.google.gson.Gson;
import com.kymjs.rxvolley.client.HttpCallback;
import com.kymjs.rxvolley.client.ProgressListener;
import com.kymjs.rxvolley.toolbox.Loger;

public abstract class RequestHttpCallback<T> extends HttpCallback implements ProgressListener {
	private Type mType;
	private boolean mIsCheckResJsonStr = true;

	public RequestHttpCallback(Type type, boolean isCheckResJsonStr) {
		mType = type;
		mIsCheckResJsonStr = isCheckResJsonStr;
	}

	public RequestHttpCallback(Type type) {
		this(type, true);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void onSuccess(String jsonStr) {
		Loger.d("Response:" + jsonStr);
		if (mIsCheckResJsonStr) {
			if (!TextUtils.isEmpty(jsonStr)) {
				try {
					IResponse<T> response = (IResponse<T>) new Gson().fromJson(
							jsonStr, mType);
					if (null != response) {
						if (IResponse.STATE_CODE_SUCCESS == response.getStateCode()) {
							responseSuccess(response);
						} else {
							onFailure(response.getStateCode(),
									response.getMessage());
						}
						return;
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			onFailure(-1, "unKnow");
		} else {
			responseSuccess(null);
		}
	}

	@Override
	public abstract void onFailure(int errorNo, String strMsg);

	@Override
	public void onProgress(long transferredBytes, long totalSize) {
	}

	public abstract void responseSuccess(IResponse<T> response);
    /**
     * 根据自身应用成功状态码，重载该方法
     * 默认成功状态码为：1
     * @return
     */
	/*public int getSuccessStateCode() {
		return IResponse.STATE_CODE_SUCCESS;
	}*/
}