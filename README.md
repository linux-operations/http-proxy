http-proxy
==========

> 实现单机多IP绑定方案。

`启动`
```Java
java -cp httpclient-example-1.0-SNAPSHOT-jar-with-dependencies.jar HttpProxy eth0 https://api.weibo.com
HttpProxy 为java类名
eth0 网卡
https://api.weibo.com 需要代理的地址
```

`查看各ip的可用剩余量`
```Java
java -cp httpclient-example-1.0-SNAPSHOT-jar-with-dependencies.jar SinaApiRemain eth0 2.00SlDQsDdcZIJC94e5308f67sRL13x
interfaceeth0, ips=[/60.169.74.156, /60.169.74.155, /60.169.74.154, /60.169.74.152]
60.169.74.156: 40000
60.169.74.155: 40000
60.169.74.154: 40000
60.169.74.152: 40000

SinaApiRemain 为java类名
eth0 网卡
2.00SlDQsDdcZIJC94e5308f67sRL13x token
```
