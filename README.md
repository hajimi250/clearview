# 澄影 / ClearView

面向科研、教学与演示的 Android 乳腺超声影像分析应用，使用 **BUSI 三分类（SigLIP）+ nnU-Net 病灶分割**。Android 客户端连接部署在本机或可信局域网内的 FastAPI 服务，分类与分割不依赖付费 API；辅助问答是需要单独配置模型服务的可选功能。

> 未经临床验证，不构成诊断依据。最终诊断须由执业医师结合超声、病理及临床资料作出。

## 功能

- 相册导入 / 系统相机拍摄、检查时间填写、提交影像分析。
- 良性 / 恶性 / 正常三类结果、逐像素病灶分割叠加、像素面积 / 占比 / 外接框测量。
- 本地历史记录、服务端记录同步、影像与掩码下载及记录删除。
- 9 条乳腺超声教学资料、引用分析记录的文本辅助问答。
- 在设置中修改服务地址并立即生效，支持 USB 调试与可信局域网演示。

仅适用于乳腺超声教学演示；不建议使用手机翻拍、拼接截图或其他医学影像模态。

## 代码与运行依赖

仓库包含 Android 和服务端完整源代码、Gradle wrapper、依赖声明及三组公开演示样图。模型权重约 **708 MiB**，不随 Git 仓库分发；[服务端说明](server/README.md)提供固定版本的下载命令、完整目录和 SHA-256 校验值。首次构建与下载需要互联网。

不包含个人 API Key、签名私钥、本地 SDK 配置、虚拟环境或用户运行数据。无需原作者的账号或签名文件即可构建 debug APK；不配置助手 Key 也能运行分类、分割与历史功能。

## 快速开始

以下以 **Windows 10/11** 为主要运行环境。命令块使用 Windows `cmd` / Anaconda Prompt，不是 PowerShell；路径均相对于克隆后的仓库。Linux / macOS 可用 Android Studio 打开项目，但未作为本次验证环境。

### 1. 获取代码

```bat
git clone https://github.com/hajimi250/clearview.git
cd clearview
```

也可以使用 GitHub 的 **Code → Download ZIP** 并解压。不使用仓库中的旧 `package-source.ps1`：该脚本属于历史项目布局，不适用于当前版本。

### 2. 启动分析服务

按照 [server/README.md](server/README.md) 完成：

1. 创建 Python 3.12 环境。
2. 安装 PyTorch 与 `server/requirements.txt`。
3. 下载并校验分类与分割模型。
4. 在 `server` 目录运行：

```bat
python run.py
```

默认只监听 `127.0.0.1:8000`。在另一个终端检查：

```bat
curl.exe --fail http://127.0.0.1:8000/health
```

返回模型信息才表示启动完成；首次加载可能需要等待。没有 NVIDIA GPU 时使用 CPU，速度会更慢。

### 3. 构建 Android 客户端

安装 Android Studio，并在 SDK Manager 中安装 **Android SDK Platform 36、Build-Tools 36.1.0、Platform-Tools**。安装 **JDK 17** 并将 `JAVA_HOME` 指向其安装目录；Android Studio 的 Gradle JDK 也选择 JDK 17。

用 Android Studio 打开仓库根目录并完成同步；它会生成本机 `local.properties`。也可以通过 `ANDROID_HOME` 指向 SDK，或按 `local.properties.windows.example` 创建本机配置，不要直接照搬示例中的磁盘路径。

在仓库根目录运行：

```bat
gradlew.bat :app:assembleDebug --console=plain
```

输出：`app/build/outputs/apk/debug/app-debug.apk`。手机系统要求 Android 8.0 / API 26 或更高。debug 包自动使用开发签名，不需要 `keystore.properties`。

### 4. 手机连接与演示

手机开启 USB 调试，连接运行后端的同一台电脑并接受授权。以下命令要求 `adb` 已加入 PATH；否则使用 SDK 下 `platform-tools/adb.exe` 的完整路径。

```bat
adb devices
adb -d install -r app\build\outputs\apk\debug\app-debug.apk
adb -d reverse tcp:8000 tcp:8000
```

1. 打开「澄影」；在「设置 → 支持与关于 → 开发者选项」选择 USB 回环地址 `http://127.0.0.1:8000/`，点击「使用该地址」。保存后立即生效，无需重启 App。
2. 在「设置 → 服务状态」确认分析服务在线。
3. 把 `server/samples/busi/*_source.jpg` 复制到手机相册，导入其中一张并提交分析；不要导入预绘制的 `*_overlay.png`。
4. 查看分类与分割叠加，在历史页回看或删除本次演示记录。正常样例为空掩码，不显示病灶叠加。
5. 辅助问答需按服务端说明配置自己的 Key；未配置时显示服务未配置，不影响影像分析。

USB 重连后通常需要重新执行 `adb -d reverse tcp:8000 tcp:8000`。已有 `start-mobile-backend.ps1` 可完成映射和启动，也支持 `-ReverseOnly`；详见服务端说明。

**局域网演示**：显式运行 `python run.py --host 0.0.0.0 --port 8000`，在手机「设置 → 服务状态」填入电脑的 IPv4 地址与端口，保存立即生效。手机必须能访问电脑，校园 Wi-Fi 可能隔离设备。后端没有生产级鉴权，只能在可信环境内短时演示，**不得暴露公网**。

## 验证与限制

- 2026-10-30：从不包含本机配置、密钥与构建产物的源码副本完成 Windows debug 构建，261 项 Gradle 任务全部执行。复用已安装的 SDK / 依赖缓存，不等于全新系统安装验证。
- 同次启动独立数据目录的真实后端，复用校验过的本地权重；三类样图均返回 HTTP 200 与对应分类，记录查询和源图 / 掩码下载成功。无效图片返回 400，未配置助手返回 503。模型配置文件的固定版本下载命令也已实际执行。
- 本次没有连接真机或配置模拟器，未重新验证手机端交互，也未进行独立模型性能评估。服务端说明提供可复现的 HTTP 验收步骤。
- 仓库包含数据库与网络映射单元测试，可在根目录执行 `gradlew.bat :core:database:testDebugUnitTest :core:network:testDebugUnitTest`；本次没有运行测试套件，它们不等于真机端到端验收。
- release 构建需要自行配置签名；没有 `keystore.properties` 时只生成未签名包。debug 的验证结果不能代替 release 真机验证。
- 分类模型卡自报 78 张留出集准确率 **87.18%**、宏 F1 **87.07%**，本项目未独立复测。
- 分割模型使用 BUSI 全部 780 张图像训练，演示图与训练集不独立；样例经过筛选，不代表整体准确率，不作 Dice 或临床性能声明。
- 影像在配置的本机或局域网服务端处理；助手会向配置的外部模型服务发送问题及引用记录的文本摘要，不发送影像。演示时不要输入患者身份信息。

## 目录

```text
app/                       Android 入口、导航和应用配置
core/model/                领域模型
core/network/              HTTP 网关、DTO 与结果映射
core/database/             本地历史与资产存储
core/designsystem/         主题、组件与影像查看器
feature/                   采集、结果、历史、知识库、助手与设置
server/                    FastAPI 推理服务、依赖及运行说明
server/samples/busi/       良性、恶性、正常演示样图
gradle/                    Gradle wrapper 与版本配置
```

当前实现以 BUSI 乳腺超声为准；历史农业、胸片与皮肤镜方案不属于当前功能。开发记录见 [PLAN.md](PLAN.md)，新环境运行以本页和服务端说明为准。

## 许可证与第三方内容

项目原始代码使用 [MIT License](LICENSE)。第三方代码、依赖、模型与素材不因本仓库的 MIT 声明而改变许可：

- 分类模型：`ghostsas001/busi-breast-ultrasound-siglip`，模型仓库标注 Apache-2.0。
- 分割模型：`veyselozdemir/nnUNet-Breast-Cancer-Ultrasound`，模型仓库标注 MIT。
- BUSI 样图及处理说明：[SAMPLES_LICENSE.md](server/samples/SAMPLES_LICENSE.md)。
- 知识库图片来源：[KNOWLEDGE_IMAGES_LICENSE.md](feature/knowledge/KNOWLEDGE_IMAGES_LICENSE.md)。
- IBM Plex 字体：[英文与等宽字体许可](core/designsystem/src/main/assets/IBM-Plex-LICENSE.txt)、[简体中文字体许可](core/designsystem/src/main/assets/IBM-Plex-Sans-SC-LICENSE.txt)。

模型仅按其公开来源获取；使用、修改和再分发第三方内容时应继续保留对应许可与署名。
