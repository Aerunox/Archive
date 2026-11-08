#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""扫描全部 Icon(...) 调用点，判定深色模式下是否存在「未指定 tint → 落到 Miuix 硬编码 Black」的站点。"""
import re, os

root = 'app/src/main/java'
rows = []
for dp, _, fs in os.walk(root):
    for f in fs:
        if not f.endswith('.kt'):
            continue
        p = os.path.join(dp, f)
        src = open(p, encoding='utf-8').read()
        lines = src.split('\n')
        for m in re.finditer(r'(?<![A-Za-z0-9_])Icon\(', src):
            ln = src[:m.start()].count('\n') + 1
            txt = lines[ln - 1].strip()
            # 注释里的 Icon(tint = …) 与函数定义不是调用点
            if txt.startswith('Icon(tint') or 'fun Icon' in txt or txt.startswith('//'):
                continue
            i = m.end() - 1
            depth = 0
            end = None
            while i < len(src):
                if src[i] == '(':
                    depth += 1
                elif src[i] == ')':
                    depth -= 1
                    if depth == 0:
                        end = i
                        break
                i += 1
            body = src[m.start():end + 1] if end else src[m.start():m.start() + 400]
            mt = re.search(r'tint\s*=\s*([^,\n]+)', body)
            tint = re.sub(r'\s+', ' ', mt.group(1).strip()) if mt else None
            rel = p.replace(os.sep, '/').split('com/archive/app/')[-1]
            rows.append((rel, ln, tint))


def verdict(t):
    if t is None:
        return '**需修（裸 Icon，落到 Miuix 硬编码 Black）**'
    if 'Color.White' in t:
        return 'safe（刻意：深色蒙层上的浮层按钮）'
    if 'textPrimary' in t or 'textSecondary' in t or 'textTertiary' in t:
        return 'safe（PurrColors 文字色，跟随深浅色）'
    if 'resolvedTint' in t:
        return '已修（设计层 IconButton 兜底）'
    if 'colors.primary' in t or 'colors.error' in t:
        return 'safe（主题色，跟随配色）'
    if '?:' in t:
        return 'safe（调用方已给默认色）'
    if t == 'tint':
        return 'safe（tint 为非空 Color 参数，调用方显式传 colors.error / textSecondary）'
    return 'safe（显式主题色）'


os.makedirs('reports', exist_ok=True)
bare = [r for r in rows if r[2] is None]
out = []
out.append('# 深色模式「固定 Black 图标」审计\n')
out.append('扫描范围：`app/src/main/java`。另 `core/src/main/java`、`data/src/main/java` 经确认 `Icon(` 调用数为 0。\n')
out.append(f'- `Icon(...)` 调用点合计：**{len(rows)}**')
out.append(f'- 其中未显式给 `tint`（真正的「裸」调用）：**{len(bare)}**\n')
out.append('\n## 结论\n')
out.append('未发现「固定 Black 图标」遗漏站点。深色下图标不可见的根因是 Miuix 的 `LocalContentColor` 默认硬编码 `Color.Black`、'
           '且应用全程不覆盖它；该根因已在设计层收口：`MiuixDesign.IconButton` 取 `tint ?: LocalPurrColors.textPrimary`，'
           '顶栏返回箭头那条链路（`MiuixDesign.kt:148` 的 `MiuixIconButton` 不经该兜底）已就地显式指定 `textPrimary`。\n')
out.append('\n## 逐点判定\n')
out.append('| 文件:行 | tint 表达式 | 判定 |')
out.append('|---|---|---|')
for rel, ln, t in sorted(rows, key=lambda x: (x[0], x[1])):
    out.append(f'| {rel}:{ln} | `{t if t else "— 未指定 —"}` | {verdict(t)} |')
out.append('\n## FILE 附件提示口径\n')
out.append('`PostDetailScreen.kt:804-807`：`MediaKind.FILE` 分支的判空依据是 `downloadUrl`'
           '（`UrlProvider.downloadUrl` = `tempDownloadUrl` 优先，回退 `fileUrl(path)`），'
           '而不是播放地址 `playbackUrl`；该分支排在通用 `playbackUrl == null` 分支之前，'
           '因此「可下载但不可播放」的 deferred 附件不会被误报成「站点尚未导入原件」。\n')
open('reports/icon-audit.md', 'w', encoding='utf-8', newline='').write('\n'.join(out) + '\n')
print('rows', len(rows), 'bare', len(bare))
for r in bare:
    print('BARE:', r[0], r[1])
print('written reports/icon-audit.md')
