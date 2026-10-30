# 澄影 / ClearView —— BUSI 乳腺超声演示版计划

> 最后更新：2026-10-25
> 当前主体：BUSI 三类乳腺超声分类（SigLIP）+ nnU-Net v2 病灶分割。
> 本文件描述当前工作区实现。旧胸片与旧 ISIC 皮肤镜内容已停用，相关代码、样例与权重已在 2026-10 清理批中删除，见 §8。

## 0. 定位与边界

**澄影 / ClearView** 是一个面向科研、教学与演示的 Android 乳腺超声影像分析应用：

- Android 端负责采集/导入超声影像、提交分析、展示分类候选与真实病灶分割、保存历史记录、引用记录进行辅助问答。
- 服务端负责三类 softmax 分类、nnU-Net 逐像素病灶分割、面积/占比/外接框测量与历史记录存储。
- 结果仅供科研与教学参考，**未经临床验证，不构成诊断依据**。最终诊断须由执业医师结合超声、病理及临床资料作出。

本项目不承诺生产级鉴权、完整审计、去标识化或临床级性能；这些能力没有因为本版页面存在而自动完成。

## 1. 当前完成状态

| 范围 | 状态 |
|---|---|
| 服务端三类分类 + nnU-Net 分割 | 已实现；GPU 引擎自测通过 |
| 服务端 HTTP 全链路 | 已用 TestClient 验证：分析、报告、记录列表、详情、删除、source/mask 下载 |
| Android 采集、结果、历史、设置、知识库、助手 | 已切换到 BUSI 乳腺超声主体 |
| 知识库 | 9 篇 BUSI 教学文章（3 篇模型标签 + 6 篇延伸阅读），5 个内容分类，3 张 BUSI 示例图（延伸阅读不配图） |
| 助手 | Android 发送乳腺超声 system prompt；服务端代理已配置模型时可用 |
| 文档 | 本文件、根 README、server/README、样例与知识库许可已按 BUSI 更新 |
| 静态检查与构建 | `:app:assembleDebug` 成功；`:core:database:testDebugUnitTest` 与 `:core:network:testDebugUnitTest` 通过 |
| QQ 天蓝换肤（§4.7） | 五批 + 一次真机回归修正已提交（49c8202..8fd564f）；红线核对通过，`StatusTag` 灰底胶囊不可见已修，待真机目测 |
| 真机端到端 | BUSI 全链路已通过（设备 `759e4c4b`，USB + 本机后端，采集→分析→结果→历史→助手）；换肤后的视觉结论需重跑一遍 |
| release 分发 | §12 第 1–3 项已落地：地址编辑器 + 输入校验 + release 自签（`app-release.apk` 已产出并验签）；真机 release 验证未执行 |

## 2. 模型与指标

### 2.1 分类

| 项 | 值 |
|---|---|
| 模型 | `ghostsas001/busi-breast-ultrasound-siglip` |
| 许可 | Apache-2.0 |
| 本地目录 | `server/models/busi_siglip/` |
| 权重 | `model.safetensors` |
| SHA-256 | `5bbb96dc240b4234ac32a81c3e2b2d50b4d83253a5a3ccc21b659f54b0e43abc` |
| 架构 | SigLIP2-base-patch16-224，`SiglipForImageClassification` |
| 输入 | 224×224 RGB，均值/方差均为 0.5 |
| 输出 | 三类 softmax，Top-3 候选，`primaryLabel` 为 Top-1 |

模型卡自报 78 张留出集：准确率 **87.18%**、宏 F1 **87.07%**、平衡准确率 86.47%。
这不是本次独立复测值，且留出集按图像切分、未去重。

### 2.2 分割

| 项 | 值 |
|---|---|
| 模型 | `veyselozdemir/nnUNet-Breast-Cancer-Ultrasound` |
| 许可 | MIT |
| 本地目录 | `server/models/busi_nnunet/nnUNet_results/Dataset101_Breast/nnUNetTrainer_10epochs__nnUNetPlans__2d/` |
| 架构 | nnU-Net v2，2D，`NNUNetTrainer_10epochs`，`fold_all` |
| 输入 | 灰度，patch 512×640，ZScore 归一化（plans 内置） |
| 输出 | 源图尺寸的类别图（0 背景 / 1 良性 / 2 恶性），应用取 `>0` 作为二值病灶掩码 |
| checkpoint SHA-256 | `6b4c5a9203c1bd741f0c9968ae22b2c1c33a19d63a80b2ed335b7c32722bfef6` |

作者使用 BUSI 全部 780 张图像训练，未提供独立测试协议。因此本项目**不作 Dice 性能声明**；
`server/samples/SAMPLES_LICENSE.md` 中三张演示图的 Dice 只用于说明样例分割质量，不是独立评测。

### 2.3 三类标签与顺序

分类权重 `config.json` 的 `id2label` 与服务端 `pathology.CLASSIFIER_LABELS` 必须逐位一致：

| 索引 | 标签 | 中文名 |
|---|---|---|
| 0 | benign | 良性病变 |
| 1 | malignant | 恶性病变 |
| 2 | normal | 正常乳腺组织 |

服务端加载权重时逐位核对；不一致时拒绝启动，禁止改代码迁就权重。

## 3. 服务端目标状态

分析主链路：

1. 解码上传影像为 sRGB，统一重编码为 `source.jpg`，不做灰度化、极性翻转或多视图拼图判定。
2. SigLIP 输出三类 softmax，按概率降序取 Top-K（默认 3；`top_k` 上限为 3）。
3. nnU-Net 通过 `predict_single_npy_array` 在进程内输出源图尺寸类别图，取 `>0` 生成逐像素掩码。
4. 计算 `lesionAreaPixels`、`lesionImageRatio`、`lesionBoundingBox`、`quality.maskValid`、`quality.maskTouchesEdge`、`imageWidth`、`imageHeight`、`imagePixels`。
5. 写入 `source.jpg` 与 `mask.png`，并在 `report.json` 中记录 `assets.source` 与 `assets.attention`。

报告字段：

- `schemaVersion = 3`
- `severityLevel = null`
- `attentionTarget = null`
- `attentionState = null`
- `severityUsesCtr = false`
- `maskUrl` 与 `assets.attention.url` 指向同一个 `/mask.png`
- `segmentor.inputSize = 640` 为兼容旧 DTO 的整数；真实 patch 为 512×640，记录在 `provenance.segmentationPatchSize`
- `provenance` 包含两套模型名称、来源、许可、SHA-256、模型卡/训练说明、标签顺序、预处理版本与 patch 尺寸

`ASSET_FILES` 只包含 `source` 与 `attention`。`attention` 是 Android 历史字段名，语义是真实病灶分割掩码，不是显著图或近似注意力图。分析链路只写源图与病灶掩码两项资产，不生成旧版解剖掩码、派生定量指标或证据门禁。

旧胸片对照模块 `comparison.py` / `evidence.py` 已删除，`store.py` 中的对照存储函数一并移除；服务端仅暴露 §6 所列路由，从未保留对照路由。

助手代理：Android 发送问题与已引用记录的文本摘要，服务端转发到已配置模型；影像不上传。模型地址与 Key 只保留在服务端。系统提示词已切换为“乳腺超声影像辅助解读助手”。提示词不再要求模型在结尾自我免责，保留模型原始输出；「内容由 AI 生成，请以医生最终判断为准。」改为界面侧渲染：显示在最后一条回答气泡下方，用 caption 小字与 text3 淡灰色，与回答正文的字体、配色区分。

## 4. Android 目标状态

### 4.1 拍摄页

- 文案为“乳腺超声影像”。
- 不显示体位选择，不显示“影像边界完整”确认。
- 可填写检查时间；输入框默认不放说明小字，仅当日期格式无效时显示纠正提示。分析成功后不再显示“影像分析完成：…”提示，未成功时保留“未完成，已保留影像，可稍后重试”。
- 提交时 `acquisition.projection` 固定为 `UNKNOWN`，`completenessConfirmed` 固定为 `false`，仅作为兼容字段。

### 4.2 结果页

- 标题为“影像与病灶分割”。
- 主标题展示主诊断；模型诊断面板说明主分类含义与病灶检出状态；界面不展示概率、置信度、原始名称或其他鉴别行，候补分类如需展示也只给名称。
- 主卡不显示“分析完成 · 待复核”“服务端分析”等状态与推理来源胶囊；操作区为「向助手提问」（携带当前记录进入助手会话，与历史页聊天气泡同一跳转逻辑）+「重新拍摄/重新分析/继续分析」。
- 病灶分割叠加使用醒目红色渲染；分割无病灶（空掩码）时「标记」「叠加」按钮置灰不可按。
- 影像下方不再展示“分割病灶占图面积”“叠加区域为…”等定量或说明文字。
- 不展示旧版严重度、派生定量指标与证据门禁。
- 免责表述同一界面至多一处：结果页仅保留主卡片下方一行「仅供教学，不替代医师诊断。」；知识库详情弹窗、助手隐私弹窗中的重复声明已删除。

### 4.3 首页、历史、启动页、设置、助手

- 首页无记录时不使用单行空提示，改为两块内容：“第一次使用”三步引导卡与“模型输出”三类标签卡；仍不显示黑底示例框与“标题 + 副标题”叠层，说明文字全部位于卡片内部。首页不放知识库板块，知识库入口只在底部导航“知识库”标签。服务未连接时在“新建分析”下方追加离线卡。
- 首页首屏 hero：最新一张影像固定高 160dp——底图同图 Crop 铺满宽度并虚化压暗，前景 Fit 完整显示整幅影像（纯 Crop 会把近正方形超声图裁得只剩一半高度），再按与前景一致的 Fit 比例叠一层约 40% 不透明度的淡红分割轮廓（mask 资产边缘检测，仅描边）。容器无圆角裁切，遵守 §4.7 影像像素保真红线。
- 「新建分析」按钮与全部强调色（选中态、开关、单选、主按钮、链接式操作）按 §4.7 的 QQ 天蓝规范取色，不再单独规定 info 容器底色与描边组合。
- 历史页：标题行 = 「历史记录」+ 刷新图标 + 选择按钮；搜索提示“搜索病症、日期或记录编号”以淡灰占位符显示在输入框内、输入后消失；“当前筛选”行与记录详情中的“推理来源”行已删除；聊天气泡与删除图标按钮统一为 44dp。
- 设置页“清除本地缓存”不再显示说明小字，仅在清除成功后短暂显示反馈。
- 品牌启动页文案为“乳腺超声影像 · 科研与教学”。
- 设置页知识库数量为“9 条乳腺超声资料”。
- 助手 system prompt 与失败回退提示均使用乳腺超声语义；免责不再出现在服务端回退提示词或输入区，只在最后一条 AI 回答下方以不喧宾夺主的小字出现一次。

### 4.4 知识库

- 模型标签教学文章 3 篇：良性病变、恶性病变、正常乳腺组织；延伸阅读 6 篇：一张合格的乳腺超声图、如何阅读模型分类结果、BI-RADS 0–6 速览、纤维腺瘤与单纯性囊肿、超声报告常见术语、发现异常后的就医路径。
- 分类为：良性病变、恶性病变、正常组织、检查基础、检查与随访（外加“全部”筛选）。
- 默认视图按“模型标签 / 延伸阅读”分组展示，筛选或搜索时为平铺列表；延伸阅读卡片带“延伸阅读”标签，详情弹窗追加“延伸阅读 · 非模型类别”标签与“参考来源”区块。
- 当前引用 3 张 `kb_busi_*.jpg`，来源 BUSI 公开数据集（CC BY 4.0；镜像页标注 CC0）；延伸阅读为纯文字内容，不配图。
- 免责仅两处、各页面至多一处：列表页底部一行使用边界；详情弹窗只保留图片说明行（含“不构成病理诊断”），弹窗尾部的重复边界声明已删除。
- 旧 `kb_isic_*.jpg` 与胸片 `kb_*.jpg` 已从 `drawable-nodpi/` 删除，清单与署名见 `feature/knowledge/KNOWLEDGE_IMAGES_LICENSE.md` 的历史段。

### 4.5 网络与调试

- Android 网络 DTO 字段名不改；继续兼容 `assets.attention` 作为掩码资产。
- 资产 DTO 只保留 `source` 与 `attention` 两项；旧版多余资产字段已从 DTO 删除，兼容读取时响应中未识别的字段被直接忽略，不崩溃。
- 后端地址在设置「服务状态」内填写，调试版与 release 共用这一入口。**没填过时**：调试版默认 `http://127.0.0.1:8000/`（配合 USB `adb reverse`），release 默认取构建配置里的地址；**填过以后**以填的为准，保存后立即生效并自动重新检查服务状态，无需重进 App。
- 服务状态只报「在线 / 离线」，不再显示底层英文异常文本；诊断请回到电脑看后端日志。

### 4.6 布局硬规则

页面与区块标题只允许一行。共享组件 `SectionHeader` 与 `EmptyState` 不提供副标题参数；空状态、页面标题下不得再叠说明行。该规则在 `core/designsystem/.../CommonComponents.kt` 注释中固定。QQ 版式的分组标签本身位于卡片外、单行显示，与本规则不冲突。

### 4.7 视觉语言：完全照搬 QQ 天蓝经典版式

本项目视觉以 QQ 经典设置页（iOS 分组卡片式）为准，**完全照搬，不做本地化改良**：

- 分组标签在卡片外、卡片上方，小一号灰色常规字重，左内缩与卡片内文字起始线对齐。
- 卡片为无边框、无描边、无阴影的纯白圆角矩形，圆角幅度小（10–12dp）。
- 行标题纯黑 16sp、行高约 52dp；右侧值文字与 chevron 用 QQ 的浅灰阶；行间 0.5dp 分隔线左端内缩、右端顶到卡片内边距，末行无分隔线。
- 主色为 QQ 天蓝（起始值 `#12B7F5`），用于选中态、开关、单选、主按钮与链接式操作；浅色模式导航与状态栏为白底黑字。
- 深色模式同步 QQ 深灰分层：页面底 `#171717`、卡片 `#232323`、分隔线 `#2C2C2C`。

**已明确放弃的约束**：不再以 WCAG 对比度红线作为取色依据。QQ 的浅灰值文字与分组标签在本项目中照抄使用，可读性让位于版式一致性；此前审计提出的 `text3` 对比度问题按「有意为之」处理，不再整改。

**仍然保留的两条红线**（与 QQ 版式无关，不因照搬而解除）：

1. 影像像素保真：`imageCanvas` 保持纯黑，影像容器不得新增圆角裁切，不得改 `ContentScale`——超声图边缘含诊断信息。
2. 契约与语义不动：HTTP 字段名、功能逻辑、中文句子语义不因换肤而修改；只允许改字号、字重、颜色、对齐、间距与容器结构。

## 5. 演示样例

`server/samples/busi/` 包含三组：

- `benign_113_source.jpg` + overlay：模型判读良性，P=1.0000，Dice≈0.956
- `malignant_202_source.jpg` + overlay：模型判读恶性，P=0.9957，Dice≈0.915
- `normal_92_source.jpg` + overlay：模型判读正常，P=0.9992，掩码为空

来源为 BUSI 公开数据集。`*_overlay.png` 只用于核对分割质量；App 演示导入 `*_source.jpg`，由服务端实时生成掩码。三张图是人工筛出的正确/清晰样例，**不是独立评测**。旧 ISIC 与胸片样例已从 `server/samples/` 删除，来源均为公开数据集，可按原许可重新获取。

## 6. HTTP 契约摘要

| 方法 | 路径 | 用途 |
|---|---|---|
| GET | `/health` | 设备、模型、3 类标签、助手配置自检（不含 Key） |
| POST | `/api/v1/assistant/chat` | 助手代理 |
| POST | `/api/v1/analyze` | 上传影像，返回分类、分割、测量与报告 |
| GET | `/api/v1/records?limit&offset` | 当前 install 的记录列表 |
| GET / DELETE | `/api/v1/records/{id}` | 单条记录 / 删除 |
| GET | `/api/v1/records/{id}/source.jpg`、`.../mask.png` | 源图 / 病灶掩码 |

关键字段：

- `primaryLabel`、`primaryLabelZh`
- `classifier.diseasePredictions[]`
- `segmentor.maskUrl`、`segmentor.inputSize`
- `lesionImageRatio`
- `measurements`（含病灶面积、占比、外接框、质量、尺寸）
- `assets.source`、`assets.attention`
- `schemaVersion = 3`

`sourceImageUrl`、`maskUrl`、`recordUrl` 保持相对 URL。请求带 `X-Install-Id` 作为匿名安装命名空间，不是患者身份或鉴权。

## 7. 静态自检与构建

本轮已执行：

- 服务端引擎自测（GPU）：
  - benign → 良性病变，mask ratio ≈ 0.0412
  - malignant → 恶性病变，mask ratio ≈ 0.1265
  - normal → 正常乳腺组织，mask 为空
- 服务端 HTTP 全链路 TestClient：分析三张样例、记录列表、详情、删除、source/mask 下载全部通过；报告 `schemaVersion=3`，掩码尺寸与源图一致。
- Windows Gradle：`:core:database:testDebugUnitTest`、`:core:network:testDebugUnitTest` 通过；
  `:app:assembleDebug -PDEBUG_BACKEND_BASE_URL=http://127.0.0.1:8000/` 成功。
- 已核对 APK dex 中后端地址为 `http://127.0.0.1:8000/`。
- QQ 换肤红线核对（范围 49c8202..8fd564f，逐条反证）：影像保真与契约语义两条红线均未违反——`imageCanvas` 仍为 `Color.Black`、影像容器无新增圆角裁切、`ContentScale` 未改，`core/model`/`core/network`/`server/` 零改动，用户可见中文文案逐文件比对零差异，§4.6 单行标题与组件无副标题参数。修出一处同类缺陷：`StatusTag` 中性态底色 `surface2` 与浅色页面底同值（#F2F3F5），灰底上胶囊轮廓消失，改为 `surface3`（与 8fd564f 修的次按钮同因）。修复后 `:app:assembleDebug` 重新构建通过。

未执行：模拟器、`connectedAndroidTest`。真机端到端已由用户执行并通过（设备 `759e4c4b`，USB 后端），本轮记录于 §1。

## 8. 历史与 legacy

以下内容属于已删除的遗留三代（农业/胸片/皮肤镜）：2026-10 清理批已从代码与磁盘中删除，不得再接入新导航或新分析链路，也不得据以回退当前实现：

- Android：`ResultComparisonScreen.kt`、`ResultMetricsScreen.kt`、`ComparisonViewModel.kt`（未跟踪，文件已删）；`DiagnosisModels.kt`、`DiagnosisHistoryStore.kt`、`core/network` 各文件与 `ResultSupport.kt` 中的对照屏、胸片指标、旧语义 reason 映射同步删除（commit `3f86b00`）
- 服务端：`comparison.py`、`evidence.py`（未跟踪，文件已删）及 `store.py` 中的对照存储函数
- 知识库：4 张旧 `kb_isic_*.jpg` 与 16 张旧胸片 `kb_*.jpg`（未跟踪，文件已删；署名与来源记录保留在 `KNOWLEDGE_IMAGES_LICENSE.md` 历史段）
- 样例：`server/samples/isic/`、`server/samples/legacy_chest/`（公开数据集，可按原许可重新下载）
- 权重与探测数据：`server/models/isic_vit_base/`、`server/models/isic_unet_resnet34.pth`、`server/models/densenet121_chexpert14.safetensors/.json`、`server/data/_isic_probe/`、`server/data/_ptx_model/` 已清除；体积与 SHA-256 记录在清理报告中，可按记录重新获取
- 注意：`ResultSupport.kt` 实际被当前 `ResultScreen` 同包调用（`statusLabel`、`statusTone`、`inferenceSourceLabel`），因此保留为活跃共享代码
- 本地历史中可能仍有旧胸片或皮肤镜记录；只做兼容读取，不迁移、不重新解释

旧胸片方案（CheXpert/DenseNet121、TorchXRayVision 分割、Grad-CAM、心胸比、严重度分级、证据门禁）与旧 ISIC 七类皮肤镜链路已停用并删除。不要根据旧 PLAN、旧知识库或旧记录回退当前 BUSI 实现。

## 9. 隐私与合规边界

- 服务端只应部署在可信内网；`/api/v1/analyze`、记录接口和助手接口没有生产级鉴权，不得暴露公网。
- 助手只发送文本摘要与问题，不上传影像；模型 URL/Key 仅在服务端。
- 日志不得包含图像、原始文件名、患者姓名、问答正文或凭据。
- 不声称临床验证、诊断级性能、生产级审计或全应用验收。
- 旧记录/旧 assistant 对话可能含历史措辞；本次不做批量清洗。

## 10. 演示流程

1. 启动院内后端，确认 `/health` 返回 `models.pathologies = ["benign","malignant","normal"]`。
2. 在拍摄页导入 `server/samples/busi/` 中的一张超声图，或从相册选择。
3. 可选填写检查时间，提交分析。
4. 结果页查看主诊断与红色病灶分割叠加（可切「标记」「叠加」）；正常记录无病灶范围，两按钮置灰。
5. 在历史页回看记录、删除记录或从记录进入助手。
6. 在知识库查看模型标签文章、延伸阅读与 BUSI 示例图。
7. 在设置页查看服务状态、隐私说明与“9 条乳腺超声资料”说明。

## 11. 未完成与风险

- 服务端若已在运行，需要重启后端才会加载后续代码改动。
- QQ 换肤落地后，真机视觉结论需重跑一遍（BUSI 链路的正确性结论不受影响）。
- release 包已可安装，但**真机 release 验证未执行**（步骤见 §12）；跑通之前不得当作可用分发物发给同学。ProGuard keep 规则经核对不需要补（DTO 全程 `@SerializedName`）。
- 两条 §4.7 版式偏离留待真机目测定夺，不在文档层面擅改：知识库「最近搜索」与设置页「显示主题」的分组标签仍写在卡片内（移出需拆卡或安置同行的「清空」操作）；Capture / Home / Result 的分组标签左端在 16dp、未内缩到卡内文字起始线 32dp（其中 Result 页两组标签下方是满宽影像而非带内缩的卡片，对齐目标本身存在歧义）。
- 分类 87.18% 是模型卡值，分割未做独立评测；演示样例是人工筛出的正确/清晰样本，不能外推为整体准确率。
- nnU-Net 使用 BUSI 全部图像训练，演示图与训练数据不独立。
- 两套权重仅适用于乳腺超声；其他模态、手机翻拍、截图拼接均不建议。
- 旧胸片/皮肤镜模块、旧知识库图片与旧模型数据已删除（见 §8 与清理报告）。

## 12. 分发计划：同学用自签 release 包，本机继续 debug + USB

**决策（已定）**：不上架、不公开发行；release 包只本机自签后私下交给同学试用在线服务。

地址入口对调试版与 release **一致**（推翻本节早先「debug 分支不读地址偏好」的写法）：`AppGraph` 先读 `ServiceEndpointConfigStore.loadOrNull()`，没填过时调试版回落 `http://127.0.0.1:8000/`、release 回落构建配置地址。这样同一台手机既能 USB 演示，也能填局域网地址连后端。

**已落地（编译通过，运行时表现待 release 包验证）**：

1. **地址编辑器**：设置页「服务状态」弹窗内可编辑服务地址，写入此前零调用的 `ServiceEndpointConfigStore.save()`。编辑器仅保留手填 LAN 地址，占位符 `http://192.168.1.23:8000/`；「USB 回环」预设（`http://127.0.0.1:8000/`）不在卡面上露出：它收在「支持与关于」卡片末尾的「开发者选项」行里，点开弹窗才显示该地址，按「使用该地址」即保存。网关经 `DynamicDebugHostConfig` + OkHttp 拦截器在每次请求时按当前保存地址重写 origin（`X-Install-Id` 防泄露校验同步按最新地址比对），保存后立即生效并自动重新检查服务状态，无需重启应用。弹窗中「在线推理：在线」以绿色字体显示。调试版与 release 都显示编辑器，无门控。
2. **输入校验**：只接受 `http://` + IPv4 + 端口，输出统一补尾斜杠；`https`、裸 IP、省略端口、八位组 >255、端口越界一律拒绝并给出行内错误提示。理由：网络层 `normalizedBaseUrl()` 只补尾部斜杠，不补 scheme 与端口，存进必然失败的地址比拒掉更坏。

**待办按阻塞顺序**：

3. ~~release 签名~~ **已完成**：keystore 在仓库外 `A:\keystore\clearview.jks`（PKCS12，alias `clearview`，有效期 10950 天，2026-10-25 生成），口令在 `A:\keystore\clearview.password.txt`，两者都不进仓库；`app/build.gradle.kts` 通过仓库根部被忽略的 `keystore.properties` 读取，该文件缺失时 release 仍产出未签名包、不阻断其他环境的构建。`apksigner verify` 通过，签名证书 SHA-256 `5a568b342ab27984eaf5d90e631ac92a12e75aec6d26b9569b7c07c9162a4a31`。keystore 丢失后无法给已安装版本覆盖升级，只能卸载重装，口令需自行备份。
4. ~~ProGuard keep 规则~~ **前提不成立，无需整改**：先前判断「R8 改名会让 Gson 对不上 JSON key」是错的。`core/network` 的 DTO 全程使用 `@SerializedName`（`PredictApiModels.kt` 内 36 处），Gson 按注解取值而非字段名；`app/proguard-rules.pro` 的 `-keepattributes ...RuntimeVisibleAnnotations...` 正是保住这些注解所需。R8 mapping 里 `label -> a`、`diseasePredictions -> d` 确实被改名，但在注解存在时无害。因此不额外加 `-keep class` 规则；若真机 release 出现「未返回有效分类候选」或空字段，再按实测结果补。
5. **后端监听**：`start-mobile-backend.ps1` 第 132 行硬编码 `--host 127.0.0.1`，只服务 USB，同学填对地址也连不上。给脚本加 `-Host` 参数（默认 `127.0.0.1`，分发场景传 `0.0.0.0`）。`0.0.0.0` 含回环，一次启动可同时服务本机 USB 与同学局域网，两条路不互斥。
6. **网络与自检顺序**：Windows 防火墙放行 TCP 8000 入站（网络配置文件为「公用」时默认全拦）；同学先在手机浏览器访问 `http://<IPv4>:8000/health` 看到 JSON 再开 App。IPv4 取 **WLAN 适配器**地址，不得用 `127.0.0.1`、`169.254.*`（DHCP 失败的 APIPA）或 `vEthernet (WSL)` 等虚拟网卡地址。
7. **文档**：`server/README.md` 与本页补 LAN 分发段，并写明首选拓扑为「同学开手机热点、本机连热点」——校园/办公 WiFi 常开客户端隔离，且 DHCP 会换 IP。

**release 真机验证步骤（尚未执行）**：

1. release 与 debug 签名不同，覆盖安装会失败：先 `adb uninstall com.chengying.clearview`，本机调试记录随卸载丢失（服务端记录仍在，重新同步可回看）。
2. 安装 `app/build/outputs/apk/release/app-release.apk`。
3. USB 场景验证：设置 → 支持与关于 → 开发者选项 → 点「使用该地址」→ 服务状态自动重新检查并显示在线（绿色字体），无需重进 App（`adb reverse` 映射仍需在位）。
4. 混淆判据：走一遍提交分析，结果页应出现三类候选、掩码可切「标记」「叠加」，助手带记录能回答，历史能同步。候选为空或字段缺失即视为 R8 影响，再据实测补 keep 规则。
5. 局域网场景验同学链路：`python run.py --host 0.0.0.0` + 防火墙放行 → 手机浏览器 `http://<IPv4>:8000/health` → App 内手填同一地址，保存后立即生效并自动重新检查服务状态。

**已知风险，本版按接受处理**：

- 后端无鉴权，`X-Install-Id` 只是命名空间而非访问控制（见 §6、§9）：同网段任意设备可提交分析、按 install id 读取他人与自己的记录及源图/mask、占用 GPU，并可借服务端模型凭据消耗助手额度。因此 `0.0.0.0` 监听**只在演示窗口内开启**，结束即回到 `127.0.0.1`；§9 的「不得暴露公网」在分发轨同样成立，不引入任何公网隧道。
- 单 uvicorn worker，多人同时提交会排队（3060 上一次分类+分割约 1–2 秒）。
- 分发期间本机不可睡眠：接电源、关自动睡眠，服务终端不关。
- 同学上传的影像落在 `server/data/installs/<installId>/`，由用户自行清理。
- debug 包与 release 包地址语义不同，属有意为之；不要把 debug 包当分发包发出去。
