#!/bin/bash
set -e

# ====== 配置（签名材料不入库，由环境变量提供） ======
# ANDROID_HOME  Android SDK 路径（需 build-tools/34.0.0 与 platforms/android-34）
# JAVA_HOME     JDK 17+ 路径
# KS_FILE       keystore 路径（默认 android/release.keystore，需自行生成，不入库）
# KS_ALIAS      keystore 别名（默认 suyinji）
# KS_PASS       keystore 密码（必填）
ANDROID_HOME="${ANDROID_HOME:?请设置 ANDROID_HOME 指向 Android SDK}"
JAVA_HOME="${JAVA_HOME:?请设置 JAVA_HOME 指向 JDK 17+}"
KS_FILE="${KS_FILE:-$(cd "$(dirname "$0")" && pwd)/android/release.keystore}"
KS_ALIAS="${KS_ALIAS:-suyinji}"
KS_PASS="${KS_PASS:?请设置 KS_PASS（签名密码）。首次构建请先自行生成 keystore，见 README 构建一节}"

BUILD_TOOLS=$ANDROID_HOME/build-tools/34.0.0
PLATFORM=$ANDROID_HOME/platforms/android-34
APP_ROOT="$(cd "$(dirname "$0")" && pwd)"
PROJECT=$APP_ROOT/android
OUTPUT=$APP_ROOT

VERSION_CODE=22
VERSION_NAME="3.0"

# ====== 清理 ======
rm -rf $PROJECT/build
mkdir -p $PROJECT/build/obj $PROJECT/build/dex $PROJECT/build/apk

# v2.6 修复：打包前自动同步 index.html → assets/（之前漏了这步导致 APK 里打包旧代码）
cp "$APP_ROOT/index.html" "$PROJECT/app/src/main/assets/index.html"
echo ">>> 0. 同步 index.html → assets/ OK"

echo ">>> 1. 编译资源 (aapt2)..."
$BUILD_TOOLS/aapt2 compile \
  --dir $PROJECT/app/src/main/res \
  -o $PROJECT/build/res.zip

$BUILD_TOOLS/aapt2 link \
  -o $PROJECT/build/apk/base.apk \
  -I $PLATFORM/android.jar \
  --manifest $PROJECT/app/src/main/AndroidManifest.xml \
  --java $PROJECT/build/obj \
  --package-id 0x7f \
  --min-sdk-version 21 \
  --target-sdk-version 34 \
  --version-code $VERSION_CODE \
  --version-name "$VERSION_NAME" \
  -A $PROJECT/app/src/main/assets \
  $PROJECT/build/res.zip

echo ">>> 2. 编译 Java..."
mkdir -p $PROJECT/build/classes
$JAVA_HOME/bin/javac \
  -source 11 -target 11 \
  -classpath $PLATFORM/android.jar \
  -d $PROJECT/build/classes \
  $PROJECT/app/src/main/java/com/xiaozhangben/app/*.java \
  $PROJECT/build/obj/com/xiaozhangben/app/R.java

echo ">>> 3. 转换 DEX (d8)..."
$BUILD_TOOLS/d8 \
  --lib $PLATFORM/android.jar \
  --output $PROJECT/build/dex \
  $PROJECT/build/classes/com/xiaozhangben/app/*.class

# Add classes.dex to the APK
cd $PROJECT/build/dex
zip -q $PROJECT/build/apk/base.apk classes.dex

echo ">>> 4. 签名 APK..."
$BUILD_TOOLS/apksigner sign \
  --ks "$KS_FILE" \
  --ks-pass "pass:$KS_PASS" \
  --key-pass "pass:$KS_PASS" \
  --ks-key-alias "$KS_ALIAS" \
  --out $OUTPUT/xiaozhangben.apk \
  $PROJECT/build/apk/base.apk

echo ">>> 完成! APK 已生成: $OUTPUT/xiaozhangben.apk"
ls -lh $OUTPUT/xiaozhangben.apk
