#!/bin/bash
set -e

# ====== 配置 ======
ANDROID_HOME=/workspace/android-sdk
BUILD_TOOLS=$ANDROID_HOME/build-tools/34.0.0
PLATFORM=$ANDROID_HOME/platforms/android-34
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
PROJECT=/workspace/expense-tracker/android
OUTPUT=/workspace/expense-tracker

# ====== 清理 ======
rm -rf $PROJECT/build
mkdir -p $PROJECT/build/obj $PROJECT/build/dex $PROJECT/build/apk

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
  --version-code 1 \
  --version-name "2.0" \
  -A $PROJECT/app/src/main/assets \
  $PROJECT/build/res.zip

echo ">>> 2. 编译 Java..."
mkdir -p $PROJECT/build/classes
$JAVA_HOME/bin/javac \
  -source 17 -target 17 \
  -classpath $PLATFORM/android.jar \
  -d $PROJECT/build/classes \
  $PROJECT/app/src/main/java/com/xiaozhangben/app/MainActivity.java \
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
# Use release keystore
$BUILD_TOOLS/apksigner sign \
  --ks $PROJECT/release.keystore \
  --ks-pass pass:SYJ2026Release \
  --key-pass pass:SYJ2026Release \
  --out $OUTPUT/xiaozhangben.apk \
  $PROJECT/build/apk/base.apk

echo ">>> 完成! APK 已生成: $OUTPUT/xiaozhangben.apk"
ls -lh $OUTPUT/xiaozhangben.apk