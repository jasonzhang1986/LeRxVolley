# LeRxVolley
#### fork from RxVolley, add some feature

1. 针对Response进行了封装

2. 添加了makeJar任务，打包后的jar在release目录下

3. 添加了Facebook的调试库stetho，并添加了开关方便在主工程中切换

4. 引入jar后主工程需要如下配置

   ```java
   //Application的onCreate中初始化
   if (BuildConfig.DEBUG) {
     	 NetManager.getInstance(this).initStetho(this);
   }
   ```

   ```groovy
    //在build.gradle的dependencies中添加stetho库
    compile 'com.facebook.stetho:stetho:1.4.2'
    compile 'com.facebook.stetho:stetho-urlconnection:1.4.2'
   ```

   ​