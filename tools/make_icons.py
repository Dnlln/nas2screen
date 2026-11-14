"""Generate launcher icons and the Android TV banner for the app."""
import os

from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")

BG_TOP = (24, 38, 46)
BG_BOTTOM = (12, 18, 22)
ACCENT = (82, 208, 138)
SKY = (58, 122, 168)
SUN = (255, 214, 102)


def gradient(size, top, bottom):
    img = Image.new("RGB", (size, size), bottom)
    d = ImageDraw.Draw(img)
    for y in range(size):
        t = y / max(1, size - 1)
        c = tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3))
        d.line([(0, y), (size, y)], fill=c)
    return img


def draw_glyph(img, s, inset):
    """Draw a photo frame with mountains and a sun."""
    x0, y0 = inset, inset
    x1, y1 = s - inset, s - inset
    r = max(2, int(s * 0.06))

    # white photo frame first, the scene is drawn on top of its inner area
    frame = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ImageDraw.Draw(frame).rounded_rectangle([x0, y0, x1, y1], radius=r,
                                           fill=(250, 250, 250, 255))
    img.paste(frame, (0, 0), frame)

    pad = max(2, int(s * 0.055))
    sx0, sy0, sx1, sy1 = x0 + pad, y0 + pad, x1 - pad, y1 - pad
    inner_radius = max(2, r // 2)

    scene = Image.new("RGBA", img.size, (0, 0, 0, 0))
    sd = ImageDraw.Draw(scene)
    # sky gradient
    height = sy1 - sy0
    for i in range(height):
        t = i / max(1, height - 1)
        top = (108, 178, 227)
        bottom = (58, 122, 168)
        color = tuple(int(top[c] + (bottom[c] - top[c]) * t) for c in range(3))
        sd.line([(sx0, sy0 + i), (sx1, sy0 + i)], fill=color + (255,))

    sr = max(3, int(s * 0.075))
    scx = sx0 + int((sx1 - sx0) * 0.70)
    scy = sy0 + int((sy1 - sy0) * 0.28)
    sd.ellipse([scx - sr, scy - sr, scx + sr, scy + sr], fill=SUN + (255,))

    # mountains
    w = sx1 - sx0
    h = sy1 - sy0
    sd.polygon(
        [
            (sx0, sy1),
            (sx0 + int(w * 0.02), sy1 - int(h * 0.30)),
            (sx0 + int(w * 0.30), sy1 - int(h * 0.74)),
            (sx0 + int(w * 0.55), sy1 - int(h * 0.18)),
            (sx0 + int(w * 0.74), sy1 - int(h * 0.50)),
            (sx1, sy1 - int(h * 0.10)),
            (sx1, sy1),
        ],
        fill=ACCENT + (255,),
    )
    # nearer, darker hill for depth
    sd.polygon(
        [
            (sx0, sy1),
            (sx0 + int(w * 0.18), sy1 - int(h * 0.34)),
            (sx0 + int(w * 0.48), sy1 - int(h * 0.06)),
            (sx0 + int(w * 0.70), sy1 - int(h * 0.26)),
            (sx1, sy1 - int(h * 0.02)),
            (sx1, sy1),
        ],
        fill=(38, 132, 92, 255),
    )

    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([sx0, sy0, sx1, sy1], radius=inner_radius,
                                           fill=255)
    img.paste(scene, (0, 0), mask)


def icon(size, rounded=True):
    img = gradient(size, BG_TOP, BG_BOTTOM).convert("RGBA")
    if rounded:
        mask = Image.new("L", (size, size), 0)
        ImageDraw.Draw(mask).rounded_rectangle(
            [0, 0, size - 1, size - 1], radius=int(size * 0.22), fill=255
        )
        img.putalpha(mask)
    draw_glyph(img, size, max(2, int(size * 0.18)))
    return img


def banner(w=320, h=180):
    img = Image.new("RGBA", (w, h), BG_BOTTOM + (255,))
    # diagonal gradient
    grad = Image.new("RGB", (w, h), BG_BOTTOM)
    d = ImageDraw.Draw(grad)
    for y in range(h):
        t = y / max(1, h - 1)
        c = tuple(int(BG_TOP[i] + (BG_BOTTOM[i] - BG_TOP[i]) * t) for i in range(3))
        d.line([(0, y), (w, y)], fill=c)
    img.paste(grad, (0, 0))
    d = ImageDraw.Draw(img, "RGBA")
    d.ellipse([int(w * 0.62), -int(h * 0.35), int(w * 1.15), int(h * 0.55)], fill=(40, 90, 78, 120))
    # glyph on the left
    glyph_size = int(h * 0.62)
    glyph = Image.new("RGBA", (glyph_size, glyph_size), (0, 0, 0, 0))
    draw_glyph(glyph, glyph_size, int(glyph_size * 0.04))
    img.paste(glyph, (int(w * 0.07), (h - glyph_size) // 2), glyph)
    # text
    try:
        from PIL import ImageFont

        font = None
        for candidate in [
            "C:/Windows/Fonts/segoeuib.ttf",
            "C:/Windows/Fonts/arialbd.ttf",
            "C:/Windows/Fonts/arial.ttf",
        ]:
            if os.path.exists(candidate):
                font = ImageFont.truetype(candidate, int(h * 0.16))
                break
        if font is None:
            raise RuntimeError("no font")
        tx = int(w * 0.07) + glyph_size + int(w * 0.05)
        d.text((tx, int(h * 0.36)), "NAS", font=font, fill=(236, 239, 241, 255))
        d.text((tx, int(h * 0.55)), "Screensaver", font=font, fill=ACCENT + (255,))
    except Exception:
        pass
    return img


def main():
    sizes = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    for dpi, s in sizes.items():
        out = os.path.join(RES, "mipmap-" + dpi)
        os.makedirs(out, exist_ok=True)
        icon(s).save(os.path.join(out, "ic_launcher.png"))
    os.makedirs(os.path.join(RES, "drawable-xhdpi"), exist_ok=True)
    banner().save(os.path.join(RES, "drawable-xhdpi", "banner.png"))
    # preview for quick visual check
    os.makedirs(os.path.join(ROOT, "docs"), exist_ok=True)
    big = icon(512)
    big.save(os.path.join(ROOT, "docs", "icon-512.png"))
    banner(640, 360).save(os.path.join(ROOT, "docs", "banner-640.png"))
    print("icons written")


if __name__ == "__main__":
    main()
