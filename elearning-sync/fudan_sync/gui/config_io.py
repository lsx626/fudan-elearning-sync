"""业务配置（config.yaml）与 GUI 状态（gui_state.json）的读写助手。

直接在 YAML 字典上做局部更新，不依赖数据类的序列化能力，也能保留未知字段。
"""
from __future__ import annotations

import json
import os
from typing import Any, Dict, List, Tuple

import yaml


def load_yaml(path: str) -> Dict[str, Any]:
    if not os.path.exists(path):
        return {}
    with open(path, "r", encoding="utf-8") as handle:
        return yaml.safe_load(handle) or {}


def save_yaml(path: str, data: Dict[str, Any]) -> None:
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    with open(path, "w", encoding="utf-8") as handle:
        yaml.safe_dump(data, handle, allow_unicode=True, sort_keys=False)


def update_config(path: str, updates: List[Tuple[str, Any]]) -> Dict[str, Any]:
    """把 (键路径, 值) 列表合并写回配置文件，键路径支持多级，如 ("sync", "interval_minutes")。

    值为 None 时表示删除该键。返回写回后的完整字典。
    """
    data = load_yaml(path)
    for keys, value in updates:
        node = data
        for key in keys[:-1]:
            if not isinstance(node.get(key), dict):
                node[key] = {}
            node = node[key]
        if value is None:
            node.pop(keys[-1], None)
        else:
            node[keys[-1]] = value
    save_yaml(path, data)
    return data


def load_gui_state(path: str) -> Dict[str, Any]:
    if not os.path.exists(path):
        return {}
    try:
        with open(path, "r", encoding="utf-8") as handle:
            return json.load(handle) or {}
    except (json.JSONDecodeError, OSError):
        return {}


def save_gui_state(path: str, state: Dict[str, Any]) -> None:
    try:
        os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
        with open(path, "w", encoding="utf-8") as handle:
            json.dump(state, handle, ensure_ascii=False, indent=2)
    except OSError:
        pass
