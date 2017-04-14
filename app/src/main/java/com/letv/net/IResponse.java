package com.letv.net;



/**
 * 通过服务器的响应生成的数据实体
 * @author linan1
 *
 */
public class IResponse<T> {
	
	/**
	 * stateCode默认值
	 */
	public static final int STATE_CODE_NOT_INIT = -10000;
	
	/**
	 * stateCode成功值
	 */
	public static final int STATE_CODE_SUCCESS = 200;
	
	/**
	 * 此值为请求状态的标示,在约定的协议中,一定会返回code和msg.如果不是约定的协议,此值和msg无意义.
	 */
	private int status = STATE_CODE_NOT_INIT;
	/**
	 * 服务器返回的信息
	 */
	private String message;
	
	/**
	 * 服务器返回的接口状态
	 */
	private String code;
	
	/**
	 * 服务器时间
	 */
	private String serverTime;
	
	private T entity;
	
	
	/**
	 * 获取网络请求得到的数据
	 * @return
	 */
	public T getEntity() {
		return entity;
	}
	public void setEntity(T networkDate) {
		this.entity = networkDate;
	}
	public int getStateCode() {
		return status;
	}
	public void setStateCode(int stateCode) {
		this.status = stateCode;
	}
	public String getMessage() {
		return message;
	}
	public void setMessage(String message) {
		this.message = message;
	}
	public String getServerTime() {
		return serverTime;
	}
	public void setServerTime(String serverTime) {
		this.serverTime = serverTime;
	}
	public String getCode() {
		return code;
	}
	public void setCode(String code) {
		this.code = code;
	}
	
}
