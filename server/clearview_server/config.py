"""部署与路径配置：BUSI 乳腺超声三类分类 + nnU-Net 病灶分割。"""
import os
from pathlib import Path

SERVER_ROOT = Path(__file__).resolve().parents[1]

DATA_ROOT = Path(os.environ.get("CLEARVIEW_DATA_DIR", SERVER_ROOT / "data"))
MODELS_ROOT = Path(os.environ.get("CLEARVIEW_MODELS_DIR", SERVER_ROOT / "models"))

# 分类权重：ghostsas001/busi-breast-ultrasound-siglip（Apache-2.0）
# benign / malignant / normal 三类，标签顺序在加载时按 config.id2label 逐位核对。
CLASSIFIER_DIR = Path(os.environ.get(
    "CLEARVIEW_CLASSIFIER_DIR", MODELS_ROOT / "busi_siglip"))
CLASSIFIER_WEIGHTS_SHA256 = os.environ.get(
    "CLEARVIEW_CLASSIFIER_SHA256",
    "5bbb96dc240b4234ac32a81c3e2b2d50b4d83253a5a3ccc21b659f54b0e43abc")

# 分割权重：veyselozdemir/nnUNet-Breast-Cancer-Ultrasound（MIT）
# nnU-Net v2，2D，fold_all，10 epoch，在 BUSI 全部图像上训练。
NNUNET_MODEL_DIR = Path(os.environ.get(
    "CLEARVIEW_NNUNET_MODEL_DIR",
    MODELS_ROOT / "busi_nnunet" / "nnUNet_results" / "Dataset101_Breast"
    / "nnUNetTrainer_10epochs__nnUNetPlans__2d"))
NNUNET_CHECKPOINT = "checkpoint_final.pth"
NNUNET_FOLD = "all"
NNUNET_CHECKPOINT_SHA256 = os.environ.get(
    "CLEARVIEW_SEG_SHA256",
    "6b4c5a9203c1bd741f0c9968ae22b2c1c33a19d63a80b2ed335b7c32722bfef6")

CLASSIFIER_INPUT = 224
CLASSIFIER_MEAN = (0.5, 0.5, 0.5)
CLASSIFIER_STD = (0.5, 0.5, 0.5)
# nnU-Net 2D 配置的 patch 为（高 512、宽 640）；inputSize 字段沿用旧契约，取宽边。
SEG_INPUT = 640
SEG_PATCH_SIZE = (512, 640)
# 低于该面积视为未检出可靠病灶；高于该面积视为分割溢出。仍保留掩码供复核。
SEG_MIN_AREA_RATIO = 0.002
SEG_MAX_AREA_RATIO = 0.95

CLASSIFIER_MODEL_NAME = "siglip2-base-busi-breast-3class"
CLASSIFIER_MODEL_SOURCE = (
    "https://huggingface.co/ghostsas001/busi-breast-ultrasound-siglip")
CLASSIFIER_MODEL_LICENSE = "Apache-2.0"
CLASSIFIER_DATASET = "BUSI 乳腺超声（benign / malignant / normal 三分类）"
# 模型卡自报值，不是本次独立复测；留出集仅 78 张且按图像切分。
CLASSIFIER_METRICS = {
    "reportedHoldoutAccuracy": 0.8718,
    "reportedHoldoutMacroF1": 0.8707,
    "reportedHoldoutSamples": 78,
    "independentlyReevaluated": False,
}

SEG_MODEL_NAME = "nnunet-v2-busi-breast-2d"
SEG_MODEL_SOURCE = (
    "https://huggingface.co/veyselozdemir/nnUNet-Breast-Cancer-Ultrasound")
SEG_MODEL_LICENSE = "MIT"
SEG_DATASET = "BUSI 全量 780 张图像（10 epoch/fold_all，训练集与演示图不独立）"
# 作者未提供独立测试协议；不作 Dice 指标声明，避免把演示效果当成临床性能。
SEG_METRICS = {
    "independentEvaluation": False,
    "trainingEpochs": 10,
    "trainingImages": 780,
}

MODEL_VERSION = "4.0.0"
EXAM_DESCRIPTION = "乳腺超声影像"

DISCLAIMER = (
    "本结果由院内服务器本地推理生成。分类权重为公开 BUSI SigLIP 三类模型"
    "（Apache-2.0），模型卡在 78 张留出集报告准确率 87.18%、宏 F1 87.07%，"
    "本次未做独立复测；病灶分割权重为公开 BUSI nnU-Net v2（MIT，10 epoch，"
    "在 BUSI 全部 780 张图像上训练），未做独立评测。结果仅供科研与教学演示，"
    "不构成临床诊断依据，最终诊断须由执业医师结合超声、病理及临床资料作出。"
)

# 兼容未启用的旧对照模块；BUSI 推理链路不使用这些阈值。
POSITIVE_THRESHOLD = 0.70
POSITIVE_THRESHOLD_BASIS = "legacy-comparison-only"
ATTENTION_TOP_PERCENT = 92.0
ATTENTION_MIN_AREA_RATIO = 0.005
