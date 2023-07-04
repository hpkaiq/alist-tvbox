BASE_DIR=./data
PORT=5678

if [ $# -gt 0 ]; then
	BASE_DIR=$1
fi

if [ $# -gt 1 ]; then
	PORT=$2
fi

if docker ps | grep -q xiaoya-tvbox; then
  echo -e "\e[33m集成版Docker容器运行中。\e[0m"
  read -r -p "是否停止集成版Docker容器？[Y/N] " yn
  case $yn in
      [Yy]* ) docker rm -f xiaoya-tvbox 2>/dev/null;;
      [Nn]* ) exit 0;;
  esac
fi

echo -e "\e[36m端口映射：\e[0m $PORT:8080"

docker image prune -f

platform="linux/amd64"
tag="latest"
ARCH=$(uname -m)
if [ "$ARCH" = "armv7l" ]; then
  platform="linux/arm/v7"
  tag="arm-v7"
elif [ "$ARCH" = "aarch64" ]; then
    platform="linux/arm64"
fi

echo -e "\e[32m下载最新Docker镜像，平台：${platform}\e[0m"
for i in 1 2 3 4 5
do
   docker pull --platform ${platform} haroldli/alist-tvbox:${tag} && break
done

docker rm -f alist-tvbox && \
docker run -d -p $PORT:8080 --restart=always -v "$BASE_DIR":/data --name=alist-tvbox haroldli/alist-tvbox:${tag}

IP=$(ip a | grep -F '192.168.' | awk '{print $2}' | awk -F/ '{print $1}' | head -1)
if [ -n "$IP" ]; then
  echo -e "\e[32m请用以下地址访问：\e[0m"
  echo -e "    \e[32m管理界面\e[0m： http://$IP:$PORT/"
else
  echo -e "\e[32m云服务器请用公网IP访问\e[0m"
fi
echo ""
