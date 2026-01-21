#!/usr/bin/env python3
"""
Generate Foundry modifier overlays by masking modifier icons to a tool silhouette.
Supports targeted roots via group or prefixes.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Dict, Iterable, List, Optional

from PIL import Image, ImageChops

REPO_ROOT = Path(__file__).resolve().parents[1]
ASSETS_ROOT = REPO_ROOT / "src/main/resources/assets"

SMALL_ICON_TIERS = [
    (0.03, 2.0, 0.9),
    (0.06, 1.6, 0.85),
    (0.10, 1.3, 0.8),
]
MAX_UPSCALE = 2.5
EMBELLISHMENT_BOOST = 1.8
EMBELLISHMENT_MAX_UPSCALE = 3.0

GROUPS = {
    "ranged": [
        "item/foundry/tool/crossbow",
        "item/foundry/tool/longbow",
        "item/foundry/tool/war_pick",
    ],
}


def collect_modifier_roots() -> Dict[str, List[Path]]:
    roots: Dict[str, List[Path]] = {}
    for path in ASSETS_ROOT.rglob("*.json"):
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
        except Exception:
            continue
        if not isinstance(data, dict) or "modifier_roots" not in data:
            continue
        roots_value = data.get("modifier_roots")
        root_list: List[str] = []
        if isinstance(roots_value, list):
            root_list = [v for v in roots_value if isinstance(v, str)]
        elif isinstance(roots_value, dict):
            for value in roots_value.values():
                if isinstance(value, list):
                    root_list.extend([v for v in value if isinstance(v, str)])
        for root in root_list:
            roots.setdefault(root, []).append(path)
    return roots


def resource_to_texture_dir(resource: str) -> Path:
    namespace, path = resource.split(":", 1)
    path = path.lstrip("/")
    return ASSETS_ROOT / namespace / "textures" / path


def resource_to_texture_file(resource: str) -> Path:
    namespace, path = resource.split(":", 1)
    path = path.lstrip("/")
    return ASSETS_ROOT / namespace / "textures" / f"{path}.png"


def is_large_root(root: str) -> bool:
    return "/large/" in root or "large_modifiers" in root


def is_placeholder_root(root: str) -> bool:
    if not root.startswith("devmod:item/foundry/tool/"):
        return False
    t_root = root.replace("devmod:item/foundry/", "tconstruct:item/", 1)
    return not resource_to_texture_dir(t_root).exists()


def choose_model(paths: List[Path]) -> Path:
    devmod = [p for p in paths if "/assets/devmod/models/" in p.as_posix()]
    if devmod:
        return devmod[0]
    return paths[0]


def extract_part_names(model: dict) -> List[str]:
    parts = model.get("parts")
    if isinstance(parts, list) and parts:
        names: List[str] = []
        for entry in parts:
            if isinstance(entry, dict) and isinstance(entry.get("name"), str):
                names.append(entry["name"])
        if names:
            return names
    return ["tool"]


def build_mask(model: dict, use_large: bool) -> Optional[Image.Image]:
    textures = model.get("textures")
    if not isinstance(textures, dict):
        return None

    mask: Optional[Image.Image] = None
    for part in extract_part_names(model):
        key = f"large_{part}" if use_large and f"large_{part}" in textures else part
        tex_ref = textures.get(key)
        if not isinstance(tex_ref, str):
            continue
        tex_path = resource_to_texture_file(tex_ref)
        if not tex_path.exists():
            continue
        img = Image.open(tex_path).convert("RGBA")
        alpha = img.split()[3]
        if mask is None:
            mask = Image.new("L", img.size, 0)
        if alpha.size != mask.size:
            alpha = alpha.resize(mask.size, resample=Image.NEAREST)
        mask = ImageChops.lighter(mask, alpha)
    return mask


def build_template_maps(roots: Iterable[str]) -> Dict[str, Dict[str, Path]]:
    maps = {
        "small": {},
        "large": {},
        "small_broken": {},
        "large_broken": {},
    }

    def extract_frames(image: Image.Image) -> List[Image.Image]:
        width, height = image.size
        if height > width and height % width == 0:
            frames = []
            for index in range(height // width):
                top = index * width
                frames.append(image.crop((0, top, width, top + width)))
            return frames
        return [image]

    def has_alpha(path: Path) -> bool:
        img = Image.open(path).convert("RGBA")
        for frame in extract_frames(img):
            alpha = frame.split()[3]
            if any(v != 0 for v in alpha.getdata()):
                return True
        return False

    for root in roots:
        if is_placeholder_root(root):
            continue
        if "/broken/" in root:
            continue
        tex_dir = resource_to_texture_dir(root)
        if not tex_dir.exists():
            continue
        large = is_large_root(root)
        key = "large" if large else "small"
        for png in tex_dir.glob("*.png"):
            if has_alpha(png):
                maps[key].setdefault(png.name, png)
        broken_dir = tex_dir / "broken"
        if broken_dir.exists():
            broken_key = "large_broken" if large else "small_broken"
            for png in broken_dir.glob("*.png"):
                if has_alpha(png):
                    maps[broken_key].setdefault(png.name, png)
    return maps


def scale_icon(icon: Image.Image, scale: float) -> Image.Image:
    if scale == 1.0:
        return icon
    w = max(1, int(round(icon.size[0] * scale)))
    h = max(1, int(round(icon.size[1] * scale)))
    return icon.resize((w, h), resample=Image.NEAREST)


def extract_frames(image: Image.Image) -> List[Image.Image]:
    width, height = image.size
    if height > width and height % width == 0:
        frames = []
        for index in range(height // width):
            top = index * width
            frames.append(image.crop((0, top, width, top + width)))
        return frames
    return [image]


def find_best_position(mask: Image.Image, icon: Image.Image) -> tuple[int, int]:
    mask_w, mask_h = mask.size
    icon_w, icon_h = icon.size
    if icon_w > mask_w or icon_h > mask_h:
        return 0, 0
    mask_pixels = mask.load()
    alpha = icon.split()[3]
    alpha_pixels = alpha.load()
    icon_points = []
    for y in range(icon_h):
        for x in range(icon_w):
            a = alpha_pixels[x, y]
            if a:
                icon_points.append((x, y, a))
    if not icon_points:
        return 0, 0
    best_sum = -1
    best = (0, 0)
    for y in range(mask_h - icon_h + 1):
        for x in range(mask_w - icon_w + 1):
            total = 0
            for ix, iy, a in icon_points:
                total += mask_pixels[x + ix, y + iy] * a
            if total > best_sum:
                best_sum = total
                best = (x, y)
    return best


def render_overlays(
    root: str,
    model_path: Path,
    templates: Dict[str, Path],
    fallback_templates: Dict[str, Path],
) -> int:
    model = json.loads(model_path.read_text(encoding="utf-8"))
    use_large = is_large_root(root)
    mask = build_mask(model, use_large)
    if mask is None:
        return 0
    bbox = mask.getbbox()
    if not bbox:
        return 0

    target_w, target_h = mask.size
    mask_w = bbox[2] - bbox[0]
    mask_h = bbox[3] - bbox[1]

    icon_names = set(templates.keys()) | set(fallback_templates.keys())
    if not icon_names:
        return 0

    dest_dir = resource_to_texture_dir(root)
    dest_dir.mkdir(parents=True, exist_ok=True)

    written = 0
    for name in sorted(icon_names):
        src_path = templates.get(name) or fallback_templates.get(name)
        if src_path is None:
            continue
        img = Image.open(src_path).convert("RGBA")
        frames = extract_frames(img)
        if not frames:
            continue
        frame_size = frames[0].size[0]

        union_bbox = None
        for frame in frames:
            alpha = frame.split()[3]
            bbox = alpha.getbbox()
            if bbox:
                if union_bbox is None:
                    union_bbox = list(bbox)
                else:
                    union_bbox[0] = min(union_bbox[0], bbox[0])
                    union_bbox[1] = min(union_bbox[1], bbox[1])
                    union_bbox[2] = max(union_bbox[2], bbox[2])
                    union_bbox[3] = max(union_bbox[3], bbox[3])
        if not union_bbox:
            continue

        icons = [frame.crop(tuple(union_bbox)) for frame in frames]
        alpha = icons[0].split()[3]
        nonzero = sum(1 for v in alpha.getdata() if v != 0)
        if nonzero == 0:
            continue

        base_scale = target_w / frame_size
        base_area = frame_size * frame_size
        coverage = nonzero / base_area
        boost = 1.0
        max_factor = 0.7
        embellishment = "embellishment" in name
        for limit, scale, factor in SMALL_ICON_TIERS:
            if coverage <= limit:
                boost = scale
                max_factor = factor
                break
        if embellishment:
            boost *= EMBELLISHMENT_BOOST
            max_factor = max(max_factor, 0.9)

        scale_factor = base_scale * boost
        scaled_probe = scale_icon(icons[0], scale_factor)

        max_w = max(1, int(mask_w * max_factor))
        max_h = max(1, int(mask_h * max_factor))
        scale_limit = min(max_w / scaled_probe.size[0], max_h / scaled_probe.size[1])
        max_upscale = EMBELLISHMENT_MAX_UPSCALE if embellishment else MAX_UPSCALE
        if boost <= 1.0:
            scale_limit = min(scale_limit, 1.0)
        else:
            scale_limit = min(scale_limit, max_upscale / boost)
        final_scale = scale_factor * scale_limit
        scaled_icons = [scale_icon(icon, final_scale) for icon in icons]
        x, y = find_best_position(mask, scaled_icons[0])

        sheet_height = target_h * len(scaled_icons)
        sheet = Image.new("RGBA", (target_w, sheet_height), (0, 0, 0, 0))
        for index, scaled in enumerate(scaled_icons):
            canvas = Image.new("RGBA", (target_w, target_h), (0, 0, 0, 0))
            canvas.paste(scaled, (x, y), scaled)
            new_alpha = ImageChops.multiply(canvas.split()[3], mask)
            canvas.putalpha(new_alpha)
            sheet.paste(canvas, (0, index * target_h))

        sheet.save(dest_dir / name)
        written += 1

        mcmeta = src_path.with_suffix(src_path.suffix + ".mcmeta")
        if mcmeta.exists():
            (dest_dir / mcmeta.name).write_text(mcmeta.read_text(encoding="utf-8"), encoding="utf-8")

    return written


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate Foundry modifier overlays for specific roots.")
    parser.add_argument("--group", choices=sorted(GROUPS.keys()))
    parser.add_argument("--prefix", action="append", default=[], help="Root path prefix (after namespace). Can be repeated.")
    args = parser.parse_args()

    prefixes = list(args.prefix)
    if args.group:
        prefixes.extend(GROUPS[args.group])

    if not prefixes:
        raise SystemExit("Provide --group or at least one --prefix")

    roots_map = collect_modifier_roots()
    all_roots = list(roots_map.keys())
    template_maps = build_template_maps(all_roots)

    target_roots = []
    for root in all_roots:
        if not root.startswith("devmod:"):
            continue
        path = root.split(":", 1)[1]
        if any(path.startswith(prefix) for prefix in prefixes):
            if "modifiers" in path:
                target_roots.append(root)

    if not target_roots:
        raise SystemExit("No matching modifier roots found for given prefixes")

    total_written = 0
    for root in sorted(target_roots):
        model_path = choose_model(roots_map[root])
        use_large = is_large_root(root)
        is_broken = "/broken/" in root
        if is_broken:
            templates = template_maps["large_broken" if use_large else "small_broken"]
            if use_large:
                fallback = {**template_maps["small"], **template_maps["small_broken"]}
            else:
                fallback = template_maps["small"]
        else:
            templates = template_maps["large" if use_large else "small"]
            fallback = template_maps["small"] if use_large else {}
        written = render_overlays(root, model_path, templates, fallback)
        total_written += written

    print(f"Generated {total_written} overlays across {len(target_roots)} roots")


if __name__ == "__main__":
    main()
