



```mermaid
gantt
dateFormat  YYYY-MM-DD
title sprint甘特图
section Non-real time dev
    Non-real time plan              :crit,active,des1, 2021-04-01,2021-05-03
section Sprint in 05-11
	05-11 :crit,done,2021-04-05,2021-04-11
    Webpage dev							:active, 2021-04-07, 10d
    webpage with table to input			:crit,active, 2021-04-08, 2021-04-10
    Trial<Routing.html>					:active,2021-04-07,5d
section Sprint in 12-18
	12-18 :crit,done,2021-04-12,2021-04-18
section Sprint in 19-25
	19-25 :crit,done,2021-04-19,2021-04-25
section Sprint in 26-02
	26-02 :crit,done,2021-04-25,2021-05-03
```

## 5 -11

> 结束,清理当前分支的任务。结束web页面的一些尝试性功能包括，statistic页面下的flowtable，以及topology页面上的host弹窗；
>
> - 需求，以testcase列举：
>
>   - flowtable 在有流表时，能在flowtable中记录并显示所有的流表；
>   - topology中，host在第二次触发popup 弹窗时，能够显示其attachment端口信息；
>   - 删除不必要的switch popup 中的信息，变简洁。



创建新分支并开始接下来的任务。

- sprint log
  - 建立表格接收非实时的json数据格式
  - 写module 返回所有的QoS 数据到前端。
  - 另一个<u>尝试</u>：在routing.html 中手动输入一个metic是否有效，尝试修复并运行以熟悉web展示的逻辑。



## 12-18

> 上午上课，下午去公司实习



## 19-25

## 26-02

