# 澄影 ClearView 推理服务

FastAPI 乳腺超声教学演示后端：BUSI 三类 SigLIP 分类、nnU-Net v2 病灶分割、像素测量与记录管理。分类和分割在运行此服务的机器上完成，助手为可选外部文本模型服务。

> 未经临床验证，不构成诊断依据。后端没有生产级鉴权，不得暴露公网；不要上传包含患者身份信息的影像。

## 1. 环境与依赖

以下命令使用 Windows `cmd` / Anaconda Prompt，从仓库根目录开始。推荐 Python **3.12**、NVIDIA GPU；CPU 也可运行，但更慢。预留模型约 708 MiB，以及 PyTorch、CUDA 库和 Python 依赖所需的额外磁盘空间。

```bat
cd server
py -3.12 -m venv .venv
.venv\Scripts\activate.bat
python -m pip install --upgrade pip
```

没有 `py` 启动器时，在 Anaconda Prompt 中用下面三行替代创建及激活 venv 的命令：

```bat
conda create -n clearview python=3.12 -y
conda activate clearview
python --version
```

选择一套 PyTorch 安装命令，**不要同时执行 CPU 与 CUDA 两套**。已有可用环境无需重装。

```bat
:: 无 NVIDIA GPU：CPU 版
python -m pip install torch==2.14.1 torchvision==0.29.1 --index-url https://download.pytorch.org/whl/cpu
```

```bat
:: NVIDIA GPU 且驱动支持 CUDA 12.6：CUDA 版
python -m pip install torch==2.14.1 torchvision==0.29.1 --index-url https://download.pytorch.org/whl/cu126
```

安装项目依赖与本次使用的 Transformers / 下载工具版本：

```bat
python -m pip install -r requirements.txt transformers==4.57.6 huggingface-hub==0.36.2
```

参考环境：Python 3.12、torch 2.14.1+cu126、torchvision 0.29.1+cu126、transformers 4.57.6、huggingface-hub 0.36.2、nnunetv2 2.8.1。不要求原作者的 Conda 安装目录或私人配置。

## 2. 下载固定版本模型

两个模型均公开提供，无需项目作者的账号或 API Key。首次下载需要联网，模型文件不包含在 Git 仓库中。

| 任务 | 来源 | 仓库许可 | 固定 revision |
|---|---|---|---|
| 分类 | [BUSI SigLIP](https://huggingface.co/ghostsas001/busi-breast-ultrasound-siglip) | Apache-2.0 | `0cb9a9e1671176a29f663a7c79e7437061746961` |
| 分割 | [BUSI nnU-Net](https://huggingface.co/veyselozdemir/nnUNet-Breast-Cancer-Ultrasound) | MIT | `54986829e540020aec55168080ea4c1a3567fe8a` |

如果当前网络不能访问 Hugging Face，可以在当前终端先设置镜像；能直连则跳过：

```bat
set "HF_ENDPOINT=https://hf-mirror.com"
```

确保 Python 环境已激活、当前目录为 `server`，运行以下两条命令。只下载运行必需的 5 个文件，已完成的下载会复用：

```bat
hf download ghostsas001/busi-breast-ultrasound-siglip ^
  config.json model.safetensors ^
  --revision 0cb9a9e1671176a29f663a7c79e7437061746961 ^
  --local-dir models/busi_siglip

hf download veyselozdemir/nnUNet-Breast-Cancer-Ultrasound ^
  nnUNet_results/Dataset101_Breast/nnUNetTrainer_10epochs__nnUNetPlans__2d/dataset.json ^
  nnUNet_results/Dataset101_Breast/nnUNetTrainer_10epochs__nnUNetPlans__2d/plans.json ^
  nnUNet_results/Dataset101_Breast/nnUNetTrainer_10epochs__nnUNetPlans__2d/fold_all/checkpoint_final.pth ^
  --revision 54986829e540020aec55168080ea4c1a3567fe8a ^
  --local-dir models/busi_nnunet
```

预期文件结构：

```text
models/
├── busi_siglip/
│   ├── config.json
│   └── model.safetensors
└── busi_nnunet/nnUNet_results/Dataset101_Breast/nnUNetTrainer_10epochs__nnUNetPlans__2d/
    ├── dataset.json
    ├── plans.json
    └── fold_all/checkpoint_final.pth
```

校验两个权重文件：

```bat
certutil -hashfile models\busi_siglip\model.safetensors SHA256
certutil -hashfile models\busi_nnunet\nnUNet_results\Dataset101_Breast\nnUNetTrainer_10epochs__nnUNetPlans__2d\fold_all\checkpoint_final.pth SHA256
```

| 文件 | SHA-256 |
|---|---|
| `model.safetensors` | `5bbb96dc240b4234ac32a81c3e2b2d50b4d83253a5a3ccc21b659f54b0e43abc` |
| `checkpoint_final.pth` | `6b4c5a9203c1bd741f0c9968ae22b2c1c33a19d63a80b2ed335b7c32722bfef6` |

服务启动还会校验权重 SHA-256 和分类标签顺序；缺失或不匹配会拒绝启动，不使用假结果或静默降级。不要为绕过下载错误而禁用校验。

已有模型可以通过 `CLEARVIEW_MODELS_DIR` 指向上述 `models` 根目录；也支持 `CLEARVIEW_CLASSIFIER_DIR`、`CLEARVIEW_NNUNET_MODEL_DIR` 单独指定目录。默认路径和校验值在 `clearview_server/config.py`。

## 3. 启动与 HTTP 验收

在 `server` 目录、已激活的 Python 环境中运行：

```bat
python run.py
```

默认 `127.0.0.1:8000`，只允许本机访问。可用 `--port 8001` 更换端口；只有可信局域网演示才显式加 `--host 0.0.0.0`，并按需配置防火墙，演示后停止服务。

在另一个终端进入 `server`，检查健康接口：

```bat
curl.exe --fail-with-body http://127.0.0.1:8000/health
```

应返回 `models.pathologies` 为 `["benign","malignant","normal"]`，`device` 为 `cuda` 或 `cpu`。未配置助手时 `assistant.configured` 为 `false`，这不影响影像分析。

使用独立演示命名空间提交三张公开样图：

```bat
curl.exe --fail-with-body http://127.0.0.1:8000/api/v1/analyze ^
  -H "X-Install-Id: clearview-demo" ^
  -F "image=@samples/busi/benign_113_source.jpg;type=image/jpeg" ^
  -F "top_k=3" -F "clientRecordId=demo-benign"

curl.exe --fail-with-body http://127.0.0.1:8000/api/v1/analyze ^
  -H "X-Install-Id: clearview-demo" ^
  -F "image=@samples/busi/malignant_202_source.jpg;type=image/jpeg" ^
  -F "top_k=3" -F "clientRecordId=demo-malignant"

curl.exe --fail-with-body http://127.0.0.1:8000/api/v1/analyze ^
  -H "X-Install-Id: clearview-demo" ^
  -F "image=@samples/busi/normal_92_source.jpg;type=image/jpeg" ^
  -F "top_k=3" -F "clientRecordId=demo-normal"
```

三次均应返回 HTTP 200、`schemaVersion=3`，`primaryLabel` 分别为 `benign`、`malignant`、`normal`；结果包含三类候选、模型溯源、源图和掩码地址。良性及恶性样例有非空掩码；正常样例掩码为空。这只是链路验收，不是独立性能评测。

继续验证记录与资产接口：

```bat
curl.exe --fail-with-body -H "X-Install-Id: clearview-demo" http://127.0.0.1:8000/api/v1/records
curl.exe --fail-with-body -H "X-Install-Id: clearview-demo" http://127.0.0.1:8000/api/v1/records/demo-malignant
curl.exe --fail -H "X-Install-Id: clearview-demo" http://127.0.0.1:8000/api/v1/records/demo-malignant/source.jpg -o demo-source.jpg
curl.exe --fail -H "X-Install-Id: clearview-demo" http://127.0.0.1:8000/api/v1/records/demo-malignant/mask.png -o demo-mask.png
```

`demo-source.jpg`、`demo-mask.png` 是本次命令生成的文件，不应提交到仓库。只删除自己的演示记录：

```bat
curl.exe --fail-with-body -X DELETE -H "X-Install-Id: clearview-demo" http://127.0.0.1:8000/api/v1/records/demo-malignant
```

服务端运行数据默认写入 `server/data/`，已被 Git 忽略；如需隔离验收数据，在启动前设置 `CLEARVIEW_DATA_DIR` 为新的空目录。不要将 `X-Install-Id` 当作访问控制，它只是记录命名空间。

## 4. 手机连接

Android 构建与安装见[根 README](../README.md)。手机需要 Android 8.0 / API 26 或以上。

### USB（推荐）

手机与后端连接同一台电脑，开启并授权 USB 调试，在电脑执行：

```bat
adb -d reverse tcp:8000 tcp:8000
```

手机「设置 → 支持与关于 → 开发者选项」选择 `http://127.0.0.1:8000/` 并点击「使用该地址」，随后在「设置 → 服务状态」确认在线。保存立即生效，**无需重启**。

也可在仓库根目录、激活 Python 环境后运行现有启动器：

```bat
powershell -NoProfile -File .\start-mobile-backend.ps1
```

启动器先配置 USB reverse，再启动仅监听回环地址的后端。后端已启动或 USB 重连时，只恢复映射：

```bat
powershell -NoProfile -File .\start-mobile-backend.ps1 -ReverseOnly
```

启动器支持 `-PythonExe`、`-AdbPath`、`-Port`。ADB 不在 PATH 时使用 SDK 的 `platform-tools/adb.exe`；不修改 PowerShell 执行策略。脚本映射成功不等于模型已加载，仍需检查 `/health` 和 App 服务状态。

### 可信局域网

运行 `python run.py --host 0.0.0.0 --port 8000`，手机「设置 → 服务状态」填写 `http://电脑IPv4:8000/`（将“电脑IPv4”替换为实际数字地址），保存即可使用。debug 和 release 均读取已保存地址。校园 Wi-Fi 可能隔离客户端；先确认手机能访问电脑的 `/health`。

后端没有鉴权，不要配置公网转发或隧道。演示结束后停止服务；服务器需要保持唤醒。USB 模式不要求手机和电脑处于同一 Wi-Fi。

## 5. 可选助手配置

复制 `assistant.env.example` 为 `assistant.env`，填写自己的 OpenAI 兼容模型服务地址、API Key 和模型名：

```bat
copy assistant.env.example assistant.env
```

编辑文件后重启后端。环境变量优先于文件；`/health` 中 `assistant.configured=true` 只说明配置存在，不代表模型服务已验证可调用。

- 不配置时，`/api/v1/assistant/chat` 返回 HTTP 503，分类、分割和历史照常工作。
- 助手发送问题与所引用记录的文本摘要，不上传影像；不要输入患者身份信息。
- Key 只放服务端，不放 APK、不提交到 Git。实际调用可能消耗你自己的模型服务额度。
- 服务端无鉴权，可信内网中的其他设备也可能调用接口，因此不能将服务暴露公网。

## 6. 常见问题

| 现象 | 处理 |
|---|---|
| 没有 `py` 或 `conda` | 安装 Python 3.12 或 Miniconda，使用其对应终端，不需要作者原先的安装目录 |
| `hf` 不存在 | 激活刚安装依赖的环境，确认 `huggingface-hub==0.36.2` 已安装 |
| Hugging Face 连接超时 | 在下载前设置 `HF_ENDPOINT` 镜像；仍需保持固定 revision 并校验权重 |
| `CLASSIFIER_CKPT_MISSING` / `SEG_CKPT_MISSING` | 检查第 2 节的五个文件与目录层级，尤其 nnU-Net 的 `fold_all` |
| `HASH_MISMATCH` | 核对固定 revision 与文件哈希，重新获取错误文件，不关闭校验 |
| 标签顺序错误 | 使用指定 revision 的分类 `config.json` 和权重，不改代码迎合错误模型 |
| SimpleITK / sklearn 导入时 `WinError 1455` | 检查 Windows 内存和页面文件；较小页面文件可能无法加载大型 DLL |
| `/health` 显示 `cpu` | GPU 不可用或安装了 CPU 版 PyTorch；CPU 模式可用但更慢 |
| 端口已被占用 | 检查已有服务或换用 `--port`，不要随意结束其他进程；手机端口与映射需要对应 |
| ADB 无设备 / 未授权 | 检查数据线、USB 调试和手机授权；启动器只选择实体 USB 设备 |
| USB 映射成功但 App 离线 | 先检查后端 `/health`，再检查 App 保存的地址；重连 USB 后恢复 reverse |
| 局域网手机无法连接 | 核对电脑 IPv4、监听地址、防火墙与 Wi-Fi 客户端隔离，不使用 WSL 虚拟网卡地址 |
| 助手返回 503 | 配置自己的助手服务并重启；无需配置助手也可以演示影像分析 |

## 7. 接口与许可边界

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/health` | 设备、模型与助手配置状态 |
| POST | `/api/v1/analyze` | multipart 影像分析 |
| GET | `/api/v1/records` | 当前安装命名空间内的记录 |
| GET / DELETE | `/api/v1/records/{id}` | 记录详情 / 删除 |
| GET | `/api/v1/records/{id}/source.jpg` | 标准化源图 |
| GET | `/api/v1/records/{id}/mask.png` | 逐像素分割掩码 |
| POST | `/api/v1/assistant/chat` | 可选外部模型的文本辅助问答 |

实现入口为 `run.py` 与 `clearview_server/main.py`，HTTP 字段与 Android 的 `core/network` DTO 对齐。原始代码采用仓库根目录的 MIT 许可证，模型、依赖、字体与样图保持各自许可；样图署名见 [SAMPLES_LICENSE.md](samples/SAMPLES_LICENSE.md)。

分类模型的 **87.18% 准确率 / 87.07% 宏 F1** 来自作者模型卡的 78 张留出集，本项目未独立复测。分割模型在 BUSI 全部 780 张图像上训练，演示样图不独立，不声明 Dice 性能。样例经过筛选，两套模型未经本院数据微调；所有结果仅供科研、教学演示，不构成临床诊断依据。
