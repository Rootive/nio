# Network by Rootive
## 关于 rpc
主要的特性有仨：
- 可无编码传输二进制
- 并发调用
- 链式调用
## 关于 nio
主要实现了可靠udp：
- 宏观上“有序可靠”
- 局部“无序可靠”
## 关于 distributed
主要实现了raft：
- 领导选举
- 日志复制