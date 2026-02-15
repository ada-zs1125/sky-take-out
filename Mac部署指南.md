## 🍔 Mac 本地部署完整指南

> 适用于 macOS（Apple Silicon / Intel 均可）
> 技术栈：Spring Boot + MySQL + Redis + Nginx

---

### 📌 一、环境准备

#### 1️⃣ 安装 Homebrew

如果未安装：

```bash
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
```

建议更换清华源（可自行查清华源官方教程）。

---

#### 2️⃣ 安装基础服务

#### 安装 Nginx

```bash
brew install nginx
```

#### 安装 Redis

```bash
brew install redis
```

#### 安装 MySQL

```bash
brew install mysql
```

---

#### 3️⃣ 启动服务

```bash
brew services start mysql
brew services start redis
brew services start nginx
```

查看服务状态：

```bash
brew services list
```

正常应显示：

```
mysql  started
redis  started
nginx  started
```

---

### 📌 二、确认 Nginx 配置文件位置（关键）

查看 nginx 路径：

```bash
which nginx
```

如果显示：

```
/opt/homebrew/bin/nginx
```

查看配置文件：

```bash
nginx -t
```

会显示类似：

```
/opt/homebrew/etc/nginx/nginx.conf
```

⚠️ 这个才是真正生效的配置文件路径。

---

### 📌 三、配置前端静态资源

假设你的前端目录为：

```
/Users/你的用户名/Desktop/Sky-take-out/Front-end/nginx-1.20.2/html/sky
```

---

#### 1️⃣ 编辑 nginx.conf

```bash
nano /opt/homebrew/etc/nginx/nginx.conf
```

找到：

```nginx
server {
    listen 80;
    server_name localhost;
```

修改 root 为你的前端绝对路径：

```nginx
location / {
    root   /Users/你的用户名/Desktop/Sky-take-out/Front-end/nginx-1.20.2/html/sky;
    index  index.html index.htm;
}
```

---

#### 2️⃣ 保存退出

在 nano 中：

```
Ctrl + O
Enter
Ctrl + X
```

---

#### 3️⃣ 重新加载配置

```bash
brew services restart nginx
```

或：

```bash
nginx -s reload
```

---

#### 4️⃣ 配置访问权限

```bash
sudo chmod -R 755 /Users/你的用户名/Desktop/Sky-take-out/Front-end
```

---

#### 5️⃣ 访问前端

浏览器访问：

```
http://localhost
```

默认端口 80。

---

### 📌 四、启动后端服务

进入后端项目目录：

```bash
mvn clean install
mvn spring-boot:run
```

或直接运行：

```
SkyServerApplication
```

⚠️ 记得修改：

```
application-dev.yml
```

包括：

* 数据库账号密码
* Redis 地址
* OSS 配置

---

### 📌 五、Redis 操作

启动：

```bash
brew services start redis
```

停止：

```bash
brew services stop redis
```

默认端口：

```
6379
```

---

### 📌 六、MySQL 启动

```bash
brew services start mysql
```

默认端口：

```
3306
```

导入数据库 SQL 脚本后再启动后端。

---

### 📌 七、微信小程序登录无弹窗问题

如果点击登录不弹出授权框：

1. 打开微信开发者工具
2. 点击「详情」
3. 本地设置
4. 调试基础库版本改为：

```
2.25.4
```

重新编译即可。

---

### 📌 八、常见问题排查

#### 1️⃣ 查看 nginx 是否启动

```bash
ps aux | grep nginx
```

---

#### 2️⃣ 测试 nginx 配置是否正确

```bash
nginx -t
```

---

#### 3️⃣ 重启 nginx

```bash
brew services restart nginx
```

---

#### 4️⃣ 如果端口 80 无法访问

可能被占用：

```bash
lsof -i :80
```

---

### 📌 九、完整启动顺序

推荐顺序：

```
1️⃣ MySQL
2️⃣ Redis
3️⃣ 后端服务
4️⃣ Nginx
5️⃣ 浏览器访问
```

---

### 📌 十、最终访问地址

```
http://localhost
```

---

### 🎯 部署完成

当你能正常：

* 登录后台
* 上传图片
* 创建订单
* 查看商品

说明部署成功。

---

### 📌 附：可选源码编译 Nginx（不推荐）

如果一定要源码编译（一般不需要）：

```bash
tar -zxvf nginx-1.24.0.tar.gz
cd nginx-1.24.0
./configure
make
sudo make install
```

⚠️ 但 Mac 使用 brew 安装更简单稳定。


