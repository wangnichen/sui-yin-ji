# 碎银记 GitHub 发布指南

本文档教你如何把碎银记发布到 GitHub，包括创建仓库、上传源码、发布 Release。

---

## 一、准备工作

1. 注册 GitHub 账号：https://github.com
2. 安装 Git：https://git-scm.com/downloads
3. 准备好 `碎银记v2.0发布包` 里的文件

---

## 二、创建 GitHub 仓库

1. 登录 GitHub，点击右上角 **+** → **New repository**
2. 填写：
   - **Repository name**：`suiyinji`（或 `碎银记`，建议英文）
   - **Description**：`一个简洁好用的个人记账工具 · 碎银记`
   - 选择 **Public**（公开）
   - 勾选 **Add a README file**
3. 点击 **Create repository**

---

## 三、上传源代码到仓库

### 方式 A：网页上传（最简单）

1. 打开刚创建的仓库页面
2. 点击 **Add file** → **Upload files**
3. 把 `源代码/` 文件夹里的 5 个文件拖进去：
   - `index.html`
   - `MainActivity.java`
   - `AndroidManifest.xml`
   - `build_apk.sh`
   - `manifest.json`
4. 底部填写提交信息：`feat: 碎银记 v2.0 源代码`
5. 点击 **Commit changes**

### 方式 B：用 Git 命令行（推荐）

```bash
# 1. 克隆仓库（把下面的 URL 换成你自己的）
git clone https://github.com/你的用户名/suiyinji.git
cd suiyinji

# 2. 复制源码文件进去
cp /path/to/碎银记v2.0发布包/源代码/* .

# 3. 提交并推送
git add .
git commit -m "feat: 碎银记 v2.0 源代码"
git push
```

---

## 四、添加 README

把发布包里的 `README.md` 内容复制到仓库的 README.md（或直接上传覆盖）。

---

## 五、发布 Release（让用户下载 APK）

1. 在仓库页面点击右侧 **Releases** → **Create a new release**
2. 填写：
   - **Choose a tag**：输入 `v2.0`，点 **Create new tag: v2.0**
   - **Release title**：`碎银记 v2.0 · 沉浸式体验 & 统计页重构`
   - **Describe this release**：复制粘贴下面的内容 👇

```markdown
## 碎银记 v2.0 更新内容

### 🎨 全新交互体验
- 左右滑动切换页面，紫色玻璃指示器实时跟随
- 页面永不回顶端，每个页面独立记忆滚动位置
- 底部 Dock 栏固定屏幕最底端
- 透明沉浸式状态栏 + 毛玻璃质感
- 液态玻璃指示器可拖拽切换

### 📊 统计页重构
- 列表 / 组件双视图切换，带缩放动画
- 默认 6 大实用组件：智能洞察、支出分类、消费日历、趋势图、支出排行、月度对比
- 组件内容填满方框，支持内部滚动

### 🪙 碎银时钟
- 实时时钟 + 日期 + 星期 + 轮换诗句
- NTP 网络时间校准

### 🎋 碎银签
- 每日一签问财运

---

**下载**：碎银记_v2.0.apk（Android 7.0+）
```

3. 点击 **Attach binaries** ，把 `碎银记_v2.0.apk` 拖进去
4. 勾选 **Set as the latest release**
5. 点击 **Publish release**

---

## 六、发布完成后

- 你的仓库地址：`https://github.com/你的用户名/suiyinji`
- Release 下载页：`https://github.com/你的用户名/suiyinji/releases`
- 把这个链接放到你的视频简介、评论区，用户就能下载了

---

## 七、后续更新发布新版本

当你有新版本时：

1. 把新代码 `git push` 到仓库
2. 重新打一个 Release，tag 用 `v2.1`、`v3.0` 等
3. 上传新的 APK

---

## 八、小提示

- GitHub 仓库大小限制 100MB，APK 只有 778K，完全没问题
- 如果要上传图标和海报，可以在仓库建一个 `assets/` 文件夹
- 想让别人更容易搜到，仓库描述里加上关键词：记账、理财、Android、开源、学生作品
