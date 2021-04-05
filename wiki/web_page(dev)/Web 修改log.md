# Web 修改log



**寻找有用的rest api：**

- 端口的走向

  

192.168.17.1:8080/wm/core/switch/all/<statType>/json 

192.168.17.1:8080/wm/core/switch/<switchId>/<statType>/json 

> ** switchId**: Valid Switch DPID (XX:XX:XX:XX:XX:XX:XX:XX)  **statType**: port, queue, flow, aggregate, desc, table, features 



#### change the topology shows

url: 192.168.17.1:8080/wm/core/switch/1/flow/json

flow format:



#### set up the table

以host为例

url:

1. 配置页面
2. js语句







#### **vis.js**

在谷歌浏览器上好使

| showPopup | id of item corresponding to popup | 当弹出窗口(工具提示)显示时触发。 |
| --------- | --------------------------------- | -------------------------------- |
|           |                                   |                                  |

| on(String event name,Function callback)  | Returns:none | 设置事件侦听器。根据事件的类型，回调函数会得到不同的参数。有关更多信息，请参阅文档的事件部分。 |
| ---------------------------------------- | ------------ | ------------------------------------------------------------ |
| off(String event name,Function callback) | Returns:none | 删除事件侦听器。您提供的函数必须与on函数中使用的函数完全相同。如果没有提供任何函数，所有监听器将被删除。有关更多信息，请参阅文档的事件部分。 |



#### **寻找有用的rest api：**







# Note

ip: 192.168.17.1

#### 查看返回值:curl and http

```
curl 192.168.17.1:8080/wm/core/controller/switches/json
```

~~~json
[
  {
    "inetAddress": "/192.168.17.1:57876",
    "connectedSince": 1617159735427,
    "switchDPID": "00:00:00:00:00:00:00:02",
    "openFlowVersion": "OF_10"
  },
  {
    "inetAddress": "/192.168.17.1:57877",
    "connectedSince": 1617159735427,
    "switchDPID": "00:00:00:00:00:00:00:01",
    "openFlowVersion": "OF_10"
  }
]

http显示
<HashSet>
<item>
<inetAddress>/192.168.17.1:50984</inetAddress>
<connectedSince>1617167123128</connectedSince>
<switchDPID>00:00:00:00:00:00:00:01</switchDPID>
<openFlowVersion>OF_10</openFlowVersion>
</item>
<item>
<inetAddress>/192.168.17.1:50985</inetAddress>
<connectedSince>1617167123128</connectedSince>
<switchDPID>00:00:00:00:00:00:00:02</switchDPID>
<openFlowVersion>OF_10</openFlowVersion>
</item>
</HashSet>
~~~



### REST api 查表

REST API ： [Project Floodlight (atlassian.net)](https://floodlight.atlassian.net/wiki/spaces/floodlightcontroller/pages/1343492/Floodlight+REST+API+pre-v1.0)

| /wm/core/switch/all/<statType>/json        | GET  | Retrieve aggregate stats across all switches | **statType**: port, queue, flow, aggregate, desc, table, features |
| ------------------------------------------ | ---- | -------------------------------------------- | ------------------------------------------------------------ |
| /wm/core/switch/<switchId>/<statType>/json | GET  | Retrieve per switch stats                    | **switchId**: Valid Switch DPID (XX:XX:XX:XX:XX:XX:XX:XX)  **statType**: port, queue, flow, aggregate, desc, table, features |

### 配置静态flow

Static Flow Pusher API pre-v1.0

[Static Flow Pusher API pre-v1.0 - Floodlight Controller - Project Floodlight (atlassian.net)](https://floodlight.atlassian.net/wiki/spaces/floodlightcontroller/pages/1343498/Static+Flow+Pusher+API+pre-v1.0)

```
curl -d '{"switch": "00:00:00:00:00:00:00:01", "name":"flow-mod-1", "priority":"32768", "ingress-port":"1","active":"true", "actions":"output=2"}' http://<``controller_ip``>:8080/wm/staticflowentrypusher/json
```

```
curl http://<``controller_ip``>:8080/wm/core/switch/1/flow/json;
```



### js相关

#### subString

```
stringObject.substring(start,stop)
```

| 参数    | 描述                                                         |
| :------ | :----------------------------------------------------------- |
| *start* | 必需。一个非负的整数，规定要提取的子串的第一个字符在 stringObject 中的位置。 |
| *stop*  | 可选。一个非负的整数，比要提取的子串的最后一个字符在 stringObject 中的位置多 1。如果省略该参数，那么返回的子串会一直到字符串的结尾。 |

#### console.log 弹出消息

```
console.log("this is the params:"+params);
```

## bug

同步js修改

（1）、在调试页面中network勾选Disable cache