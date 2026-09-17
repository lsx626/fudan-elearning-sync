"""认证模块：支持 Canvas API Token、浏览器 Cookie、Playwright 交互式登录."""
from __future__ import annotations

import json
import os
import re
import time
from typing import Any, Dict, List, Optional

import requests

CSRF_META_RE = re.compile(r'<meta\s+name="csrf-token"\s+content="([^"]+)"', re.IGNORECASE)


class AuthError(RuntimeError):
    """认证失败。"""


class BaseAuth:
    """所有认证方式的公共接口。"""

    name = "base"

    def apply(self, session: requests.Session) -> None:
        raise NotImplementedError

    def verify(self, session: requests.Session, api_base: str) -> bool:
        """调用 /api/v1/users/self 验证认证是否有效。"""
        try:
            resp = session.get(f"{api_base}/users/self", timeout=30)
            if resp.status_code == 200:
                data = resp.json()
                return bool(data.get("id"))
            return False
        except (requests.RequestException, ValueError):
            return False


class TokenAuth(BaseAuth):
    """Canvas Personal Access Token（Bearer）。"""

    name = "token"

    def __init__(self, token: str):
        if not token:
            raise AuthError("API Token 为空")
        self.token = token.strip()

    def apply(self, session: requests.Session) -> None:
        session.headers.update({"Authorization": f"Bearer {self.token}"})


class CookieAuth(BaseAuth):
    """使用浏览器会话 Cookie 访问 API（只读 GET 请求无需 CSRF）。"""

    name = "cookie"

    def __init__(self, cookie_file: str):
        self.cookie_file = cookie_file
        self.cookies: List[Dict[str, Any]] = []
        self.csrf_token: Optional[str] = None
        self._load()

    def _load(self) -> None:
        if not os.path.exists(self.cookie_file):
            raise AuthError(f"Cookie 文件不存在: {self.cookie_file}（请先运行 login 或手动导出）")
        with open(self.cookie_file, "r", encoding="utf-8") as handle:
            raw = json.load(handle)

        cookies: List[Dict[str, Any]] = []
        if isinstance(raw, list):  # Playwright context.cookies() 格式
            cookies = [c for c in raw if isinstance(c, dict) and c.get("name")]
        elif isinstance(raw, dict):
            if isinstance(raw.get("cookies"), list):  # {"cookies": [...], "csrf_token": ...}
                cookies = [c for c in raw["cookies"] if isinstance(c, dict) and c.get("name")]
                self.csrf_token = raw.get("csrf_token")
            elif isinstance(raw.get("cookie_header"), str):  # 原始 header 字符串
                for pair in raw["cookie_header"].split(";"):
                    if "=" in pair:
                        name, value = pair.strip().split("=", 1)
                        cookies.append({"name": name, "value": value,
                                        "domain": "", "path": "/"})
            else:  # 简单 {name: value} 映射
                cookies = [{"name": str(k), "value": str(v), "domain": "", "path": "/"}
                           for k, v in raw.items() if v is not None]
        else:
            raise AuthError(f"无法识别的 Cookie 文件格式: {self.cookie_file}")

        if not cookies:
            raise AuthError("Cookie 文件中未找到有效 cookie")
        self.cookies = cookies

    def apply(self, session: requests.Session) -> None:
        for cookie in self.cookies:
            session.cookies.set(
                cookie["name"], cookie["value"],
                domain=cookie.get("domain") or "",
                path=cookie.get("path") or "/",
            )
        if self.csrf_token:
            session.headers.update({"X-CSRF-Token": self.csrf_token})

    def extract_csrf(self, session: requests.Session, base_url: str) -> Optional[str]:
        """从已登录首页 HTML 中提取 CSRF token（提升 API 访问稳定性）。"""
        try:
            resp = session.get(base_url, timeout=30, allow_redirects=True)
            match = CSRF_META_RE.search(resp.text or "")
            if match:
                self.csrf_token = match.group(1)
                session.headers.update({"X-CSRF-Token": self.csrf_token})
        except requests.RequestException:
            pass
        return self.csrf_token


def save_cookies(cookie_file: str, cookies: List[Dict[str, Any]],
                 csrf_token: Optional[str] = None) -> None:
    """保存 cookie（Playwright 格式）到 JSON 文件，并限制文件权限。"""
    payload = {"csrf_token": csrf_token, "saved_at": int(time.time()), "cookies": cookies}
    os.makedirs(os.path.dirname(os.path.abspath(cookie_file)), exist_ok=True)
    with open(cookie_file, "w", encoding="utf-8") as handle:
        json.dump(payload, handle, ensure_ascii=False, indent=2)
    _restrict_file_permissions(cookie_file)


def _restrict_file_permissions(path: str) -> None:
    """尽可能限制敏感文件的可访问范围（Windows 用 icacls，POSIX 用 chmod）。"""
    try:
        if os.name == "nt":
            import subprocess
            subprocess.run(["icacls", path, "/inheritance:r", "/grant:r",
                            f"{os.environ.get('USERNAME', '')}:F"],
                           check=False, capture_output=True, timeout=15)
        else:
            os.chmod(path, 0o600)
    except Exception:  # pylint: disable=broad-except
        pass


def build_auth(cfg) -> BaseAuth:
    """根据配置构造认证对象（CLI 与 GUI 共用，避免逻辑重复）。

    cfg 只需提供属性：auth_method, token, cookie_file, base_url, uis_username。
    password 方式会先用钥匙串里的密码静默登录，再返回 Cookie 认证。
    """
    method = (getattr(cfg, "auth_method", "") or "").lower()
    if method == "token":
        return TokenAuth(cfg.token)
    if method in ("cookie", "browser"):
        return CookieAuth(cfg.cookie_file)
    if method == "password":
        # 延迟导入，避免与 password_login 模块形成循环导入
        from .password_login import login_with_stored_credentials  # pylint: disable=import-outside-toplevel
        login_with_stored_credentials(cfg.base_url, cfg.uis_username, cfg.cookie_file)
        return CookieAuth(cfg.cookie_file)
    raise AuthError(f"未知认证方式: {method}")


def browser_login(base_url: str, cookie_file: str, headless: bool = False,
                  timeout_seconds: int = 900) -> CookieAuth:
    """启动真实浏览器引导用户完成 UIS 登录，成功后自动保存 Cookie。

    需要：pip install playwright && python -m playwright install chromium
    """
    try:
        from playwright.sync_api import sync_playwright  # pylint: disable=import-outside-toplevel
    except ImportError as exc:
        raise AuthError(
            "未安装 playwright。请执行：\n"
            "  pip install playwright\n"
            "  python -m playwright install chromium\n"
            "或改用 token / cookie 方式登录。"
        ) from exc

    login_url = f"{base_url.rstrip('/')}/login"
    host = base_url.rstrip("/").split("//", 1)[-1]
    with sync_playwright() as pw:
        browser = pw.chromium.launch(headless=headless, args=["--start-maximized"])
        context = browser.new_context(ignore_https_errors=True)
        page = context.new_page()
        print(f"[browser_login] 正在打开 {login_url}")
        print("[browser_login] 请在浏览器中完成复旦大学统一身份认证（UIS）登录。")
        print(f"[browser_login] 登录成功后程序会自动捕获 Cookie（最长等待 {timeout_seconds} 秒）。")
        page.goto(login_url, wait_until="domcontentloaded")

        deadline = time.time() + timeout_seconds
        logged_in = False
        while time.time() < deadline:
            current = page.url or ""
            # 已登录标志：URL 回到 elearning 域且不在登录页，且页面含用户菜单
            if host in current and "/login" not in current:
                try:
                    page.wait_for_selector(
                        "button[aria-label*='账户'], button[aria-label*='Account'], "
                        "#global_nav_profile_link, .ic-avatar",
                        timeout=15000,
                    )
                    logged_in = True
                    break
                except Exception:  # pylint: disable=broad-except
                    # 选择器没找到但 URL 已回主页，也视为登录成功
                    if "/login" not in current:
                        logged_in = True
                        break
            time.sleep(1.5)

        if not logged_in:
            browser.close()
            raise AuthError("登录超时或未检测到登录成功状态。")

        cookies = [c for c in context.cookies() if host in (c.get("domain") or "")]
        csrf_token = None
        try:
            csrf_token = page.evaluate(
                "() => (document.querySelector('meta[name=csrf-token]') || {}).value || null"
            )
        except Exception:  # pylint: disable=broad-except
            pass
        browser.close()

    if not cookies:
        raise AuthError("未捕获到 elearning 域的 Cookie，登录可能未完成。")
    save_cookies(cookie_file, cookies, csrf_token)
    print(f"[browser_login] 已保存 {len(cookies)} 条 Cookie 到 {cookie_file}")
    return CookieAuth(cookie_file)
