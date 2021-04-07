



```mermaid
gantt
dateFormat  YYYY-MM-DD
title sprint甘特图
section sprint1
    需求                      :done,    des1, 2014-01-06,2014-01-08
    原型                      :active,  des2, 2014-01-09, 3d
    UI设计                     :         des3, after des2, 5d
    未来任务                     :         des4, after des3, 5d
section 开发
    学习准备理解需求                      :crit, done, 2014-01-06,24h
    设计框架                             :crit, done, after des2, 2d
    开发                                 :crit, active, 3d
    未来任务                              :crit, 5d
    耍                                   :2d
section 测试
    功能测试                              :active, a1, after des3, 3d
    压力测试                               :after a1  , 20h
    测试报告                               : 48
```
## 5 -11

结束,清理当前分支的任务。结束web页面的一些尝试性功能包括，statistic页面下的flowtable，以及topology页面上的host弹窗；

- 需求，以testcase列举：

  - flowtable 在有流表时，能在flowtable中记录并显示所有的流表；
  - topology中，host在第二次触发popup 弹窗时，能够显示其attachment端口信息；
  - 删除不必要的switch popup 中的信息，变简洁。

  

创建新分支并开始。

非实时的json数据格式





## 12-18

## 19-25

## 26-02

