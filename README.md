# 基于giiwa框架的ActiveMQ模块
关于giiwa， 请参阅 http://giiwa.org

提供ActiveMQ的基本配置管理，并向其他模块提供MQ API，以实现分布式处理和简化其他模块的开发。
<h1>功能介绍</h1>
<ul>
<li>MQ消息队列注册api</li>
<li>MQ消息发送api</li>
<li>MQ连接配置管理</li>
</ul>

<h1>开发使用</h1>
<ul>
<li>下载所有源码，然后直接导入Eclipse， 修改...</li>
<li>进入项目目录， 直接运行 ant编译打包, 会生成 activemq_1.0.1_????.zip </li>
<li>在你安装的giiwa 服务器中， 进入后台管理->系统管理->模块管理->上传模块，然后重启giiwa</li>
<li>重启后，进入后台管理->系统管理->ActiveMQL，查看该模块日志和配置管理。</li>
</ul>

activemq模块包含了ActiveMQ Client的所有依赖包。是为第三方模块开发提供分布式消息服务的API模块。
