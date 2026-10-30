# 知识库配图来源与许可

## 当前使用：BUSI 乳腺超声示例（3 张）

当前 `KnowledgeDatasetData.kt` 只引用以下 3 张图片：

| 资源 | 对应文章 | BUSI 原图 | 许可 |
|---|---|---|---|
| `kb_busi_benign.jpg` | 良性病变 | `benign (113).png` | **CC BY 4.0**（镜像页标注 CC0） |
| `kb_busi_malignant.jpg` | 恶性病变 | `malignant (202).png` | **CC BY 4.0**（镜像页标注 CC0） |
| `kb_busi_normal.jpg` | 正常乳腺组织 | `normal (92).png` | **CC BY 4.0**（镜像页标注 CC0） |

这些图片来自 BUSI 公开数据集（Al-Dhabyani 等，Data in Brief 2020），应用仅做缩放与重新编码。图为原始超声影像，**未叠加 nnU-Net 分割**，也不是病理确诊；分割效果以分析结果页实时输出为准。完整来源、样例筛选说明与模型局限见 `server/samples/SAMPLES_LICENSE.md`。

知识库中的 6 篇延伸阅读文章为纯文字内容，不引用任何图片；上述 3 张图仅用于 3 篇模型标签文章。

## 历史遗留：ISIC 皮肤镜与胸片教学图（已删除，仅作署名留档）

- 4 张 `kb_isic_*.jpg`（旧 ISIC 2018 皮肤镜知识库资源，CC0）已从仓库删除。
- 16 张旧胸片 `kb_*.jpg` 已从 `feature/knowledge/src/main/res/drawable-nodpi/` 删除，`KnowledgeDatasetData.kt` 不再引用。
- 以下各节来源、许可与署名仅作为已删除资源的历史记录保留；不得把胸片或皮肤镜内容当作当前乳腺超声知识库的一部分，也不得据此回退当前实现。

## 历史清单共同事实与使用约定

- **全部为真实公开版权教学影像**，取自 Europe PMC 收录的开放获取文献，逐条按 CC 许可再发布；**没有任何一张来自本应用、本院或任何真实使用者的数据**。
- 每条许可均为 **CC BY**（署名许可，允许商用与改编），许可条件是**必须署名**。因此每张卡片在详情弹窗内强制渲染「图片来源与许可」一行；本文件是仓库侧的完整署名记录。
- 入库处理：统一转灰度 JPEG、按长边 ≤820 px 缩放、`-map_metadata -1` 清除全部 EXIF/XMP/注释段。**未绘制、未擦除、未拼接任何解剖内容**；唯一的像素级改动见 `kb_atelectasis` 条目说明。
- 隐私核查：每张图入库前逐边、逐角放大并做对比度提升扫描，确认**不含患者姓名、住院号/检查号、出生日期、检查日期、机构名或任何可归属标识**。侧位标记（R/L）、投照标注（PA/AP/ERECT/PORTABLE）、比例尺与出版方箭头属技术性标注、不含患者信息，予以保留。
- 内容边界：图注一律以「来源图注：」开头，表示**这是原文章的描述，不是本应用的判断**。审计中发现来源图注与像素不完全相符的，已在图注内直接写明差异，避免把作者主张当作可见征象传授。
## 已配图条目（16）

| 卡片 | 资源 | 来源文献 | 许可 |
|---|---|---|---|
| 肺不张 | `kb_atelectasis.jpg` | Ibrahim B, Ahmad R, Abbas Y 等. Endobronchial epithelioid leiomyoma (leiomyoblastoma) causing complete right-lung atelectasis: A case report. Medicine (Baltimore)，doi:10.1097/MD.0000000000050599，图 1 | **CC BY 4.0**（PMC13549589） |
| 肺实变 | `kb_consolidation.jpg` | Pallangyo P, Kweka G, Lyimo F 等. Complete heart block ensuing from a metastatic small cell carcinoma: a case report. Journal of Medical Case Reports 2022，doi:10.1186/s13256-021-03244-z，图 2 | **CC BY 4.0**（PMC8830129） |
| 肺部浸润 | `kb_infiltration.jpg` | Singh B, Kaur P, Reid RJ 等. COVID-19 and Influenza Co-Infection: Report of Three Cases. Cureus 2020;12(8):e9852，doi:10.7759/cureus.9852，图 2 | **CC BY 3.0**（PMC7437098） |
| 气胸 | `kb_pneumothorax.jpg` | Zoghi S, Brock CL, Atsina K 等. Iatrogenic chest tube placement within the heart following a large pneumothorax. Radiology Case Reports 2025，doi:10.1016/j.radcr.2025.04.089，图 2 | **CC BY 4.0**（PMC12148379） |
| 肺水肿 | `kb_oedema.jpg` | Abu Zaher M, Lee C, Abeygunasekar S. Flash Pulmonary Oedema in a Patient With Unilateral Renal Artery Stenosis and Preserved Contralateral Function: A Medically Managed Case Report. Cureus 2025，doi:10.7759/cureus.98005，图 2 | **CC BY 4.0**（PMC12744519） |
| 肺气肿 | `kb_emphysema.jpg` | Ragnoli B, Pochetti P, Veselagu X, Malerba M. Delayed Diagnosis of Alpha-1 Antitrypsin Deficiency in an Elderly Patient. Diagnostics (Basel)，doi:10.3390/diagnostics16091329，图 1 | **CC BY 4.0**（PMC13163515） |
| 肺纤维化 | `kb_fibrosis.jpg` | Rutka K, Garkowski A, Karaszewska K, Łebkowska U. Imaging in Diagnosis of Systemic Sclerosis. Journal of Clinical Medicine 2021;10(2):248，doi:10.3390/jcm10020248，图 3 | **CC BY 4.0**（PMC7827740） |
| 胸腔积液 | `kb_effusion.jpg` | Samanta RP, Agarwal S, Pathak S, Kamath S. Transudative Pleural Effusion as a Rare Manifestation of Disseminated Tuberculosis With Tubercular Myocarditis: A Case Report. Cureus 2026;18(5):e108863，doi:10.7759/cureus.108863，图 1 | **CC BY 4.0**（PMC13264609） |
| 肺炎 | `kb_pneumonia.jpg` | Biciusca T, Vogl TJ, Goedeking L 等. Host, Pathogen, and Pulmonary Anatomy in Pneumonia: An Integrated Imaging and Clinical Perspective. Diagnostics (Basel) 2026;16(11):1605，doi:10.3390/diagnostics16111605，图 5 | **CC BY 4.0**（PMC13257341） |
| 胸膜增厚 | `kb_pleural_thickening.jpg` | Saito A, Hakamata Y, Yamada Y 等. Pleural thickening on screening chest X-rays: a single institutional study. BMC Pulmonary Medicine 2019，doi:10.1186/s12931-019-1116-9，图 1 | **CC BY 4.0**（PMC6612134） |
| 肺结节 | `kb_nodule.jpg` | Hashimoto R, Nakagawa T, Kurebayashi Y, Masuda R. Solitary nodular pulmonary amyloidosis mimicking primary lung cancer: A case report. Radiology Case Reports 2026;21(12):6145-6148，doi:10.1016/j.radcr.2026.08.079，图 1 | **CC BY 4.0**（PMC13598527） |
| 肿块 | `kb_mass.jpg` | Pokhrel M, Shrestha B, Ghimire B 等. Incidental lung mass: a rare case of intrapulmonary schwannoma mimicking a pulmonary hydatid cyst. Oxford Medical Case Reports 2026，doi:10.1093/omcr/omaf306，图 1 | **CC BY 4.0**（PMC12916008） |
| 食管裂孔疝 | `kb_hiatal_hernia.jpg` | Lin YA, Berglind WJ, Cromer R. Acute Mesenteroaxial Volvulus in the Setting of Chronic Paraesophageal Hernia: A Case Report. Cureus 2023;15(1):e34124，doi:10.7759/cureus.34124，图 1 | **CC BY 3.0**（PMC9949735） |
| 肺部病灶 | `kb_lung_lesion.jpg` | Suliman AM, Alsayed A, Obiedat S, Massad E, Ul Haq I. Actinomyces-infected bronchopulmonary sequestration: An uncommon pathogen in a rare anomaly. Clinical Case Reports 2024;12(6):e8984，doi:10.1002/ccr3.8984，图 1 | **CC BY 4.0**（PMC11154760） |
| 肺部不透明影 | `kb_lung_opacity.jpg` | Ghahary D, Goldstein B, Hong L, Maffey M. A Hospitalized Patient With Systemic Scleroderma Requiring ICU-Level Care. Cureus 2026;18(7):e111920，doi:10.7759/cureus.111920，图 1 | **CC BY 4.0**（PMC13427440） |
| 纵隔心影增宽 | `kb_widened_mediastinum.jpg` | Alipuria A, Pandey KK, Gupta A, Alipuria P. The Terrible Ts That Was Not: Anterior Mediastinal Tuberculosis Mimicking Malignancy in an Immunocompetent Young Adult. Cureus 2026;18(1):e101776，doi:10.7759/cureus.101776，图 1 | **CC BY 4.0**（PMC12910525） |

## 完整署名文本（逐条，与 App 内渲染一致）

### `kb_atelectasis.jpg` — 肺不张

Ibrahim B, Ahmad R, Abbas Y 等. Endobronchial epithelioid leiomyoma (leiomyoblastoma) causing complete right-lung atelectasis: A case report. Medicine (Baltimore)，doi:10.1097/MD.0000000000050599，图 1。(c) 作者，CC BY 4.0（https://creativecommons.org/licenses/by/4.0/）。来源 PMC13549589。本应用裁去画面底部一处残缺字符并缩放重编码，未改动图示内容。

### `kb_consolidation.jpg` — 肺实变

Pallangyo P, Kweka G, Lyimo F 等. Complete heart block ensuing from a metastatic small cell carcinoma: a case report. Journal of Medical Case Reports 2022，doi:10.1186/s13256-021-03244-z，图 2。(c) 作者，CC BY 4.0（https://creativecommons.org/licenses/by/4.0/）。来源 PMC8830129。本应用仅缩放与重新编码。

### `kb_infiltration.jpg` — 肺部浸润

Singh B, Kaur P, Reid RJ 等. COVID-19 and Influenza Co-Infection: Report of Three Cases. Cureus 2020;12(8):e9852，doi:10.7759/cureus.9852，图 2。(c) 作者，CC BY 3.0（https://creativecommons.org/licenses/by/3.0/）。来源 PMC7437098。本应用仅缩放与重新编码。

### `kb_pneumothorax.jpg` — 气胸

Zoghi S, Brock CL, Atsina K 等. Iatrogenic chest tube placement within the heart following a large pneumothorax. Radiology Case Reports 2025，doi:10.1016/j.radcr.2025.04.089，图 2。(c) 作者，CC BY 4.0（http://creativecommons.org/licenses/by/4.0/）。来源 PMC12148379。本应用仅缩放与重新编码。

### `kb_oedema.jpg` — 肺水肿

Abu Zaher M, Lee C, Abeygunasekar S. Flash Pulmonary Oedema in a Patient With Unilateral Renal Artery Stenosis and Preserved Contralateral Function: A Medically Managed Case Report. Cureus 2025，doi:10.7759/cureus.98005，图 2。(c) 作者，CC BY 4.0（https://creativecommons.org/licenses/by/4.0/）。来源 PMC12744519。本应用仅缩放与重新编码。

### `kb_emphysema.jpg` — 肺气肿

Ragnoli B, Pochetti P, Veselagu X, Malerba M. Delayed Diagnosis of Alpha-1 Antitrypsin Deficiency in an Elderly Patient. Diagnostics (Basel)，doi:10.3390/diagnostics16091329，图 1。(c) 作者，CC BY 4.0（https://creativecommons.org/licenses/by/4.0/）。来源 PMC13163515。本应用仅缩放与重新编码。

### `kb_fibrosis.jpg` — 肺纤维化

Rutka K, Garkowski A, Karaszewska K, Łebkowska U. Imaging in Diagnosis of Systemic Sclerosis. Journal of Clinical Medicine 2021;10(2):248，doi:10.3390/jcm10020248，图 3。(c) 作者，CC BY 4.0（http://creativecommons.org/licenses/by/4.0/）。来源 PMC7827740。本应用仅缩放与重新编码。

### `kb_effusion.jpg` — 胸腔积液

Samanta RP, Agarwal S, Pathak S, Kamath S. Transudative Pleural Effusion as a Rare Manifestation of Disseminated Tuberculosis With Tubercular Myocarditis: A Case Report. Cureus 2026;18(5):e108863，doi:10.7759/cureus.108863，图 1。(c) 作者，CC BY 4.0（https://creativecommons.org/licenses/by/4.0/）。来源 PMC13264609。本应用仅缩放与重新编码。

### `kb_pneumonia.jpg` — 肺炎

Biciusca T, Vogl TJ, Goedeking L 等. Host, Pathogen, and Pulmonary Anatomy in Pneumonia: An Integrated Imaging and Clinical Perspective. Diagnostics (Basel) 2026;16(11):1605，doi:10.3390/diagnostics16111605，图 5。(c) 作者，CC BY 4.0（https://creativecommons.org/licenses/by/4.0/）。来源 PMC13257341。本应用仅缩放与重新编码。

### `kb_pleural_thickening.jpg` — 胸膜增厚

Saito A, Hakamata Y, Yamada Y 等. Pleural thickening on screening chest X-rays: a single institutional study. BMC Pulmonary Medicine 2019，doi:10.1186/s12931-019-1116-9，图 1。(c) 作者，CC BY 4.0（https://creativecommons.org/licenses/by/4.0/）。来源 PMC6612134。本应用仅缩放与重新编码。

### `kb_nodule.jpg` — 肺结节

Hashimoto R, Nakagawa T, Kurebayashi Y, Masuda R. Solitary nodular pulmonary amyloidosis mimicking primary lung cancer: A case report. Radiology Case Reports 2026;21(12):6145-6148，doi:10.1016/j.radcr.2026.08.079，图 1。(c) 作者，CC BY 4.0（http://creativecommons.org/licenses/by/4.0/）。来源 PMC13598527。本应用仅缩放与重新编码。

### `kb_mass.jpg` — 肿块

Pokhrel M, Shrestha B, Ghimire B 等. Incidental lung mass: a rare case of intrapulmonary schwannoma mimicking a pulmonary hydatid cyst. Oxford Medical Case Reports 2026，doi:10.1093/omcr/omaf306，图 1。(c) 作者，CC BY 4.0（https://creativecommons.org/licenses/by/4.0/）。来源 PMC12916008。本应用仅缩放与重新编码。

### `kb_hiatal_hernia.jpg` — 食管裂孔疝

Lin YA, Berglind WJ, Cromer R. Acute Mesenteroaxial Volvulus in the Setting of Chronic Paraesophageal Hernia: A Case Report. Cureus 2023;15(1):e34124，doi:10.7759/cureus.34124，图 1。(c) 作者，CC BY 3.0（https://creativecommons.org/licenses/by/3.0/）。来源 PMC9949735。本应用仅缩放与重新编码。

### `kb_lung_lesion.jpg` — 肺部病灶

Suliman AM, Alsayed A, Obiedat S, Massad E, Ul Haq I. Actinomyces-infected bronchopulmonary sequestration: An uncommon pathogen in a rare anomaly. Clinical Case Reports 2024;12(6):e8984，doi:10.1002/ccr3.8984，图 1。(c) 作者，CC BY 4.0（http://creativecommons.org/licenses/by/4.0/）。来源 PMC11154760。本应用仅缩放与重新编码。

### `kb_lung_opacity.jpg` — 肺部不透明影

Ghahary D, Goldstein B, Hong L, Maffey M. A Hospitalized Patient With Systemic Scleroderma Requiring ICU-Level Care. Cureus 2026;18(7):e111920，doi:10.7759/cureus.111920，图 1。(c) 作者，CC BY 4.0（https://creativecommons.org/licenses/by/4.0/）。来源 PMC13427440。本应用仅缩放与重新编码。

### `kb_widened_mediastinum.jpg` — 纵隔心影增宽

Alipuria A, Pandey KK, Gupta A, Alipuria P. The Terrible Ts That Was Not: Anterior Mediastinal Tuberculosis Mimicking Malignancy in an Immunocompetent Young Adult. Cureus 2026;18(1):e101776，doi:10.7759/cureus.101776，图 1。(c) 作者，CC BY 4.0（https://creativecommons.org/licenses/by/4.0/）。来源 PMC12910525。本应用仅缩放与重新编码。

## 未配图条目（5）

以下卡片**刻意保持无图**，不留空框、不用示意图或生成图充数：

- **心影增大**（心影与纵隔）
- **骨折**（膈肌与骨骼）
- **未见明确阳性征象**（报告与装置）
- **其他胸膜病变**（胸膜）
- **体内管线与装置**（报告与装置）

原因（前两条）：候选图均未通过内容核验。`心影增大` 的两个候选一张是印刷胶片的翻拍（半色调网点、上下裁切），一张叠加了起搏器、电极与 12 支箭头且合并大量后方致密影，任一张都会让学习者把「心影大」与其他征象混淆；`骨折` 的一个候选在四角与两侧边缘烧入检查日期、时间与序列号（检查日期属受保护标识，直接淘汰），另一候选为明显旋转体位且箭头所指无法确认肋骨骨折。宁缺毋滥。

原因（后三条）：分类权重换成 CheXpert 14 类标签集后新增的条目，**尚未检索配图**，不是候选被核验否决。其中 `未见明确阳性征象` 本身是"无阳性判读"的阈值结果，单张教学片无法代表它，原则上不打算配图。

## 核实方法（可复现）

检索与取证链路，全部经 Europe PMC REST：

1. `(相关术语) AND OPEN_ACCESS:Y AND LICENSE:"CC BY" AND SRC:MED`，`resultType=core`；
2. `rest/<PMCID>/fullTextXML` 取 `<graphic content-type="image">`、`<caption>` 与 `creativecommons.org` 许可 URI；
3. `rest/<PMCID>/supplementaryFiles` 资产包取图片二进制（python3 `zipfile` 解包），`ffprobe` 量尺寸；
4. 门槛：宽、高均 ≥500 px 且文件 ≥30 KB，**单图正位胸片**（排除 CT、面板图、临床照片、图表），逐张肉眼确认；
5. 溯源比对：与出版方原图做逐像素差值，确认非「缩略图放大」，并排除左右翻转与错图（如把复查片当成首诊片）。

许可一律由复核方**重新拉取原文确认**，不采信转述；并额外检索 `reprinted / courtesy / used with permission / third-party` 等第三方图片豁免条款，确认像素本身也在文章级 CC BY 覆盖范围内。

## 历史清单教学与合规边界

这些片子是**教学模式图**，不是临床病例：多数带有体外电极、导线、衣物或体内装置等干扰物，投照体位（PA/AP、立位/卧位）多不可考，因此**不得用于讲授心影大小、心胸比测量或任何需要标准体位的定量内容**。卡片与详情弹窗均保留「资料内容仅供参考，不构成临床诊断依据，不替代医师判断」的声明。

