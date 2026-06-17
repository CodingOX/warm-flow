#!/bin/bash
# warm-flow-ui 构建并同步到后端插件资源目录
# 执行: yarn build:prod 后将 dist 复制到 warm-flow-plugin-vue3-ui 的静态资源目录

set -e

TARGET="../warm-flow-plugin/warm-flow-plugin-ui/warm-flow-plugin-vue3-ui/src/main/resources/warm-flow-ui"

echo "==> 1/2 构建前端生产包..."
yarn build:prod

echo "==> 2/2 同步 dist 到 $TARGET ..."
rm -rf "$TARGET"/*
cp -r dist/* "$TARGET/"

echo "==> 完成！请执行 mvn install 打包后端"
