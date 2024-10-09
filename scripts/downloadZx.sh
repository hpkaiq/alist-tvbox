if ps aux | grep -v grep | grep -q wget ; then
  exit 1
fi

LOCAL="0"
if [ -f /data/zx_version.txt ]; then
  LOCAL=$(head -n 1 </data/zx_version.txt)
else
  cp /zx.zip /data/
fi

REMOTE=$(curl -s http://har01d.org/zx.version)

echo "local zx: ${LOCAL}, remote zx: ${REMOTE}"
if [ "$LOCAL" = "${REMOTE}" ]; then
  echo "sync files"
  rm -rf /www/zx/* && unzip -q -o /data/zx.zip -d /www/zx && [ -d /data/zx ] && cp -r /data/zx/* /www/zx/
  exit 2
fi

LOCAL_NUM=$(echo "$LOCAL" | tr -d '-')
REMOTE_NUM=$(echo "$REMOTE" | tr -d '-')

LOCAL_LEN=$(expr length "$LOCAL_NUM")
REMOTE_LEN=$(expr length "$REMOTE_NUM")

if [ "$LOCAL_LEN" -eq "8" ]; then
    LOCAL_NUM="${LOCAL_NUM}0000"
fi

if [ "$REMOTE_LEN" -eq "8" ]; then
    REMOTE_NUM="${REMOTE_NUM}0000"
fi

if (( REMOTE_NUM > LOCAL_NUM )); then

echo "download ${REMOTE}" && \
wget http://har01d.org/zx.zip -O /data/zx.zip && \
echo "unzip file" && \
rm -rf /www/zx/* && unzip -q -o /data/zx.zip -d /www/zx && \
echo "save version" && \
echo -n ${REMOTE} > /data/zx_version.txt && \
echo "sync files" && \
[ -d /data/zx ] && \
cp -r /data/zx/* /www/zx/

fi