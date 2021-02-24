# 项目说明

本项目集成了 SEATA 1.4.1 版本，要使用本项目，引用本项目到工程中，并在 Application Context 文件中配置 `@Import({TxAspect.class})`。

FEATURE

- 集成 SEATA 1.4.1
- AT 模式下，通过 AOP 完成回滚，引入本工程实现业务代码无入侵
- 使用 AOP @AfterReturning 特性时，需要返回值中以下有两个属性中的一个

    ``` java
    //值为 true 时完成回滚
    private Boolean failed;

    //错误时需要值等于 -1 以完成回滚
    private String code;
    ```

## SEATA 配置

使用 nacos 做为配置中心配置 SEATA

当前 SEATA 版本： **1.4.1**

## TC (Transaction Coordinator) - 事务协调者

**维护全局和分支事务的状态，驱动全局事务提交或回滚。**

### 配置参数

[参数文档](http://seata.io/zh-cn/docs/user/configurations.html)

<details>
<summary>config.txt</summary>

``` conf
transport.type=TCP
transport.server=NIO
transport.heartbeat=true
transport.enableClientBatchSendRequest=false
transport.threadFactory.bossThreadPrefix=NettyBoss
transport.threadFactory.workerThreadPrefix=NettyServerNIOWorker
transport.threadFactory.serverExecutorThreadPrefix=NettyServerBizHandler
transport.threadFactory.shareBossWorker=false
transport.threadFactory.clientSelectorThreadPrefix=NettyClientSelector
transport.threadFactory.clientSelectorThreadSize=1
transport.threadFactory.clientWorkerThreadPrefix=NettyClientWorkerThread
transport.threadFactory.bossThreadSize=1
transport.threadFactory.workerThreadSize=default
transport.shutdown.wait=3
service.vgroupMapping.app-server-tx-group=default
service.default.grouplist=127.0.0.1:8091
service.enableDegrade=false
service.disableGlobalTransaction=false
client.rm.asyncCommitBufferLimit=10000
client.rm.lock.retryInterval=10
client.rm.lock.retryTimes=30
client.rm.lock.retryPolicyBranchRollbackOnConflict=true
client.rm.reportRetryCount=5
client.rm.tableMetaCheckEnable=false
client.rm.sqlParserType=druid
client.rm.reportSuccessEnable=false
client.rm.sagaBranchRegisterEnable=false
client.tm.commitRetryCount=5
client.tm.rollbackRetryCount=5
client.tm.defaultGlobalTransactionTimeout=60000
client.tm.degradeCheck=false
client.tm.degradeCheckAllowTimes=10
client.tm.degradeCheckPeriod=2000
store.mode=db
store.file.dir=file_store/data
store.file.maxBranchSessionSize=16384
store.file.maxGlobalSessionSize=512
store.file.fileWriteBufferCacheSize=16384
store.file.flushDiskMode=async
store.file.sessionReloadReadSize=100
store.db.datasource=druid
store.db.dbType=mysql
store.db.driverClassName=com.mysql.jdbc.Driver
store.db.url=url
store.db.user=root
store.db.password=123456
store.db.minConn=5
store.db.maxConn=30
store.db.globalTable=global_table
store.db.branchTable=branch_table
store.db.queryLimit=100
store.db.lockTable=lock_table
store.db.maxWait=5000
store.redis.host=127.0.0.1
store.redis.port=6379
store.redis.maxConn=10
store.redis.minConn=1
store.redis.database=10
store.redis.password=null
store.redis.queryLimit=100
server.recovery.committingRetryPeriod=1000
server.recovery.asynCommittingRetryPeriod=1000
server.recovery.rollbackingRetryPeriod=1000
server.recovery.timeoutRetryPeriod=1000
server.maxCommitRetryTimeout=-1
server.maxRollbackRetryTimeout=-1
server.rollbackRetryTimeoutUnlockEnable=false
client.undo.dataValidation=true
client.undo.logSerialization=jackson
client.undo.onlyCareUpdateColumns=true
server.undo.logSaveDays=7
server.undo.logDeletePeriod=86400000
client.undo.logTable=undo_log
client.log.exceptionRate=100
transport.serialization=seata
transport.compressor=none
metrics.enabled=false
metrics.registryType=compact
metrics.exporterList=prometheus
metrics.exporterPrometheusPort=9898
```

</details>

### nacos bash 脚本

[参考 SEATA github 配置说明](https://github.com/seata/seata/tree/develop/script/config-center)

<details>
<summary>nacos-config.sh</summary>

``` bash

while getopts ":h:p:g:t:u:w:" opt
do
  case $opt in
  h)
    host=$OPTARG
    ;;
  p)
    port=$OPTARG
    ;;
  g)
    group=$OPTARG
    ;;
  t)
    tenant=$OPTARG
    ;;
  u)
    username=$OPTARG
    ;;
  w)
    password=$OPTARG
    ;;
  ?)
    echo " USAGE OPTION: $0 [-h host] [-p port] [-g group] [-t tenant] [-u username] [-w password] "
    exit 1
    ;;
  esac
done

if [[ -z ${host} ]]; then
    host=localhost
fi
if [[ -z ${port} ]]; then
    port=8848
fi
if [[ -z ${group} ]]; then
    group="SEATA_GROUP"
fi
if [[ -z ${tenant} ]]; then
    tenant=""
fi
if [[ -z ${username} ]]; then
    username=""
fi
if [[ -z ${password} ]]; then
    password=""
fi

nacosAddr=$host:$port
contentType="content-type:application/json;charset=UTF-8"

echo "set nacosAddr=$nacosAddr"
echo "set group=$group"

failCount=0
tempLog=$(mktemp -u)
function addConfig() {
  curl -X POST -H "${contentType}" "http://$nacosAddr/nacos/v1/cs/configs?dataId=$1&group=$group&content=$2&tenant=$tenant&username=$username&password=$password" >"${tempLog}" 2>/dev/null
  if [[ -z $(cat "${tempLog}") ]]; then
    echo " Please check the cluster status. "
    exit 1
  fi
  if [[ $(cat "${tempLog}") =~ "true" ]]; then
    echo "Set $1=$2 successfully "
  else
    echo "Set $1=$2 failure "
    (( failCount++ ))
  fi
}

count=0
for line in $(cat config.txt | sed s/[[:space:]]//g); do
  (( count++ ))
	key=${line%%=*}
    value=${line#*=}
	addConfig "${key}" "${value}"
done

echo "========================================================================="
echo " Complete initialization parameters,  total-count:$count ,  failure-count:$failCount "
echo "========================================================================="

if [[ ${failCount} -eq 0 ]]; then
	echo " Init nacos config finished, please start seata-server. "
else
	echo " init nacos config fail. "
fi
```

</details>

### 同步 config 配置到 nacos

进入 TC 服务器

- 新建文件夹 seata-config
- 进入 seata-config
- 新建 config.txt 文件并复制配置参数到 config.txt 文件中
- 新建 nacos-config.sh 文件，同时复制 nacos bash 脚本到 nacos-config.sh 中
- 使用以下命令同步配置参数到 nacos

    ``` bash
    bash nacos-config.sh -h 127.0.0.1 -p 8848 -g SEATA_GROUP -t 3a2aea46-07c6-4e21-9a1e-8946cde9e2b3 -u nacos -w nacos
    ```

  得到输出

    ``` bash
    set nacosAddr=127.0.0.1:8848
    set group=SEATA_GROUP
    Set transport.type=TCP successfully 
    Set transport.server=NIO successfully 
    .
    .
    .
    =========================================================================
    Complete initialization parameters,  total-count:80 ,  failure-count:0 
    =========================================================================
    Init nacos config finished, please start seata-server. 
    ```

### 使用 docker 部署 SEATA

Docker 部署 SEATA 官方[文档](https://seata.io/zh-cn/docs/ops/deploy-by-docker.html)

进入 TC 服务器，并进入 seat-config 文件夹

- 新建 registry.conf 文件，并添加以下内容，registry
  配置[参考](https://github.com/seata/seata/blob/develop/script/server/config/registry.conf)

  <details>
    <summary>registry.conf</summary>

    ``` conf
    registry {
    # file 、nacos 、eureka、redis、zk、consul、etcd3、sofa
    type = "nacos"

    nacos {
        application = "seata-server"
        group = "SEATA_GROUP"
        serverAddr = "127.0.0.1"
        namespace = "3a2aea46-07c6-4e21-9a1e-8946cde9e2b3"
        cluster = "default"
    }
    }

    config {
    # file、nacos 、apollo、zk、consul、etcd3、springCloudConfig
    type = "nacos"

    nacos {
        serverAddr = "127.0.0.1"
        namespace = "3a2aea46-07c6-4e21-9a1e-8946cde9e2b3"
        group = "SEATA_GROUP"
        username = "nacos"
        password = "nacos"
    }
    }
    ```

    </details>

- 新建 file.conf 文件并添加以下内容（可选，可通过 nacos 读取）

  <details>
    <summary>file.conf</summary>

    ``` conf
    transport {
    # tcp udt unix-domain-socket
    type = "TCP"
    #NIO NATIVE
    server = "NIO"
    #enable heartbeat
    heartbeat = true
    # the client batch send request enable
    enableClientBatchSendRequest = true
    #thread factory for netty
    threadFactory {
        bossThreadPrefix = "NettyBoss"
        workerThreadPrefix = "NettyServerNIOWorker"
        serverExecutorThread-prefix = "NettyServerBizHandler"
        shareBossWorker = false
        clientSelectorThreadPrefix = "NettyClientSelector"
        clientSelectorThreadSize = 1
        clientWorkerThreadPrefix = "NettyClientWorkerThread"
        # netty boss thread size,will not be used for UDT
        bossThreadSize = 1
        #auto default pin or 8
        workerThreadSize = "default"
    }
    shutdown {
        # when destroy server, wait seconds
        wait = 3
    }
    serialization = "seata"
    compressor = "none"
    }
    service {
    #transaction service group mapping
    vgroupMapping.my_test_tx_group = "default"
    #only support when registry.type=file, please don't set multiple addresses
    default.grouplist = "127.0.0.1:8091"
    #degrade, current not support
    enableDegrade = false
    #disable seata
    disableGlobalTransaction = false
    }

    client {
    rm {
        asyncCommitBufferLimit = 10000
        lock {
        retryInterval = 10
        retryTimes = 30
        retryPolicyBranchRollbackOnConflict = true
        }
        reportRetryCount = 5
        tableMetaCheckEnable = false
        reportSuccessEnable = false
    }
    tm {
        commitRetryCount = 5
        rollbackRetryCount = 5
    }
    undo {
        dataValidation = true
        logSerialization = "jackson"
        logTable = "undo_log"
    }
    log {
        exceptionRate = 100
    }
    }
    ```

    </details>

- 运行 docker 命令
    - **注意：** 当在 config.txt 中配置 **store.mode=db** 时，需要在配置的数据库连接中初始化表 `global_table`、`branch_table`、`lock_table`
      ，[sql 传送门](https://github.com/seata/seata/blob/develop/script/server/db/mysql.sql)。

  ``` bash
  docker run -d --name seata-server \
        --net=host \
        -p 8091:8091 \
        -e SEATA_CONFIG_NAME=file:/root/seata-config/registry \
        -v /root/seata-config:/root/seata-config  \
        seataio/seata-server:1.4.1
  ```

  挂载目录为 TC 服务器配置目录。

## TM (Transaction Manager) - 事务管理器

**定义全局事务的范围：开始全局事务、提交或回滚全局事务。**

例子：业务聚合服务

- SEATA 包引入。pom 配置如下，当使用 `spring-cloud-starter-openfeign` 包时，需要移除 `spring-cloud-starter-openfeign`
  包，`spring-cloud-starter-alibaba-seata` 中已经包含了 `spring-cloud-starter-openfeign` ,再次引入可能导致包冲突。

    <details>
    <summary>pom.xml</summary>

    ``` maven
    <dependency>
        <groupId>io.seata</groupId>
        <artifactId>seata-spring-boot-starter</artifactId>
        <version>1.4.1</version>
    </dependency>
    <dependency>
        <groupId>com.alibaba.cloud</groupId>
        <artifactId>spring-cloud-starter-alibaba-seata</artifactId>
        <version>2.2.1.RELEASE</version>
        <exclusions>
            <exclusion>
                <groupId>io.seata</groupId>
                <artifactId>seata-spring-boot-starter</artifactId>
            </exclusion>
            <exclusion>
                <groupId>io.seata</groupId>
                <artifactId>seata-all</artifactId>
            </exclusion>
        </exclusions>
    </dependency>
    ```

</details>

- 添加 registry.conf。在工程中 resource 目录下添加如下内容

    <details>
    <summary>registry.conf</summary>

    ``` conf
    registry {
    # file 、nacos 、eureka、redis、zk、consul、etcd3、sofa
    type = "nacos"

    nacos {
        application = "seata-server"
        serverAddr = "127.0.0.1:8848"
        namespace = "3a2aea46-07c6-4e21-9a1e-8946cde9e2b3"
        cluster = "default"
        username = "nacos"
        password = "nacos"
    }
    }

    config {
    # file、nacos 、apollo、zk、consul、etcd3、springCloudConfig
    type = "nacos"

    nacos {
        serverAddr = "127.0.0.1:8848"
        namespace = "3a2aea46-07c6-4e21-9a1e-8946cde9e2b3"
        group = "SEATA_GROUP"
        username = "nacos"
        password = "nacos"
    }
    }

    ```

    </details>

- 配置 bootstrap.properties，添加配置,内容如下，`seata.tx-service-group` 和 `namespace` 改为对应的值
  <details>
  <summary>bootstrap.properties</summary>

  ``` properties
    ...
    seata.tx-service-group=app-server-tx-group
    seata.config.type=nacos
    seata.config.nacos.server-addr=127.0.0.1:8848
    seata.config.nacos.namespace=3a2aea46-07c6-4e21-9a1e-8946cde9e2b3
    seata.config.nacos.group=SEATA_GROUP
  ```

  </details>

在 TM 中通过 `@GlobalTransactional` 开启全局异常，示例代码:

``` java
 
    @GlobalTransactional
    @GetMapping({"create"})
    public String create(String name,Integer age) {
        ...
        return "创建成功";
    }
```

## RM (Resource Manager) - 资源管理器

**管理分支事务处理的资源，与TC交谈以注册分支事务和报告分支事务的状态，并驱动分支事务提交或回滚。**

例子：被调用服务。

- 配置 bootstrap.properties，添加配置,内容如下，`seata.tx-service-group` 和 `namespace` 改为对应的值
  <details>
  <summary>bootstrap.properties</summary>

  ``` properties
    ...
    seata.tx-service-group=app-server-tx-group
    seata.config.type=nacos
    seata.config.nacos.server-addr=127.0.0.1:8848
    seata.config.nacos.namespace=3a2aea46-07c6-4e21-9a1e-8946cde9e2b3
    seata.config.nacos.group=SEATA_GROUP
  ```

  </details>

- 对需要做回滚的业务标记 `@Transactional(rollbackFor = Exception.class)`

- AT(Automatic Transaction) 模式下配置 [undo_log](https://github.com/seata/seata/tree/develop/script/client/at/db) 数据库表，mysql
  建表 sql 如下

    ``` sql
    CREATE TABLE IF NOT EXISTS `undo_log`
    (
        `branch_id`     BIGINT(20)   NOT NULL COMMENT 'branch transaction id',
        `xid`           VARCHAR(100) NOT NULL COMMENT 'global transaction id',
        `context`       VARCHAR(128) NOT NULL COMMENT 'undo_log context,such as serialization',
        `rollback_info` LONGBLOB     NOT NULL COMMENT 'rollback info',
        `log_status`    INT(11)      NOT NULL COMMENT '0:normal status,1:defense status',
        `log_created`   DATETIME(6)  NOT NULL COMMENT 'create datetime',
        `log_modified`  DATETIME(6)  NOT NULL COMMENT 'modify datetime',
        UNIQUE KEY `ux_undo_log` (`xid`, `branch_id`)
    ) ENGINE = InnoDB
      AUTO_INCREMENT = 1
      DEFAULT CHARSET = utf8 COMMENT ='AT transaction mode undo table';
    ```

## 关闭 SEATA

``` properties
seata.enabled=false
```
