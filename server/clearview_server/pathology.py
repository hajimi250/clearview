"""BUSI 乳腺超声三类标签的中英文映射。

标签顺序与分类权重的 config.id2label 对齐；加载时逐位核对，不一致即拒绝启动。
"""
CLASSIFIER_LABELS = (
    "benign",
    "malignant",
    "normal",
)

PATHOLOGY_EN = {
    "benign": "Benign breast lesion",
    "malignant": "Malignant breast lesion",
    "normal": "Normal breast tissue",
}

PATHOLOGY_ZH = {
    "benign": "良性病变",
    "malignant": "恶性病变",
    "normal": "正常乳腺组织",
}

MALIGNANT_LABELS = frozenset({"malignant"})
MAX_CANDIDATES = len(CLASSIFIER_LABELS)


def zh_for(label: str) -> str:
    return PATHOLOGY_ZH.get(label, label)


def en_for(label: str) -> str:
    return PATHOLOGY_EN.get(label, label)
