# 演示样图来源与许可

这些样图仅用于科研、教学与开发自验，不含患者身份信息。

## 当前演示集：BUSI 乳腺超声

| 文件 | 模型判读 | BUSI 原图 | 许可 |
|---|---|---|---|
| `busi/benign_113_source.jpg` | 良性病变（P=1.0000，Dice≈0.956） | `benign (113).png` | **CC BY 4.0**（镜像页标注 CC0） |
| `busi/malignant_202_source.jpg` | 恶性病变（P=0.9957，Dice≈0.915） | `malignant (202).png` | **CC BY 4.0**（镜像页标注 CC0） |
| `busi/normal_92_source.jpg` | 正常乳腺组织（P=0.9992，掩码为空） | `normal (92).png` | **CC BY 4.0**（镜像页标注 CC0） |

`*_overlay.png` 为同一图像叠加 nnU-Net 预测病灶掩码后的效果图，仅用于核对分割质量；
App 演示时导入 `*_source.jpg`，由服务端实时生成掩码。

说明：

- **样例筛选不是独立评测。** 三张图是在 BUSI 整理集内先确认 SigLIP 分类正确、再确认 nnU-Net 分割清晰后选出的演示样例；表中的 P 与 Dice 只描述这三张图，不代表整体性能。
- SigLIP 分类权重为 `ghostsas001/busi-breast-ultrasound-siglip`（Apache-2.0），模型卡在 78 张留出集报告准确率 87.18%、宏 F1 87.07%，本次未独立复测。
- nnU-Net 分割权重为 `veyselozdemir/nnUNet-Breast-Cancer-Ultrasound`（MIT），10 epoch、fold_all，使用 BUSI 全部 780 张图像训练；本目录三张演示图的 Dice 都不是独立测试结果。
- 表中的“模型判读”是分类权重输出，不是病理确诊标签；演示时请勿表述为金标准。
- 掩码为模型逐像素分割，不构成临床诊断。

来源与引用：

- Al-Dhabyani W., Gomaa M., Khaled H., Fahmy A. *Dataset of breast ultrasound images.* Data in Brief, 2020. <https://doi.org/10.1016/j.dib.2019.104863>
- BUSI 原始数据集：<https://www.kaggle.com/datasets/aryashah2k/breast-ultrasound-images-dataset>
- 本项目使用的整理版来自公开 Hugging Face 镜像，仅做去重、尺寸重编码与固定演示集划分。

## 历史样例（已删除）

- `isic/`：ISIC 2018 皮肤镜四组样例（CC0）已从仓库删除；如需历史核对，可从 ISIC Archive 按原许可重新获取。
- `legacy_chest/`：旧胸片样例已从仓库删除；来源为公开胸片数据集，不得作为当前演示素材。
