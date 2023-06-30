BASE_DIR=./data
PORT=5678

if [ $# -gt 0 ]; then
	BASE_DIR=$1
fi

if [ $# -gt 1 ]; then
	PORT=$2
fi

if docker ps | grep -v xiaoya-hostmode | grep -q xiaoya; then
  echo -e "\e[33m其它版本小雅Docker容器运行中。\e[0m"
  while true; do
      read -r -p "是否停止小雅Docker容器？[Y/N] " yn
      case $yn in
          [Yy]* ) docker rm -f xiaoya xiaoya-tvbox 2>/dev/null; break;;
          [Nn]* ) exit 1;;
          * ) echo "请输入'Y'或者'N'";;
      esac
  done
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

docker pull --platform ${platform} haroldli/alist-tvbox:${tag} && \
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
