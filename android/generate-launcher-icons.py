"""Generate Android launcher icon variants from the OpenAVC square logo.

Reads logo-square.png (539x539, white-on-content), writes both the
adaptive-icon foreground (transparent, content scaled to fit a circular
safe zone) and the legacy square mipmap icons (PNG fallback for older
launchers + Play Store thumbnails).

Run from this directory: `python generate-launcher-icons.py`. Idempotent.
"""

from __future__ import annotations

from pathlib import Path

from PIL import Image

HERE = Path(__file__).parent
RES = HERE / "app" / "src" / "main" / "res"
SOURCE_LOGO = HERE.parent / "openavc-logo-square.png"  # placed by the workflow

# Android density buckets and the launcher icon size at each.
# Square (legacy) icon is 48dp x 48dp, scaled per density.
# Adaptive foreground/background canvas is 108dp x 108dp.
DENSITIES = {
    "mdpi":    {"scale": 1.0, "legacy": 48,  "adaptive": 108},
    "hdpi":    {"scale": 1.5, "legacy": 72,  "adaptive": 162},
    "xhdpi":   {"scale": 2.0, "legacy": 96,  "adaptive": 216},
    "xxhdpi":  {"scale": 3.0, "legacy": 144, "adaptive": 324},
    "xxxhdpi": {"scale": 4.0, "legacy": 192, "adaptive": 432},
}

# Fraction of the adaptive 108dp canvas the logo occupies. The "safe zone"
# (always visible regardless of mask shape) is the inner 72/108 = 0.667 of
# the canvas. Going slightly above that — the corners + edges of the source
# logo are pure white, which matches the background drawable, so visual
# clipping of those areas is invisible. 0.82 keeps "Open" inside the
# circular safe zone while leaving the logo readable.
FOREGROUND_SCALE = 0.82


def load_logo() -> Image.Image:
    img = Image.open(SOURCE_LOGO).convert("RGBA")
    print(f"  source: {img.size[0]}x{img.size[1]} from {SOURCE_LOGO.name}")
    return img


def write_legacy_mipmaps(logo: Image.Image) -> None:
    """Write the square fallback mipmaps (ic_launcher.png + ic_launcher_round.png).

    Legacy launchers and the Play Store listing use these directly with
    no shape masking, so the icon is just the logo resized.
    """
    for bucket, cfg in DENSITIES.items():
        size = cfg["legacy"]
        out_dir = RES / f"mipmap-{bucket}"
        out_dir.mkdir(parents=True, exist_ok=True)
        resized = logo.resize((size, size), Image.Resampling.LANCZOS)
        # Square launcher icon.
        resized.save(out_dir / "ic_launcher.png", optimize=True)
        # Round launcher icon (same image; older launchers may apply a circular
        # mask themselves, but we're providing the source pixels regardless).
        resized.save(out_dir / "ic_launcher_round.png", optimize=True)
        print(f"  legacy mipmap-{bucket}: {size}x{size}")


def write_adaptive_foreground(logo: Image.Image) -> None:
    """Write the adaptive icon foreground at each density.

    Canvas is the full adaptive 108dp size, transparent. The logo is
    centered at FOREGROUND_SCALE of the canvas so its visible content
    falls within the safe zone on circle-masked launchers.
    """
    for bucket, cfg in DENSITIES.items():
        canvas_size = cfg["adaptive"]
        content_size = int(round(canvas_size * FOREGROUND_SCALE))
        offset = (canvas_size - content_size) // 2

        canvas = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
        scaled = logo.resize((content_size, content_size), Image.Resampling.LANCZOS)
        canvas.paste(scaled, (offset, offset), scaled)

        out_dir = RES / f"mipmap-{bucket}"
        out_dir.mkdir(parents=True, exist_ok=True)
        canvas.save(out_dir / "ic_launcher_foreground.png", optimize=True)
        print(f"  adaptive mipmap-{bucket}: {canvas_size}x{canvas_size} "
              f"(content {content_size}x{content_size})")


def main() -> None:
    if not SOURCE_LOGO.exists():
        raise SystemExit(f"Source logo not found: {SOURCE_LOGO}")
    print(f"Output res dir: {RES}")
    logo = load_logo()
    write_legacy_mipmaps(logo)
    write_adaptive_foreground(logo)
    print("Done.")


if __name__ == "__main__":
    main()
